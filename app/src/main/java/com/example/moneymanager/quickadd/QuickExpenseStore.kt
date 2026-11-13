package com.example.moneymanager.quickadd

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.utils.CategoryLearning
import com.example.moneymanager.utils.InstrumentLedger

/**
 * Saves quick expenses into the normal `transactions` table, the same way the in-app
 * quick add and `jaruri://add` do (ledger delta + category learning). No separate store.
 */
class QuickExpenseStore(
    context: Context,
    db: AppDatabase = AppDatabase.getDatabase(context),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val app = context.applicationContext
    private val dao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val learnDao = db.categoryLearnDao()
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastCategory: String?
        get() = prefs.getString(KEY_LAST_CATEGORY, null)
        private set(v) { prefs.edit().putString(KEY_LAST_CATEGORY, v).apply() }

    /** Inserts an EXPENSE dated now and returns its row id. */
    suspend fun save(amount: Double, category: String, note: String): Long {
        require(amount > 0) { "amount must be positive" }
        val entity = TransactionEntity(
            type = TYPE,
            category = category,
            amount = amount,
            dateTimestamp = clock(),
            memo = note,
            needsCategoryReview = false
        )
        val id = dao.insertTransaction(entity)
        InstrumentLedger.applyEntityDelta(accountDao, entity)
        CategoryLearning.remember(learnDao, CategoryRepository(app), note, null, category, TYPE, clock())
        lastCategory = category
        return id
    }

    /**
     * Undo a save from this sheet: reverse its ledger effect and remove the row (it was
     * created seconds ago, so it does not go to the recycle bin). False if it is gone.
     */
    suspend fun undo(id: Long): Boolean {
        val tx = dao.getById(id) ?: return false
        if (tx.deletedAt == null) InstrumentLedger.applyEntityDelta(accountDao, tx, reverse = true)
        dao.hardDeleteById(id)
        return true
    }

    companion object {
        const val TYPE = "EXPENSE"
        private const val PREFS = "quick_expense"
        private const val KEY_LAST_CATEGORY = "last_category"
    }
}
