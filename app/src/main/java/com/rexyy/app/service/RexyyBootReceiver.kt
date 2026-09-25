package com.rexyy.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.device.ChargingMonitor
import com.rexyy.app.notifications.RexyyNotificationListenerService

class RexyyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // 1. Maintain charging connect/disconnect detection
            try {
                ChargingMonitor.start(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start ChargingMonitor on boot: ${e.message}")
            }

            // 2. Recover NotificationListenerService if access is granted in Settings
            try {
                if (RexyyNotificationListenerService.isNotificationAccessEnabled(context)) {
                    Log.i(TAG, "Rebinding NotificationListenerService on boot...")
                    RexyyNotificationListenerService.rebindIfNecessary(context, force = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to rebind NotificationListenerService on boot: ${e.message}")
            }

            // 3. Resume background assistant if activated by user
            try {
                val storage = SecureStorage(context)
                if (storage.isRexyyActivated() && BackgroundAssistantManager.canStartService(context)) {
                    Log.i(TAG, "Resuming RexyyBackgroundAssistantService on boot...")
                    BackgroundAssistantManager.startAssistant(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start background assistant on boot: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "RexyyBootReceiver"
    }
}
