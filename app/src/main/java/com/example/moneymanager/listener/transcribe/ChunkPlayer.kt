package com.example.moneymanager.listener.transcribe

import android.media.MediaPlayer
import java.io.File

/**
 * Plays a session's chunk files as one timeline. Seeking uses the same
 * [ChunkTimeline] (and the same durations) the transcriber stamped segments
 * with, so tapping a segment lands on its audio.
 */
class ChunkPlayer(
    private val files: List<File>,
    durations: List<Long>,
    private val listener: Listener
) {
    interface Listener {
        fun onPlayingChanged(playing: Boolean)
        fun onError()
    }

    val timeline = ChunkTimeline(durations)
    private var player: MediaPlayer? = null
    private var chunk = -1

    val isPlaying: Boolean get() = try { player?.isPlaying == true } catch (_: IllegalStateException) { false }
    val durationMs: Long get() = timeline.totalMs

    /** Current position in session time. */
    val positionMs: Long
        get() {
            val p = player ?: return 0
            if (chunk < 0) return 0
            return try { timeline.toSessionMs(chunk, p.currentPosition.toLong()) } catch (_: IllegalStateException) { 0 }
        }

    fun seekTo(sessionMs: Long, play: Boolean) {
        if (files.isEmpty()) return
        val pos = timeline.locate(sessionMs) ?: return
        if (!open(pos.chunk)) return
        try {
            player?.seekTo(pos.offsetMs.toInt())
            if (play) player?.start()
        } catch (_: IllegalStateException) {
            fail(); return
        }
        listener.onPlayingChanged(isPlaying)
    }

    fun toggle() {
        val p = player
        if (p == null) { seekTo(0, play = true); return }
        try {
            if (p.isPlaying) p.pause() else p.start()
        } catch (_: IllegalStateException) {
            fail(); return
        }
        listener.onPlayingChanged(isPlaying)
    }

    fun pause() {
        try { player?.takeIf { it.isPlaying }?.pause() } catch (_: IllegalStateException) {}
        listener.onPlayingChanged(false)
    }

    fun release() {
        player?.release()
        player = null
        chunk = -1
    }

    private fun open(index: Int): Boolean {
        if (index == chunk && player != null) return true
        release()
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(files[index].absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { onChunkDone() }
            player = mp
            chunk = index
            true
        } catch (e: Exception) {
            mp.release()
            fail()
            false
        }
    }

    private fun onChunkDone() {
        val next = chunk + 1
        if (next < files.size && open(next)) {
            try { player?.start() } catch (_: IllegalStateException) { fail() }
            listener.onPlayingChanged(isPlaying)
        } else {
            listener.onPlayingChanged(false)
        }
    }

    private fun fail() {
        release()
        listener.onError()
        listener.onPlayingChanged(false)
    }
}
