package com.rexyy.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BackgroundAssistantManager {

    fun isServiceRunning(): Boolean {
        return RexyyAssistantServiceState.serviceRunning.value
    }

    fun canStartService(context: Context): Boolean {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasMic && hasNotification
    }

    fun startAssistant(context: Context): Boolean {
        return try {
            val intent = Intent(context, RexyyBackgroundAssistantService::class.java).apply {
                action = RexyyBackgroundAssistantService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stopAssistant(context: Context) {
        try {
            val intent = Intent(context, RexyyBackgroundAssistantService::class.java).apply {
                action = RexyyBackgroundAssistantService.ACTION_STOP_SERVICE
            }
            context.stopService(intent)
        } catch (_: Exception) {}
        RexyyAssistantServiceState.updateRunning(false)
    }

    fun toggleAssistant(context: Context): Boolean {
        return if (isServiceRunning()) {
            stopAssistant(context)
            false
        } else {
            startAssistant(context)
        }
    }
}
