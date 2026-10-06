package com.example.moneymanager.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.TimeEntryEntity
import com.example.moneymanager.utils.journal.JournalTextHelpers
import com.example.moneymanager.utils.journal.VoiceJournalRecognizer
import com.example.moneymanager.viewmodel.TimeViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimeHomeFragment : Fragment(R.layout.fragment_time_home) {

    private val viewModel: TimeViewModel by activityViewModels()
    private var monthLiveData: LiveData<List<TimeEntryEntity>>? = null
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH)

    private var voiceRecognizer: VoiceJournalRecognizer? = null
    private var voiceStatus: TextView? = null
    private var voiceButton: MaterialButton? = null
    private var holdStartedAt = 0L
    private var tapModeListening = false
    private var latestPartial = ""
    private var previewShownForSession = false
    private var expectClientCancel = false
    private var consumeNextUp = false

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.voice_journal_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_time_entries)
        val empty = view.findViewById<TextView>(R.id.tv_time_empty)
        val total = view.findViewById<TextView>(R.id.tv_time_month_total)
        voiceStatus = view.findViewById(R.id.tv_voice_status)
        voiceButton = view.findViewById(R.id.btn_voice_journal)

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
        view.findViewById<MaterialButton>(R.id.btn_open_usage).setOnClickListener {
            findNavController().navigate(R.id.action_time_to_usage)
        }
        setupVoiceButton(voiceButton!!)
    }

    override fun onDestroyView() {
        voiceRecognizer?.destroy()
        voiceRecognizer = null
        voiceStatus = null
        voiceButton = null
        super.onDestroyView()
    }

    private fun setupVoiceButton(button: MaterialButton) {
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    holdStartedAt = System.currentTimeMillis()
                    if (tapModeListening && voiceRecognizer?.isListening == true) {
                        tapModeListening = false
                        consumeNextUp = true
                        stopListeningAndAwaitResult()
                    } else {
                        consumeNextUp = false
                        ensureMicThenListen()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (consumeNextUp) {
                        consumeNextUp = false
                        v.performClick()
                        return@setOnTouchListener true
                    }
                    val heldMs = System.currentTimeMillis() - holdStartedAt
                    if (heldMs < TAP_TOGGLE_MS) {
                        if (voiceRecognizer?.isListening == true) {
                            tapModeListening = true
                            setVoiceStatus(getString(R.string.voice_journal_tap_stop))
                        }
                    } else if (!tapModeListening) {
                        stopListeningAndAwaitResult()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun ensureMicThenListen() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginListening()
    }

    private fun beginListening() {
        val ctx = requireContext()
        if (voiceRecognizer == null) {
            voiceRecognizer = VoiceJournalRecognizer(
                ctx,
                object : VoiceJournalRecognizer.Callbacks {
                    override fun onListeningStarted() {
                        latestPartial = ""
                        previewShownForSession = false
                        setVoiceUiListening(true)
                        setVoiceStatus(getString(R.string.voice_journal_listening))
                    }

                    override fun onPartialResult(text: String) {
                        latestPartial = text
                        setVoiceStatus(text.ifBlank { getString(R.string.voice_journal_listening) })
                    }

                    override fun onFinalResult(text: String) {
                        setVoiceUiListening(false)
                        setVoiceStatus(null)
                        tapModeListening = false
                        expectClientCancel = false
                        openPreviewOnce(text)
                    }

                    override fun onError(message: String) {
                        setVoiceUiListening(false)
                        setVoiceStatus(null)
                        tapModeListening = false
                        if (expectClientCancel) {
                            expectClientCancel = false
                            if (latestPartial.isNotBlank()) {
                                openPreviewOnce(latestPartial)
                                return
                            }
                        }
                        val shown = if (
                            message == VoiceJournalRecognizer.ERR_UNAVAILABLE ||
                            message == VoiceJournalRecognizer.ERR_NETWORK ||
                            message == VoiceJournalRecognizer.ERR_SERVER
                        ) {
                            getString(R.string.voice_journal_unavailable)
                        } else {
                            message
                        }
                        Toast.makeText(requireContext(), shown, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        if (voiceRecognizer?.isAvailable != true) {
            Toast.makeText(
                requireContext(),
                R.string.voice_journal_unavailable,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        latestPartial = ""
        previewShownForSession = false
        tapModeListening = false
        expectClientCancel = false
        voiceRecognizer?.start()
    }

    private fun stopListeningAndAwaitResult() {
        if (voiceRecognizer?.isListening == true) {
            expectClientCancel = true
        }
        voiceRecognizer?.stop()
        setVoiceUiListening(false)
    }

    private fun openPreviewOnce(text: String) {
        val cleaned = JournalTextHelpers.normalizeTranscript(text)
        if (cleaned.isEmpty() || previewShownForSession) return
        previewShownForSession = true
        latestPartial = ""
        showVoicePreview(cleaned)
    }

    private fun setVoiceUiListening(listening: Boolean) {
        voiceButton?.apply {
            text = getString(
                if (listening) R.string.voice_journal_listening else R.string.voice_journal_hold
            )
            backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (listening) R.color.accent_red else R.color.primary_yellow
            )
        }
    }

    private fun setVoiceStatus(text: String?) {
        voiceStatus?.apply {
            if (text.isNullOrBlank()) {
                visibility = View.GONE
                this.text = ""
            } else {
                visibility = View.VISIBLE
                this.text = text
            }
        }
    }

    private fun showVoicePreview(transcript: String) {
        if (!isAdded) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_journal, null, false)
        val etText = dialogView.findViewById<TextInputEditText>(R.id.et_voice_text)
        val etLabel = dialogView.findViewById<TextInputEditText>(R.id.et_voice_label)
        val etHours = dialogView.findViewById<TextInputEditText>(R.id.et_voice_hours)
        val etMinutes = dialogView.findViewById<TextInputEditText>(R.id.et_voice_minutes)
        val spCategory = dialogView.findViewById<Spinner>(R.id.spinner_voice_category)
        val tvDate = dialogView.findViewById<TextView>(R.id.tv_voice_date)

        val normalized = JournalTextHelpers.normalizeTranscript(transcript)
        etText.setText(normalized)
        etLabel.setText(JournalTextHelpers.suggestLabel(normalized))
        JournalTextHelpers.parseDurationMinutes(normalized)?.let { mins ->
            etHours.setText((mins / 60).toString())
            etMinutes.setText((mins % 60).toString())
        }

        spCategory.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            TimeViewModel.DEFAULT_CATEGORIES
        )

        val cal = Calendar.getInstance()
        fun refreshDate() {
            tvDate.text = getString(
                R.string.time_date_picked,
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
            )
        }
        refreshDate()
        tvDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d, 12, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    refreshDate()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.voice_journal_preview_title)
            .setView(dialogView)
            .setPositiveButton(R.string.voice_journal_save) { _, _ ->
                val text = etText.text?.toString().orEmpty()
                if (JournalTextHelpers.normalizeTranscript(text).isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        R.string.voice_journal_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val hours = etHours.text?.toString()?.toIntOrNull() ?: 0
                val mins = etMinutes.text?.toString()?.toIntOrNull() ?: 0
                val totalMins = hours * 60 + mins
                viewModel.addVoiceJournal(
                    text = text,
                    category = spCategory.selectedItem.toString(),
                    durationMinutes = totalMins.takeIf { it > 0 },
                    startedAt = cal.timeInMillis,
                    label = etLabel.text?.toString().orEmpty()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
            val minutes = TimeViewModel.sumLoggedMinutes(list)
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
                    notes = etNotes.text.toString(),
                    source = TimeEntryEntity.SOURCE_MANUAL
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
            val ctx = holder.itemView.context
            holder.label.text = item.label
            holder.duration.text = when {
                item.durationMinutes > 0 -> TimeViewModel.formatDuration(item.durationMinutes)
                else -> ctx.getString(R.string.voice_journal_note_only)
            }
            val voiceBadge = if (JournalTextHelpers.isVoiceSource(item.source)) {
                " · ${ctx.getString(R.string.voice_journal_source_badge)}"
            } else {
                ""
            }
            val notes = item.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            holder.sub.text =
                "${item.category}$voiceBadge · ${df.format(Date(item.startedAt))}$notes"
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        /** Presses shorter than this are treated as tap-to-toggle listen. */
        private const val TAP_TOGGLE_MS = 280L
    }
}
