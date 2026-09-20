package com.rexyy.app.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
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
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getCapabilities(context: Context): List<DeviceCapability> {
        val hasMic = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        val hasContacts = hasPermission(context, Manifest.permission.READ_CONTACTS)
        val hasPhoneState = hasPermission(context, Manifest.permission.READ_PHONE_STATE)
        val hasCall = hasPermission(context, Manifest.permission.CALL_PHONE)
        val hasCamera = hasPermission(context, Manifest.permission.CAMERA)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        val isRooted = isRootAvailable()
        val isA11y = isAccessibilityEnabled(context)

        return listOf(
            DeviceCapability(
                id = "mic",
                name = "Microphone & Speech",
                description = "Speech recognition and voice-driven assistant commands",
                status = if (hasMic) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.RECORD_AUDIO
            ),
            DeviceCapability(
                id = "contacts",
                name = "Contacts & Caller ID",
                description = "Resolves contacts for calls, WhatsApp messaging, and incoming call announcements",
                status = if (hasContacts) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.READ_CONTACTS
            ),
            DeviceCapability(
                id = "phone_state",
                name = "Call Detection & Control",
                description = "Detects incoming calls, announces callers, and manages call actions",
                status = if (hasPhoneState) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.READ_PHONE_STATE
            ),
            DeviceCapability(
                id = "camera",
                name = "Camera & Capture",
                description = "Opens camera and captures images upon assistant command",
                status = if (hasCamera) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.CAMERA
            ),
            DeviceCapability(
                id = "notif",
                name = "Notifications",
                description = "Status updates, task completion alerts, and reminders",
                status = if (hasNotif) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                requiredPermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            ),
            DeviceCapability(
                id = "app_launch",
                name = "Dynamic App Launcher",
                description = "Discovers and launches all installed apps, YouTube, WhatsApp, and browser",
                status = CapabilityStatus.AVAILABLE
            ),
            DeviceCapability(
                id = "settings_control",
                name = "System Settings & Alarms",
                description = "Open device settings, Bluetooth, Wi-Fi, and set alarms or timers",
                status = CapabilityStatus.AVAILABLE
            ),
            DeviceCapability(
                id = "audio_volume",
                name = "Volume & Brightness",
                description = "Adjust media volume, mute, and display brightness via system services",
                status = CapabilityStatus.AVAILABLE
            ),
            DeviceCapability(
                id = "phone_call",
                name = "Phone & Calling",
                description = "Direct or dialer phone calls with user confirmation",
                status = if (hasCall) CapabilityStatus.AVAILABLE else CapabilityStatus.PERMISSION_REQUIRED,
                requiresPermission = true,
                requiredPermissionName = Manifest.permission.CALL_PHONE,
                requiresConfirmation = true
            ),
            DeviceCapability(
                id = "accessibility_nav",
                name = "Accessibility Service",
                description = "Assisted screen reading and automated UI navigation (requires user activation)",
                status = if (isA11y) CapabilityStatus.AVAILABLE else CapabilityStatus.ACCESSIBILITY_REQUIRED,
                requiresAccessibility = true
            ),
            DeviceCapability(
                id = "root_sys",
                name = "Root Capabilities",
                description = "Advanced system-level modifications (safely restricted; only active if device is rooted)",
                status = if (isRooted) CapabilityStatus.AVAILABLE else CapabilityStatus.ROOT_REQUIRED,
                requiresRoot = true,
                requiresConfirmation = true
            )
        )
    }
}
