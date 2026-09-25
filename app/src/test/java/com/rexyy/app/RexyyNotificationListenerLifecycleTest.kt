package com.rexyy.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.rexyy.app.notifications.NotificationDeduplicator
import com.rexyy.app.notifications.NotificationPrivacyFilter
import com.rexyy.app.notifications.NotificationSpeechCoordinator
import com.rexyy.app.notifications.RexyyNotificationListenerService
import com.rexyy.app.service.RexyyBootReceiver
import com.rexyy.app.telecom.NotificationAccessStatus
import com.rexyy.app.telecom.Phase7DiagnosticManager
import com.rexyy.app.utils.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RexyyNotificationListenerLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        NotificationDeduplicator.clearCacheForTesting()
        Phase7DiagnosticManager.resetForTesting()
        RexyyNotificationListenerService.simulateConnectionForTesting(false)
    }

    @Test
    fun testPrivacyFilterMasksSensitiveCodesAndFinancials() {
        // OTP in message
        val otpResult = NotificationPrivacyFilter.sanitize(
            appName = "Bank",
            rawTitle = "Debit Card Alert",
            rawText = "Your OTP is 839210 for transaction of Rs 5000",
            language = AppLanguage.ENGLISH
        )
        assertTrue(otpResult.isSensitive)
        assertFalse(otpResult.sanitizedText.contains("839210"))
        assertFalse(otpResult.speechAnnouncement.contains("839210"))
        assertEquals("Security alert from Bank.", otpResult.speechAnnouncement)
        assertTrue(otpResult.diagnosticSummary.contains("Masked for privacy"))

        // Hindi sensitive announcement
        val hindiResult = NotificationPrivacyFilter.sanitize(
            appName = "WhatsApp",
            rawTitle = "Verification code",
            rawText = "123456 is your WhatsApp code",
            language = AppLanguage.HINDI
        )
        assertTrue(hindiResult.isSensitive)
        assertFalse(hindiResult.speechAnnouncement.contains("123456"))
        assertEquals("WhatsApp से सिक्योरिटी अलर्ट आया है।", hindiResult.speechAnnouncement)

        // Non-sensitive normal message
        val normalResult = NotificationPrivacyFilter.sanitize(
            appName = "WhatsApp",
            rawTitle = "Rahul",
            rawText = "Hey, are you free for a call?",
            language = AppLanguage.ENGLISH
        )
        assertFalse(normalResult.isSensitive)
        assertEquals("Rahul", normalResult.sanitizedTitle)
        assertEquals("Hey, are you free for a call?", normalResult.sanitizedText)
        assertEquals("Notification from WhatsApp: Rahul.", normalResult.speechAnnouncement)
    }

    @Test
    fun testDeduplicatorBlocksDuplicatesWithinCooldown() {
        val now = 100_000L
        val eventKey = "com.whatsapp_12345_67890"

        // First arrival is allowed
        assertTrue(NotificationDeduplicator.shouldAnnounce(eventKey, now - 1000L, now - 5000L, now))
        NotificationDeduplicator.recordAnnouncement(eventKey, now)

        // Immediate duplicate (5s later) must be blocked
        assertFalse(NotificationDeduplicator.shouldAnnounce(eventKey, now + 5000L, now - 5000L, now + 5000L))

        // After cooldown (25s later) is allowed
        assertTrue(NotificationDeduplicator.shouldAnnounce(eventKey, now + 25_000L, now - 5000L, now + 25_000L))
    }

    @Test
    fun testDeduplicatorRejectsStalePreRebootNotifications() {
        val listenerConnectedAt = 1_700_000_000_000L
        val eventKey = "com.google.android.gm_email_1"

        // Notification posted 2 minutes before listener connected (e.g. before device reboot)
        val preRebootPostTime = listenerConnectedAt - 120_000L
        val now = listenerConnectedAt + 2000L

        assertFalse(
            "Pre-reboot notifications sitting in shade must not be announced",
            NotificationDeduplicator.shouldAnnounce(eventKey, preRebootPostTime, listenerConnectedAt, now)
        )

        // Notification older than 30s must also be rejected
        val oldPostTime = now - 35_000L
        assertFalse(
            "Notifications older than 30s must be dropped",
            NotificationDeduplicator.shouldAnnounce("fresh_key", oldPostTime, listenerConnectedAt, now)
        )
    }

    @Test
    fun testSpeechCoordinatorPausesAndResumesMicrophone() {
        var observedSpeakingState: Boolean? = null
        val listener: (Boolean) -> Unit = { observedSpeakingState = it }

        NotificationSpeechCoordinator.registerListener(listener)

        NotificationSpeechCoordinator.onNotificationSpeechStarted()
        assertTrue(NotificationSpeechCoordinator.isSpeakingNotification.value)
        assertEquals(true, observedSpeakingState)

        NotificationSpeechCoordinator.onNotificationSpeechEnded()
        assertFalse(NotificationSpeechCoordinator.isSpeakingNotification.value)
        assertEquals(false, observedSpeakingState)

        NotificationSpeechCoordinator.unregisterListener(listener)
    }

    @Test
    fun testConnectionStateReportingAndDiagnostics() {
        // Initially disconnected
        assertFalse(RexyyNotificationListenerService.isListenerConnected.value)

        // Simulate connection
        RexyyNotificationListenerService.simulateConnectionForTesting(true)
        assertTrue(RexyyNotificationListenerService.isListenerConnected.value)
        assertTrue(RexyyNotificationListenerService.connectedTimestamp.value > 0)

        // Simulate disconnection
        RexyyNotificationListenerService.simulateConnectionForTesting(false)
        assertFalse(RexyyNotificationListenerService.isListenerConnected.value)
    }

    @Test
    fun testBootReceiverHandlesBootCompletedGracefully() {
        val receiver = RexyyBootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Must run cleanly without throwing any exceptions
        receiver.onReceive(context, intent)
    }

    @Test
    fun testPhase7DiagnosticsTracksNotificationState() {
        Phase7DiagnosticManager.updateNotificationState(
            status = NotificationAccessStatus.ACCESS_ACTIVE,
            source = "WhatsApp",
            event = "Rahul: Message received",
            outcome = "Announced successfully"
        )

        assertEquals(NotificationAccessStatus.ACCESS_ACTIVE, Phase7DiagnosticManager.notificationAccessStatus.value)
        assertEquals("WhatsApp", Phase7DiagnosticManager.lastNotificationSource.value)
        assertEquals("Rahul: Message received", Phase7DiagnosticManager.lastNotificationEvent.value)
        assertEquals("Announced successfully", Phase7DiagnosticManager.lastAnnouncementOutcome.value)
    }
}
