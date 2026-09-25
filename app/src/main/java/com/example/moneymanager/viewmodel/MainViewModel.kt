package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.BudgetEntity
import com.example.moneymanager.data.CategoryLearnEntity
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.models.Category
import com.example.moneymanager.utils.CategoryLearning
import com.example.moneymanager.utils.LegacyExportBootstrap
import com.example.moneymanager.utils.PhoneMergeBootstrap
import com.example.moneymanager.utils.SmsCategoryBackfill
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()
    private val accountDao = AppDatabase.getDatabase(application).accountDao()
    private val budgetDao = AppDatabase.getDatabase(application).budgetDao()
    private val recurringDao = AppDatabase.getDatabase(application).recurringDao()
    private val learnDao = AppDatabase.getDatabase(application).categoryLearnDao()
    private val categoryRepository = CategoryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()
    val deletedTransactions: LiveData<List<TransactionEntity>> =
        dao.getDeletedTransactions().asLiveData()
    val allAccounts: LiveData<List<AccountEntity>> = accountDao.getAll().asLiveData()
    val allBudgets: LiveData<List<BudgetEntity>> = budgetDao.getAll().asLiveData()
    val activeRecurring: LiveData<List<RecurringEntity>> = recurringDao.getActive().asLiveData()
    val expenseCategories: LiveData<List<Category>> = categoryRepository.expenseCategories.asLiveData()
    val incomeCategories: LiveData<List<Category>> = categoryRepository.incomeCategories.asLiveData()

    val currencySymbol: LiveData<String> = settingsRepository.currencySymbol.asLiveData()
    val dateFormat: LiveData<String> = settingsRepository.dateFormat.asLiveData()

    val incomeTotal = MediatorLiveData<Double>()
    val expenseTotal = MediatorLiveData<Double>()
    val balance = MediatorLiveData<Double>()

    init {
        incomeTotal.addSource(allTransactions) { list -> calculateTotals(list) }
        viewModelScope.launch {
            try {
                LegacyExportBootstrap.runOnce(getApplication())
                PhoneMergeBootstrap.runOnce(getApplication())
                SmsCategoryBackfill.runOnce(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Startup ledger bootstrap failed", e)
            }
        }
    }

    private fun calculateTotals(list: List<TransactionEntity>) {
        var inc = 0.0
        var exp = 0.0
        list.forEach {
            if (it.type == "INCOME") inc += it.amount else exp += it.amount
        }
        incomeTotal.value = inc
        expenseTotal.value = exp
        balance.value = inc - exp
    }

    fun addTransaction(type: String, category: String, amount: Double, date: Long, memo: String) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
                    type = type,
                    category = category,
                    amount = amount,
                    dateTimestamp = date,
                    memo = memo
                )
            )
            rememberCategory(memo, null, category, type)
        }
    }

    fun updateTransaction(tx: TransactionEntity, previousCategory: String? = null) {
        viewModelScope.launch {
            dao.updateTransaction(tx)
            if (previousCategory != null && previousCategory != tx.category) {
                rememberCategory(tx.memo, null, tx.category, tx.type)
            }
        }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = dao.getById(id)

    suspend fun learnedCategoryFor(description: String?, memo: String, type: String): String? {
        val key = CategoryLearning.keyFromDescription(description, memo) ?: return null
        val row = learnDao.get(key) ?: return null
        return if (row.type.equals(type, ignoreCase = true)) row.category else null
    }

    private suspend fun rememberCategory(
        memo: String,
        description: String?,
        category: String,
        type: String
    ) {
        val key = CategoryLearning.keyFromDescription(description, memo) ?: return
        val existing = learnDao.get(key)
        learnDao.upsert(
            CategoryLearnEntity(
                merchantKey = key,
                category = category,
                type = type,
                hitCount = (existing?.hitCount ?: 0) + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        // Ensure category exists in the user's list
        categoryRepository.addCategory(
            category,
            if (type == "INCOME") CategoryRepository.TYPE_INCOME else CategoryRepository.TYPE_EXPENSE
        )
    }

    fun addAccount(account: AccountEntity) {
        viewModelScope.launch { accountDao.insert(account) }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch { accountDao.update(account) }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch { accountDao.delete(account) }
    }

    fun addBudget(category: String, monthlyLimit: Double) {
        viewModelScope.launch {
            budgetDao.insert(BudgetEntity(category = category, monthlyLimit = monthlyLimit))
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch { budgetDao.delete(budget) }
    }

    fun addRecurring(item: RecurringEntity) {
        viewModelScope.launch { recurringDao.insert(item) }
    }

    fun deleteRecurring(item: RecurringEntity) {
        viewModelScope.launch { recurringDao.delete(item) }
    }

    fun importTransactions(rows: List<TransactionEntity>) {
        viewModelScope.launch {
            rows.forEach { dao.insertTransaction(it) }
        }
    }

    /**
     * Append rows from a second phone/export, skipping exact duplicates
     * (same calendar day + type + category + amount + memo).
     */
    fun mergeTransactions(
        rows: List<TransactionEntity>,
        onDone: (added: Int, skipped: Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val existing = dao.getAllActiveList()
            val keys = existing.map { fingerprint(it) }.toMutableSet()
            var added = 0
            var skipped = 0
            for (row in rows) {
                val clean = row.copy(id = 0, deletedAt = null)
                val fp = fingerprint(clean)
                if (fp in keys) {
                    skipped++
                    continue
                }
                dao.insertTransaction(clean)
                keys += fp
                rememberCategory(clean.memo, null, clean.category, clean.type)
                added++
            }
            onDone(added, skipped)
        }
    }

    /** Soft-delete → recycle bin. */
    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.softDelete(tx.id)
        }
    }

    fun restoreTransaction(tx: TransactionEntity) {
        viewModelScope.launch { dao.restore(tx.id) }
    }

    fun permanentlyDelete(tx: TransactionEntity) {
        viewModelScope.launch { dao.hardDeleteById(tx.id) }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch { dao.emptyRecycleBin() }
    }

    /**
     * One-shot: wipe active SMS auto-imports (and optionally all active rows),
     * then insert legacy export rows.
     */
    fun replaceActiveLedgerWith(
        rows: List<TransactionEntity>,
        clearAllActive: Boolean = true,
        onDone: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (clearAllActive) dao.deleteAllActive() else dao.deleteActiveSmsImports()
            dao.insertAll(rows.map { it.copy(id = 0, deletedAt = null) })
            // Learn from imported categories
            rows.forEach { rememberCategory(it.memo, null, it.category, it.type) }
            onDone(rows.size)
        }
    }

    fun addCategory(name: String, type: String) {
        viewModelScope.launch {
            categoryRepository.addCategory(name, type)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }

    fun getTransactionsForMonth(year: Int, month: Int): LiveData<List<TransactionEntity>> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month, 1, 0, 0, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.set(
            java.util.Calendar.DAY_OF_MONTH,
            calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        )
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis

        return dao.getTransactionsByDateRange(start, end).asLiveData()
    }

    companion object {
        fun fingerprint(tx: TransactionEntity): String {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = tx.dateTimestamp
            val day = "%04d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            val cents = kotlin.math.round(tx.amount * 100.0).toLong()
            val memo = tx.memo.trim().lowercase()
            val cat = tx.category.trim().lowercase()
            return "$day|${tx.type}|$cat|$cents|$memo"
        }

        fun filterTransactions(
            list: List<TransactionEntity>,
            query: String,
            typeFilter: String
        ): List<TransactionEntity> {
            val q = query.trim().lowercase()
            return list.filter { tx ->
                val typeOk = when (typeFilter) {
                    "INCOME" -> tx.type == "INCOME"
                    "EXPENSE" -> tx.type == "EXPENSE"
                    else -> true
                }
                if (!typeOk) return@filter false
                if (q.isEmpty()) return@filter true
                tx.memo.lowercase().contains(q) ||
                    tx.category.lowercase().contains(q) ||
                    tx.amount.toString().contains(q) ||
                    tx.type.lowercase().contains(q)
            }
        }

        fun filterByMonth(
            list: List<TransactionEntity>,
            year: Int,
            monthZeroBased: Int
        ): List<TransactionEntity> {
            if (monthZeroBased < 0) return list
            val cal = java.util.Calendar.getInstance()
            return list.filter { tx ->
                cal.timeInMillis = tx.dateTimestamp
                cal.get(java.util.Calendar.YEAR) == year &&
                    cal.get(java.util.Calendar.MONTH) == monthZeroBased
            }
        }
    }
}
