package com.rexyy.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.utils.AppLanguage
import com.rexyy.app.utils.RexyyLanguageManager
import com.rexyy.app.voice.VoiceTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ChargingMonitor {

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ttsManager: VoiceTtsManager? = null
    private var receiver: BroadcastReceiver? = null
    private var isRegistered = false

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private var lastAnnouncedState: Boolean? = null
    private var lastAnnouncementTime = 0L

    fun start(context: Context) {
        if (isRegistered) return

        if (ttsManager == null) {
            ttsManager = VoiceTtsManager(context.applicationContext)
        }

        // Initialize current state from system
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val currentlyCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        _isCharging.value = currentlyCharging
        lastAnnouncedState = currentlyCharging

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> handlePowerEvent(true, ctx)
                    Intent.ACTION_POWER_DISCONNECTED -> handlePowerEvent(false, ctx)
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val currStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val charging = currStatus == BatteryManager.BATTERY_STATUS_CHARGING || currStatus == BatteryManager.BATTERY_STATUS_FULL
                        _isCharging.value = charging
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        try {
            context.applicationContext.registerReceiver(receiver, filter)
            isRegistered = true
        } catch (_: Exception) {}
    }

    private fun handlePowerEvent(connected: Boolean, context: Context?) {
        _isCharging.value = connected
        val now = System.currentTimeMillis()

        // Deduplication: do not announce if state hasn't changed or if called within 3 seconds
        if (lastAnnouncedState == connected && now - lastAnnouncementTime < 3000L) return
        lastAnnouncedState = connected
        lastAnnouncementTime = now

        val targetContext = context ?: return
        val storage = SecureStorage(targetContext)
        if (!storage.isChargingAnnouncementEnabled()) return

        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())
        val announcement = if (connected) {
            when (lang) {
                AppLanguage.HINDI -> "REXXY: चार्जिंग शुरू हो गई है।"
                AppLanguage.ENGLISH -> "REXXY: Charging started."
                AppLanguage.HINGLISH -> "REXXY: Charging shuru ho gayi hai."
            }
        } else {
            when (lang) {
                AppLanguage.HINDI -> "REXXY: चार्जर डिसकनेक्ट हो गया है।"
                AppLanguage.ENGLISH -> "REXXY: Charging disconnected."
                AppLanguage.HINGLISH -> "REXXY: Charging disconnect ho gaya."
            }
        }

        monitorScope.launch {
            ttsManager?.speak(announcement)
        }
    }

    fun stop(context: Context) {
        if (!isRegistered) return
        try {
            receiver?.let { context.applicationContext.unregisterReceiver(it) }
            receiver = null
            isRegistered = false
        } catch (_: Exception) {}
    }
}
