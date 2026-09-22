package com.rexyy.app

import android.app.Application
import com.rexyy.app.device.ChargingMonitor

class RexyyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ChargingMonitor.start(this)
        } catch (_: Exception) {}
    }
}
