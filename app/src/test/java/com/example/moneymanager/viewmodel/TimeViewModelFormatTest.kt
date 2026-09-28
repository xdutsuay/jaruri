package com.example.moneymanager.viewmodel

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
}
