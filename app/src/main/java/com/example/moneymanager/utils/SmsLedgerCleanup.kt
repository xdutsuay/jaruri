package com.example.moneymanager.utils

import android.content.Context
import android.util.Log
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * One-shot ledger hygiene after parser/categorizer improvements:
 * 1) Soft-delete autopay / e-mandate / schedule notices that were wrongly imported
 * 2) Re-categorize SMS rows whose category is still a payment-mode label
 * 3) Flag remaining unlearned merchants for categorize-later review
 */
object SmsLedgerCleanup {

    private const val TAG = "SmsLedgerCleanup"

    /** Categories that are really payment modes from older imports. */
    private val MODE_AS_CATEGORY = setOf(
        "credit card", "bank", "upi", "sms", "cash", "card"
    )

    suspend fun runOnce(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (settings.smsLedgerCleanupV3Done.first()) return

        val db = AppDatabase.getDatabase(app)
        val dao = db.transactionDao()
        val learnDao = db.categoryLearnDao()
        val categories = CategoryRepository(app)
        val all = dao.getAllActiveList()

        var removed = 0
        var recategorized = 0
        var flagged = 0

        for (tx in all) {
            if (!tx.memo.contains("[sms:") && !tx.memo.contains("| SMS:")) continue
            val body = smsBodyFromMemo(tx.memo)

            if (SmsParser.isNonLedgerNotice(body) || SmsParser.isPromotional(body)) {
                dao.softDelete(tx.id)
                removed++
                continue
            }

            val mode = modeFromMemo(tx.memo)
            val desc = descFromMemo(tx.memo)
            val learnKey = CategoryLearning.keyFromDescription(desc, tx.memo)
            val learned = learnKey?.let { learnDao.get(it)?.category }

            val parsed = ParsedSms(
                amount = tx.amount,
                isIncome = tx.type.equals("INCOME", ignoreCase = true),
                description = desc,
                modeOfPayment = mode,
                remarks = tx.memo,
                dateTimestamp = tx.dateTimestamp,
                smsHash = ""
            )
            val suggested = SmsCategorizer.categorize(
                parsed,
                body,
                learned,
                db.categoryRuleDao().getEnabledList()
            )
            val catIsMode = MODE_AS_CATEGORY.contains(tx.category.trim().lowercase())
            val shouldReplace = catIsMode || !learned.isNullOrBlank()

            var next = tx
            if (shouldReplace && suggested != tx.category) {
                next = next.copy(category = suggested)
                recategorized++
                categories.addCategory(
                    suggested,
                    if (tx.type.equals("INCOME", ignoreCase = true)) {
                        CategoryRepository.TYPE_INCOME
                    } else {
                        CategoryRepository.TYPE_EXPENSE
                    }
                )
            }

            val needsReview = SmsCategorizer.needsCategoryReview(next.category, learned)
            if (needsReview != next.needsCategoryReview) {
                next = next.copy(needsCategoryReview = needsReview)
                if (needsReview) flagged++
            }

            if (next != tx) {
                dao.updateTransaction(next)
            }
        }

        settings.setSmsLedgerCleanupV3Done(true)
        Log.i(
            TAG,
            "RESULT removed=$removed recategorized=$recategorized flagged=$flagged total=${all.size}"
        )
    }

    fun smsBodyFromMemo(memo: String): String {
        val marker = "| SMS:"
        val idx = memo.indexOf(marker)
        return if (idx >= 0) memo.substring(idx + marker.length).trim() else memo
    }

    fun descFromMemo(memo: String): String? {
        val head = memo.substringBefore("·").substringBefore("|").trim()
        return head.takeIf { it.isNotBlank() }
    }

    fun modeFromMemo(memo: String): String = when {
        memo.contains("· Credit Card", ignoreCase = true) -> "Credit Card"
        memo.contains("· Bank", ignoreCase = true) -> "Bank"
        memo.contains("· UPI", ignoreCase = true) -> "UPI"
        else -> "SMS"
    }
}
