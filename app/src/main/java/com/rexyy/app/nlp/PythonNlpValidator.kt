package com.rexyy.app.nlp

import com.rexyy.app.nlp.contract.AiCommandResult
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.voice.VoiceCommand

/**
 * Security and validation authority in Kotlin.
 * Validates Python AI/NLP output before any action is executed on Android.
 * Prevents unauthorized intent execution, parameter injection, and unverified sensitive actions.
 */
object PythonNlpValidator {

    private const val MIN_CONFIDENCE = 0.65

    private val ALLOWED_INTENTS = setOf(
        "OPEN_APP",
        "SEARCH_APP",
        "CLOSE_APP",
        "CALL",
        "SMS",
        "WHATSAPP",
        "FLASHLIGHT",
        "BATTERY",
        "TIME",
        "DATE",
        "VOLUME",
        "BRIGHTNESS",
        "ALARM",
        "CONVERSATIONAL",
        "FALLBACK_AI"
    )

    /**
     * Validates an AiCommandResult from the Python/AI layer.
     *
     * @return True if the result is secure, valid, and meets confidence standards.
     */
    fun validate(result: AiCommandResult): Boolean {
        if (!result.isSuccess()) return false
        if (result.confidence < MIN_CONFIDENCE) return false
        if (!ALLOWED_INTENTS.contains(result.intent.uppercase())) return false

        // Check for disallowed shell or injection characters in query/app/entities
        if (isMaliciousInput(result.app) || isMaliciousInput(result.query)) {
            return false
        }
        for ((_, value) in result.entities) {
            if (isMaliciousInput(value)) return false
        }

        return true
    }

    /**
     * Sanitizes strings to prevent parameter or intent injection.
     */
    private fun isMaliciousInput(text: String?): Boolean {
        if (text == null) return false
        val dangerousPatterns = listOf(";", "&&", "||", "`", "$(", "rm -rf", "../", "\\x")
        return dangerousPatterns.any { text.contains(it) }
    }

    /**
     * Converts a validated AiCommandResult into an authoritative VoiceCommand.
     * If validation fails, safely routes to conversational AI or error.
     */
    fun toVoiceCommand(result: AiCommandResult, fallbackRaw: String): VoiceCommand {
        if (!validate(result)) {
            // Validation failed or low confidence -> fallback to conversational AI router
            return RexyyCommandRouter.route(fallbackRaw)
        }

        return when (result.intent.uppercase()) {
            "OPEN_APP" -> {
                val app = result.app ?: result.entities["app"] ?: ""
                if (app.isNotBlank()) VoiceCommand.OpenApp(appName = app, rawInput = fallbackRaw) else RexyyCommandRouter.route(fallbackRaw)
            }
            "SEARCH_APP" -> {
                val app = result.app ?: result.entities["app"] ?: "YouTube"
                val q = result.query ?: result.entities["query"] ?: ""
                VoiceCommand.AppSearch(targetApp = app, query = q, rawInput = fallbackRaw)
            }
            "CLOSE_APP" -> VoiceCommand.CloseApp(target = result.entities["target"] ?: "home", rawInput = fallbackRaw)
            "BATTERY" -> VoiceCommand.GetBattery(rawInput = fallbackRaw)
            "TIME" -> VoiceCommand.GetTime(rawInput = fallbackRaw)
            "DATE" -> VoiceCommand.GetDate(rawInput = fallbackRaw)
            "FLASHLIGHT" -> {
                val state = result.entities["state"]?.lowercase()
                VoiceCommand.ToggleFlashlight(turnOn = state != "off" && state != "band", rawInput = fallbackRaw)
            }
            "CONVERSATIONAL", "FALLBACK_AI" -> {
                VoiceCommand.AiChat(prompt = fallbackRaw)
            }
            else -> RexyyCommandRouter.route(fallbackRaw)
        }
    }
}
