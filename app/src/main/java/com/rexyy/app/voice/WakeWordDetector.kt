package com.rexyy.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.pill.DynamicPillManager
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

/**
 * Recognition lifecycle states for enforcing a single authoritative recognizer session.
 */
enum class RecognitionLifecycleState {
    IDLE,
    STARTING,
    LISTENING,
    STOPPING,
    RECOVERING,
    DESTROYED
}

/**
 * Listener callbacks for WakeWordDetector events.
 */
interface WakeWordListener {
    fun onWakeWordDetected(phrase: String, inlineCommand: String?)
    fun onCommandRecognized(command: String)
    fun onPartialCommandRecognized(partialText: String) {}
    fun onCommandTimeout()
    fun onError(errorCode: Int, message: String)
    fun onStopInterrupt()
    fun onMicReady() {}
    fun onMicSessionFailed(errorCode: Int, message: String) {}
}

/**
 * Abstraction for wake word detection and speech recognition lifecycle management.
 */
interface WakeWordDetector {
    val state: StateFlow<WakeWordState>
    val recognitionLifecycleState: StateFlow<RecognitionLifecycleState>

    fun isListening(): Boolean
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
            return AndroidWakeWordDetector(context)
        }
    }
}

/**
 * Android implementation of WakeWordDetector using a single controlled SpeechRecognizer
 * instance, guarded recovery scheduler, sensible standby intervals, on-device automatic fallback,
 * and explicit microphone session lifecycle tracking.
 */
class AndroidWakeWordDetector(
    private val context: Context
) : WakeWordDetector {

    companion object {
        const val STANDBY_CYCLE_DELAY_MS = 1200L      // Sensible rest between standby listening cycles
        const val RESUME_AFTER_SPEAKING_DELAY_MS = 400L
        const val ERROR_BACKOFF_INITIAL_MS = 1500L
        const val ERROR_BACKOFF_MAX_MS = 5000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WakeWordState.WAKE_STANDBY)
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _recognitionLifecycleState = MutableStateFlow(RecognitionLifecycleState.IDLE)
    override val recognitionLifecycleState: StateFlow<RecognitionLifecycleState> = _recognitionLifecycleState.asStateFlow()

    private var listener: WakeWordListener? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isStandbyActive = false
    private var isDestroyed = false
    private var isPausedForSpeaking = false

    private var consecutiveErrors = 0
    private var recoveryJob: Job? = null
    private var isRecoveryScheduled = false
    private var wakeAlreadyTriggered = false

    // Controlled on-device recognizer preference with automatic fallback
    private val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var onDeviceFailed = false

    val recognizerId: String
        get() = speechRecognizer?.let { "Rec#${System.identityHashCode(it)}" } ?: "Rec#None"

    private enum class Mode {
        STANDBY,
        COMMAND
    }

    private var currentMode = Mode.STANDBY

    // Regex for detecting wake words
    // Canonical: "Hello REXXY". Also accepts: hello rexxy, hello rexy, hey rexxy, hey rexy, suno rexxy, hi rexxy, ok rexxy, rexxy, rexyy, rexy, rex
    private val wakeWordPattern = Regex(
        """(?:\b(?:hello|hey|hi|ok|suno)\s+)?\b(?:rexxy|rexyy|rexy|rex|rexi|rexie)\b""",
        RegexOption.IGNORE_CASE
    )

    private val interruptPhrases = setOf(
        "stop", "ruko", "ruk ja", "bas", "cancel", "chup", "chup raho", "shant"
    )

    override fun isListening(): Boolean {
        return _recognitionLifecycleState.value == RecognitionLifecycleState.LISTENING ||
                _recognitionLifecycleState.value == RecognitionLifecycleState.STARTING
    }

    override fun setListener(listener: WakeWordListener?) {
        this.listener = listener
    }

    override fun transitionState(newState: WakeWordState) {
        _state.value = newState
        BackgroundListeningDiagnostics.logEvent(
            "STATE_TRANSITION",
            newState,
            recognizerInstanceId = recognizerId,
            recognitionState = _recognitionLifecycleState.value.name
        )
    }

    private fun transitionRecognitionState(newState: RecognitionLifecycleState, reason: String) {
        _recognitionLifecycleState.value = newState
        BackgroundListeningDiagnostics.logEvent(
            "RECOGNITION_STATE_CHANGE",
            state = _state.value,
            detail = "Transition to ${newState.name} ($reason)",
            recognizerInstanceId = recognizerId,
            recognitionState = newState.name,
            reasonStart = if (newState == RecognitionLifecycleState.STARTING) reason else null,
            reasonStop = if (newState == RecognitionLifecycleState.STOPPING) reason else null,
            isStartScheduled = isRecoveryScheduled
        )
    }

    override fun startStandby() {
        if (isDestroyed) return
        isStandbyActive = true
        isPausedForSpeaking = false
        wakeAlreadyTriggered = false
        cancelGuardedRecovery("startStandby")
        consecutiveErrors = 0
        currentMode = Mode.STANDBY
        transitionState(WakeWordState.WAKE_STANDBY)

        val curState = _recognitionLifecycleState.value
        if (curState != RecognitionLifecycleState.STARTING && curState != RecognitionLifecycleState.LISTENING) {
            scheduleGuardedRecovery(100L, "startStandby")
        }
    }

    override fun startCommandListening() {
        if (isDestroyed) return
        isPausedForSpeaking = false
        wakeAlreadyTriggered = false
        cancelGuardedRecovery("startCommandListening")
        currentMode = Mode.COMMAND
        transitionState(WakeWordState.COMMAND_LISTENING)
        BackgroundListeningDiagnostics.recordCommandListeningStarted()

        cancelListeningInternal("switching_to_command")
        startListeningInternal(Mode.COMMAND, "user_command_mode")
    }

    override fun pauseForSpeaking() {
        isPausedForSpeaking = true
        cancelGuardedRecovery("pauseForSpeaking")
        cancelListeningInternal("pauseForSpeaking")
        transitionState(WakeWordState.RETURNING_TO_STANDBY)
    }

    override fun resumeAfterSpeaking() {
        if (isDestroyed || !isStandbyActive) return
        isPausedForSpeaking = false
        wakeAlreadyTriggered = false
        transitionState(WakeWordState.RETURNING_TO_STANDBY)
        scheduleGuardedRecovery(RESUME_AFTER_SPEAKING_DELAY_MS, "resumeAfterSpeaking")
    }

    override fun stop() {
        isStandbyActive = false
        wakeAlreadyTriggered = false
        cancelGuardedRecovery("stop")
        cancelListeningInternal("stop")
        transitionRecognitionState(RecognitionLifecycleState.IDLE, "stop")
        transitionState(WakeWordState.WAKE_STANDBY)
    }

    override fun destroy() {
        isDestroyed = true
        isStandbyActive = false
        wakeAlreadyTriggered = false
        cancelGuardedRecovery("destroy")
        scope.cancel()

        mainHandler.post {
            destroyRecognizerInternal("destroy")
        }
    }

    private fun scheduleGuardedRecovery(delayMs: Long, reason: String) {
        if (isDestroyed || !isStandbyActive || isPausedForSpeaking) return
        if (currentMode != Mode.STANDBY) return

        if (isRecoveryScheduled) {
            return
        }

        isRecoveryScheduled = true
        transitionRecognitionState(RecognitionLifecycleState.RECOVERING, reason)

        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(delayMs)
            isRecoveryScheduled = false
            if (isDestroyed || !isStandbyActive || isPausedForSpeaking || currentMode != Mode.STANDBY) {
                return@launch
            }
            startListeningInternal(Mode.STANDBY, "guarded_recovery_after_$reason")
        }
    }

    private fun cancelGuardedRecovery(reason: String) {
        isRecoveryScheduled = false
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun startListeningInternal(mode: Mode, reason: String) {
        if (isDestroyed || isPausedForSpeaking) return
        if (mode == Mode.STANDBY && !isStandbyActive) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            transitionState(WakeWordState.MICROPHONE_DISABLED)
            transitionRecognitionState(RecognitionLifecycleState.IDLE, "no_mic_permission")
            DynamicPillManager.postMicrophoneDisabled()
            BackgroundListeningDiagnostics.recordRecognitionError(-1, "Permission RECORD_AUDIO not granted")
            cancelGuardedRecovery("no_mic_permission")
            return
        }

        mainHandler.post {
            if (isDestroyed || isPausedForSpeaking) return@post
            if (mode == Mode.STANDBY && !isStandbyActive) return@post

            // Strict duplicate start prevention
            val curState = _recognitionLifecycleState.value
            if (curState == RecognitionLifecycleState.STARTING || curState == RecognitionLifecycleState.LISTENING) {
                BackgroundListeningDiagnostics.logEvent(
                    "RECOGNIZER_START_REJECTED",
                    state = _state.value,
                    detail = "Already in $curState, rejected start (reason: $reason)",
                    recognizerInstanceId = recognizerId,
                    recognitionState = curState.name
                )
                return@post
            }

            try {
                ensureRecognizer()
                transitionRecognitionState(RecognitionLifecycleState.STARTING, reason)

                val id = recognizerId
                BackgroundListeningDiagnostics.recordRecognizerStartRequest(
                    recognizerId = id,
                    reason = reason,
                    mode = mode.name,
                    isScheduled = isRecoveryScheduled
                )
                BackgroundListeningDiagnostics.recordMicRequested()
                BackgroundListeningDiagnostics.recordMicSessionRequested(id, reason)

                val useOnDevice = onDeviceAvailable && !onDeviceFailed
                val recognizerType = if (useOnDevice) "OnDeviceSpeechRecognizer" else "StandardSpeechRecognizer"
                BackgroundListeningDiagnostics.recordMicSessionStarting(id, recognizerType)

                val intent = createRecognizerIntent(mode)
                speechRecognizer?.startListening(intent)

                BackgroundListeningDiagnostics.recordRecognizerStartAccepted(
                    recognizerId = id,
                    reason = reason,
                    mode = mode.name
                )
                BackgroundListeningDiagnostics.recordSpeechRecognizerStarted()

                if (mode == Mode.STANDBY) {
                    BackgroundListeningDiagnostics.recordWakeEngineListening()
                }
            } catch (e: Exception) {
                val id = recognizerId
                BackgroundListeningDiagnostics.recordMicSessionFailed(id, SpeechRecognizer.ERROR_CLIENT, "startListening exception: ${e.message}")
                transitionRecognitionState(RecognitionLifecycleState.STOPPING, "start_exception")
                handleErrorInternal(SpeechRecognizer.ERROR_CLIENT, "startListening exception: ${e.message}")
            }
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            val useOnDevice = onDeviceAvailable && !onDeviceFailed
            val recognizerType = if (useOnDevice) "OnDeviceSpeechRecognizer" else "StandardSpeechRecognizer"

            speechRecognizer = try {
                if (useOnDevice) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                if (useOnDevice) {
                    BackgroundListeningDiagnostics.logEvent(
                        "ON_DEVICE_CREATE_EXCEPTION_FALLING_BACK",
                        detail = "OnDeviceSpeechRecognizer failed (${e.message}), using StandardSpeechRecognizer",
                        component = "AndroidWakeWordDetector"
                    )
                    onDeviceFailed = true
                    SpeechRecognizer.createSpeechRecognizer(context)
                } else {
                    throw e
                }
            }

            val id = recognizerId
            BackgroundListeningDiagnostics.recordRecognizerCreate(id, recognizerType)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }
    }

    private fun cancelListeningInternal(reason: String = "cancel") {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        val id = recognizerId
        BackgroundListeningDiagnostics.recordRecognizerCancel(id, reason)
        BackgroundListeningDiagnostics.recordSpeechRecognizerStopped()
        BackgroundListeningDiagnostics.recordMicReleased()
        BackgroundListeningDiagnostics.recordMicSessionEnded(id, reason)
        transitionRecognitionState(RecognitionLifecycleState.STOPPING, reason)
    }

    private fun destroyRecognizerInternal(reason: String = "destroy") {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        val id = recognizerId
        BackgroundListeningDiagnostics.recordRecognizerDestroy(id, reason)
        BackgroundListeningDiagnostics.recordSpeechRecognizerDestroyed()
        BackgroundListeningDiagnostics.recordMicSessionEnded(id, reason)
        speechRecognizer = null
        transitionRecognitionState(RecognitionLifecycleState.DESTROYED, reason)
    }

    private fun createRecognizerIntent(mode: Mode): Intent {
        val storage = SecureStorage(context)
        val lang = storage.getVoiceLanguage()

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Standard supported extras without forcing offline-only mode
            if (mode == Mode.STANDBY) {
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
            } else {
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
                transitionRecognitionState(RecognitionLifecycleState.LISTENING, "onReadyForSpeech")
                val id = recognizerId
                BackgroundListeningDiagnostics.recordRecognizerOnReady(id)
                BackgroundListeningDiagnostics.recordSpeechRecognizerReady()
                BackgroundListeningDiagnostics.recordMicActive()
                BackgroundListeningDiagnostics.recordMicSessionReady(id)
                BackgroundListeningDiagnostics.recordMicSessionActive(id)
                consecutiveErrors = 0
                listener?.onMicReady()
            }

            override fun onBeginningOfSpeech() {
                val id = recognizerId
                BackgroundListeningDiagnostics.recordRecognizerOnBeginning(id)
                BackgroundListeningDiagnostics.recordMicSessionActive(id)
                consecutiveErrors = 0
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 0.5f) {
                    BackgroundListeningDiagnostics.recordMicActive()
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                val id = recognizerId
                BackgroundListeningDiagnostics.recordRecognizerOnEnd(id)
                BackgroundListeningDiagnostics.recordMicReleased()
                BackgroundListeningDiagnostics.recordMicSessionEnded(id, "EndOfSpeech")
                transitionRecognitionState(RecognitionLifecycleState.STOPPING, "onEndOfSpeech")

                if (currentMode == Mode.STANDBY && !isRecoveryScheduled && !wakeAlreadyTriggered) {
                    scheduleGuardedRecovery(STANDBY_CYCLE_DELAY_MS, "onEndOfSpeech_fallback")
                }
            }

            override fun onError(error: Int) {
                val id = recognizerId
                val errorMsg = getErrorText(error)
                BackgroundListeningDiagnostics.recordRecognizerOnError(id, error, errorMsg)
                BackgroundListeningDiagnostics.recordMicSessionFailed(id, error, errorMsg)
                transitionRecognitionState(RecognitionLifecycleState.STOPPING, "onError_$errorMsg")

                // Controlled On-Device recognition fallback (Section 7 Requirement)
                val usingOnDevice = onDeviceAvailable && !onDeviceFailed
                if (usingOnDevice && error == SpeechRecognizer.ERROR_CLIENT) {
                    BackgroundListeningDiagnostics.logEvent(
                        "ON_DEVICE_FALLBACK_TO_STANDARD",
                        detail = "On-device recognition failed with ERROR_CLIENT. Falling back to StandardSpeechRecognizer.",
                        component = "AndroidWakeWordDetector"
                    )
                    onDeviceFailed = true
                    destroyRecognizerInternal("on_device_fallback")
                    scheduleGuardedRecovery(300L, "on_device_fallback")
                    return
                }

                handleErrorInternal(error, errorMsg)
            }

            override fun onResults(results: Bundle?) {
                val id = recognizerId
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topMatch = matches?.firstOrNull()?.trim() ?: ""

                BackgroundListeningDiagnostics.recordRecognizerOnResults(id, topMatch)
                transitionRecognitionState(RecognitionLifecycleState.IDLE, "onResults")

                if (topMatch.isNotBlank()) {
                    BackgroundListeningDiagnostics.recordRecognitionResult(topMatch)
                    if (currentMode == Mode.COMMAND) {
                        BackgroundListeningDiagnostics.recordCommandRecognizedDiagnostic(topMatch)
                    }
                }

                if (currentMode == Mode.STANDBY) {
                    processStandbyResults(topMatch, matches)
                } else {
                    if (topMatch.isNotBlank()) {
                        mainHandler.post {
                            DynamicPillManager.postCommandRecognized(topMatch)
                        }
                    }
                    processCommandResults(topMatch)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim() ?: ""
                if (partial.isBlank()) return

                val id = recognizerId
                BackgroundListeningDiagnostics.recordRecognizerOnPartial(id, partial)

                val lower = partial.lowercase().trim()

                if (isInterruptPhrase(lower)) {
                    cancelListeningInternal("stop_interrupt")
                    listener?.onStopInterrupt()
                    return
                }

                if (currentMode == Mode.COMMAND) {
                    mainHandler.post {
                        listener?.onPartialCommandRecognized(partial)
                        DynamicPillManager.postLiveSpeech(partial)
                    }
                } else if (currentMode == Mode.STANDBY) {
                    val matchResult = wakeWordPattern.find(lower)
                    if (matchResult != null) {
                        cancelGuardedRecovery("wake_detected_in_partial")
                        wakeAlreadyTriggered = true
                        cancelListeningInternal("wake_detected_in_partial")
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
        if (wakeAlreadyTriggered) return

        if (text.isBlank()) {
            scheduleGuardedRecovery(STANDBY_CYCLE_DELAY_MS, "standby_silence")
            return
        }

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
            cancelGuardedRecovery("wake_detected_in_results")
            cancelListeningInternal("wake_detected_in_results")
            triggerWakeDetected(detectedRaw, detectedMatch)
        } else {
            scheduleGuardedRecovery(STANDBY_CYCLE_DELAY_MS, "standby_no_wake_match")
        }
    }

    private fun triggerWakeDetected(rawSpeech: String, match: MatchResult) {
        if (wakeAlreadyTriggered) return
        wakeAlreadyTriggered = true

        cancelGuardedRecovery("triggerWakeDetected")
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
        wakeAlreadyTriggered = false
        if (text.isBlank()) {
            transitionState(WakeWordState.RETURNING_TO_STANDBY)
            listener?.onCommandTimeout()
        } else {
            transitionState(WakeWordState.PROCESSING)
            DynamicPillManager.postProcessing(text)
            listener?.onCommandRecognized(text)
        }
    }

    private fun handleErrorInternal(errorCode: Int, errorMsg: String) {
        if (wakeAlreadyTriggered) {
            return
        }

        if (currentMode == Mode.COMMAND) {
            consecutiveErrors = 0
            wakeAlreadyTriggered = false
            transitionState(WakeWordState.RETURNING_TO_STANDBY)
            listener?.onCommandTimeout()
            return
        }

        val isSilenceInStandby = currentMode == Mode.STANDBY && (
                errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        errorCode == SpeechRecognizer.ERROR_NO_MATCH
                )

        if (isSilenceInStandby) {
            consecutiveErrors = 0
            scheduleGuardedRecovery(STANDBY_CYCLE_DELAY_MS, "normal_standby_silence")
            return
        }

        consecutiveErrors++
        BackgroundListeningDiagnostics.recordSpeechRecognizerError(errorCode, errorMsg)
        BackgroundListeningDiagnostics.recordRecoveryAttempt(consecutiveErrors)
        BackgroundListeningDiagnostics.recordWakeEngineError("SpeechRecognizer error $errorCode ($errorMsg), attempt #$consecutiveErrors")

        if (consecutiveErrors >= 3) {
            DynamicPillManager.postMicrophoneUnavailable()
            listener?.onMicSessionFailed(errorCode, errorMsg)
        } else {
            DynamicPillManager.postReconnecting()
        }

        if (consecutiveErrors >= 5) {
            mainHandler.post {
                destroyRecognizerInternal("consecutive_errors_exceeded")
            }
        } else {
            cancelListeningInternal("transient_error_backoff")
        }

        val backoffMs = when (consecutiveErrors) {
            1 -> ERROR_BACKOFF_INITIAL_MS
            2 -> 3000L
            else -> ERROR_BACKOFF_MAX_MS
        }

        scheduleGuardedRecovery(backoffMs, "error_backoff_$errorCode")
        transitionState(WakeWordState.WAKE_STANDBY)
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
