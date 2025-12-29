package com.example.moneymanager.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.viewmodel.MainViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.floatingactionbutton.FloatingActionButton

import androidx.fragment.app.activityViewModels

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvRecentTransactions)
        val adapter = TransactionAdapter { 
            // Handle Item Click (Optional: Edit Logic)
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        // Observe Data
        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            updateChart(view, list)
        }

        viewModel.incomeTotal.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.tvIncomeVal).text = "$it"
        }
        viewModel.expenseTotal.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.tvExpenseVal).text = "$it"
        }
        viewModel.balance.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.tvBalanceVal).text = "$it"
        }

        // Navigation
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_addEdit)
        }
    }

    private fun updateChart(view: View, list: List<com.example.moneymanager.data.TransactionEntity>) {
        val chart = view.findViewById<PieChart>(R.id.pieChart)
        val expenses = list.filter { it.type == "EXPENSE" }
        if (expenses.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val categoryMap = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val entries = categoryMap.map { PieEntry(it.value.toFloat(), it.key) }
        
        val dataSet = PieDataSet(entries, "Expenses")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 14f
        
        chart.data = PieData(dataSet)
        chart.description.isEnabled = false
        chart.centerText = "Expenses"
        chart.animateY(1000)
        chart.invalidate()
    }
}
