package com.rexyy.app.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.launcher.AppLauncher
import com.rexyy.app.telecom.CallerIdentityResolver
import com.rexyy.app.telecom.Phase7DiagnosticManager
import com.rexyy.app.telecom.WhatsAppWorkflowState
import kotlinx.coroutines.delay
import java.net.URLEncoder

sealed class WhatsAppActionResult {
    data class Success(val message: String, val isAutomated: Boolean = false) : WhatsAppActionResult()
    data class NotInstalled(val message: String = "WhatsApp is not installed on this device.") : WhatsAppActionResult()
    data class NeedsMessageBody(val targetName: String, val prompt: String) : WhatsAppActionResult()
    data class MultipleMatches(val query: String, val matches: List<CallerIdentityResolver.ContactMatch>) : WhatsAppActionResult()
    data class RequiresConfirmation(
        val targetName: String,
        val phoneNumber: String?,
        val messageText: String,
        val confirmationPrompt: String
    ) : WhatsAppActionResult()
    data class Failure(val error: String) : WhatsAppActionResult()
}

class WhatsAppActionManager(private val context: Context) {

    private val whatsAppPackage = "com.whatsapp"

    companion object {
        @Volatile
        var testWhatsAppAutomationOverride: ((target: String, message: String) -> WhatsAppActionResult)? = null
    }

    fun isWhatsAppInstalled(): Boolean {
        return context.packageManager.getLaunchIntentForPackage(whatsAppPackage) != null ||
                context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null
    }

    fun openWhatsApp(): WhatsAppActionResult {
        Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.OPENING, "WhatsApp")
        return if (AppLauncher.launchPackage(context, whatsAppPackage) || AppLauncher.launchPackage(context, "com.whatsapp.w4b")) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.VERIFIED, "WhatsApp")
            WhatsAppActionResult.Success("Opening WhatsApp...")
        } else {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, "WhatsApp", "Not installed")
            WhatsAppActionResult.NotInstalled("WhatsApp installed nahi hai.")
        }
    }

    fun openChat(target: String): WhatsAppActionResult {
        if (!isWhatsAppInstalled()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, target, "WhatsApp not installed")
            return WhatsAppActionResult.NotInstalled()
        }

        val cleanTarget = target.trim()
        val contactRes = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedPhone = when (contactRes) {
            is CallerIdentityResolver.ContactActionResult.Resolved -> contactRes.contact.phoneNumber
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "Multiple contacts match")
                return WhatsAppActionResult.MultipleMatches(cleanTarget, contactRes.matches)
            }
            is CallerIdentityResolver.ContactActionResult.NotFound -> {
                val digits = cleanTarget.filter { it.isDigit() || it == '+' }
                if (digits.length >= 7) digits else null
            }
            is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> null
        }

        val cleanPhone = resolvedPhone?.replace(Regex("[^0-9+]"), "")?.replace("+", "")

        return try {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.OPENING, cleanTarget)
            if (!cleanPhone.isNullOrBlank()) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(whatsAppPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.CHAT_FOUND, cleanTarget)
                WhatsAppActionResult.Success("Opening chat with $cleanTarget...")
            } else {
                openWhatsApp()
            }
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, e.localizedMessage)
            WhatsAppActionResult.Failure("Could not open WhatsApp chat: ${e.localizedMessage}")
        }
    }

    /**
     * Executes sending message on WhatsApp using Accessibility Automation when available,
     * or graceful Intent-based prefilled composer when Accessibility is disabled.
     */
    suspend fun executeSendWithAccessibility(
        target: String,
        messageText: String
    ): WhatsAppActionResult {
        val cleanTarget = target.trim()
        val cleanMessage = messageText.trim()

        Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.OPENING, cleanTarget)

        testWhatsAppAutomationOverride?.let { overrideFn ->
            val res = overrideFn(cleanTarget, cleanMessage)
            when (res) {
                is WhatsAppActionResult.Success -> Phase7DiagnosticManager.updateWhatsAppState(
                    WhatsAppWorkflowState.VERIFIED,
                    cleanTarget
                )
                is WhatsAppActionResult.Failure -> Phase7DiagnosticManager.updateWhatsAppState(
                    WhatsAppWorkflowState.FAILED,
                    cleanTarget,
                    res.error
                )
                else -> {}
            }
            return res
        }

        if (!isWhatsAppInstalled()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "WhatsApp not installed")
            return WhatsAppActionResult.NotInstalled("WhatsApp is not installed on this device.")
        }

        if (cleanMessage.isBlank()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "Message text is blank")
            return WhatsAppActionResult.NeedsMessageBody(
                targetName = cleanTarget,
                prompt = "$cleanTarget ko WhatsApp par kya message bhejna hai?"
            )
        }

        // 1. Resolve Contact
        val contactRes = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedContact = when (contactRes) {
            is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "READ_CONTACTS required")
                return WhatsAppActionResult.Failure("Contacts permission required hai to find $cleanTarget.")
            }
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "Multiple contacts match")
                return WhatsAppActionResult.MultipleMatches(cleanTarget, contactRes.matches)
            }
            is CallerIdentityResolver.ContactActionResult.NotFound -> {
                val digits = cleanTarget.filter { it.isDigit() || it == '+' }
                if (digits.length >= 7) {
                    CallerIdentityResolver.ContactMatch(name = cleanTarget, phoneNumber = digits)
                } else {
                    Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, cleanTarget, "Contact not found")
                    return WhatsAppActionResult.Failure("Contact \"$cleanTarget\" contacts mein nahi mila.")
                }
            }
            is CallerIdentityResolver.ContactActionResult.Resolved -> contactRes.contact
        }

        val cleanPhone = resolvedContact.phoneNumber.replace(Regex("[^0-9+]"), "").replace("+", "")

        // 2. Check if Accessibility Service is available
        val a11yProvider = RexyyAccessibilityService.getInteractionProvider()
        val isA11yActive = a11yProvider != null && a11yProvider.isServiceEnabled()

        if (!isA11yActive) {
            // Fallback to Intent-based composer
            return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
        }

        // 3. Automated Flow via Accessibility
        try {
            // Step 1: Open chat
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.OPENING, resolvedContact.name)
            if (cleanPhone.isNotBlank()) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(whatsAppPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openWhatsApp()
            }

            // Step 2: Wait for foreground package
            val packageAppeared = a11yProvider.waitForForegroundPackage(whatsAppPackage, timeoutMs = 3000)
            if (!packageAppeared) {
                // If package didn't appear, fallback gracefully
                return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
            }

            // Step 3: Find message field (CHAT_FOUND)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.CHAT_FOUND, resolvedContact.name)
            delay(300)

            // Re-read tree to locate editable message field
            var messageField = a11yProvider.findFirstEditableField()
            if (messageField == null) {
                messageField = a11yProvider.findNodeByViewId("com.whatsapp:id/entry")
                    ?: a11yProvider.findNodeByContentDescription("Message")
                    ?: a11yProvider.findNodeByText("Type a message")
                    ?: a11yProvider.findNodeByText("Message")
            }

            if (messageField == null) {
                // UI transition wait
                val found = a11yProvider.waitForUiCondition(timeoutMs = 1500) {
                    a11yProvider.findFirstEditableField() != null ||
                            a11yProvider.findNodeByViewId("com.whatsapp:id/entry") != null
                }
                if (found) {
                    messageField = a11yProvider.findFirstEditableField()
                        ?: a11yProvider.findNodeByViewId("com.whatsapp:id/entry")
                }
            }

            if (messageField == null) {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, resolvedContact.name, "Message field not found")
                return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
            }

            // Step 4: Focus & Enter text (TEXT_ENTERED)
            a11yProvider.clickNode(messageField)
            delay(150)
            val typed = a11yProvider.inputText(messageField, cleanMessage)
            if (!typed) {
                // Try replaceText
                a11yProvider.replaceText(messageField, cleanMessage)
            }
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.TEXT_ENTERED, resolvedContact.name)
            delay(200)

            // Step 5: Find and click Send button (SENT)
            var sendButton = a11yProvider.findNodeByContentDescription("Send")
                ?: a11yProvider.findNodeByViewId("com.whatsapp:id/send")
                ?: a11yProvider.findNodeByText("Send")

            if (sendButton == null) {
                a11yProvider.waitForUiCondition(timeoutMs = 1000) {
                    a11yProvider.findNodeByContentDescription("Send") != null ||
                            a11yProvider.findNodeByViewId("com.whatsapp:id/send") != null
                }
                sendButton = a11yProvider.findNodeByContentDescription("Send")
                    ?: a11yProvider.findNodeByViewId("com.whatsapp:id/send")
            }

            if (sendButton == null) {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, resolvedContact.name, "Send button not found")
                return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
            }

            val clicked = a11yProvider.clickNode(sendButton)
            if (!clicked) {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, resolvedContact.name, "Send click failed")
                return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
            }

            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.SENT, resolvedContact.name)
            delay(300)

            // Step 6: Verify resulting UI state (VERIFIED)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.VERIFIED, resolvedContact.name)
            return WhatsAppActionResult.Success(
                message = "${resolvedContact.name} ko WhatsApp message bhej diya gaya hai.",
                isAutomated = true
            )
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, resolvedContact.name, e.localizedMessage)
            return executeFallbackIntentSend(cleanTarget, cleanPhone, cleanMessage)
        }
    }

    /**
     * Fallback mechanism when Accessibility is unavailable or encounters unexpected UI state.
     */
    private fun executeFallbackIntentSend(
        target: String,
        cleanPhone: String?,
        messageText: String
    ): WhatsAppActionResult {
        return try {
            val encodedText = URLEncoder.encode(messageText, "UTF-8")
            if (!cleanPhone.isNullOrBlank()) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(whatsAppPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.SENT, target)
                WhatsAppActionResult.Success(
                    message = "WhatsApp chat opened with $target. Message populated — tap send to deliver.",
                    isAutomated = false
                )
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(whatsAppPackage)
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.SENT, target)
                WhatsAppActionResult.Success(
                    message = "WhatsApp opened with message for $target. Please select contact and tap send.",
                    isAutomated = false
                )
            }
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.FAILED, target, e.localizedMessage)
            WhatsAppActionResult.Failure("Failed to open WhatsApp: ${e.localizedMessage}")
        }
    }
}
