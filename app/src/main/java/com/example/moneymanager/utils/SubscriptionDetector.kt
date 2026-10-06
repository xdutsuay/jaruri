package com.example.moneymanager.utils

import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.data.TransactionEntity
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Heuristic detection of monthly subscriptions from existing ledger rows.
 * Same merchant key + similar amount + ~monthly cadence (≥2 spaced charges).
 */
object SubscriptionDetector {

    data class Suggestion(
        val merchantKey: String,
        val displayName: String,
        val amount: Double,
        val category: String,
        val type: String,
        val hitCount: Int,
        val lastTimestamp: Long,
        val accountId: Long?
    ) {
        fun toRecurring(nextDue: Long = defaultNextDue(lastTimestamp)): RecurringEntity =
            RecurringEntity(
                type = type,
                category = category,
                amount = amount,
                memo = displayName,
                frequency = "MONTH",
                nextDueTimestamp = nextDue,
                accountId = accountId,
                active = true
            )
    }

    /** Amount may differ by this fraction (e.g. 0.08 = 8%) and still match. */
    private const val AMOUNT_TOLERANCE = 0.08

    /** Accept gaps of ~20–40 days as monthly. */
    private const val MIN_GAP_DAYS = 20
    private const val MAX_GAP_DAYS = 40

    fun detect(
        transactions: List<TransactionEntity>,
        existingRecurring: List<RecurringEntity> = emptyList()
    ): List<Suggestion> {
        val candidates = transactions.filter { tx ->
            !TransactionAccounting.isTransferType(tx.type) &&
                tx.category.trim().lowercase(Locale.ROOT) !=
                TransactionAccounting.CAT_CC_PAYMENT.lowercase(Locale.ROOT) &&
                tx.amount > 0
        }

        val byMerchant = candidates.groupBy { merchantKey(it) }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        val existingKeys = existingRecurring.mapNotNull {
            normalizeKey(it.memo.ifBlank { it.category })
        }.toSet()

        val out = mutableListOf<Suggestion>()
        for ((key, rows) in byMerchant) {
            if (key in existingKeys) continue
            val sorted = rows.sortedBy { it.dateTimestamp }
            val clusters = clusterByAmount(sorted)
            for (cluster in clusters) {
                if (cluster.size < 2) continue
                if (!hasMonthlyCadence(cluster)) continue
                val sample = cluster.last()
                val avgAmount = cluster.map { it.amount }.average()
                out += Suggestion(
                    merchantKey = key,
                    displayName = displayName(sample),
                    amount = kotlin.math.round(avgAmount * 100.0) / 100.0,
                    category = sample.category.ifBlank { "Others" },
                    type = if (sample.type.equals("INCOME", true)) "INCOME" else "EXPENSE",
                    hitCount = cluster.size,
                    lastTimestamp = sample.dateTimestamp,
                    accountId = sample.accountId
                )
            }
        }
        return out.sortedByDescending { it.hitCount }
    }

    private fun merchantKey(tx: TransactionEntity): String? {
        val fromMemo = CategoryLearning.keyFromDescription(null, tx.memo)
        if (fromMemo != null) return fromMemo
        val cat = tx.category.trim().lowercase(Locale.ROOT)
        if (cat.length >= 3 && cat !in setOf("others", "transfer")) return cat
        return null
    }

    private fun normalizeKey(raw: String): String? =
        CategoryLearning.keyFromDescription(raw, raw)

    private fun displayName(tx: TransactionEntity): String {
        val head = tx.memo.substringBefore("·").substringBefore("|").trim()
        return head.ifBlank { tx.category }.take(48)
    }

    private fun clusterByAmount(sorted: List<TransactionEntity>): List<List<TransactionEntity>> {
        val used = BooleanArray(sorted.size)
        val clusters = mutableListOf<List<TransactionEntity>>()
        for (i in sorted.indices) {
            if (used[i]) continue
            val base = sorted[i].amount
            val group = mutableListOf(sorted[i])
            used[i] = true
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                if (amountsSimilar(base, sorted[j].amount)) {
                    group += sorted[j]
                    used[j] = true
                }
            }
            clusters += group
        }
        return clusters
    }

    fun amountsSimilar(a: Double, b: Double, tolerance: Double = AMOUNT_TOLERANCE): Boolean {
        val mid = (a + b) / 2.0
        if (mid <= 0) return false
        return abs(a - b) / mid <= tolerance
    }

    fun hasMonthlyCadence(sortedAsc: List<TransactionEntity>): Boolean {
        if (sortedAsc.size < 2) return false
        val gaps = mutableListOf<Long>()
        for (i in 1 until sortedAsc.size) {
            val days = (sortedAsc[i].dateTimestamp - sortedAsc[i - 1].dateTimestamp) /
                (24L * 60L * 60L * 1000L)
            gaps += days
        }
        val monthlyGaps = gaps.count { it in MIN_GAP_DAYS..MAX_GAP_DAYS }
        // At least one monthly-ish gap, and majority of gaps in range when ≥3 hits
        return if (sortedAsc.size == 2) {
            monthlyGaps == 1
        } else {
            monthlyGaps >= (gaps.size + 1) / 2
        }
    }

    fun defaultNextDue(lastTimestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastTimestamp
        cal.add(Calendar.MONTH, 1)
        return cal.timeInMillis
    }
}
