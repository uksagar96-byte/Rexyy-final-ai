package com.rexyy.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LifecycleDiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val state: String,
    val detail: String? = null
)

object BackgroundListeningDiagnostics {

    private val _currentListeningState = MutableStateFlow(WakeWordState.WAKE_STANDBY)
    val currentListeningState: StateFlow<WakeWordState> = _currentListeningState.asStateFlow()

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

    fun logEvent(event: String, state: WakeWordState, detail: String? = null) {
        _currentListeningState.value = state
        _lastRecognizerEvent.value = event
        val sanitizedDetail = sanitizeForPrivacy(detail)
        val entry = LifecycleDiagnosticEntry(
            event = event,
            state = state.name,
            detail = sanitizedDetail
        )
        _history.value = (listOf(entry) + _history.value).take(50)
    }

    fun recordRecognizerCreated() {
        _activeRecognizersCount.value = 1
        logEvent("RECOGNIZER_CREATED", _currentListeningState.value)
    }

    fun recordRecognizerStarted() {
        logEvent("RECOGNIZER_STARTED", _currentListeningState.value)
    }

    fun recordRecognizerStopped() {
        logEvent("RECOGNIZER_STOPPED", _currentListeningState.value)
    }

    fun recordRecognizerDestroyed() {
        _activeRecognizersCount.value = 0
        logEvent("RECOGNIZER_DESTROYED", _currentListeningState.value)
    }

    fun recordRecognitionResult(result: String) {
        _lastRecognitionResult.value = sanitizeForPrivacy(result)
        logEvent("RECOGNITION_RESULT", _currentListeningState.value, result)
    }

    fun recordRecognitionError(errorCode: Int, description: String) {
        _lastRecognitionError.value = errorCode
        _lastErrorDescription.value = description
        logEvent("RECOGNITION_ERROR", _currentListeningState.value, "Code: $errorCode ($description)")
    }

    fun recordRecoveryAttempt(attemptCount: Int) {
        _recoveryAttempts.value = attemptCount
        logEvent("RECOVERY_ATTEMPT", _currentListeningState.value, "Attempt #$attemptCount")
    }

    fun recordWakeDetected(phrase: String) {
        logEvent("WAKE_DETECTED", WakeWordState.WAKE_DETECTED, phrase)
    }

    fun recordCommandListeningStarted() {
        logEvent("COMMAND_LISTENING_STARTED", WakeWordState.COMMAND_LISTENING)
    }

    fun recordReturningToStandby() {
        logEvent("RETURNING_TO_STANDBY", WakeWordState.RETURNING_TO_STANDBY)
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
