package com.rexyy.app.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.telecom.NotificationAccessStatus
import com.rexyy.app.telecom.Phase7DiagnosticManager
import com.rexyy.app.utils.AppLanguage
import com.rexyy.app.utils.RexyyLanguageManager
import com.rexyy.app.voice.VoiceTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    val timestamp: Long = System.currentTimeMillis(),
    val isSensitive: Boolean = false
)

class RexyyNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ttsManager: VoiceTtsManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        ttsManager = VoiceTtsManager(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        _connectedTimestamp.value = System.currentTimeMillis()
        _reconnectAttempts.value = 0

        Phase7DiagnosticManager.updateNotificationState(
            status = NotificationAccessStatus.ACCESS_ACTIVE,
            source = "System",
            event = "Notification listener connected",
            reconnectAttempts = 0,
            outcome = "Ready"
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerConnected.value = false

        Phase7DiagnosticManager.updateNotificationState(
            status = NotificationAccessStatus.ACCESS_INACTIVE,
            source = "System",
            event = "Notification listener disconnected",
            error = "Listener disconnected by Android OS",
            outcome = "Disconnected"
        )

        // Attempt a single bounded recovery rebind if user has enabled access
        if (isNotificationAccessEnabled(this)) {
            serviceScope.launch {
                delay(2000L)
                if (!_isListenerConnected.value && isNotificationAccessEnabled(this@RexyyNotificationListenerService)) {
                    rebindIfNecessary(this@RexyyNotificationListenerService)
                }
            }
        }
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

        val storage = SecureStorage(this)
        val lang = RexyyLanguageManager.parseLanguage(storage.getVoiceLanguage())

        // Privacy sanitization: masks OTPs, PINs, passwords, and banking details
        val sanitized = NotificationPrivacyFilter.sanitize(appName, title, text, lang)

        val eventKey = "${pkg}_${title.hashCode()}_${text.take(30).hashCode()}"
        val now = System.currentTimeMillis()

        // Deduplication & staleness check: prevents re-announcing pre-reboot notifications
        if (!NotificationDeduplicator.shouldAnnounce(eventKey, sbn.postTime, _connectedTimestamp.value, now)) {
            Phase7DiagnosticManager.updateNotificationState(
                status = NotificationAccessStatus.ACCESS_ACTIVE,
                source = appName,
                event = sanitized.diagnosticSummary,
                outcome = "Deduplicated/Stale (Skipped)"
            )
            return
        }

        val captured = CapturedNotification(
            id = "${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            title = sanitized.sanitizedTitle,
            text = sanitized.sanitizedText,
            timestamp = now,
            isSensitive = sanitized.isSensitive
        )

        // Update notification history (using sanitized entries to protect private data)
        val currentList = _recentNotifications.value.toMutableList()
        currentList.add(0, captured)
        if (currentList.size > 50) currentList.removeAt(currentList.lastIndex)
        _recentNotifications.value = currentList

        // Synchronize with Phase 7 Diagnostics (safe summary, no sensitive values)
        Phase7DiagnosticManager.updateNotificationState(
            status = NotificationAccessStatus.ACCESS_ACTIVE,
            source = appName,
            event = sanitized.diagnosticSummary,
            outcome = "Processing"
        )

        // Post contextual notification event to Dynamic Pill
        com.rexyy.app.pill.DynamicPillManager.postNotification(appName, sanitized.sanitizedTitle)

        // Announce notification if enabled in user settings
        if (storage.isNotificationAnnouncementEnabled()) {
            announceNotification(sanitized.speechAnnouncement, appName, eventKey, storage)
        } else {
            Phase7DiagnosticManager.updateNotificationState(
                status = NotificationAccessStatus.ACCESS_ACTIVE,
                source = appName,
                outcome = "Announcement disabled in settings"
            )
        }
    }

    private fun announceNotification(
        announcementText: String,
        appName: String,
        eventKey: String,
        storage: SecureStorage
    ) {
        val lang = storage.getVoiceLanguage()

        serviceScope.launch {
            // Pause wake word microphone in background service to avoid microphone picking up own voice
            NotificationSpeechCoordinator.onNotificationSpeechStarted()

            // Ensure TTS manager is ready
            var tts = ttsManager
            if (tts == null) {
                tts = VoiceTtsManager(this@RexyyNotificationListenerService)
                ttsManager = tts
                delay(800L)
            }

            var announcedSuccessfully = false

            try {
                tts.speak(announcementText, lang) {
                    announcedSuccessfully = true
                    NotificationDeduplicator.recordAnnouncement(eventKey)
                    NotificationSpeechCoordinator.onNotificationSpeechEnded()

                    Phase7DiagnosticManager.updateNotificationState(
                        status = NotificationAccessStatus.ACCESS_ACTIVE,
                        source = appName,
                        outcome = "Announced successfully"
                    )
                }

                // Safety timeout to resume wake-word microphone if TTS callback hangs
                delay(6000L)
                if (NotificationSpeechCoordinator.isSpeakingNotification.value) {
                    NotificationSpeechCoordinator.onNotificationSpeechEnded()
                }
            } catch (e: Exception) {
                NotificationSpeechCoordinator.onNotificationSpeechEnded()
                Phase7DiagnosticManager.updateNotificationState(
                    status = NotificationAccessStatus.ACCESS_ACTIVE,
                    source = appName,
                    error = "TTS announcement failed: ${e.localizedMessage}",
                    outcome = "Failed (TTS error)"
                )
            }
        }
    }

    override fun onDestroy() {
        _isListenerConnected.value = false
        if (instance?.get() == this) instance = null
        NotificationSpeechCoordinator.onNotificationSpeechEnded()
        serviceScope.cancel()
        ttsManager?.shutdown()
        ttsManager = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RexyyNotificationListener"

        private var instance: WeakReference<RexyyNotificationListenerService>? = null

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _connectedTimestamp = MutableStateFlow(0L)
        val connectedTimestamp: StateFlow<Long> = _connectedTimestamp.asStateFlow()

        private val _reconnectAttempts = MutableStateFlow(0)
        val reconnectAttempts: StateFlow<Int> = _reconnectAttempts.asStateFlow()

        private var lastRebindTimestamp = 0L
        private const val MIN_REBIND_INTERVAL_MS = 2500L
        private const val MAX_CONSECUTIVE_REBIND_ATTEMPTS = 3

        private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()

        /**
         * Checks if the user granted Notification Access in Android Settings.
         */
        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        /**
         * Returns true only if permission is granted in Settings AND the listener is actively connected.
         */
        fun isListenerActive(context: Context): Boolean {
            return isNotificationAccessEnabled(context) && _isListenerConnected.value
        }

        /**
         * Real status reporting: reports ACTIVE only if listener is genuinely connected.
         */
        fun getNotificationAccessStatus(context: Context): NotificationAccessStatus {
            return if (isNotificationAccessEnabled(context)) {
                if (_isListenerConnected.value) {
                    NotificationAccessStatus.ACCESS_ACTIVE
                } else {
                    NotificationAccessStatus.ACCESS_INACTIVE
                }
            } else {
                NotificationAccessStatus.ACCESS_INACTIVE
            }
        }

        /**
         * Safe bounded recovery/rebind method to reconnect NotificationListenerService
         * after a device reboot, app restart, or OS disconnect.
         */
        fun rebindIfNecessary(context: Context, force: Boolean = false): Boolean {
            if (!isNotificationAccessEnabled(context)) {
                Phase7DiagnosticManager.updateNotificationState(
                    status = NotificationAccessStatus.ACCESS_INACTIVE,
                    error = "Notification Access not granted in system Settings",
                    outcome = "Access Disabled"
                )
                return false
            }

            if (_isListenerConnected.value && !force) {
                return true
            }

            val now = System.currentTimeMillis()
            if (!force && (now - lastRebindTimestamp) < MIN_REBIND_INTERVAL_MS) {
                return false
            }

            if (!force && _reconnectAttempts.value >= MAX_CONSECUTIVE_REBIND_ATTEMPTS) {
                Phase7DiagnosticManager.updateNotificationState(
                    status = NotificationAccessStatus.ACCESS_INACTIVE,
                    error = "Rebind attempts capped. Open Settings or tap Rebind to refresh.",
                    outcome = "Rebind Capped"
                )
                return false
            }

            lastRebindTimestamp = now
            val attempt = _reconnectAttempts.value + 1
            _reconnectAttempts.value = attempt

            Phase7DiagnosticManager.updateNotificationState(
                status = NotificationAccessStatus.ACCESS_INACTIVE,
                event = "Rebind attempt #$attempt initiated",
                reconnectAttempts = attempt,
                outcome = "Rebinding..."
            )

            val component = ComponentName(context, RexyyNotificationListenerService::class.java)

            // Step 1: Standard Android Nougat+ API requestRebind
            var requestedApiRebind = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(component)
                    requestedApiRebind = true
                } catch (e: Exception) {
                    Log.w(TAG, "requestRebind failed: ${e.message}")
                }
            }

            // Step 2: Unstick Android NotificationManagerService on reboot / OEM desynchronization
            // Toggling the component setting forces NMS's package monitor to re-evaluate and rebind.
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !requestedApiRebind) {
                    requestRebind(component)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Component toggle rebind failed: ${e.message}")
            }

            return true
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        fun simulateConnectionForTesting(connected: Boolean) {
            _isListenerConnected.value = connected
            if (connected) {
                _connectedTimestamp.value = System.currentTimeMillis()
                _reconnectAttempts.value = 0
            }
        }
    }
}
