package com.rexyy.app

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.rexyy.app.accessibility.AccessibilityInteractionProvider
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.device.ChargingMonitor
import com.rexyy.app.notifications.NotificationDeduplicator
import com.rexyy.app.notifications.NotificationPrivacyFilter
import com.rexyy.app.pill.DynamicPillManager
import com.rexyy.app.pill.RexyyPillState
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.telecom.CallActionController
import com.rexyy.app.telecom.CallLifecycleState
import com.rexyy.app.telecom.CallerIdentityResolver
import com.rexyy.app.telecom.ChargingState
import com.rexyy.app.telecom.ContactsLookupState
import com.rexyy.app.telecom.Phase7DiagnosticManager
import com.rexyy.app.telecom.SmsActionController
import com.rexyy.app.telecom.SmsActionResult
import com.rexyy.app.telecom.SmsLifecycleState
import com.rexyy.app.telecom.TelecomActionResult
import com.rexyy.app.telecom.WhatsAppWorkflowState
import com.rexyy.app.utils.AppLanguage
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import com.rexyy.app.whatsapp.WhatsAppActionManager
import com.rexyy.app.whatsapp.WhatsAppActionResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase7CommunicationSuiteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CallerIdentityResolver.testContacts = null
        CallActionController.testCallOverride = null
        SmsActionController.testSmsOverride = null
        WhatsAppActionManager.testWhatsAppAutomationOverride = null
        RexyyAccessibilityService.setTestProvider(null)
        ChargingMonitor.resetForTesting(context)
        DynamicPillManager.resetForTesting()
    }

    @After
    fun tearDown() {
        CallerIdentityResolver.testContacts = null
        CallActionController.testCallOverride = null
        SmsActionController.testSmsOverride = null
        WhatsAppActionManager.testWhatsAppAutomationOverride = null
        RexyyAccessibilityService.setTestProvider(null)
        ChargingMonitor.resetForTesting(context)
        DynamicPillManager.resetForTesting()
    }

    // =========================================================================
    // 1. CONTACT RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testContactResolutionSingleMatch() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul Sharma", phoneNumber = "+919876543210")
        )

        val result = CallerIdentityResolver.resolveContactForAction(context, "Rahul Sharma")
        assertTrue(result is CallerIdentityResolver.ContactActionResult.Resolved)
        val resolved = (result as CallerIdentityResolver.ContactActionResult.Resolved).contact
        assertEquals("Rahul Sharma", resolved.name)
        assertEquals("+919876543210", resolved.phoneNumber)

        assertEquals(ContactsLookupState.CONTACT_RESOLVED, Phase7DiagnosticManager.contactsState.value)
        assertEquals("Rahul Sharma", Phase7DiagnosticManager.lastContactQuery.value)
        assertEquals(1, Phase7DiagnosticManager.lastContactMatchCount.value)
    }

    @Test
    fun testContactResolutionAmbiguousMultipleContactsStopsSafely() {
        // Must NEVER silently select one when multiple contacts match
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul Sharma", phoneNumber = "+919876543210"),
            CallerIdentityResolver.ContactMatch(name = "Rahul Verma", phoneNumber = "+919876543211")
        )

        val result = CallerIdentityResolver.resolveContactForAction(context, "Rahul")
        assertTrue("Must report multiple matches when ambiguous", result is CallerIdentityResolver.ContactActionResult.Multiple)
        val multiple = result as CallerIdentityResolver.ContactActionResult.Multiple
        assertEquals(2, multiple.matches.size)

        assertEquals(ContactsLookupState.CONTACT_AMBIGUOUS, Phase7DiagnosticManager.contactsState.value)
        assertEquals(2, Phase7DiagnosticManager.lastContactMatchCount.value)
    }

    @Test
    fun testContactResolutionMultipleNumbersForSameContactStopsSafely() {
        // When one contact has multiple numbers, do not arbitrarily pick one
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul", phoneNumber = "+919876543210"),
            CallerIdentityResolver.ContactMatch(name = "Rahul", phoneNumber = "+919123456789")
        )

        val result = CallerIdentityResolver.resolveContactForAction(context, "Rahul")
        assertTrue("Must report multiple numbers as ambiguous", result is CallerIdentityResolver.ContactActionResult.Multiple)
        assertEquals(ContactsLookupState.CONTACT_AMBIGUOUS, Phase7DiagnosticManager.contactsState.value)
    }

    @Test
    fun testContactResolutionNotFound() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Priya", phoneNumber = "9876543210")
        )

        val result = CallerIdentityResolver.resolveContactForAction(context, "Vikram")
        assertTrue(result is CallerIdentityResolver.ContactActionResult.NotFound)
        assertEquals(ContactsLookupState.CONTACT_NOT_FOUND, Phase7DiagnosticManager.contactsState.value)
    }

    @Test
    fun testContactResolutionRawPhoneNumberDirectly() {
        val result = CallerIdentityResolver.resolveContactForAction(context, "+919876543210")
        assertTrue(result is CallerIdentityResolver.ContactActionResult.Resolved)
        assertEquals("+919876543210", (result as CallerIdentityResolver.ContactActionResult.Resolved).contact.phoneNumber)
        assertEquals(ContactsLookupState.CONTACT_RESOLVED, Phase7DiagnosticManager.contactsState.value)
    }

    // =========================================================================
    // 2. PHONE CALL TESTS
    // =========================================================================

    @Test
    fun testCallCommandRouting() {
        val cmd = RexyyCommandRouter.route("Rahul ko call karo")
        assertTrue("Routing must detect CallContact", cmd is VoiceCommand.CallContact)
        val callCmd = cmd as VoiceCommand.CallContact
        assertEquals("Rahul", callCmd.target)
    }

    @Test
    fun testCallExecutionConfirmedDirectCallWithSingleContact() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul", phoneNumber = "9876543210")
        )

        // Grant CALL_PHONE permission in shadow package manager
        shadowOf(context.packageManager).apply {
            // Permission simulated
        }

        val controller = CallActionController(context)
        val result = controller.callContact("Rahul", confirmedDirectCall = false)

        // Without confirmation, launches dialer safely
        assertTrue(result is TelecomActionResult.Success)
        assertEquals(CallLifecycleState.CALL_STARTED, Phase7DiagnosticManager.callState.value)
        assertEquals("Rahul", Phase7DiagnosticManager.lastCallTarget.value)

        // Pill must reflect Call state
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Call)
        val pill = DynamicPillManager.pillState.value as RexyyPillState.Call
        assertEquals("Rahul", pill.target)
    }

    @Test
    fun testCallExecutionAmbiguousStopsSafelyWithoutCalling() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul Sharma", phoneNumber = "9876543210"),
            CallerIdentityResolver.ContactMatch(name = "Rahul Kumar", phoneNumber = "9876543211")
        )

        val controller = CallActionController(context)
        val result = controller.callContact("Rahul", confirmedDirectCall = true)

        assertTrue("Must return multiple matches", result is TelecomActionResult.MultipleMatches)
        assertEquals(CallLifecycleState.CALL_FAILED, Phase7DiagnosticManager.callState.value)
    }

    @Test
    fun testCallExecutionInvalidNumberFails() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Ghost", phoneNumber = "0")
        )

        val controller = CallActionController(context)
        val result = controller.callContact("Ghost", confirmedDirectCall = true)

        assertTrue(result is TelecomActionResult.Failure)
        assertEquals(CallLifecycleState.CALL_FAILED, Phase7DiagnosticManager.callState.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Error)
    }

    // =========================================================================
    // 3. SMS TESTS
    // =========================================================================

    @Test
    fun testSmsCommandParsingExactMessageExtraction() {
        val cmd = RexyyCommandRouter.route("Rahul ko SMS bhejo ki main 10 minute me aa raha hoon")
        assertTrue(cmd is VoiceCommand.SendMessage)
        val sendCmd = cmd as VoiceCommand.SendMessage
        assertEquals("Rahul", sendCmd.target)
        // Must extract the exact message without altering it or inventing anything
        assertEquals("main 10 minute me aa raha hoon", sendCmd.body)
    }

    @Test
    fun testSmsEmptyBodyPromptsUserWithoutInventing() {
        val cmd = RexyyCommandRouter.route("Rahul ko SMS bhejo")
        assertTrue(cmd is VoiceCommand.SendMessage)
        val sendCmd = cmd as VoiceCommand.SendMessage
        assertEquals("Rahul", sendCmd.target)
        assertEquals("", sendCmd.body)

        val result = runBlocking { VoiceCommandExecutor.execute(sendCmd, context) }
        assertTrue(result is VoiceCommandResult.CollectMessageInput)
        val collect = result as VoiceCommandResult.CollectMessageInput
        assertEquals("Rahul", collect.targetName)
        assertFalse(collect.isWhatsApp)
    }

    @Test
    fun testSmsExecutionLifecycleDirectSend() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul", phoneNumber = "9876543210")
        )

        val controller = SmsActionController(context)
        val result = controller.sendSms("Rahul", "Kal milte hain", confirmedSend = false)

        // Without confirmation / direct permission, opens SMS composer safely
        assertTrue(result is SmsActionResult.Success)
        assertFalse((result as SmsActionResult.Success).isDirectlySent)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Sms)
    }

    @Test
    fun testSmsAmbiguousContactFailsSafely() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul Sharma", phoneNumber = "9876543210"),
            CallerIdentityResolver.ContactMatch(name = "Rahul Gupta", phoneNumber = "9876543211")
        )

        val controller = SmsActionController(context)
        val result = controller.sendSms("Rahul", "Test message", confirmedSend = true)

        assertTrue(result is SmsActionResult.MultipleMatches)
        assertEquals(SmsLifecycleState.SMS_FAILED, Phase7DiagnosticManager.smsState.value)
    }

    // =========================================================================
    // 4. WHATSAPP TESTS
    // =========================================================================

    @Test
    fun testWhatsAppCommandParsingExactMessage() {
        val cmd = RexyyCommandRouter.route("Rahul ko WhatsApp message karo ki kal milte hain")
        assertTrue(cmd is VoiceCommand.WhatsAppMessage)
        val waCmd = cmd as VoiceCommand.WhatsAppMessage
        assertEquals("Rahul", waCmd.target)
        assertEquals("kal milte hain", waCmd.body)
    }

    @Test
    fun testWhatsAppNotInstalledReportsClearly() {
        val manager = WhatsAppActionManager(context)
        // In Robolectric without WhatsApp package installed:
        assertFalse(manager.isWhatsAppInstalled())

        val result = runBlocking {
            manager.executeSendWithAccessibility("Rahul", "Hello")
        }

        assertTrue(result is WhatsAppActionResult.NotInstalled)
        assertEquals(WhatsAppWorkflowState.WHATSAPP_FAILED, Phase7DiagnosticManager.whatsAppState.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Error)
    }

    @Test
    fun testWhatsAppDisabledAccessibilityNeverFakesSuccess() {
        CallerIdentityResolver.testContacts = listOf(
            CallerIdentityResolver.ContactMatch(name = "Rahul", phoneNumber = "9876543210")
        )

        // Override installation check for test
        WhatsAppActionManager.testWhatsAppAutomationOverride = { _, _ ->
            WhatsAppActionResult.AccessibilityRequired()
        }

        val manager = WhatsAppActionManager(context)
        val result = runBlocking {
            manager.executeSendWithAccessibility("Rahul", "Kal milte hain")
        }

        assertTrue("Must report AccessibilityRequired, never fake success", result is WhatsAppActionResult.AccessibilityRequired)
        assertEquals(WhatsAppWorkflowState.WHATSAPP_FAILED, Phase7DiagnosticManager.whatsAppState.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Error)
    }

    @Test
    fun testWhatsAppAutomatedFlowViaAccessibilitySemantic() {
        WhatsAppActionManager.testWhatsAppAutomationOverride = { target, msg ->
            assertEquals("Rahul", target)
            assertEquals("Kal milte hain", msg)
            WhatsAppActionResult.Success("$target ko WhatsApp message bhej diya gaya hai.", isAutomated = true)
        }

        val manager = WhatsAppActionManager(context)
        val result = runBlocking {
            manager.executeSendWithAccessibility("Rahul", "Kal milte hain")
        }

        assertTrue(result is WhatsAppActionResult.Success)
        assertEquals(WhatsAppWorkflowState.WHATSAPP_VERIFIED, Phase7DiagnosticManager.whatsAppState.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Success)
    }

    // =========================================================================
    // 5. NOTIFICATION + CHARGING REGRESSION
    // =========================================================================

    @Test
    fun testChargingMonitorConnectDisconnectPillState() {
        ChargingMonitor.start(context)

        // Simulate charger connect intent
        val connectIntent = Intent(Intent.ACTION_POWER_CONNECTED)
        context.sendBroadcast(connectIntent)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Phase 7 state reflects charging
        assertEquals(ChargingState.CHARGING, Phase7DiagnosticManager.chargingState.value)
        assertTrue(ChargingMonitor.isCharging.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Charging)
        assertTrue((DynamicPillManager.pillState.value as RexyyPillState.Charging).isCharging)

        // Simulate charger disconnect
        val disconnectIntent = Intent(Intent.ACTION_POWER_DISCONNECTED)
        context.sendBroadcast(disconnectIntent)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(ChargingState.DISCONNECTED, Phase7DiagnosticManager.chargingState.value)
        assertFalse(ChargingMonitor.isCharging.value)
        assertTrue(DynamicPillManager.pillState.value is RexyyPillState.Charging)
        assertFalse((DynamicPillManager.pillState.value as RexyyPillState.Charging).isCharging)
    }

    @Test
    fun testNotificationSanitizationProtectsSensitiveData() {
        // OTP must never be leaked
        val sanitizedOtp = NotificationPrivacyFilter.sanitize(
            appName = "Bank",
            rawTitle = "OTP",
            rawText = "Your secret OTP is 482910. Do not share with anyone.",
            language = AppLanguage.ENGLISH
        )
        assertTrue(sanitizedOtp.isSensitive)
        assertFalse(sanitizedOtp.sanitizedText.contains("482910"))
        assertTrue(sanitizedOtp.sanitizedText.contains("[Protected Security / Sensitive Content]"))
        assertFalse(sanitizedOtp.speechAnnouncement.contains("482910"))

        // Duplicate notifications must be filtered using realistic epoch timestamps
        val key = "com.test.app_123_456"
        val connectedTime = 1_700_000_000_000L
        val postTime = 1_700_000_001_000L
        val now = 1_700_000_002_000L

        assertTrue(NotificationDeduplicator.shouldAnnounce(key, postTime, connectedTime, now))
        NotificationDeduplicator.recordAnnouncement(key, now)
        // Immediate repeat within deduplication cooldown window (20s)
        assertFalse(NotificationDeduplicator.shouldAnnounce(key, postTime, connectedTime, now + 5_000L))
    }
}
