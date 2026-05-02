package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.models.Category
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()
    private val categoryRepository = CategoryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    // Real-time list of transactions observed by UI
    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()
    val expenseCategories: LiveData<List<Category>> = categoryRepository.expenseCategories.asLiveData()
    val incomeCategories: LiveData<List<Category>> = categoryRepository.incomeCategories.asLiveData()

    // Settings observed by UI
    val currencySymbol: LiveData<String> = settingsRepository.currencySymbol.asLiveData()
    val dateFormat: LiveData<String> = settingsRepository.dateFormat.asLiveData()

    // Derived stats for Dashboard
    val incomeTotal = MediatorLiveData<Double>()
    val expenseTotal = MediatorLiveData<Double>()
    val balance = MediatorLiveData<Double>()

    init {
        android.util.Log.d("DEBUG_VM", "ViewModel Initialized")
        // Recalculate totals whenever the list changes
        incomeTotal.addSource(allTransactions) { list ->
            android.util.Log.d("DEBUG_VM", "AllTransactions updated: size=${list.size}")
            calculateTotals(list)
            if (list.isEmpty()) {
                android.util.Log.d("DEBUG_VM", "List empty, attempting sample data...")
                populateSampleData()
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
            val newTx = TransactionEntity(
                type = type,
                category = category,
                amount = amount,
                dateTimestamp = date,
                memo = memo
            )
            dao.insertTransaction(newTx)
        }
    }

    private fun populateSampleData() {
        viewModelScope.launch {
            try {
                val count = dao.getCount()
                android.util.Log.d("DEBUG_VM", "Current DB Count: $count")
                if (count == 0) {
                     android.util.Log.d("DEBUG_VM", "Inserting Sample Data...")
                     val samples = listOf(
                         TransactionEntity(type="INCOME", category="Salary", amount=217333.0, dateTimestamp=System.currentTimeMillis(), memo="Monthly Salary"),
                         TransactionEntity(type="EXPENSE", category="Bills", amount=734.0, dateTimestamp=System.currentTimeMillis(), memo="Axis Bank"),
                         TransactionEntity(type="EXPENSE", category="Home", amount=11000.0, dateTimestamp=System.currentTimeMillis(), memo="Advance for grill"),
                         TransactionEntity(type="EXPENSE", category="Clothing", amount=2094.0, dateTimestamp=System.currentTimeMillis() - 86400000, memo="Baby Cloth"),
                         TransactionEntity(type="EXPENSE", category="Transportation", amount=340.0, dateTimestamp=System.currentTimeMillis() - 172800000, memo="Bus/Train"),
                         TransactionEntity(type="EXPENSE", category="Home", amount=17527.0, dateTimestamp=System.currentTimeMillis() - 259200000, memo="Home Loan EMI")
                     )
                     samples.forEach { dao.insertTransaction(it) }
                     android.util.Log.d("DEBUG_VM", "Sample Data Inserted")
                }
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_VM", "Error in populateSampleData", e)
            }
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
        
        calendar.set(java.util.Calendar.DAY_OF_MONTH, calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis
        
        return dao.getTransactionsByDateRange(start, end).asLiveData()
    }
}
