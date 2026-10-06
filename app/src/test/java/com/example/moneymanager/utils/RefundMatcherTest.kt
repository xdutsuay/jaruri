package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundMatcherTest {

    @Test
    fun extractUpiRefAndRrn() {
        val refs = RefundMatcher.extractRefs(
            "Refund of Rs.50. UPI Ref 123456789012. RRN 998877665544"
        )
        assertEquals("123456789012", refs.upiRef)
        assertEquals("998877665544", refs.rrn)
    }

    @Test
    fun detectsRefundLanguage() {
        assertTrue(RefundMatcher.isRefundOrReversal("Rs.100 refunded to your a/c", true))
        assertTrue(RefundMatcher.isRefundOrReversal("Transaction reversed", null))
        assertTrue(RefundMatcher.isRefundOrReversal("txn failed amount credited", true))
        assertFalse(RefundMatcher.isRefundOrReversal("Rs.100 paid to SWIGGY", false))
    }

    @Test
    fun matchesByRefFirst() {
        val candidates = listOf(
            RefundMatcher.MatchCandidate(
                1, 100.0, "EXPENSE", 1_000L, 5L, "AAA111", null, "SWIGGY"
            ),
            RefundMatcher.MatchCandidate(
                2, 100.0, "EXPENSE", 2_000L, 5L, "BBB222", null, "OTHER"
            )
        )
        val match = RefundMatcher.findOriginalExpense(
            candidates,
            amount = 100.0,
            payeeId = 5L,
            refs = RefundMatcher.Refs("BBB222", null),
            refundAt = 3_000L
        )
        assertEquals(2L, match?.id)
    }

    @Test
    fun matchesByAmountAndPayeeInWindow() {
        val candidates = listOf(
            RefundMatcher.MatchCandidate(
                10, 249.0, "EXPENSE", 1_000L, 7L, null, null, "SWIGGY"
            )
        )
        val match = RefundMatcher.findOriginalExpense(
            candidates,
            amount = 249.0,
            payeeId = 7L,
            refs = RefundMatcher.Refs(null, null),
            refundAt = 1_000L + java.util.concurrent.TimeUnit.DAYS.toMillis(2)
        )
        assertNotNull(match)
        assertEquals(10L, match!!.id)

        val tooOld = RefundMatcher.findOriginalExpense(
            candidates,
            amount = 249.0,
            payeeId = 7L,
            refs = RefundMatcher.Refs(null, null),
            refundAt = 1_000L + java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        )
        assertNull(tooOld)
    }

    @Test
    fun refundNeutralExcludedFromIncomeTotals() {
        val expense = com.example.moneymanager.data.TransactionEntity(
            1, "EXPENSE", "Food", 100.0, 1L, "x"
        )
        val refund = com.example.moneymanager.data.TransactionEntity(
            2, "INCOME", "Refund", 100.0, 2L, "refund",
            isRefundNeutral = true,
            linkedTransactionId = 1L
        )
        val totals = TransactionAccounting.sumTotals(listOf(expense, refund))
        assertEquals(0.0, totals.income, 0.001)
        assertEquals(100.0, totals.expense, 0.001)
        assertTrue(TransactionAccounting.isNeutralForTotals(refund))
    }
}
