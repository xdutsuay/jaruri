package com.example.moneymanager.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var fullList: List<TransactionEntity> = emptyList()
    private var searchQuery: String = ""
    private var typeFilter: String = "ALL"
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var toolbarMonthView: View? = null

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
            onLongClick = { tx -> confirmDelete(tx) },
            currencySymbol = { viewModel.currencySymbol.value ?: "₹" }
        )

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.toggleTypeFilter.check(R.id.btn_filter_all)
        binding.toggleTypeFilter.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            typeFilter = when (checkedId) {
                R.id.btn_filter_income -> "INCOME"
                R.id.btn_filter_expense -> "EXPENSE"
                else -> "ALL"
            }
            applyFilter(adapter)
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
            findNavController().navigate(R.id.action_home_to_quickAdd)
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

    override fun onResume() {
        super.onResume()
        installToolbarMonthPicker()
        updateToolbarMonthLabel()
    }

    override fun onPause() {
        clearToolbarMonthPicker()
        super.onPause()
    }

    private fun installToolbarMonthPicker() {
        val activity = activity as? AppCompatActivity ?: return
        val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar) ?: return
        activity.supportActionBar?.setDisplayShowTitleEnabled(false)
        if (toolbarMonthView == null) {
            val monthView = layoutInflater.inflate(R.layout.toolbar_month_title, toolbar, false)
            monthView.setOnClickListener { showMonthYearPicker() }
            toolbarMonthView = monthView
        }
        val parent = toolbarMonthView?.parent as? ViewGroup
        parent?.removeView(toolbarMonthView)
        val lp = androidx.appcompat.widget.Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER }
        toolbar.addView(toolbarMonthView, lp)
        updateToolbarMonthLabel()
    }

    private fun clearToolbarMonthPicker() {
        val activity = activity as? AppCompatActivity ?: return
        val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbarMonthView?.let { toolbar?.removeView(it) }
        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun updateToolbarMonthLabel() {
        val label = toolbarMonthView?.findViewById<TextView>(R.id.tv_toolbar_month) ?: return
        label.text = if (selectedMonth < 0) {
            getString(R.string.filter_all_months)
        } else {
            DateFormatSymbols(Locale.getDefault()).shortMonths[selectedMonth]
        }
    }

    private fun showMonthYearPicker() {
        val adapter = binding.rvTransactions.adapter as? TransactionAdapter ?: return
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 8)
            gravity = Gravity.CENTER
        }
        val monthPicker = NumberPicker(requireContext()).apply {
            val labels = mutableListOf(getString(R.string.filter_all_months))
            labels.addAll(DateFormatSymbols(Locale.getDefault()).months.filter { it.isNotBlank() })
            minValue = 0
            maxValue = labels.size - 1
            displayedValues = labels.toTypedArray()
            value = if (selectedMonth < 0) 0 else selectedMonth + 1
            wrapSelectorWheel = false
        }
        val yearPicker = NumberPicker(requireContext()).apply {
            val current = Calendar.getInstance().get(Calendar.YEAR)
            minValue = 2018
            maxValue = current + 1
            value = selectedYear.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        container.addView(monthPicker)
        container.addView(yearPicker)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pick_month_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedMonth = if (monthPicker.value == 0) -1 else monthPicker.value - 1
                selectedYear = yearPicker.value
                updateToolbarMonthLabel()
                applyFilter(adapter)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyFilter(adapter: TransactionAdapter) {
        val monthList = MainViewModel.filterByMonth(fullList, selectedYear, selectedMonth)
        val filtered = MainViewModel.filterTransactions(monthList, searchQuery, typeFilter)
        adapter.submitGrouped(filtered)

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
        clearToolbarMonthPicker()
        toolbarMonthView = null
        super.onDestroyView()
        _binding = null
    }
}
