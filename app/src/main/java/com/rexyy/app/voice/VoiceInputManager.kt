package com.rexyy.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.rexyy.app.data.local.SecureStorage
import java.util.Locale

class VoiceInputManager(
    private val context: Context,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechRecognized: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageSetting: String) {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.setRecognitionListener(createListener())

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

                    when (languageSetting) {
                        SecureStorage.VOICE_LANG_HI -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                        }
                        SecureStorage.VOICE_LANG_EN -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN"))
                        }
                        else -> {
                            val defaultLocale = Locale.getDefault().toLanguageTag()
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLocale)
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "hi-IN"))
                        }
                    }
                }

                isListening = true
                onListeningStateChanged(true)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                onListeningStateChanged(false)
                onError("Unable to start speech recognition: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                }
            } catch (_: Exception) {
            } finally {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            } finally {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                isListening = false
                onListeningStateChanged(false)
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                onListeningStateChanged(true)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level updates if needed for animations
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChanged(false)
                val errorMessage = mapError(error)
                onError(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                onListeningStateChanged(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                if (!recognizedText.isNullOrBlank()) {
                    onSpeechRecognized(recognizedText)
                } else {
                    onError("No speech detected. Please try again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun mapError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition. Check your connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network connection timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Please try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "Speech server encountered an error. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please tap the microphone and speak."
            else -> "Speech recognition encountered an issue (Code $error)."
        }
    }
}
