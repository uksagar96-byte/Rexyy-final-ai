package com.rexyy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rexyy.app.MainActivity
import com.rexyy.app.R
import com.rexyy.app.notifications.NotificationSpeechCoordinator
import com.rexyy.app.notifications.RexyyNotificationListenerService
import com.rexyy.app.pill.DynamicPillManager
import com.rexyy.app.pill.DynamicPillOverlayManager
import com.rexyy.app.pill.RexyyPillState
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import com.rexyy.app.voice.VoiceTtsManager
import com.rexyy.app.voice.WakeWordDetector
import com.rexyy.app.voice.WakeWordListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RexyyAssistantServiceState {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _currentState = MutableStateFlow(WakeWordState.WAKE_STANDBY)
    val currentState: StateFlow<WakeWordState> = _currentState.asStateFlow()

    private val _lastRecognizedCommand = MutableStateFlow("")
    val lastRecognizedCommand: StateFlow<String> = _lastRecognizedCommand.asStateFlow()

    private val _lastExecutionFeedback = MutableStateFlow("")
    val lastExecutionFeedback: StateFlow<String> = _lastExecutionFeedback.asStateFlow()

    fun updateRunning(running: Boolean) {
        _serviceRunning.value = running
        if (!running) {
            _currentState.value = WakeWordState.WAKE_STANDBY
        }
    }

    fun updateState(state: WakeWordState) {
        _currentState.value = state
    }

    fun updateCommand(cmd: String) {
        _lastRecognizedCommand.value = cmd
    }

    fun updateFeedback(feedback: String) {
        _lastExecutionFeedback.value = feedback
    }
}

class RexyyBackgroundAssistantService : Service(), WakeWordListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeWordDetector: WakeWordDetector? = null
    private var ttsManager: VoiceTtsManager? = null

    private var isDestroyed = false

    private val notificationSpeechListener: (Boolean) -> Unit = { isSpeaking ->
        if (isSpeaking) {
            wakeWordDetector?.pauseForSpeaking()
        } else {
            if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value &&
                RexyyAssistantServiceState.currentState.value == WakeWordState.WAKE_STANDBY
            ) {
                wakeWordDetector?.resumeAfterSpeaking()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "rexyy_background_assistant_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_SERVICE = "com.rexyy.app.action.STOP_ASSISTANT_SERVICE"
        const val ACTION_START_SERVICE = "com.rexyy.app.action.START_ASSISTANT_SERVICE"

        @Volatile
        private var activeServiceInstance: java.lang.ref.WeakReference<RexyyBackgroundAssistantService>? = null

        fun getActiveServiceInstanceId(): String? {
            return activeServiceInstance?.get()?.serviceInstanceId
        }
    }

    val serviceInstanceId: String by lazy { "Service#${System.identityHashCode(this)}" }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Duplicate service instance check and prevention (Section 10 Requirement)
        val existing = activeServiceInstance?.get()
        if (existing != null && existing != this) {
            android.util.Log.w("RexyyService", "Duplicate service detected! Stopping stale instance ${existing.serviceInstanceId}")
            try {
                existing.stopSelf()
            } catch (_: Exception) {}
        }
        activeServiceInstance = java.lang.ref.WeakReference(this)

        BackgroundListeningDiagnostics.recordServiceCreated(serviceInstanceId)
        BackgroundListeningDiagnostics.logEvent(
            "DEVICE_TELEMETRY",
            detail = "Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL}, Android: ${Build.VERSION.RELEASE}, API: ${Build.VERSION.SDK_INT}",
            component = "RexyyBackgroundAssistantService",
            serviceInstanceId = serviceInstanceId
        )
        createNotificationChannel()
        startForegroundWithMicrophone()
        BackgroundListeningDiagnostics.recordServiceForeground(serviceInstanceId)

        ttsManager = VoiceTtsManager(this) { isSpeaking ->
            if (isSpeaking) {
                wakeWordDetector?.pauseForSpeaking()
            }
        }

        BackgroundListeningDiagnostics.recordWakeEngineCreated()
        wakeWordDetector = WakeWordDetector.create(this).apply {
            setListener(this@RexyyBackgroundAssistantService)
        }

        NotificationSpeechCoordinator.registerListener(notificationSpeechListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BackgroundListeningDiagnostics.recordServiceStarted(serviceInstanceId)
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithMicrophone()
        RexyyAssistantServiceState.updateRunning(true)

        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        BackgroundListeningDiagnostics.recordMicRequested()
        if (!hasMic) {
            syncState(WakeWordState.MICROPHONE_DISABLED)
            DynamicPillManager.postMicrophoneDisabled()
            updateNotification("REXYY Paused", "Microphone permission required")
        } else {
            syncState(WakeWordState.WAKE_STANDBY)
            DynamicPillManager.postReconnecting()
            updateNotification("REXYY Active", "Starting voice engine...")
        }

        if (DynamicPillOverlayManager.canDrawOverlay(this)) {
            DynamicPillOverlayManager.showOverlay(this)
        }

        if (hasMic) {
            // Only start standby if not already listening
            val currentListening = wakeWordDetector?.isListening() ?: false
            if (!currentListening) {
                BackgroundListeningDiagnostics.recordWakeEngineStarted()
                wakeWordDetector?.startStandby()
            }
        }

        // Ensure notification listener service is recovered/rebound if user granted access
        try {
            if (RexyyNotificationListenerService.isNotificationAccessEnabled(this)) {
                RexyyNotificationListenerService.rebindIfNecessary(this)
            }
        } catch (_: Exception) {}

        return START_STICKY
    }

    private fun startForegroundWithMicrophone() {
        val notification = buildForegroundNotification("REXYY Active", "Starting voice engine...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "REXYY Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps REXXY voice assistant listening for wake-word in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RexyyBackgroundAssistantService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Assistant", pStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(title, content))
    }

    private fun syncState(state: WakeWordState) {
        RexyyAssistantServiceState.updateState(state)
        wakeWordDetector?.transitionState(state)
    }

    // WakeWordListener implementation

    override fun onWakeWordDetected(phrase: String, inlineCommand: String?) {
        syncState(WakeWordState.WAKE_DETECTED)
        DynamicPillManager.postWakeDetected(phrase)

        if (!inlineCommand.isNullOrBlank()) {
            // Wake word and command spoken in one breath: "Hello REXXY Rahul ko call karo"
            syncState(WakeWordState.COMMAND_RECOGNIZED)
            DynamicPillManager.postCommandRecognized(inlineCommand)
            executeCommandFromBackground(inlineCommand)
        } else {
            // Wake word alone: prompt user and transition to COMMAND_LISTENING
            updateNotification("REXYY Listening...", "Speak your command now")
            syncState(WakeWordState.RESPONDING)
            DynamicPillManager.postResponding("Yes, bolo.")
            wakeWordDetector?.pauseForSpeaking()
            ttsManager?.speak("Yes, bolo.") {
                // When "Yes, bolo." finishes speaking, enter full command listening
                mainHandler.post {
                    if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
                        syncState(WakeWordState.COMMAND_LISTENING)
                        DynamicPillManager.postCommandListening()
                        wakeWordDetector?.startCommandListening()
                    }
                }
            }
        }
    }

    override fun onCommandRecognized(command: String) {
        syncState(WakeWordState.COMMAND_RECOGNIZED)
        DynamicPillManager.postCommandRecognized(command)
        executeCommandFromBackground(command)
    }

    override fun onPartialCommandRecognized(partialText: String) {
        DynamicPillManager.postLiveSpeech(partialText)
    }

    override fun onCommandTimeout() {
        syncState(WakeWordState.RETURNING_TO_STANDBY)
        syncState(WakeWordState.WAKE_STANDBY)
        DynamicPillManager.postWakeStandby()
        updateNotification("REXYY Active", "Listening for \"Hello REXXY\"...")
        wakeWordDetector?.startStandby()
    }

    override fun onMicReady() {
        if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value &&
            RexyyAssistantServiceState.currentState.value == WakeWordState.WAKE_STANDBY) {
            BackgroundListeningDiagnostics.recordMicActive()
            DynamicPillManager.postWakeStandby()
            updateNotification("REXYY Active", "Listening for \"Hello REXXY\"...")
        }
    }

    override fun onMicSessionFailed(errorCode: Int, message: String) {
        if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
            BackgroundListeningDiagnostics.recordMicReleased()
            DynamicPillManager.postMicrophoneUnavailable()
            updateNotification("REXYY Paused", "Microphone unavailable")
        }
    }

    override fun onError(errorCode: Int, message: String) {
        // Handled with bounded recovery inside WakeWordDetector
        BackgroundListeningDiagnostics.recordWakeEngineError("Code: $errorCode, $message")
        if (RexyyAssistantServiceState.currentState.value == WakeWordState.WAKE_STANDBY) {
            DynamicPillManager.postReconnecting()
            updateNotification("REXYY Active", "Reconnecting voice engine...")
        } else {
            syncState(WakeWordState.ERROR)
            DynamicPillManager.postError(message)
        }
    }

    override fun onStopInterrupt() {
        stopSpeakingAndCancel()
    }

    private fun stopSpeakingAndCancel() {
        ttsManager?.stop()
        RexyyAssistantServiceState.updateFeedback("Stopped.")
        syncState(WakeWordState.RETURNING_TO_STANDBY)
        syncState(WakeWordState.WAKE_STANDBY)
        DynamicPillManager.postWakeStandby()
        updateNotification("REXYY Active", "Listening for \"Hello REXXY\"...")
        wakeWordDetector?.startStandby()
    }

    private fun executeCommandFromBackground(commandText: String) {
        RexyyAssistantServiceState.updateCommand(commandText)
        syncState(WakeWordState.PROCESSING)
        DynamicPillManager.postProcessing(commandText)
        updateNotification("REXYY Processing...", commandText)

        serviceScope.launch {
            try {
                syncState(WakeWordState.EXECUTING)
                DynamicPillManager.postExecuting("Executing: $commandText")
                val command = RexyyCommandRouter.route(commandText)
                val result = VoiceCommandExecutor.execute(command, this@RexyyBackgroundAssistantService)

                syncState(WakeWordState.VERIFYING)
                DynamicPillManager.postVerifying("Verifying: $commandText")

                val replyText = when (result) {
                    is VoiceCommandResult.Handled -> {
                        result.replyText
                    }
                    is VoiceCommandResult.Error -> {
                        result.errorMessage
                    }
                    is VoiceCommandResult.RequiresConfirmation -> {
                        result.prompt
                    }
                    is VoiceCommandResult.CollectMessageInput -> {
                        result.prompt
                    }
                    is VoiceCommandResult.ForwardToAi -> {
                        "Sir, ${result.prompt} ke liye AI stream activate kar raha hoon."
                    }
                }

                RexyyAssistantServiceState.updateFeedback(replyText)
                updateNotification("REXYY Done", replyText)
                speakFeedbackAndResume(replyText, isError = result is VoiceCommandResult.Error)
            } catch (e: Exception) {
                val err = "Command failed: ${e.localizedMessage ?: "Unknown error"}"
                RexyyAssistantServiceState.updateFeedback(err)
                speakFeedbackAndResume(err, isError = true)
            }
        }
    }

    private fun speakFeedbackAndResume(text: String, isError: Boolean = false) {
        syncState(WakeWordState.RESPONDING)
        if (isError) {
            DynamicPillManager.postError(text)
        } else {
            DynamicPillManager.postResponding(text)
        }
        wakeWordDetector?.pauseForSpeaking()
        ttsManager?.speak(text) {
            mainHandler.post {
                if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
                    syncState(WakeWordState.RETURNING_TO_STANDBY)
                    syncState(WakeWordState.WAKE_STANDBY)
                    DynamicPillManager.postWakeStandby()
                    updateNotification("REXYY Active", "Listening for \"Hello REXXY\"...")
                    wakeWordDetector?.resumeAfterSpeaking()
                }
            }
        }
    }

    override fun onDestroy() {
        BackgroundListeningDiagnostics.recordServiceDestroyed(serviceInstanceId)
        if (activeServiceInstance?.get() == this) {
            activeServiceInstance = null
        }
        isDestroyed = true
        NotificationSpeechCoordinator.unregisterListener(notificationSpeechListener)
        DynamicPillManager.onServiceStopped()
        DynamicPillOverlayManager.hideOverlay()
        RexyyAssistantServiceState.updateRunning(false)
        syncState(WakeWordState.WAKE_STANDBY)

        serviceScope.cancel()

        wakeWordDetector?.stop()
        wakeWordDetector?.destroy()
        wakeWordDetector = null

        try {
            ttsManager?.shutdown()
            ttsManager = null
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
