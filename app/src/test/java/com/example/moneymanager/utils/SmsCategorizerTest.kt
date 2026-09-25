package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCategorizerTest {

    private fun parsed(
        amount: Double = 100.0,
        isIncome: Boolean,
        description: String?,
        mode: String = "UPI",
        remarks: String = "SMS: test"
    ) = ParsedSms(
        amount = amount,
        isIncome = isIncome,
        description = description,
        modeOfPayment = mode,
        remarks = remarks,
        smsHash = "abc"
    )

    @Test
    fun foodKeywordsMapToFood() {
        val p = parsed(isIncome = false, description = "SWIGGY")
        assertEquals("Food", SmsCategorizer.categorize(p, "₹249 spent on UPI to SWIGGY"))
        assertEquals(
            "Food",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "ZOMATO"),
                "Paid Rs.450 to ZOMATO using UPI"
            )
        )
    }

    @Test
    fun shoppingKeywordsMapToShopping() {
        val p = parsed(isIncome = false, description = "AMAZON", mode = "Bank")
        assertEquals(
            "Shopping",
            SmsCategorizer.categorize(p, "Rs.1250 debited at AMAZON")
        )
        assertEquals(
            "Shopping",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "FLIPKART"),
                "debited for Flipkart order"
            )
        )
    }

    @Test
    fun salaryKeywordsMapToSalary() {
        val p = parsed(isIncome = true, description = null, mode = "Bank")
        assertEquals(
            "Salary",
            SmsCategorizer.categorize(p, "INR 5000 credited. Info: SALARY.")
        )
    }

    @Test
    fun upiP2pMapsToTransfer() {
        val p = parsed(isIncome = true, description = "RAHUL SHARMA", mode = "UPI")
        assertEquals(
            "Transfer",
            SmsCategorizer.categorize(p, "You have received Rs 1000 from RAHUL SHARMA via UPI")
        )
        val paid = parsed(isIncome = false, description = "PRIYA", mode = "UPI")
        assertEquals(
            "Transfer",
            SmsCategorizer.categorize(paid, "Paid Rs.200 to PRIYA using UPI")
        )
    }

    @Test
    fun defaultExpenseIsOthers() {
        val p = parsed(isIncome = false, description = "UNKNOWN MERCHANT XYZ", mode = "Bank")
        assertEquals("Others", SmsCategorizer.categorize(p, "Rs.50 debited from A/c at UNKNOWN MERCHANT XYZ"))
    }

    @Test
    fun defaultIncomeIsOthersNotSalary() {
        val p = parsed(isIncome = true, description = "ACME CORP", mode = "Bank")
        assertEquals(
            "Others",
            SmsCategorizer.categorize(p, "Rs.100 credited to A/c from ACME CORP")
        )
    }

    @Test
    fun transportationAndEntertainment() {
        assertEquals(
            "Transportation",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "UBER"),
                "₹120 spent on UPI to UBER"
            )
        )
        assertEquals(
            "Entertainment",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "NETFLIX"),
                "Rs.199 debited for NETFLIX"
            )
        )
    }

    @Test
    fun creditCardSpendUsesMerchantCategoryNotCreditCard() {
        assertEquals(
            "Shopping",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "FLIPKART", mode = "Credit Card"),
                "spent on HDFC Bank Credit Card XX1234 at FLIPKART"
            )
        )
        assertEquals(
            "Food",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "Zomato", mode = "Credit Card"),
                "INR 250 spent on IDFC FIRST Bank Credit Card at Zomato"
            )
        )
    }

    @Test
    fun idfcBankDebitNotForcedToCreditCardCategory() {
        assertEquals(
            "Shopping",
            SmsCategorizer.categorize(
                parsed(isIncome = false, description = "Amazon India", mode = "Bank"),
                "Your A/c XX0545 debited by Rs. 334.00; Amazon India credited. Team IDFC FIRST Bank"
            )
        )
    }

    @Test
    fun creditCardPaymentIncome() {
        assertEquals(
            "Credit Card Payment",
            SmsCategorizer.categorize(
                parsed(isIncome = true, description = null, mode = "Credit Card"),
                "Payment of Rs.5,000 received towards your HDFC Credit Card XX1234. Thank you."
            )
        )
    }
}
