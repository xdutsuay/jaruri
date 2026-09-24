package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.BudgetEntity
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.HistoricalSeedData
import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.models.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()
    private val accountDao = AppDatabase.getDatabase(application).accountDao()
    private val budgetDao = AppDatabase.getDatabase(application).budgetDao()
    private val recurringDao = AppDatabase.getDatabase(application).recurringDao()
    private val categoryRepository = CategoryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()
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

    private var sampleSeedAttempted = false

    init {
        // Keep totals in sync whenever the ledger changes.
        incomeTotal.addSource(allTransactions) { list ->
            calculateTotals(list)
        }
        // Seed must not depend on Home observing incomeTotal (it no longer does).
        viewModelScope.launch {
            maybePopulateSampleData()
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
        }
    }

    fun updateTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.updateTransaction(tx)
        }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = dao.getById(id)

    /**
     * Seeds multi-month demo history when DB is empty and demo seeding is allowed.
     * Never deletes existing transactions.
     */
    private suspend fun maybePopulateSampleData() {
        try {
            if (sampleSeedAttempted) return
            sampleSeedAttempted = true
            val enabled = settingsRepository.sampleDataEnabled.first()
            val count = dao.getCount()
            if (enabled && count == 0) {
                seedDemoHistoryInternal()
            } else {
                // Phase 2 tables may be empty after schema bump while ledger already has rows.
                seedAccountsAndBudgetsIfEmpty()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Sample data seed failed", e)
        }
    }

    /** Public: append demo history only if DB empty; used from Settings. */
    fun seedDemoHistoryIfEmpty(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (dao.getCount() != 0) {
                    onResult(false)
                    return@launch
                }
                seedDemoHistoryInternal()
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Demo seed failed", e)
                onResult(false)
            }
        }
    }

    private suspend fun seedDemoHistoryInternal() {
        HistoricalSeedData.transactions().forEach { dao.insertTransaction(it) }
        seedAccountsAndBudgetsIfEmpty()
        settingsRepository.setDemoHistorySeeded(true)
    }

    private suspend fun seedAccountsAndBudgetsIfEmpty() {
        if (accountDao.getCount() == 0) {
            HistoricalSeedData.accounts().forEach { accountDao.insert(it) }
        }
        if (budgetDao.getAll().first().isEmpty()) {
            HistoricalSeedData.budgets().forEach { budgetDao.insert(it) }
        }
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

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
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
            if (monthZeroBased < 0) return list // "All months"
            val cal = java.util.Calendar.getInstance()
            return list.filter { tx ->
                cal.timeInMillis = tx.dateTimestamp
                cal.get(java.util.Calendar.YEAR) == year &&
                    cal.get(java.util.Calendar.MONTH) == monthZeroBased
            }
        }
    }
}
