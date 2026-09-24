package com.rexyy.app.router

import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.voice.VoiceCommand

/**
 * Structured local intent representation with strongly-typed properties,
 * intent names, and structured entity maps.
 */
sealed class LocalIntent(
    open val rawCommand: String,
    open val intentName: String,
    open val entities: Map<String, Any?>
) {
    data class OpenApp(
        val appName: String,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "OpenApp", mapOf("app" to appName))

    data class CloseApp(
        val target: String = "home", // "home", "background", "exit"
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "CloseApp", mapOf("target" to target))

    data class GetBattery(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "GetBattery", emptyMap())

    data class GetDate(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "GetDate", emptyMap())

    data class GetTime(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "GetTime", emptyMap())

    data class ToggleFlashlight(
        val turnOn: Boolean,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "ToggleFlashlight", mapOf("turnOn" to turnOn))

    data class SetBrightness(
        val percent: Int? = null,
        val raise: Boolean? = null,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "SetBrightness", mapOf("percent" to percent, "raise" to raise))

    data class AdjustVolume(
        val percent: Int? = null,
        val raise: Boolean? = null,
        val mute: Boolean = false,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "AdjustVolume", mapOf("percent" to percent, "raise" to raise, "mute" to mute))

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val message: String = "REXYY Alarm",
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "SetAlarm", mapOf("time" to String.format("%02d:%02d", hour, minute), "message" to message))

    data class CancelAlarm(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "CancelAlarm", emptyMap())

    data class SetTimer(
        val seconds: Int,
        val message: String = "REXYY Timer",
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "SetTimer", mapOf("seconds" to seconds, "message" to message))

    data class CancelTimer(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "CancelTimer", emptyMap())

    data class OpenSettings(
        val subSettings: String = "",
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "OpenSettings", mapOf("subSettings" to subSettings))

    data class WifiControl(
        val queryState: Boolean = false,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "WifiControl", mapOf("queryState" to queryState))

    data class BluetoothControl(
        val turnOn: Boolean? = null,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "BluetoothControl", mapOf("turnOn" to turnOn))

    data class OpenCamera(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "OpenCamera", emptyMap())

    data class OpenContacts(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "OpenContacts", emptyMap())

    data class FindContact(
        val contactName: String,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "FindContact", mapOf("contactName" to contactName))

    data class GetDeviceStatus(
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "GetDeviceStatus", emptyMap())

    // Conversational & System
    data class Stop(override val rawCommand: String) : LocalIntent(rawCommand, "Stop", emptyMap())
    data class WhatCanYouDo(override val rawCommand: String) : LocalIntent(rawCommand, "WhatCanYouDo", emptyMap())
    data class AreYouThere(override val rawCommand: String) : LocalIntent(rawCommand, "AreYouThere", emptyMap())
    data class WakeWord(override val rawCommand: String) : LocalIntent(rawCommand, "WakeWord", emptyMap())
    data class RepeatLast(override val rawCommand: String) : LocalIntent(rawCommand, "RepeatLast", emptyMap())

    // Telephony & Messaging
    data class CallContact(val target: String, override val rawCommand: String) : LocalIntent(rawCommand, "CallContact", mapOf("target" to target))
    data class WhatsAppMessage(val target: String, val body: String, override val rawCommand: String) : LocalIntent(rawCommand, "WhatsAppMessage", mapOf("target" to target, "body" to body))
    data class WhatsAppChat(val target: String, override val rawCommand: String) : LocalIntent(rawCommand, "WhatsAppChat", mapOf("target" to target))
    data class OpenWhatsApp(override val rawCommand: String) : LocalIntent(rawCommand, "OpenWhatsApp", emptyMap())
    data class SendSms(val target: String, val body: String, override val rawCommand: String) : LocalIntent(rawCommand, "SendSms", mapOf("target" to target, "body" to body))
    data class CheckCaller(override val rawCommand: String) : LocalIntent(rawCommand, "CheckCaller", emptyMap())
    data class AnswerCall(override val rawCommand: String) : LocalIntent(rawCommand, "AnswerCall", emptyMap())
    data class RejectCall(override val rawCommand: String) : LocalIntent(rawCommand, "RejectCall", emptyMap())

    // App & Web Search
    data class AppSearch(val targetApp: String, val query: String, val searchType: String = "general", override val rawCommand: String) : LocalIntent(rawCommand, "AppSearch", mapOf("targetApp" to targetApp, "query" to query))
    data class WebSearch(val query: String, override val rawCommand: String) : LocalIntent(rawCommand, "WebSearch", mapOf("query" to query))

    // Reminders & Calendar
    data class OpenCalendar(override val rawCommand: String) : LocalIntent(rawCommand, "OpenCalendar", emptyMap())
    data class SetReminder(val title: String, override val rawCommand: String) : LocalIntent(rawCommand, "SetReminder", mapOf("title" to title))

    // Accessibility Actions
    data class AccessibilityAction(
        val actionType: VoiceCommand.AccessibilityAction.ActionType,
        val argument: String = "",
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "AccessibilityAction", mapOf("actionType" to actionType.name, "argument" to argument))

    // Multi-Step Task
    data class MultiStepTask(
        val description: String,
        val steps: List<LocalIntent>,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "MultiStepTask", mapOf("description" to description, "stepCount" to steps.size))

    // Generative AI Fallback
    data class FallbackToAi(
        val prompt: String,
        val providerOverride: AiProviderType? = null,
        override val rawCommand: String
    ) : LocalIntent(rawCommand, "FallbackToAi", mapOf("prompt" to prompt, "provider" to providerOverride?.name))

    fun toVoiceCommand(): VoiceCommand {
        return when (this) {
            is OpenApp -> VoiceCommand.OpenApp(appName = appName, rawInput = rawCommand)
            is CloseApp -> VoiceCommand.CloseApp(target = target, rawInput = rawCommand)
            is GetBattery -> VoiceCommand.GetBattery(rawInput = rawCommand)
            is GetDate -> VoiceCommand.GetDate(rawInput = rawCommand)
            is GetTime -> VoiceCommand.GetTime(rawInput = rawCommand)
            is ToggleFlashlight -> VoiceCommand.ToggleFlashlight(turnOn = turnOn, rawInput = rawCommand)
            is SetBrightness -> VoiceCommand.AdjustBrightness(raise = raise, percent = percent, rawInput = rawCommand)
            is AdjustVolume -> VoiceCommand.AdjustVolume(raise = raise ?: true, percent = percent, mute = mute, rawInput = rawCommand)
            is SetAlarm -> VoiceCommand.SetAlarm(hour = hour, minute = minute, message = message, rawInput = rawCommand)
            is CancelAlarm -> VoiceCommand.CancelAlarm(rawInput = rawCommand)
            is SetTimer -> VoiceCommand.SetTimer(seconds = seconds, message = message, rawInput = rawCommand)
            is CancelTimer -> VoiceCommand.CancelTimer(rawInput = rawCommand)
            is OpenSettings -> VoiceCommand.OpenSettings(subSettings = subSettings, rawInput = rawCommand)
            is WifiControl -> VoiceCommand.WifiSettings(rawInput = rawCommand)
            is BluetoothControl -> VoiceCommand.BluetoothSettings(turnOn = turnOn ?: true, rawInput = rawCommand)
            is OpenCamera -> VoiceCommand.OpenCamera(rawInput = rawCommand)
            is OpenContacts -> VoiceCommand.OpenContacts(rawInput = rawCommand)
            is FindContact -> VoiceCommand.FindContact(contactName = contactName, rawInput = rawCommand)
            is GetDeviceStatus -> VoiceCommand.GetDeviceInfo(rawInput = rawCommand)
            is Stop -> VoiceCommand.Stop(rawInput = rawCommand)
            is WhatCanYouDo -> VoiceCommand.WhatCanYouDo(rawInput = rawCommand)
            is AreYouThere -> VoiceCommand.AreYouThere(rawInput = rawCommand)
            is WakeWord -> VoiceCommand.WakeWord(rawInput = rawCommand)
            is RepeatLast -> VoiceCommand.RepeatLast(rawInput = rawCommand)
            is CallContact -> VoiceCommand.CallContact(target = target, rawInput = rawCommand)
            is WhatsAppMessage -> VoiceCommand.WhatsAppMessage(target = target, body = body, rawInput = rawCommand)
            is WhatsAppChat -> VoiceCommand.WhatsAppChat(target = target, rawInput = rawCommand)
            is OpenWhatsApp -> VoiceCommand.OpenWhatsApp(rawInput = rawCommand)
            is SendSms -> VoiceCommand.SendMessage(target = target, body = body, rawInput = rawCommand)
            is CheckCaller -> VoiceCommand.CheckCaller(rawInput = rawCommand)
            is AnswerCall -> VoiceCommand.AnswerCall(rawInput = rawCommand)
            is RejectCall -> VoiceCommand.RejectCall(rawInput = rawCommand)
            is AppSearch -> VoiceCommand.AppSearch(targetApp = targetApp, query = query, searchType = searchType, rawInput = rawCommand)
            is WebSearch -> VoiceCommand.GoogleSearch(query = query, rawInput = rawCommand)
            is OpenCalendar -> VoiceCommand.OpenCalendar(rawInput = rawCommand)
            is SetReminder -> VoiceCommand.SetReminder(title = title, rawInput = rawCommand)
            is AccessibilityAction -> VoiceCommand.AccessibilityAction(actionType = actionType, argument = argument, rawInput = rawCommand)
            is MultiStepTask -> VoiceCommand.MultiStepTask(steps = steps.map { it.toVoiceCommand() }, description = description, rawInput = rawCommand)
            is FallbackToAi -> VoiceCommand.AiChat(prompt = prompt, providerOverride = providerOverride)
        }
    }
}
