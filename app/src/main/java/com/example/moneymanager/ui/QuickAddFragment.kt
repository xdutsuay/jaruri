package com.example.moneymanager.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.utils.CategoryIcons
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Money Manager–style quick add: category icon grid + numeric keypad.
 */
class QuickAddFragment : Fragment(R.layout.fragment_quick_add) {

    private val viewModel: MainViewModel by activityViewModels()
    private var isIncome = false
    private var selectedCategory: String = "Food"
    private var amountBuffer: String = "0"
    private lateinit var categoryAdapter: CatAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_categories)
        val tvAmount = view.findViewById<TextView>(R.id.tv_amount)
        val tvSelected = view.findViewById<TextView>(R.id.tv_selected_category)
        val etMemo = view.findViewById<EditText>(R.id.et_memo)
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_type)
        val keypad = view.findViewById<GridLayout>(R.id.keypad)

        categoryAdapter = CatAdapter { name ->
            selectedCategory = name
            tvSelected.text = name
            categoryAdapter.selected = name
            categoryAdapter.notifyDataSetChanged()
        }
        rv.layoutManager = GridLayoutManager(requireContext(), 4)
        rv.adapter = categoryAdapter

        toggle.check(R.id.btn_expense)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isIncome = checkedId == R.id.btn_income
            refreshCategories()
        }

        viewModel.expenseCategories.observe(viewLifecycleOwner) { refreshCategories() }
        viewModel.incomeCategories.observe(viewLifecycleOwner) { refreshCategories() }

        tvAmount.text = amountBuffer
        tvSelected.text = selectedCategory
        buildKeypad(keypad, tvAmount, etMemo)
    }

    private fun refreshCategories() {
        val names = if (isIncome) {
            viewModel.incomeCategories.value?.map { it.name }.orEmpty()
        } else {
            viewModel.expenseCategories.value?.map { it.name }.orEmpty()
        }
        if (names.isNotEmpty() && selectedCategory !in names) {
            selectedCategory = names.first()
            view?.findViewById<TextView>(R.id.tv_selected_category)?.text = selectedCategory
        }
        categoryAdapter.submit(names, selectedCategory)
    }

    private fun buildKeypad(grid: GridLayout, tvAmount: TextView, etMemo: EditText) {
        grid.removeAllViews()
        val keys = listOf(
            "1", "2", "3", "Today",
            "4", "5", "6", "+",
            "7", "8", "9", "−",
            ".", "0", "⌫", "✓"
        )
        keys.forEachIndexed { index, label ->
            val btn = MaterialButton(requireContext()).apply {
                text = label
                textSize = if (label == "✓") 18f else 16f
                insetTop = 0
                insetBottom = 0
                minimumHeight = (48 * resources.displayMetrics.density).toInt()
                if (label == "✓") {
                    setBackgroundColor(resources.getColor(R.color.primary_yellow_dark, null))
                    setTextColor(resources.getColor(R.color.button_on_yellow, null))
                }
                setOnClickListener { onKey(label, tvAmount, etMemo) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % 4, 1f)
                rowSpec = GridLayout.spec(index / 4)
                setGravity(Gravity.FILL)
            }
            grid.addView(btn, params)
        }
    }

    private fun onKey(label: String, tvAmount: TextView, etMemo: EditText) {
        when (label) {
            "⌫" -> {
                amountBuffer = if (amountBuffer.length <= 1) "0" else amountBuffer.dropLast(1)
            }
            "." -> {
                if (!amountBuffer.contains('.')) amountBuffer += "."
            }
            "+", "−", "Today" -> {
                // date/operators: Today is default; ignore +/− for v1
            }
            "✓" -> {
                val amount = amountBuffer.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "Enter an amount", Toast.LENGTH_SHORT).show()
                    return
                }
                viewModel.addTransaction(
                    type = if (isIncome) "INCOME" else "EXPENSE",
                    category = selectedCategory,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    memo = etMemo.text.toString().trim()
                )
                findNavController().popBackStack()
                return
            }
            else -> {
                amountBuffer = if (amountBuffer == "0") label else amountBuffer + label
                if (amountBuffer.length > 12) amountBuffer = amountBuffer.take(12)
            }
        }
        tvAmount.text = amountBuffer
    }

    private class CatAdapter(
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<CatAdapter.VH>() {
        private var items: List<String> = emptyList()
        var selected: String = ""

        fun submit(list: List<String>, selectedName: String) {
            items = list
            selected = selectedName
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val bg: View = v.findViewById(R.id.vCatBg)
            val letter: TextView = v.findViewById(R.id.tvCatLetter)
            val name: TextView = v.findViewById(R.id.tvCatName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_chip, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val name = items[position]
            holder.name.text = name
            CategoryIcons.bind(holder.bg, holder.letter, name)
            holder.itemView.alpha = if (name == selected) 1f else 0.7f
            holder.itemView.scaleX = if (name == selected) 1.05f else 1f
            holder.itemView.scaleY = if (name == selected) 1.05f else 1f
            holder.itemView.setOnClickListener { onSelect(name) }
        }

        override fun getItemCount() = items.size
    }
}
