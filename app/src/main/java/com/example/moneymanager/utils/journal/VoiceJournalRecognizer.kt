package com.example.moneymanager.utils.journal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Thin wrapper around system [SpeechRecognizer]. No audio is persisted —
 * only callback text. Offline recognition depends on the device language pack.
 */
class VoiceJournalRecognizer(
    context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(message: String)
        fun onRmsChanged(rmsdB: Float) {}
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(locale: Locale = Locale.getDefault()) {
        if (!isAvailable) {
            callbacks.onError(ERR_UNAVAILABLE)
            return
        }
        stopInternal(destroy = false)
        val sr = SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                callbacks.onListeningStarted()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                callbacks.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false
                callbacks.onError(mapError(error))
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = firstResult(results)
                if (text.isNotBlank()) {
                    callbacks.onFinalResult(JournalTextHelpers.normalizeTranscript(text))
                } else {
                    callbacks.onError(ERR_NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = firstResult(partialResults)
                if (text.isNotBlank()) {
                    callbacks.onPartialResult(JournalTextHelpers.normalizeTranscript(text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefer on-device when the OEM supports it; still may need a language pack.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            callbacks.onError(e.message?.takeIf { it.isNotBlank() } ?: ERR_UNAVAILABLE)
        }
    }

    fun stop() {
        stopInternal(destroy = false)
    }

    fun destroy() {
        stopInternal(destroy = true)
    }

    val isListening: Boolean get() = listening

    private fun stopInternal(destroy: Boolean) {
        listening = false
        val sr = recognizer ?: return
        try {
            sr.stopListening()
        } catch (_: Exception) {
        }
        try {
            sr.cancel()
        } catch (_: Exception) {
        }
        if (destroy) {
            try {
                sr.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
        }
    }

    private fun firstResult(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull().orEmpty()
    }

    private fun mapError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> ERR_AUDIO
        SpeechRecognizer.ERROR_CLIENT -> ERR_CLIENT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ERR_PERMISSION
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ERR_NETWORK
        SpeechRecognizer.ERROR_NO_MATCH -> ERR_NO_MATCH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ERR_BUSY
        SpeechRecognizer.ERROR_SERVER -> ERR_SERVER
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ERR_TIMEOUT
        else -> ERR_UNAVAILABLE
    }

    companion object {
        const val ERR_UNAVAILABLE =
            "Speech recognition is unavailable. Install an offline language pack in system settings, or try again later."
        const val ERR_NETWORK =
            "Recognition needs a network or an offline language pack. Check Settings → Languages → Speech."
        const val ERR_SERVER = ERR_NETWORK
        const val ERR_PERMISSION = "Microphone permission is required for voice journal."
        const val ERR_NO_MATCH = "Didn't catch that. Hold the mic and try again."
        const val ERR_TIMEOUT = "No speech detected. Hold the mic and speak."
        const val ERR_BUSY = "Recognizer is busy. Wait a moment and try again."
        const val ERR_AUDIO = "Couldn't access the microphone."
        const val ERR_CLIENT = "Speech recognition stopped unexpectedly."
    }
}
