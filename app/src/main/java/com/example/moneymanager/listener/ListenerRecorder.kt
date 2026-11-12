package com.example.moneymanager.listener

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * One [MediaRecorder] per chunk file. Rolling to the next chunk is
 * stop() + start(next), which leaves a gap of well under a second between
 * chunks; acceptable for meeting notes and works the same on API 24–34.
 */
class ListenerRecorder(
    private val context: Context,
    private val onError: (String) -> Unit
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    val isActive: Boolean get() = recorder != null

    @Throws(Exception::class)
    fun start(file: File) {
        check(recorder == null) { "already recording" }
        file.parentFile?.mkdirs()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(ListenerPolicy.SAMPLE_RATE_HZ)
            r.setAudioEncodingBitRate(ListenerPolicy.BIT_RATE_BPS)
            r.setOutputFile(file.absolutePath)
            r.setOnErrorListener { _, what, extra ->
                onError("MediaRecorder error what=$what extra=$extra")
            }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            file.delete()
            throw e
        }
        recorder = r
        currentFile = file
    }

    /**
     * Finalises the current chunk. MediaRecorder throws if stopped before any
     * audio was captured; that chunk is unusable, so it is deleted.
     * @return the finished file, or null if nothing usable was written.
     */
    fun stop(): File? {
        val r = recorder ?: return null
        val f = currentFile
        recorder = null
        currentFile = null
        var ok = true
        try {
            r.stop()
        } catch (_: RuntimeException) {
            ok = false
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        if (!ok || f == null || f.length() == 0L) {
            f?.delete()
            return null
        }
        return f
    }
}
