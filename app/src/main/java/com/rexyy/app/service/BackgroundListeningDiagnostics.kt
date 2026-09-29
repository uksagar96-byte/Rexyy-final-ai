package com.rexyy.app.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LifecycleDiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val component: String = "Service",
    val contextType: String = "ServiceContext",
    val state: String = "WAKE_STANDBY",
    val serviceInstanceId: String? = null,
    val recognizerInstanceId: String? = null,
    val recognitionState: String = "IDLE",
    val reasonStart: String? = null,
    val reasonStop: String? = null,
    val errorCode: Int? = null,
    val isStartScheduled: Boolean = false,
    val detail: String? = null
)

object BackgroundListeningDiagnostics {

    private const val TAG = "RexyyBackgroundDiag"

    private val _currentListeningState = MutableStateFlow(WakeWordState.WAKE_STANDBY)
    val currentListeningState: StateFlow<WakeWordState> = _currentListeningState.asStateFlow()

    private val _currentRecognitionLifecycleState = MutableStateFlow("IDLE")
    val currentRecognitionLifecycleState: StateFlow<String> = _currentRecognitionLifecycleState.asStateFlow()

    private val _lastRecognizerEvent = MutableStateFlow("NONE")
    val lastRecognizerEvent: StateFlow<String> = _lastRecognizerEvent.asStateFlow()

    private val _lastRecognitionResult = MutableStateFlow<String?>(null)
    val lastRecognitionResult: StateFlow<String?> = _lastRecognitionResult.asStateFlow()

    private val _lastRecognitionError = MutableStateFlow<Int?>(null)
    val lastRecognitionError: StateFlow<Int?> = _lastRecognitionError.asStateFlow()

    private val _lastErrorDescription = MutableStateFlow<String?>(null)
    val lastErrorDescription: StateFlow<String?> = _lastErrorDescription.asStateFlow()

    private val _recoveryAttempts = MutableStateFlow(0)
    val recoveryAttempts: StateFlow<Int> = _recoveryAttempts.asStateFlow()

    private val _activeRecognizersCount = MutableStateFlow(0)
    val activeRecognizersCount: StateFlow<Int> = _activeRecognizersCount.asStateFlow()

    private val _history = MutableStateFlow<List<LifecycleDiagnosticEntry>>(emptyList())
    val history: StateFlow<List<LifecycleDiagnosticEntry>> = _history.asStateFlow()

    fun logEvent(
        event: String,
        state: WakeWordState = _currentListeningState.value,
        detail: String? = null,
        component: String = "Service",
        contextType: String = "ServiceContext",
        serviceInstanceId: String? = null,
        recognizerInstanceId: String? = null,
        recognitionState: String = _currentRecognitionLifecycleState.value,
        reasonStart: String? = null,
        reasonStop: String? = null,
        errorCode: Int? = null,
        isStartScheduled: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        _currentListeningState.value = state
        _currentRecognitionLifecycleState.value = recognitionState
        _lastRecognizerEvent.value = event
        val sanitizedDetail = sanitizeForPrivacy(detail)
        val entry = LifecycleDiagnosticEntry(
            timestamp = now,
            event = event,
            component = component,
            contextType = contextType,
            state = state.name,
            serviceInstanceId = serviceInstanceId,
            recognizerInstanceId = recognizerInstanceId,
            recognitionState = recognitionState,
            reasonStart = reasonStart,
            reasonStop = reasonStop,
            errorCode = errorCode,
            isStartScheduled = isStartScheduled,
            detail = sanitizedDetail
        )
        _history.value = (listOf(entry) + _history.value).take(150)
        try {
            Log.d(TAG, "[$now] [$event] [$component] [$contextType] [${state.name}] [RecState: $recognitionState] [Svc: $serviceInstanceId] [Rec: $recognizerInstanceId] ${sanitizedDetail ?: ""}")
        } catch (_: Throwable) {
            // JVM test environment fallback
        }
    }

    // Service Lifecycle Diagnostics with Instance Tracking
    fun recordServiceCreated(serviceInstanceId: String = "ServiceDefault") {
        logEvent("SERVICE_CREATED", _currentListeningState.value, component = "RexyyBackgroundAssistantService", contextType = "ServiceContext", serviceInstanceId = serviceInstanceId)
    }

    fun recordServiceStarted(serviceInstanceId: String = "ServiceDefault") {
        logEvent("SERVICE_STARTED", _currentListeningState.value, component = "RexyyBackgroundAssistantService", contextType = "ServiceContext", serviceInstanceId = serviceInstanceId)
    }

    fun recordServiceForeground(serviceInstanceId: String = "ServiceDefault") {
        logEvent("SERVICE_FOREGROUND", _currentListeningState.value, component = "RexyyBackgroundAssistantService", contextType = "ServiceContext", serviceInstanceId = serviceInstanceId)
    }

    fun recordServiceDestroyed(serviceInstanceId: String = "ServiceDefault") {
        logEvent("SERVICE_DESTROYED", _currentListeningState.value, component = "RexyyBackgroundAssistantService", contextType = "ServiceContext", serviceInstanceId = serviceInstanceId)
    }

    // Granular Recognition Operation Diagnostics (Section 3 Requirement)
    fun recordRecognizerCreate(recognizerId: String, recognizerType: String) {
        _activeRecognizersCount.value = 1
        logEvent(
            "RECOGNIZER_CREATE",
            _currentListeningState.value,
            detail = recognizerType,
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "IDLE"
        )
        recordSpeechRecognizerCreated(recognizerType)
    }

    fun recordRecognizerStartRequest(recognizerId: String, reason: String, mode: String, isScheduled: Boolean) {
        logEvent(
            "RECOGNIZER_START_REQUEST",
            _currentListeningState.value,
            detail = "Mode: $mode, Reason: $reason",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STARTING",
            reasonStart = reason,
            isStartScheduled = isScheduled
        )
    }

    fun recordRecognizerStartAccepted(recognizerId: String, reason: String, mode: String) {
        logEvent(
            "RECOGNIZER_START_ACCEPTED",
            _currentListeningState.value,
            detail = "Mode: $mode, Reason: $reason",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STARTING",
            reasonStart = reason
        )
    }

    fun recordRecognizerStop(recognizerId: String, reason: String) {
        logEvent(
            "RECOGNIZER_STOP",
            _currentListeningState.value,
            detail = "Reason: $reason",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            reasonStop = reason
        )
    }

    fun recordRecognizerCancel(recognizerId: String, reason: String) {
        logEvent(
            "RECOGNIZER_CANCEL",
            _currentListeningState.value,
            detail = "Reason: $reason",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            reasonStop = reason
        )
    }

    fun recordRecognizerDestroy(recognizerId: String, reason: String) {
        _activeRecognizersCount.value = 0
        logEvent(
            "RECOGNIZER_DESTROY",
            _currentListeningState.value,
            detail = "Reason: $reason",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "DESTROYED",
            reasonStop = reason
        )
    }

    fun recordRecognizerOnReady(recognizerId: String) {
        logEvent(
            "RECOGNIZER_ON_READY",
            _currentListeningState.value,
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "LISTENING"
        )
    }

    fun recordRecognizerOnBeginning(recognizerId: String) {
        logEvent(
            "RECOGNIZER_ON_BEGINNING",
            _currentListeningState.value,
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "LISTENING"
        )
    }

    fun recordRecognizerOnEnd(recognizerId: String) {
        logEvent(
            "RECOGNIZER_ON_END",
            _currentListeningState.value,
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            reasonStop = "EndOfSpeech"
        )
    }

    fun recordRecognizerOnResults(recognizerId: String, resultSummary: String) {
        logEvent(
            "RECOGNIZER_ON_RESULTS",
            _currentListeningState.value,
            detail = sanitizeForPrivacy(resultSummary),
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "IDLE"
        )
    }

    fun recordRecognizerOnPartial(recognizerId: String, partial: String) {
        logEvent(
            "RECOGNIZER_ON_PARTIAL",
            _currentListeningState.value,
            detail = sanitizeForPrivacy(partial),
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "LISTENING"
        )
    }

    fun recordRecognizerOnError(recognizerId: String, errorCode: Int, errorMsg: String) {
        logEvent(
            "RECOGNIZER_ON_ERROR",
            _currentListeningState.value,
            detail = "Code $errorCode: $errorMsg",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            errorCode = errorCode,
            reasonStop = errorMsg
        )
    }

    // Explicit Microphone Session States (Section 2 Requirement)
    fun recordMicSessionRequested(recognizerId: String, reason: String) {
        logEvent(
            "MIC_SESSION_REQUESTED",
            _currentListeningState.value,
            detail = "Requested session ($reason)",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STARTING",
            reasonStart = reason
        )
    }

    fun recordMicSessionStarting(recognizerId: String, recognizerType: String) {
        logEvent(
            "MIC_SESSION_STARTING",
            _currentListeningState.value,
            detail = "Recognizer starting ($recognizerType)",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STARTING"
        )
    }

    fun recordMicSessionReady(recognizerId: String) {
        logEvent(
            "MIC_SESSION_READY",
            _currentListeningState.value,
            detail = "Audio hardware ready for speech",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "LISTENING"
        )
    }

    fun recordMicSessionActive(recognizerId: String) {
        logEvent(
            "MIC_SESSION_ACTIVE",
            _currentListeningState.value,
            detail = "Active microphone capture confirmed by audio lifecycle",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "LISTENING"
        )
    }

    fun recordMicSessionEnded(recognizerId: String, reason: String) {
        logEvent(
            "MIC_SESSION_ENDED",
            _currentListeningState.value,
            detail = "Session ended ($reason)",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            reasonStop = reason
        )
    }

    fun recordMicSessionFailed(recognizerId: String, errorCode: Int, errorMsg: String) {
        logEvent(
            "MIC_SESSION_FAILED",
            _currentListeningState.value,
            detail = "Session failed: Code $errorCode ($errorMsg)",
            component = "AndroidWakeWordDetector",
            contextType = "ServiceContext",
            recognizerInstanceId = recognizerId,
            recognitionState = "STOPPING",
            errorCode = errorCode,
            reasonStop = errorMsg
        )
    }

    // Wake Engine Lifecycle Diagnostics
    fun recordWakeEngineCreated() {
        logEvent("WAKE_ENGINE_CREATED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordWakeEngineStarted() {
        logEvent("WAKE_ENGINE_STARTED", WakeWordState.WAKE_STANDBY, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordWakeEngineListening() {
        logEvent("WAKE_ENGINE_LISTENING", WakeWordState.WAKE_STANDBY, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordWakeEngineStopped() {
        logEvent("WAKE_ENGINE_STOPPED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordWakeEngineError(detail: String) {
        logEvent("WAKE_ENGINE_ERROR", _currentListeningState.value, detail = detail, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    // Microphone Hardware State Diagnostics
    fun recordMicRequested() {
        logEvent("MIC_REQUESTED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordMicActive() {
        logEvent("MIC_ACTIVE", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordMicReleased() {
        logEvent("MIC_RELEASED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    // SpeechRecognizer Lifecycle Diagnostics
    fun recordSpeechRecognizerCreated(recognizerType: String = "SpeechRecognizer") {
        _activeRecognizersCount.value = 1
        logEvent("SPEECH_RECOGNIZER_CREATED", _currentListeningState.value, detail = recognizerType, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerStarted() {
        logEvent("SPEECH_RECOGNIZER_STARTED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerReady() {
        logEvent("SPEECH_RECOGNIZER_READY", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerResult(result: String) {
        _lastRecognitionResult.value = sanitizeForPrivacy(result)
        logEvent("SPEECH_RECOGNIZER_RESULT", _currentListeningState.value, detail = result, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerError(errorCode: Int, description: String) {
        _lastRecognitionError.value = errorCode
        _lastErrorDescription.value = description
        logEvent("SPEECH_RECOGNIZER_ERROR", _currentListeningState.value, detail = "Code $errorCode: $description", component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerStopped() {
        logEvent("SPEECH_RECOGNIZER_STOPPED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordSpeechRecognizerDestroyed() {
        _activeRecognizersCount.value = 0
        logEvent("SPEECH_RECOGNIZER_DESTROYED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    // Wake and Command Events
    fun recordWakeDetected(phrase: String) {
        logEvent("WAKE_DETECTED", WakeWordState.WAKE_DETECTED, detail = phrase, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordCommandListeningStarted() {
        logEvent("COMMAND_LISTENING", WakeWordState.COMMAND_LISTENING, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordCommandRecognizedDiagnostic(command: String) {
        logEvent("COMMAND_RECOGNIZED", WakeWordState.COMMAND_RECOGNIZED, detail = command, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordPillStateChanged(title: String, subtitle: String) {
        logEvent("PILL_STATE_CHANGED", _currentListeningState.value, detail = "$title: $subtitle", component = "DynamicPillManager", contextType = "ServiceContext")
    }

    // Compatibility aliases and primary recognizer methods
    fun recordRecognizerCreated() {
        _activeRecognizersCount.value = 1
        logEvent("RECOGNIZER_CREATED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordRecognizerStarted() {
        logEvent("RECOGNIZER_STARTED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordRecognizerStopped() {
        logEvent("RECOGNIZER_STOPPED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordRecognizerDestroyed() {
        _activeRecognizersCount.value = 0
        logEvent("RECOGNIZER_DESTROYED", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    fun recordRecognitionResult(result: String) {
        recordSpeechRecognizerResult(result)
    }

    fun recordRecognitionError(errorCode: Int, description: String) {
        recordSpeechRecognizerError(errorCode, description)
    }

    fun recordRecoveryAttempt(attemptCount: Int) {
        _recoveryAttempts.value = attemptCount
        logEvent("RECOVERY_ATTEMPT", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext", detail = "Attempt #$attemptCount")
    }

    fun recordReturningToStandby() {
        logEvent("RETURNING_TO_STANDBY", WakeWordState.RETURNING_TO_STANDBY, component = "AndroidWakeWordDetector", contextType = "ServiceContext")
    }

    enum class ActivationSource {
        NONE,
        MANUAL,
        ALWAYS_READY
    }

    private val _lastActivationSource = MutableStateFlow(ActivationSource.NONE)
    val lastActivationSource: StateFlow<ActivationSource> = _lastActivationSource.asStateFlow()

    fun recordManualActivationRequested() {
        _lastActivationSource.value = ActivationSource.MANUAL
        logEvent("MANUAL_ACTIVATION_REQUESTED", _currentListeningState.value, component = "RexyyCoreTap", contextType = "UIContext", detail = "User tapped REXXY core/animation")
    }

    fun recordManualActivationStarted() {
        logEvent("MANUAL_ACTIVATION_STARTED", _currentListeningState.value, component = "BackgroundAssistantManager", contextType = "ServiceContext", detail = "Starting assistant via manual path")
    }

    fun recordManualMicReady() {
        logEvent("MANUAL_MIC_READY", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext", detail = "Microphone ready from manual activation")
    }

    fun recordAlwaysReadyEnabled(enabled: Boolean) {
        logEvent("ALWAYS_READY_ENABLED", _currentListeningState.value, component = "SettingsScreen", contextType = "UIContext", detail = "enabled=$enabled")
    }

    fun recordAlwaysReadyActivationRequested() {
        _lastActivationSource.value = ActivationSource.ALWAYS_READY
        logEvent("ALWAYS_READY_ACTIVATION_REQUESTED", _currentListeningState.value, component = "AlwaysReadyController", contextType = "BackgroundContext", detail = "Always Ready auto-activation requested")
    }

    fun recordAlwaysReadyActivationStarted() {
        logEvent("ALWAYS_READY_ACTIVATION_STARTED", _currentListeningState.value, component = "BackgroundAssistantManager", contextType = "ServiceContext", detail = "Always Ready starting existing voice engine")
    }

    fun recordAlwaysReadyMicReady() {
        logEvent("ALWAYS_READY_MIC_READY", _currentListeningState.value, component = "AndroidWakeWordDetector", contextType = "ServiceContext", detail = "Microphone ready under Always Ready")
    }

    fun reset() {
        _currentListeningState.value = WakeWordState.WAKE_STANDBY
        _lastRecognizerEvent.value = "NONE"
        _lastRecognitionResult.value = null
        _lastRecognitionError.value = null
        _lastErrorDescription.value = null
        _recoveryAttempts.value = 0
        _activeRecognizersCount.value = 0
        _history.value = emptyList()
    }

    private fun sanitizeForPrivacy(text: String?): String? {
        if (text == null) return null
        val lower = text.lowercase()
        // Never log passwords, OTPs, PINs, CVVs, or sensitive credentials
        if (lower.contains("password") || lower.contains("pin") || lower.contains("otp") || lower.contains("cvv")) {
            return "[REDACTED_SENSITIVE_CONTENT]"
        }
        return text.take(60)
    }
}
