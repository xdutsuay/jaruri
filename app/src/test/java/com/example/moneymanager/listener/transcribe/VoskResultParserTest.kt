package com.example.moneymanager.listener.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) // org.json is an Android stub on the plain JVM
@Config(sdk = [33])
class VoskResultParserTest {
    @Test
    fun wordTimesGiveSegmentBounds() {
        val json = """{"result":[{"conf":1.0,"end":1.02,"start":0.5,"word":"hello"},
            {"conf":0.9,"end":1.5,"start":1.1,"word":"world"}],"text":"hello world"}"""
        assertEquals(RawSegment(500, 1500, "hello world"), VoskResultParser.parse(json, 99))
    }

    @Test
    fun emptyOrBlankOrBrokenIsNull() {
        assertNull(VoskResultParser.parse("""{"text":""}""", 0))
        assertNull(VoskResultParser.parse("""{"text":"   "}""", 0))
        assertNull(VoskResultParser.parse("not json", 0))
        assertNull(VoskResultParser.parse(null, 0))
    }

    @Test
    fun textWithoutWordTimesFallsBackToStreamPosition() {
        assertEquals(RawSegment(7000, 7000, "hi there"), VoskResultParser.parse("""{"text":" hi   there "}""", 7000))
    }
}
