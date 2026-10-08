package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountDao
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.BalanceObservationDao
import com.example.moneymanager.data.BalanceObservationEntity
import com.example.moneymanager.data.TransactionEntity
import java.util.Locale

/**
 * Resolves masked instruments (card / bank last-4) to [AccountEntity] rows,
 * keeps ledger balances in sync, and records SMS-vs-ledger difference snapshots.
 *
 * Bank-agnostic: identity is (type + last4), with optional free-text [bankHint].
 */
object InstrumentLedger {

    suspend fun resolveOrCreate(
        accountDao: AccountDao,
        parsed: ParsedSms
    ): Long? {
        val card = parsed.cardLast4?.trim()?.takeLast(4).orEmpty()
        if (card.length == 4 && card.all { it.isDigit() }) {
            return resolveCard(accountDao, card, parsed.bankHint)
        }
        val ac = parsed.accountLast4?.trim()?.takeLast(4).orEmpty()
        if (ac.length == 4 && ac.all { it.isDigit() }) {
            return resolveBank(accountDao, ac, parsed.bankHint)
        }
        return null
    }

    /** @deprecated Prefer [resolveOrCreate]; kept for call-site compatibility. */
    suspend fun resolveOrCreateCard(accountDao: AccountDao, cardLast4: String?): Long? {
        val last4 = cardLast4?.trim()?.takeLast(4).orEmpty()
        if (last4.length != 4 || !last4.all { it.isDigit() }) return null
        return resolveCard(accountDao, last4, null)
    }

    private suspend fun resolveCard(
        accountDao: AccountDao,
        last4: String,
        bankHint: String?
    ): Long {
        accountDao.getByLast4AndType(last4, AccountEntity.TYPE_CREDIT_CARD)?.let { existing ->
            touchSeen(accountDao, existing, bankHint)
            return existing.id
        }
        val hint = bankHint?.trim().orEmpty()
        val label = if (hint.isNotBlank()) "$hint Card XX$last4" else "Card XX$last4"
        return accountDao.insert(
            AccountEntity(
                name = label,
                type = AccountEntity.TYPE_CREDIT_CARD,
                balance = 0.0,
                creditLimit = 0.0,
                last4 = last4,
                bankHint = hint,
                seenCount = 1
            )
        )
    }

    private suspend fun resolveBank(
        accountDao: AccountDao,
        last4: String,
        bankHint: String?
    ): Long {
        accountDao.getByLast4AndType(last4, AccountEntity.TYPE_BANK)?.let { existing ->
            touchSeen(accountDao, existing, bankHint)
            return existing.id
        }
        val hint = bankHint?.trim().orEmpty()
        val label = if (hint.isNotBlank()) "$hint A/c XX$last4" else "A/c XX$last4"
        // Older imports stored every XX#### mask as a credit card, including savings accounts.
        val mislabeled = accountDao.getByLast4AndType(last4, AccountEntity.TYPE_CREDIT_CARD)
        if (mislabeled != null && isAutoNamedCard(mislabeled, last4)) {
            accountDao.update(
                mislabeled.copy(
                    name = label,
                    type = AccountEntity.TYPE_BANK,
                    bankHint = if (mislabeled.bankHint.isBlank()) hint else mislabeled.bankHint,
                    seenCount = mislabeled.seenCount + 1
                )
            )
            return mislabeled.id
        }
        return accountDao.insert(
            AccountEntity(
                name = label,
                type = AccountEntity.TYPE_BANK,
                balance = 0.0,
                last4 = last4,
                bankHint = hint,
                seenCount = 1
            )
        )
    }

    private fun isAutoNamedCard(account: AccountEntity, last4: String): Boolean {
        val name = account.name.trim()
        return name == "Card XX$last4" || name.endsWith(" Card XX$last4")
    }

    private suspend fun touchSeen(accountDao: AccountDao, existing: AccountEntity, bankHint: String?) {
        val hint = bankHint?.trim().orEmpty()
        val mergedHint = when {
            existing.bankHint.isBlank() && hint.isNotBlank() -> hint
            else -> existing.bankHint
        }
        if (mergedHint != existing.bankHint || existing.seenCount == 0) {
            accountDao.update(
                existing.copy(
                    bankHint = mergedHint,
                    seenCount = existing.seenCount + 1
                )
            )
        } else {
            accountDao.update(existing.copy(seenCount = existing.seenCount + 1))
        }
    }

    /**
     * Pure balance delta for a ledger transaction.
     * Credit cards: expenses raise outstanding; income / repayments lower it.
     * Bank / cash: income raises balance; expenses lower it.
     * TRANSFER types return 0 — use [applyTransfer] / [transferSideDelta].
     */
    fun txnBalanceDelta(
        isCreditCard: Boolean,
        type: String,
        category: String,
        amount: Double,
        reverse: Boolean = false
    ): Double {
        if (amount <= 0) return 0.0
        if (TransactionAccounting.isTransferType(type)) return 0.0
        var delta = when {
            isCreditCard -> when {
                type.equals("EXPENSE", ignoreCase = true) -> amount
                type.equals("INCOME", ignoreCase = true) -> -amount
                category.contains("credit card payment", ignoreCase = true) -> -amount
                else -> 0.0
            }
            else -> when {
                type.equals("INCOME", ignoreCase = true) -> amount
                type.equals("EXPENSE", ignoreCase = true) -> -amount
                else -> 0.0
            }
        }
        if (reverse) delta = -delta
        return delta
    }

    /** Pure delta for one side of a transfer (from or to). */
    fun transferSideDelta(
        isCreditCard: Boolean,
        isFrom: Boolean,
        amount: Double,
        reverse: Boolean = false
    ): Double {
        if (amount <= 0) return 0.0
        var delta = if (isCreditCard) {
            if (isFrom) amount else -amount
        } else {
            if (isFrom) -amount else amount
        }
        if (reverse) delta = -delta
        return delta
    }

    /**
     * Apply a transaction to the account balance.
     * Credit cards: expenses raise outstanding; income / repayments lower it.
     * Bank / cash: income raises balance; expenses lower it.
     * TRANSFER on a single [accountId] is a no-op — use [applyTransfer].
     */
    suspend fun applyTxnDelta(
        accountDao: AccountDao,
        accountId: Long?,
        type: String,
        category: String,
        amount: Double,
        reverse: Boolean = false
    ) {
        if (accountId == null || amount <= 0) return
        if (TransactionAccounting.isTransferType(type)) return
        val acc = accountDao.getById(accountId) ?: return
        val delta = txnBalanceDelta(acc.isCreditCard, type, category, amount, reverse)
        if (delta == 0.0) return
        val next = if (acc.isCreditCard) {
            (acc.balance + delta).coerceAtLeast(0.0)
        } else {
            acc.balance + delta
        }
        accountDao.update(acc.copy(balance = next))
    }

    /**
     * Move [amount] from [fromAccountId] to [toAccountId].
     * Bank/cash: from decreases, to increases.
     * Credit card as destination: outstanding decreases (repayment).
     * Credit card as source: outstanding increases.
     */
    suspend fun applyTransfer(
        accountDao: AccountDao,
        fromAccountId: Long?,
        toAccountId: Long?,
        amount: Double,
        reverse: Boolean = false
    ) {
        if (amount <= 0) return
        if (fromAccountId != null) {
            applyTransferSide(accountDao, fromAccountId, isFrom = true, amount, reverse)
        }
        if (toAccountId != null) {
            applyTransferSide(accountDao, toAccountId, isFrom = false, amount, reverse)
        }
    }

    private suspend fun applyTransferSide(
        accountDao: AccountDao,
        accountId: Long,
        isFrom: Boolean,
        amount: Double,
        reverse: Boolean
    ) {
        val acc = accountDao.getById(accountId) ?: return
        val delta = transferSideDelta(acc.isCreditCard, isFrom, amount, reverse)
        if (delta == 0.0) return
        val next = if (acc.isCreditCard) {
            (acc.balance + delta).coerceAtLeast(0.0)
        } else {
            acc.balance + delta
        }
        accountDao.update(acc.copy(balance = next))
    }

    /** Undo / redo ledger impact for any stored transaction row. */
    suspend fun applyEntityDelta(
        accountDao: AccountDao,
        tx: TransactionEntity,
        reverse: Boolean = false
    ) {
        if (TransactionAccounting.isTransferType(tx.type)) {
            applyTransfer(
                accountDao,
                tx.accountId,
                tx.transferToAccountId,
                tx.amount,
                reverse = reverse
            )
        } else {
            applyTxnDelta(
                accountDao,
                tx.accountId,
                tx.type,
                tx.category,
                tx.amount,
                reverse = reverse
            )
        }
    }

    /**
     * Bank available-balance SMS is the cash figure to show.
     * A later message replaces an earlier one. Card limits do not overwrite cash.
     */
    fun adoptSmsAsCashBalance(
        lastObservedAt: Long,
        smsAt: Long,
        kind: BalanceKind,
        isCreditCard: Boolean
    ): Boolean {
        if (kind != BalanceKind.AVAILABLE || isCreditCard) return false
        return lastObservedAt == 0L || smsAt >= lastObservedAt
    }

    /**
     * Compare SMS-reported balance to current ledger and store a running difference.
     * For a bank account, the newest "available / new balance" figure becomes the
     * balance shown in the app. Month income is unchanged — a salary dated last
     * month still belongs to last month.
     */
    suspend fun recordReportedBalance(
        accountDao: AccountDao,
        observationDao: BalanceObservationDao,
        accountId: Long?,
        parsed: ParsedSms,
        transactionId: Long?
    ) {
        if (accountId == null) return
        val reported = parsed.reportedBalance ?: return
        val kind = parsed.reportedBalanceKind ?: return
        val hash = parsed.smsHash
        if (hash.isNotBlank() && observationDao.countByHash(hash) > 0) return

        var acc = accountDao.getById(accountId) ?: return
        if (shouldReclassAsBank(accountDao, acc, parsed, kind)) {
            val hint = parsed.bankHint?.trim().orEmpty()
            val label = if (hint.isNotBlank()) "$hint A/c XX${acc.last4}" else "A/c XX${acc.last4}"
            acc = acc.copy(
                type = AccountEntity.TYPE_BANK,
                name = label,
                bankHint = if (acc.bankHint.isBlank()) hint else acc.bankHint
            )
        }

        val ledgerComparable = ledgerComparable(acc, kind)
        val difference = reported - ledgerComparable
        val adopt = adoptSmsAsCashBalance(
            acc.lastBalanceObservedAt,
            parsed.dateTimestamp,
            kind,
            acc.isCreditCard
        )
        val newerReport = acc.lastBalanceObservedAt == 0L ||
            parsed.dateTimestamp >= acc.lastBalanceObservedAt

        observationDao.insert(
            BalanceObservationEntity(
                accountId = acc.id,
                reportedBalance = reported,
                balanceKind = kind.name,
                ledgerBalance = if (adopt) reported else ledgerComparable,
                difference = if (adopt) 0.0 else difference,
                smsHash = hash,
                transactionId = transactionId,
                observedAt = parsed.dateTimestamp
            )
        )

        accountDao.update(
            acc.copy(
                balance = if (adopt) reported else acc.balance,
                lastReportedBalance = if (newerReport) reported else acc.lastReportedBalance,
                lastReportedKind = if (newerReport) kind.name else acc.lastReportedKind,
                runningDifference = if (adopt) 0.0 else if (newerReport) difference else acc.runningDifference,
                observationCount = acc.observationCount + 1,
                lastBalanceObservedAt = if (newerReport) parsed.dateTimestamp else acc.lastBalanceObservedAt,
                creditLimit = maybeUpdateCreditLimit(acc, kind, reported)
            )
        )
    }

    private suspend fun shouldReclassAsBank(
        accountDao: AccountDao,
        acc: AccountEntity,
        parsed: ParsedSms,
        kind: BalanceKind
    ): Boolean {
        if (!acc.isCreditCard || kind != BalanceKind.AVAILABLE) return false
        val ac = parsed.accountLast4 ?: return false
        if (ac != acc.last4 || !parsed.cardLast4.isNullOrBlank()) return false
        if (!isAutoNamedCard(acc, ac)) return false
        return accountDao.getByLast4AndType(ac, AccountEntity.TYPE_BANK) == null
    }

    private fun ledgerComparable(acc: AccountEntity, kind: BalanceKind): Double {
        return when (kind) {
            BalanceKind.AVAILABLE -> acc.balance
            BalanceKind.OUTSTANDING -> if (acc.isCreditCard) acc.balance else acc.balance
            BalanceKind.AVAILABLE_LIMIT -> if (acc.isCreditCard) acc.availableCredit else acc.balance
        }
    }

    private fun maybeUpdateCreditLimit(
        acc: AccountEntity,
        kind: BalanceKind,
        reported: Double
    ): Double {
        if (!acc.isCreditCard) return acc.creditLimit
        if (kind != BalanceKind.AVAILABLE_LIMIT) return acc.creditLimit
        // Infer limit ≈ available + outstanding when we only have available limit.
        val inferred = reported + acc.balance
        return if (acc.creditLimit <= 0) inferred.coerceAtLeast(reported) else acc.creditLimit
    }

    suspend fun knownInstruments(accountDao: AccountDao): List<SmsTrustEvaluator.KnownInstrument> {
        return accountDao.getAllList()
            .filter { it.last4.length == 4 }
            .map {
                SmsTrustEvaluator.KnownInstrument(
                    last4 = it.last4,
                    kind = it.type,
                    bankHint = it.bankHint,
                    observationCount = it.observationCount
                )
            }
    }

    fun formatDifference(diff: Double): String {
        val sign = if (diff > 0) "+" else ""
        return "$sign${"%.2f".format(Locale.US, diff)}"
    }
}
