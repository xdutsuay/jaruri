package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moneymanager.databinding.FragmentChartBinding
import com.example.moneymanager.viewmodel.MainViewModel
import java.util.ArrayList

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChart()

        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            updateChartData(list)
        }
    }

    private fun setupChart() {
        binding.pieChart.apply {
            description.isEnabled = false
            holeRadius = 60f
            transparentCircleRadius = 65f
            setHoleColor(android.graphics.Color.WHITE)
            legend.isEnabled = false // We have a custom list
        }
    }

    private fun updateChartData(list: List<com.example.moneymanager.data.TransactionEntity>) {
        if (list.isEmpty()) {
            binding.pieChart.clear()
            return
        }

        // Filter Expenses (Charts usually show expense breakdown)
        // Or Income? Screenshot 1 says "Income", Screenshot 4 says "Expenses".
        // For now, let's show Expenses by default or based on a toggle?
        // Detailed check: Screenshot 1 is Income Donut. Screenshot 4 is Expense Donut.
        // I will default to Expenses for now as it's more common to track.
        // TODO: Add toggle in future.
        
        val expenses = list.filter { it.type == "EXPENSE" }
        val categoryMap = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val entries = ArrayList<com.github.mikephil.charting.data.PieEntry>()
        val colors = ArrayList<Int>()
        
        // Simple colors
        val colorTemplate = listOf(
            android.graphics.Color.parseColor("#EF5350"), // Red
            android.graphics.Color.parseColor("#42A5F5"), // Blue
            android.graphics.Color.parseColor("#66BB6A"), // Green
            android.graphics.Color.parseColor("#FFCA28"), // Amber
            android.graphics.Color.parseColor("#AB47BC"), // Purple
            android.graphics.Color.parseColor("#8D6E63")  // Brown
        )

        var i = 0
        categoryMap.forEach { (category, amount) ->
            if (amount > 0) {
                entries.add(com.github.mikephil.charting.data.PieEntry(amount.toFloat(), category))
                colors.add(colorTemplate[i % colorTemplate.size])
                i++
            }
        }

        val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "Expenses")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.valueTextColor = android.graphics.Color.TRANSPARENT // Hide values on slices for cleaner look, or show them? Screenshot shows percentage inside list.

        val data = com.github.mikephil.charting.data.PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.centerText = "Expenses\n${String.format("%.0f", expenses.sumOf { it.amount })}"
        binding.pieChart.setCenterTextSize(16f)
        binding.pieChart.invalidate() // refresh
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
