package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.TransactionEntity
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()

    // Real-time list of transactions observed by UI
    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()

    // Derived stats for Dashboard
    val incomeTotal = MediatorLiveData<Double>()
    val expenseTotal = MediatorLiveData<Double>()
    val balance = MediatorLiveData<Double>()

    init {
        // Recalculate totals whenever the list changes
        incomeTotal.addSource(allTransactions) { list -> 
            calculateTotals(list) 
            if (list.isEmpty()) {
                populateSampleData()
            }
        }
    }

    private fun populateSampleData() {
        // Prevent infinite loop if still empty after attempt (shouldn't happen if insert works)
        // But livedata observation might trigger again. 
        // Better trigger: Check count in background.
        viewModelScope.launch {
            if (dao.getCount() == 0) {
                 val samples = listOf(
                     TransactionEntity(type="INCOME", category="Salary", amount=217333.0, dateTimestamp=System.currentTimeMillis(), memo="Monthly Salary"),
                     TransactionEntity(type="EXPENSE", category="Bills", amount=734.0, dateTimestamp=System.currentTimeMillis(), memo="Axis Bank"),
                     TransactionEntity(type="EXPENSE", category="Home", amount=11000.0, dateTimestamp=System.currentTimeMillis(), memo="Advance for grill"),
                     TransactionEntity(type="EXPENSE", category="Clothing", amount=2094.0, dateTimestamp=System.currentTimeMillis() - 86400000, memo="Baby Cloth"),
                     TransactionEntity(type="EXPENSE", category="Transportation", amount=340.0, dateTimestamp=System.currentTimeMillis() - 172800000, memo="Bus/Train"),
                     TransactionEntity(type="EXPENSE", category="Home", amount=17527.0, dateTimestamp=System.currentTimeMillis() - 259200000, memo="Home Loan EMI")
                 )
                 samples.forEach { dao.insertTransaction(it) }
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
    
    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
        }
    }
}
