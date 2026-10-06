package com.example.moneymanager.utils

import java.util.Calendar

/**
 * Calendar year vs Indian financial year (Apr–Mar) helpers.
 * Pure Kotlin — no Android deps.
 */
object FiscalYearHelpers {

    const val MODE_CALENDAR = "CALENDAR"
    const val MODE_APR_MAR = "APR_MAR"

    data class YearLabel(val value: Int, val label: String)

    data class MonthRange(val startMillis: Long, val endMillis: Long)

    /** FY starting year for a given calendar date (Apr 2025 → 2025; Jan 2026 → 2025). */
    fun fyStartYear(calendarYear: Int, monthZeroBased: Int): Int =
        if (monthZeroBased >= Calendar.APRIL) calendarYear else calendarYear - 1

    fun currentFyStartYear(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return fyStartYear(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    /**
     * Year picker entries.
     * CALENDAR: 2020, 2021, … as plain years.
     * APR_MAR: FY labels "FY 2024-25" with value = start year (2024).
     */
    fun yearLabels(
        mode: String,
        fromYear: Int = 2018,
        toYearInclusive: Int = Calendar.getInstance().get(Calendar.YEAR) + 1,
        now: Long = System.currentTimeMillis()
    ): List<YearLabel> {
        return if (mode == MODE_APR_MAR) {
            val maxFy = currentFyStartYear(now) + 1
            val minFy = fromYear
            (minFy..maxFy).map { start ->
                YearLabel(start, "FY $start-${((start + 1) % 100).toString().padStart(2, '0')}")
            }
        } else {
            (fromYear..toYearInclusive).map { YearLabel(it, it.toString()) }
        }
    }

    /**
     * Absolute calendar month range for a selected year value + month.
     * For APR_MAR: [yearValue] is FY start year; Jan–Mar map to yearValue+1.
     */
    fun monthRange(
        mode: String,
        yearValue: Int,
        monthZeroBased: Int
    ): MonthRange {
        val calYear = if (mode == MODE_APR_MAR && monthZeroBased < Calendar.APRIL) {
            yearValue + 1
        } else {
            yearValue
        }
        val start = Calendar.getInstance().apply {
            clear()
            set(calYear, monthZeroBased, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return MonthRange(start.timeInMillis, end.timeInMillis)
    }

    /** Full FY Apr 1 yearValue 00:00 → Mar 31 yearValue+1 23:59:59.999 */
    fun fiscalYearRange(fyStartYear: Int): MonthRange {
        val start = Calendar.getInstance().apply {
            clear()
            set(fyStartYear, Calendar.APRIL, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            clear()
            set(fyStartYear + 1, Calendar.MARCH, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return MonthRange(start.timeInMillis, end.timeInMillis)
    }

    /**
     * Whether [timestamp] falls in the selected month under [mode].
     * [yearValue] is calendar year (CALENDAR) or FY start year (APR_MAR).
     */
    fun isInSelectedMonth(
        timestamp: Long,
        mode: String,
        yearValue: Int,
        monthZeroBased: Int
    ): Boolean {
        if (monthZeroBased < 0) return true
        val range = monthRange(mode, yearValue, monthZeroBased)
        return timestamp in range.startMillis..range.endMillis
    }

    fun defaultYearValue(mode: String, now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return if (mode == MODE_APR_MAR) {
            fyStartYear(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        } else {
            cal.get(Calendar.YEAR)
        }
    }
}
