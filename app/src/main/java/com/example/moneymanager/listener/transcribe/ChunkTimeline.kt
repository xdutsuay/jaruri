package com.example.moneymanager.listener.transcribe

/**
 * Maps between session time and (chunk, offset-in-chunk). Chunks play back to
 * back, so chunk i starts at the sum of the durations before it. The same
 * durations are used by the transcriber (to stamp segments) and the player
 * (to seek), so a search hit lands where its words are.
 */
class ChunkTimeline(durationsMs: List<Long>) {
    val durations: List<Long> = durationsMs.map { it.coerceAtLeast(0) }
    val offsets: List<Long> = run {
        var acc = 0L
        durations.map { d -> acc.also { acc += d } }
    }
    val totalMs: Long = durations.sum()

    data class Position(val chunk: Int, val offsetMs: Long)

    /** Session ms → chunk + offset. Clamps into range; null if there are no chunks. */
    fun locate(sessionMs: Long): Position? {
        if (durations.isEmpty()) return null
        val t = sessionMs.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        for (i in durations.indices) {
            val end = offsets[i] + durations[i]
            if (t < end) return Position(i, t - offsets[i])
        }
        val last = durations.lastIndex
        return Position(last, (durations[last] - 1).coerceAtLeast(0))
    }

    fun toSessionMs(chunk: Int, offsetMs: Long): Long {
        require(chunk in durations.indices) { "chunk $chunk out of range" }
        return offsets[chunk] + offsetMs.coerceAtLeast(0)
    }
}
