package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionAccountingTest {

    @Test
    fun transfersExcludedFromTotals() {
        val list = listOf(
            TransactionEntity(1, "INCOME", "Salary", 1000.0, 1L, "pay"),
            TransactionEntity(2, "EXPENSE", "Food", 200.0, 2L, "lunch"),
            TransactionEntity(
                3, "TRANSFER", "Transfer", 500.0, 3L, "Bank→Cash",
                accountId = 1, transferToAccountId = 2
            ),
            TransactionEntity(4, "EXPENSE", "Transfer", 100.0, 4L, "UPI friend"),
            TransactionEntity(5, "INCOME", "Credit Card Payment", 300.0, 5L, "CC pay")
        )
        val totals = TransactionAccounting.sumTotals(list)
        assertEquals(1000.0, totals.income, 0.001)
        assertEquals(200.0, totals.expense, 0.001)
        assertEquals(800.0, totals.balance, 0.001)
    }

    @Test
    fun isNeutralDetectsTransferTypeAndCategories() {
        assertTrue(TransactionAccounting.isTransferType("TRANSFER"))
        assertTrue(TransactionAccounting.isTransferType("transfer"))
        assertFalse(TransactionAccounting.isTransferType("EXPENSE"))
        assertTrue(
            TransactionAccounting.isNeutralForTotals(
                TransactionEntity(1, "TRANSFER", "Transfer", 1.0, 1L, "")
            )
        )
        assertTrue(
            TransactionAccounting.isNeutralForTotals(
                TransactionEntity(2, "EXPENSE", "Transfer", 1.0, 1L, "")
            )
        )
        assertTrue(
            TransactionAccounting.isNeutralForTotals(
                TransactionEntity(3, "INCOME", "Credit Card Payment", 1.0, 1L, "")
            )
        )
        assertFalse(
            TransactionAccounting.isNeutralForTotals(
                TransactionEntity(4, "EXPENSE", "Food", 1.0, 1L, "")
            )
        )
    }

    @Test
    fun netLiquidCashBankMinusCards() {
        val accounts = listOf(
            AccountEntity(1, "Wallet", AccountEntity.TYPE_CASH, balance = 1000.0),
            AccountEntity(2, "Bank", AccountEntity.TYPE_BANK, balance = 5000.0),
            AccountEntity(3, "Card", AccountEntity.TYPE_CREDIT_CARD, balance = 1500.0)
        )
        assertEquals(4500.0, TransactionAccounting.netLiquid(accounts), 0.001)
        assertEquals(6000.0, TransactionAccounting.cashAndBankTotal(accounts), 0.001)
        assertEquals(1500.0, TransactionAccounting.creditCardOutstanding(accounts), 0.001)
    }
}
