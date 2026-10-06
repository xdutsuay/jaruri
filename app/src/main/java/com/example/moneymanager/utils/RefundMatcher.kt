package com.example.moneymanager.utils

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Detects refund/reversal SMS and matches them to original expenses.
 *
 * Accounting rule (see [TransactionAccounting.isNeutralForTotals]):
 * A matched refund is stored as INCOME with [com.example.moneymanager.data.TransactionEntity.isRefundNeutral]
 * = true so account balance still receives the credit, but income/expense totals do **not**
 * count the refund as income (avoids double-counting). The original EXPENSE stays as expense;
 * net cash is reflected on the account balance, not in the income headline.
 */
object RefundMatcher {

    private val UPI_REF = Regex(
        """(?:upi\s*(?:ref(?:erence)?|txn(?:\s*id)?|transaction\s*id)|ref(?:erence)?(?:\s*no\.?)?)\s*[:\-]?\s*([A-Za-z0-9]{6,24})""",
        RegexOption.IGNORE_CASE
    )
    private val RRN = Regex(
        """(?:\brrn\b|retrieval\s*ref(?:erence)?(?:\s*no\.?)?)\s*[:\-]?\s*([A-Za-z0-9]{6,24})""",
        RegexOption.IGNORE_CASE
    )
    private val TXN_ID = Regex(
        """(?:txn\s*id|transaction\s*id|xtn)\s*[:\-]?\s*([A-Za-z0-9]{6,24})""",
        RegexOption.IGNORE_CASE
    )

    data class Refs(val upiRef: String?, val rrn: String?)

    data class MatchCandidate(
        val id: Long,
        val amount: Double,
        val type: String,
        val dateTimestamp: Long,
        val payeeId: Long?,
        val upiRef: String?,
        val rrn: String?,
        val memo: String
    )

    fun extractRefs(text: String): Refs {
        val upi = UPI_REF.find(text)?.groupValues?.get(1)
            ?: TXN_ID.find(text)?.groupValues?.get(1)
        val rrn = RRN.find(text)?.groupValues?.get(1)
        return Refs(
            upiRef = upi?.trim()?.takeIf { it.isNotBlank() },
            rrn = rrn?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * True when SMS language indicates a refund, reversal, failed debit credit-back, etc.
     */
    fun isRefundOrReversal(text: String, isIncome: Boolean?): Boolean {
        val t = text.lowercase(Locale.ROOT)
        val refundWords = listOf(
            "refund", "refunded", "reversed", "reversal", "failed",
            "transaction failed", "txn failed", "has been reversed",
            "debited amount credited", "amount credited back", "credited back"
        )
        if (refundWords.any { t.contains(it) }) return true
        // "credite" typo / truncated "credited" in reverse-of-debit context
        if (t.contains("credite") && (t.contains("reverse") || t.contains("fail") ||
                t.contains("refund") || t.contains("debit"))
        ) {
            return true
        }
        // Credit that explicitly reverses a prior debit
        if (isIncome == true && (t.contains("against") && t.contains("debit") ||
                t.contains("towards reversal"))
        ) {
            return true
        }
        return false
    }

    /**
     * Find the best original EXPENSE to link.
     * Priority: matching UPI Ref / RRN, else same amount + same payee within [windowMillis].
     */
    fun findOriginalExpense(
        candidates: List<MatchCandidate>,
        amount: Double,
        payeeId: Long?,
        refs: Refs,
        refundAt: Long,
        windowMillis: Long = TimeUnit.DAYS.toMillis(14)
    ): MatchCandidate? {
        val expenses = candidates.filter {
            it.type.equals("EXPENSE", ignoreCase = true) && it.amount > 0
        }
        if (expenses.isEmpty()) return null

        val refKeys = listOfNotNull(refs.upiRef, refs.rrn)
            .map { it.lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        if (refKeys.isNotEmpty()) {
            val byRef = expenses.firstOrNull { exp ->
                val expRefs = listOfNotNull(exp.upiRef, exp.rrn)
                    .map { it.lowercase(Locale.ROOT) }
                refKeys.any { key -> expRefs.any { it == key || it.contains(key) || key.contains(it) } } ||
                    refKeys.any { key -> exp.memo.contains(key, ignoreCase = true) }
            }
            if (byRef != null) return byRef
        }

        val amountTol = 0.01
        val windowed = expenses.filter { exp ->
            kotlin.math.abs(exp.amount - amount) <= amountTol &&
                kotlin.math.abs(exp.dateTimestamp - refundAt) <= windowMillis &&
                (payeeId == null || exp.payeeId == null || exp.payeeId == payeeId)
        }
        if (windowed.isEmpty()) return null
        // Prefer same payee, then closest in time
        return windowed.sortedWith(
            compareByDescending<MatchCandidate> { it.payeeId != null && it.payeeId == payeeId }
                .thenBy { kotlin.math.abs(it.dateTimestamp - refundAt) }
        ).firstOrNull()
    }
}
