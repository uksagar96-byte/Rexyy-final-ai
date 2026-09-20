package com.rexyy.app.router

import com.rexyy.app.voice.VoiceCommand

data class ParsedAiResponse(
    val displayText: String,
    val actionToExecute: VoiceCommand? = null
)

object AiActionTagParser {

    private val actionRegex = Regex("\\[ACTION:\\s*([A-Z_]+)\\((.*?)\\)\\]")

    fun parse(rawResponse: String): ParsedAiResponse {
        val match = actionRegex.find(rawResponse) ?: return ParsedAiResponse(rawResponse.trim(), null)

        val actionType = match.groupValues[1].trim()
        val argsStr = match.groupValues[2].trim()

        val cleanText = rawResponse.replace(match.value, "").trim()

        val action = when (actionType) {
            "OPEN_APP" -> {
                val appName = cleanArg(argsStr)
                if (appName.isNotBlank()) VoiceCommand.OpenApp(appName, rawResponse) else null
            }
            "WHATSAPP" -> {
                val parts = splitArgs(argsStr)
                val target = cleanArg(parts.getOrElse(0) { "Contact" })
                val body = cleanArg(parts.getOrElse(1) { "" })
                VoiceCommand.WhatsAppMessage(target = target, body = body, rawInput = rawResponse)
            }
            "CALL" -> {
                val target = cleanArg(argsStr)
                VoiceCommand.CallContact(target = target, rawInput = rawResponse)
            }
            "ALARM" -> {
                val parts = splitArgs(argsStr)
                val h = cleanArg(parts.getOrElse(0) { "7" }).toIntOrNull() ?: 7
                val m = cleanArg(parts.getOrElse(1) { "0" }).toIntOrNull() ?: 0
                VoiceCommand.SetAlarm(hour = h, minute = m, message = "REXYY Alarm", rawInput = rawResponse)
            }
            "TIMER" -> {
                val secs = cleanArg(argsStr).toIntOrNull() ?: 300
                VoiceCommand.SetTimer(seconds = secs, message = "REXYY Timer", rawInput = rawResponse)
            }
            "CAMERA" -> VoiceCommand.OpenCamera(rawInput = rawResponse)
            "SEARCH" -> {
                val query = cleanArg(argsStr)
                VoiceCommand.GoogleSearch(query = query, rawInput = rawResponse)
            }
            else -> null
        }

        return ParsedAiResponse(
            displayText = cleanText.ifBlank { "Executing requested command..." },
            actionToExecute = action
        )
    }

    private fun cleanArg(arg: String): String {
        return arg.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }

    private fun splitArgs(args: String): List<String> {
        return args.split(",").map { it.trim() }
    }
}
