package com.example.moneymanager.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.viewmodel.MainViewModel

import androidx.fragment.app.activityViewModels

class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etMemo = view.findViewById<EditText>(R.id.etMemo)
        val spCategory = view.findViewById<Spinner>(R.id.spCategory)
        val rgType = view.findViewById<RadioGroup>(R.id.rgType)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Populate Spinner
        val categories = listOf("Food", "Transport", "Bills", "Salary", "Entertainment", "Health")
        spCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        btnSave.setOnClickListener {
            val amountStr = etAmount.text.toString()
            if (amountStr.isBlank()) {
                etAmount.error = "Required"
                return@setOnClickListener
            }

            val type = if (rgType.checkedRadioButtonId == R.id.rbIncome) "INCOME" else "EXPENSE"
            val category = spCategory.selectedItem.toString()
            val amount = amountStr.toDouble()
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
}
