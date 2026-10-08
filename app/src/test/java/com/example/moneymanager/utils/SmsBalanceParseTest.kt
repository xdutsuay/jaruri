package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmsBalanceParseTest {

    private val fixedNow: Long = calendarMillis(2026, 9, 9)

    @Test
    fun bankDebitExtractsAccountAndAvailableBalance() {
        val sms =
            "Rs.1,250.00 debited from A/c XX4521 on 08-09-2026 at AMAZON. Avl Bal Rs.12,340.50"
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals("4521", p.accountLast4)
        assertNull(p.cardLast4)
        assertEquals(12340.50, p.reportedBalance!!, 0.001)
        assertEquals(BalanceKind.AVAILABLE, p.reportedBalanceKind)
        assertTrue(p.toMemo().contains("A/c XX4521"))
        assertTrue(p.toMemo().contains("Avl"))
    }

    @Test
    fun idfcBankKeepsBalanceSeparateFromTxn() {
        val sms =
            "Your A/c XX0545 debited by Rs. 334.00 on 23/09/26; Amazon India credited. RRN 626641174342. Available balance Rs. 27,613.24. Team IDFC FIRST Bank"
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(334.0, p.amount!!, 0.001)
        assertEquals("0545", p.accountLast4)
        assertEquals(27613.24, p.reportedBalance!!, 0.001)
        assertEquals(BalanceKind.AVAILABLE, p.reportedBalanceKind)
        assertEquals("IDFC", p.bankHint)
    }

    @Test
    fun creditCardExtractsAvailableLimit() {
        val sms =
            "Delicious Purchase! INR 250.38 spent on your IDFC FIRST Bank Credit Card ending XX7354 at Zomato on 24 SEP 2026 at 06:01 PM Avbl Limit: INR 161337.57 If not done by you, call 180010888"
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals("7354", p.cardLast4)
        assertNull(p.accountLast4)
        assertEquals(161337.57, p.reportedBalance!!, 0.001)
        assertEquals(BalanceKind.AVAILABLE_LIMIT, p.reportedBalanceKind)
        assertEquals("IDFC", p.bankHint)
    }

    @Test
    fun newBalAbbreviationIsSeparateFromTheDebit() {
        val sms =
            "Your A/C XXXXX540545 is debited by INR 41,000.00 on 01/10/26 13:20. New Bal :INR 1,52,378.24."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(41000.0, p.amount!!, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("0545", p.accountLast4)
        assertEquals(152378.24, p.reportedBalance!!, 0.001)
        assertEquals(BalanceKind.AVAILABLE, p.reportedBalanceKind)
    }

    @Test
    fun needsReviewUntilLearned() {
        assertTrue(SmsCategorizer.needsCategoryReview("Food", learnedCategory = null))
        assertTrue(SmsCategorizer.needsCategoryReview("Others", learnedCategory = null))
        assertTrue(!SmsCategorizer.needsCategoryReview("Food", learnedCategory = "Food"))
        assertTrue(!SmsCategorizer.needsCategoryReview("Credit Card Payment", null))
    }

    @Test
    fun maskedLongAccountUsesLast4() {
        val sms =
            "Your A/C XXXXX540545 is credited with INR 50.00 on 23/09/26 15:46. Your new balance is INR 27,663.24. Team IDFC FIRST Bank"
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals("0545", p.accountLast4)
        assertEquals(27663.24, p.reportedBalance!!, 0.001)
        assertNotNull(p.bankHint)
    }

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }
}
