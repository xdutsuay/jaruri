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
    val cardLast4: String? = null
) {
    val isComplete: Boolean
        get() = amount != null && amount > 0 && isIncome != null

    fun typeLabel(): String = if (isIncome == true) "INCOME" else "EXPENSE"

    /** Memo suitable for [com.example.moneymanager.data.TransactionEntity.memo]. */
    fun toMemo(): String {
        val desc = description?.takeIf { it.isNotBlank() } ?: "SMS transaction"
        val cardTag = cardLast4?.let { " · Card XX$it" }.orEmpty()
        val snippet = remarks.removePrefix("SMS: ").trim()
        val hashTag = if (smsHash.isNotEmpty()) " [sms:$smsHash]" else ""
        return "$desc$cardTag · $modeOfPayment$hashTag | SMS: $snippet".take(500)
    }
}
