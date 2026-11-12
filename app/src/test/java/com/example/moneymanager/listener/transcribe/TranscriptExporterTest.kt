package com.example.moneymanager.listener.transcribe

import com.example.moneymanager.listener.transcribe.TranscriptExporter.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TranscriptExporterTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val start = 1_788_000_000_000L // 2026-08-29 10:40 UTC
    private fun seg(id: Long, ms: Long, text: String) =
        TranscriptSegmentEntity(id = id, sessionId = 1, chunkIndex = 0, startMs = ms, endMs = ms + 500, text = text)

    private val segs = listOf(seg(2, 3_723_000, "late point"), seg(1, 1_000, " hello team "), seg(3, 5_000, "   "))

    @Test
    fun timestampsAreZeroPaddedHours() {
        assertEquals("00:00:00", TranscriptExporter.timestamp(0))
        assertEquals("00:00:00", TranscriptExporter.timestamp(-5))
        assertEquals("00:01:05", TranscriptExporter.timestamp(65_999))
        assertEquals("01:02:03", TranscriptExporter.timestamp(3_723_000))
        assertEquals("27:46:40", TranscriptExporter.timestamp(100_000_000))
    }

    @Test
    fun plainText_exactFormat() {
        val out = TranscriptExporter.toPlain("Standup", start, 3_725_000, segs, utc)
        assertEquals(
            "Standup\n" +
                "Date: 2026-08-29 10:40 UTC\n" +
                "Duration: 01:02:05\n" +
                TranscriptExporter.DISCLAIMER + "\n\n" +
                "[00:00:01] hello team\n" +
                "[01:02:03] late point\n",
            out
        )
    }

    @Test
    fun markdown_hasHeadingMetaAndEscapedLines() {
        val out = TranscriptExporter.toMarkdown("Q3 #plan *draft*", start, 61_000,
            listOf(seg(1, 2_000, "use [x] and `y`")), utc)
        assertEquals(
            "# Q3 \\#plan \\*draft\\*\n\n" +
                "- Date: 2026-08-29 10:40 UTC\n" +
                "- Duration: 00:01:01\n" +
                "- " + TranscriptExporter.DISCLAIMER + "\n\n" +
                "**[00:00:02]** use \\[x\\] and \\`y\\`\n",
            out
        )
    }

    @Test
    fun blankTitleFallsBack() {
        assertTrue(TranscriptExporter.toPlain(" ", start, 0, emptyList(), utc).startsWith("Recording\n"))
    }

    @Test
    fun fileNames_areSafe() {
        assertEquals("Standup-20260829-1040.md", TranscriptExporter.fileName("Standup", start, Format.MARKDOWN, utc))
        assertEquals("a_b_c-20260829-1040.txt", TranscriptExporter.fileName("a/b\\c", start, Format.PLAIN, utc))
        val dotty = TranscriptExporter.fileName("../..", start, Format.PLAIN, utc)
        assertTrue(dotty, !dotty.contains('/') && !dotty.startsWith("."))
        assertEquals("transcript-20260829-1040.md", TranscriptExporter.fileName("", start, Format.MARKDOWN, utc))
        assertEquals("मीटिंग-20260829-1040.md", TranscriptExporter.fileName("मीटिंग", start, Format.MARKDOWN, utc))
        assertEquals(60 + "-20260829-1040.md".length, TranscriptExporter.fileName("x".repeat(200), start, Format.MARKDOWN, utc).length)
    }
}
