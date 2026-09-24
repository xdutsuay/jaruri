package com.example.moneymanager.viewmodel

import com.example.moneymanager.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MainViewModelFilterTest {

    private val list = listOf(
        TransactionEntity(1, "INCOME", "Salary", 1000.0, 1L, "Monthly pay"),
        TransactionEntity(2, "EXPENSE", "Food", 200.0, 2L, "Lunch"),
        TransactionEntity(3, "EXPENSE", "Credit Card", 500.0, 3L, "Card XX1234 Flipkart")
    )

    @Test
    fun filterByType() {
        assertEquals(1, MainViewModel.filterTransactions(list, "", "INCOME").size)
        assertEquals(2, MainViewModel.filterTransactions(list, "", "EXPENSE").size)
        assertEquals(3, MainViewModel.filterTransactions(list, "", "ALL").size)
    }

    @Test
    fun filterByQuery() {
        assertEquals(1, MainViewModel.filterTransactions(list, "flipkart", "ALL").size)
        assertEquals(1, MainViewModel.filterTransactions(list, "salary", "ALL").size)
        assertEquals(1, MainViewModel.filterTransactions(list, "200", "ALL").size)
    }

    @Test
    fun filterByMonth() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        val sep = TransactionEntity(10, "EXPENSE", "Food", 10.0, cal.timeInMillis, "Sep")
        cal.set(2026, Calendar.AUGUST, 15, 12, 0, 0)
        val aug = TransactionEntity(11, "EXPENSE", "Food", 20.0, cal.timeInMillis, "Aug")
        val monthList = MainViewModel.filterByMonth(listOf(sep, aug), 2026, Calendar.SEPTEMBER)
        assertEquals(1, monthList.size)
        assertEquals("Sep", monthList[0].memo)
    }
}
