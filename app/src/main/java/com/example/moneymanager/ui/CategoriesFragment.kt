package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.databinding.FragmentCategorySettingsBinding
import com.google.android.material.tabs.TabLayout

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategorySettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorySettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupTabs()
        setupList()

        return root
    }

    private fun setupList() {
        // Sample Data matching Screenshot 3
        val sampleCategories = listOf(
            CategoryAdapter.CategoryItem("Food", 0),
            CategoryAdapter.CategoryItem("Bills", 0),
            CategoryAdapter.CategoryItem("Transportation", 0),
            CategoryAdapter.CategoryItem("Home", 0),
            CategoryAdapter.CategoryItem("Car", 0),
            CategoryAdapter.CategoryItem("Entertainment", 0)
        )
        
        binding.rvCategories.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = CategoryAdapter(sampleCategories)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // TODO: Update list based on tab
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
