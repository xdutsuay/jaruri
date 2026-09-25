package com.example.moneymanager.utils

import android.content.Context
import android.util.Log
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first

/**
 * One-shot fix: re-derive categories from existing SMS memos using current
 * [SmsCategorizer] rules (payment mode stays in memo; category = purpose).
 */
object SmsCategoryBackfill {

    private const val TAG = "SmsCategoryBackfill"

    suspend fun runOnce(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (settings.smsCategoryBackfillDone.first()) return

        val dao = AppDatabase.getDatabase(app).transactionDao()
        val all = dao.getAllTransactions().first()

        var changed = 0
        for (tx in all) {
            if (!tx.memo.contains("[sms:")) continue
            val mode = modeFromMemo(tx.memo)
            val parsed = ParsedSms(
                amount = tx.amount,
                isIncome = tx.type == "INCOME",
                description = tx.memo.substringBefore("·").trim().ifBlank { null },
                modeOfPayment = mode,
                remarks = tx.memo,
                dateTimestamp = tx.dateTimestamp,
                smsHash = ""
            )
            val newCat = SmsCategorizer.categorize(parsed, tx.memo)
            if (newCat != tx.category) {
                dao.updateTransaction(tx.copy(category = newCat))
                changed++
            }
        }
        settings.setSmsCategoryBackfillDone(true)
        Log.i(TAG, "RESULT changed=$changed total=${all.size}")
    }

    private fun modeFromMemo(memo: String): String = when {
        memo.contains("· Credit Card") -> "Credit Card"
        memo.contains("· Bank") -> "Bank"
        memo.contains("· UPI") -> "UPI"
        else -> "SMS"
    }
}
