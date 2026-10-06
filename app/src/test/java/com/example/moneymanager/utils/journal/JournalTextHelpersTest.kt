package com.example.moneymanager.utils.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTextHelpersTest {

    @Test
    fun normalizeTranscript_collapsesWhitespace() {
        assertEquals("hello world", JournalTextHelpers.normalizeTranscript("  hello   world \n"))
        assertEquals("", JournalTextHelpers.normalizeTranscript("   "))
        assertEquals("", JournalTextHelpers.normalizeTranscript(null))
    }

    @Test
    fun suggestLabel_usesFirstSentenceAndCaps() {
        assertEquals(
            "Worked on reports",
            JournalTextHelpers.suggestLabel("Worked on reports. Then gym.")
        )
        assertEquals("Journal", JournalTextHelpers.suggestLabel(""))
        val long = "a".repeat(60)
        val label = JournalTextHelpers.suggestLabel(long, maxLen = 48)
        assertEquals(48, label.length)
        assertTrue(label.endsWith("…"))
    }

    @Test
    fun parseDurationMinutes_commonPhrases() {
        assertEquals(30, JournalTextHelpers.parseDurationMinutes("spent 30 minutes on email"))
        assertEquals(80, JournalTextHelpers.parseDurationMinutes("about 1 hour 20 minutes"))
        assertEquals(60, JournalTextHelpers.parseDurationMinutes("1 hour of focus"))
        assertNull(JournalTextHelpers.parseDurationMinutes("one hour of focus"))
        assertEquals(90, JournalTextHelpers.parseDurationMinutes("did 1h 30m deep work"))
        assertEquals(45, JournalTextHelpers.parseDurationMinutes("45m commute"))
        assertNull(JournalTextHelpers.parseDurationMinutes("no duration here"))
    }

    @Test
    fun formatOptionalDuration_blankWhenZero() {
        assertEquals("", JournalTextHelpers.formatOptionalDuration(0))
        assertEquals("", JournalTextHelpers.formatOptionalDuration(null))
        assertEquals("1h 5m", JournalTextHelpers.formatOptionalDuration(65))
        assertEquals("2h", JournalTextHelpers.formatOptionalDuration(120))
        assertEquals("12m", JournalTextHelpers.formatOptionalDuration(12))
    }

    @Test
    fun isVoiceSource() {
        assertTrue(JournalTextHelpers.isVoiceSource("VOICE"))
        assertTrue(JournalTextHelpers.isVoiceSource("voice"))
        assertFalse(JournalTextHelpers.isVoiceSource("MANUAL"))
        assertFalse(JournalTextHelpers.isVoiceSource(null))
    }
}
