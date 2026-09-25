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
        val initialPct = calculateBatteryPercentage(batteryStatus)
        _isCharging.value = currentlyCharging
        lastAnnouncedState = currentlyCharging

        com.rexyy.app.telecom.Phase7DiagnosticManager.updateChargingState(
            state = if (currentlyCharging) com.rexyy.app.telecom.ChargingState.CHARGING else com.rexyy.app.telecom.ChargingState.DISCONNECTED,
            pct = initialPct,
            event = if (currentlyCharging) "Charger Connected ($initialPct%)" else "Charger Disconnected ($initialPct%)"
        )

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val pct = calculateBatteryPercentage(intent)
                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> handlePowerEvent(true, pct, ctx)
                    Intent.ACTION_POWER_DISCONNECTED -> handlePowerEvent(false, pct, ctx)
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val currStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val charging = currStatus == BatteryManager.BATTERY_STATUS_CHARGING || currStatus == BatteryManager.BATTERY_STATUS_FULL
                        _isCharging.value = charging
                        com.rexyy.app.telecom.Phase7DiagnosticManager.updateChargingState(
                            state = if (charging) com.rexyy.app.telecom.ChargingState.CHARGING else com.rexyy.app.telecom.ChargingState.DISCONNECTED,
                            pct = pct
                        )
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

    private fun handlePowerEvent(connected: Boolean, pct: Int, context: Context?) {
        _isCharging.value = connected
        val now = System.currentTimeMillis()

        // Update Phase 7 Diagnostics
        val eventLabel = if (connected) "Charger Connected ($pct%)" else "Charger Disconnected ($pct%)"
        com.rexyy.app.telecom.Phase7DiagnosticManager.updateChargingState(
            state = if (connected) com.rexyy.app.telecom.ChargingState.CHARGING else com.rexyy.app.telecom.ChargingState.DISCONNECTED,
            pct = pct,
            event = eventLabel
        )

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

    private fun calculateBatteryPercentage(intent: Intent?): Int {
        if (intent == null) return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
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
