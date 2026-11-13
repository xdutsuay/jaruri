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

    /** v3: the user's own notes and the marks dropped while recording (session time, ms). */
    data class Extras(val userNotes: String = "", val markers: List<Long> = emptyList()) {
        val isEmpty: Boolean get() = userNotes.isBlank() && markers.isEmpty()
    }

    fun export(
        format: Format,
        title: String,
        startedAt: Long,
        durationMs: Long,
        segments: List<TranscriptSegmentEntity>,
        timeZone: TimeZone = TimeZone.getDefault(),
        extras: Extras = Extras()
    ): String = when (format) {
        Format.MARKDOWN -> toMarkdown(title, startedAt, durationMs, segments, timeZone, extras)
        Format.PLAIN -> toPlain(title, startedAt, durationMs, segments, timeZone, extras)
    }

    fun toMarkdown(
        title: String, startedAt: Long, durationMs: Long,
        segments: List<TranscriptSegmentEntity>, timeZone: TimeZone = TimeZone.getDefault(),
        extras: Extras = Extras()
    ): String = buildString {
        append("# ").append(escapeMarkdown(title.ifBlank { "Recording" })).append("\n\n")
        append("- Date: ").append(formatDate(startedAt, timeZone)).append('\n')
        append("- Duration: ").append(timestamp(durationMs)).append('\n')
        append("- ").append(DISCLAIMER).append("\n\n")
        if (extras.userNotes.isNotBlank()) {
            append("## Notes\n\n").append(escapeMarkdown(extras.userNotes.trim())).append("\n\n")
        }
        if (extras.markers.isNotEmpty()) {
            append("## Marks\n\n")
            extras.markers.sorted().forEach { append("- ").append(timestamp(it)).append('\n') }
            append('\n')
        }
        if (!extras.isEmpty) append("## Transcript\n\n")
        for (s in ordered(segments)) {
            append("**[").append(timestamp(s.startMs)).append("]** ")
                .append(escapeMarkdown(s.text.trim())).append("\n\n")
        }
    }.trimEnd() + "\n"

    fun toPlain(
        title: String, startedAt: Long, durationMs: Long,
        segments: List<TranscriptSegmentEntity>, timeZone: TimeZone = TimeZone.getDefault(),
        extras: Extras = Extras()
    ): String = buildString {
        append(title.ifBlank { "Recording" }).append('\n')
        append("Date: ").append(formatDate(startedAt, timeZone)).append('\n')
        append("Duration: ").append(timestamp(durationMs)).append('\n')
        append(DISCLAIMER).append("\n\n")
        if (extras.userNotes.isNotBlank()) append("Notes:\n").append(extras.userNotes.trim()).append("\n\n")
        if (extras.markers.isNotEmpty()) {
            append("Marks: ").append(extras.markers.sorted().joinToString(", ") { timestamp(it) }).append("\n\n")
        }
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
