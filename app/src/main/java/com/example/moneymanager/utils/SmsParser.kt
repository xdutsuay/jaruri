package com.example.moneymanager.utils

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure Kotlin parser for common Indian bank / UPI / credit-card SMS messages.
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
        """\b(credited|debited|spent|received|paid|withdrawn|purchase|txn|transaction|upi|outstanding|due)\b""",
        Pattern.CASE_INSENSITIVE
    )
    /** Verbs that indicate an actual money movement (not promo / statement fluff). */
    private val TXN_VERB_LOOK = Pattern.compile(
        """\b(credited|debited|spent|withdrawn|received|paid\s+to|sent\s+to|purchase)\b""",
        Pattern.CASE_INSENSITIVE
    )
    private val BALANCE_BEFORE = Pattern.compile(
        """(?:avl\.?\s*bal(?:ance)?|available\s+balance|avbl\.?\s*limit|available\s+limit|""" +
            """new\s+balance|bal(?:ance)?|limit|outstanding|total\s+due|min(?:imum)?\s+due|""" +
            """closing\s+balance|curr(?:ent)?\s+bal)\s*[:\-]?\s*$""",
        Pattern.CASE_INSENSITIVE
    )

    private val CARD_LAST4 = Pattern.compile(
        """(?:card|xx|ending|x{2,})\s*[xX*]*\s*(\d{4})\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_HINTS = listOf(
        "credited", "received", "has been credited", "credit alert",
        "deposited", "refund", "payment received", "payment of"
        // note: bare "cashback" removed — too common in promo SMS
    )
    private val DEBIT_HINTS = listOf(
        "debited", "spent", "paid", "withdrawn", "purchase", "sent",
        "debit alert", "has been debited", "dr ", "swiped", "charged"
    )

    fun parse(raw: String, fallbackNow: Long = System.currentTimeMillis()): ParsedSms? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (isPromotional(text)) return null

        val amount = extractAmount(text)
        val isIncome = extractIsIncome(text)
        if (amount == null && isIncome == null) return null

        val mode = extractMode(text)
        val cardLast4 = extractCardLast4(text)
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
            smsHash = hash,
            cardLast4 = cardLast4
        )
    }

    fun parseBatch(raw: String, fallbackNow: Long = System.currentTimeMillis()): List<ParsedSms> {
        return splitSmsBlocks(raw).mapNotNull { parse(it, fallbackNow) }
    }

    fun looksFinancial(raw: String): Boolean {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return false
        val hasMoney = MONEY_LOOK.matcher(t).find()
        val hasVerb = VERB_LOOK.matcher(t).find()
        val hasCard = t.contains("credit card") || t.contains("crd") || CARD_LAST4.matcher(t).find()
        return hasMoney || hasCard || (hasVerb && t.any { it.isDigit() })
    }

    /**
     * Stricter filter for inbox import lists: requires a currency amount and a
     * real debit/credit/spend verb so loan promos and OTPs are skipped.
     */
    fun looksLikeTransaction(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (isPromotional(t)) return false
        if (!MONEY_LOOK.matcher(t).find()) return false
        if (!TXN_VERB_LOOK.matcher(t).find()) return false
        val lower = t.lowercase(Locale.ROOT)
        // Skip pure OTP / verification codes even if they mention Rs somehow.
        if (lower.contains("otp") || lower.contains("verification code")) return false
        return true
    }

    /**
     * Marketing / scam-style SMS that mention rupee amounts but are not ledger events.
     * Example: "Get 5% Extra Cashback… Min. Trxn.: Rs.2500".
     */
    fun isPromotional(raw: String): Boolean {
        val t = raw.lowercase(Locale.ROOT)
        if (t.isEmpty()) return false
        val promoPhrases = listOf(
            "cashback", "extra cashback", "get 5%", "get 10%", "apply now",
            "t&c", "t&cs", "tnc", "terms and conditions", "valid till", "valid until",
            "min. trxn", "min trxn", "min. txn", "minimum transaction",
            "max. cashback", "limited period", "offer", "unlock a personal loan",
            "pre-approved", "missed call", "click here", "hurry", "avail now",
            "congratulations! you are eligible", "don't miss", "flat rs",
            "save upto", "save up to", "interest-free", "no cost emi offer"
        )
        val hits = promoPhrases.count { t.contains(it) }
        if (hits >= 2) return true
        if (t.contains("cashback") && (t.contains("min") || t.contains("valid"))) return true
        if (t.contains("get ") && t.contains("%") && t.contains("credit card")) return true
        // Indian DLT promo sender IDs often end with -P
        // (checked by callers that have the address; body-only heuristic below)
        if (!TXN_VERB_LOOK.matcher(t).find() && MONEY_LOOK.matcher(t).find() &&
            (t.contains("offer") || t.contains("cashback") || t.contains("apply"))
        ) {
            return true
        }
        return false
    }

    /** True when the SMS address looks like a promotional DLT template (-P suffix). */
    fun isPromotionalSender(address: String): Boolean {
        val a = address.trim().uppercase(Locale.ROOT)
        if (a.isEmpty()) return false
        return a.endsWith("-P") || a.endsWith("-P\"") || a.contains("-P,")
    }

    fun memoContainsHash(memo: String, smsHash: String): Boolean {
        if (smsHash.isBlank()) return false
        return memo.contains("[sms:$smsHash]", ignoreCase = false)
    }

    fun contentHash(text: String): String {
        var h = 1125899906842597L
        for (ch in text.trim()) {
            h = 31 * h + ch.code
        }
        return java.lang.Long.toHexString(h)
    }

    fun samplePasteText(): String = """
        Rs.1,250.00 debited from A/c XX4521 on 08-09-2026 at AMAZON. Avl Bal Rs.12,340.50

        INR 5000.00 credited to your A/c XX7788 on 01-Sep-26. Info: SALARY.

        ₹249.00 spent on UPI to SWIGGY using PhonePe. UPI Ref 123456789012.

        INR 3,499.00 spent on HDFC Bank Credit Card XX1234 at FLIPKART on 20-09-2026.

        Payment of Rs.5,000 received towards your HDFC Credit Card XX1234. Thank you.
    """.trimIndent()

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
        // Prefer amount near a transaction verb (avoids Avbl Limit / Available balance).
        val verbAnchored = Pattern.compile(
            """(?i)(?:(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)\s*(?:is\s+)?(?:credited|debited|spent)|""" +
                """(?:credited|debited|spent|withdrawn)\s+(?:with\s+|by\s+|for\s+)?(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)|""" +
                """(?:credited|debited)\s+with\s+(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?))"""
        ).matcher(text)
        if (verbAnchored.find()) {
            for (g in 1..verbAnchored.groupCount()) {
                val rawAmt = verbAnchored.group(g)?.replace(",", "")
                val value = rawAmt?.toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }

        for (re in AMOUNT_PATTERNS) {
            val m = re.matcher(text)
            while (m.find()) {
                if (isBalanceOrLimitContext(text, m.start())) continue
                val rawAmt = m.group(1)?.replace(",", "") ?: continue
                val value = rawAmt.toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        return null
    }

    private fun isBalanceOrLimitContext(text: String, matchStart: Int): Boolean {
        val from = (matchStart - 48).coerceAtLeast(0)
        val before = text.substring(from, matchStart)
        return BALANCE_BEFORE.matcher(before).find()
    }

    private fun extractIsIncome(text: String): Boolean? {
        val t = text.lowercase(Locale.ROOT)
        if (t.contains("payment") && (t.contains("credit card") || t.contains("towards your"))) {
            if (t.contains("received") || t.contains("thank you") || t.contains("payment of")) {
                return true
            }
        }
        // IDFC / UPI style: "debited by Rs. X; MERCHANT credited" — treat as debit.
        if (Regex("""\bdebited\b.*\bcredited\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
            !t.contains("has been credited")
        ) {
            return false
        }
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
        if (t.contains("credit card") ||
            t.contains("crd ") ||
            (t.contains("card") && (t.contains("hdfc") || t.contains("sbi") || t.contains("icici") ||
                t.contains("axis") || t.contains("amex") || t.contains("idfc") ||
                t.contains("spent on")))
        ) {
            return "Credit Card"
        }
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

    private fun extractCardLast4(text: String): String? {
        val m = CARD_LAST4.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun extractDescription(text: String, mode: String): String {
        val toMatch = Pattern.compile(
            """\b(?:to|towards|paid to|sent to)\s+([A-Za-z0-9 &._@-]{2,40})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (toMatch.find()) {
            val candidate = cleanMerchant(toMatch.group(1)!!)
            if (isUsefulMerchant(candidate)) {
                return candidate
            }
        }

        val atMatcher = Pattern.compile(
            """(?i)(?:\s|^)(?:at)\s+([A-Za-z][A-Za-z0-9 &._-]{1,48}?)(?=\s+on\s+\d|\s+at\s+\d|,|\.|$)"""
        ).matcher(text)
        while (atMatcher.find()) {
            val candidate = cleanMerchant(atMatcher.group(1)!!)
            if (isUsefulMerchant(candidate) &&
                !Regex(
                    """^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|your|on)\b""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(candidate)
            ) {
                return candidate
            }
        }

        // IDFC bank: "debited by Rs. X; MERCHANT credited"
        val idfcMerchant = Pattern.compile(
            """;\s*([A-Za-z][A-Za-z0-9 &._-]{1,48}?)\s+credited\b""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (idfcMerchant.find()) {
            val candidate = cleanMerchant(idfcMerchant.group(1)!!)
            if (isUsefulMerchant(candidate) && !looksLikeAccountRef(candidate)) {
                return candidate
            }
        }

        val fromMatch = Pattern.compile(
            """\b(?:from|received from)\s+([A-Za-z0-9 &._@-]{2,40})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(text)
        if (fromMatch.find()) {
            val candidate = cleanMerchant(fromMatch.group(1)!!)
            if (isUsefulMerchant(candidate) && !looksLikeAccountRef(candidate)) {
                return candidate
            }
        }

        return when (mode) {
            "UPI" -> "UPI transaction"
            "Bank" -> "Bank transaction"
            "Credit Card" -> "Credit card transaction"
            else -> "SMS transaction"
        }
    }

    private fun looksLikeAccountRef(candidate: String): Boolean {
        val c = candidate.lowercase(Locale.ROOT)
        return c.startsWith("a/c") ||
            c.startsWith("ac ") ||
            c.contains("xx") ||
            Regex("""\d{4,}""").containsMatchIn(c)
    }

    private fun isUsefulMerchant(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val c = candidate.lowercase(Locale.ROOT)
        if (c == "your" || c.startsWith("hdfc") || c.startsWith("credit")) return false
        if (c == "rs" || c == "rs." || c == "inr" || c.startsWith("rs.") || c.startsWith("₹")) return false
        if (c.all { it.isDigit() || it == '.' || it == ',' }) return false
        return true
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
