package com.example.moneymanager.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.viewmodel.MainViewModel

class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var expenseCategoryNames: List<String> = emptyList()
    private var incomeCategoryNames: List<String> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etMemo = view.findViewById<EditText>(R.id.etMemo)
        val spCategory = view.findViewById<Spinner>(R.id.spCategory)
        val rgType = view.findViewById<RadioGroup>(R.id.rgType)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            mutableListOf()
        )
        spCategory.adapter = categoryAdapter

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

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isBlank()) {
                etAmount.error = "Required"
                return@setOnClickListener
            }

            if (categoryAdapter.count == 0) {
                Toast.makeText(requireContext(), "Add a category first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = if (rgType.checkedRadioButtonId == R.id.rbIncome) "INCOME" else "EXPENSE"
            val category = spCategory.selectedItem.toString()
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                etAmount.error = "Enter a valid amount"
                return@setOnClickListener
            }
            val memo = etMemo.text.toString()

            viewModel.addTransaction(
                type = type,
                category = category,
                amount = amount,
                date = System.currentTimeMillis(),
                memo = memo
            )

            // Navigate back
            findNavController().popBackStack()
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
