package com.example.moneymanager.utils

import java.util.Locale

/**
 * Best-effort category from SMS body / parsed merchant keywords.
 * Kept in one place so auto-import and tests share the same rules.
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
            "uber", "ola", "rapido", "metro", "petrol", "fuel", "irctc", "railway"
        )),
        Rule("Entertainment", listOf(
            "netflix", "spotify", "prime video", "hotstar", "bookmyshow", "youtube"
        )),
        Rule("Health", listOf(
            "pharmacy", "apollo", "medplus", "hospital", "clinic", "1mg", "pharmeasy"
        )),
        Rule("Bills", listOf(
            "electricity", "broadband", "recharge", "airtel", "jio", "vi ",
            "bsnl", "gas", "water bill", "emi", "loan"
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
     * Income vs expense comes from [ParsedSms.isIncome]; UPI P2P defaults to Transfer.
     */
    fun categorize(parsed: ParsedSms, rawBody: String = ""): String {
        val haystack = buildString {
            append(rawBody)
            append(' ')
            append(parsed.description.orEmpty())
            append(' ')
            append(parsed.remarks)
        }.lowercase(Locale.ROOT)

        val isIncome = parsed.isIncome == true

        if (isIncome) {
            matchRules(haystack, INCOME_RULES)?.let { return it }
            if (isUpiP2p(parsed, haystack)) return "Transfer"
            return "Salary"
        }

        matchRules(haystack, EXPENSE_RULES)?.let { return it }
        if (isUpiP2p(parsed, haystack)) return "Transfer"
        return "Bills"
    }

    private fun matchRules(haystack: String, rules: List<Rule>): String? {
        for (rule in rules) {
            if (rule.keywords.any { haystack.contains(it) }) return rule.category
        }
        return null
    }

    /** Heuristic: UPI to/from a person-like name, not a known merchant keyword. */
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
