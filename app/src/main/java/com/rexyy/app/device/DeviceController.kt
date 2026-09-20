package com.rexyy.app.device

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.net.URLEncoder

sealed class DeviceAction {
    data class OpenApp(val appName: String) : DeviceAction()
    data class OpenSettings(val subSettings: String = "") : DeviceAction()
    data class AdjustVolume(val raise: Boolean) : DeviceAction()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String) : DeviceAction()
    data class GoogleSearch(val query: String) : DeviceAction()
    data class CallPhone(val numberOrName: String) : DeviceAction()
    data class SendSms(val numberOrName: String, val message: String) : DeviceAction()
    object OpenBluetooth : DeviceAction()
    object OpenWifi : DeviceAction()
    object OpenCalendar : DeviceAction()
    object GetDeviceInfo : DeviceAction()
}

sealed class DeviceActionResult {
    data class Success(val message: String) : DeviceActionResult()
    data class NeedsConfirmation(val action: DeviceAction, val description: String) : DeviceActionResult()
    data class Error(val errorMessage: String) : DeviceActionResult()
}

interface DeviceController {
    val isRoot: Boolean
    fun executeAction(action: DeviceAction, context: Context): DeviceActionResult
}

class NonRootDeviceController : DeviceController {
    override val isRoot: Boolean = false

    override fun executeAction(action: DeviceAction, context: Context): DeviceActionResult {
        return try {
            when (action) {
                is DeviceAction.OpenApp -> handleOpenApp(action.appName, context)
                is DeviceAction.OpenSettings -> handleOpenSettings(action.subSettings, context)
                is DeviceAction.AdjustVolume -> handleAdjustVolume(action.raise, context)
                is DeviceAction.SetAlarm -> handleSetAlarm(action.hour, action.minute, action.message, context)
                is DeviceAction.GoogleSearch -> handleGoogleSearch(action.query, context)
                is DeviceAction.CallPhone -> handleCall(action.numberOrName, context)
                is DeviceAction.SendSms -> handleSendSms(action.numberOrName, action.message, context)
                DeviceAction.OpenBluetooth -> handleOpenBluetooth(context)
                DeviceAction.OpenWifi -> handleOpenWifi(context)
                DeviceAction.OpenCalendar -> handleOpenCalendar(context)
                DeviceAction.GetDeviceInfo -> handleGetDeviceInfo(context)
            }
        } catch (e: Exception) {
            DeviceActionResult.Error("Action could not be completed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun handleOpenApp(appName: String, context: Context): DeviceActionResult {
        val pm = context.packageManager
        val lower = appName.lowercase().trim()

        if (lower.contains("youtube")) {
            val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return DeviceActionResult.Success("Opening YouTube...")
        }

        try {
            val apps = pm.getInstalledApplications(0)
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(appName, ignoreCase = true) || appName.contains(label, ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return DeviceActionResult.Success("Opening $label...")
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to web search
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(appName, "UTF-8"))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
        return DeviceActionResult.Success("Searching for $appName...")
    }

    private fun handleOpenSettings(subSettings: String, context: Context): DeviceActionResult {
        val action = when (subSettings.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return DeviceActionResult.Success("Opening Settings...")
    }

    private fun handleAdjustVolume(raise: Boolean, context: Context): DeviceActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return DeviceActionResult.Error("Audio service unavailable")
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return DeviceActionResult.Success(if (raise) "Volume increased" else "Volume decreased")
    }

    private fun handleSetAlarm(hour: Int, minute: Int, message: String, context: Context): DeviceActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "REXYY Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            DeviceActionResult.Success("Alarm set for %02d:%02d".format(hour, minute))
        } else {
            val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(clockIntent)
            DeviceActionResult.Success("Opening clock alarms...")
        }
    }

    private fun handleGoogleSearch(query: String, context: Context): DeviceActionResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionResult.Success("Searching for \"$query\"...")
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            DeviceActionResult.Success("Searching Google for \"$query\"...")
        }
    }

    private fun handleCall(numberOrName: String, context: Context): DeviceActionResult {
        val isNumeric = numberOrName.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        val uri = if (isNumeric && numberOrName.isNotBlank()) {
            Uri.parse("tel:${numberOrName.replace(" ", "")}")
        } else {
            Uri.parse("tel:")
        }
        val intent = Intent(Intent.ACTION_DIAL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return DeviceActionResult.Success("Opening dialer for $numberOrName")
    }

    private fun handleSendSms(numberOrName: String, message: String, context: Context): DeviceActionResult {
        val uri = Uri.parse("smsto:${if (numberOrName.all { it.isDigit() || it == '+' }) numberOrName else ""}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (message.isNotBlank()) putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return DeviceActionResult.Success("Opening messages...")
    }

    private fun handleOpenBluetooth(context: Context): DeviceActionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return DeviceActionResult.Success("Opening Bluetooth settings...")
    }

    private fun handleOpenWifi(context: Context): DeviceActionResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return DeviceActionResult.Success("Opening Wi-Fi settings...")
    }

    private fun handleOpenCalendar(context: Context): DeviceActionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionResult.Success("Opening Calendar...")
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            DeviceActionResult.Success("Opening Calendar...")
        }
    }

    private fun handleGetDeviceInfo(context: Context): DeviceActionResult {
        val info = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
        return DeviceActionResult.Success(info)
    }
}

class RootDeviceController(
    private val delegate: NonRootDeviceController = NonRootDeviceController()
) : DeviceController {
    override val isRoot: Boolean = true

    override fun executeAction(action: DeviceAction, context: Context): DeviceActionResult {
        // Safe delegated execution with root status indicator
        return delegate.executeAction(action, context)
    }
}
