package com.example.moneymanager.ui

import android.widget.ArrayAdapter
import android.widget.Spinner
import com.example.moneymanager.utils.LastAccountPrefs
import com.example.moneymanager.utils.AccountChoices
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.utils.CategoryChoices
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var expenseCategoryNames: List<String> = emptyList()
    private var incomeCategoryNames: List<String> = emptyList()
    private var editingId: Long = -1L
    private var editingDate: Long = System.currentTimeMillis()
    private var editingCategory: String? = null
    private var editingType: String? = null
    private var prefillCategory: String? = null
    private var accounts: List<AccountEntity> = emptyList()
    /** Account to show selected once the list arrives (edit: the row's; new: last used). */
    private var wantedAccountId: Long? = null
    private lateinit var spAccount: Spinner
    private lateinit var accountAdapter: ArrayAdapter<String>
    private lateinit var lastAccount: LastAccountPrefs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingId = arguments?.getLong("transactionId", -1L) ?: -1L

        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etMemo = view.findViewById<EditText>(R.id.etMemo)
        val spCategory = view.findViewById<Spinner>(R.id.spCategory)
        val rgType = view.findViewById<RadioGroup>(R.id.rgType)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnImportSms = view.findViewById<Button>(R.id.btnImportSms)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf()
        )
        spCategory.adapter = categoryAdapter

        lastAccount = LastAccountPrefs(requireContext())
        spAccount = view.findViewById(R.id.spAccount)
        accountAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, mutableListOf())
        spAccount.adapter = accountAdapter
        if (editingId <= 0) wantedAccountId = lastAccount.lastAccountId
        viewModel.allAccounts.observe(viewLifecycleOwner) { list ->
            val keep = if (accountAdapter.count > 0) selectedAccountId() else wantedAccountId
            accounts = list
            accountAdapter.clear()
            accountAdapter.add(getString(R.string.account_none))
            accountAdapter.addAll(list.map { AccountChoices.label(it) })
            accountAdapter.notifyDataSetChanged()
            selectAccount(keep)
        }

        btnSave.setText(
            if (editingId > 0) R.string.update_transaction else R.string.save_transaction
        )
        btnImportSms.visibility = if (editingId > 0) View.GONE else View.VISIBLE
        btnDelete.visibility = if (editingId > 0) View.VISIBLE else View.GONE

        viewModel.expenseCategories.observe(viewLifecycleOwner) { categories ->
            expenseCategoryNames = categories.map { it.name }
            if (!isIncomeSelected(rgType)) {
                updateCategorySpinner(spCategory, rgType)
            }
        }

        viewModel.incomeCategories.observe(viewLifecycleOwner) { categories ->
            incomeCategoryNames = categories.map { it.name }
            if (isIncomeSelected(rgType)) {
                updateCategorySpinner(spCategory, rgType)
            }
        }

        rgType.setOnCheckedChangeListener { _, _ ->
            updateCategorySpinner(spCategory, rgType)
        }

        if (editingId <= 0 && savedInstanceState == null) {
            arguments?.let { args ->
                args.getString(ARG_PREFILL_AMOUNT)?.let { etAmount.setText(it) }
                args.getString(ARG_PREFILL_NOTE)?.let { etMemo.setText(it) }
                prefillCategory = args.getString(ARG_PREFILL_CATEGORY)
                if (args.getString(ARG_PREFILL_TYPE).equals("INCOME", ignoreCase = true)) {
                    rgType.check(R.id.rbIncome)
                }
            }
        }

        if (editingId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                val tx = viewModel.getTransaction(editingId) ?: return@launch
                editingDate = tx.dateTimestamp
                editingCategory = tx.category
                editingType = tx.type
                wantedAccountId = tx.accountId
                selectAccount(tx.accountId)
                etAmount.setText(tx.amount.toString())
                etMemo.setText(tx.memo)
                if (tx.type == "INCOME") {
                    rgType.check(R.id.rbIncome)
                } else {
                    rgType.check(R.id.rbExpense)
                }
                updateCategorySpinner(spCategory, rgType)
                val idx = CategoryChoices.indexOf((0 until categoryAdapter.count).map { categoryAdapter.getItem(it).toString() }, tx.category)
                if (idx >= 0) spCategory.setSelection(idx)
            }
        }

        btnImportSms.setOnClickListener {
            findNavController().navigate(R.id.action_addTransaction_to_importSms)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val tx = viewModel.getTransaction(editingId)
                        if (tx != null) viewModel.deleteTransaction(tx)
                        findNavController().popBackStack()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            if (amountStr.isBlank()) {
                etAmount.error = "Required"
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                etAmount.error = "Enter a valid amount"
                return@setOnClickListener
            }

            if (categoryAdapter.count == 0) {
                Toast.makeText(requireContext(), "Add a category first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = if (rgType.checkedRadioButtonId == R.id.rbIncome) "INCOME" else "EXPENSE"
            val category = spCategory.selectedItem.toString()
            val memo = etMemo.text.toString()

            if (editingId > 0) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val previous = viewModel.getTransaction(editingId)
                    // Copy the stored row so payee, UPI ref, refund links etc. survive an edit.
                    val base = previous ?: TransactionEntity(
                        id = editingId, type = type, category = category, amount = amount,
                        dateTimestamp = editingDate, memo = memo
                    )
                    viewModel.updateTransaction(
                        base.copy(
                            type = type,
                            category = category,
                            amount = amount,
                            dateTimestamp = editingDate,
                            memo = memo,
                            accountId = selectedAccountId()
                        ),
                        previousCategory = previous?.category
                    )
                    findNavController().popBackStack()
                }
            } else {
                val accountId = selectedAccountId()
                lastAccount.lastAccountId = accountId
                viewModel.addTransaction(
                    type = type,
                    category = category,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    memo = memo,
                    accountId = accountId
                )
                findNavController().popBackStack()
            }
        }
    }

    /** Position 0 is "No account". */
    private fun selectedAccountId(): Long? =
        spAccount.selectedItemPosition.takeIf { it > 0 }?.let { accounts.getOrNull(it - 1)?.id }

    private fun selectAccount(id: Long?) {
        val idx = accounts.indexOfFirst { it.id == id }
        spAccount.setSelection(if (idx >= 0) idx + 1 else 0)
    }

    private fun isIncomeSelected(radioGroup: RadioGroup): Boolean {
        return radioGroup.checkedRadioButtonId == R.id.rbIncome
    }

    private fun updateCategorySpinner(spinner: Spinner, radioGroup: RadioGroup) {
        val previousSelection = spinner.selectedItem?.toString()
        // Keep the edited row's own category even if it was removed from the list.
        val currentCategories = CategoryChoices.forPicker(
            if (isIncomeSelected(radioGroup)) incomeCategoryNames else expenseCategoryNames,
            editingCategory?.takeIf { editingType == if (isIncomeSelected(radioGroup)) "INCOME" else "EXPENSE" }
        )

        categoryAdapter.clear()
        categoryAdapter.addAll(currentCategories)
        categoryAdapter.notifyDataSetChanged()

        if (currentCategories.isEmpty()) {
            return
        }

        val selectedIndex = CategoryChoices.indexOf(currentCategories, previousSelection)
            .takeIf { it >= 0 }
            ?: CategoryChoices.indexOf(currentCategories, editingCategory).takeIf { it >= 0 }
            ?: CategoryChoices.indexOf(currentCategories, prefillCategory).takeIf { it >= 0 }
            ?: 0
        spinner.setSelection(selectedIndex)
    }

    companion object {
        /** Optional prefill for a new transaction (e.g. from `jaruri://add`); nothing is saved until Save. */
        const val ARG_PREFILL_AMOUNT = "prefillAmount"
        const val ARG_PREFILL_TYPE = "prefillType"
        const val ARG_PREFILL_CATEGORY = "prefillCategory"
        const val ARG_PREFILL_NOTE = "prefillNote"
    }
}
