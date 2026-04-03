package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.databinding.FragmentCategorySettingsBinding
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.models.Category
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.tabs.TabLayout

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategorySettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: CategoryAdapter
    private var expenseCategories: List<Category> = emptyList()
    private var incomeCategories: List<Category> = emptyList()

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

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.expenseCategories.observe(viewLifecycleOwner) { categories ->
            expenseCategories = categories
            renderSelectedCategories()
        }

        viewModel.incomeCategories.observe(viewLifecycleOwner) { categories ->
            incomeCategories = categories
            renderSelectedCategories()
        }
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(mutableListOf()) { category ->
            viewModel.deleteCategory(category)
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                renderSelectedCategories()
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
                val type = if (selectedTabPosition == 0) {
                    CategoryRepository.TYPE_EXPENSE
                } else {
                    CategoryRepository.TYPE_INCOME
                }
                viewModel.addCategory(categoryName, type)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun renderSelectedCategories() {
        val selectedCategories = if (binding.tabLayout.selectedTabPosition == 0) {
            expenseCategories
        } else {
            incomeCategories
        }
        adapter.updateCategories(selectedCategories)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
