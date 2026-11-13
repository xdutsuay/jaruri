package com.example.moneymanager.quickadd

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure validation for the quick expense sheet (no Android types, unit-tested). */
object QuickExpenseInput {
    const val MAX_AMOUNT = 99_999_999.99
    const val MAX_NOTE = 200

    enum class AmountError { EMPTY, INVALID, ZERO, TOO_LARGE }

    sealed interface Amount {
        data class Ok(val value: Double) : Amount
        data class Bad(val error: AmountError) : Amount
    }

    /**
     * Accepts what people type on a phone keypad: "50", "50.5", "1,200", " 99 ".
     * At most two decimals; a comma is a grouping separator, never a decimal point.
     */
    fun parseAmount(text: String?): Amount {
        val t = text.orEmpty().trim().replace(",", "").replace(" ", "")
        if (t.isEmpty()) return Amount.Bad(AmountError.EMPTY)
        if (!Regex("""\d+(\.\d{0,2})?|\.\d{1,2}""").matches(t)) return Amount.Bad(AmountError.INVALID)
        val v = BigDecimal(if (t.startsWith(".")) "0$t" else t.removeSuffix(".")).setScale(2, RoundingMode.UNNECESSARY)
        if (v.signum() == 0) return Amount.Bad(AmountError.ZERO)
        if (v > BigDecimal.valueOf(MAX_AMOUNT)) return Amount.Bad(AmountError.TOO_LARGE)
        return Amount.Ok(v.toDouble())
    }

    /** Trimmed, single-line, capped note. */
    fun cleanNote(note: String?): String =
        note.orEmpty().replace(Regex("""\s+"""), " ").trim().take(MAX_NOTE)

    /** Last-used category if it still exists, else the first one, else null. */
    fun pickCategory(names: List<String>, lastUsed: String?): String? =
        lastUsed?.takeIf { it in names } ?: names.firstOrNull()
}
