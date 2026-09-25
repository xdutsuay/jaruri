package com.example.moneymanager.ui

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

        if (editingId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                val tx = viewModel.getTransaction(editingId) ?: return@launch
                editingDate = tx.dateTimestamp
                etAmount.setText(tx.amount.toString())
                etMemo.setText(tx.memo)
                if (tx.type == "INCOME") {
                    rgType.check(R.id.rbIncome)
                } else {
                    rgType.check(R.id.rbExpense)
                }
                updateCategorySpinner(spCategory, rgType)
                val idx = categoryAdapter.getPosition(tx.category)
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
                    viewModel.updateTransaction(
                        TransactionEntity(
                            id = editingId,
                            type = type,
                            category = category,
                            amount = amount,
                            dateTimestamp = editingDate,
                            memo = memo,
                            accountId = previous?.accountId,
                            deletedAt = previous?.deletedAt
                        ),
                        previousCategory = previous?.category
                    )
                    findNavController().popBackStack()
                }
            } else {
                viewModel.addTransaction(
                    type = type,
                    category = category,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    memo = memo
                )
                findNavController().popBackStack()
            }
        }
    }

    private fun isIncomeSelected(radioGroup: RadioGroup): Boolean {
        return radioGroup.checkedRadioButtonId == R.id.rbIncome
    }

    private fun updateCategorySpinner(spinner: Spinner, radioGroup: RadioGroup) {
        val currentCategories = if (isIncomeSelected(radioGroup)) {
            incomeCategoryNames
        } else {
            expenseCategoryNames
        }
        val previousSelection = spinner.selectedItem?.toString()

        categoryAdapter.clear()
        categoryAdapter.addAll(currentCategories)
        categoryAdapter.notifyDataSetChanged()

        if (currentCategories.isEmpty()) {
            return
        }

        val selectedIndex = previousSelection?.let(currentCategories::indexOf)
            ?.takeIf { it >= 0 }
            ?: 0
        spinner.setSelection(selectedIndex)
    }
}
