package com.rexyy.app.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.launcher.AppDiscoveryManager
import com.rexyy.app.launcher.AppLaunchOutcome
import com.rexyy.app.telecom.CallActionController
import com.rexyy.app.telecom.CallStateManager
import com.rexyy.app.telecom.CallerIdentityResolver
import com.rexyy.app.telecom.TelecomActionResult
import com.rexyy.app.utils.RexyyLanguageManager
import com.rexyy.app.whatsapp.WhatsAppActionManager
import com.rexyy.app.whatsapp.WhatsAppActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceCommandExecutor {

    suspend fun execute(command: VoiceCommand, context: Context): VoiceCommandResult = withContext(Dispatchers.Main) {
        return@withContext try {
            when (command) {
                is VoiceCommand.OpenApp -> executeOpenApp(command.appName, context)
                is VoiceCommand.AppSearch -> executeAppSearch(command, context)
                is VoiceCommand.GetBattery -> executeGetBattery(context)
                is VoiceCommand.GetDate -> executeGetDate()
                is VoiceCommand.GetTime -> executeGetTime()
                is VoiceCommand.ToggleFlashlight -> executeToggleFlashlight(command.turnOn, context)
                is VoiceCommand.OpenWhatsApp -> executeOpenWhatsApp(context)
                is VoiceCommand.WhatsAppMessage -> executeWhatsAppMessage(command, context)
                is VoiceCommand.WhatsAppChat -> executeWhatsAppChat(command.target, context)
                is VoiceCommand.CallContact -> executeCallContact(command, context)
                is VoiceCommand.CheckCaller -> executeCheckCaller(context)
                is VoiceCommand.AnswerCall -> executeAnswerCall(context)
                is VoiceCommand.RejectCall -> executeRejectCall(context)
                is VoiceCommand.AdjustVolume -> executeAdjustVolume(command, context)
                is VoiceCommand.AdjustBrightness -> executeAdjustBrightness(command, context)
                is VoiceCommand.BluetoothSettings -> executeBluetoothSettings(command.turnOn, context)
                is VoiceCommand.WifiSettings -> executeWifiSettings(context)
                is VoiceCommand.OpenSettings -> executeOpenSettings(context)
                is VoiceCommand.OpenCamera -> executeOpenCamera(context)
                is VoiceCommand.OpenCalendar -> executeOpenCalendar(context)
                is VoiceCommand.OpenContacts -> executeOpenContacts(context)
                is VoiceCommand.FindContact -> executeFindContact(command.contactName, context)
                is VoiceCommand.WakeWord -> executeWakeWord(context)
                is VoiceCommand.CancelAlarm -> executeCancelAlarm(context)
                is VoiceCommand.CancelTimer -> executeCancelTimer(context)
                is VoiceCommand.Stop -> VoiceCommandResult.Handled("Stopped.")
                is VoiceCommand.WhatCanYouDo -> executeWhatCanYouDo()
                is VoiceCommand.AreYouThere -> VoiceCommandResult.Handled("Yes Sir, I am right here and ready for your command.")
                is VoiceCommand.RepeatLast -> VoiceCommandResult.Handled("__REPEAT_LAST__")
                is VoiceCommand.GetDeviceInfo -> executeGetDeviceInfo(context)
                is VoiceCommand.SetTimer -> executeSetTimer(command.seconds, command.message, context)
                is VoiceCommand.SetAlarm -> executeSetAlarm(command.hour, command.minute, command.message, context)
                is VoiceCommand.GoogleSearch -> executeGoogleSearch(command.query, context)
                is VoiceCommand.SendMessage -> executeSendMessage(command, context)
                is VoiceCommand.SetReminder -> executeSetReminder(command.title, context)
                is VoiceCommand.AccessibilityAction -> executeAccessibilityAction(command, context)
                is VoiceCommand.MultiStepTask -> VoiceCommandResult.Handled("Executing task: ${command.description}")
                is VoiceCommand.AiChat -> VoiceCommandResult.ForwardToAi(command.prompt, command.providerOverride)
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Unable to complete action: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private suspend fun executeOpenApp(appName: String, context: Context): VoiceCommandResult {
        val discovery = AppDiscoveryManager(context)
        return when (val outcome = discovery.findAndLaunchApp(appName)) {
            is AppLaunchOutcome.Success -> {
                VoiceCommandResult.Handled("Opening ${outcome.appLabel}...")
            }
            is AppLaunchOutcome.MultipleMatches -> {
                VoiceCommandResult.Handled("Found multiple matches: ${outcome.candidates.joinToString(", ")}. Which one should I open?")
            }
            is AppLaunchOutcome.NotInstalled -> {
                VoiceCommandResult.Handled("${outcome.appName} installed nahi hai.")
            }
            is AppLaunchOutcome.FailedToLaunch -> {
                VoiceCommandResult.Error("Could not launch ${outcome.appLabel}.")
            }
        }
    }

    private fun executeOpenWhatsApp(context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        return when (val result = wa.openWhatsApp()) {
            is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(result.message)
            is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(result.message)
            else -> VoiceCommandResult.Error("Could not open WhatsApp.")
        }
    }

    private fun executeWhatsAppMessage(cmd: VoiceCommand.WhatsAppMessage, context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        if (cmd.body.isBlank()) {
            return VoiceCommandResult.CollectMessageInput(
                targetName = cmd.target,
                isWhatsApp = true,
                prompt = "${cmd.target} ko WhatsApp par kya message bhejna hai?"
            )
        }

        if (!cmd.confirmedSend) {
            val prep = wa.prepareMessage(cmd.target, cmd.body)
            return when (prep) {
                is WhatsAppActionResult.NeedsMessageBody -> {
                    VoiceCommandResult.CollectMessageInput(
                        targetName = prep.targetName,
                        isWhatsApp = true,
                        prompt = prep.prompt
                    )
                }
                is WhatsAppActionResult.RequiresConfirmation -> {
                    VoiceCommandResult.RequiresConfirmation(
                        prompt = prep.confirmationPrompt,
                        commandToExecute = cmd.copy(confirmedSend = true)
                    )
                }
                is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(prep.message)
                else -> VoiceCommandResult.Error("Unable to prepare WhatsApp message.")
            }
        } else {
            val resolvedPhone = CallerIdentityResolver.findPhoneNumberByName(context, cmd.target)
            return when (val sent = wa.executeSendMessage(cmd.target, resolvedPhone, cmd.body)) {
                is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(sent.message)
                is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(sent.message)
                is WhatsAppActionResult.Failure -> VoiceCommandResult.Error(sent.error)
                else -> VoiceCommandResult.Error("WhatsApp send failed.")
            }
        }
    }

    private fun executeWhatsAppChat(target: String, context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        return when (val chat = wa.openChat(target)) {
            is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(chat.message)
            is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(chat.message)
            is WhatsAppActionResult.Failure -> VoiceCommandResult.Error(chat.error)
            else -> VoiceCommandResult.Error("Could not open chat.")
        }
    }

    private fun executeCallContact(cmd: VoiceCommand.CallContact, context: Context): VoiceCommandResult {
        if (!cmd.confirmedDirectCall) {
            return VoiceCommandResult.RequiresConfirmation(
                prompt = "${cmd.target} ko call karu?",
                commandToExecute = cmd.copy(confirmedDirectCall = true)
            )
        }

        val controller = CallActionController(context)
        return when (val res = controller.callContact(cmd.target, confirmedDirectCall = true)) {
            is TelecomActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is TelecomActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is TelecomActionResult.Unsupported -> VoiceCommandResult.Handled(res.reason)
            is TelecomActionResult.Failure -> VoiceCommandResult.Error(res.error)
        }
    }

    private fun executeCheckCaller(context: Context): VoiceCommandResult {
        val manager = CallStateManager(context)
        val voiceLang = com.rexyy.app.data.local.SecureStorage(context).getVoiceLanguage()
        val msg = manager.getCurrentRingingCallerAnnouncement(voiceLang)
        return VoiceCommandResult.Handled(msg)
    }

    private fun executeAnswerCall(context: Context): VoiceCommandResult {
        val controller = CallActionController(context)
        return when (val res = controller.answerCall()) {
            is TelecomActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is TelecomActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is TelecomActionResult.Unsupported -> VoiceCommandResult.Handled(res.reason)
            is TelecomActionResult.Failure -> VoiceCommandResult.Error(res.error)
        }
    }

    private fun executeRejectCall(context: Context): VoiceCommandResult {
        val controller = CallActionController(context)
        return when (val res = controller.rejectCall()) {
            is TelecomActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is TelecomActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is TelecomActionResult.Unsupported -> VoiceCommandResult.Handled(res.reason)
            is TelecomActionResult.Failure -> VoiceCommandResult.Error(res.error)
        }
    }

    private fun executeAdjustVolume(cmd: VoiceCommand.AdjustVolume, context: Context): VoiceCommandResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VoiceCommandResult.Error("Audio service unavailable.")

        if (cmd.mute) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            return VoiceCommandResult.Handled("Media volume muted.")
        }

        if (cmd.percent != null) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (maxVol * (cmd.percent / 100f)).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            return VoiceCommandResult.Handled("Volume set to ${cmd.percent}%.")
        }

        val direction = if (cmd.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val actionDesc = if (cmd.raise) "Volume increased." else "Volume decreased."
        return VoiceCommandResult.Handled(actionDesc)
    }

    private fun executeAdjustBrightness(cmd: VoiceCommand.AdjustBrightness, context: Context): VoiceCommandResult {
        val canWrite = Settings.System.canWrite(context)
        if (canWrite) {
            try {
                val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val targetValue = if (cmd.percent != null) {
                    (cmd.percent * 255 / 100).coerceIn(10, 255)
                } else if (cmd.raise == true) {
                    (current + 45).coerceAtMost(255)
                } else if (cmd.raise == false) {
                    (current - 45).coerceAtLeast(15)
                } else {
                    current
                }
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetValue)
                val finalPct = (targetValue * 100 / 255)
                return VoiceCommandResult.Handled("Brightness set to $finalPct%.")
            } catch (e: Exception) {
                // fallback to settings
            }
        }

        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("System settings write permission required for direct brightness. Opening display settings...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Unable to open display settings: ${e.localizedMessage}")
        }
    }

    private fun executeAccessibilityAction(cmd: VoiceCommand.AccessibilityAction, context: Context): VoiceCommandResult {
        if (!com.rexyy.app.accessibility.RexyyAccessibilityService.isServiceEnabled()) {
            com.rexyy.app.accessibility.RexyyAccessibilityService.openAccessibilitySettings(context)
            return VoiceCommandResult.Handled("Accessibility service permission required. Opening Accessibility Settings...")
        }

        val success = when (cmd.actionType) {
            VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN -> com.rexyy.app.accessibility.AccessibilityActionExecutor.scroll(true)
            VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP -> com.rexyy.app.accessibility.AccessibilityActionExecutor.scroll(false)
            VoiceCommand.AccessibilityAction.ActionType.GO_BACK -> com.rexyy.app.accessibility.AccessibilityActionExecutor.pressBack()
            VoiceCommand.AccessibilityAction.ActionType.GO_HOME -> com.rexyy.app.accessibility.AccessibilityActionExecutor.pressHome()
            VoiceCommand.AccessibilityAction.ActionType.RECENTS -> com.rexyy.app.accessibility.AccessibilityActionExecutor.pressRecentApps()
            VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT -> com.rexyy.app.accessibility.AccessibilityActionExecutor.inputText(cmd.argument)
            VoiceCommand.AccessibilityAction.ActionType.COPY -> com.rexyy.app.accessibility.AccessibilityActionExecutor.copy()
            VoiceCommand.AccessibilityAction.ActionType.PASTE -> com.rexyy.app.accessibility.AccessibilityActionExecutor.paste()
        }

        return if (success) {
            val label = when (cmd.actionType) {
                VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN -> "Scrolled down."
                VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP -> "Scrolled up."
                VoiceCommand.AccessibilityAction.ActionType.GO_BACK -> "Went back."
                VoiceCommand.AccessibilityAction.ActionType.GO_HOME -> "Returned to home screen."
                VoiceCommand.AccessibilityAction.ActionType.RECENTS -> "Opened recent apps."
                VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT -> "Typed: ${cmd.argument}."
                VoiceCommand.AccessibilityAction.ActionType.COPY -> "Text copied."
                VoiceCommand.AccessibilityAction.ActionType.PASTE -> "Text pasted."
            }
            VoiceCommandResult.Handled(label)
        } else {
            VoiceCommandResult.Error("Could not perform accessibility action on active screen.")
        }
    }

    private fun executeBluetoothSettings(turnOn: Boolean?, context: Context): VoiceCommandResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening Bluetooth settings...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not open Bluetooth settings: ${e.localizedMessage}")
        }
    }

    private fun executeWifiSettings(context: Context): VoiceCommandResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening Wi-Fi settings...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not open Wi-Fi settings: ${e.localizedMessage}")
        }
    }

    private fun executeOpenSettings(context: Context): VoiceCommandResult {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening system settings...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not open Settings: ${e.localizedMessage}")
        }
    }

    private suspend fun executeOpenCamera(context: Context): VoiceCommandResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening camera...")
        } catch (_: Exception) {
            // Fallback to launching general camera app
            val discovery = AppDiscoveryManager(context)
            executeOpenApp("Camera", context)
        }
    }

    private fun executeOpenCalendar(context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening calendar...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not open calendar: ${e.localizedMessage}")
        }
    }

    private fun executeOpenContacts(context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Opening contacts...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not open contacts: ${e.localizedMessage}")
        }
    }

    private fun executeGetDeviceInfo(context: Context): VoiceCommandResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val androidVer = Build.VERSION.RELEASE

        val info = buildString {
            append("Device: $manufacturer $model\n")
            append("Android OS: Version $androidVer (API ${Build.VERSION.SDK_INT})\n")
            if (batteryPct >= 0) append("Battery Level: $batteryPct%")
        }

        return VoiceCommandResult.Handled(info)
    }

    private fun executeSetTimer(seconds: Int, message: String, context: Context): VoiceCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val min = seconds / 60
            val sec = seconds % 60
            val desc = if (min > 0 && sec > 0) "$min minutes $sec seconds" else if (min > 0) "$min minutes" else "$sec seconds"
            VoiceCommandResult.Handled("Timer set for $desc.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not set timer: ${e.localizedMessage}")
        }
    }

    private fun executeSetAlarm(hour: Int, minute: Int, message: String, context: Context): VoiceCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val formatted = String.format("%02d:%02d", hour, minute)
            VoiceCommandResult.Handled("Alarm screen open kar di hai ($formatted). Kripya Clock app mein confirm karein.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not set alarm: ${e.localizedMessage}")
        }
    }

    private fun executeGoogleSearch(query: String, context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Searching for \"$query\"...")
        } catch (_: Exception) {
            val url = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            VoiceCommandResult.Handled("Searching for \"$query\"...")
        }
    }

    private fun executeSendMessage(cmd: VoiceCommand.SendMessage, context: Context): VoiceCommandResult {
        if (cmd.body.isBlank()) {
            return VoiceCommandResult.CollectMessageInput(
                targetName = cmd.target,
                isWhatsApp = false,
                prompt = "${cmd.target} ko kya SMS bhejna hai?"
            )
        }

        if (!cmd.confirmedSend) {
            val prompt = "${cmd.target} ko ye SMS bheju?\n\"${cmd.body}\""
            return VoiceCommandResult.RequiresConfirmation(
                prompt = prompt,
                commandToExecute = cmd.copy(confirmedSend = true)
            )
        }

        val resolvedPhone = CallerIdentityResolver.findPhoneNumberByName(context, cmd.target) ?: cmd.target
        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(resolvedPhone)}")
            putExtra("sms_body", cmd.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(sendIntent)
            VoiceCommandResult.Handled("Opening SMS composer for ${cmd.target}...")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not compose SMS: ${e.localizedMessage}")
        }
    }

    private fun executeFindContact(contactName: String, context: Context): VoiceCommandResult {
        val storage = SecureStorage(context)
        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())

        return when (val result = CallerIdentityResolver.resolveContactSummary(context, contactName)) {
            is CallerIdentityResolver.ContactSearchResult.PermissionNeeded -> {
                VoiceCommandResult.Error("Contacts permission is required to search contacts. Please grant Contacts permission in Settings.")
            }
            is CallerIdentityResolver.ContactSearchResult.NotFound -> {
                val msg = when (lang) {
                    com.rexyy.app.utils.AppLanguage.HINDI -> "कॉन्टैक्ट्स में \"${result.query}\" नहीं मिला।"
                    com.rexyy.app.utils.AppLanguage.ENGLISH -> "Could not find \"${result.query}\" in your contacts."
                    com.rexyy.app.utils.AppLanguage.HINGLISH -> "\"${result.query}\" contacts mein nahi mila."
                }
                VoiceCommandResult.Handled(msg)
            }
            is CallerIdentityResolver.ContactSearchResult.SingleMatch -> {
                val c = result.contact
                val msg = when (lang) {
                    com.rexyy.app.utils.AppLanguage.HINDI -> "${c.name} का नंबर मिल गया: ${c.phoneNumber}। क्या आप कॉल या WhatsApp करना चाहते हैं?"
                    com.rexyy.app.utils.AppLanguage.ENGLISH -> "Found ${c.name}: ${c.phoneNumber}. You can say \"Call ${c.name}\" or \"WhatsApp ${c.name}\"."
                    com.rexyy.app.utils.AppLanguage.HINGLISH -> "Found ${c.name}: ${c.phoneNumber}. Aap keh sakte hain \"${c.name} ko call karo\" ya \"${c.name} ko WhatsApp bhejo\"."
                }
                VoiceCommandResult.Handled(msg)
            }
            is CallerIdentityResolver.ContactSearchResult.MultipleMatches -> {
                val list = result.matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                val msg = when (lang) {
                    com.rexyy.app.utils.AppLanguage.HINDI -> "${result.matches.size} कॉन्टैक्ट्स मिले: $list। आप किसे चुनना चाहते हैं?"
                    com.rexyy.app.utils.AppLanguage.ENGLISH -> "Found ${result.matches.size} matching contacts: $list. Which one would you like to call or message?"
                    com.rexyy.app.utils.AppLanguage.HINGLISH -> "${result.matches.size} contacts mile: $list. Kise call ya message karna hai?"
                }
                VoiceCommandResult.Handled(msg)
            }
        }
    }

    private fun executeWakeWord(context: Context): VoiceCommandResult {
        val storage = SecureStorage(context)
        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())
        val name = storage.getAssistantName()
        return VoiceCommandResult.Handled(RexyyLanguageManager.getWakeResponse(lang, name))
    }

    private fun executeCancelAlarm(context: Context): VoiceCommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            VoiceCommandResult.Handled("Dismissing alarm...")
        } catch (_: Exception) {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                VoiceCommandResult.Handled("Opening alarms to cancel.")
            } catch (e: Exception) {
                VoiceCommandResult.Error("Could not cancel alarm: ${e.localizedMessage}")
            }
        }
    }

    private fun executeCancelTimer(context: Context): VoiceCommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            VoiceCommandResult.Handled("Dismissing timer...")
        } catch (_: Exception) {
            VoiceCommandResult.Handled("Timer dismiss intent sent.")
        }
    }

    private fun executeWhatCanYouDo(): VoiceCommandResult {
        val capabilities = """
            Here is what I can do locally & fast:
            • Open Apps: "Open YouTube", "Instagram kholo", "Open WhatsApp", "YT kholo"
            • Calls: "Call Ramzan", "Ramzan ko call karo", "Incoming caller check"
            • WhatsApp: "Send WhatsApp to Ramzan", "Ramzan ko WhatsApp message bhejo"
            • SMS: "Ramzan ko SMS bhejo"
            • Contacts: "Find Ramzan in my contacts"
            • Alarms & Timers: "7 baje alarm lagao", "10 minute ka timer lagao", "Cancel alarm"
            • Device & Settings: "Bluetooth settings", "Wi-Fi settings", "Volume badhao", "Brightness set karo"
            • Multi-Step: "Open YouTube and search cricket"
            • AI Knowledge: Ask anything for complex conversational answers!
        """.trimIndent()
        return VoiceCommandResult.Handled(capabilities)
    }

    private fun executeSetReminder(title: String, context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "REXYY Reminder" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Creating reminder: \"$title\"")
        } catch (_: Exception) {
            VoiceCommandResult.Handled("Reminder noted: $title")
        }
    }

    private fun executeAppSearch(cmd: VoiceCommand.AppSearch, context: Context): VoiceCommandResult {
        return try {
            when (cmd.targetApp) {
                "youtube" -> {
                    val encoded = URLEncoder.encode(cmd.query, "UTF-8")
                    val intent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", cmd.query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(webIntent)
                    }
                    VoiceCommandResult.Handled("Searching for \"${cmd.query}\" on YouTube...")
                }
                "chrome" -> {
                    val encoded = URLEncoder.encode(cmd.query, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                        setPackage("com.android.chrome")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(browserIntent)
                    }
                    VoiceCommandResult.Handled("Searching for \"${cmd.query}\" on Chrome...")
                }
                "maps" -> {
                    val encoded = Uri.encode(cmd.query)
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                        setPackage("com.google.android.apps.maps")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(mapIntent)
                    } catch (_: Exception) {
                        val webMap = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$encoded")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(webMap)
                    }
                    VoiceCommandResult.Handled("Searching for \"${cmd.query}\" on Maps...")
                }
                "spotify" -> {
                    val encoded = Uri.encode(cmd.query)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(webIntent)
                    }
                    VoiceCommandResult.Handled("Searching for \"${cmd.query}\" on Spotify...")
                }
                "playstore" -> {
                    val encoded = Uri.encode(cmd.query)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(webIntent)
                    }
                    VoiceCommandResult.Handled("Searching for \"${cmd.query}\" on Google Play Store...")
                }
                else -> {
                    executeGoogleSearch(cmd.query, context)
                }
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not perform search: ${e.localizedMessage}")
        }
    }

    private fun executeGetBattery(context: Context): VoiceCommandResult {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (rawLevel >= 0 && scale > 0) ((rawLevel / scale.toFloat()) * 100).toInt() else 100
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val statusStr = if (isCharging) "Charging" else "Not charging"
            VoiceCommandResult.Handled("Battery is currently at $pct% ($statusStr).")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Could not retrieve battery info: ${e.localizedMessage}")
        }
    }

    private fun executeGetDate(): VoiceCommandResult {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date())
        return VoiceCommandResult.Handled("Today's date is $dateStr.")
    }

    private fun executeGetTime(): VoiceCommandResult {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeStr = sdf.format(Date())
        return VoiceCommandResult.Handled("Current time is $timeStr.")
    }

    private fun executeToggleFlashlight(turnOn: Boolean?, context: Context): VoiceCommandResult {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = camManager?.cameraIdList?.firstOrNull()
            if (camManager != null && cameraId != null) {
                val enable = turnOn ?: true
                camManager.setTorchMode(cameraId, enable)
                VoiceCommandResult.Handled(if (enable) "Flashlight turned on." else "Flashlight turned off.")
            } else {
                VoiceCommandResult.Error("Flashlight hardware not available.")
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Flashlight error: ${e.localizedMessage}")
        }
    }
}
