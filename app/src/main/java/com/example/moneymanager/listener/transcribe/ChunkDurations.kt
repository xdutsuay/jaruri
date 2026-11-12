package com.example.moneymanager.listener.transcribe

import android.media.MediaMetadataRetriever
import com.example.moneymanager.listener.ListenerPolicy
import java.io.File

/**
 * Chunk durations from container metadata. The transcriber and the player use
 * this same function so timestamps and seeking agree.
 */
object ChunkDurations {
    fun read(files: List<File>): List<Long> = files.map { durationOf(it) }

    private fun durationOf(file: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: estimate(file)
        } catch (_: Exception) {
            estimate(file)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** Fallback from size at the recording bit rate. */
    private fun estimate(file: File): Long = file.length() * 8 * 1000 / ListenerPolicy.BIT_RATE_BPS
}
