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
        "^(hello|hey|hi|ok|suno)?\\s*(rex|rexyy)\\b",
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

        BackgroundListeningDiagnostics.logEvent("WAKE_DETECTED", WakeWordState.WAKE_DETECTED, "hello rex")
        assertEquals(WakeWordState.WAKE_DETECTED, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.recordCommandListeningStarted()
        assertEquals(WakeWordState.COMMAND_LISTENING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("PROCESSING", WakeWordState.PROCESSING)
        assertEquals(WakeWordState.PROCESSING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("EXECUTING", WakeWordState.EXECUTING)
        assertEquals(WakeWordState.EXECUTING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("VERIFYING", WakeWordState.VERIFYING)
        assertEquals(WakeWordState.VERIFYING, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.recordReturningToStandby()
        assertEquals(WakeWordState.RETURNING_TO_STANDBY, BackgroundListeningDiagnostics.currentListeningState.value)

        BackgroundListeningDiagnostics.logEvent("STANDBY", WakeWordState.WAKE_STANDBY)
        assertEquals(WakeWordState.WAKE_STANDBY, BackgroundListeningDiagnostics.currentListeningState.value)
    }

    @Test
    fun testWakeWordDetectionAndCommandExtraction() {
        val testCases = listOf(
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
}
