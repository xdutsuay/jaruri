package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.example.moneymanager.viewmodel.MainViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Debug Toast to verify deployment
        android.widget.Toast.makeText(context, "Home Fragment Active", android.widget.Toast.LENGTH_SHORT).show()

        val adapter = TransactionAdapter {
            // Handle item click (e.g. show details or delete dialog)
            // For now, maybe just show a toast or nothing?
            // Or navigate to edit?
        }
        binding.rvTransactions.adapter = adapter
        // binding.rvTransactions.layoutManager is set by default to LinearLayoutManager if not specified? 
        // No, need to specify it.
        binding.rvTransactions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        // FAB Click Listener
        binding.fabAdd.setOnClickListener {
            // Navigate to AddTransactionFragment
             findNavController().navigate(R.id.action_home_to_addTransaction)
        }

        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvWarning.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.incomeTotal.observe(viewLifecycleOwner) {
            binding.tvIncome.text = String.format("%.0f", it)
        }
        viewModel.expenseTotal.observe(viewLifecycleOwner) {
             binding.tvExpense.text = String.format("%.0f", it)
        }
        viewModel.balance.observe(viewLifecycleOwner) {
             binding.tvBalance.text = String.format("%.0f", it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
