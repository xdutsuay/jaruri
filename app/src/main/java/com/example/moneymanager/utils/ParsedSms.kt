package com.example.moneymanager.utils

/**
 * Result of parsing a single SMS body into transaction fields.
 * Pure data — no Android dependencies.
 */
data class ParsedSms(
    val amount: Double? = null,
    /** true = INCOME, false = EXPENSE, null = unknown */
    val isIncome: Boolean? = null,
    val description: String? = null,
    val modeOfPayment: String = "SMS",
    val remarks: String = "",
    /** Epoch millis when a date was found or [fallbackNow] was used. */
    val dateTimestamp: Long = System.currentTimeMillis(),
    /** Stable id derived from SMS body for light dedup (stored in memo). */
    val smsHash: String = "",
    /** Last 4 digits of a credit card when detected. */
    val cardLast4: String? = null,
    /** Last 4 digits of a bank account mask when detected (A/c XX####). */
    val accountLast4: String? = null,
    /**
     * Balance / limit figure reported in the SMS (not the transaction amount).
     * Interpreted by [reportedBalanceKind].
     */
    val reportedBalance: Double? = null,
    /** How to interpret [reportedBalance]. */
    val reportedBalanceKind: BalanceKind? = null,
    /** Free-text bank / issuer hint extracted from body (not a fixed bank list). */
    val bankHint: String? = null,
    /** UPI VPA extracted from body (merchant@oksbi etc.). */
    val vpa: String? = null,
    /** UPI Ref / txn id when present. */
    val upiRef: String? = null,
    /** RRN when present. */
    val rrn: String? = null,
    /** True when body looks like a refund/reversal of a prior debit. */
    val isRefundOrReversal: Boolean = false
) {
    val isComplete: Boolean
        get() = amount != null && amount > 0 && isIncome != null

    /** Prefer card mask, else account mask — used for instrument linking. */
    val instrumentLast4: String?
        get() = cardLast4 ?: accountLast4

    fun typeLabel(): String = if (isIncome == true) "INCOME" else "EXPENSE"

    /** Memo suitable for [com.example.moneymanager.data.TransactionEntity.memo]. */
    fun toMemo(): String {
        val desc = description?.takeIf { it.isNotBlank() } ?: "SMS transaction"
        val cardTag = cardLast4?.let { " · Card XX$it" }.orEmpty()
        val acTag = if (cardLast4 == null) {
            accountLast4?.let { " · A/c XX$it" }.orEmpty()
        } else {
            ""
        }
        val balTag = reportedBalance?.let { bal ->
            val kind = reportedBalanceKind?.memoLabel() ?: "Bal"
            " · $kind ${"%.2f".format(bal)}"
        }.orEmpty()
        val snippet = remarks.removePrefix("SMS: ").trim()
        val hashTag = if (smsHash.isNotEmpty()) " [sms:$smsHash]" else ""
        return "$desc$cardTag$acTag$balTag · $modeOfPayment$hashTag | SMS: $snippet".take(500)
    }
}

/** Semantic meaning of a balance figure found in an SMS. */
enum class BalanceKind {
    /** Available / closing / current bank balance. */
    AVAILABLE,
    /** Credit-card outstanding / amount owed. */
    OUTSTANDING,
    /** Remaining credit-card limit. */
    AVAILABLE_LIMIT;

    fun memoLabel(): String = when (this) {
        AVAILABLE -> "Avl"
        OUTSTANDING -> "Due"
        AVAILABLE_LIMIT -> "Limit"
    }
}
