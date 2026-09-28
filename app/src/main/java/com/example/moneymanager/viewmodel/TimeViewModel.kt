package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.TimeEntryEntity
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
        notes: String = ""
    ) {
        if (label.isBlank() || durationMinutes <= 0) return
        viewModelScope.launch {
            dao.insert(
                TimeEntryEntity(
                    label = label.trim(),
                    category = category.trim().ifEmpty { "Other" },
                    durationMinutes = durationMinutes,
                    startedAt = startedAt,
                    notes = notes.trim()
                )
            )
        }
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
    }
}
