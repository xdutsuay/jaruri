package com.example.moneymanager.utils

import com.example.moneymanager.data.CategoryRuleEntity
import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SubscriptionDetectorTest {

    private fun ts(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun amountsSimilarWithinTolerance() {
        assertTrue(SubscriptionDetector.amountsSimilar(199.0, 199.0))
        assertTrue(SubscriptionDetector.amountsSimilar(200.0, 210.0))
        assertFalse(SubscriptionDetector.amountsSimilar(200.0, 250.0))
    }

    @Test
    fun monthlyCadenceRequiresGap() {
        val a = TransactionEntity(1, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.JANUARY, 5), "NETFLIX")
        val b = TransactionEntity(2, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.FEBRUARY, 5), "NETFLIX")
        val tooSoon = TransactionEntity(3, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.JANUARY, 12), "NETFLIX")
        assertTrue(SubscriptionDetector.hasMonthlyCadence(listOf(a, b)))
        assertFalse(SubscriptionDetector.hasMonthlyCadence(listOf(a, tooSoon)))
    }

    @Test
    fun detectsNetflixLikePattern() {
        val txs = listOf(
            TransactionEntity(1, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.JANUARY, 10), "NETFLIX · UPI"),
            TransactionEntity(2, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.FEBRUARY, 10), "NETFLIX · UPI"),
            TransactionEntity(3, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.MARCH, 10), "NETFLIX · UPI"),
            TransactionEntity(4, "EXPENSE", "Food", 250.0, ts(2026, Calendar.MARCH, 11), "ZOMATO")
        )
        val suggestions = SubscriptionDetector.detect(txs)
        assertTrue(suggestions.any { it.merchantKey.contains("netflix") })
        val netflix = suggestions.first { it.merchantKey.contains("netflix") }
        assertEquals(3, netflix.hitCount)
        assertEquals(199.0, netflix.amount, 0.01)
    }

    @Test
    fun skipsAlreadyRecurring() {
        val txs = listOf(
            TransactionEntity(1, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.JANUARY, 10), "SPOTIFY"),
            TransactionEntity(2, "EXPENSE", "Entertainment", 199.0, ts(2026, Calendar.FEBRUARY, 10), "SPOTIFY")
        )
        val existing = listOf(
            RecurringEntity(
                type = "EXPENSE",
                category = "Entertainment",
                amount = 199.0,
                memo = "SPOTIFY",
                nextDueTimestamp = ts(2026, Calendar.MARCH, 10)
            )
        )
        assertTrue(SubscriptionDetector.detect(txs, existing).isEmpty())
    }

    @Test
    fun suggestionConvertsToRecurring() {
        val s = SubscriptionDetector.Suggestion(
            merchantKey = "netflix",
            displayName = "NETFLIX",
            amount = 199.0,
            category = "Entertainment",
            type = "EXPENSE",
            hitCount = 3,
            lastTimestamp = ts(2026, Calendar.MARCH, 10),
            accountId = null
        )
        val r = s.toRecurring()
        assertEquals("MONTH", r.frequency)
        assertEquals(199.0, r.amount, 0.01)
        assertEquals("Entertainment", r.category)
    }
}

class CategoryRulesMatchTest {

    @Test
    fun customRuleBeatsBuiltinWhenAfterLearned() {
        val rules = listOf(
            CategoryRuleEntity(
                keyword = "swiggy",
                category = "Work Meals",
                type = CategoryRuleEntity.TYPE_EXPENSE,
                priority = 10
            )
        )
        val p = ParsedSms(
            amount = 200.0,
            isIncome = false,
            description = "SWIGGY",
            modeOfPayment = "UPI",
            remarks = "SMS",
            smsHash = "x"
        )
        // Without learned: custom rule wins over Food
        assertEquals(
            "Work Meals",
            SmsCategorizer.categorize(p, "Paid to SWIGGY", null, rules)
        )
        // Learned still wins
        assertEquals(
            "Food",
            SmsCategorizer.categorize(p, "Paid to SWIGGY", "Food", rules)
        )
    }

    @Test
    fun customRuleRespectsTypeFilter() {
        val rules = listOf(
            CategoryRuleEntity(
                keyword = "acme",
                category = "Salary",
                type = CategoryRuleEntity.TYPE_INCOME
            )
        )
        val expense = ParsedSms(
            amount = 50.0,
            isIncome = false,
            description = "ACME",
            modeOfPayment = "Bank",
            remarks = "",
            smsHash = "y"
        )
        // Expense SMS should not match INCOME-only rule
        assertEquals(
            "Others",
            SmsCategorizer.categorize(expense, "debited at ACME", null, rules)
        )
        val income = expense.copy(isIncome = true)
        assertEquals(
            "Salary",
            SmsCategorizer.categorize(income, "credited from ACME", null, rules)
        )
    }
}
