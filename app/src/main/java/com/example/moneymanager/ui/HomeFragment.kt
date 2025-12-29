package com.example.moneymanager.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.moneymanager.R
import com.example.moneymanager.databinding.FragmentHomeBinding
import com.example.moneymanager.viewmodel.MainViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

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

        // Debug Toast
        Toast.makeText(context, "Home Fragment Active", Toast.LENGTH_SHORT).show()

        val adapter = TransactionAdapter {
            // Click listener
        }
        
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_addTransaction)
        }
        
        // Navigation to Chart Fragment on Summary Click
        binding.incomeLayout.setOnClickListener {
             findNavController().navigate(R.id.nav_chart)
        }
        binding.expenseLayout.setOnClickListener {
             findNavController().navigate(R.id.nav_chart)
        }

        viewModel.allTransactions.observe(viewLifecycleOwner) { list ->
            Log.d("DEBUG_UI", "Observed items: ${list.size}")
            adapter.submitList(list)
            
            if (list.isEmpty()) {
                binding.tvWarning.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            } else {
                binding.tvWarning.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
            }
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
