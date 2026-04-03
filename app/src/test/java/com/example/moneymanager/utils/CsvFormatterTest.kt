package com.example.moneymanager.utils

import com.example.moneymanager.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CsvFormatterTest {

    @Test
    fun testFormatTransactions() {
        // Set standard timezone to avoid local discrepancies
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        
        // 1712128800000 = 2024-04-03 07:20:00 UTC
        val dateValue = 1712128800000L
        
        val tx1 = TransactionEntity(
            id = 1,
            type = "INCOME",
            category = "Salary",
            amount = 5000.0,
            dateTimestamp = dateValue,
            memo = "March, Salary" // Testing comma replacement
        )

        val tx2 = TransactionEntity(
            id = 2,
            type = "EXPENSE",
            category = "Food",
            amount = 15.5,
            dateTimestamp = dateValue,
            memo = "Lunch"
        )
        
        val csv = CsvFormatter.format(listOf(tx1, tx2))
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = dateFormat.format(java.util.Date(dateValue))
        
        val expected = "Type,Category,Amount,Date,Memo\n" +
                       "INCOME,Salary,5000.0,$dateStr,March  Salary\n" +
                       "EXPENSE,Food,15.5,$dateStr,Lunch\n"
                       
        assertEquals(expected, csv)
    }
}
