package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.databinding.FragmentCategorySettingsBinding
import com.example.moneymanager.models.Category
import com.google.android.material.tabs.TabLayout

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategorySettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CategoryAdapter
    private val expenseCategories = mutableListOf(
        Category("Food", "expense"),
        Category("Bills", "expense"),
        Category("Transportation", "expense"),
        Category("Home", "expense"),
        Category("Car", "expense"),
        Category("Entertainment", "expense")
    )
    private val incomeCategories = mutableListOf(
        Category("Salary", "income"),
        Category("Business", "income"),
        Category("Gift", "income")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupTabs()
        setupAddCategoryButton()

        // Set initial list
        adapter.updateCategories(expenseCategories)

        return root
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(mutableListOf()) { category ->
            // onDelete logic
            val currentList = if (binding.tabLayout.selectedTabPosition == 0) expenseCategories else incomeCategories
            currentList.remove(category)
            adapter.updateCategories(currentList.toList()) // Pass a copy
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> adapter.updateCategories(expenseCategories)
                    1 -> adapter.updateCategories(incomeCategories)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupAddCategoryButton() {
        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add New Category")

        val input = EditText(requireContext())
        input.hint = "Category Name"
        builder.setView(input)

        builder.setPositiveButton("Add") { dialog, _ ->
            val categoryName = input.text.toString()
            if (categoryName.isNotEmpty()) {
                val selectedTabPosition = binding.tabLayout.selectedTabPosition
                val type = if (selectedTabPosition == 0) "expense" else "income"
                val newCategory = Category(categoryName, type)

                if (type == "expense") {
                    expenseCategories.add(newCategory)
                    adapter.updateCategories(expenseCategories)
                } else {
                    incomeCategories.add(newCategory)
                    adapter.updateCategories(incomeCategories)
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
