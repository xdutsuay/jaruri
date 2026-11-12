package com.example.moneymanager.listener.transcribe

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Plain-text / Markdown transcript export. Pure, so the format is unit-tested. */
object TranscriptExporter {

    enum class Format(val extension: String, val mimeType: String) {
        MARKDOWN("md", "text/markdown"),
        PLAIN("txt", "text/plain")
    }

    const val DISCLAIMER = "Transcribed offline on this phone. Automatic transcripts may contain errors."

    fun export(
        format: Format,
        title: String,
        startedAt: Long,
        durationMs: Long,
        segments: List<TranscriptSegmentEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = when (format) {
        Format.MARKDOWN -> toMarkdown(title, startedAt, durationMs, segments, timeZone)
        Format.PLAIN -> toPlain(title, startedAt, durationMs, segments, timeZone)
    }

    fun toMarkdown(
        title: String, startedAt: Long, durationMs: Long,
        segments: List<TranscriptSegmentEntity>, timeZone: TimeZone = TimeZone.getDefault()
    ): String = buildString {
        append("# ").append(escapeMarkdown(title.ifBlank { "Recording" })).append("\n\n")
        append("- Date: ").append(formatDate(startedAt, timeZone)).append('\n')
        append("- Duration: ").append(timestamp(durationMs)).append('\n')
        append("- ").append(DISCLAIMER).append("\n\n")
        for (s in ordered(segments)) {
            append("**[").append(timestamp(s.startMs)).append("]** ")
                .append(escapeMarkdown(s.text.trim())).append("\n\n")
        }
    }.trimEnd() + "\n"

    fun toPlain(
        title: String, startedAt: Long, durationMs: Long,
        segments: List<TranscriptSegmentEntity>, timeZone: TimeZone = TimeZone.getDefault()
    ): String = buildString {
        append(title.ifBlank { "Recording" }).append('\n')
        append("Date: ").append(formatDate(startedAt, timeZone)).append('\n')
        append("Duration: ").append(timestamp(durationMs)).append('\n')
        append(DISCLAIMER).append("\n\n")
        for (s in ordered(segments)) {
            append('[').append(timestamp(s.startMs)).append("] ").append(s.text.trim()).append('\n')
        }
    }

    /** Always HH:MM:SS so columns line up in long meetings. */
    fun timestamp(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    fun fileName(title: String, startedAt: Long, format: Format, timeZone: TimeZone = TimeZone.getDefault()): String {
        val base = title.replace(Regex("[^\\p{L}\\p{M}\\p{N}._ -]"), "_")
            .replace(Regex("\\s+"), " ").trim().trim('.').take(60).trim()
            .ifEmpty { "transcript" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { this.timeZone = timeZone }
            .format(Date(startedAt))
        return "$base-$stamp.${format.extension}"
    }

    private fun ordered(segments: List<TranscriptSegmentEntity>) =
        segments.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.startMs }, { it.id }))

    private fun formatDate(ms: Long, tz: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).apply { timeZone = tz }.format(Date(ms))

    private val MD_SPECIAL = Regex("""([\\`*_\[\]#<>|])""")
    private fun escapeMarkdown(s: String) = s.replace(MD_SPECIAL, "\\\\$1")
}
