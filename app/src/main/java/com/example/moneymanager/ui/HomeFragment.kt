package com.example.moneymanager.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.example.moneymanager.viewmodel.MainViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var fullList: List<TransactionEntity> = emptyList()
    private var searchQuery: String = ""
    private var typeFilter: String = "ALL"
    private var selectedMonth: Int = -1 // -1 = All months (show full ledger)
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TransactionAdapter(
            onClick = { tx ->
                findNavController().navigate(
                    R.id.action_home_to_addTransaction,
                    bundleOf("transactionId" to tx.id)
                )
            },
            onLongClick = { tx -> confirmDelete(tx) }
        )

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        setupMonthYearSpinners(adapter)

        val filterLabels = listOf(
            getString(R.string.filter_all),
            getString(R.string.filter_income),
            getString(R.string.filter_expense)
        )
        binding.spTypeFilter.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            filterLabels
        )
        binding.spTypeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                typeFilter = when (position) {
                    1 -> "INCOME"
                    2 -> "EXPENSE"
                    else -> "ALL"
                }
                applyFilter(adapter)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilter(adapter)
            }
        })

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(
                R.id.action_home_to_addTransaction,
                bundleOf("transactionId" to -1L)
            )
        }

        binding.incomeLayout.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chart)
        }
        binding.expenseLayout.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_chart)
        }

        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            fullList = list
            applyFilter(adapter)
        }

        viewModel.currencySymbol.observe(viewLifecycleOwner) {
            applyFilter(adapter)
        }
    }

    private fun setupMonthYearSpinners(adapter: TransactionAdapter) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (2020..currentYear).map { it.toString() }
        binding.spinnerYear.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            years
        )
        binding.spinnerYear.setSelection(years.indexOf(currentYear.toString()).coerceAtLeast(0))

        val monthLabels = mutableListOf(getString(R.string.filter_all_months))
        monthLabels.addAll(resources.getStringArray(R.array.months))
        binding.spinnerMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthLabels
        )
        // Default to current calendar month (index 0 is "All", so +1)
        val currentMonthIndex = Calendar.getInstance().get(Calendar.MONTH) + 1
        binding.spinnerMonth.setSelection(currentMonthIndex)
        selectedMonth = Calendar.getInstance().get(Calendar.MONTH)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val monthPos = binding.spinnerMonth.selectedItemPosition
                selectedMonth = if (monthPos <= 0) -1 else monthPos - 1
                selectedYear = binding.spinnerYear.selectedItem?.toString()?.toIntOrNull()
                    ?: currentYear
                applyFilter(adapter)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.spinnerMonth.onItemSelectedListener = listener
        binding.spinnerYear.onItemSelectedListener = listener
    }

    private fun applyFilter(adapter: TransactionAdapter) {
        val monthList = MainViewModel.filterByMonth(fullList, selectedYear, selectedMonth)
        val filtered = MainViewModel.filterTransactions(monthList, searchQuery, typeFilter)
        adapter.submitList(filtered)

        val symbol = viewModel.currencySymbol.value ?: "₹"
        var income = 0.0
        var expense = 0.0
        monthList.forEach { tx ->
            if (tx.type == "INCOME") income += tx.amount else expense += tx.amount
        }
        binding.tvIncome.text = formatCurrency(income, symbol)
        binding.tvExpense.text = formatCurrency(expense, symbol)
        binding.tvBalance.text = formatCurrency(income - expense, symbol)

        when {
            fullList.isEmpty() -> {
                binding.tvWarning.text = getString(R.string.empty_ledger_hint)
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            monthList.isEmpty() && selectedMonth >= 0 -> {
                binding.tvWarning.text = getString(R.string.empty_month_hint)
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            filtered.isEmpty() -> {
                binding.tvWarning.text = getString(R.string.empty_month_hint)
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            else -> {
                binding.tvWarning.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
            }
        }
    }

    private fun confirmDelete(tx: TransactionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_transaction_title)
            .setMessage(R.string.delete_transaction_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteTransaction(tx) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatCurrency(amount: Double, symbol: String): String {
        return "$symbol${String.format("%.0f", amount)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
