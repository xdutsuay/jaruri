package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountDao
import com.example.moneymanager.data.AccountEntity

/**
 * Links SMS / ledger rows to credit-card accounts and keeps outstanding debt in sync.
 */
object CreditCardLedger {

    suspend fun resolveOrCreateCard(accountDao: AccountDao, cardLast4: String?): Long? {
        val last4 = cardLast4?.trim()?.takeLast(4).orEmpty()
        if (last4.length != 4 || !last4.all { it.isDigit() }) return null
        accountDao.getCreditCardByLast4(last4)?.let { return it.id }
        return accountDao.insert(
            AccountEntity(
                name = "Card XX$last4",
                type = AccountEntity.TYPE_CREDIT_CARD,
                balance = 0.0,
                creditLimit = 0.0,
                last4 = last4
            )
        )
    }

    /**
     * Spend increases outstanding; repayments / income linked to the card decrease it.
     */
    suspend fun applyDebtDelta(
        accountDao: AccountDao,
        accountId: Long?,
        type: String,
        category: String,
        amount: Double,
        reverse: Boolean = false
    ) {
        if (accountId == null || amount <= 0) return
        val acc = accountDao.getById(accountId) ?: return
        if (!acc.isCreditCard) return
        var delta = when {
            type.equals("EXPENSE", ignoreCase = true) -> amount
            type.equals("INCOME", ignoreCase = true) -> -amount
            category.contains("credit card payment", ignoreCase = true) -> -amount
            else -> 0.0
        }
        if (reverse) delta = -delta
        if (delta == 0.0) return
        accountDao.update(acc.copy(balance = (acc.balance + delta).coerceAtLeast(0.0)))
    }
}
