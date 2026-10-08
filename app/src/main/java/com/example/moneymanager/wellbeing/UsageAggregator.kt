package com.example.moneymanager.wellbeing

/**
 * Pure aggregation helpers for usage stats — unit-testable without Android framework.
 */
object UsageAggregator {

    data class ForegroundSlice(
        /** Local calendar day `yyyy-MM-dd`. */
        val day: String,
        val packageName: String,
        /** Foreground time in milliseconds for that day. */
        val foregroundMs: Long
    )

    /**
     * Collapse raw slices into whole-minute aggregates.
     * Sub-minute totals round down; zero-minute packages are dropped.
     * Same (day, package) rows are summed.
     */
    fun aggregateToDailyMinutes(slices: List<ForegroundSlice>): List<UsageDailyEntity> {
        if (slices.isEmpty()) return emptyList()
        val summedMs = linkedMapOf<Pair<String, String>, Long>()
        for (slice in slices) {
            val pkg = slice.packageName.trim()
            if (pkg.isEmpty() || slice.foregroundMs <= 0L) continue
            val day = slice.day.trim()
            if (day.isEmpty()) continue
            val key = day to pkg
            summedMs[key] = (summedMs[key] ?: 0L) + slice.foregroundMs
        }
        return summedMs.mapNotNull { (key, ms) ->
            val minutes = (ms / 60_000L).toInt()
            if (minutes <= 0) null
            else UsageDailyEntity(day = key.first, packageName = key.second, minutes = minutes)
        }.sortedWith(compareByDescending<UsageDailyEntity> { it.minutes }.thenBy { it.packageName })
    }

    fun totalMinutes(rows: List<UsageDailyEntity>): Int = rows.sumOf { it.minutes.coerceAtLeast(0) }

    fun topApps(rows: List<UsageDailyEntity>, limit: Int = 10): List<Pair<String, Int>> {
        if (limit <= 0 || rows.isEmpty()) return emptyList()
        return rows
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.sumOf { it.minutes.coerceAtLeast(0) } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
    }

    fun categoryTotals(
        rows: List<UsageDailyEntity>,
        categoryOf: (String) -> String
    ): List<Pair<String, Int>> {
        if (rows.isEmpty()) return emptyList()
        return rows
            .groupBy { categoryOf(it.packageName) }
            .map { (cat, list) -> cat to list.sumOf { it.minutes.coerceAtLeast(0) } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }

    /** Minutes per calendar day, oldest → newest. */
    fun dailyTotals(rows: List<UsageDailyEntity>): List<Pair<String, Int>> {
        if (rows.isEmpty()) return emptyList()
        return rows
            .groupBy { it.day }
            .map { (day, list) -> day to list.sumOf { it.minutes.coerceAtLeast(0) } }
            .sortedBy { it.first }
    }
}
