package com.rexyy.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rexyy.app.MainActivity
import com.rexyy.app.R
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import com.rexyy.app.voice.VoiceTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistantBackgroundState {
    INACTIVE,
    IDLE,
    BACKGROUND_ACTIVE,
    WAKE_DETECTED,
    LISTENING,
    UNDERSTANDING,
    EXECUTING,
    SPEAKING,
    ERROR
}

object RexyyAssistantServiceState {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _currentState = MutableStateFlow(AssistantBackgroundState.INACTIVE)
    val currentState: StateFlow<AssistantBackgroundState> = _currentState.asStateFlow()

    private val _lastRecognizedCommand = MutableStateFlow("")
    val lastRecognizedCommand: StateFlow<String> = _lastRecognizedCommand.asStateFlow()

    private val _lastExecutionFeedback = MutableStateFlow("")
    val lastExecutionFeedback: StateFlow<String> = _lastExecutionFeedback.asStateFlow()

    fun updateRunning(running: Boolean) {
        _serviceRunning.value = running
        if (!running) _currentState.value = AssistantBackgroundState.INACTIVE
    }

    fun updateState(state: AssistantBackgroundState) {
        _currentState.value = state
    }

    fun updateCommand(cmd: String) {
        _lastRecognizedCommand.value = cmd
    }

    fun updateFeedback(feedback: String) {
        _lastExecutionFeedback.value = feedback
    }
}

class RexyyBackgroundAssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsManager: VoiceTtsManager? = null
    private var toneGenerator: ToneGenerator? = null

    private var isContinuousListening = false
    private var isDestroyed = false
    private var restartJob: Job? = null

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
                RexyyAssistantServiceState.updateState(AssistantBackgroundState.SPEAKING)
            } else {
                if (isContinuousListening && !isDestroyed) {
                    RexyyAssistantServiceState.updateState(AssistantBackgroundState.BACKGROUND_ACTIVE)
                    scheduleNextListening(400L)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithMicrophone()
        RexyyAssistantServiceState.updateRunning(true)
        RexyyAssistantServiceState.updateState(AssistantBackgroundState.BACKGROUND_ACTIVE)

        isContinuousListening = true
        startSpeechListening()

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
                description = "Keeps REXYY voice assistant listening for wake-word in background"
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

    private fun startSpeechListening() {
        if (isDestroyed || !isContinuousListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            RexyyAssistantServiceState.updateState(AssistantBackgroundState.ERROR)
            return
        }

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                }
                speechRecognizer?.setRecognitionListener(createListener())

                val storage = SecureStorage(this)
                val lang = storage.getVoiceLanguage()
                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    when (lang) {
                        SecureStorage.VOICE_LANG_HI -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                        }
                        SecureStorage.VOICE_LANG_EN -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN"))
                        }
                        else -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "hi-IN"))
                        }
                    }
                }

                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                scheduleNextListening(1000L)
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // If recognizer had timeout or no match, gracefully restart listening
                if (isContinuousListening && !isDestroyed) {
                    val delayMs = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
                        SpeechRecognizer.ERROR_NETWORK -> 1500L
                        else -> 400L
                    }
                    scheduleNextListening(delayMs)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    handleRecognizedSpeech(text)
                } else if (isContinuousListening && !isDestroyed) {
                    scheduleNextListening(300L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()?.trim()?.lowercase() ?: ""
                if (isInterruptPhrase(partialText)) {
                    stopSpeakingAndCancel()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun isInterruptPhrase(text: String): Boolean {
        return text == "stop" || text == "ruko" || text == "ruk ja" || text == "bas" ||
                text == "cancel" || text == "chup" || text == "chup raho" || text == "shant"
    }

    private fun stopSpeakingAndCancel() {
        ttsManager?.stop()
        speechRecognizer?.cancel()
        RexyyAssistantServiceState.updateFeedback("Stopped.")
        RexyyAssistantServiceState.updateState(AssistantBackgroundState.BACKGROUND_ACTIVE)
        scheduleNextListening(500L)
    }

    private fun handleRecognizedSpeech(rawSpeech: String) {
        val lower = rawSpeech.lowercase().trim()

        // 1. Check for immediate stop interrupts
        if (isInterruptPhrase(lower)) {
            stopSpeakingAndCancel()
            return
        }

        // 2. Check current state
        val currentState = RexyyAssistantServiceState.currentState.value

        // Check if wake word is present
        val isWakeWord = lower.startsWith("hello rex") || lower.startsWith("hey rex") ||
                lower.startsWith("hello rexyy") || lower.startsWith("hey rexyy") ||
                lower.startsWith("ok rex") || lower.startsWith("hi rex") ||
                lower == "rex" || lower == "rexyy"

        if (isWakeWord || currentState == AssistantBackgroundState.LISTENING) {
            RexyyAssistantServiceState.updateState(AssistantBackgroundState.WAKE_DETECTED)
            playCueTone()

            // Check if command is embedded directly with wake word (e.g. "Hello Rex YouTube pe cricket search karo")
            val strippedCommand = rawSpeech.replace("(?i)^(hello|hey|hi|ok)?\\s*(rex|rexyy)\\s*".toRegex(), "").trim()

            if (strippedCommand.isNotBlank()) {
                executeCommandFromBackground(strippedCommand)
            } else {
                // Wake word alone -> prompt user and enter LISTENING state
                RexyyAssistantServiceState.updateState(AssistantBackgroundState.LISTENING)
                updateNotification("REXYY Listening...", "Speak your command now")
                ttsManager?.speak("Yes, bolo.")
            }
        } else {
            // If in continuous background and not explicit wake word, check if it's a direct command
            // or loop back to listening
            if (isContinuousListening && !isDestroyed) {
                scheduleNextListening(300L)
            }
        }
    }

    private fun playCueTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (_: Exception) {}
    }

    private fun executeCommandFromBackground(commandText: String) {
        RexyyAssistantServiceState.updateCommand(commandText)
        RexyyAssistantServiceState.updateState(AssistantBackgroundState.UNDERSTANDING)
        updateNotification("REXYY Processing...", commandText)

        serviceScope.launch {
            try {
                RexyyAssistantServiceState.updateState(AssistantBackgroundState.EXECUTING)
                val command = RexyyCommandRouter.route(commandText)
                val result = VoiceCommandExecutor.execute(command, this@RexyyBackgroundAssistantService)

                when (result) {
                    is VoiceCommandResult.Handled -> {
                        RexyyAssistantServiceState.updateFeedback(result.replyText)
                        updateNotification("REXYY Done", result.replyText)
                        speakFeedbackAndResume(result.replyText)
                    }
                    is VoiceCommandResult.Error -> {
                        RexyyAssistantServiceState.updateFeedback(result.errorMessage)
                        updateNotification("REXYY", result.errorMessage)
                        speakFeedbackAndResume(result.errorMessage)
                    }
                    is VoiceCommandResult.RequiresConfirmation -> {
                        RexyyAssistantServiceState.updateFeedback(result.prompt)
                        speakFeedbackAndResume(result.prompt)
                    }
                    is VoiceCommandResult.CollectMessageInput -> {
                        RexyyAssistantServiceState.updateFeedback(result.prompt)
                        speakFeedbackAndResume(result.prompt)
                    }
                    is VoiceCommandResult.ForwardToAi -> {
                        val message = "Forwarding query: ${result.prompt}"
                        RexyyAssistantServiceState.updateFeedback(message)
                        speakFeedbackAndResume("Sir, ${result.prompt} ke liye AI stream activate kar raha hoon.")
                    }
                }
            } catch (e: Exception) {
                val err = "Command failed: ${e.localizedMessage ?: "Unknown error"}"
                RexyyAssistantServiceState.updateFeedback(err)
                speakFeedbackAndResume(err)
            }
        }
    }

    private fun speakFeedbackAndResume(text: String) {
        RexyyAssistantServiceState.updateState(AssistantBackgroundState.SPEAKING)
        ttsManager?.speak(text)
    }

    private fun scheduleNextListening(delayMs: Long) {
        restartJob?.cancel()
        if (isDestroyed || !isContinuousListening) return

        restartJob = serviceScope.launch {
            delay(delayMs)
            if (isContinuousListening && !isDestroyed) {
                RexyyAssistantServiceState.updateState(AssistantBackgroundState.BACKGROUND_ACTIVE)
                updateNotification("REXYY Active", "Listening for \"Hello Rex\"...")
                startSpeechListening()
            }
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        isContinuousListening = false
        restartJob?.cancel()
        serviceScope.cancel()

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        try {
            ttsManager?.shutdown()
            ttsManager = null
        } catch (_: Exception) {}

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}

        RexyyAssistantServiceState.updateRunning(false)
        RexyyAssistantServiceState.updateState(AssistantBackgroundState.INACTIVE)
        super.onDestroy()
    }
}
