package com.rexyy.app

import android.app.Application
import com.rexyy.app.device.ChargingMonitor
import com.rexyy.app.notifications.RexyyNotificationListenerService

class RexyyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ChargingMonitor.start(this)
        } catch (_: Exception) {}

        try {
            if (RexyyNotificationListenerService.isNotificationAccessEnabled(this)) {
                RexyyNotificationListenerService.rebindIfNecessary(this)
            }
        } catch (_: Exception) {}
    }
}
