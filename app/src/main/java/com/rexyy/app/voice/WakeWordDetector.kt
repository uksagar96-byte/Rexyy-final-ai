package com.rexyy.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.service.BackgroundListeningDiagnostics
import com.rexyy.app.service.WakeWordState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/**
 * Listener callbacks for WakeWordDetector events.
 */
interface WakeWordListener {
    /**
     * Called when the wake word is detected.
     * @param phrase The recognized wake phrase (e.g., "hello rex")
     * @param inlineCommand Any command spoken in the same breath (e.g., "YouTube kholo")
     */
    fun onWakeWordDetected(phrase: String, inlineCommand: String?)

    /**
     * Called when a full voice command is recognized during COMMAND_LISTENING.
     */
    fun onCommandRecognized(command: String)

    /**
     * Called when command listening times out without speech.
     */
    fun onCommandTimeout()

    /**
     * Called when an unrecoverable recognition error occurs.
     */
    fun onError(errorCode: Int, message: String)

    /**
     * Called when an immediate stop phrase is uttered (e.g., "stop", "ruko").
     */
    fun onStopInterrupt()
}

/**
 * Abstraction for wake word detection and speech recognition lifecycle management.
 */
interface WakeWordDetector {
    val state: StateFlow<WakeWordState>

    fun startStandby()
    fun startCommandListening()
    fun pauseForSpeaking()
    fun resumeAfterSpeaking()
    fun transitionState(newState: WakeWordState)
    fun setListener(listener: WakeWordListener?)
    fun stop()
    fun destroy()

    companion object {
        fun create(context: Context): WakeWordDetector {
            return AndroidWakeWordDetector(context.applicationContext)
        }
    }
}

/**
 * Android implementation of WakeWordDetector using a single controlled SpeechRecognizer
 * instance, bounded error recovery, silent standby, and explicit state machine.
 */
class AndroidWakeWordDetector(
    private val context: Context
) : WakeWordDetector {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WakeWordState.WAKE_STANDBY)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private var listener: WakeWordListener? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningActive = false
    private var isStandbyActive = false
    private var isDestroyed = false
    private var isPausedForSpeaking = false

    private var consecutiveErrors = 0
    private var standbyLoopJob: Job? = null

    private enum class Mode {
        STANDBY,
        COMMAND
    }

    private var currentMode = Mode.STANDBY

    // Regex for detecting wake words
    // Matches "hello rex", "hey rex", "hi rex", "ok rex", "suno rex", "rex", "rexyy", etc.
    private val wakeWordPattern = Regex(
        "^(hello|hey|hi|ok|suno)?\\s*(rex|rexyy)\\b",
        RegexOption.IGNORE_CASE
    )

    private val interruptPhrases = setOf(
        "stop", "ruko", "ruk ja", "bas", "cancel", "chup", "chup raho", "shant"
    )

    override fun setListener(listener: WakeWordListener?) {
        this.listener = listener
    }

    override fun transitionState(newState: WakeWordState) {
        _state.value = newState
        BackgroundListeningDiagnostics.logEvent("STATE_TRANSITION", newState)
    }

    override fun startStandby() {
        if (isDestroyed) return
        isStandbyActive = true
        isPausedForSpeaking = false
        consecutiveErrors = 0
        currentMode = Mode.STANDBY
        transitionState(WakeWordState.WAKE_STANDBY)
        scheduleStandbyListening(100L)
    }

    override fun startCommandListening() {
        if (isDestroyed) return
        standbyLoopJob?.cancel()
        currentMode = Mode.COMMAND
        transitionState(WakeWordState.COMMAND_LISTENING)
        BackgroundListeningDiagnostics.recordCommandListeningStarted()
        startListeningInternal(Mode.COMMAND)
    }

    override fun pauseForSpeaking() {
        isPausedForSpeaking = true
        standbyLoopJob?.cancel()
        cancelListeningInternal()
        transitionState(WakeWordState.RETURNING_TO_STANDBY)
    }

    override fun resumeAfterSpeaking() {
        if (isDestroyed || !isStandbyActive) return
        isPausedForSpeaking = false
        transitionState(WakeWordState.RETURNING_TO_STANDBY)
        scheduleStandbyListening(350L)
    }

    override fun stop() {
        isStandbyActive = false
        standbyLoopJob?.cancel()
        cancelListeningInternal()
        transitionState(WakeWordState.WAKE_STANDBY)
    }

    override fun destroy() {
        isDestroyed = true
        isStandbyActive = false
        standbyLoopJob?.cancel()
        scope.cancel()

        mainHandler.post {
            destroyRecognizerInternal()
        }
    }

    private fun scheduleStandbyListening(delayMs: Long) {
        standbyLoopJob?.cancel()
        if (isDestroyed || !isStandbyActive || isPausedForSpeaking) return

        standbyLoopJob = scope.launch {
            delay(delayMs)
            if (isDestroyed || !isStandbyActive || isPausedForSpeaking) return@launch
            currentMode = Mode.STANDBY
            transitionState(WakeWordState.WAKE_STANDBY)
            startListeningInternal(Mode.STANDBY)
        }
    }

    private fun startListeningInternal(mode: Mode) {
        if (isDestroyed) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            transitionState(WakeWordState.ERROR)
            BackgroundListeningDiagnostics.recordRecognitionError(-1, "Permission RECORD_AUDIO not granted")
            return
        }

        mainHandler.post {
            if (isDestroyed) return@post

            // If a previous recognition session is still active, cancel it cleanly first
            if (isListeningActive) {
                cancelListeningInternal()
            }

            try {
                ensureRecognizer()
                val intent = createRecognizerIntent(mode)
                speechRecognizer?.startListening(intent)
                isListeningActive = true
                BackgroundListeningDiagnostics.recordRecognizerStarted()
            } catch (e: Exception) {
                isListeningActive = false
                handleErrorInternal(SpeechRecognizer.ERROR_CLIENT, "startListening exception: ${e.message}")
            }
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
            BackgroundListeningDiagnostics.recordRecognizerCreated()
        }
    }

    private fun cancelListeningInternal() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListeningActive = false
        BackgroundListeningDiagnostics.recordRecognizerStopped()
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListeningActive = false
        BackgroundListeningDiagnostics.recordRecognizerDestroyed()
    }

    private fun createRecognizerIntent(mode: Mode): Intent {
        val storage = SecureStorage(context)
        val lang = storage.getVoiceLanguage()

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Silent operation extras to minimize OS audio artifacts
            putExtra("android.speech.extra.DICTATION_MODE", true)

            if (mode == Mode.STANDBY) {
                // Standby: lightweight, max 1-2 results, fast return
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
                // Prefer offline recognition if available for speed and zero audio beep
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
            } else {
                // Command mode: rich language support
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                when (lang) {
                    SecureStorage.VOICE_LANG_HI -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                    }
                    SecureStorage.VOICE_LANG_EN -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN"))
                    }
                    else -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "hi-IN"))
                    }
                }
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                BackgroundListeningDiagnostics.logEvent("READY_FOR_SPEECH", _state.value)
            }

            override fun onBeginningOfSpeech() {
                BackgroundListeningDiagnostics.logEvent("BEGINNING_OF_SPEECH", _state.value)
                consecutiveErrors = 0
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                BackgroundListeningDiagnostics.logEvent("END_OF_SPEECH", _state.value)
                isListeningActive = false
            }

            override fun onError(error: Int) {
                isListeningActive = false
                val errorMsg = getErrorText(error)
                handleErrorInternal(error, errorMsg)
            }

            override fun onResults(results: Bundle?) {
                isListeningActive = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topMatch = matches?.firstOrNull()?.trim() ?: ""

                if (topMatch.isNotBlank()) {
                    BackgroundListeningDiagnostics.recordRecognitionResult(topMatch)
                }

                if (currentMode == Mode.STANDBY) {
                    processStandbyResults(topMatch, matches)
                } else {
                    processCommandResults(topMatch)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim() ?: ""
                if (partial.isBlank()) return

                val lower = partial.lowercase().trim()

                // Check for immediate stop interrupt in any mode
                if (isInterruptPhrase(lower)) {
                    cancelListeningInternal()
                    listener?.onStopInterrupt()
                    return
                }

                // In standby mode, detect wake-word early from partial results for instant response
                if (currentMode == Mode.STANDBY) {
                    val matchResult = wakeWordPattern.find(lower)
                    if (matchResult != null) {
                        // Immediate wake word detection! Cancel standby listening immediately
                        cancelListeningInternal()
                        triggerWakeDetected(partial, matchResult)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun isInterruptPhrase(text: String): Boolean {
        return interruptPhrases.contains(text) ||
                text.startsWith("stop ") ||
                text.startsWith("ruko ") ||
                text.startsWith("chup ")
    }

    private fun processStandbyResults(text: String, allMatches: List<String>?) {
        if (text.isBlank()) {
            // Normal silence in standby -> continue standby loop
            scheduleStandbyListening(250L)
            return
        }

        // Check if any match contains wake word
        val candidates = if (allMatches.isNullOrEmpty()) listOf(text) else allMatches
        var detectedMatch: MatchResult? = null
        var detectedRaw = ""

        for (candidate in candidates) {
            val m = wakeWordPattern.find(candidate.lowercase().trim())
            if (m != null) {
                detectedMatch = m
                detectedRaw = candidate
                break
            }
        }

        if (detectedMatch != null) {
            triggerWakeDetected(detectedRaw, detectedMatch)
        } else {
            // Speech detected but not wake word -> resume standby smoothly
            scheduleStandbyListening(250L)
        }
    }

    private fun triggerWakeDetected(rawSpeech: String, match: MatchResult) {
        standbyLoopJob?.cancel()
        consecutiveErrors = 0

        val wakePhrase = match.value
        val inlineCommand = rawSpeech.substring(match.range.last + 1).trim()

        BackgroundListeningDiagnostics.recordWakeDetected(wakePhrase)
        transitionState(WakeWordState.WAKE_DETECTED)

        listener?.onWakeWordDetected(
            phrase = wakePhrase,
            inlineCommand = inlineCommand.ifBlank { null }
        )
    }

    private fun processCommandResults(text: String) {
        if (text.isBlank()) {
            transitionState(WakeWordState.RETURNING_TO_STANDBY)
            listener?.onCommandTimeout()
        } else {
            transitionState(WakeWordState.PROCESSING)
            listener?.onCommandRecognized(text)
        }
    }

    private fun handleErrorInternal(errorCode: Int, errorMsg: String) {
        // ERROR_SPEECH_TIMEOUT (6) and ERROR_NO_MATCH (7) are expected and normal
        // in background standby when silence occurs.
        val isSilenceInStandby = currentMode == Mode.STANDBY && (
                errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        errorCode == SpeechRecognizer.ERROR_NO_MATCH
                )

        if (isSilenceInStandby) {
            // Silence must NOT permanently stop the assistant.
            // Bounded next cycle after brief rest.
            consecutiveErrors = 0
            scheduleStandbyListening(250L)
            return
        }

        if (currentMode == Mode.COMMAND && (
                    errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            errorCode == SpeechRecognizer.ERROR_NO_MATCH
                    )) {
            // In command listening, timeout means user didn't speak a command.
            consecutiveErrors = 0
            transitionState(WakeWordState.RETURNING_TO_STANDBY)
            listener?.onCommandTimeout()
            return
        }

        // Genuine or transient errors: ERROR_CLIENT (5), ERROR_RECOGNIZER_BUSY (8), ERROR_NETWORK (2), etc.
        consecutiveErrors++
        transitionState(WakeWordState.ERROR)
        BackgroundListeningDiagnostics.recordRecognitionError(errorCode, errorMsg)
        BackgroundListeningDiagnostics.recordRecoveryAttempt(consecutiveErrors)

        // If repeated errors occur or recognizer got stuck in client/busy error:
        // Safely destroy and recreate instance to guarantee clean state.
        if (consecutiveErrors >= 3 || errorCode == SpeechRecognizer.ERROR_CLIENT || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            mainHandler.post {
                destroyRecognizerInternal()
            }
        }

        // Bounded exponential backoff: 500ms -> 1s -> 2s, capped at 5s.
        val backoffMs = when (consecutiveErrors) {
            1 -> 500L
            2 -> 1000L
            3 -> 2000L
            else -> 5000L
        }

        scheduleStandbyListening(backoffMs)
        listener?.onError(errorCode, errorMsg)
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "UNKNOWN_ERROR_$errorCode"
        }
    }
}
