package com.rexyy.app

import com.rexyy.app.nlp.PythonNlpBridge
import com.rexyy.app.nlp.PythonNlpValidator
import com.rexyy.app.nlp.contract.AiCommandRequest
import com.rexyy.app.nlp.contract.AiCommandResult
import com.rexyy.app.ui.bridge.RexyyBackgroundStateProvider
import com.rexyy.app.ui.bridge.RexyyCommandController
import com.rexyy.app.ui.bridge.RexyyCoreController
import com.rexyy.app.ui.bridge.RexyyPermissionController
import com.rexyy.app.ui.bridge.RexyyUiStateListener
import com.rexyy.app.ui.bridge.RexyyUiStateProvider
import com.rexyy.app.ui.java.RexyyUiCoordinator
import com.rexyy.app.ui.java.RexyyUiPresentationModel
import com.rexyy.app.voice.VoiceCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying the Multi-Language Architecture:
 * 1. Java UI layer (RexyyUiCoordinator, RexyyUiPresentationModel, Java bridge interfaces)
 * 2. Kotlin Core authority & controller implementation
 * 3. Python AI/NLP layer contracts, bridge serialization, and validation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiLanguageArchitectureTest {

    // --- 1. Java UI Layer & Presentation Model Tests ---

    @Test
    fun testJavaPresentationModel_FromDifferentStates() {
        val listening = RexyyUiPresentationModel.fromState("LISTENING", "", "", true)
        assertEquals("Listening...", listening.title)
        assertEquals("LISTENING", listening.statusBadgeText)
        assertTrue(listening.isWorking)

        val processing = RexyyUiPresentationModel.fromState("PROCESSING", "Instagram kholo", "", true)
        assertEquals("Processing", processing.title)
        assertEquals("Instagram kholo", processing.subtitle)
        assertEquals("WORKING", processing.statusBadgeText)
        assertTrue(processing.isWorking)

        val speaking = RexyyUiPresentationModel.fromState("SPEAKING", "", "Opening Instagram", true)
        assertEquals("Speaking", speaking.title)
        assertEquals("Opening Instagram", speaking.subtitle)
        assertEquals("TRANSMITTING", speaking.statusBadgeText)
        assertTrue(speaking.isWorking)

        val error = RexyyUiPresentationModel.fromState("ERROR", "", "Network timed out", true)
        assertEquals("Attention Required", error.title)
        assertEquals("ERROR", error.statusBadgeText)
        assertFalse(error.isWorking)

        val idleArmed = RexyyUiPresentationModel.fromState("WAKE_STANDBY", "", "", true)
        assertEquals("ARMED", idleArmed.statusBadgeText)

        val idleStopped = RexyyUiPresentationModel.fromState("WAKE_STANDBY", "", "", false)
        assertEquals("STANDBY", idleStopped.statusBadgeText)
    }

    @Test
    fun testJavaUiCoordinator_LifecycleAndState() {
        var assistantStarted = false
        var overlayRequested = false
        var overlayDismissed = false

        val mockController = object : RexyyCoreController {
            override fun startAssistant(context: android.content.Context): Boolean {
                assistantStarted = true
                return true
            }
            override fun stopAssistant(context: android.content.Context) {}
            override fun isAssistantRunning(): Boolean = assistantStarted
            override fun executeCommand(command: String, isVoice: Boolean) {}
            override fun activateRexyy() {}
            override fun requestOverlay(context: android.content.Context) {
                overlayRequested = true
            }
            override fun dismissOverlay() {
                overlayDismissed = true
            }
            override fun getUiStateProvider(): RexyyUiStateProvider = object : RexyyUiStateProvider {
                override fun getAssistantStateName(): String = "IDLE"
                override fun getLastSpokenCommand(): String = ""
                override fun getLastExecutionFeedback(): String = ""
                override fun isRexyyActivated(): Boolean = true
                override fun addStateListener(listener: RexyyUiStateListener?) {}
                override fun removeStateListener(listener: RexyyUiStateListener?) {}
            }
            override fun getCommandController(): RexyyCommandController = object : RexyyCommandController {
                override fun dispatchCommand(commandText: String?) {}
                override fun dispatchVoiceToggle() {}
                override fun cancelActiveTask() {}
                override fun confirmPendingAction() {}
                override fun cancelPendingAction() {}
            }
            override fun getPermissionController(): RexyyPermissionController = object : RexyyPermissionController {
                override fun hasRecordAudioPermission(context: android.content.Context?): Boolean = true
                override fun hasOverlayPermission(context: android.content.Context?): Boolean = true
                override fun hasAccessibilityPermission(context: android.content.Context?): Boolean = true
                override fun hasNotificationAccess(context: android.content.Context?): Boolean = true
                override fun openOverlaySettings(context: android.content.Context?) {}
                override fun openAccessibilitySettings(context: android.content.Context?) {}
                override fun openNotificationSettings(context: android.content.Context?) {}
            }
            override fun getBackgroundStateProvider(): RexyyBackgroundStateProvider = object : RexyyBackgroundStateProvider {
                override fun isServiceRunning(): Boolean = assistantStarted
                override fun getCurrentListeningState(): String = "WAKE_STANDBY"
                override fun getActiveRecognizersCount(): Int = 1
                override fun getRecoveryAttempts(): Int = 0
                override fun getLastErrorDescription(): String? = null
            }
        }

        val coordinator = RexyyUiCoordinator(mockController)
        assertNotNull(coordinator.coreController)

        val model = coordinator.presentationModel
        assertNotNull(model)
        assertEquals("REXXY Ready", model.title)
    }

    // --- 2. Python AI/NLP Contract & Bridge Tests ---

    @Test
    fun testPythonNlpBridge_SerializationAndDeserialization() {
        val bridge = PythonNlpBridge()

        val request = AiCommandRequest(
            requestId = "test-req-123",
            rawInput = "Instagram kholo",
            language = "hi",
            contextMetadata = mapOf("device" to "mobile"),
            version = 1
        )

        val jsonStr = bridge.serializeRequest(request)
        assertTrue(jsonStr.contains("test-req-123"))
        assertTrue(jsonStr.contains("Instagram kholo"))
        assertTrue(jsonStr.contains("\"version\":1"))

        val mockResultJson = """
            {
              "requestId": "test-req-123",
              "intent": "OPEN_APP",
              "app": "Instagram",
              "query": null,
              "entities": {"app": "Instagram"},
              "confidence": 0.98,
              "requiresConfirmation": false,
              "error": null,
              "version": 1
            }
        """.trimIndent()

        val result = bridge.deserializeResult(mockResultJson, "test-req-123")
        assertEquals("test-req-123", result.requestId)
        assertEquals("OPEN_APP", result.intent)
        assertEquals("Instagram", result.app)
        assertNull(result.query)
        assertEquals(0.98, result.confidence, 0.001)
        assertFalse(result.requiresConfirmation)
        assertTrue(result.isSuccess())
    }

    @Test
    fun testPythonNlpBridge_InterpretationOfAppSearch() = runBlocking {
        val bridge = PythonNlpBridge()

        val request = AiCommandRequest(
            rawInput = "YouTube kholo aur Virat Kohli search karo"
        )

        val result = bridge.interpret(request)
        assertTrue(result.isSuccess())
        assertEquals("SEARCH_APP", result.intent)
        assertEquals("YouTube", result.app)
        assertNotNull(result.query)
        assertTrue(result.query!!.contains("Virat Kohli", ignoreCase = true))
    }

    @Test
    fun testPythonNlpBridge_InterpretationOfTelemetry() = runBlocking {
        val bridge = PythonNlpBridge()

        val request = AiCommandRequest(
            rawInput = "battery kitni hai"
        )

        val result = bridge.interpret(request)
        assertTrue(result.isSuccess())
        assertEquals("BATTERY", result.intent)
    }

    // --- 3. Python Security & Kotlin Authority Validation Tests ---

    @Test
    fun testPythonNlpValidator_ValidatesLegitimateCommand() {
        val validResult = AiCommandResult(
            requestId = "r-1",
            intent = "OPEN_APP",
            app = "WhatsApp",
            confidence = 0.95
        )

        assertTrue(PythonNlpValidator.validate(validResult))

        val voiceCommand = PythonNlpValidator.toVoiceCommand(validResult, "WhatsApp kholo")
        assertTrue(voiceCommand is VoiceCommand.OpenApp)
        assertEquals("WhatsApp", (voiceCommand as VoiceCommand.OpenApp).appName)
    }

    @Test
    fun testPythonNlpValidator_RejectsMaliciousPayloads() {
        val maliciousResult = AiCommandResult(
            requestId = "r-bad",
            intent = "OPEN_APP",
            app = "Settings; rm -rf /",
            confidence = 0.99
        )

        assertFalse(PythonNlpValidator.validate(maliciousResult))

        // When validator rejects, it securely falls back to safe conversational AI without executing shell
        val fallback = PythonNlpValidator.toVoiceCommand(maliciousResult, "Settings; rm -rf /")
        assertFalse(fallback is VoiceCommand.OpenApp)
    }

    @Test
    fun testPythonNlpValidator_RejectsLowConfidence() {
        val lowConfResult = AiCommandResult(
            requestId = "r-low",
            intent = "OPEN_APP",
            app = "Instagram",
            confidence = 0.40 // Below 0.65 threshold
        )

        assertFalse(PythonNlpValidator.validate(lowConfResult))
    }
}
