package com.example.moneymanager.utils

import java.util.Locale

/**
 * Normalizes merchant / description text into a stable learning key.
 */
object CategoryLearning {
    fun keyFromDescription(description: String?, memo: String = ""): String? {
        val raw = when {
            !description.isNullOrBlank() -> description
            else -> memo.substringBefore("·").substringBefore("|").trim()
        }
        val cleaned = raw.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.length < 3) return null
        if (cleaned in IGNORED) return null
        return cleaned.take(48)
    }

    private val IGNORED = setOf(
        "sms transaction", "bank transaction", "credit card transaction",
        "upi transaction", "others", "unknown"
    )
}
