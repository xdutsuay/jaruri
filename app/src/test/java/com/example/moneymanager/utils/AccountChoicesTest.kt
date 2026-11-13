package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountChoicesTest {
    private val bank = AccountEntity(id = 1, name = "HDFC Savings", type = AccountEntity.TYPE_BANK, balance = 5000.0, last4 = "1234")
    private val overdrawn = AccountEntity(id = 2, name = "Current", type = AccountEntity.TYPE_BANK, balance = -300.0)
    private val card = AccountEntity(id = 3, name = "ICICI Card", type = AccountEntity.TYPE_CREDIT_CARD, balance = 1200.0, last4 = "9876")
    private val overpaid = AccountEntity(id = 4, name = "SBI Card", type = AccountEntity.TYPE_CREDIT_CARD, balance = -50.0)
    private val wallet = AccountEntity(id = 5, name = "Paytm", type = AccountEntity.TYPE_WALLET, balance = 250.0)

    @Test
    fun signedBalance_positiveIsYours_negativeIsOwed() {
        assertEquals(5000.0, AccountChoices.signedBalance(bank), 0.0)
        assertEquals(-300.0, AccountChoices.signedBalance(overdrawn), 0.0)
        assertEquals(-1200.0, AccountChoices.signedBalance(card), 0.0)
        assertEquals(50.0, AccountChoices.signedBalance(overpaid), 0.0)
        assertEquals(250.0, AccountChoices.signedBalance(wallet), 0.0)
        assertEquals(5000.0 - 300 - 1200 + 50 + 250, AccountChoices.netBalance(listOf(bank, overdrawn, card, overpaid, wallet)), 0.0)
    }

    @Test
    fun netLiquidCountsWalletsAndCardCredit() {
        assertEquals(5000.0 - 300 - 1200 + 50 + 250, TransactionAccounting.netLiquid(listOf(bank, overdrawn, card, overpaid, wallet)), 0.0)
        assertEquals(5000.0 - 300 + 250, TransactionAccounting.cashAndBankTotal(listOf(bank, overdrawn, card, wallet)), 0.0)
    }

    @Test
    fun labelAndInitialPick() {
        assertEquals("HDFC Savings ··1234", AccountChoices.label(bank))
        assertEquals("Paytm", AccountChoices.label(wallet))
        assertEquals(3L, AccountChoices.pickInitial(listOf(bank, card), 3L))
        assertNull(AccountChoices.pickInitial(listOf(bank, card), 99L))
        assertNull(AccountChoices.pickInitial(listOf(bank, card), null))
    }

    @Test
    fun smsWithoutDigitsMapsByBankNameOnlyWhenUnambiguous() {
        val hdfc = AccountEntity(id = 10, name = "HDFC Savings", type = AccountEntity.TYPE_BANK)
        val icici = AccountEntity(id = 11, name = "My card", type = AccountEntity.TYPE_CREDIT_CARD, bankHint = "ICICI")
        val sbi1 = AccountEntity(id = 12, name = "SBI Salary", type = AccountEntity.TYPE_BANK)
        val sbi2 = AccountEntity(id = 13, name = "SBI Joint", type = AccountEntity.TYPE_BANK)
        val all = listOf(hdfc, icici, sbi1, sbi2)
        assertEquals(10L, InstrumentLedger.matchByBankName(all, "HDFC"))
        assertEquals(11L, InstrumentLedger.matchByBankName(all, "icici"))
        assertNull("two SBI accounts: don't guess", InstrumentLedger.matchByBankName(all, "SBI"))
        assertNull(InstrumentLedger.matchByBankName(all, "Axis"))
        assertNull(InstrumentLedger.matchByBankName(all, null))
        assertNull("too short", InstrumentLedger.matchByBankName(all, "HD"))
    }
}
