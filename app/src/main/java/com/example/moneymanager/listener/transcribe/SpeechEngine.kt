package com.example.moneymanager.listener.transcribe

import java.io.Closeable
import java.io.File

/** An utterance with times relative to the start of the audio fed to its stream. */
data class RawSegment(val startMs: Long, val endMs: Long, val text: String)

/** Offline speech-to-text engine. Implementations must not touch the network. */
interface SpeechEngine : Closeable {
    /** Short label for exports/UI, e.g. "Vosk · vosk-model-small-en-in-0.4". */
    val label: String
    fun newStream(sampleRate: Int): SpeechStream
}

interface SpeechStream : Closeable {
    /** Feed mono 16-bit PCM; returns utterances completed by this buffer. */
    fun accept(pcm: ShortArray, length: Int): List<RawSegment>
    /** Flush the tail; returns any last utterance. */
    fun finish(): List<RawSegment>
}

/** Engine can't run (no model imported, native library missing on this ABI, …). */
class EngineUnavailableException(message: String) : Exception(message)

/** Pull-based mono 16-bit PCM decoder for one audio chunk. */
interface PcmDecoder {
    fun open(file: File): PcmStream
}

interface PcmStream : Closeable {
    val sampleRate: Int
    /** Fills [buffer] with mono samples; returns count, or -1 at end of stream. */
    fun read(buffer: ShortArray): Int
}
