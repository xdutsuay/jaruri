package com.example.moneymanager.wellbeing

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Thin wrapper around [UsageStatsManager]. Stores nothing — callers persist aggregates.
 * OEM builds may return empty lists even when permission is granted; that is handled as empty.
 */
class UsageStatsReader(private val context: Context) {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Query daily foreground totals for each local calendar day in
     * `[startInclusiveMs, endExclusiveMs)`.
     */
    fun queryDailyForeground(
        startInclusiveMs: Long,
        endExclusiveMs: Long
    ): List<UsageAggregator.ForegroundSlice> {
        if (!UsagePermission.hasUsageAccess(context)) return emptyList()
        if (endExclusiveMs <= startInclusiveMs) return emptyList()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val slices = mutableListOf<UsageAggregator.ForegroundSlice>()
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = startInclusiveMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (dayStart.timeInMillis < endExclusiveMs) {
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val rangeEnd = minOf(dayEnd.timeInMillis, endExclusiveMs)
            val dayKey = dayFormat.format(dayStart.time)
            try {
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStart.timeInMillis,
                    rangeEnd
                )
                if (!stats.isNullOrEmpty()) {
                    for (stat in stats) {
                        val pkg = stat.packageName ?: continue
                        val ms = stat.totalTimeInForeground
                        if (ms > 0L) {
                            slices += UsageAggregator.ForegroundSlice(dayKey, pkg, ms)
                        }
                    }
                }
            } catch (_: SecurityException) {
                return emptyList()
            } catch (_: Exception) {
                // OEM variance — skip this day
            }
            dayStart.add(Calendar.DAY_OF_MONTH, 1)
        }
        return slices
    }

    companion object {
        fun startOfTodayMs(): Long =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        fun startOfLocalDayOffset(daysAgo: Int): Long =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, -daysAgo)
            }.timeInMillis

        fun formatDay(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }.format(ms)
    }
}
