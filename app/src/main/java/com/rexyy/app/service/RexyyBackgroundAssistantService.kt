package com.rexyy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rexyy.app.MainActivity
import com.rexyy.app.R
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
    private var toneGenerator: ToneGenerator? = null

    private var isDestroyed = false

    companion object {
        const val CHANNEL_ID = "rexyy_background_assistant_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_SERVICE = "com.rexyy.app.action.STOP_ASSISTANT_SERVICE"
        const val ACTION_START_SERVICE = "com.rexyy.app.action.START_ASSISTANT_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {
            null
        }

        ttsManager = VoiceTtsManager(this) { isSpeaking ->
            if (isSpeaking) {
                wakeWordDetector?.pauseForSpeaking()
                syncState(WakeWordState.RETURNING_TO_STANDBY)
            } else {
                if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
                    syncState(WakeWordState.WAKE_STANDBY)
                    wakeWordDetector?.resumeAfterSpeaking()
                }
            }
        }

        wakeWordDetector = WakeWordDetector.create(this).apply {
            setListener(this@RexyyBackgroundAssistantService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithMicrophone()
        RexyyAssistantServiceState.updateRunning(true)
        syncState(WakeWordState.WAKE_STANDBY)

        wakeWordDetector?.startStandby()

        return START_STICKY
    }

    private fun startForegroundWithMicrophone() {
        val notification = buildForegroundNotification("REXYY Active", "Listening for \"Hello Rex\"...")
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

    private fun playCueTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (_: Exception) {}
    }

    // WakeWordListener implementation

    override fun onWakeWordDetected(phrase: String, inlineCommand: String?) {
        playCueTone()
        syncState(WakeWordState.WAKE_DETECTED)

        if (!inlineCommand.isNullOrBlank()) {
            // Wake word and command spoken in one breath: "Hello Rex Rahul ko call karo"
            executeCommandFromBackground(inlineCommand)
        } else {
            // Wake word alone: prompt user and transition to COMMAND_LISTENING
            updateNotification("REXYY Listening...", "Speak your command now")
            ttsManager?.speak("Yes, bolo.") {
                // When "Yes, bolo." finishes speaking, enter full command listening
                mainHandler.post {
                    if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
                        syncState(WakeWordState.COMMAND_LISTENING)
                        wakeWordDetector?.startCommandListening()
                    }
                }
            }
        }
    }

    override fun onCommandRecognized(command: String) {
        executeCommandFromBackground(command)
    }

    override fun onCommandTimeout() {
        syncState(WakeWordState.RETURNING_TO_STANDBY)
        updateNotification("REXYY Active", "Listening for \"Hello Rex\"...")
        wakeWordDetector?.startStandby()
    }

    override fun onError(errorCode: Int, message: String) {
        // Handled with bounded recovery inside WakeWordDetector
        syncState(WakeWordState.ERROR)
    }

    override fun onStopInterrupt() {
        stopSpeakingAndCancel()
    }

    private fun stopSpeakingAndCancel() {
        ttsManager?.stop()
        RexyyAssistantServiceState.updateFeedback("Stopped.")
        syncState(WakeWordState.RETURNING_TO_STANDBY)
        updateNotification("REXYY Active", "Listening for \"Hello Rex\"...")
        wakeWordDetector?.startStandby()
    }

    private fun executeCommandFromBackground(commandText: String) {
        RexyyAssistantServiceState.updateCommand(commandText)
        syncState(WakeWordState.PROCESSING)
        updateNotification("REXYY Processing...", commandText)

        serviceScope.launch {
            try {
                syncState(WakeWordState.EXECUTING)
                val command = RexyyCommandRouter.route(commandText)
                val result = VoiceCommandExecutor.execute(command, this@RexyyBackgroundAssistantService)

                syncState(WakeWordState.VERIFYING)

                val replyText = when (result) {
                    is VoiceCommandResult.Handled -> result.replyText
                    is VoiceCommandResult.Error -> result.errorMessage
                    is VoiceCommandResult.RequiresConfirmation -> result.prompt
                    is VoiceCommandResult.CollectMessageInput -> result.prompt
                    is VoiceCommandResult.ForwardToAi -> "Sir, ${result.prompt} ke liye AI stream activate kar raha hoon."
                }

                RexyyAssistantServiceState.updateFeedback(replyText)
                updateNotification("REXYY Done", replyText)
                speakFeedbackAndResume(replyText)
            } catch (e: Exception) {
                val err = "Command failed: ${e.localizedMessage ?: "Unknown error"}"
                RexyyAssistantServiceState.updateFeedback(err)
                speakFeedbackAndResume(err)
            }
        }
    }

    private fun speakFeedbackAndResume(text: String) {
        syncState(WakeWordState.RETURNING_TO_STANDBY)
        wakeWordDetector?.pauseForSpeaking()
        ttsManager?.speak(text) {
            mainHandler.post {
                if (!isDestroyed && RexyyAssistantServiceState.serviceRunning.value) {
                    syncState(WakeWordState.WAKE_STANDBY)
                    updateNotification("REXYY Active", "Listening for \"Hello Rex\"...")
                    wakeWordDetector?.resumeAfterSpeaking()
                }
            }
        }
    }

    override fun onDestroy() {
        isDestroyed = true
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

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}

        super.onDestroy()
    }
}
