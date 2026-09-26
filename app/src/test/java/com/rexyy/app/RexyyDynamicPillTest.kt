package com.rexyy.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rexyy.app.pill.DynamicPillManager
import com.rexyy.app.pill.DynamicPillOverlayManager
import com.rexyy.app.pill.RexyyPillState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RexyyDynamicPillTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DynamicPillManager.resetForTesting()
        DynamicPillOverlayManager.hideOverlay()
    }

    @Test
    fun testA_ServiceActive_TransitionsToBackgroundActive() {
        DynamicPillManager.postBackgroundActive()
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.BackgroundActive)
        assertEquals("REXXY Active", state.title)
    }

    @Test
    fun testB_Standby_TransitionsToWakeStandby() {
        DynamicPillManager.postWakeStandby()
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.WakeStandby)
        assertEquals("REXXY Standby", state.title)
        assertEquals("Listening for \"Hello Rex\"", state.subtitle)
    }

    @Test
    fun testC_WakeDetected_ExpandsAndShowsListeningPrompt() {
        DynamicPillManager.postWakeDetected("Hello Rex")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.WakeDetected)
        assertEquals("Wake Detected", state.title)
        assertTrue(state.isExpanded)
        assertTrue(DynamicPillManager.isExpanded.value)
    }

    @Test
    fun testD_CommandListening_EntersListeningState() {
        DynamicPillManager.postCommandListening()
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.CommandListening)
        assertEquals("Listening...", state.title)
        assertEquals("Speak your command", state.subtitle)
    }

    @Test
    fun testE_RecognizedCommand_DisplaysRecognizedQuery() {
        DynamicPillManager.postCommandRecognized("Instagram kholo")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.CommandRecognized)
        assertEquals("Command Recognized", state.title)
        assertEquals("\"Instagram kholo\"", state.subtitle)
    }

    @Test
    fun testF_Processing_ShowsProcessingStatus() {
        DynamicPillManager.postProcessing("Instagram kholo")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Processing)
        assertEquals("Processing...", state.title)
        assertEquals("\"Instagram kholo\"", state.subtitle)
    }

    @Test
    fun testG_Execution_ShowsExecutingStatus() {
        DynamicPillManager.postExecuting("Opening Instagram...")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Executing)
        assertEquals("Executing...", state.title)
        assertEquals("Opening Instagram...", state.subtitle)
    }

    @Test
    fun testH_Verification_ShowsVerifyingStatus() {
        DynamicPillManager.postVerifying("Checking Instagram...")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Verifying)
        assertEquals("Verifying...", state.title)
        assertEquals("Checking Instagram...", state.subtitle)
    }

    @Test
    fun testI_VerifiedSuccess_ShowsSuccessOnlyAfterVerification() {
        DynamicPillManager.postSuccess("Instagram is open")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Success)
        assertTrue(state.isSuccess)
        assertEquals("Done", state.title)
        assertEquals("Instagram is open", state.subtitle)
    }

    @Test
    fun testJ_FailedVerification_ShowsErrorStatus() {
        DynamicPillManager.postError("Instagram couldn't be opened")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Error)
        assertTrue(state.isError)
        assertEquals("Error", state.title)
        assertEquals("Instagram couldn't be opened", state.subtitle)
    }

    @Test
    fun testK_SuccessReturnsToWakeStandby() = runTest {
        DynamicPillManager.postWakeStandby()
        DynamicPillManager.postSuccess("Instagram is open", autoRevertMs = 500L)

        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Success)

        // Advance past transient timeout
        advanceTimeBy(600L)
        DynamicPillManager.returnToStandby()

        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.WakeStandby)
    }

    @Test
    fun testL_MicrophonePermissionDisabled_ShowsMicrophoneDisabled() {
        DynamicPillManager.postMicrophoneDisabled()
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.MicrophoneDisabled)
        assertTrue(state.isError)
        assertEquals("Mic Disabled", state.title)
        assertEquals("Microphone access is disabled", state.subtitle)
    }

    @Test
    fun testM_NotificationEvent_ShowsContextualNotification() {
        DynamicPillManager.postNotification("WhatsApp", "Rahul: Hey!")
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Notification)
        assertEquals("Notification", state.title)
        assertTrue(state.subtitle.contains("WhatsApp"))
    }

    @Test
    fun testN_ChargingEvent_ShowsContextualCharging() {
        DynamicPillManager.postCharging(isCharging = true, pct = 85)
        val state = DynamicPillManager.pillState.value
        assertTrue(state is RexyyPillState.Charging)
        assertEquals("Charging", state.title)
        assertEquals("Charging started (85%)", state.subtitle)

        DynamicPillManager.postCharging(isCharging = false, pct = 84)
        val disconnectedState = DynamicPillManager.pillState.value as RexyyPillState.Charging
        assertEquals("Charging disconnected (84%)", disconnectedState.subtitle)
    }

    @Test
    fun testO_NoDuplicatePillInstances() {
        assertFalse(DynamicPillOverlayManager.isOverlayAttached())
        DynamicPillOverlayManager.hideOverlay()
        assertFalse(DynamicPillOverlayManager.isOverlayAttached())
    }

    @Test
    fun testP_NoStalePillStateAfterServiceRecreation() {
        DynamicPillManager.postCommandListening()
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.CommandListening)

        // Simulate service shutdown/recreation
        DynamicPillManager.onServiceStopped()
        assertFalse(DynamicPillManager.pillState.value is RexyyPillState.CommandListening)
        assertEquals(RexyyPillState.SystemForeground, DynamicPillManager.pillState.value)
        assertFalse(DynamicPillManager.isExpanded.value)
    }
}
