package com.example.moneymanager.listener.transcribe

import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Vosk (Kaldi) offline recogniser. The model is a folder the user imported
 * (see [SttModelStore]); nothing is downloaded. The native library ships for
 * arm64-v8a and armeabi-v7a only (x86 builds are excluded to keep the APK
 * small), so on emulators this reports unavailable instead of crashing.
 */
class VoskSpeechEngine private constructor(
    private val model: Model,
    override val label: String
) : SpeechEngine {

    override fun newStream(sampleRate: Int): SpeechStream {
        val rec = Recognizer(model, sampleRate.toFloat())
        rec.setWords(true)
        return Stream(rec, sampleRate)
    }

    override fun close() {
        model.close()
    }

    private class Stream(private val rec: Recognizer, private val sampleRate: Int) : SpeechStream {
        private var samplesFed = 0L

        private fun positionMs() = samplesFed * 1000 / sampleRate

        override fun accept(pcm: ShortArray, length: Int): List<RawSegment> {
            samplesFed += length
            if (!rec.acceptWaveForm(pcm, length)) return emptyList()
            return listOfNotNull(VoskResultParser.parse(rec.result, positionMs()))
        }

        override fun finish(): List<RawSegment> =
            listOfNotNull(VoskResultParser.parse(rec.finalResult, positionMs()))

        override fun close() {
            rec.close()
        }
    }

    companion object {
        @Throws(EngineUnavailableException::class)
        fun load(modelDir: File, modelName: String): VoskSpeechEngine {
            val model = try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Model(modelDir.absolutePath)
            } catch (e: LinkageError) {
                // UnsatisfiedLinkError / ExceptionInInitializerError from JNA.
                throw EngineUnavailableException(
                    "The speech engine isn't available on this device (${e.javaClass.simpleName})."
                )
            } catch (e: IOException) {
                throw EngineUnavailableException("Couldn't load the speech model: ${e.message}")
            }
            return VoskSpeechEngine(model, "Vosk · $modelName")
        }
    }
}
