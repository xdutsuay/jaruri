package com.example.moneymanager.utils

import java.util.Locale

/**
 * Best-effort category from SMS body / parsed merchant keywords.
 *
 * Payment mode (Bank / Credit Card / UPI) is NOT a category — it stays in the memo.
 * Categories mirror purpose (Food, Home, Bills…), matching typical money-manager usage.
 */
object SmsCategorizer {

    private data class Rule(val category: String, val keywords: List<String>)

    private val EXPENSE_RULES = listOf(
        Rule("Food", listOf(
            "swiggy", "zomato", "food", "restaurant", "cafe", "dominos",
            "mcdonald", "burger", "pizza", "dunzo", "blinkit", "zepto", "instamart"
        )),
        Rule("Shopping", listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa",
            "snapdeal", "shop", "mall"
        )),
        Rule("Transportation", listOf(
            "uber", "ola", "rapido", "metro", "petrol", "fuel", "irctc",
            "railway", "automobile"
        )),
        Rule("Entertainment", listOf(
            "netflix", "spotify", "prime video", "hotstar", "bookmyshow", "youtube"
        )),
        Rule("Health", listOf(
            "pharmacy", "apollo", "medplus", "hospital", "clinic", "1mg", "pharmeasy"
        )),
        Rule("Bills", listOf(
            "electricity", "broadband", "recharge", "airtel", "jio", "vi ",
            "bsnl", "gas", "water bill", "emi", "loan", "insurance", "premium"
        )),
        Rule("Home", listOf(
            "rent", "society maintenance", "maintenance"
        )),
        Rule("Electronics", listOf(
            "croma", "reliance digital"
        ))
    )

    private val INCOME_RULES = listOf(
        Rule("Salary", listOf(
            "salary", "payroll", "stipend", "wage", "credited payroll"
        )),
        Rule("Business", listOf(
            "invoice", "freelance", "client payment"
        )),
        Rule("Gift", listOf(
            "gift", "cashback", "refund", "reward"
        ))
    )

    /**
     * Picks a category name for [parsed] using [rawBody] (and merchant description) keywords.
     * [learnedCategory] wins when the user previously corrected this merchant.
     */
    fun categorize(
        parsed: ParsedSms,
        rawBody: String = "",
        learnedCategory: String? = null
    ): String {
        if (!learnedCategory.isNullOrBlank()) return learnedCategory

        val haystack = buildString {
            append(rawBody)
            append(' ')
            append(parsed.description.orEmpty())
            append(' ')
            append(parsed.remarks)
        }.lowercase(Locale.ROOT)

        val isIncome = parsed.isIncome == true

        if (isIncome) {
            // CC repayment only when this SMS is actually a card payment receipt.
            if (parsed.modeOfPayment == "Credit Card" && looksLikeCardPayment(haystack)) {
                return "Credit Card Payment"
            }
            matchRules(haystack, INCOME_RULES)?.let { return it }
            if (isUpiP2p(parsed, haystack)) return "Transfer"
            // Unknown bank credits are often transfers/refunds — not salary.
            return "Others"
        }

        // Expense: never map payment-mode "Credit Card" → category "Credit Card".
        matchRules(haystack, EXPENSE_RULES)?.let { return it }
        if (isUpiP2p(parsed, haystack)) return "Transfer"
        return "Others"
    }

    private fun looksLikeCardPayment(haystack: String): Boolean {
        return (haystack.contains("towards your") ||
            haystack.contains("payment received") ||
            haystack.contains("payment of") ||
            haystack.contains("thank you")) &&
            (haystack.contains("credit card") || haystack.contains("card"))
    }

    private fun matchRules(haystack: String, rules: List<Rule>): String? {
        for (rule in rules) {
            if (rule.keywords.any { haystack.contains(it) }) return rule.category
        }
        return null
    }

    private fun isUpiP2p(parsed: ParsedSms, haystack: String): Boolean {
        if (parsed.modeOfPayment != "UPI") return false
        val desc = parsed.description?.lowercase(Locale.ROOT).orEmpty()
        if (desc.isBlank() || desc.contains("upi transaction")) return true
        val merchantHit = (EXPENSE_RULES + INCOME_RULES)
            .flatMap { it.keywords }
            .any { haystack.contains(it) || desc.contains(it) }
        return !merchantHit
    }
}
