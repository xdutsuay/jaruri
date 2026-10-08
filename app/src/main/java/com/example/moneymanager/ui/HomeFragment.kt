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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.example.moneymanager.utils.TransactionAccounting
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
    private var accounts: List<AccountEntity> = emptyList()
    private var searchQuery: String = ""
    private var typeFilter: String = "ALL"
    private var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var fiscalMode: String = com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
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
            onClick = { tx -> openTransaction(tx) },
            onLongClick = { tx -> showTransactionActions(tx) },
            onCategorize = { tx -> showCategorizeDialog(tx) },
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
                R.id.btn_filter_review -> "REVIEW"
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
        binding.fabAdd.setOnLongClickListener {
            viewModel.repeatLastExpense { ok ->
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.money_repeat_last_ok else R.string.money_repeat_last_empty,
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
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

        viewModel.allAccounts.observe(viewLifecycleOwner) { list ->
            accounts = list.orEmpty()
            applyFilter(adapter)
        }

        viewModel.currencySymbol.observe(viewLifecycleOwner) {
            applyFilter(adapter)
        }

        viewModel.fiscalYearMode.observe(viewLifecycleOwner) { mode ->
            fiscalMode = mode ?: com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
            selectedYear = com.example.moneymanager.utils.FiscalYearHelpers.defaultYearValue(fiscalMode)
            updateToolbarMonthLabel()
            applyFilter(adapter)
        }

        viewModel.needsCategoryReviewCount.observe(viewLifecycleOwner) { count ->
            if (count != null && count > 0 && typeFilter != "REVIEW") {
                binding.tvWarning.text = getString(R.string.needs_category_hint, count)
                // Only show as soft hint when list is otherwise visible and empty-state not active
                if (fullList.isNotEmpty() && binding.rvTransactions.visibility == View.VISIBLE) {
                    binding.tvWarning.visibility = View.VISIBLE
                    binding.tvWarning.setBackgroundColor(
                        requireContext().getColor(R.color.review_banner_bg)
                    )
                }
            }
        }

        binding.tvRunway.visibility = View.GONE
    }

    private fun openTransaction(tx: TransactionEntity) {
        findNavController().navigate(
            R.id.action_home_to_addTransaction,
            bundleOf("transactionId" to tx.id)
        )
    }

    private fun showCategorizeDialog(tx: TransactionEntity) {
        val categories = if (tx.type == "INCOME") {
            viewModel.incomeCategories.value?.map { it.name }.orEmpty()
        } else {
            viewModel.expenseCategories.value?.map { it.name }.orEmpty()
        }.ifEmpty {
            listOf("Others", "Food", "Shopping", "Bills", "Transfer")
        }
        val labels = categories.toTypedArray()
        val preselect = categories.indexOfFirst { it.equals(tx.category, ignoreCase = true) }
            .coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.categorize_title)
            .setSingleChoiceItems(labels, preselect) { dialog, which ->
                viewModel.setCategory(tx, labels[which])
                dialog.dismiss()
            }
            .setNeutralButton(R.string.update_transaction) { _, _ -> openTransaction(tx) }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun showTransactionActions(tx: TransactionEntity) {
        val options = mutableListOf(
            getString(R.string.delete),
            getString(R.string.money_rename_payee)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(tx.memo.substringBefore(" ·").substringBefore(" |").ifBlank { tx.category })
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> confirmDelete(tx)
                    1 -> showRenamePayeeDialog(tx)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenamePayeeDialog(tx: TransactionEntity) {
        val payeeId = tx.payeeId
        if (payeeId == null) {
            Toast.makeText(requireContext(), R.string.money_rename_payee_none, Toast.LENGTH_SHORT).show()
            return
        }
        val input = android.widget.EditText(requireContext()).apply {
            setText(tx.memo.substringBefore(" ·").substringBefore(" |").trim())
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.money_rename_payee)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.renamePayee(payeeId, name) { ok ->
                        Toast.makeText(
                            requireContext(),
                            if (ok) R.string.money_rename_payee_ok else R.string.money_rename_payee_none,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(tx: TransactionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_transaction_title)
            .setMessage(R.string.delete_transaction_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteTransaction(tx) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateToolbarMonthLabel() {
        val label = toolbarMonthView?.findViewById<TextView>(R.id.tv_toolbar_month) ?: return
        val monthPart = if (selectedMonth < 0) {
            getString(R.string.filter_all_months)
        } else {
            DateFormatSymbols(Locale.getDefault()).shortMonths[selectedMonth]
        }
        label.text = if (fiscalMode == com.example.moneymanager.data.SettingsRepository.FY_APR_MAR) {
            val fy = "FY $selectedYear-${((selectedYear + 1) % 100).toString().padStart(2, '0')}"
            "$monthPart · $fy"
        } else {
            "$monthPart $selectedYear"
        }
    }

    private fun showMonthYearPicker() {
        val adapter = binding.rvTransactions.adapter as? TransactionAdapter ?: return
        val content = layoutInflater.inflate(R.layout.dialog_month_year, null, false)
        val monthPicker = content.findViewById<NumberPicker>(R.id.np_month)
        val yearPicker = content.findViewById<NumberPicker>(R.id.np_year)

        val monthLabels = mutableListOf(getString(R.string.filter_all_months))
        monthLabels.addAll(
            DateFormatSymbols(Locale.getDefault()).shortMonths.filter { it.isNotBlank() }
        )
        monthPicker.minValue = 0
        monthPicker.maxValue = monthLabels.size - 1
        monthPicker.displayedValues = monthLabels.toTypedArray()
        monthPicker.value = if (selectedMonth < 0) 0 else selectedMonth + 1
        monthPicker.wrapSelectorWheel = false
        styleNumberPicker(monthPicker)

        val yearLabels = com.example.moneymanager.utils.FiscalYearHelpers.yearLabels(fiscalMode)
        yearPicker.minValue = 0
        yearPicker.maxValue = yearLabels.size - 1
        yearPicker.displayedValues = yearLabels.map { it.label }.toTypedArray()
        yearPicker.value = yearLabels.indexOfFirst { it.value == selectedYear }.coerceAtLeast(0)
        yearPicker.wrapSelectorWheel = false
        styleNumberPicker(yearPicker)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_Jaruri_Dialog
        )
            .setTitle(R.string.pick_month_title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedMonth = if (monthPicker.value == 0) -1 else monthPicker.value - 1
                selectedYear = yearLabels.getOrNull(yearPicker.value)?.value ?: selectedYear
                updateToolbarMonthLabel()
                applyFilter(adapter)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Make NumberPicker selection text dark and large enough to read. */
    private fun styleNumberPicker(picker: NumberPicker) {
        val ink = requireContext().getColor(R.color.text_primary)
        picker.setBackgroundColor(requireContext().getColor(R.color.surface_white))
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            picker.setTextColor(ink)
            picker.setTextSize(22f * resources.displayMetrics.scaledDensity)
        }
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is android.widget.EditText) {
                child.setTextColor(ink)
                child.textSize = 22f
                child.typeface = android.graphics.Typeface.DEFAULT_BOLD
                child.isFocusable = false
                child.isClickable = false
            }
        }
        try {
            val divider = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            divider.isAccessible = true
            divider.set(
                picker,
                android.graphics.drawable.ColorDrawable(
                    requireContext().getColor(R.color.outline_soft)
                )
            )
            val height = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight")
            height.isAccessible = true
            height.setInt(picker, (2f * resources.displayMetrics.density).toInt().coerceAtLeast(2))
        } catch (_: Exception) {
            // OEM NumberPicker internals vary; text styling above still applies.
        }
    }

    private fun applyFilter(adapter: TransactionAdapter) {
        val monthList = MainViewModel.filterByMonth(fullList, selectedYear, selectedMonth, fiscalMode)
        val filtered = MainViewModel.filterTransactions(monthList, searchQuery, typeFilter)
        adapter.submitGrouped(filtered)

        val symbol = viewModel.currencySymbol.value ?: "₹"
        val totals = TransactionAccounting.sumTotals(monthList)
        binding.tvIncome.text = formatCurrency(totals.income, symbol)
        binding.tvExpense.text = formatCurrency(totals.expense, symbol)
        binding.tvBalance.text = formatCurrency(totals.balance, symbol)
        updateCashHero(symbol)

        val reviewCount = fullList.count { it.needsCategoryReview }
        when {
            fullList.isEmpty() -> {
                binding.tvWarning.text = getString(R.string.empty_ledger_hint)
                binding.tvWarning.setBackgroundColor(requireContext().getColor(R.color.empty_banner_bg))
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            monthList.isEmpty() && selectedMonth >= 0 -> {
                binding.tvWarning.text = getString(R.string.empty_month_hint)
                binding.tvWarning.setBackgroundColor(requireContext().getColor(R.color.empty_banner_bg))
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            filtered.isEmpty() -> {
                binding.tvWarning.text = getString(R.string.empty_month_hint)
                binding.tvWarning.setBackgroundColor(requireContext().getColor(R.color.empty_banner_bg))
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
            reviewCount > 0 -> {
                binding.tvWarning.text = getString(R.string.needs_category_hint, reviewCount)
                binding.tvWarning.setBackgroundColor(requireContext().getColor(R.color.review_banner_bg))
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.VISIBLE
            }
            else -> {
                binding.tvWarning.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCashHero(symbol: String) {
        val banks = accounts.filter {
            it.type == AccountEntity.TYPE_BANK || it.type == AccountEntity.TYPE_CASH
        }
        // One SMS can mention someone else's account. Only accounts we keep
        // seeing belong in the home total.
        val repeated = banks.filter { it.observationCount >= 5 }
        val banksForTotal = if (repeated.isNotEmpty()) repeated else banks
        val show = banksForTotal.any { bank ->
            bank.lastReportedBalance != null || kotlin.math.abs(bank.balance) >= 1.0
        }
        val visibility = if (show) View.VISIBLE else View.GONE
        binding.tvCashLabel.visibility = visibility
        binding.tvCashBalance.visibility = visibility
        binding.tvCashCaption.visibility = visibility
        if (show) {
            val total = banksForTotal.sumOf { bank ->
                if (bank.lastReportedKind == "AVAILABLE" && bank.lastReportedBalance != null) {
                    bank.lastReportedBalance
                } else {
                    bank.balance
                }
            }
            binding.tvCashBalance.text = formatCurrency(total, symbol)
            val owing = accounts.filter { it.isCreditCard }.sumOf { it.balance.coerceAtLeast(0.0) }
            binding.tvCashCaption.text = if (owing >= 1.0) {
                getString(R.string.home_cards_owing, symbol, owing)
            } else {
                getString(R.string.home_from_sms)
            }
        }
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
