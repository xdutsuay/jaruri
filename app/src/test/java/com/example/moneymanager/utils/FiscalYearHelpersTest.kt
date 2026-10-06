package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FiscalYearHelpersTest {

    @Test
    fun fyStartYearAprVsJan() {
        assertEquals(2025, FiscalYearHelpers.fyStartYear(2025, Calendar.APRIL))
        assertEquals(2025, FiscalYearHelpers.fyStartYear(2025, Calendar.DECEMBER))
        assertEquals(2024, FiscalYearHelpers.fyStartYear(2025, Calendar.JANUARY))
        assertEquals(2024, FiscalYearHelpers.fyStartYear(2025, Calendar.MARCH))
    }

    @Test
    fun yearLabelsAprMar() {
        val labels = FiscalYearHelpers.yearLabels(
            FiscalYearHelpers.MODE_APR_MAR,
            fromYear = 2024,
            toYearInclusive = 2025,
            now = calMillis(2025, Calendar.JUNE, 15)
        )
        assertTrue(labels.any { it.label.startsWith("FY 2024") })
        assertTrue(labels.any { it.value == 2024 })
    }

    @Test
    fun monthRangeMapsJanIntoNextCalendarYearForFy() {
        // FY 2025-26, January → calendar Jan 2026
        val jan = FiscalYearHelpers.monthRange(
            FiscalYearHelpers.MODE_APR_MAR, 2025, Calendar.JANUARY
        )
        val cal = Calendar.getInstance().apply { timeInMillis = jan.startMillis }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))

        val apr = FiscalYearHelpers.monthRange(
            FiscalYearHelpers.MODE_APR_MAR, 2025, Calendar.APRIL
        )
        val cal2 = Calendar.getInstance().apply { timeInMillis = apr.startMillis }
        assertEquals(2025, cal2.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, cal2.get(Calendar.MONTH))
    }

    @Test
    fun fiscalYearRangeAprToMar() {
        val range = FiscalYearHelpers.fiscalYearRange(2025)
        val start = Calendar.getInstance().apply { timeInMillis = range.startMillis }
        val end = Calendar.getInstance().apply { timeInMillis = range.endMillis }
        assertEquals(2025, start.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, start.get(Calendar.MONTH))
        assertEquals(2026, end.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, end.get(Calendar.MONTH))
    }

    @Test
    fun isInSelectedMonthRespectsMode() {
        val jan2026 = calMillis(2026, Calendar.JANUARY, 10)
        assertTrue(
            FiscalYearHelpers.isInSelectedMonth(
                jan2026, FiscalYearHelpers.MODE_APR_MAR, 2025, Calendar.JANUARY
            )
        )
        assertFalse(
            FiscalYearHelpers.isInSelectedMonth(
                jan2026, FiscalYearHelpers.MODE_CALENDAR, 2025, Calendar.JANUARY
            )
        )
    }

    private fun calMillis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis
    }
}
