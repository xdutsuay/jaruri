package com.example.moneymanager.utils.journal

/**
 * Pure helpers for voice/manual journal text — no Android framework deps so unit tests stay JVM.
 */
object JournalTextHelpers {

    private val WHITESPACE = Regex("\\s+")
    private val HOURS_MINUTES = Regex(
        """(?i)(?:(\d+)\s*(?:hours?|hrs?|h))\s*(?:(\d+)\s*(?:minutes?|mins?|m))?|(?:(\d+)\s*(?:minutes?|mins?|m))"""
    )
    private val COMPACT_DURATION = Regex("""(?i)\b(\d+)\s*h(?:\s*(\d+)\s*m)?\b|\b(\d+)\s*m\b""")

    /** Trim and collapse whitespace; empty input → empty string. */
    fun normalizeTranscript(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return WHITESPACE.replace(raw.trim(), " ")
    }

    /**
     * Suggest a short list label from journal text: first sentence/clause, capped.
     * Falls back to [fallback] when blank.
     */
    fun suggestLabel(text: String, fallback: String = "Journal", maxLen: Int = 48): String {
        val cleaned = normalizeTranscript(text)
        if (cleaned.isEmpty()) return fallback
        val firstChunk = cleaned
            .split(Regex("[.!?;\\n]"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: cleaned
        return if (firstChunk.length <= maxLen) {
            firstChunk
        } else {
            firstChunk.take(maxLen - 1).trimEnd() + "…"
        }
    }

    /**
     * Best-effort parse of spoken duration phrases ("30 minutes", "1 hour 20 minutes", "1h 20m").
     * Returns null when nothing recognizable is found.
     */
    fun parseDurationMinutes(text: String): Int? {
        val cleaned = normalizeTranscript(text)
        if (cleaned.isEmpty()) return null

        HOURS_MINUTES.find(cleaned)?.let { m ->
            val hours = m.groupValues[1].toIntOrNull()
            val minsAfterHours = m.groupValues[2].toIntOrNull()
            val minsOnly = m.groupValues[3].toIntOrNull()
            when {
                hours != null -> {
                    val total = hours * 60 + (minsAfterHours ?: 0)
                    if (total > 0) return total
                }
                minsOnly != null && minsOnly > 0 -> return minsOnly
            }
        }

        COMPACT_DURATION.find(cleaned)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val mAfterH = m.groupValues[2].toIntOrNull()
            val mOnly = m.groupValues[3].toIntOrNull()
            when {
                h != null -> {
                    val total = h * 60 + (mAfterH ?: 0)
                    if (total > 0) return total
                }
                mOnly != null && mOnly > 0 -> return mOnly
            }
        }
        return null
    }

    /** Format optional duration for list rows; blank when zero/null. */
    fun formatOptionalDuration(totalMinutes: Int?): String {
        val m = totalMinutes ?: 0
        if (m <= 0) return ""
        val h = m / 60
        val rem = m % 60
        return when {
            h > 0 && rem > 0 -> "${h}h ${rem}m"
            h > 0 -> "${h}h"
            else -> "${rem}m"
        }
    }

    fun isVoiceSource(source: String?): Boolean =
        source.equals("VOICE", ignoreCase = true)
}
