package com.example.moneymanager.listener.transcribe

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Decodes a recorded `.m4a` chunk to mono 16-bit PCM with platform codecs (no network). */
class MediaCodecPcmDecoder : PcmDecoder {
    override fun open(file: File): PcmStream = Stream(file)

    private class Stream(file: File) : PcmStream {
        private val extractor = MediaExtractor()
        private val codec: MediaCodec
        private val info = MediaCodec.BufferInfo()
        private var channels: Int
        override val sampleRate: Int
        private var inputDone = false
        private var outputDone = false
        private var pending = ShortArray(0)
        private var pendingPos = 0

        init {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run {
                extractor.release()
                throw IOException("No audio track in ${file.name}")
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
        }

        override fun read(buffer: ShortArray): Int {
            if (pendingPos >= pending.size && !pump()) return -1
            val n = minOf(buffer.size, pending.size - pendingPos)
            System.arraycopy(pending, pendingPos, buffer, 0, n)
            pendingPos += n
            return n
        }

        /** Refills [pending]; false at end of stream. */
        private fun pump(): Boolean {
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        val n = extractor.readSampleData(ib, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                } else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val raw = ShortArray(sb.remaining())
                    sb.get(raw)
                    codec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    pending = PcmMath.downmix(raw, channels)
                    pendingPos = 0
                    if (pending.isNotEmpty()) return true
                }
            }
            return false
        }

        override fun close() {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

object PcmMath {
    /** Interleaved PCM → mono by averaging channels. */
    fun downmix(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        return ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) sum += interleaved[f * channels + c]
            (sum / channels).toShort()
        }
    }
}
