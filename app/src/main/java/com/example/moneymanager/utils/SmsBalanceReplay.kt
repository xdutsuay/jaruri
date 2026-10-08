package com.example.moneymanager.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Fills account balances from SMS that already say "available balance" / "New Bal".
 * Import used to keep the transaction and drop the balance sentence, and it stored
 * savings accounts as cards. This pass reads the inbox (full text) once, then
 * falls back to the balance text still sitting in transaction memos.
 */
object SmsBalanceReplay {

    private const val TAG = "SmsBalanceReplay"
    private val HASH = Regex("""\[sms:([0-9a-fA-F]+)\]""")

    suspend fun runOnce(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (settings.smsBalanceReplayV1Done.first()) return

        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        val db = AppDatabase.getDatabase(app)
        val txByHash = db.transactionDao().getAllActiveList().mapNotNull { tx ->
            val hash = HASH.find(tx.memo)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            hash to tx.id
        }.toMap()

        var recorded = 0
        if (granted) {
            val inbox = SmsInboxReader.readFinancialSms(app, limit = 2000, maxScan = 8000)
            val withBalance = inbox.mapNotNull { sms ->
                val parsed = SmsParser.parse(sms.body, fallbackNow = sms.dateMillis) ?: return@mapNotNull null
                if (parsed.reportedBalance == null) null else parsed
            }.sortedBy { it.dateTimestamp }
            for (parsed in withBalance) {
                val accountId = InstrumentLedger.resolveOrCreate(db.accountDao(), parsed) ?: continue
                val before = db.accountDao().getById(accountId)?.observationCount ?: 0
                InstrumentLedger.recordReportedBalance(
                    db.accountDao(),
                    db.balanceObservationDao(),
                    accountId,
                    parsed,
                    transactionId = txByHash[parsed.smsHash]
                )
                val after = db.accountDao().getById(accountId)?.observationCount ?: before
                if (after > before) recorded++
            }
        }

        val memoHits = replayMemos(db)
        recorded += memoHits
        val merged = mergeAutoNamedCardsIntoBanks(db)
        Log.i(TAG, "RESULT recorded=$recorded merged=$merged smsPermission=$granted")

        if (granted || recorded > 0 || merged > 0) {
            settings.setSmsBalanceReplayV1Done(true)
        }
    }

    /**
     * A savings account that was stored as "Card XX####" stays around after the
     * real bank row is created. Drop that card once the bank has an SMS balance,
     * and point its transactions at the bank. Safe to call more than once.
     */
    suspend fun mergeAutoNamedCardsIntoBanks(db: AppDatabase): Int {
        val accounts = db.accountDao().getAllList()
        val banks = accounts.filter { it.type == AccountEntity.TYPE_BANK && it.last4.length == 4 }
        var merged = 0
        for (card in accounts) {
            if (card.type != AccountEntity.TYPE_CREDIT_CARD) continue
            if (card.name.trim() != "Card XX${card.last4}") continue
            val bank = banks.find { it.last4 == card.last4 } ?: continue
            if (bank.observationCount <= 0 && bank.lastReportedBalance == null) continue
            db.transactionDao().reassignAccount(card.id, bank.id)
            db.accountDao().delete(card)
            merged++
        }
        return merged
    }

    private suspend fun replayMemos(db: AppDatabase): Int {
        val rows = db.transactionDao().getAllActiveList()
            .filter { it.memo.contains("| SMS:") }
            .sortedBy { it.dateTimestamp }
        var recorded = 0
        for (tx in rows) {
            val body = SmsLedgerCleanup.smsBodyFromMemo(tx.memo)
            // Stored memos used to clip the SMS at 160 characters, which chops
            // "1,52,378.24" into "1,52". Only a complete snippet is safe.
            if (body.endsWith("...") || body.endsWith("…")) continue
            val (reported, kind) = SmsParser.extractReportedBalance(body)
            if (reported == null || kind == null) continue
            val hash = HASH.find(tx.memo)?.groupValues?.getOrNull(1).orEmpty()
            val parsed = ParsedSms(
                amount = tx.amount,
                isIncome = tx.type.equals("INCOME", ignoreCase = true),
                description = SmsLedgerCleanup.descFromMemo(tx.memo),
                modeOfPayment = SmsLedgerCleanup.modeFromMemo(tx.memo),
                remarks = "SMS: $body",
                dateTimestamp = tx.dateTimestamp,
                smsHash = hash,
                cardLast4 = SmsParser.extractCardLast4(body),
                accountLast4 = SmsParser.extractAccountLast4(body),
                reportedBalance = reported,
                reportedBalanceKind = kind,
                bankHint = SmsParser.extractBankHint(body)
            )
            val accountId = tx.accountId
                ?: InstrumentLedger.resolveOrCreate(db.accountDao(), parsed)
                ?: continue
            val before = db.accountDao().getById(accountId)?.observationCount ?: 0
            InstrumentLedger.recordReportedBalance(
                db.accountDao(),
                db.balanceObservationDao(),
                accountId,
                parsed,
                transactionId = tx.id
            )
            val after = db.accountDao().getById(accountId)?.observationCount ?: before
            if (after > before) recorded++
        }
        return recorded
    }
}
