package com.example.moneymanager.listener

import java.util.Locale

/** Why a session ended. Stored in [ListenerSessionEntity.notes]. */
enum class ListenerStopReason {
    USER,
    MAX_DURATION,
    LOW_STORAGE,
    RECORDER_ERROR,
    PROCESS_DIED
}

/**
 * Pure rules for the Listener: chunk naming, auto-stop limits and free-space
 * thresholds. No Android types so it is plain-JUnit testable.
 */
object ListenerPolicy {
    /** Each chunk file covers this much audio; a crash loses at most one chunk. */
    const val DEFAULT_SEGMENT_MS: Long = 15 * 60 * 1000L

    /** Safety auto-stop. Never silent: the service posts a notice when it fires. */
    const val DEFAULT_MAX_SESSION_MS: Long = 4 * 60 * 60 * 1000L

    /** Refuse to start below this much free space (≈ 20 h of audio headroom). */
    const val MIN_FREE_BYTES_TO_START: Long = 300L * 1024 * 1024

    /** Stop an active session when free space drops below this. */
    const val MIN_FREE_BYTES_TO_CONTINUE: Long = 200L * 1024 * 1024

    /** Debug builds only: lower bound for the chunk-length override. */
    const val MIN_DEBUG_SEGMENT_MS: Long = 5_000L

    // AAC-LC mono 16 kHz ≈ 32 kbps ≈ 14 MB/hour.
    const val SAMPLE_RATE_HZ = 16_000
    const val BIT_RATE_BPS = 32_000

    fun segmentFileName(index: Int): String {
        require(index >= 1) { "segment index starts at 1" }
        return String.format(Locale.US, "seg-%03d.m4a", index)
    }

    fun isSegmentFile(name: String): Boolean = SEGMENT_REGEX.matches(name)

    fun segmentIndex(name: String): Int? =
        SEGMENT_REGEX.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()

    fun sessionDirName(startedAt: Long): String = "session-$startedAt"

    fun canStart(freeBytes: Long): Boolean = freeBytes >= MIN_FREE_BYTES_TO_START

    fun shouldStopForStorage(freeBytes: Long): Boolean = freeBytes < MIN_FREE_BYTES_TO_CONTINUE

    fun shouldStopForDuration(elapsedMs: Long, maxMs: Long = DEFAULT_MAX_SESSION_MS): Boolean =
        elapsedMs >= maxMs

    /**
     * Debug-only override for chunk length (evidence runs). Returns the default
     * unless [debuggable] and the raw value parses to something sane.
     */
    fun effectiveSegmentMs(debuggable: Boolean, overrideRaw: String?): Long {
        if (!debuggable) return DEFAULT_SEGMENT_MS
        val v = overrideRaw?.trim()?.toLongOrNull() ?: return DEFAULT_SEGMENT_MS
        return if (v >= MIN_DEBUG_SEGMENT_MS) v else DEFAULT_SEGMENT_MS
    }

    fun defaultTitle(startedAt: Long, locale: Locale = Locale.getDefault()): String =
        "Meeting " + java.text.SimpleDateFormat("d MMM, HH:mm", locale).format(java.util.Date(startedAt))

    fun formatElapsed(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0)) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 1024.0) return String.format(Locale.US, "%.1f GB", mb / 1024.0)
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(0))
    }

    private val SEGMENT_REGEX = Regex("""seg-(\d{3,})\.m4a""")
}
