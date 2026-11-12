package com.example.moneymanager.listener.transcribe

import com.example.moneymanager.listener.ListenerSessionDao
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcribes one finished session, chunk by chunk, resumably.
 *
 * - Resume point is `transcribedChunks`; a chunk's segments and the pointer are
 *   committed in one transaction, so a crash or cancel mid-chunk just redoes
 *   that chunk.
 * - Never runs on a session that is still RECORDING.
 * - Engine/decoder are injected so the state machine is testable without
 *   native code or MediaCodec.
 */
class TranscriptionRunner(
    private val sessions: ListenerSessionDao,
    private val transcripts: TranscriptDao,
    private val storage: ListenerStorage,
    private val engineFactory: () -> SpeechEngine,
    private val decoder: PcmDecoder,
    private val durationsOf: (List<File>) -> List<Long>
) {
    sealed class Outcome {
        object Done : Outcome()
        object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    data class Progress(val chunksDone: Int, val totalChunks: Int, val withinChunk: Float) {
        val fraction: Float
            get() = if (totalChunks == 0) 1f
            else ((chunksDone + withinChunk.coerceIn(0f, 1f)) / totalChunks).coerceIn(0f, 1f)
    }

    suspend fun run(
        sessionId: Long,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit = {}
    ): Outcome {
        val row = sessions.getById(sessionId) ?: return Outcome.Failed("Session not found.")
        if (row.state == ListenerSessionEntity.STATE_RECORDING) {
            return fail(sessionId, "Still recording; stop the session first.")
        }
        if (isCancelled() || transcripts.markRunningUnlessCancelled(sessionId) == 0) {
            return Outcome.Cancelled
        }
        val files = storage.segments(row.dirName)
        if (files.isEmpty()) return fail(sessionId, "No audio saved for this session.")

        val engine = try {
            engineFactory()
        } catch (e: EngineUnavailableException) {
            return fail(sessionId, e.message ?: "Speech engine unavailable.")
        }
        try {
            val timeline = ChunkTimeline(durationsOf(files))
            val startAt = row.transcribedChunks.coerceIn(0, files.size)
            transcripts.deleteFromChunk(sessionId, startAt)
            onProgress(Progress(startAt, files.size, 0f))

            for (i in startAt until files.size) {
                if (isCancelled()) return Outcome.Cancelled
                val raw = transcribeChunk(engine, files[i], timeline.durations[i], isCancelled) { f ->
                    onProgress(Progress(i, files.size, f))
                } ?: return Outcome.Cancelled
                val chunkStart = timeline.offsets[i]
                val chunkLen = timeline.durations[i]
                val rows = raw.mapNotNull { r ->
                    val text = r.text.trim()
                    if (text.isEmpty()) null
                    else {
                        // Clamp into the chunk so rounding never pushes a hit into the next one.
                        val s = if (chunkLen > 0) r.startMs.coerceIn(0, chunkLen) else r.startMs.coerceAtLeast(0)
                        val e = if (chunkLen > 0) r.endMs.coerceIn(s, chunkLen) else maxOf(s, r.endMs)
                        TranscriptSegmentEntity(
                            sessionId = sessionId, chunkIndex = i,
                            startMs = chunkStart + s, endMs = chunkStart + e, text = text
                        )
                    }
                }
                transcripts.commitChunk(sessionId, i, rows)
                onProgress(Progress(i + 1, files.size, 0f))
            }
            withContext(NonCancellable) {
                transcripts.setState(sessionId, ListenerSessionEntity.TRANSCRIPT_DONE, null)
            }
            return Outcome.Done
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return fail(sessionId, e.message ?: e.javaClass.simpleName)
        } finally {
            try { engine.close() } catch (_: Exception) {}
        }
    }

    /** @return segments for the chunk, or null if cancelled part-way. */
    private fun transcribeChunk(
        engine: SpeechEngine,
        file: File,
        durationMs: Long,
        isCancelled: () -> Boolean,
        onFraction: (Float) -> Unit
    ): List<RawSegment>? {
        val out = mutableListOf<RawSegment>()
        decoder.open(file).use { pcm ->
            engine.newStream(pcm.sampleRate).use { stream ->
                val buf = ShortArray(BUFFER_SAMPLES)
                var fed = 0L
                val expected = (durationMs * pcm.sampleRate / 1000).coerceAtLeast(1)
                while (true) {
                    if (isCancelled()) return null
                    val n = pcm.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    out += stream.accept(buf, n)
                    fed += n
                    onFraction((fed.toFloat() / expected).coerceAtMost(1f))
                }
                out += stream.finish()
            }
        }
        return out
    }

    private suspend fun fail(sessionId: Long, message: String): Outcome.Failed {
        withContext(NonCancellable) {
            transcripts.setState(sessionId, ListenerSessionEntity.TRANSCRIPT_FAILED, message)
        }
        return Outcome.Failed(message)
    }

    private companion object {
        const val BUFFER_SAMPLES = 4096
    }
}
