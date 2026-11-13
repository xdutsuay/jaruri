package com.example.moneymanager.quickadd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.utils.AccountChoices
import com.example.moneymanager.utils.CategoryChoices
import com.example.moneymanager.utils.LastAccountPrefs
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QuickExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val store = QuickExpenseStore(application)

    val categories: LiveData<List<String>> =
        CategoryRepository(application).expenseCategories
            .map { list -> CategoryChoices.forPicker(list.map { it.name }) }.asLiveData()
    val accounts: LiveData<List<AccountEntity>> = AppDatabase.getDatabase(application).accountDao().getAll().asLiveData()
    private val lastAccount = LastAccountPrefs(application)
    val currencySymbol: LiveData<String> = SettingsRepository(application).currencySymbol.asLiveData()

    /** Set while a save is in flight or its Undo bar is showing, so Save can't double-fire. */
    var busy = false
        private set

    sealed interface Result {
        data class Saved(val id: Long, val amount: Double, val category: String) : Result
        data class Invalid(val error: QuickExpenseInput.AmountError) : Result
        object NoCategory : Result
    }

    fun initialAccount(list: List<AccountEntity>): Long? = AccountChoices.pickInitial(list, lastAccount.lastAccountId)

    fun initialCategory(names: List<String>): String? = QuickExpenseInput.pickCategory(names, store.lastCategory)

    fun save(amountText: String?, category: String?, note: String?, accountId: Long? = null, onResult: (Result) -> Unit) {
        if (busy) return
        val amount = when (val a = QuickExpenseInput.parseAmount(amountText)) {
            is QuickExpenseInput.Amount.Bad -> return onResult(Result.Invalid(a.error))
            is QuickExpenseInput.Amount.Ok -> a.value
        }
        if (category.isNullOrBlank()) return onResult(Result.NoCategory)
        busy = true
        viewModelScope.launch {
            val id = store.save(amount, category, QuickExpenseInput.cleanNote(note), accountId)
            onResult(Result.Saved(id, amount, category))
        }
    }

    fun undo(id: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = store.undo(id)
            busy = false
            onDone(ok)
        }
    }
}
