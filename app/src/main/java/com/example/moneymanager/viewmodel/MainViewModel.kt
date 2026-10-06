package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.BudgetEntity
import com.example.moneymanager.data.CategoryLearnEntity
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.CategoryRuleEntity
import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.data.QuickAddTemplateEntity
import com.example.moneymanager.data.PayeeEntity
import com.example.moneymanager.models.Category
import com.example.moneymanager.utils.CategoryLearning
import com.example.moneymanager.utils.InstrumentLedger
import com.example.moneymanager.utils.LegacyExportBootstrap
import com.example.moneymanager.utils.PhoneMergeBootstrap
import com.example.moneymanager.utils.SmsCategoryBackfill
import com.example.moneymanager.utils.SmsLedgerCleanup
import com.example.moneymanager.utils.SubscriptionDetector
import com.example.moneymanager.utils.TransactionAccounting
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).transactionDao()
    private val accountDao = AppDatabase.getDatabase(application).accountDao()
    private val budgetDao = AppDatabase.getDatabase(application).budgetDao()
    private val recurringDao = AppDatabase.getDatabase(application).recurringDao()
    private val learnDao = AppDatabase.getDatabase(application).categoryLearnDao()
    private val ruleDao = AppDatabase.getDatabase(application).categoryRuleDao()
    private val categoryRepository = CategoryRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val allTransactions: LiveData<List<TransactionEntity>> = dao.getAllTransactions().asLiveData()
    val deletedTransactions: LiveData<List<TransactionEntity>> =
        dao.getDeletedTransactions().asLiveData()
    val needsCategoryReviewCount: LiveData<Int> = dao.countNeedsCategoryReview().asLiveData()
    val allAccounts: LiveData<List<AccountEntity>> = accountDao.getAll().asLiveData()
    val allBudgets: LiveData<List<BudgetEntity>> = budgetDao.getAll().asLiveData()
    val activeRecurring: LiveData<List<RecurringEntity>> = recurringDao.getActive().asLiveData()
    val categoryRules: LiveData<List<CategoryRuleEntity>> = ruleDao.getAll().asLiveData()
    val expenseCategories: LiveData<List<Category>> = categoryRepository.expenseCategories.asLiveData()
    val incomeCategories: LiveData<List<Category>> = categoryRepository.incomeCategories.asLiveData()

    val currencySymbol: LiveData<String> = settingsRepository.currencySymbol.asLiveData()
    val dateFormat: LiveData<String> = settingsRepository.dateFormat.asLiveData()
    val fiscalYearMode: LiveData<String> = settingsRepository.fiscalYearMode.asLiveData()

    private val templateDao = AppDatabase.getDatabase(application).quickAddTemplateDao()
    private val payeeDao = AppDatabase.getDatabase(application).payeeDao()
    val quickAddTemplates: LiveData<List<QuickAddTemplateEntity>> =
        templateDao.getAll().asLiveData()
    val allPayees: LiveData<List<PayeeEntity>> =
        payeeDao.getAll().asLiveData()

    val incomeTotal = MediatorLiveData<Double>()
    val expenseTotal = MediatorLiveData<Double>()
    val balance = MediatorLiveData<Double>()
    val netLiquid = MediatorLiveData<Double>()
    val subscriptionSuggestions = MediatorLiveData<List<SubscriptionDetector.Suggestion>>()

    init {
        incomeTotal.addSource(allTransactions) { list -> calculateTotals(list) }
        netLiquid.addSource(allAccounts) { accounts ->
            netLiquid.value = TransactionAccounting.netLiquid(accounts)
        }
        subscriptionSuggestions.addSource(allTransactions) { refreshSuggestions() }
        subscriptionSuggestions.addSource(activeRecurring) { refreshSuggestions() }
        viewModelScope.launch {
            try {
                LegacyExportBootstrap.runOnce(getApplication())
                PhoneMergeBootstrap.runOnce(getApplication())
                SmsCategoryBackfill.runOnce(getApplication())
                SmsLedgerCleanup.runOnce(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Startup ledger bootstrap failed", e)
            }
        }
    }

    private fun refreshSuggestions() {
        val txs = allTransactions.value.orEmpty()
        val recurring = activeRecurring.value.orEmpty()
        subscriptionSuggestions.value = SubscriptionDetector.detect(txs, recurring)
    }

    private fun calculateTotals(list: List<TransactionEntity>) {
        val totals = TransactionAccounting.sumTotals(list)
        incomeTotal.value = totals.income
        expenseTotal.value = totals.expense
        balance.value = totals.balance
    }

    fun addTransaction(
        type: String,
        category: String,
        amount: Double,
        date: Long,
        memo: String,
        cardLast4: String? = null,
        accountId: Long? = null
    ) {
        viewModelScope.launch {
            val resolvedAccountId = accountId
                ?: InstrumentLedger.resolveOrCreateCard(accountDao, cardLast4)
            val entity = TransactionEntity(
                type = type,
                category = category,
                amount = amount,
                dateTimestamp = date,
                memo = memo,
                accountId = resolvedAccountId,
                needsCategoryReview = false
            )
            dao.insertTransaction(entity)
            InstrumentLedger.applyEntityDelta(accountDao, entity)
            rememberCategory(memo, null, category, type)
        }
    }

    /**
     * Move money between accounts without counting as income/expense.
     * [fromAccountId] is debited; [toAccountId] is credited
     * (CC destination lowers outstanding).
     */
    fun addTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        date: Long = System.currentTimeMillis(),
        memo: String = "",
        category: String = TransactionAccounting.CAT_TRANSFER
    ) {
        if (fromAccountId == toAccountId || amount <= 0) return
        viewModelScope.launch {
            val entity = TransactionEntity(
                type = TransactionAccounting.TYPE_TRANSFER,
                category = category.ifBlank { TransactionAccounting.CAT_TRANSFER },
                amount = amount,
                dateTimestamp = date,
                memo = memo.ifBlank { "Transfer" },
                accountId = fromAccountId,
                transferToAccountId = toAccountId,
                needsCategoryReview = false
            )
            dao.insertTransaction(entity)
            InstrumentLedger.applyEntityDelta(accountDao, entity)
        }
    }

    fun updateTransaction(tx: TransactionEntity, previousCategory: String? = null) {
        viewModelScope.launch {
            val previous = dao.getById(tx.id)
            if (previous != null) {
                InstrumentLedger.applyEntityDelta(accountDao, previous, reverse = true)
            }
            val clearedReview = tx.copy(needsCategoryReview = false)
            dao.updateTransaction(clearedReview)
            InstrumentLedger.applyEntityDelta(accountDao, clearedReview)
            val categoryChanged = previous != null &&
                previous.category != clearedReview.category
            val confirmingReview = previous?.needsCategoryReview == true
            if (categoryChanged || confirmingReview ||
                (previousCategory != null && previousCategory != clearedReview.category)
            ) {
                rememberCategory(
                    clearedReview.memo,
                    null,
                    clearedReview.category,
                    clearedReview.type
                )
            }
        }
    }

    /** Quick categorize-later path from the home list. */
    fun setCategory(tx: TransactionEntity, category: String) {
        if (category.isBlank()) return
        updateTransaction(
            tx.copy(category = category.trim(), needsCategoryReview = false),
            previousCategory = tx.category
        )
    }

    fun setFiscalYearMode(mode: String) {
        viewModelScope.launch { settingsRepository.setFiscalYearMode(mode) }
    }

    /** Rename a payee; updates displayName and past memos that still use the old prefix. */
    fun renamePayee(payeeId: Long, newName: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val updated = com.example.moneymanager.utils.PayeeStore.renamePayee(
                payeeDao, payeeId, newName
            )
            onDone(updated != null)
        }
    }

    /** Repeat the most recent expense (+1). */
    fun repeatLastExpense(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val last = dao.getLastExpense()
            if (last == null) {
                onDone(false)
                return@launch
            }
            val copy = last.copy(
                id = 0,
                dateTimestamp = System.currentTimeMillis(),
                deletedAt = null,
                needsCategoryReview = false,
                linkedTransactionId = null,
                isRefundNeutral = false,
                upiRef = null,
                rrn = null
            )
            dao.insertTransaction(copy)
            InstrumentLedger.applyEntityDelta(accountDao, copy)
            onDone(true)
        }
    }

    fun saveQuickAddTemplate(
        name: String,
        amount: Double,
        category: String,
        type: String,
        note: String = ""
    ) {
        viewModelScope.launch {
            val order = (templateDao.getAllList().maxOfOrNull { it.pinOrder } ?: 0) + 1
            templateDao.insert(
                com.example.moneymanager.data.QuickAddTemplateEntity(
                    name = name.trim().ifBlank { category },
                    amount = amount,
                    category = category,
                    type = type,
                    note = note,
                    pinOrder = order
                )
            )
        }
    }

    fun deleteQuickAddTemplate(id: Long) {
        viewModelScope.launch { templateDao.deleteById(id) }
    }

    fun applyQuickAddTemplate(template: com.example.moneymanager.data.QuickAddTemplateEntity) {
        addTransaction(
            type = template.type,
            category = template.category,
            amount = template.amount,
            date = System.currentTimeMillis(),
            memo = template.note
        )
    }

    /** Deep-link / Tasker silent add. */
    fun addFromDeepLink(
        amount: Double,
        category: String?,
        note: String?,
        type: String = "EXPENSE",
        silent: Boolean = false,
        onDone: (Long) -> Unit = {}
    ) {
        if (amount <= 0) {
            onDone(-1L)
            return
        }
        viewModelScope.launch {
            val cat = category?.trim()?.takeIf { it.isNotBlank() }
                ?: if (type.equals("INCOME", true)) "Salary" else "Others"
            val entity = TransactionEntity(
                type = if (type.equals("INCOME", true)) "INCOME" else "EXPENSE",
                category = cat,
                amount = amount,
                dateTimestamp = System.currentTimeMillis(),
                memo = note?.trim().orEmpty(),
                needsCategoryReview = false
            )
            val id = dao.insertTransaction(entity)
            InstrumentLedger.applyEntityDelta(accountDao, entity)
            rememberCategory(entity.memo, null, entity.category, entity.type)
            onDone(id)
            android.util.Log.d("MainViewModel", "Deep link add id=$id silent=$silent")
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
        if (TransactionAccounting.isTransferType(type)) return
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

    fun acceptSubscriptionSuggestion(suggestion: SubscriptionDetector.Suggestion) {
        addRecurring(suggestion.toRecurring())
    }

    fun addCategoryRule(keyword: String, category: String, type: String = CategoryRuleEntity.TYPE_ANY) {
        val kw = keyword.trim()
        val cat = category.trim()
        if (kw.length < 2 || cat.isBlank()) return
        viewModelScope.launch {
            ruleDao.insert(
                CategoryRuleEntity(
                    keyword = kw,
                    category = cat,
                    type = type.ifBlank { CategoryRuleEntity.TYPE_ANY },
                    priority = 100,
                    enabled = true
                )
            )
            categoryRepository.addCategory(
                cat,
                if (type.equals(CategoryRuleEntity.TYPE_INCOME, true)) {
                    CategoryRepository.TYPE_INCOME
                } else {
                    CategoryRepository.TYPE_EXPENSE
                }
            )
        }
    }

    fun deleteCategoryRule(rule: CategoryRuleEntity) {
        viewModelScope.launch { ruleDao.delete(rule) }
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
            InstrumentLedger.applyEntityDelta(accountDao, tx, reverse = true)
            dao.softDelete(tx.id)
        }
    }

    fun restoreTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.restore(tx.id)
            InstrumentLedger.applyEntityDelta(accountDao, tx)
        }
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
        val mode = fiscalYearMode.value ?: SettingsRepository.FY_CALENDAR
        val range = com.example.moneymanager.utils.FiscalYearHelpers.monthRange(mode, year, month)
        return dao.getTransactionsByDateRange(range.startMillis, range.endMillis).asLiveData()
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
                    "TRANSFER" -> TransactionAccounting.isTransferType(tx.type) ||
                        tx.category.equals(TransactionAccounting.CAT_TRANSFER, ignoreCase = true)
                    "REVIEW" -> tx.needsCategoryReview
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
            monthZeroBased: Int,
            fiscalMode: String = SettingsRepository.FY_CALENDAR
        ): List<TransactionEntity> {
            if (monthZeroBased < 0) return list
            return list.filter { tx ->
                com.example.moneymanager.utils.FiscalYearHelpers.isInSelectedMonth(
                    tx.dateTimestamp, fiscalMode, year, monthZeroBased
                )
            }
        }

        /** Month-over-month expense/income comparison (transfers excluded). */
        fun monthComparison(
            list: List<TransactionEntity>,
            year: Int,
            monthZeroBased: Int,
            fiscalMode: String = SettingsRepository.FY_CALENDAR
        ): MonthCompare {
            val thisMonth = filterByMonth(list, year, monthZeroBased, fiscalMode)
            // Previous calendar month relative to the absolute month of the selection
            val range = com.example.moneymanager.utils.FiscalYearHelpers.monthRange(
                fiscalMode, year, monthZeroBased
            )
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = range.startMillis
            cal.add(java.util.Calendar.MONTH, -1)
            val prevYear = cal.get(java.util.Calendar.YEAR)
            val prevMonth = cal.get(java.util.Calendar.MONTH)
            // For prev month use CALENDAR absolute year/month
            val prev = filterByMonth(list, prevYear, prevMonth, SettingsRepository.FY_CALENDAR)
            val cur = TransactionAccounting.sumTotals(thisMonth)
            val previous = TransactionAccounting.sumTotals(prev)
            return MonthCompare(cur, previous)
        }
    }

    data class MonthCompare(
        val current: TransactionAccounting.Totals,
        val previous: TransactionAccounting.Totals
    ) {
        val expenseDelta: Double get() = current.expense - previous.expense
        val incomeDelta: Double get() = current.income - previous.income
    }
}
