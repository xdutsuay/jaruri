package com.example.moneymanager.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.TimeEntryEntity
import com.example.moneymanager.viewmodel.TimeViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimeHomeFragment : Fragment(R.layout.fragment_time_home) {

    private val viewModel: TimeViewModel by activityViewModels()
    private var monthLiveData: LiveData<List<TimeEntryEntity>>? = null
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_time_entries)
        val empty = view.findViewById<TextView>(R.id.tv_time_empty)
        val total = view.findViewById<TextView>(R.id.tv_time_month_total)
        val adapter = Adapter(onLongClick = { confirmDelete(it) })
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        setupMonthYearSpinners(view) {
            observeMonth(adapter, empty, total)
        }
        observeMonth(adapter, empty, total)

        view.findViewById<MaterialButton>(R.id.btn_add_time).setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupMonthYearSpinners(view: View, onChanged: () -> Unit) {
        val months = (0..11).map {
            Calendar.getInstance().apply { set(Calendar.MONTH, it) }
                .getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "$it"
        }
        val years = ((selectedYear - 3)..(selectedYear + 1)).toList()
        val spMonth = view.findViewById<Spinner>(R.id.spinner_time_month)
        val spYear = view.findViewById<Spinner>(R.id.spinner_time_year)
        spMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            months
        )
        spYear.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            years.map { it.toString() }
        )
        spMonth.setSelection(selectedMonth)
        spYear.setSelection(years.indexOf(selectedYear).coerceAtLeast(0))

        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedMonth = spMonth.selectedItemPosition
                selectedYear = years[spYear.selectedItemPosition]
                onChanged()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        spMonth.onItemSelectedListener = listener
        spYear.onItemSelectedListener = listener
    }

    private fun observeMonth(
        adapter: Adapter,
        empty: TextView,
        total: TextView
    ) {
        monthLiveData?.removeObservers(viewLifecycleOwner)
        val live = viewModel.entriesForMonth(selectedYear, selectedMonth)
        monthLiveData = live
        live.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            val minutes = list.sumOf { it.durationMinutes }
            total.text = getString(
                R.string.time_month_total,
                TimeViewModel.formatDuration(minutes)
            )
        }
    }

    private fun showAddDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, pad, pad * 2, pad / 2)
        }
        val etLabel = EditText(requireContext()).apply {
            hint = getString(R.string.time_label_hint)
        }
        val spCategory = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                TimeViewModel.DEFAULT_CATEGORIES
            )
        }
        val etHours = EditText(requireContext()).apply {
            hint = getString(R.string.time_hours_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etMinutes = EditText(requireContext()).apply {
            hint = getString(R.string.time_minutes_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etNotes = EditText(requireContext()).apply {
            hint = getString(R.string.time_notes_hint)
        }
        val cal = Calendar.getInstance()
        val tvDate = TextView(requireContext()).apply {
            text = getString(
                R.string.time_date_picked,
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
            )
            setPadding(0, pad / 2, 0, pad / 2)
            setOnClickListener {
                DatePickerDialog(
                    requireContext(),
                    { _, y, m, d ->
                        cal.set(y, m, d, 12, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        text = getString(
                            R.string.time_date_picked,
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
                        )
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        container.addView(etLabel)
        container.addView(spCategory)
        container.addView(etHours)
        container.addView(etMinutes)
        container.addView(tvDate)
        container.addView(etNotes)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.time_add)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val label = etLabel.text.toString().trim()
                val hours = etHours.text.toString().toIntOrNull() ?: 0
                val mins = etMinutes.text.toString().toIntOrNull() ?: 0
                val totalMins = hours * 60 + mins
                if (label.isEmpty() || totalMins <= 0) {
                    Toast.makeText(
                        requireContext(),
                        R.string.time_add_validation,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                viewModel.addEntry(
                    label = label,
                    category = spCategory.selectedItem.toString(),
                    durationMinutes = totalMins,
                    startedAt = cal.timeInMillis,
                    notes = etNotes.text.toString()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(entry: TimeEntryEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(entry.label)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteEntry(entry) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class Adapter(
        private val onLongClick: (TimeEntryEntity) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<TimeEntryEntity> = emptyList()
        private val df = SimpleDateFormat("dd MMM", Locale.getDefault())

        fun submit(list: List<TimeEntryEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.tvTimeLabel)
            val duration: TextView = v.findViewById(R.id.tvTimeDuration)
            val sub: TextView = v.findViewById(R.id.tvTimeSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_time_entry, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.text = item.label
            holder.duration.text = TimeViewModel.formatDuration(item.durationMinutes)
            val notes = item.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            holder.sub.text = "${item.category} · ${df.format(Date(item.startedAt))}$notes"
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
