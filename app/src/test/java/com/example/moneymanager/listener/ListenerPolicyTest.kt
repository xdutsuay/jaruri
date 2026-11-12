package com.example.moneymanager.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerPolicyTest {

    @Test
    fun segmentNamesAreZeroPaddedAndSortable() {
        assertEquals("seg-001.m4a", ListenerPolicy.segmentFileName(1))
        assertEquals("seg-012.m4a", ListenerPolicy.segmentFileName(12))
        assertEquals("seg-1000.m4a", ListenerPolicy.segmentFileName(1000))
        val names = (1..20).map { ListenerPolicy.segmentFileName(it) }
        assertEquals(names, names.sorted())
    }

    @Test(expected = IllegalArgumentException::class)
    fun segmentIndexStartsAtOne() {
        ListenerPolicy.segmentFileName(0)
    }

    @Test
    fun segmentFileRecognition() {
        assertTrue(ListenerPolicy.isSegmentFile("seg-003.m4a"))
        assertEquals(3, ListenerPolicy.segmentIndex("seg-003.m4a"))
        assertFalse(ListenerPolicy.isSegmentFile("seg-3.m4a"))
        assertFalse(ListenerPolicy.isSegmentFile("notes.txt"))
        assertNull(ListenerPolicy.segmentIndex("seg-abc.m4a"))
    }

    @Test
    fun defaultsMatchPlan() {
        assertEquals(15 * 60 * 1000L, ListenerPolicy.DEFAULT_SEGMENT_MS)
        assertEquals(4 * 60 * 60 * 1000L, ListenerPolicy.DEFAULT_MAX_SESSION_MS)
    }

    @Test
    fun maxDurationStop() {
        val max = ListenerPolicy.DEFAULT_MAX_SESSION_MS
        assertFalse(ListenerPolicy.shouldStopForDuration(max - 1))
        assertTrue(ListenerPolicy.shouldStopForDuration(max))
        assertTrue(ListenerPolicy.shouldStopForDuration(max + 1))
    }

    @Test
    fun freeSpaceThresholdsHaveHeadroom() {
        val mb = 1024L * 1024
        assertFalse(ListenerPolicy.canStart(299 * mb))
        assertTrue(ListenerPolicy.canStart(300 * mb))
        assertFalse(ListenerPolicy.shouldStopForStorage(200 * mb))
        assertTrue(ListenerPolicy.shouldStopForStorage(199 * mb))
        // Anything we allow to start must not immediately trip the stop rule.
        assertFalse(ListenerPolicy.shouldStopForStorage(ListenerPolicy.MIN_FREE_BYTES_TO_START))
    }

    @Test
    fun segmentOverrideOnlyInDebuggableBuilds() {
        val d = ListenerPolicy.DEFAULT_SEGMENT_MS
        assertEquals(d, ListenerPolicy.effectiveSegmentMs(false, "30000"))
        assertEquals(30_000L, ListenerPolicy.effectiveSegmentMs(true, "30000"))
        assertEquals(30_000L, ListenerPolicy.effectiveSegmentMs(true, " 30000\n"))
        assertEquals(d, ListenerPolicy.effectiveSegmentMs(true, null))
        assertEquals(d, ListenerPolicy.effectiveSegmentMs(true, "junk"))
        assertEquals(d, ListenerPolicy.effectiveSegmentMs(true, "100")) // below floor
    }

    @Test
    fun formatting() {
        assertEquals("00:05", ListenerPolicy.formatElapsed(5_000))
        assertEquals("59:59", ListenerPolicy.formatElapsed(3_599_000))
        assertEquals("1:00:00", ListenerPolicy.formatElapsed(3_600_000))
        assertEquals("00:00", ListenerPolicy.formatElapsed(-10))
        assertEquals("14.0 MB", ListenerPolicy.formatBytes(14L * 1024 * 1024))
        assertEquals("512 KB", ListenerPolicy.formatBytes(512L * 1024))
    }
}
