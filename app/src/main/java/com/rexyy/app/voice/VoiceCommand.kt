package com.rexyy.app.voice

import com.rexyy.app.network.provider.AiProviderType

sealed class VoiceCommand {
    data class OpenApp(val appName: String, val rawInput: String) : VoiceCommand()
    data class CloseApp(val target: String = "home", val rawInput: String) : VoiceCommand()
    data class AppSearch(val targetApp: String, val query: String, val searchType: String = "general", val rawInput: String) : VoiceCommand()
    data class GoogleSearch(val query: String, val rawInput: String) : VoiceCommand()
    data class GetBattery(val rawInput: String) : VoiceCommand()
    data class GetDate(val rawInput: String) : VoiceCommand()
    data class GetTime(val rawInput: String) : VoiceCommand()
    data class ToggleFlashlight(val turnOn: Boolean? = null, val rawInput: String) : VoiceCommand()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String, val rawInput: String) : VoiceCommand()
    data class SetTimer(val seconds: Int, val message: String = "REXYY Timer", val rawInput: String) : VoiceCommand()
    data class OpenSettings(val subSettings: String = "", val rawInput: String) : VoiceCommand()
    data class BluetoothSettings(val turnOn: Boolean, val rawInput: String) : VoiceCommand()
    data class WifiSettings(val rawInput: String) : VoiceCommand()
    data class AdjustVolume(val raise: Boolean = true, val percent: Int? = null, val mute: Boolean = false, val rawInput: String) : VoiceCommand()
    data class AdjustBrightness(val raise: Boolean? = null, val percent: Int? = null, val rawInput: String) : VoiceCommand()
    data class CallContact(val target: String, val confirmedDirectCall: Boolean = false, val rawInput: String) : VoiceCommand()
    data class SendMessage(val target: String, val body: String = "", val confirmedSend: Boolean = false, val rawInput: String) : VoiceCommand()
    data class WhatsAppMessage(val target: String, val body: String = "", val confirmedSend: Boolean = false, val rawInput: String) : VoiceCommand()
    data class WhatsAppChat(val target: String, val rawInput: String) : VoiceCommand()
    data class OpenWhatsApp(val rawInput: String) : VoiceCommand()
    data class OpenCamera(val rawInput: String) : VoiceCommand()
    data class OpenCalendar(val rawInput: String) : VoiceCommand()
    data class OpenContacts(val rawInput: String) : VoiceCommand()
    data class FindContact(val contactName: String, val rawInput: String) : VoiceCommand()
    data class WakeWord(val rawInput: String) : VoiceCommand()
    data class CancelAlarm(val rawInput: String) : VoiceCommand()
    data class CancelTimer(val rawInput: String) : VoiceCommand()
    data class RepeatLast(val rawInput: String) : VoiceCommand()
    data class Stop(val rawInput: String) : VoiceCommand()
    data class WhatCanYouDo(val rawInput: String) : VoiceCommand()
    data class AreYouThere(val rawInput: String) : VoiceCommand()
    data class CheckCaller(val rawInput: String) : VoiceCommand()
    data class AnswerCall(val rawInput: String) : VoiceCommand()
    data class RejectCall(val rawInput: String) : VoiceCommand()
    data class GetDeviceInfo(val rawInput: String) : VoiceCommand()
    data class MultiStepTask(val steps: List<VoiceCommand>, val description: String, val rawInput: String) : VoiceCommand()
    data class AccessibilityAction(val actionType: ActionType, val argument: String = "", val rawInput: String) : VoiceCommand() {
        enum class ActionType {
            SCROLL_DOWN, SCROLL_UP, GO_BACK, GO_HOME, RECENTS, TYPE_TEXT, COPY, PASTE,
            REPLACE_TEXT, CLICK_NODE, SUBMIT_SEARCH
        }
    }
    data class SetReminder(val title: String = "", val rawInput: String) : VoiceCommand()
    data class ReadNotifications(val rawInput: String) : VoiceCommand()
    data class CheckCharging(val rawInput: String) : VoiceCommand()
    data class AiChat(val prompt: String, val providerOverride: AiProviderType? = null) : VoiceCommand()
}
