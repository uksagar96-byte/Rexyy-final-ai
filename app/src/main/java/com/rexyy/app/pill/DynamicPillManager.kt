package com.rexyy.app.pill

import android.content.Context
import com.rexyy.app.service.BackgroundAssistantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object DynamicPillManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reversionJob: Job? = null

    // Base state to return to after transient success, error or contextual events
    @Volatile
    private var baseState: RexyyPillState = RexyyPillState.WakeStandby

    private val _pillState = MutableStateFlow<RexyyPillState>(RexyyPillState.BackgroundActive)
    val pillState: StateFlow<RexyyPillState> = _pillState.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    /**
     * Updates the base operational state (e.g. WAKE_STANDBY, BACKGROUND_ACTIVE).
     */
    fun setBaseState(state: RexyyPillState) {
        baseState = state
        postState(state)
    }

    /**
     * Posts a new direct state to the Dynamic Pill.
     */
    fun postState(state: RexyyPillState) {
        reversionJob?.cancel()
        reversionJob = null
        _pillState.value = state
        _isExpanded.value = state.isExpanded
    }

    /**
     * Posts a transient state (such as Success, Error, or a Contextual alert)
     * which automatically reverts to the base standby state after [durationMs].
     */
    fun postTransientState(state: RexyyPillState, durationMs: Long = 2500L) {
        reversionJob?.cancel()
        _pillState.value = state
        _isExpanded.value = state.isExpanded

        reversionJob = managerScope.launch {
            delay(durationMs)
            returnToStandby()
        }
    }

    // Helper convenience functions mapped to assistant lifecycle

    fun postBackgroundActive() {
        baseState = RexyyPillState.BackgroundActive
        postState(RexyyPillState.BackgroundActive)
    }

    fun postWakeStandby() {
        baseState = RexyyPillState.WakeStandby
        postState(RexyyPillState.WakeStandby)
    }

    fun postWakeDetected(phrase: String = "Hello Rex") {
        postState(RexyyPillState.WakeDetected(phrase))
    }

    fun postCommandListening() {
        postState(RexyyPillState.CommandListening)
    }

    fun postCommandRecognized(command: String) {
        postState(RexyyPillState.CommandRecognized(command))
    }

    fun postProcessing(command: String) {
        postState(RexyyPillState.Processing(command))
    }

    fun postExecuting(action: String) {
        postState(RexyyPillState.Executing(action))
    }

    fun postVerifying(action: String) {
        postState(RexyyPillState.Verifying(action))
    }

    /**
     * MUST be called ONLY after real execution and verification report success.
     */
    fun postSuccess(message: String, autoRevertMs: Long = 2500L) {
        postTransientState(RexyyPillState.Success(message), autoRevertMs)
    }

    /**
     * MUST be called when real execution or recognition encounters an error.
     */
    fun postError(errorMessage: String, autoRevertMs: Long = 3000L) {
        postTransientState(RexyyPillState.Error(errorMessage), autoRevertMs)
    }

    // Real Contextual events

    fun postNotification(appName: String, summary: String) {
        postTransientState(RexyyPillState.Notification(appName, summary), 3000L)
    }

    fun postCharging(isCharging: Boolean, pct: Int) {
        postTransientState(RexyyPillState.Charging(isCharging, pct), 3000L)
    }

    fun postCall(target: String, statusText: String) {
        postTransientState(RexyyPillState.Call(target, statusText), 3500L)
    }

    fun postSms(recipient: String, statusText: String) {
        postTransientState(RexyyPillState.Sms(recipient, statusText), 3000L)
    }

    fun postWhatsApp(target: String, statusText: String) {
        postTransientState(RexyyPillState.WhatsAppMessage(target, statusText), 3000L)
    }

    fun postNetwork(isConnected: Boolean) {
        postTransientState(RexyyPillState.Network(isConnected), 3000L)
    }

    fun postMicrophoneDisabled() {
        postState(RexyyPillState.MicrophoneDisabled)
    }

    fun postSystemForeground() {
        baseState = RexyyPillState.SystemForeground
        postState(RexyyPillState.SystemForeground)
    }

    fun returnToStandby() {
        reversionJob?.cancel()
        reversionJob = null
        _pillState.value = baseState
        _isExpanded.value = baseState.isExpanded
    }

    fun onServiceStopped() {
        reversionJob?.cancel()
        reversionJob = null
        baseState = RexyyPillState.SystemForeground
        _pillState.value = RexyyPillState.SystemForeground
        _isExpanded.value = false
    }

    fun resetForTesting() {
        reversionJob?.cancel()
        reversionJob = null
        baseState = RexyyPillState.WakeStandby
        _pillState.value = RexyyPillState.WakeStandby
        _isExpanded.value = false
    }
}
