package com.example.moneymanager.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.databinding.FragmentChartBinding
import com.example.moneymanager.utils.ChartDisplayWeights
import com.example.moneymanager.utils.TransactionAccounting
import com.example.moneymanager.viewmodel.MainViewModel
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.tabs.TabLayout
import java.util.Calendar
import java.util.Locale

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var currentFilteredTransactions: List<TransactionEntity> = emptyList()
    private var allTransactions: List<TransactionEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupSpinners()
        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            allTransactions = list
            updateCompareAndBreakdown()
        }
    }

    private fun setupTabs() {
        binding.tabLayoutChart.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateChart()
                updateCompareAndBreakdown()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSpinners() {
        val fyMode = viewModel.fiscalYearMode.value
            ?: com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
        val yearLabels = com.example.moneymanager.utils.FiscalYearHelpers.yearLabels(fyMode)
        val yearAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            yearLabels.map { it.label }
        )
        binding.spinnerYear.adapter = yearAdapter
        val defaultYear = com.example.moneymanager.utils.FiscalYearHelpers.defaultYearValue(fyMode)
        val defaultIdx = yearLabels.indexOfFirst { it.value == defaultYear }.coerceAtLeast(0)
        binding.spinnerYear.setSelection(defaultIdx)
        // Stash year values for later lookup
        binding.spinnerYear.tag = yearLabels.map { it.value }

        val monthAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.months,
            android.R.layout.simple_spinner_item
        )
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonth.adapter = monthAdapter
        binding.spinnerMonth.setSelection(Calendar.getInstance().get(Calendar.MONTH))

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                observeData()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerMonth.onItemSelectedListener = spinnerListener
        binding.spinnerYear.onItemSelectedListener = spinnerListener

        viewModel.fiscalYearMode.observe(viewLifecycleOwner) { mode ->
            val labels = com.example.moneymanager.utils.FiscalYearHelpers.yearLabels(
                mode ?: com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
            )
            binding.spinnerYear.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                labels.map { it.label }
            )
            binding.spinnerYear.tag = labels.map { it.value }
            val def = com.example.moneymanager.utils.FiscalYearHelpers.defaultYearValue(
                mode ?: com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
            )
            val idx = labels.indexOfFirst { it.value == def }.coerceAtLeast(0)
            binding.spinnerYear.setSelection(idx)
            observeData()
        }
    }

    private fun observeData() {
        val (year, month) = selectedYearMonth()
        viewModel.getTransactionsForMonth(year, month).observe(viewLifecycleOwner) { list ->
            currentFilteredTransactions = list
            updateChart()
            updateCompareAndBreakdown()
        }
    }

    private fun selectedYearMonth(): Pair<Int, Int> {
        @Suppress("UNCHECKED_CAST")
        val yearValues = binding.spinnerYear.tag as? List<Int>
        val year = yearValues?.getOrNull(binding.spinnerYear.selectedItemPosition)
            ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = binding.spinnerMonth.selectedItemPosition
        return year to month
    }

    private fun updateCompareAndBreakdown() {
        if (_binding == null) return
        val symbol = viewModel.currencySymbol.value ?: "₹"
        val (year, month) = selectedYearMonth()
        val fyMode = viewModel.fiscalYearMode.value
            ?: com.example.moneymanager.data.SettingsRepository.FY_CALENDAR
        val compare = MainViewModel.monthComparison(allTransactions, year, month, fyMode)
        val isExpense = binding.tabLayoutChart.selectedTabPosition == 0
        val delta = if (isExpense) compare.expenseDelta else compare.incomeDelta
        val sign = if (delta > 0) "+" else ""
        val compareText = if (isExpense) {
            getString(
                R.string.chart_vs_prev_expense,
                symbol, compare.current.expense,
                symbol, compare.previous.expense,
                sign, delta
            )
        } else {
            getString(
                R.string.chart_vs_prev_income,
                symbol, compare.current.income,
                symbol, compare.previous.income,
                sign, delta
            )
        }
        binding.tvMonthCompare.visibility = View.VISIBLE
        binding.tvMonthCompare.text = compareText

        val typeToFilter = if (isExpense) "EXPENSE" else "INCOME"
        val categoryMap = currentFilteredTransactions
            .filter { it.type == typeToFilter && !TransactionAccounting.isNeutralForTotals(it) }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        if (categoryMap.isEmpty()) {
            binding.tvCategoryBreakdown.visibility = View.GONE
        } else {
            binding.tvCategoryBreakdown.visibility = View.VISIBLE
            val lines = buildString {
                append(getString(R.string.chart_breakdown_header))
                append('\n')
                categoryMap.take(8).forEach { (cat, total) ->
                    append(cat)
                    append("  ")
                    append(symbol)
                    append(String.format(Locale.getDefault(), "%.0f", total))
                    append('\n')
                }
            }.trimEnd()
            binding.tvCategoryBreakdown.text = lines
        }
    }

    private fun updateChart() {
        val isExpense = binding.tabLayoutChart.selectedTabPosition == 0
        val typeToFilter = if (isExpense) "EXPENSE" else "INCOME"

        val filteredByType = currentFilteredTransactions.filter {
            it.type == typeToFilter && !TransactionAccounting.isNeutralForTotals(it)
        }

        val categoryMap = filteredByType.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Visual weights: floor tiny slices so labels stay readable; amounts in labels stay true.
        val slices = ChartDisplayWeights.forPie(categoryMap, minShare = 0.045f, maxSlices = 7)
        val entries = ArrayList<PieEntry>()
        val actualByIndex = ArrayList<Double>()
        slices.forEach { slice ->
            entries.add(PieEntry(slice.displayValue, slice.label))
            actualByIndex.add(slice.actualValue)
        }

        if (entries.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.setNoDataText("No transactions for this selection")
            return
        }

        val symbol = viewModel.currencySymbol.value ?: "₹"
        val dataSet = PieDataSet(entries, if (isExpense) "Expenses" else "Income")
        dataSet.colors = if (isExpense) ColorTemplate.MATERIAL_COLORS.toList() else ColorTemplate.JOYFUL_COLORS.toList()
        dataSet.valueTextColor = Color.parseColor("#212121")
        dataSet.valueTextSize = 12f
        dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.35f
        dataSet.valueLineColor = Color.parseColor("#616161")
        dataSet.sliceSpace = 1.5f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                val idx = entries.indexOf(pieEntry)
                val actual = if (idx >= 0) actualByIndex[idx] else value.toDouble()
                return symbol + String.format(Locale.getDefault(), "%.0f", actual)
            }
        }

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.isDrawHoleEnabled = true
        binding.pieChart.setHoleColor(Color.WHITE)
        binding.pieChart.setEntryLabelColor(Color.parseColor("#212121"))
        binding.pieChart.setEntryLabelTextSize(11f)
        binding.pieChart.setUsePercentValues(false)
        binding.pieChart.setExtraOffsets(12f, 8f, 12f, 8f)
        binding.pieChart.legend.textColor = Color.parseColor("#212121")
        binding.pieChart.legend.textSize = 12f
        binding.pieChart.setNoDataTextColor(Color.parseColor("#616161"))
        binding.pieChart.animateY(800)
        binding.pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
