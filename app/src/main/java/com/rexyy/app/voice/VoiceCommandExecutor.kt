package com.rexyy.app.voice

import android.app.ActivityManager
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
import com.rexyy.app.launcher.AppSearchManager
import com.rexyy.app.launcher.AppSearchResult
import com.rexyy.app.launcher.SearchVerificationStatus
import com.rexyy.app.router.CommandDiagnosticLogger
import com.rexyy.app.telecom.CallActionController
import com.rexyy.app.telecom.CallStateManager
import com.rexyy.app.telecom.CallerIdentityResolver
import com.rexyy.app.telecom.SmsActionController
import com.rexyy.app.telecom.SmsActionResult
import com.rexyy.app.telecom.TelecomActionResult
import com.rexyy.app.notifications.RexyyNotificationListenerService
import com.rexyy.app.device.ChargingMonitor
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

    @Volatile
    private var lastAppSearchResult: AppSearchResult? = null

    suspend fun execute(command: VoiceCommand, context: Context): VoiceCommandResult {
        val startTime = System.currentTimeMillis()
        val result = try {
            when (command) {
                is VoiceCommand.OpenApp -> executeOpenApp(command.appName, context)
                is VoiceCommand.CloseApp -> executeCloseApp(command, context)
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
                is VoiceCommand.OpenSettings -> executeOpenSettings(command.subSettings, context)
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
                is VoiceCommand.ReadNotifications -> executeReadNotifications(context)
                is VoiceCommand.CheckCharging -> executeCheckCharging(context)
                is VoiceCommand.AccessibilityAction -> executeAccessibilityAction(command, context)
                is VoiceCommand.MultiStepTask -> executeMultiStepTask(command, context)
                is VoiceCommand.AiChat -> VoiceCommandResult.ForwardToAi(command.prompt, command.providerOverride)
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Unable to complete action: ${e.localizedMessage ?: "Unknown error"}")
        }

        val latencyMs = System.currentTimeMillis() - startTime
        logDiagnostic(command, result, latencyMs)
        return result
    }

    private fun logDiagnostic(command: VoiceCommand, result: VoiceCommandResult, latencyMs: Long) {
        val raw = when (command) {
            is VoiceCommand.OpenApp -> command.rawInput
            is VoiceCommand.CloseApp -> command.rawInput
            is VoiceCommand.AppSearch -> command.rawInput
            is VoiceCommand.GoogleSearch -> command.rawInput
            is VoiceCommand.GetBattery -> command.rawInput
            is VoiceCommand.GetDate -> command.rawInput
            is VoiceCommand.GetTime -> command.rawInput
            is VoiceCommand.ToggleFlashlight -> command.rawInput
            is VoiceCommand.SetAlarm -> command.rawInput
            is VoiceCommand.SetTimer -> command.rawInput
            is VoiceCommand.OpenSettings -> command.rawInput
            is VoiceCommand.BluetoothSettings -> command.rawInput
            is VoiceCommand.WifiSettings -> command.rawInput
            is VoiceCommand.AdjustVolume -> command.rawInput
            is VoiceCommand.AdjustBrightness -> command.rawInput
            is VoiceCommand.CallContact -> command.rawInput
            is VoiceCommand.SendMessage -> command.rawInput
            is VoiceCommand.WhatsAppMessage -> command.rawInput
            is VoiceCommand.WhatsAppChat -> command.rawInput
            is VoiceCommand.OpenWhatsApp -> command.rawInput
            is VoiceCommand.OpenCamera -> command.rawInput
            is VoiceCommand.OpenCalendar -> command.rawInput
            is VoiceCommand.OpenContacts -> command.rawInput
            is VoiceCommand.FindContact -> command.rawInput
            is VoiceCommand.WakeWord -> command.rawInput
            is VoiceCommand.CancelAlarm -> command.rawInput
            is VoiceCommand.CancelTimer -> command.rawInput
            is VoiceCommand.RepeatLast -> command.rawInput
            is VoiceCommand.Stop -> command.rawInput
            is VoiceCommand.WhatCanYouDo -> command.rawInput
            is VoiceCommand.AreYouThere -> command.rawInput
            is VoiceCommand.CheckCaller -> command.rawInput
            is VoiceCommand.AnswerCall -> command.rawInput
            is VoiceCommand.RejectCall -> command.rawInput
            is VoiceCommand.GetDeviceInfo -> command.rawInput
            is VoiceCommand.MultiStepTask -> command.rawInput
            is VoiceCommand.AccessibilityAction -> command.rawInput
            is VoiceCommand.SetReminder -> command.rawInput
            is VoiceCommand.ReadNotifications -> command.rawInput
            is VoiceCommand.CheckCharging -> command.rawInput
            is VoiceCommand.AiChat -> command.prompt
        }

        val intentName = command::class.simpleName ?: "UnknownIntent"

        val entities = mutableMapOf<String, String>()
        var detectedTargetApp: String? = null
        var detectedSearchQuery: String? = null
        var detectedLaunchMethod: String? = null
        var detectedSearchMethod: String? = null
        var specificVerification: String? = null
        var specificFailureReason: String? = null

        when (command) {
            is VoiceCommand.OpenApp -> {
                entities["appName"] = command.appName
                entities["targetApp"] = command.appName
                detectedTargetApp = command.appName
                detectedLaunchMethod = "LAUNCH_INTENT"
            }
            is VoiceCommand.CloseApp -> entities["target"] = command.target
            is VoiceCommand.AppSearch -> {
                entities["targetApp"] = command.targetApp
                entities["query"] = command.query
                detectedTargetApp = command.targetApp
                detectedSearchQuery = command.query
                val searchRes = lastAppSearchResult
                if (searchRes != null && searchRes.extractedQuery == command.query) {
                    detectedTargetApp = searchRes.targetApp
                    detectedLaunchMethod = searchRes.launchMethod
                    detectedSearchMethod = searchRes.searchMethod
                    specificVerification = searchRes.verificationStatus.name
                    specificFailureReason = searchRes.failureReason
                    entities["launchMethod"] = searchRes.launchMethod
                    entities["searchMethod"] = searchRes.searchMethod
                    entities["verificationStatus"] = searchRes.verificationStatus.name
                }
            }
            is VoiceCommand.GoogleSearch -> {
                entities["query"] = command.query
                detectedSearchQuery = command.query
                detectedLaunchMethod = "WEB_INTENT"
                detectedSearchMethod = "GOOGLE_SEARCH"
            }
            is VoiceCommand.ToggleFlashlight -> entities["turnOn"] = command.turnOn?.toString() ?: "toggle"
            is VoiceCommand.SetAlarm -> {
                entities["hour"] = command.hour.toString()
                entities["minute"] = command.minute.toString()
            }
            is VoiceCommand.SetTimer -> entities["seconds"] = command.seconds.toString()
            is VoiceCommand.OpenSettings -> entities["subSettings"] = command.subSettings
            is VoiceCommand.AdjustVolume -> {
                command.percent?.let { entities["percent"] = it.toString() }
                entities["raise"] = command.raise.toString()
                entities["mute"] = command.mute.toString()
            }
            is VoiceCommand.AdjustBrightness -> {
                command.percent?.let { entities["percent"] = it.toString() }
                command.raise?.let { entities["raise"] = it.toString() }
            }
            is VoiceCommand.CallContact -> entities["target"] = command.target
            is VoiceCommand.SendMessage -> {
                entities["target"] = command.target
                entities["body"] = command.body
            }
            is VoiceCommand.WhatsAppMessage -> {
                entities["target"] = command.target
                entities["body"] = command.body
            }
            is VoiceCommand.FindContact -> entities["contactName"] = command.contactName
            else -> Unit
        }

        val (execResult, defaultVerification, failureReason) = when (result) {
            is VoiceCommandResult.Handled -> Triple(result.replyText, "VERIFIED", null)
            is VoiceCommandResult.Error -> Triple("ERROR", "FAILED", result.errorMessage)
            is VoiceCommandResult.ForwardToAi -> Triple("FORWARDED_TO_AI", "PENDING_AI", null)
            is VoiceCommandResult.RequiresConfirmation -> Triple("CONFIRMATION_REQUIRED", "PENDING_USER", null)
            is VoiceCommandResult.CollectMessageInput -> Triple("COLLECT_INPUT", "PENDING_USER", null)
        }

        val finalVerification = specificVerification ?: defaultVerification
        val finalFailureReason = specificFailureReason ?: failureReason

        CommandDiagnosticLogger.log(
            rawCommand = raw,
            detectedIntent = intentName,
            extractedEntities = entities,
            targetApp = detectedTargetApp,
            searchQuery = detectedSearchQuery,
            launchMethod = detectedLaunchMethod,
            searchMethod = detectedSearchMethod,
            executionResult = execResult,
            verificationResult = finalVerification,
            failureReason = finalFailureReason,
            latencyMs = latencyMs
        )
    }

    private suspend fun executeOpenApp(appName: String, context: Context): VoiceCommandResult {
        val discovery = AppDiscoveryManager(context)
        return when (val outcome = discovery.findAndLaunchApp(appName)) {
            is AppLaunchOutcome.Success -> {
                VoiceCommandResult.Handled("${outcome.appLabel} khol diya hai.")
            }
            is AppLaunchOutcome.MultipleMatches -> {
                VoiceCommandResult.Handled("Multiple apps mile: ${outcome.candidates.joinToString(", ")}. Kaunsa open karna hai?")
            }
            is AppLaunchOutcome.NotInstalled -> {
                VoiceCommandResult.Handled("${outcome.appName} phone mein installed nahi hai.")
            }
            is AppLaunchOutcome.FailedToLaunch -> {
                VoiceCommandResult.Error("${outcome.appLabel} open nahi ho paya.")
            }
        }
    }

    private fun executeCloseApp(cmd: VoiceCommand.CloseApp, context: Context): VoiceCommandResult {
        if (com.rexyy.app.accessibility.RexyyAccessibilityService.isServiceEnabled()) {
            val success = com.rexyy.app.accessibility.AccessibilityActionExecutor.pressHome()
            if (success) {
                return VoiceCommandResult.Handled("REXYY background mein chala gaya.")
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(homeIntent)
            VoiceCommandResult.Handled("REXYY background mein chala gaya.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Unable to minimize app: ${e.localizedMessage}")
        }
    }

    private fun executeOpenWhatsApp(context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        return when (val result = wa.openWhatsApp()) {
            is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(result.message)
            is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(result.message)
            else -> VoiceCommandResult.Error("WhatsApp open nahi ho paya.")
        }
    }

    private suspend fun executeWhatsAppMessage(cmd: VoiceCommand.WhatsAppMessage, context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        if (cmd.body.isBlank()) {
            return VoiceCommandResult.CollectMessageInput(
                targetName = cmd.target,
                isWhatsApp = true,
                prompt = "${cmd.target} ko WhatsApp par kya message bhejna hai?"
            )
        }

        if (!cmd.confirmedSend) {
            val prompt = "${cmd.target} ko WhatsApp par ye message bheju?\n\"${cmd.body}\""
            return VoiceCommandResult.RequiresConfirmation(
                prompt = prompt,
                commandToExecute = cmd.copy(confirmedSend = true)
            )
        }

        return when (val sent = wa.executeSendWithAccessibility(cmd.target, cmd.body)) {
            is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(sent.message)
            is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(sent.message)
            is WhatsAppActionResult.MultipleMatches -> {
                val names = sent.matches.take(3).joinToString { it.name }
                VoiceCommandResult.Handled("${sent.matches.size} contacts mile: $names. Kisko WhatsApp bhejna hai?")
            }
            is WhatsAppActionResult.NeedsMessageBody -> {
                VoiceCommandResult.CollectMessageInput(
                    targetName = sent.targetName,
                    isWhatsApp = true,
                    prompt = sent.prompt
                )
            }
            is WhatsAppActionResult.Failure -> VoiceCommandResult.Error(sent.error)
            else -> VoiceCommandResult.Error("WhatsApp message send nahi hua.")
        }
    }

    private fun executeWhatsAppChat(target: String, context: Context): VoiceCommandResult {
        val wa = WhatsAppActionManager(context)
        return when (val chat = wa.openChat(target)) {
            is WhatsAppActionResult.Success -> VoiceCommandResult.Handled(chat.message)
            is WhatsAppActionResult.NotInstalled -> VoiceCommandResult.Handled(chat.message)
            is WhatsAppActionResult.Failure -> VoiceCommandResult.Error(chat.error)
            else -> VoiceCommandResult.Error("WhatsApp chat open nahi ho payi.")
        }
    }

    private fun executeCallContact(cmd: VoiceCommand.CallContact, context: Context): VoiceCommandResult {
        if (!cmd.confirmedDirectCall) {
            val contactRes = CallerIdentityResolver.resolveContactForAction(context, cmd.target)
            return when (contactRes) {
                is CallerIdentityResolver.ContactActionResult.Multiple -> {
                    val names = contactRes.matches.take(3).joinToString { it.name }
                    VoiceCommandResult.Handled("${contactRes.matches.size} contacts mile: $names. Kise call lagani hai?")
                }
                is CallerIdentityResolver.ContactActionResult.NotFound -> {
                    val digits = cmd.target.filter { it.isDigit() || it == '+' }
                    if (digits.length >= 3) {
                        VoiceCommandResult.RequiresConfirmation(
                            prompt = "${cmd.target} ko call karu?",
                            commandToExecute = cmd.copy(confirmedDirectCall = true)
                        )
                    } else {
                        VoiceCommandResult.Error("Contact \"${cmd.target}\" contacts mein nahi mila.")
                    }
                }
                is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> {
                    VoiceCommandResult.Error("Contacts permission required hai. Settings mein Contacts permission allow karein.")
                }
                is CallerIdentityResolver.ContactActionResult.Resolved -> {
                    VoiceCommandResult.RequiresConfirmation(
                        prompt = "${contactRes.contact.name} ko call karu?",
                        commandToExecute = cmd.copy(target = contactRes.contact.name, confirmedDirectCall = true)
                    )
                }
            }
        }

        val controller = CallActionController(context)
        return when (val res = controller.callContact(cmd.target, confirmedDirectCall = true)) {
            is TelecomActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is TelecomActionResult.MultipleMatches -> {
                val names = res.matches.take(3).joinToString { it.name }
                VoiceCommandResult.Handled("${res.matches.size} contacts mile: $names. Kise call lagani hai?")
            }
            is TelecomActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is TelecomActionResult.Unsupported -> VoiceCommandResult.Handled(res.reason)
            is TelecomActionResult.Failure -> VoiceCommandResult.Error(res.error)
        }
    }

    private fun executeCheckCaller(context: Context): VoiceCommandResult {
        val manager = CallStateManager(context)
        val voiceLang = SecureStorage(context).getVoiceLanguage()
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
            else -> VoiceCommandResult.Error("Unable to answer call.")
        }
    }

    private fun executeRejectCall(context: Context): VoiceCommandResult {
        val controller = CallActionController(context)
        return when (val res = controller.rejectCall()) {
            is TelecomActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is TelecomActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is TelecomActionResult.Unsupported -> VoiceCommandResult.Handled(res.reason)
            is TelecomActionResult.Failure -> VoiceCommandResult.Error(res.error)
            else -> VoiceCommandResult.Error("Unable to reject call.")
        }
    }

    private fun executeAdjustVolume(cmd: VoiceCommand.AdjustVolume, context: Context): VoiceCommandResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VoiceCommandResult.Error("Audio service available nahi hai.")

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (cmd.mute) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            return if (current == 0) {
                VoiceCommandResult.Handled("Media volume mute kar diya gaya hai.")
            } else {
                VoiceCommandResult.Handled("Volume minimum set kar diya gaya hai.")
            }
        }

        if (cmd.percent != null) {
            val target = (maxVol * (cmd.percent / 100f)).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            val readVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val readPct = if (maxVol > 0) (readVol * 100 / maxVol) else cmd.percent
            return VoiceCommandResult.Handled("Volume $readPct% set kar diya gaya hai.")
        }

        val direction = if (cmd.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val actionDesc = if (cmd.raise) "Volume badha diya gaya hai." else "Volume kam kar diya gaya hai."
        return VoiceCommandResult.Handled(actionDesc)
    }

    private fun executeAdjustBrightness(cmd: VoiceCommand.AdjustBrightness, context: Context): VoiceCommandResult {
        val canWrite = Settings.System.canWrite(context)
        if (!canWrite) {
            // Strictly return error with clear explanation, never falsely report success
            return VoiceCommandResult.Error("Screen brightness change karne ke liye 'Write System Settings' permission zaroori hai. Kripya Settings mein 'Modify system settings' allow karein.")
        }

        return try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val targetValue = if (cmd.percent != null) {
                (cmd.percent * 255 / 100).coerceIn(10, 255)
            } else if (cmd.raise == true) {
                (current + 50).coerceAtMost(255)
            } else if (cmd.raise == false) {
                (current - 50).coerceAtLeast(15)
            } else {
                current
            }

            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetValue)
            val readBack = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            val finalPct = (readBack * 100 / 255)
            VoiceCommandResult.Handled("Brightness $finalPct% par set ho gayi hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Brightness change karne mein error: ${e.localizedMessage}")
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
            VoiceCommand.AccessibilityAction.ActionType.REPLACE_TEXT -> com.rexyy.app.accessibility.AccessibilityActionExecutor.replaceText(cmd.argument)
            VoiceCommand.AccessibilityAction.ActionType.COPY -> com.rexyy.app.accessibility.AccessibilityActionExecutor.copy()
            VoiceCommand.AccessibilityAction.ActionType.PASTE -> com.rexyy.app.accessibility.AccessibilityActionExecutor.paste()
            VoiceCommand.AccessibilityAction.ActionType.CLICK_NODE -> com.rexyy.app.accessibility.AccessibilityActionExecutor.clickByText(cmd.argument)
            VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH -> com.rexyy.app.accessibility.AccessibilityActionExecutor.submitSearch()
        }

        return if (success) {
            val label = when (cmd.actionType) {
                VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN -> "Scrolled down."
                VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP -> "Scrolled up."
                VoiceCommand.AccessibilityAction.ActionType.GO_BACK -> "Went back."
                VoiceCommand.AccessibilityAction.ActionType.GO_HOME -> "Returned to home screen."
                VoiceCommand.AccessibilityAction.ActionType.RECENTS -> "Opened recent apps."
                VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT -> "Typed: ${cmd.argument}."
                VoiceCommand.AccessibilityAction.ActionType.REPLACE_TEXT -> "Replaced text: ${cmd.argument}."
                VoiceCommand.AccessibilityAction.ActionType.COPY -> "Text copied."
                VoiceCommand.AccessibilityAction.ActionType.PASTE -> "Text pasted."
                VoiceCommand.AccessibilityAction.ActionType.CLICK_NODE -> "Clicked: ${cmd.argument}."
                VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH -> "Search submitted."
            }
            VoiceCommandResult.Handled(label)
        } else {
            VoiceCommandResult.Error("Could not perform accessibility action on active screen.")
        }
    }

    private suspend fun executeMultiStepTask(cmd: VoiceCommand.MultiStepTask, context: Context): VoiceCommandResult {
        val workflowResult = com.rexyy.app.accessibility.MultiStepWorkflowManager.executeWorkflow(cmd, context)
        return when (workflowResult.finalState) {
            com.rexyy.app.accessibility.WorkflowExecutionState.VERIFIED -> {
                VoiceCommandResult.Handled(workflowResult.summaryMessage)
            }
            else -> {
                VoiceCommandResult.Error(workflowResult.summaryMessage)
            }
        }
    }

    private fun executeBluetoothSettings(turnOn: Boolean?, context: Context): VoiceCommandResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Bluetooth settings open kar di gayi hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Bluetooth settings open nahi ho payi: ${e.localizedMessage}")
        }
    }

    private fun executeWifiSettings(context: Context): VoiceCommandResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Wi-Fi settings open kar di gayi hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Wi-Fi settings open nahi ho payi: ${e.localizedMessage}")
        }
    }

    private fun executeOpenSettings(subSettings: String = "", context: Context): VoiceCommandResult {
        val intent = when (subSettings.lowercase().trim()) {
            "display", "screen" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound", "audio", "volume", "ringtone" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery", "power" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "apps", "applications" -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "network", "data", "wireless" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            val name = if (subSettings.isBlank()) "Settings" else "$subSettings Settings"
            VoiceCommandResult.Handled("$name open kar di gayi hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Settings open nahi ho payi: ${e.localizedMessage}")
        }
    }

    private suspend fun executeOpenCamera(context: Context): VoiceCommandResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Camera open kar diya gaya hai.")
        } catch (_: Exception) {
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
            VoiceCommandResult.Handled("Calendar open kar diya gaya hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Calendar open nahi ho paya: ${e.localizedMessage}")
        }
    }

    private fun executeOpenContacts(context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("Contacts open kar diye gaye hain.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Contacts open nahi ho paye: ${e.localizedMessage}")
        }
    }

    private fun executeGetDeviceInfo(context: Context): VoiceCommandResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val androidVer = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        val info = buildString {
            append("Device: $manufacturer $model\n")
            append("Android OS: Version $androidVer (API $sdk)\n")
            if (batteryPct >= 0) append("Battery: $batteryPct%\n")
            if (totalRamMb > 0) append("RAM: ${availRamMb}MB available of ${totalRamMb}MB")
        }.trim()

        return VoiceCommandResult.Handled(info)
    }

    private fun executeSetTimer(seconds: Int, message: String, context: Context): VoiceCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val min = seconds / 60
            val sec = seconds % 60
            val desc = if (min > 0 && sec > 0) "$min minute $sec second" else if (min > 0) "$min minute" else "$sec second"
            VoiceCommandResult.Handled("$desc ka timer set kar diya gaya hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Timer set nahi ho paya: ${e.localizedMessage}")
        }
    }

    private fun executeSetAlarm(hour: Int, minute: Int, message: String, context: Context): VoiceCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val formatted = String.format("%02d:%02d", hour, minute)
            VoiceCommandResult.Handled("Alarm $formatted baje ke liye set kar diya gaya hai.")
        } catch (e: Exception) {
            VoiceCommandResult.Error("Alarm set nahi ho paya: ${e.localizedMessage}")
        }
    }

    private fun executeGoogleSearch(query: String, context: Context): VoiceCommandResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            VoiceCommandResult.Handled("\"$query\" search kiya ja raha hai...")
        } catch (_: Exception) {
            val url = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            VoiceCommandResult.Handled("\"$query\" search kiya ja raha hai...")
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

        val controller = SmsActionController(context)
        return when (val res = controller.sendSms(cmd.target, cmd.body, confirmedSend = true)) {
            is SmsActionResult.Success -> VoiceCommandResult.Handled(res.message)
            is SmsActionResult.MultipleMatches -> {
                val names = res.matches.take(3).joinToString { it.name }
                VoiceCommandResult.Handled("${res.matches.size} contacts mile: $names. Kisko SMS bhejna hai?")
            }
            is SmsActionResult.NeedsMessageBody -> {
                VoiceCommandResult.CollectMessageInput(
                    targetName = res.target,
                    isWhatsApp = false,
                    prompt = res.prompt
                )
            }
            is SmsActionResult.PermissionNeeded -> VoiceCommandResult.Error(res.message)
            is SmsActionResult.Failure -> VoiceCommandResult.Error(res.error)
        }
    }

    private fun executeReadNotifications(context: Context): VoiceCommandResult {
        if (!RexyyNotificationListenerService.isNotificationAccessEnabled(context)) {
            RexyyNotificationListenerService.openNotificationAccessSettings(context)
            return VoiceCommandResult.Handled("Notification access required hai. Settings open kar di gayi hai, kripya REXXY ko allow karein.")
        }

        val notifs = RexyyNotificationListenerService.recentNotifications.value
        if (notifs.isEmpty()) {
            return VoiceCommandResult.Handled("Abhi koi naye notifications nahi hain.")
        }

        val count = notifs.size
        val top = notifs.take(3).joinToString("; ") { "${it.appName} se: ${it.title.ifBlank { it.text.take(30) }}" }
        return VoiceCommandResult.Handled("Aapke paas $count notifications hain. Recent: $top")
    }

    private fun executeCheckCharging(context: Context): VoiceCommandResult {
        val isCharging = ChargingMonitor.isCharging.value
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val pctStr = if (pct >= 0) " Battery $pct% hai." else ""
        return if (isCharging) {
            VoiceCommandResult.Handled("Charger connected hai aur charging ho rahi hai.$pctStr")
        } else {
            VoiceCommandResult.Handled("Phone charger se connect nahi hai.$pctStr")
        }
    }

    private fun executeFindContact(contactName: String, context: Context): VoiceCommandResult {
        val storage = SecureStorage(context)
        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())

        return when (val result = CallerIdentityResolver.resolveContactSummary(context, contactName)) {
            is CallerIdentityResolver.ContactSearchResult.PermissionNeeded -> {
                VoiceCommandResult.Error("Contacts permission required hai. Settings mein Contacts permission allow karein.")
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
                    com.rexyy.app.utils.AppLanguage.HINGLISH -> "Found ${c.name}: ${c.phoneNumber}. Aap bol sakte hain \"${c.name} ko call karo\" ya \"${c.name} ko WhatsApp bhejo\"."
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
            VoiceCommandResult.Handled("Alarm dismiss kiya ja raha hai.")
        } catch (_: Exception) {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                VoiceCommandResult.Handled("Alarms open kar diye gaye hain.")
            } catch (e: Exception) {
                VoiceCommandResult.Error("Alarm cancel nahi ho paya: ${e.localizedMessage}")
            }
        }
    }

    private fun executeCancelTimer(context: Context): VoiceCommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            VoiceCommandResult.Handled("Timer dismiss kiya ja raha hai.")
        } catch (_: Exception) {
            VoiceCommandResult.Handled("Timer dismiss request sent.")
        }
    }

    private fun executeWhatCanYouDo(): VoiceCommandResult {
        val capabilities = """
            Main yeh sab commands directly bina AI ke locally execute kar sakta hoon:
            • Apps: "YouTube kholo", "Instagram khol do", "Open WhatsApp", "YT chalao"
            • Close / Background: "Exit", "Close app", "Background me jao"
            • Calls: "Call Ramzan", "Ramzan ko call karo", "Call uthao", "Call kato"
            • WhatsApp: "Ramzan ko WhatsApp message bhejo", "WhatsApp open karo"
            • SMS: "Ramzan ko SMS bhejo"
            • Contacts: "Find Ramzan in my contacts", "Contacts kholo"
            • Alarms & Timers: "7 baje alarm laga do", "10 minute ka timer lagao"
            • Battery & Device: "Battery kitni hai", "Battery status", "Device info"
            • Date & Time: "Aaj ki date kya hai", "Time kya hua"
            • Brightness & Volume: "Brightness 50% karo", "Volume badhao", "Mute"
            • Torch & Settings: "Torch on karo", "Settings kholo", "Wi-Fi settings"
            • Search: "YouTube pe cricket search karo", "Search for weather"
            • Generative AI: Kisi bhi sawal ya discussion ke liye poochhein!
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
            VoiceCommandResult.Handled("Reminder create kiya ja raha hai: \"$title\"")
        } catch (_: Exception) {
            VoiceCommandResult.Handled("Reminder noted: $title")
        }
    }

    private suspend fun executeAppSearch(cmd: VoiceCommand.AppSearch, context: Context): VoiceCommandResult {
        return try {
            val searchManager = AppSearchManager(context)
            val result = searchManager.executeSearch(cmd.targetApp, cmd.query)
            lastAppSearchResult = result

            when (result.verificationStatus) {
                SearchVerificationStatus.SEARCH_VERIFIED,
                SearchVerificationStatus.SEARCH_STARTED,
                SearchVerificationStatus.APP_LAUNCHED -> {
                    VoiceCommandResult.Handled(result.replyText)
                }
                SearchVerificationStatus.FAILED -> {
                    VoiceCommandResult.Error(result.replyText)
                }
            }
        } catch (e: Exception) {
            val failedResult = AppSearchResult(
                targetApp = cmd.targetApp,
                resolvedPackage = null,
                extractedQuery = cmd.query,
                launchMethod = "NONE",
                searchMethod = "NONE",
                verificationStatus = SearchVerificationStatus.FAILED,
                replyText = "Search perform nahi ho paya: ${e.localizedMessage}",
                failureReason = e.localizedMessage
            )
            lastAppSearchResult = failedResult
            VoiceCommandResult.Error("Search perform nahi ho paya: ${e.localizedMessage}")
        }
    }

    private fun executeGetBattery(context: Context): VoiceCommandResult {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (rawLevel >= 0 && scale > 0) {
                ((rawLevel / scale.toFloat()) * 100).toInt()
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            }
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargingText = if (isCharging) "charging ho rahi hai" else "charging nahi ho rahi hai"

            if (pct >= 0) {
                VoiceCommandResult.Handled("Battery abhi $pct% hai aur $chargingText.")
            } else {
                VoiceCommandResult.Handled("Battery status available nahi hai.")
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Battery info retrieve nahi ho payi: ${e.localizedMessage}")
        }
    }

    private fun executeGetDate(): VoiceCommandResult {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date())
        return VoiceCommandResult.Handled("Aaj ki date hai: $dateStr.")
    }

    private fun executeGetTime(): VoiceCommandResult {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeStr = sdf.format(Date())
        return VoiceCommandResult.Handled("Abhi samay hai: $timeStr.")
    }

    private fun executeToggleFlashlight(turnOn: Boolean?, context: Context): VoiceCommandResult {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = camManager?.cameraIdList?.firstOrNull()
            if (camManager != null && cameraId != null) {
                val enable = turnOn ?: true
                camManager.setTorchMode(cameraId, enable)
                VoiceCommandResult.Handled(if (enable) "Torch on ho gaya." else "Torch off ho gaya.")
            } else {
                VoiceCommandResult.Error("Device mein torch/flashlight hardware available nahi hai.")
            }
        } catch (e: Exception) {
            VoiceCommandResult.Error("Flashlight error: ${e.localizedMessage}")
        }
    }
}
