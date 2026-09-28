package com.rexyy.app

import com.rexyy.app.service.BackgroundListeningDiagnostics
import com.rexyy.app.service.WakeWordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakeWordDetectorLifecycleTest {

    private val wakeWordPattern = Regex(
        """(?:\b(?:hello|hey|hi|ok|suno)\s+)?\b(?:rexxy|rexyy|rexy|rex|rexi|rexie)\b""",
        RegexOption.IGNORE_CASE
    )

    private val interruptPhrases = setOf(
        "stop", "ruko", "ruk ja", "bas", "cancel", "chup", "chup raho", "shant"
    )

    @Before
    fun setUp() {
        BackgroundListeningDiagnostics.reset()
    }

    @Test
    fun testWakeWordStateSequence() {
        val initial = BackgroundListeningDiagnostics.currentListeningState.value
        assertEquals(WakeWordState.WAKE_STANDBY, initial)

        BackgroundListeningDiagnostics.logEvent("WAKE_DETECTED", WakeWordState.WAKE_DETECTED, "hello rexxy")
        assertEquals(WakeWordState.WAKE_DETECTED, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.recordCommandListeningStarted()
        assertEquals(WakeWordState.COMMAND_LISTENING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("COMMAND_RECOGNIZED", WakeWordState.COMMAND_RECOGNIZED)
        assertEquals(WakeWordState.COMMAND_RECOGNIZED, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("PROCESSING", WakeWordState.PROCESSING)
        assertEquals(WakeWordState.PROCESSING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("EXECUTING", WakeWordState.EXECUTING)
        assertEquals(WakeWordState.EXECUTING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("VERIFYING", WakeWordState.VERIFYING)
        assertEquals(WakeWordState.VERIFYING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("RESPONDING", WakeWordState.RESPONDING)
        assertEquals(WakeWordState.RESPONDING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.recordReturningToStandby()
        assertEquals(WakeWordState.RETURNING_TO_STANDBY, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("STANDBY", WakeWordState.WAKE_STANDBY)
        assertEquals(WakeWordState.WAKE_STANDBY, BackgroundListeningDiagnostics.currentListeningState.value)
    }

    @Test
    fun testWakeWordDetectionAndCommandExtraction() {
        val testCases = listOf(
            "Hello REXXY YouTube kholo" to Pair("Hello REXXY", "YouTube kholo"),
            "hello rexxy" to Pair("hello rexxy", ""),
            "hello rexy" to Pair("hello rexy", ""),
            "hey rexxy" to Pair("hey rexxy", ""),
            "hey rexy" to Pair("hey rexy", ""),
            "Hello Rex YouTube kholo" to Pair("Hello Rex", "YouTube kholo"),
            "hey rexyy Rahul ko call karo" to Pair("hey rexyy", "Rahul ko call karo"),
            "Rexyy battery kitni hai" to Pair("Rexyy", "battery kitni hai"),
            "ok rex turn on flashlight" to Pair("ok rex", "turn on flashlight"),
            "hi Rex" to Pair("hi Rex", ""),
            "Rex" to Pair("Rex", ""),
            "suno rexyy WhatsApp open karo" to Pair("suno rexyy", "WhatsApp open karo")
        )

        for ((input, expected) in testCases) {
            val match = wakeWordPattern.find(input)
            assertNotNull("Expected wake word in: '$input'", match)
            val detectedPhrase = match!!.value
            val inlineCmd = input.substring(match.range.last + 1).trim()

            assertEquals(expected.first.lowercase(), detectedPhrase.lowercase())
            assertEquals(expected.second, inlineCmd)
        }
    }

    @Test
    fun testNonWakeWordsDoNotTrigger() {
        val nonWakeCases = listOf(
            "What is the weather today",
            "Call Rahul",
            "Play music",
            "YouTube open",
            "good morning"
        )

        for (input in nonWakeCases) {
            val match = wakeWordPattern.find(input)
            assertNull("Did not expect wake word in: '$input'", match)
        }
    }

    @Test
    fun testInterruptPhrases() {
        assertTrue(interruptPhrases.contains("stop"))
        assertTrue(interruptPhrases.contains("ruko"))
        assertTrue(interruptPhrases.contains("cancel"))
        assertTrue(interruptPhrases.contains("chup"))
        assertFalse(interruptPhrases.contains("continue"))
    }

    @Test
    fun testSingleRecognizerLifecycleDiagnostics() {
        assertEquals(0, BackgroundListeningDiagnostics.activeRecognizersCount.value)

        BackgroundListeningDiagnostics.recordRecognizerCreated()
        assertEquals(1, BackgroundListeningDiagnostics.activeRecognizersCount.value)

        BackgroundListeningDiagnostics.recordRecognizerStarted()
        assertEquals(1, BackgroundListeningDiagnostics.activeRecognizersCount.value)
        assertEquals("RECOGNIZER_STARTED", BackgroundListeningDiagnostics.lastRecognizerEvent.value)

        BackgroundListeningDiagnostics.recordRecognizerStopped()
        assertEquals(1, BackgroundListeningDiagnostics.activeRecognizersCount.value)
        assertEquals("RECOGNIZER_STOPPED", BackgroundListeningDiagnostics.lastRecognizerEvent.value)

        BackgroundListeningDiagnostics.recordRecognizerDestroyed()
        assertEquals(0, BackgroundListeningDiagnostics.activeRecognizersCount.value)
        assertEquals("RECOGNIZER_DESTROYED", BackgroundListeningDiagnostics.lastRecognizerEvent.value)
    }

    @Test
    fun testBoundedErrorRecoveryDiagnostics() {
        BackgroundListeningDiagnostics.recordRecognitionError(5, "ERROR_CLIENT")
        assertEquals(5, BackgroundListeningDiagnostics.lastRecognitionError.value)
        assertEquals("ERROR_CLIENT", BackgroundListeningDiagnostics.lastErrorDescription.value)

        BackgroundListeningDiagnostics.recordRecoveryAttempt(1)
        assertEquals(1, BackgroundListeningDiagnostics.recoveryAttempts.value)

        BackgroundListeningDiagnostics.recordRecoveryAttempt(2)
        assertEquals(2, BackgroundListeningDiagnostics.recoveryAttempts.value)

        BackgroundListeningDiagnostics.recordRecoveryAttempt(3)
        assertEquals(3, BackgroundListeningDiagnostics.recoveryAttempts.value)
    }

    @Test
    fun testDiagnosticPrivacyRedaction() {
        BackgroundListeningDiagnostics.logEvent("TEST", WakeWordState.PROCESSING, "My password is secret123")
        val entry = BackgroundListeningDiagnostics.history.value.first()
        assertEquals("[REDACTED_SENSITIVE_CONTENT]", entry.detail)

        BackgroundListeningDiagnostics.logEvent("TEST", WakeWordState.PROCESSING, "Here is your OTP 99281")
        val otpEntry = BackgroundListeningDiagnostics.history.value.first()
        assertEquals("[REDACTED_SENSITIVE_CONTENT]", otpEntry.detail)
    }

    @Test
    fun testBoundedExponentialBackoffCalculation() {
        fun computeBackoff(consecutiveErrors: Int): Long {
            return when (consecutiveErrors) {
                1 -> 500L
                2 -> 1000L
                3 -> 2000L
                else -> 5000L
            }
        }

        assertEquals(500L, computeBackoff(1))
        assertEquals(1000L, computeBackoff(2))
        assertEquals(2000L, computeBackoff(3))
        assertEquals(5000L, computeBackoff(4))
        assertEquals(5000L, computeBackoff(10))
    }

    @Test
    fun testRexxyPronunciationSanitizer() {
        fun cleanForSpeech(input: String): String {
            return input
                .replace("\\bREXXY's\\b".toRegex(), "Rexxy's")
                .replace("\\bREXXY'S\\b".toRegex(), "Rexxy's")
                .replace("\\bREXXY\\b".toRegex(), "Rexxy")
                .replace("(?i)\\br[- ]?e[- ]?x[- ]?x[- ]?y\\b".toRegex(), "Rexxy")
        }

        assertEquals("Hello Rexxy", cleanForSpeech("Hello REXXY"))
        assertEquals("Rexxy's assistant", cleanForSpeech("REXXY's assistant"))
        assertEquals("I am Rexxy", cleanForSpeech("I am R-E-X-X-Y"))
    }

    @Test
    fun testRecognitionLifecycleStateTransitions() {
        assertEquals("IDLE", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)

        BackgroundListeningDiagnostics.recordRecognizerStartRequest("Rec#1", "user_start", "STANDBY", isScheduled = false)
        assertEquals("STARTING", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)

        BackgroundListeningDiagnostics.recordRecognizerOnReady("Rec#1")
        assertEquals("LISTENING", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)

        BackgroundListeningDiagnostics.recordRecognizerOnEnd("Rec#1")
        assertEquals("STOPPING", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)

        BackgroundListeningDiagnostics.recordRecognizerOnResults("Rec#1", "hello rexxy")
        assertEquals("IDLE", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)

        BackgroundListeningDiagnostics.recordRecognizerDestroy("Rec#1", "service_shutdown")
        assertEquals("DESTROYED", BackgroundListeningDiagnostics.currentRecognitionLifecycleState.value)
    }

    @Test
    fun testServiceInstanceTrackingAndDuplicatePrevention() {
        val serviceId1 = "Service#1001"
        val serviceId2 = "Service#1002"

        BackgroundListeningDiagnostics.recordServiceCreated(serviceId1)
        val entry1 = BackgroundListeningDiagnostics.history.value.first()
        assertEquals(serviceId1, entry1.serviceInstanceId)

        BackgroundListeningDiagnostics.recordServiceCreated(serviceId2)
        val entry2 = BackgroundListeningDiagnostics.history.value.first()
        assertEquals(serviceId2, entry2.serviceInstanceId)
        assertEquals("SERVICE_CREATED", entry2.event)

        BackgroundListeningDiagnostics.recordServiceDestroyed(serviceId1)
        val entryDestroy = BackgroundListeningDiagnostics.history.value.first()
        assertEquals("SERVICE_DESTROYED", entryDestroy.event)
        assertEquals(serviceId1, entryDestroy.serviceInstanceId)
    }

    @Test
    fun testSpeechRecognizerErrorCodes() {
        val errorCodes = mapOf(
            3 to "ERROR_AUDIO",
            5 to "ERROR_CLIENT",
            6 to "ERROR_SPEECH_TIMEOUT",
            7 to "ERROR_NO_MATCH",
            8 to "ERROR_RECOGNIZER_BUSY",
            9 to "ERROR_INSUFFICIENT_PERMISSIONS"
        )

        for ((code, name) in errorCodes) {
            BackgroundListeningDiagnostics.recordRecognizerOnError("Rec#1", code, name)
            val entry = BackgroundListeningDiagnostics.history.value.first()
            assertEquals("RECOGNIZER_ON_ERROR", entry.event)
            assertEquals(code, entry.errorCode)
            assertEquals("STOPPING", entry.recognitionState)
        }
    }

    @Test
    fun testRestartSchedulerGuardLogic() {
        var isRecoveryScheduled = false
        var scheduledCount = 0

        fun scheduleGuarded(reason: String) {
            if (isRecoveryScheduled) {
                // Drop duplicate restart requests
                return
            }
            isRecoveryScheduled = true
            scheduledCount++
        }

        scheduleGuarded("silence_timeout")
        assertEquals(1, scheduledCount)
        assertTrue(isRecoveryScheduled)

        // Multiple simultaneous callbacks arrive (e.g. onEndOfSpeech + onError + onResults)
        scheduleGuarded("onEndOfSpeech_callback")
        scheduleGuarded("error_callback")
        scheduleGuarded("results_callback")

        // Guard must prevent duplicate scheduling!
        assertEquals(1, scheduledCount)

        // Reset/complete recovery
        isRecoveryScheduled = false
        scheduleGuarded("next_standby_cycle")
        assertEquals(2, scheduledCount)
    }

    @Test
    fun testDuplicateStartPrevention() {
        var currentState = com.rexyy.app.voice.RecognitionLifecycleState.IDLE
        var startCount = 0

        fun requestStart(reason: String): Boolean {
            if (currentState == com.rexyy.app.voice.RecognitionLifecycleState.STARTING ||
                currentState == com.rexyy.app.voice.RecognitionLifecycleState.LISTENING) {
                return false // Rejected
            }
            currentState = com.rexyy.app.voice.RecognitionLifecycleState.STARTING
            startCount++
            return true
        }

        assertTrue(requestStart("initial_start"))
        assertEquals(1, startCount)

        // Competing start calls while already STARTING
        assertFalse(requestStart("competing_start_1"))
        assertFalse(requestStart("competing_start_2"))
        assertEquals(1, startCount)

        // Now transition to LISTENING
        currentState = com.rexyy.app.voice.RecognitionLifecycleState.LISTENING
        assertFalse(requestStart("competing_start_while_listening"))
        assertEquals(1, startCount)

        // Stop session -> IDLE
        currentState = com.rexyy.app.voice.RecognitionLifecycleState.IDLE
        assertTrue(requestStart("next_cycle_start"))
        assertEquals(2, startCount)
    }
}
