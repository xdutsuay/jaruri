package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplayWeightsTest {

    @Test
    fun tinySliceGetsMinimumVisualShare() {
        val slices = ChartDisplayWeights.forPie(
            mapOf(
                "Food" to 970.0,
                "Misc" to 30.0
            ),
            minShare = 0.04f,
            maxSlices = 7
        )
        assertEquals(2, slices.size)
        val totalDisplay = slices.sumOf { it.displayValue.toDouble() }
        val misc = slices.first { it.label == "Misc" }
        assertTrue(misc.displayValue / totalDisplay >= 0.039f)
        // Actual amounts preserved for labels
        assertEquals(30.0, misc.actualValue, 0.001)
        assertEquals(970.0, slices.first { it.label == "Food" }.actualValue, 0.001)
    }

    @Test
    fun collapsesTailIntoOther() {
        val map = (1..10).associate { "C$it" to (11 - it).toDouble() * 10.0 }
        val slices = ChartDisplayWeights.forPie(map, minShare = 0.04f, maxSlices = 4)
        assertEquals(5, slices.size) // 4 + Other
        assertTrue(slices.any { it.label == "Other" })
        assertEquals(map.values.sum(), slices.sumOf { it.actualValue }, 0.001)
    }

    @Test
    fun emptyOrZeroReturnsEmpty() {
        assertTrue(ChartDisplayWeights.forPie(emptyMap()).isEmpty())
        assertTrue(ChartDisplayWeights.forPie(mapOf("X" to 0.0)).isEmpty())
    }
}
