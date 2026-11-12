package com.example.moneymanager.listener.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkTimelineTest {
    private val tl = ChunkTimeline(listOf(900_000, 900_000, 120_000))

    @Test
    fun offsetsAndTotal() {
        assertEquals(listOf(0L, 900_000L, 1_800_000L), tl.offsets)
        assertEquals(1_920_000L, tl.totalMs)
    }

    @Test
    fun locateMapsAcrossChunkBoundaries() {
        assertEquals(ChunkTimeline.Position(0, 0), tl.locate(0))
        assertEquals(ChunkTimeline.Position(0, 899_999), tl.locate(899_999))
        assertEquals(ChunkTimeline.Position(1, 0), tl.locate(900_000))
        assertEquals(ChunkTimeline.Position(2, 5_000), tl.locate(1_805_000))
    }

    @Test
    fun locateClamps() {
        assertEquals(ChunkTimeline.Position(0, 0), tl.locate(-5))
        assertEquals(ChunkTimeline.Position(2, 119_999), tl.locate(9_999_999))
        assertNull(ChunkTimeline(emptyList()).locate(0))
    }

    @Test
    fun roundTrip() {
        for (ms in listOf(0L, 1L, 899_999L, 900_000L, 1_234_567L, 1_919_999L)) {
            val p = tl.locate(ms)!!
            assertEquals(ms, tl.toSessionMs(p.chunk, p.offsetMs))
        }
    }
}
