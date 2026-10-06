package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.TimeEntryEntity
import com.example.moneymanager.utils.journal.JournalTextHelpers
import kotlinx.coroutines.launch
import java.util.Calendar

class TimeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).timeEntryDao()

    val allEntries: LiveData<List<TimeEntryEntity>> = dao.getAll().asLiveData()

    fun entriesForMonth(year: Int, month: Int): LiveData<List<TimeEntryEntity>> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return dao.getInRange(start, end).asLiveData()
    }

    fun addEntry(
        label: String,
        category: String,
        durationMinutes: Int,
        startedAt: Long,
        notes: String = "",
        source: String = TimeEntryEntity.SOURCE_MANUAL
    ) {
        val trimmedLabel = label.trim()
        val trimmedNotes = notes.trim()
        // Manual timed entries need a label and positive duration.
        // Voice/journal notes may omit duration (0) but need text.
        val ok = when (source) {
            TimeEntryEntity.SOURCE_VOICE ->
                (trimmedLabel.isNotEmpty() || trimmedNotes.isNotEmpty()) && durationMinutes >= 0
            else ->
                trimmedLabel.isNotEmpty() && durationMinutes > 0
        }
        if (!ok) return
        viewModelScope.launch {
            val resolvedLabel = when {
                trimmedLabel.isNotEmpty() -> trimmedLabel
                else -> JournalTextHelpers.suggestLabel(trimmedNotes)
            }
            dao.insert(
                TimeEntryEntity(
                    label = resolvedLabel,
                    category = category.trim().ifEmpty { "Other" },
                    durationMinutes = durationMinutes.coerceAtLeast(0),
                    startedAt = startedAt,
                    notes = trimmedNotes,
                    source = source
                )
            )
        }
    }

    /** Persist an edited voice transcript as a journal / time entry (text only). */
    fun addVoiceJournal(
        text: String,
        category: String,
        durationMinutes: Int?,
        startedAt: Long,
        label: String = ""
    ) {
        val notes = JournalTextHelpers.normalizeTranscript(text)
        if (notes.isEmpty()) return
        addEntry(
            label = label.ifBlank { JournalTextHelpers.suggestLabel(notes) },
            category = category,
            durationMinutes = durationMinutes?.coerceAtLeast(0) ?: 0,
            startedAt = startedAt,
            notes = notes,
            source = TimeEntryEntity.SOURCE_VOICE
        )
    }

    fun deleteEntry(entry: TimeEntryEntity) {
        viewModelScope.launch { dao.delete(entry) }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Work",
            "Social",
            "Entertainment",
            "Learning",
            "Health",
            "Commute",
            "Other"
        )

        fun formatDuration(totalMinutes: Int): String {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }

        /** Month total ignores journal-only rows (duration 0). */
        fun sumLoggedMinutes(entries: List<TimeEntryEntity>): Int =
            entries.sumOf { it.durationMinutes.coerceAtLeast(0) }
    }
}
