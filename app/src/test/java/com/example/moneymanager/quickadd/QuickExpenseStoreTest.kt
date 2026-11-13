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
}
