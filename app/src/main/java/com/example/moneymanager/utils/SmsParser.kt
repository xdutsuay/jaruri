package com.example.moneymanager.utils

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

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
    val smsHash: String = ""
) {
    val isComplete: Boolean
        get() = amount != null && amount > 0 && isIncome != null

    fun typeLabel(): String = if (isIncome == true) "INCOME" else "EXPENSE"

    /** Memo suitable for [com.example.moneymanager.data.TransactionEntity.memo]. */
    fun toMemo(): String {
        val desc = description?.takeIf { it.isNotBlank() } ?: "SMS transaction"
        val snippet = remarks.removePrefix("SMS: ").trim()
        val hashTag = if (smsHash.isNotEmpty()) " [sms:$smsHash]" else ""
        return "$desc · $modeOfPayment$hashTag | SMS: $snippet".take(500)
    }
}

/**
 * Pure Kotlin parser for common Indian bank / UPI SMS messages.
 */
object SmsParser {

    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile(
            """(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            """([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)""",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            """(?:amount|amt)[:\s]+(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
            Pattern.CASE_INSENSITIVE
        )
    )

    private val MONEY_LOOK = Pattern.compile(
        """(rs\.?\s*|inr\s*|₹)\s*[\d,]+(?:\.\d{1,2})?""",
        Pattern.CASE_INSENSITIVE
    )
    private val VERB_LOOK = Pattern.compile(
        """\b(credited|debited|spent|received|paid|withdrawn|purchase|txn|transaction|upi)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_HINTS = listOf(
        "credited", "received", "has been credited", "credit alert",
        "deposited", "refund", "cashback"
    )
    private val DEBIT_HINTS = listOf(
        "debited", "spent", "paid", "withdrawn", "purchase", "sent",
        "debit alert", "has been debited", "dr "
    )

    /**
     * Parses one SMS body. Returns null if nothing useful can be extracted.
     */
    fun parse(raw: String, fallbackNow: Long = System.currentTimeMillis()): ParsedSms? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val amount = extractAmount(text)
        val isIncome = extractIsIncome(text)
        if (amount == null && isIncome == null) return null

        val mode = extractMode(text)
        val description = extractDescription(text, mode)
        val dateTs = extractDate(text) ?: fallbackNow
        val snippet = if (text.length > 160) text.substring(0, 157) + "..." else text
        val hash = contentHash(text)

        return ParsedSms(
            amount = amount,
            isIncome = isIncome,
            description = description,
            modeOfPayment = mode,
            remarks = "SMS: $snippet",
            dateTimestamp = dateTs,
            smsHash = hash
        )
    }

    /** Splits pasted text into SMS blocks and parses each. */
    fun parseBatch(raw: String, fallbackNow: Long = System.currentTimeMillis()): List<ParsedSms> {
        return splitSmsBlocks(raw).mapNotNull { parse(it, fallbackNow) }
    }

    /** Heuristic: does this look like a financial SMS? */
    fun looksFinancial(raw: String): Boolean {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return false
        val hasMoney = MONEY_LOOK.matcher(t).find()
        val hasVerb = VERB_LOOK.matcher(t).find()
        return hasMoney || (hasVerb && t.any { it.isDigit() })
    }

    /** True if [memo] already contains this SMS hash tag (dedup). */
    fun memoContainsHash(memo: String, smsHash: String): Boolean {
        if (smsHash.isBlank()) return false
        return memo.contains("[sms:$smsHash]", ignoreCase = false)
    }

    fun contentHash(text: String): String {
        // Stable, short, no crypto dependency — good enough for local dedup.
        var h = 1125899906842597L
        for (ch in text.trim()) {
            h = 31 * h + ch.code
        }
        return java.lang.Long.toHexString(h)
    }

    private fun splitSmsBlocks(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val blankSplit = normalized
            .split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (blankSplit.size > 1) return blankSplit

        val lines = normalized
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size > 1 && lines.all { looksFinancial(it) }) {
            return lines
        }

        return listOf(normalized)
    }

    private fun extractAmount(text: String): Double? {
        for (re in AMOUNT_PATTERNS) {
            val m = re.matcher(text)
            if (m.find()) {
                val rawAmt = m.group(1)?.replace(",", "") ?: continue
                val value = rawAmt.toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        return null
    }

    private fun extractIsIncome(text: String): Boolean? {
        val t = text.lowercase(Locale.ROOT)
        val credit = CREDIT_HINTS.any { t.contains(it) }
        val debit = DEBIT_HINTS.any { t.contains(it) }

        if (credit && !debit) return true
        if (debit && !credit) return false
        if (credit && debit) {
            val firstCredit = CREDIT_HINTS
                .map { t.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: Int.MAX_VALUE
            val firstDebit = DEBIT_HINTS
                .map { t.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: Int.MAX_VALUE
            return firstCredit <= firstDebit
        }
        return null
    }

    private fun extractMode(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        if (t.contains("upi") ||
            t.contains("@ybl") ||
            t.contains("@oksbi") ||
            t.contains("@paytm") ||
            t.contains("@apl") ||
            t.contains("gpay") ||
            t.contains("google pay") ||
            t.contains("phonepe") ||
            t.contains("bhim")
        ) {
            return "UPI"
        }
        if (t.contains("a/c") ||
            t.contains("account") ||
            t.contains("bank") ||
            t.contains("neft") ||
            t.contains("imps") ||
            t.contains("rtgs") ||
            t.contains("atm")
        ) {
            return "Bank"
        }
        return "SMS"
    }

    private fun extractDescription(text: String, mode: String): String {
        val toMatch = Pattern.compile(
            """(?:to|towards|paid to|sent to)\s+([A-Za-z0-9 &._@-]{2,40})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (toMatch.find()) {
            return cleanMerchant(toMatch.group(1)!!)
        }

        val fromMatch = Pattern.compile(
            """(?:from|received from|by)\s+([A-Za-z0-9 &._@-]{2,40})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (fromMatch.find()) {
            return cleanMerchant(fromMatch.group(1)!!)
        }

        val atMatcher = Pattern.compile(
            """(?:at|on)\s+([A-Za-z0-9 &._-]{2,40})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        while (atMatcher.find()) {
            val candidate = cleanMerchant(atMatcher.group(1)!!)
            if (!candidate.first().isDigit() &&
                !Regex("""^\d{1,2}[-/]""").containsMatchIn(candidate) &&
                !Regex(
                    """^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(candidate)
            ) {
                return candidate
            }
        }

        return when (mode) {
            "UPI" -> "UPI transaction"
            "Bank" -> "Bank transaction"
            else -> "SMS transaction"
        }
    }

    private fun cleanMerchant(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("""[,.;:].*$"""), "").trim()
        s = s.replace(
            Regex("""\s+(using|via|on|ref|upi|for|info).*$""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        if (s.length > 40) s = s.substring(0, 40).trim()
        return if (s.isEmpty()) "SMS transaction" else s
    }

    private fun extractDate(text: String): Long? {
        val dmy = Pattern.compile("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""").matcher(text)
        if (dmy.find()) {
            val d = dmy.group(1)?.toIntOrNull()
            val m = dmy.group(2)?.toIntOrNull()
            var y = dmy.group(3)?.toIntOrNull()
            if (d != null && m != null && y != null && m in 1..12 && d in 1..31) {
                if (y < 100) y += 2000
                return calendarMillis(y, m, d)
            }
        }

        val dMon = Pattern.compile(
            """\b(\d{1,2})[-\s](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-\s]?(\d{2,4})\b""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (dMon.find()) {
            val months = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
                "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
                "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
            )
            val d = dMon.group(1)?.toIntOrNull()
            val monKey = dMon.group(2)?.take(3)?.lowercase(Locale.ROOT)
            val m = months[monKey]
            var y = dMon.group(3)?.toIntOrNull()
            if (d != null && m != null && y != null) {
                if (y < 100) y += 2000
                return calendarMillis(y, m, d)
            }
        }

        return null
    }

    private fun calendarMillis(year: Int, month: Int, day: Int): Long? {
        return try {
            val cal = Calendar.getInstance()
            cal.clear()
            cal.set(year, month - 1, day, 12, 0, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
