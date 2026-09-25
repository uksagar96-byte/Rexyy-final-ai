package com.rexyy.app.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.utils.AppLanguage
import com.rexyy.app.utils.RexyyLanguageManager
import com.rexyy.app.voice.VoiceTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class RexyyNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ttsManager: VoiceTtsManager? = null

    // Deduplication map: key -> last announced timestamp
    private val recentAnnouncements = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        ttsManager = VoiceTtsManager(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        com.rexyy.app.telecom.Phase7DiagnosticManager.updateNotificationState(
            com.rexyy.app.telecom.NotificationAccessStatus.ACCESS_ACTIVE
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerConnected.value = false
        com.rexyy.app.telecom.Phase7DiagnosticManager.updateNotificationState(
            com.rexyy.app.telecom.NotificationAccessStatus.ACCESS_INACTIVE
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // Ignore our own ongoing foreground/background notifications
        if (pkg == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Skip ongoing / non-clearable / progress notifications
        val isOngoing = (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        val eventId = "${pkg}_${title}_${text.take(30)}"
        val now = System.currentTimeMillis()
        val lastTime = recentAnnouncements[eventId] ?: 0L

        // Deduplicate notifications received within 15 seconds
        if (now - lastTime < 15_000L) return
        recentAnnouncements[eventId] = now

        // Cleanup old cache entries
        if (recentAnnouncements.size > 100) {
            val cutoff = now - 60_000L
            recentAnnouncements.entries.removeAll { it.value < cutoff }
        }

        val captured = CapturedNotification(
            id = "${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = now
        )

        // Update notification history
        val currentList = _recentNotifications.value.toMutableList()
        currentList.add(0, captured)
        if (currentList.size > 50) currentList.removeAt(currentList.lastIndex)
        _recentNotifications.value = currentList

        // Synchronize with Phase 7 Diagnostics (masking full text for privacy)
        com.rexyy.app.telecom.Phase7DiagnosticManager.updateNotificationState(
            status = com.rexyy.app.telecom.NotificationAccessStatus.ACCESS_ACTIVE,
            source = appName,
            event = "Notification from $appName: ${title.take(30)}"
        )

        // Announce notification if enabled in user settings
        val storage = SecureStorage(this)
        if (storage.isNotificationAnnouncementEnabled()) {
            announceNotification(captured, storage)
        }
    }

    private fun announceNotification(captured: CapturedNotification, storage: SecureStorage) {
        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())
        val announcement = when (lang) {
            AppLanguage.HINDI -> {
                if (captured.title.isNotBlank() && captured.title != captured.appName) {
                    "${captured.appName} से ${captured.title} का नोटिफिकेशन आया है।"
                } else {
                    "${captured.appName} से नया नोटिफिकेशन आया है।"
                }
            }
            AppLanguage.ENGLISH -> {
                if (captured.title.isNotBlank() && captured.title != captured.appName) {
                    "Notification from ${captured.appName}: ${captured.title}."
                } else {
                    "New notification from ${captured.appName}."
                }
            }
            AppLanguage.HINGLISH -> {
                if (captured.title.isNotBlank() && captured.title != captured.appName) {
                    "${captured.appName} se ${captured.title} ka notification aaya hai."
                } else {
                    "${captured.appName} se new notification aaya hai."
                }
            }
        }

        serviceScope.launch {
            ttsManager?.speak(announcement)
        }
    }

    override fun onDestroy() {
        _isListenerConnected.value = false
        if (instance?.get() == this) instance = null
        serviceScope.cancel()
        ttsManager?.shutdown()
        ttsManager = null
        super.onDestroy()
    }

    companion object {
        private var instance: WeakReference<RexyyNotificationListenerService>? = null

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun getNotificationAccessStatus(context: Context): com.rexyy.app.telecom.NotificationAccessStatus {
            return if (isNotificationAccessEnabled(context)) {
                com.rexyy.app.telecom.NotificationAccessStatus.ACCESS_ACTIVE
            } else {
                com.rexyy.app.telecom.NotificationAccessStatus.ACCESS_INACTIVE
            }
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
