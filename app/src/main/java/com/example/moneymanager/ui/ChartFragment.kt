package com.example.moneymanager.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.moneymanager.R
import com.example.moneymanager.databinding.FragmentChartBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.tabs.TabLayout
import java.util.Calendar

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

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
        updateChart()
    }

    private fun setupTabs() {
        binding.tabLayoutChart.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateChart()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSpinners() {
        // Year Spinner
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (2020..currentYear).map { it.toString() }.toTypedArray()
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
        binding.spinnerYear.adapter = yearAdapter
        binding.spinnerYear.setSelection(years.indexOf(currentYear.toString()))

        // Month Spinner
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
                updateChart()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerMonth.onItemSelectedListener = spinnerListener
        binding.spinnerYear.onItemSelectedListener = spinnerListener
    }

    private fun updateChart() {
        val isExpense = binding.tabLayoutChart.selectedTabPosition == 0

        val entries = ArrayList<PieEntry>()
        if (isExpense) {
            entries.add(PieEntry(40f, "Food"))
            entries.add(PieEntry(20f, "Bills"))
            entries.add(PieEntry(15f, "Transport"))
        } else {
            entries.add(PieEntry(70f, "Salary"))
            entries.add(PieEntry(30f, "Freelance"))
        }

        val dataSet = PieDataSet(entries, if (isExpense) "Expenses" else "Income")
        dataSet.colors = if (isExpense) ColorTemplate.MATERIAL_COLORS.toList() else ColorTemplate.JOYFUL_COLORS.toList()
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 16f

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.isDrawHoleEnabled = true
        binding.pieChart.setHoleColor(Color.TRANSPARENT)
        binding.pieChart.animateY(1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
