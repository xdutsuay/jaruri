package com.example.moneymanager.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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

        setupClickListeners()
        setupCharts()
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_addTransaction)
        }

        binding.incomeLayout.setOnClickListener {
            binding.incomeChart.visibility = View.VISIBLE
            binding.expenseChart.visibility = View.GONE
        }

        binding.expenseLayout.setOnClickListener {
            binding.incomeChart.visibility = View.GONE
            binding.expenseChart.visibility = View.VISIBLE
        }
    }

    private fun setupCharts() {
        // Sample data for charts
        val expenseEntries = ArrayList<PieEntry>()
        expenseEntries.add(PieEntry(40f, "Food"))
        expenseEntries.add(PieEntry(20f, "Bills"))
        expenseEntries.add(PieEntry(15f, "Transport"))

        val incomeEntries = ArrayList<PieEntry>()
        incomeEntries.add(PieEntry(70f, "Salary"))
        incomeEntries.add(PieEntry(30f, "Freelance"))

        val expenseDataSet = PieDataSet(expenseEntries, "Expenses")
        expenseDataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        expenseDataSet.valueTextColor = Color.BLACK
        expenseDataSet.valueTextSize = 16f

        val incomeDataSet = PieDataSet(incomeEntries, "Income")
        incomeDataSet.colors = ColorTemplate.JOYFUL_COLORS.toList()
        incomeDataSet.valueTextColor = Color.BLACK
        incomeDataSet.valueTextSize = 16f

        val expenseData = PieData(expenseDataSet)
        binding.expenseChart.data = expenseData
        binding.expenseChart.description.isEnabled = false
        binding.expenseChart.isDrawHoleEnabled = true
        binding.expenseChart.setHoleColor(Color.TRANSPARENT)
        binding.expenseChart.animate()

        val incomeData = PieData(incomeDataSet)
        binding.incomeChart.data = incomeData
        binding.incomeChart.description.isEnabled = false
        binding.incomeChart.isDrawHoleEnabled = true
        binding.incomeChart.setHoleColor(Color.TRANSPARENT)
        binding.incomeChart.animate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
