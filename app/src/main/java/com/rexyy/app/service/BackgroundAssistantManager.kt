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
        // RECORD_AUDIO is the essential permission for background voice service
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
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
