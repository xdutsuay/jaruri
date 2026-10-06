package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.databinding.FragmentCategorySettingsBinding
import com.example.moneymanager.data.CategoryRepository
import com.example.moneymanager.data.CategoryRuleEntity
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
    private var rules: List<CategoryRuleEntity> = emptyList()
    private var showingRules = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySettingsBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupTabs()
        setupAddCategoryButton()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.expenseCategories.observe(viewLifecycleOwner) { categories ->
            expenseCategories = categories
            renderSelected()
        }

        viewModel.incomeCategories.observe(viewLifecycleOwner) { categories ->
            incomeCategories = categories
            renderSelected()
        }

        viewModel.categoryRules.observe(viewLifecycleOwner) { list ->
            rules = list
            if (showingRules) renderSelected()
        }
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(mutableListOf()) { category ->
            if (showingRules) {
                val rule = rules.firstOrNull {
                    getString(R.string.category_rule_row, it.keyword, it.category) == category.name
                }
                if (rule != null) viewModel.deleteCategoryRule(rule)
            } else {
                viewModel.deleteCategory(category)
            }
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showingRules = tab?.position == 2
                renderSelected()
                updateAddLabel()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateAddLabel() {
        val label = binding.btnAddCategory.getChildAt(1) as? android.widget.TextView
        label?.setText(
            if (showingRules) R.string.add_category_rule else R.string.add_category
        )
    }

    private fun setupAddCategoryButton() {
        binding.btnAddCategory.setOnClickListener {
            if (showingRules) showAddRuleDialog() else showAddCategoryDialog()
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

    private fun showAddRuleDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, pad, pad * 2, pad / 2)
        }
        val etKeyword = EditText(requireContext()).apply {
            hint = getString(R.string.category_rule_keyword)
        }
        val etCategory = EditText(requireContext()).apply {
            hint = getString(R.string.category_rule_category)
        }
        val spType = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    CategoryRuleEntity.TYPE_ANY,
                    CategoryRuleEntity.TYPE_EXPENSE,
                    CategoryRuleEntity.TYPE_INCOME
                )
            )
        }
        val hint = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.category_rule_hint)
            textSize = 12f
        }
        container.addView(etKeyword)
        container.addView(etCategory)
        container.addView(spType)
        container.addView(hint)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_category_rule)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val kw = etKeyword.text.toString().trim()
                val cat = etCategory.text.toString().trim()
                if (kw.length < 2 || cat.isBlank()) {
                    Toast.makeText(requireContext(), "Keyword and category required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addCategoryRule(kw, cat, spType.selectedItem.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderSelected() {
        when (binding.tabLayout.selectedTabPosition) {
            2 -> {
                showingRules = true
                adapter.updateCategories(
                    rules.map {
                        Category(
                            getString(R.string.category_rule_row, it.keyword, it.category),
                            "rule"
                        )
                    }
                )
            }
            1 -> {
                showingRules = false
                adapter.updateCategories(incomeCategories)
            }
            else -> {
                showingRules = false
                adapter.updateCategories(expenseCategories)
            }
        }
        updateAddLabel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
