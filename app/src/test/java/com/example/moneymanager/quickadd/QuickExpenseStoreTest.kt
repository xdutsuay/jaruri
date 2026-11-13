package com.example.moneymanager.quickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quick expenses land in the normal transactions table and Undo removes them cleanly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickExpenseStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db = AppDatabase.getDatabase(ctx)

    @Test
    fun saveInsertsATodayExpense_remembersCategory_andLearnsTheNote() = runBlocking {
        val store = QuickExpenseStore(ctx, db) { 1_763_000_000_000L }
        assertNull(store.lastCategory)
        val before = db.transactionDao().getCount()
        val id = store.save(50.0, "Transportation", "cab ride")
        val row = db.transactionDao().getById(id)!!
        assertEquals("EXPENSE", row.type)
        assertEquals("Transportation", row.category)
        assertEquals(50.0, row.amount, 0.0)
        assertEquals("cab ride", row.memo)
        assertEquals(1_763_000_000_000L, row.dateTimestamp)
        assertNull(row.deletedAt)
        assertFalse(row.needsCategoryReview)
        assertEquals(before + 1, db.transactionDao().getCount())
        assertEquals("Transportation", store.lastCategory)
        assertEquals("Transportation", QuickExpenseStore(ctx, db).lastCategory) // survives a new instance
        assertEquals("Transportation", db.categoryLearnDao().get("cab ride")?.category)
    }

    @Test
    fun undoRemovesTheRow_once() = runBlocking {
        val store = QuickExpenseStore(ctx, db)
        val id = store.save(120.0, "Food", "")
        assertNotNull(db.transactionDao().getById(id))
        assertTrue(store.undo(id))
        assertNull(db.transactionDao().getById(id))
        assertFalse(store.undo(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesNonPositiveAmounts(): Unit = runBlocking { QuickExpenseStore(ctx, db).save(0.0, "Food", "") }

    @Test
    fun saveOnAnAccountMovesItsBalance_andUndoRestoresIt() = runBlocking {
        val accounts = db.accountDao()
        val bank = accounts.insert(com.example.moneymanager.data.AccountEntity(name = "B-${System.nanoTime()}", type = "BANK", balance = 1000.0))
        val card = accounts.insert(com.example.moneymanager.data.AccountEntity(name = "C-${System.nanoTime()}", type = "CREDIT_CARD", balance = 0.0))
        val store = QuickExpenseStore(ctx, db)
        val id = store.save(50.0, "Transportation", "", bank)
        assertEquals(bank, db.transactionDao().getById(id)!!.accountId)
        assertEquals(950.0, accounts.getById(bank)!!.balance, 0.0)
        assertEquals(bank, com.example.moneymanager.utils.LastAccountPrefs(ctx).lastAccountId)
        val onCard = store.save(200.0, "Food", "", card)
        assertEquals(200.0, accounts.getById(card)!!.balance, 0.0) // owed
        assertTrue(store.undo(onCard))
        assertTrue(store.undo(id))
        assertEquals(1000.0, accounts.getById(bank)!!.balance, 0.0)
        assertEquals(0.0, accounts.getById(card)!!.balance, 0.0)
    }
}
