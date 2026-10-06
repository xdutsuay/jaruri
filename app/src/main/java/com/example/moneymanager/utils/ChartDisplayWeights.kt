package com.example.moneymanager.utils

/**
 * Builds pie-chart display weights that stay *proportionate* but readable.
 * Tiny categories get a minimum visual share; larger ones shrink slightly so
 * the chart still roughly reflects reality without unreadable slivers.
 *
 * Labels/breakdown should still show **true** amounts — only the pie geometry
 * uses [Slice.displayValue].
 */
object ChartDisplayWeights {

    data class Slice(
        val label: String,
        /** True amount (for labels / legend). */
        val actualValue: Double,
        /** Value fed to the pie chart (boosted for tiny slices). */
        val displayValue: Float
    )

    /**
     * @param minShare minimum fraction of the pie for any drawn slice (0–1), e.g. 0.04f = 4%
     * @param maxSlices keep top N by actual value; remainder collapsed into [otherLabel]
     */
    fun forPie(
        amountsByLabel: Map<String, Double>,
        minShare: Float = 0.04f,
        maxSlices: Int = 7,
        otherLabel: String = "Other"
    ): List<Slice> {
        val positive = amountsByLabel
            .mapValues { it.value.coerceAtLeast(0.0) }
            .filter { it.value > 0.0 }
            .entries
            .sortedByDescending { it.value }
        if (positive.isEmpty()) return emptyList()

        val head = positive.take(maxSlices.coerceAtLeast(1))
        val tail = positive.drop(head.size)
        val merged = buildList {
            head.forEach { add(it.key to it.value) }
            if (tail.isNotEmpty()) {
                add(otherLabel to tail.sumOf { it.value })
            }
        }

        val total = merged.sumOf { it.second }
        if (total <= 0.0) return emptyList()

        val n = merged.size
        val floor = minShare.coerceIn(0.01f, 0.2f)
        // If floors cannot fit, scale floor down so sum(floors) <= 1.
        val effectiveFloor = if (n * floor > 0.95f) (0.95f / n) else floor

        val rawShares = merged.map { (_, v) -> (v / total).toFloat() }
        val boosted = rawShares.map { share ->
            if (share < effectiveFloor) effectiveFloor else share
        }
        val boostSum = boosted.sum()
        val displayShares = if (boostSum <= 1.0001f) {
            // Distribute leftover to slices that were already above the floor,
            // proportional to how much they exceed the floor (keeps ranking).
            val leftover = (1f - boostSum).coerceAtLeast(0f)
            val excessWeights = boosted.mapIndexed { i, b ->
                if (rawShares[i] >= effectiveFloor) (b - effectiveFloor).coerceAtLeast(0.0001f) else 0f
            }
            val excessSum = excessWeights.sum()
            if (leftover > 0f && excessSum > 0f) {
                boosted.mapIndexed { i, b ->
                    b + leftover * (excessWeights[i] / excessSum)
                }
            } else {
                // Everyone was floored — normalize.
                val s = boosted.sum()
                boosted.map { it / s }
            }
        } else {
            // Over-allocated floors — normalize the boosted shares.
            boosted.map { it / boostSum }
        }

        // PieEntry uses absolute values; scale display shares by total so MPAndroidChart
        // percentages remain meaningful relative to each other.
        return merged.mapIndexed { i, (label, actual) ->
            Slice(
                label = label,
                actualValue = actual,
                displayValue = (displayShares[i] * total).toFloat().coerceAtLeast(0.01f)
            )
        }
    }
}
