package com.rexyy.app.ui.bridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.notifications.RexyyNotificationListenerService
import com.rexyy.app.pill.DynamicPillOverlayManager
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.service.BackgroundAssistantManager
import com.rexyy.app.service.BackgroundListeningDiagnostics
import com.rexyy.app.service.RexyyAssistantServiceState
import com.rexyy.app.voice.VoiceCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Kotlin implementation of the authoritative RexyyCoreController and its child interfaces.
 * Provides clean interoperability between Java UI components and the Kotlin Core services.
 */
class RexyyCoreControllerImpl(
    private val appContext: Context
) : RexyyCoreController,
    RexyyUiStateProvider,
    RexyyCommandController,
    RexyyPermissionController,
    RexyyBackgroundStateProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = CopyOnWriteArrayList<RexyyUiStateListener>()

    init {
        // Collect background state changes and dispatch to Java UI listeners
        scope.launch {
            RexyyAssistantServiceState.currentState.collect { state ->
                notifyStateChanged(state.name)
            }
        }
        scope.launch {
            RexyyAssistantServiceState.serviceRunning.collect { isRunning ->
                for (listener in listeners) {
                    listener.onServiceRunningChanged(isRunning)
                }
            }
        }
    }

    private fun notifyStateChanged(stateName: String) {
        val lastCmd = RexyyAssistantServiceState.lastRecognizedCommand.value
        val feedback = RexyyAssistantServiceState.lastExecutionFeedback.value
        for (listener in listeners) {
            listener.onStateChanged(stateName, lastCmd, feedback)
        }
    }

    // RexyyCoreController implementation
    override fun startAssistant(context: Context): Boolean {
        return BackgroundAssistantManager.startAssistant(context)
    }

    override fun stopAssistant(context: Context) {
        BackgroundAssistantManager.stopAssistant(context)
    }

    override fun isAssistantRunning(): Boolean {
        return BackgroundAssistantManager.isServiceRunning()
    }

    override fun executeCommand(command: String, isVoice: Boolean) {
        scope.launch {
            val routed = RexyyCommandRouter.route(command)
            VoiceCommandExecutor.execute(routed, appContext)
        }
    }

    override fun activateRexyy() {
        val storage = com.rexyy.app.data.local.SecureStorage(appContext)
        storage.setRexyyActivated(true)
    }

    override fun requestOverlay(context: Context) {
        DynamicPillOverlayManager.showOverlay(context)
    }

    override fun dismissOverlay() {
        DynamicPillOverlayManager.hideOverlay()
    }

    override fun getUiStateProvider(): RexyyUiStateProvider = this
    override fun getCommandController(): RexyyCommandController = this
    override fun getPermissionController(): RexyyPermissionController = this
    override fun getBackgroundStateProvider(): RexyyBackgroundStateProvider = this

    // RexyyUiStateProvider implementation
    override fun getAssistantStateName(): String {
        return RexyyAssistantServiceState.currentState.value.name
    }

    override fun getLastSpokenCommand(): String {
        return RexyyAssistantServiceState.lastRecognizedCommand.value
    }

    override fun getLastExecutionFeedback(): String {
        return RexyyAssistantServiceState.lastExecutionFeedback.value
    }

    override fun isRexyyActivated(): Boolean {
        val storage = com.rexyy.app.data.local.SecureStorage(appContext)
        return storage.isRexyyActivated()
    }

    override fun addStateListener(listener: RexyyUiStateListener?) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    override fun removeStateListener(listener: RexyyUiStateListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    // RexyyCommandController implementation
    override fun dispatchCommand(commandText: String) {
        if (commandText.isNotBlank()) {
            executeCommand(commandText, isVoice = false)
        }
    }

    override fun dispatchVoiceToggle() {
        // Toggle voice listening through BackgroundAssistantManager or service intent
        if (isAssistantRunning()) {
            val intent = Intent(appContext, com.rexyy.app.service.RexyyBackgroundAssistantService::class.java).apply {
                action = com.rexyy.app.service.RexyyBackgroundAssistantService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } else {
            startAssistant(appContext)
        }
    }

    private val confirmationManager = com.rexyy.app.confirmation.ConfirmationManager()

    override fun cancelActiveTask() {
        // Safe task cancel signal
    }

    override fun confirmPendingAction() {
        confirmationManager.confirmPending()
    }

    override fun cancelPendingAction() {
        confirmationManager.cancelPending()
    }

    // RexyyPermissionController implementation
    override fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasOverlayPermission(context: Context): Boolean {
        return DynamicPillOverlayManager.canDrawOverlay(context)
    }

    override fun hasAccessibilityPermission(context: Context): Boolean {
        return RexyyAccessibilityService.isAccessibilityServiceConfigured(context)
    }

    override fun hasNotificationAccess(context: Context): Boolean {
        return RexyyNotificationListenerService.isNotificationAccessEnabled(context)
    }

    override fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun openNotificationSettings(context: Context) {
        RexyyNotificationListenerService.openNotificationAccessSettings(context)
    }

    // RexyyBackgroundStateProvider implementation
    override fun isServiceRunning(): Boolean {
        return RexyyAssistantServiceState.serviceRunning.value
    }

    override fun getCurrentListeningState(): String {
        return BackgroundListeningDiagnostics.currentListeningState.value.name
    }

    override fun getActiveRecognizersCount(): Int {
        return BackgroundListeningDiagnostics.activeRecognizersCount.value
    }

    override fun getRecoveryAttempts(): Int {
        return BackgroundListeningDiagnostics.recoveryAttempts.value
    }

    override fun getLastErrorDescription(): String? {
        return BackgroundListeningDiagnostics.lastErrorDescription.value
    }

    companion object {
        @Volatile
        private var instance: RexyyCoreControllerImpl? = null

        @JvmStatic
        fun getInstance(context: Context): RexyyCoreControllerImpl {
            return instance ?: synchronized(this) {
                instance ?: RexyyCoreControllerImpl(context.applicationContext).also { instance = it }
            }
        }
    }
}
