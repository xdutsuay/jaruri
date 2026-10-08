package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentLedgerTest {

    @Test
    fun txnBalanceDelta_bankIncomeRaises() {
        assertEquals(
            100.0,
            InstrumentLedger.txnBalanceDelta(false, "INCOME", "Salary", 100.0),
            0.0
        )
    }

    @Test
    fun txnBalanceDelta_bankExpenseLowers() {
        assertEquals(
            -40.0,
            InstrumentLedger.txnBalanceDelta(false, "EXPENSE", "Food", 40.0),
            0.0
        )
    }

    @Test
    fun txnBalanceDelta_cardExpenseRaisesOutstanding() {
        assertEquals(
            250.0,
            InstrumentLedger.txnBalanceDelta(true, "EXPENSE", "Shopping", 250.0),
            0.0
        )
    }

    @Test
    fun txnBalanceDelta_cardIncomeLowersOutstanding() {
        assertEquals(
            -500.0,
            InstrumentLedger.txnBalanceDelta(true, "INCOME", "Credit Card Payment", 500.0),
            0.0
        )
    }

    @Test
    fun txnBalanceDelta_transferTypeIsZero() {
        assertEquals(
            0.0,
            InstrumentLedger.txnBalanceDelta(false, "TRANSFER", "Transfer", 100.0),
            0.0
        )
    }

    @Test
    fun txnBalanceDelta_reverseFlipsSign() {
        assertEquals(
            40.0,
            InstrumentLedger.txnBalanceDelta(false, "EXPENSE", "Food", 40.0, reverse = true),
            0.0
        )
    }

    @Test
    fun transferSideDelta_bankFromTo() {
        assertEquals(-50.0, InstrumentLedger.transferSideDelta(false, isFrom = true, 50.0), 0.0)
        assertEquals(50.0, InstrumentLedger.transferSideDelta(false, isFrom = false, 50.0), 0.0)
    }

    @Test
    fun transferSideDelta_cardAsDestinationLowersOutstanding() {
        assertEquals(-80.0, InstrumentLedger.transferSideDelta(true, isFrom = false, 80.0), 0.0)
    }

    @Test
    fun adoptSmsAsCashBalance_onlyNewerBankAvailable() {
        assertEquals(
            true,
            InstrumentLedger.adoptSmsAsCashBalance(0L, 10L, BalanceKind.AVAILABLE, false)
        )
        assertEquals(
            false,
            InstrumentLedger.adoptSmsAsCashBalance(50L, 10L, BalanceKind.AVAILABLE, false)
        )
        assertEquals(
            true,
            InstrumentLedger.adoptSmsAsCashBalance(10L, 50L, BalanceKind.AVAILABLE, false)
        )
        assertEquals(
            false,
            InstrumentLedger.adoptSmsAsCashBalance(0L, 10L, BalanceKind.AVAILABLE, true)
        )
    }

    @Test
    fun formatDifference_includesSignForPositive() {
        assertEquals("+12.50", InstrumentLedger.formatDifference(12.5))
    }

    @Test
    fun formatDifference_keepsMinusForNegative() {
        assertEquals("-3.00", InstrumentLedger.formatDifference(-3.0))
    }
}
