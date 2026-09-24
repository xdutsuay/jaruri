package com.example.moneymanager.data

import java.util.Calendar

/**
 * Multi-month demo ledger for verifying dashboard month toggle, charts, search, and categories.
 * Inserted only when the DB is empty (or explicitly requested). Never auto-deleted.
 */
object HistoricalSeedData {

    fun transactions(nowMillis: Long = System.currentTimeMillis()): List<TransactionEntity> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis

        fun at(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12): Long {
            val c = Calendar.getInstance()
            c.clear()
            c.set(year, monthZeroBased, day, hour, 0, 0)
            return c.timeInMillis
        }

        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)

        fun prevMonth(offset: Int): Pair<Int, Int> {
            val c = Calendar.getInstance()
            c.set(y, m, 15)
            c.add(Calendar.MONTH, -offset)
            return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
        }

        val (y0, m0) = prevMonth(0)
        val (y1, m1) = prevMonth(1)
        val (y2, m2) = prevMonth(2)
        val (y3, m3) = prevMonth(3)

        return listOf(
            // Current month
            TransactionEntity(type = "INCOME", category = "Salary", amount = 85000.0, dateTimestamp = at(y0, m0, 1), memo = "Monthly salary"),
            TransactionEntity(type = "EXPENSE", category = "Home", amount = 22000.0, dateTimestamp = at(y0, m0, 2), memo = "Rent"),
            TransactionEntity(type = "EXPENSE", category = "Bills", amount = 1899.0, dateTimestamp = at(y0, m0, 3), memo = "Electricity"),
            TransactionEntity(type = "EXPENSE", category = "Food", amount = 420.0, dateTimestamp = at(y0, m0, 5), memo = "Swiggy · UPI"),
            TransactionEntity(type = "EXPENSE", category = "Food", amount = 680.0, dateTimestamp = at(y0, m0, 8), memo = "Groceries"),
            TransactionEntity(type = "EXPENSE", category = "Transportation", amount = 350.0, dateTimestamp = at(y0, m0, 9), memo = "Uber"),
            TransactionEntity(type = "EXPENSE", category = "Credit Card", amount = 3499.0, dateTimestamp = at(y0, m0, 10), memo = "Flipkart · Card XX1234"),
            TransactionEntity(type = "EXPENSE", category = "Entertainment", amount = 649.0, dateTimestamp = at(y0, m0, 12), memo = "Netflix"),
            TransactionEntity(type = "INCOME", category = "Credit Card Payment", amount = 5000.0, dateTimestamp = at(y0, m0, 14), memo = "CC payment · Card XX1234"),
            TransactionEntity(type = "EXPENSE", category = "Shopping", amount = 2199.0, dateTimestamp = at(y0, m0, 16), memo = "Amazon"),
            TransactionEntity(type = "EXPENSE", category = "Health", amount = 450.0, dateTimestamp = at(y0, m0, 18), memo = "Pharmacy"),
            TransactionEntity(type = "EXPENSE", category = "Transfer", amount = 2000.0, dateTimestamp = at(y0, m0, 20), memo = "UPI to friend"),

            // Previous month
            TransactionEntity(type = "INCOME", category = "Salary", amount = 85000.0, dateTimestamp = at(y1, m1, 1), memo = "Monthly salary"),
            TransactionEntity(type = "EXPENSE", category = "Home", amount = 22000.0, dateTimestamp = at(y1, m1, 2), memo = "Rent"),
            TransactionEntity(type = "EXPENSE", category = "Food", amount = 3120.0, dateTimestamp = at(y1, m1, 7), memo = "Dining out"),
            TransactionEntity(type = "EXPENSE", category = "Credit Card", amount = 8999.0, dateTimestamp = at(y1, m1, 11), memo = "Laptop EMI · Card XX5678"),
            TransactionEntity(type = "EXPENSE", category = "Transportation", amount = 1200.0, dateTimestamp = at(y1, m1, 15), memo = "Fuel"),
            TransactionEntity(type = "EXPENSE", category = "Bills", amount = 799.0, dateTimestamp = at(y1, m1, 20), memo = "Jio recharge"),
            TransactionEntity(type = "INCOME", category = "Gift", amount = 2000.0, dateTimestamp = at(y1, m1, 25), memo = "Cashback"),

            // Two months ago
            TransactionEntity(type = "INCOME", category = "Salary", amount = 82000.0, dateTimestamp = at(y2, m2, 1), memo = "Monthly salary"),
            TransactionEntity(type = "EXPENSE", category = "Home", amount = 22000.0, dateTimestamp = at(y2, m2, 2), memo = "Rent"),
            TransactionEntity(type = "EXPENSE", category = "Clothing", amount = 4500.0, dateTimestamp = at(y2, m2, 9), memo = "Myntra"),
            TransactionEntity(type = "EXPENSE", category = "Credit Card", amount = 1599.0, dateTimestamp = at(y2, m2, 14), memo = "POS spend · Card XX1234"),
            TransactionEntity(type = "EXPENSE", category = "Entertainment", amount = 800.0, dateTimestamp = at(y2, m2, 19), memo = "Movie"),
            TransactionEntity(type = "EXPENSE", category = "Food", amount = 2400.0, dateTimestamp = at(y2, m2, 22), memo = "Weekend groceries"),

            // Three months ago
            TransactionEntity(type = "INCOME", category = "Salary", amount = 82000.0, dateTimestamp = at(y3, m3, 1), memo = "Monthly salary"),
            TransactionEntity(type = "INCOME", category = "Business", amount = 15000.0, dateTimestamp = at(y3, m3, 5), memo = "Freelance invoice"),
            TransactionEntity(type = "EXPENSE", category = "Home", amount = 22000.0, dateTimestamp = at(y3, m3, 2), memo = "Rent"),
            TransactionEntity(type = "EXPENSE", category = "Car", amount = 3500.0, dateTimestamp = at(y3, m3, 10), memo = "Service"),
            TransactionEntity(type = "EXPENSE", category = "Bills", amount = 2100.0, dateTimestamp = at(y3, m3, 12), memo = "Broadband + mobile"),
            TransactionEntity(type = "EXPENSE", category = "Credit Card", amount = 7200.0, dateTimestamp = at(y3, m3, 18), memo = "Travel booking · Card XX5678"),
            TransactionEntity(type = "EXPENSE", category = "Food", amount = 1800.0, dateTimestamp = at(y3, m3, 28), memo = "Family dinner")
        )
    }
}
