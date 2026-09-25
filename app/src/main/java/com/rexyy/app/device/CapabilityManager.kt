package com.rexyy.app.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.notifications.RexyyNotificationListenerService
import java.io.File

object CapabilityManager {

    fun isRootAvailable(): Boolean {
        return try {
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            paths.any { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return RexyyAccessibilityService.isAccessibilityServiceConfigured(context)
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return RexyyNotificationListenerService.isNotificationAccessEnabled(context)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun canWriteSystemSettings(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inspects actual Android system services and runtime permissions.
     * Evaluates all 11 required assistant capabilities with real current state.
     */
    fun getCapabilities(context: Context): List<DeviceCapability> {
        // 1. Microphone
        val hasMic = hasPermission(context, Manifest.permission.RECORD_AUDIO)

        // 2. Notifications (API 33+ runtime permission or NotificationManagerCompat)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        // 3. Notification Access Service
        val hasNotifAccess = isNotificationAccessEnabled(context)
        val isNotifListenerConnected = RexyyNotificationListenerService.isListenerConnected.value

        // 4. Accessibility Service
        val isA11y = isAccessibilityEnabled(context)

        // 5. Phone / Call
        val hasCallPhone = hasPermission(context, Manifest.permission.CALL_PHONE)
        val hasPhoneState = hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        val hasPhoneCapability = hasCallPhone && hasPhoneState

        // 6. Contacts
        val hasContacts = hasPermission(context, Manifest.permission.READ_CONTACTS)

        // 7. SMS
        val hasSms = hasPermission(context, Manifest.permission.SEND_SMS)

        // 8. Camera / Torch
        val hasCamera = hasPermission(context, Manifest.permission.CAMERA)

        // 9. Bluetooth / Nearby Devices (API 31+ BLUETOOTH_CONNECT or normal BLUETOOTH)
        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(context, Manifest.permission.BLUETOOTH)
        }

        // 10. Required System Settings (Modify Brightness)
        val canWriteSettings = canWriteSystemSettings(context)

        // 11. Background Battery Optimization Exemption
        val isBatteryExempt = isBatteryOptimizationIgnored(context)

        return listOf(
            // 1. MICROPHONE (Core Required)
            DeviceCapability(
                id = "mic",
                name = "Microphone & Speech Recognition",
                category = "Core Assistant",
                description = "Live speech-to-text recognition and wake-word 'Hello Rex' detection.",
                rationale = "Allows hands-free voice interactions. Audio is processed on-device and never recorded or transmitted without your explicit command.",
                status = if (hasMic) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasMic,
                statusLabel = if (hasMic) "Granted" else "Not Granted",
                isRequiredForCore = true,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.RECORD_AUDIO
            ),

            // 2. NOTIFICATIONS (Core Required)
            DeviceCapability(
                id = "notif",
                name = "Push Notifications",
                category = "Core Assistant",
                description = "Delivers assistant status alerts, task completion alarms, and reminders.",
                rationale = "Keeps you informed about active background tasks, system alarms, and scheduled reminders.",
                status = if (hasNotif) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasNotif,
                statusLabel = if (hasNotif) "Enabled" else "Disabled",
                isRequiredForCore = true,
                requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                requiredPermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            ),

            // 3. NOTIFICATION ACCESS (Needs Setup)
            DeviceCapability(
                id = "notif_access",
                name = "Notification Intelligence Access",
                category = "Automation & Intelligence",
                description = "Reads incoming notifications to announce sender and content hands-free.",
                rationale = "Enables REXXY to announce incoming WhatsApp messages, SMS, and important alerts when your phone is in pocket or dock.",
                status = when {
                    !hasNotifAccess -> CapabilityStatus.NEEDS_SETUP
                    !isNotifListenerConnected -> CapabilityStatus.NEEDS_SETUP
                    else -> CapabilityStatus.AVAILABLE
                },
                isGranted = hasNotifAccess && isNotifListenerConnected,
                statusLabel = when {
                    !hasNotifAccess -> "Needs Setup"
                    !isNotifListenerConnected -> "Connecting..."
                    else -> "Active"
                },
                requiresNotificationAccess = true
            ),

            // 4. ACCESSIBILITY SERVICE (Needs Setup)
            DeviceCapability(
                id = "accessibility_nav",
                name = "Accessibility Automation Service",
                category = "Automation & Intelligence",
                description = "Automated UI navigation, screen element reading, and action execution.",
                rationale = "Allows REXXY to navigate inside installed apps (e.g. playing songs on YouTube, sending WhatsApp messages) upon voice command.",
                status = if (isA11y) CapabilityStatus.AVAILABLE else CapabilityStatus.NEEDS_SETUP,
                isGranted = isA11y,
                statusLabel = if (isA11y) "Enabled" else "Needs Setup",
                requiresAccessibility = true
            ),

            // 5. PHONE / CALL
            DeviceCapability(
                id = "phone_call",
                name = "Phone & Calling",
                category = "Communications",
                description = "Direct or dialer phone calls and incoming call announcements.",
                rationale = "Allows voice commands such as 'Call John' or 'Call 911' after security confirmation, and identifies who is calling you.",
                status = if (hasPhoneCapability) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasPhoneCapability,
                statusLabel = if (hasPhoneCapability) "Granted" else "Not Granted",
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.CALL_PHONE,
                requiresConfirmation = true
            ),

            // 6. CONTACTS
            DeviceCapability(
                id = "contacts",
                name = "Contacts & Caller ID",
                category = "Communications",
                description = "Resolves contact names and phone numbers for calling and messaging.",
                rationale = "Enables REXXY to match names in your address book when you say 'Call Sarah' or 'Message Dad on WhatsApp'.",
                status = if (hasContacts) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasContacts,
                statusLabel = if (hasContacts) "Granted" else "Not Granted",
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.READ_CONTACTS
            ),

            // 7. SMS
            DeviceCapability(
                id = "sms",
                name = "SMS & Messaging",
                category = "Communications",
                description = "Sends text messages directly upon voice confirmation.",
                rationale = "Allows voice dictation of SMS messages to your contacts. Always requires confirmation before sending.",
                status = if (hasSms) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasSms,
                statusLabel = if (hasSms) "Granted" else "Not Granted",
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.SEND_SMS
            ),

            // 8. CAMERA & FLASHLIGHT
            DeviceCapability(
                id = "camera",
                name = "Camera & Flashlight",
                category = "Hardware & Sensors",
                description = "Controls torch flashlight and opens camera for photo capture.",
                rationale = "Allows instant voice commands like 'Turn on flashlight' or 'Take a photo' using camera hardware.",
                status = if (hasCamera) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasCamera,
                statusLabel = if (hasCamera) "Granted" else "Not Granted",
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.CAMERA
            ),

            // 9. BLUETOOTH & NEARBY DEVICES
            DeviceCapability(
                id = "bluetooth",
                name = "Bluetooth & Audio Accessories",
                category = "Hardware & Sensors",
                description = "Discovers and manages paired Bluetooth headsets and speakers.",
                rationale = "Enables routing of voice replies and microphone listening through your connected Bluetooth audio devices.",
                status = if (hasBluetooth) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                isGranted = hasBluetooth,
                statusLabel = if (hasBluetooth) "Granted" else "Not Granted",
                requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                requiredPermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
            ),

            // 10. SYSTEM SETTINGS (Modify System Settings)
            DeviceCapability(
                id = "write_settings",
                name = "System Settings (Brightness)",
                category = "System Controls",
                description = "Direct programmatic screen brightness and display adjustment.",
                rationale = "Allows REXXY to dynamically adjust brightness on command without interrupting your active workflow.",
                status = if (canWriteSettings) CapabilityStatus.AVAILABLE else CapabilityStatus.NEEDS_SETUP,
                isGranted = canWriteSettings,
                statusLabel = if (canWriteSettings) "Enabled" else "Needs Setup",
                requiresWriteSettings = true
            ),

            // 11. BACKGROUND BATTERY SETUP
            DeviceCapability(
                id = "battery_opt",
                name = "Background Battery Optimization",
                category = "System Controls",
                description = "Prevents Android OS from killing the background wake-word listener.",
                rationale = "Ensures REXXY stays alive in the background to respond to 'Hello Rex' when your screen is locked or idle.",
                status = if (isBatteryExempt) CapabilityStatus.AVAILABLE else CapabilityStatus.NEEDS_SETUP,
                isGranted = isBatteryExempt,
                statusLabel = if (isBatteryExempt) "Enabled" else "Needs Setup",
                requiresBatteryOptimization = true
            )
        )
    }

    /**
     * Checks if all required core capabilities are armed.
     * Core functionality requires at minimum Microphone and Notifications.
     */
    fun areCoreCapabilitiesArmed(context: Context): Boolean {
        val caps = getCapabilities(context)
        return caps.filter { it.isRequiredForCore }.all { it.isGranted }
    }

    /**
     * Total count of capabilities currently active.
     */
    fun getActiveCapabilityCount(context: Context): Pair<Int, Int> {
        val caps = getCapabilities(context)
        val active = caps.count { it.isGranted }
        return Pair(active, caps.size)
    }
}
