package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmsParserTest {

    private val fixedNow: Long = calendarMillis(2026, 9, 9)

    @Test
    fun hdfcDebitWithRs() {
        val sms =
            "Rs.1,250.00 debited from A/c XX4521 on 08-09-2026 at AMAZON. Avl Bal Rs.12,340.50"
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(1250.0, p.amount!!, 0.001)
        assertEquals(false, p.isIncome)
        assertTrue(p.modeOfPayment == "Bank" || p.modeOfPayment == "SMS")
        assertTrue(p.description!!.uppercase().contains("AMAZON"))
        assertTrue(p.remarks.startsWith("SMS:"))
        assertEquals(calendarMillis(2026, 9, 8), p.dateTimestamp)
        assertTrue(p.isComplete)
        assertTrue(p.toMemo().contains("[sms:"))
    }

    @Test
    fun sbiCreditInr() {
        val sms = "INR 5000.00 credited to your A/c XX7788 on 01-Sep-26. Info: SALARY."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(5000.0, p.amount!!, 0.001)
        assertEquals(true, p.isIncome)
        assertEquals("Bank", p.modeOfPayment)
        assertTrue(p.isComplete)
        assertEquals(calendarMillis(2026, 9, 1), p.dateTimestamp)
    }

    @Test
    fun upiSpentWithRupeeSymbol() {
        val sms =
            "₹249.00 spent on UPI to SWIGGY using PhonePe. UPI Ref 123456789012."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(249.0, p.amount!!, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("UPI", p.modeOfPayment)
        assertTrue(p.description!!.uppercase().contains("SWIGGY"))
        assertTrue(p.isComplete)
    }

    @Test
    fun upiReceivedFromPerson() {
        val sms =
            "You have received Rs 1,000.00 from RAHUL SHARMA via UPI. Ref: 987654321098."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(1000.0, p.amount!!, 0.001)
        assertEquals(true, p.isIncome)
        assertEquals("UPI", p.modeOfPayment)
        assertTrue(p.description!!.uppercase().contains("RAHUL"))
        assertTrue(p.isComplete)
    }

    @Test
    fun axisPaidToMerchant() {
        val sms = "Paid Rs.450 to ZOMATO using UPI on 09/09/26. Not you? Call 1800."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(450.0, p.amount!!, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("UPI", p.modeOfPayment)
        assertTrue(p.description!!.uppercase().contains("ZOMATO"))
        assertEquals(calendarMillis(2026, 9, 9), p.dateTimestamp)
    }

    @Test
    fun incompleteSmsReturnsNull() {
        assertNull(SmsParser.parse("Hello from bank", fixedNow))
        assertNull(SmsParser.parse("Your OTP is 123456", fixedNow))
    }

    @Test
    fun batchParsesBlankLineSeparated() {
        val batch = """
Rs.100 debited from A/c XX11 on 01-01-2026 at STORE.

You have received Rs 200 from ALICE via UPI.
""".trimIndent()
        val list = SmsParser.parseBatch(batch, fixedNow)
        assertEquals(2, list.size)
        assertEquals(100.0, list[0].amount!!, 0.001)
        assertEquals(false, list[0].isIncome)
        assertEquals(200.0, list[1].amount!!, 0.001)
        assertEquals(true, list[1].isIncome)
    }

    @Test
    fun looksFinancialDetectsMoneySms() {
        assertTrue(SmsParser.looksFinancial("Rs.50 debited from account"))
        assertFalse(SmsParser.looksFinancial("Meeting at 5pm"))
    }

    @Test
    fun memoContainsHashDedup() {
        val sms = "Rs.10 debited from A/c XX11 at CAFE"
        val p = SmsParser.parse(sms, fixedNow)!!
        val memo = p.toMemo()
        assertTrue(SmsParser.memoContainsHash(memo, p.smsHash))
        assertFalse(SmsParser.memoContainsHash(memo, "deadbeef"))
        assertFalse(SmsParser.memoContainsHash("plain memo", p.smsHash))
    }

    @Test
    fun contentHashIsStable() {
        val a = SmsParser.contentHash("same body")
        val b = SmsParser.contentHash("same body")
        val c = SmsParser.contentHash("other body")
        assertEquals(a, b)
        assertTrue(a != c)
    }


    @Test
    fun incompleteWithoutDirectionNotComplete() {
        // Amount found but no credit/debit verb → not complete for auto-import
        val p = SmsParser.parse("Your balance is Rs.5000", fixedNow)
        if (p != null) {
            assertFalse(p.isComplete)
        }
    }

    @Test
    fun emptyAndBlankReturnNull() {
        assertNull(SmsParser.parse("", fixedNow))
        assertNull(SmsParser.parse("   ", fixedNow))
    }

    @Test
    fun creditCardSpendParsesWithLast4() {
        val sms =
            "INR 3,499.00 spent on HDFC Bank Credit Card XX1234 at FLIPKART on 20-09-2026."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(3499.0, p.amount!!, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals("Credit Card", p.modeOfPayment)
        assertEquals("1234", p.cardLast4)
        assertTrue(p.toMemo().contains("Card XX1234"))
        assertTrue(p.isComplete)
    }

    @Test
    fun creditCardPaymentIsIncome() {
        val sms =
            "Payment of Rs.5,000 received towards your HDFC Credit Card XX1234. Thank you."
        val p = SmsParser.parse(sms, fixedNow)!!
        assertEquals(5000.0, p.amount!!, 0.001)
        assertEquals(true, p.isIncome)
        assertEquals("Credit Card", p.modeOfPayment)
        assertEquals("1234", p.cardLast4)
    }

    @Test
    fun samplePasteTextParsesMultiple() {
        val list = SmsParser.parseBatch(SmsParser.samplePasteText(), fixedNow)
        assertTrue(list.size >= 4)
        assertTrue(list.any { it.modeOfPayment == "Credit Card" })
    }

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }
}
