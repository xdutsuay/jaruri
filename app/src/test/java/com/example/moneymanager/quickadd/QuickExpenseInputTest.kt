package com.example.moneymanager.quickadd

import com.example.moneymanager.quickadd.QuickExpenseInput.Amount
import com.example.moneymanager.quickadd.QuickExpenseInput.AmountError
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickExpenseInputTest {
    private fun ok(v: Double) = Amount.Ok(v)
    private fun bad(e: AmountError) = Amount.Bad(e)

    @Test
    fun acceptsWhatPeopleType() {
        assertEquals(ok(50.0), QuickExpenseInput.parseAmount("50"))
        assertEquals(ok(50.5), QuickExpenseInput.parseAmount(" 50.5 "))
        assertEquals(ok(1200.0), QuickExpenseInput.parseAmount("1,200"))
        assertEquals(ok(125000.75), QuickExpenseInput.parseAmount("1,25,000.75"))
        assertEquals(ok(50.0), QuickExpenseInput.parseAmount("50."))
        assertEquals(ok(0.5), QuickExpenseInput.parseAmount(".5"))
        assertEquals(ok(QuickExpenseInput.MAX_AMOUNT), QuickExpenseInput.parseAmount("99999999.99"))
    }

    @Test
    fun rejectsBadAmounts() {
        assertEquals(bad(AmountError.EMPTY), QuickExpenseInput.parseAmount(null))
        assertEquals(bad(AmountError.EMPTY), QuickExpenseInput.parseAmount("   "))
        assertEquals(bad(AmountError.ZERO), QuickExpenseInput.parseAmount("0"))
        assertEquals(bad(AmountError.ZERO), QuickExpenseInput.parseAmount("0.00"))
        for (s in listOf("-5", "abc", "1.234", "1.2.3", "5e3", ".", "₹50")) {
            assertEquals(s, bad(AmountError.INVALID), QuickExpenseInput.parseAmount(s))
        }
        assertEquals(bad(AmountError.TOO_LARGE), QuickExpenseInput.parseAmount("100000000"))
    }

    @Test
    fun noteIsTrimmedSingleLineAndCapped() {
        assertEquals("", QuickExpenseInput.cleanNote(null))
        assertEquals("cab to office", QuickExpenseInput.cleanNote("  cab\n to   office "))
        assertEquals(QuickExpenseInput.MAX_NOTE, QuickExpenseInput.cleanNote("x".repeat(500)).length)
    }

    @Test
    fun remembersLastCategoryOnlyIfItStillExists() {
        val names = listOf("Food", "Transportation", "Bills")
        assertEquals("Transportation", QuickExpenseInput.pickCategory(names, "Transportation"))
        assertEquals("Food", QuickExpenseInput.pickCategory(names, "Deleted one"))
        assertEquals("Food", QuickExpenseInput.pickCategory(names, null))
        assertEquals(null, QuickExpenseInput.pickCategory(emptyList(), "Food"))
    }
}
