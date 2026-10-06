package com.example.moneymanager.viewmodel

import com.example.moneymanager.data.TimeEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeViewModelFormatTest {
    @Test
    fun formatDuration_hoursAndMinutes() {
        assertEquals("1h 20m", TimeViewModel.formatDuration(80))
        assertEquals("2h", TimeViewModel.formatDuration(120))
        assertEquals("45m", TimeViewModel.formatDuration(45))
        assertEquals("0m", TimeViewModel.formatDuration(0))
    }

    @Test
    fun sumLoggedMinutes_includesZeroDurationNotesAsZero() {
        val entries = listOf(
            TimeEntryEntity(
                label = "Work",
                category = "Work",
                durationMinutes = 60,
                startedAt = 1L,
                source = TimeEntryEntity.SOURCE_MANUAL
            ),
            TimeEntryEntity(
                label = "Journal",
                category = "Other",
                durationMinutes = 0,
                startedAt = 2L,
                notes = "end of day",
                source = TimeEntryEntity.SOURCE_VOICE
            ),
            TimeEntryEntity(
                label = "Gym",
                category = "Health",
                durationMinutes = 45,
                startedAt = 3L,
                source = TimeEntryEntity.SOURCE_VOICE
            )
        )
        assertEquals(105, TimeViewModel.sumLoggedMinutes(entries))
    }
}
