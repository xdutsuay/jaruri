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

    /** Playback speed (1.0 = normal). Applied only while playing: setting it on a paused player can start it. */
    var speed: Float = 1f
        set(value) {
            field = value.coerceIn(0.5f, 3f)
            if (isPlaying) player?.let { applySpeed(it) }
        }

    private fun applySpeed(p: MediaPlayer) {
        try {
            p.playbackParams = p.playbackParams.setSpeed(speed)
        } catch (_: Exception) {
            // IllegalStateException/IllegalArgumentException: some decoders refuse; keep normal speed.
        }
    }

    private fun play(p: MediaPlayer) {
        p.start()
        if (speed != 1f) applySpeed(p)
    }

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
            if (play) player?.let { play(it) }
        } catch (_: IllegalStateException) {
            fail(); return
        }
        listener.onPlayingChanged(isPlaying)
    }

    fun toggle() {
        val p = player
        if (p == null) { seekTo(0, play = true); return }
        try {
            if (p.isPlaying) p.pause() else play(p)
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
            try { player?.let { play(it) } } catch (_: IllegalStateException) { fail() }
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
