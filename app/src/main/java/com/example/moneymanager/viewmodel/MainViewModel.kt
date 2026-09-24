package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.HistoricalSeedData
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.models.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()
    private val categoryRepository = CategoryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()
    val expenseCategories: LiveData<List<Category>> = categoryRepository.expenseCategories.asLiveData()
    val incomeCategories: LiveData<List<Category>> = categoryRepository.incomeCategories.asLiveData()

    val currencySymbol: LiveData<String> = settingsRepository.currencySymbol.asLiveData()
    val dateFormat: LiveData<String> = settingsRepository.dateFormat.asLiveData()

    val incomeTotal = MediatorLiveData<Double>()
    val expenseTotal = MediatorLiveData<Double>()
    val balance = MediatorLiveData<Double>()

    private var sampleSeedAttempted = false

    init {
        incomeTotal.addSource(allTransactions) { list ->
            calculateTotals(list)
            if (list.isEmpty() && !sampleSeedAttempted) {
                sampleSeedAttempted = true
                maybePopulateSampleData()
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
    private fun maybePopulateSampleData() {
        viewModelScope.launch {
            try {
                val enabled = settingsRepository.sampleDataEnabled.first()
                if (!enabled) return@launch
                val count = dao.getCount()
                if (count != 0) {
                    // Preserve whatever the user already has.
                    return@launch
                }
                seedDemoHistoryInternal()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Sample data seed failed", e)
            }
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
        settingsRepository.setDemoHistorySeeded(true)
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
            val cal = java.util.Calendar.getInstance()
            return list.filter { tx ->
                cal.timeInMillis = tx.dateTimestamp
                cal.get(java.util.Calendar.YEAR) == year &&
                    cal.get(java.util.Calendar.MONTH) == monthZeroBased
            }
        }
    }
}
