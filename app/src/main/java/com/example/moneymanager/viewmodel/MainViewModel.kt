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
        incomeTotal.addSource(allTransactions) { list -> calculateTotals(list) }
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
