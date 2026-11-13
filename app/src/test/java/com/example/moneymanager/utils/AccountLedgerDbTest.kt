package com.example.moneymanager.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountLedgerDbTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dao = AppDatabase.getDatabase(ctx).accountDao()

    /** Before: a card clamped at 0, so overpaying lost the credit and Undo came back wrong. */
    @Test
    fun overpaidCardGoesNegative_andUndoIsExact() = runBlocking {
        val id = dao.insert(AccountEntity(name = "Card-${System.nanoTime()}", type = AccountEntity.TYPE_CREDIT_CARD, balance = 100.0))
        InstrumentLedger.applyTxnDelta(dao, id, "INCOME", "Credit Card Payment", 150.0)
        assertEquals(-50.0, dao.getById(id)!!.balance, 0.0)
        InstrumentLedger.applyTxnDelta(dao, id, "INCOME", "Credit Card Payment", 150.0, reverse = true)
        assertEquals(100.0, dao.getById(id)!!.balance, 0.0)
        InstrumentLedger.applyTransfer(dao, null, id, 400.0)
        assertEquals(-300.0, dao.getById(id)!!.balance, 0.0)
        InstrumentLedger.applyTransfer(dao, null, id, 400.0, reverse = true)
        assertEquals(100.0, dao.getById(id)!!.balance, 0.0)
    }

    @Test
    fun walletBehavesLikeCash() = runBlocking {
        val id = dao.insert(AccountEntity(name = "Wallet-${System.nanoTime()}", type = AccountEntity.TYPE_WALLET, balance = 100.0))
        InstrumentLedger.applyTxnDelta(dao, id, "EXPENSE", "Food", 130.0)
        assertEquals(-30.0, dao.getById(id)!!.balance, 0.0)
    }
}
