package com.rexyy.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rexyy.app.data.local.SecureStorage
import java.util.Locale
import java.util.UUID

class VoiceTtsManager(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private var pendingLanguage: String = SecureStorage.VOICE_LANG_DEFAULT
    private var currentUtteranceId: String? = null

    init {
        try {
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupUtteranceListener()
            applyLanguage(pendingLanguage)
        } else {
            isInitialized = false
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    onSpeakingStateChanged(true)
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (currentUtteranceId == utteranceId) {
                        currentUtteranceId = null
                        onSpeakingStateChanged(false)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    if (currentUtteranceId == utteranceId) {
                        currentUtteranceId = null
                        onSpeakingStateChanged(false)
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    if (currentUtteranceId == utteranceId) {
                        currentUtteranceId = null
                        onSpeakingStateChanged(false)
                    }
                }
            }
        })
    }

    fun applyLanguage(languageSetting: String) {
        pendingLanguage = languageSetting
        if (!isInitialized || textToSpeech == null) return

        val targetLocale = when (languageSetting) {
            SecureStorage.VOICE_LANG_HI -> Locale("hi", "IN")
            SecureStorage.VOICE_LANG_EN -> Locale.US
            else -> Locale.getDefault()
        }

        try {
            val availability = textToSpeech?.isLanguageAvailable(targetLocale)
            if (availability != TextToSpeech.LANG_MISSING_DATA && availability != TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.language = targetLocale
            } else {
                // Fallback to English US or default
                textToSpeech?.language = Locale.US
            }
        } catch (_: Exception) {
        }
    }

    fun speak(text: String, languageSetting: String = SecureStorage.VOICE_LANG_DEFAULT) {
        if (!isInitialized || textToSpeech == null) return
        if (text.isBlank()) return

        applyLanguage(languageSetting)

        // Clean markdown and formatting for natural speech
        val cleanedText = prepareSpeechText(text)
        if (cleanedText.isBlank()) return

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        try {
            textToSpeech?.speak(
                cleanedText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        } catch (_: Exception) {
            onSpeakingStateChanged(false)
        }
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        } finally {
            currentUtteranceId = null
            mainHandler.post {
                onSpeakingStateChanged(false)
            }
        }
    }

    fun isSpeaking(): Boolean {
        return try {
            textToSpeech?.isSpeaking == true
        } catch (_: Exception) {
            false
        }
    }

    fun shutdown() {
        try {
            stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        } catch (_: Exception) {
        }
    }

    /**
     * Cleans code blocks, markdown symbols, and caps overly long responses to avoid
     * uncomfortably long voice readouts.
     */
    private fun prepareSpeechText(rawText: String): String {
        var text = rawText
            .replace("```[\\s\\S]*?```".toRegex(), "Code snippet omitted.")
            .replace("`[^`]*`".toRegex(), "")
            .replace("\\*\\*(.*?)\\*\\*".toRegex(), "$1")
            .replace("\\*(.*?)\\*".toRegex(), "$1")
            .replace("###?".toRegex(), "")
            .replace("\\[(.*?)\\]\\(.*?\\)".toRegex(), "$1")
            .trim()

        // If response is longer than 350 chars, take the first 1-2 sentences or paragraph
        if (text.length > 350) {
            val firstParagraph = text.split("\n\n").firstOrNull { it.isNotBlank() } ?: text
            if (firstParagraph.length in 50..350) {
                return firstParagraph
            }
            // Cut at sentence boundary
            val sentenceEnd = text.indexOfAny(charArrayOf('.', '!', '?'), 150)
            if (sentenceEnd in 150..350) {
                return text.substring(0, sentenceEnd + 1)
            }
            return text.take(350).trimEnd() + "..."
        }

        return text
    }
}
