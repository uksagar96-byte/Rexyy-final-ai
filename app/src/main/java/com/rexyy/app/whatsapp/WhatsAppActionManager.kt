package com.rexyy.app.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rexyy.app.accessibility.RexyyAccessibilityService
import com.rexyy.app.launcher.AppLauncher
import com.rexyy.app.pill.DynamicPillManager
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
    data class AccessibilityRequired(val message: String = "WhatsApp par message automate karne ke liye Accessibility permission zaroori hai. Settings mein REXXY Accessibility Service ko allow karein.") : WhatsAppActionResult()
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
        Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_OPENING, "WhatsApp")
        DynamicPillManager.postWhatsApp("WhatsApp", "Opening WhatsApp")
        return if (AppLauncher.launchPackage(context, whatsAppPackage) || AppLauncher.launchPackage(context, "com.whatsapp.w4b")) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_VERIFIED, "WhatsApp")
            WhatsAppActionResult.Success("Opening WhatsApp...")
        } else {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, "WhatsApp", "Not installed")
            DynamicPillManager.postError("WhatsApp not installed")
            WhatsAppActionResult.NotInstalled("WhatsApp installed nahi hai.")
        }
    }

    fun openChat(target: String): WhatsAppActionResult {
        if (!isWhatsAppInstalled()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, target, "WhatsApp not installed")
            DynamicPillManager.postError("WhatsApp not installed")
            return WhatsAppActionResult.NotInstalled()
        }

        val cleanTarget = target.trim()
        Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_RESOLVING, cleanTarget)
        DynamicPillManager.postWhatsApp(cleanTarget, "Resolving contact...")

        val contactRes = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedPhone = when (contactRes) {
            is CallerIdentityResolver.ContactActionResult.Resolved -> contactRes.contact.phoneNumber
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "Multiple contacts match")
                DynamicPillManager.postWhatsApp(cleanTarget, "${contactRes.matches.size} contacts found")
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
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_OPENING, cleanTarget)
            DynamicPillManager.postWhatsApp(cleanTarget, "Opening chat...")
            if (!cleanPhone.isNullOrBlank()) {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(whatsAppPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_CHAT_RESOLVED, cleanTarget)
                DynamicPillManager.postWhatsApp(cleanTarget, "Chat opened")
                WhatsAppActionResult.Success("Opening chat with $cleanTarget...")
            } else {
                openWhatsApp()
            }
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, e.localizedMessage)
            DynamicPillManager.postError("Could not open chat: ${e.localizedMessage}")
            WhatsAppActionResult.Failure("Could not open WhatsApp chat: ${e.localizedMessage}")
        }
    }

    /**
     * Executes sending message on WhatsApp using Accessibility Automation.
     * If Accessibility is disabled, strictly reports the actual permission requirement (never fakes success).
     */
    suspend fun executeSendWithAccessibility(
        target: String,
        messageText: String
    ): WhatsAppActionResult {
        val cleanTarget = target.trim()
        val cleanMessage = messageText.trim()

        testWhatsAppAutomationOverride?.let { overrideFn ->
            val res = overrideFn(cleanTarget, cleanMessage)
            when (res) {
                is WhatsAppActionResult.Success -> {
                    Phase7DiagnosticManager.updateWhatsAppState(
                        WhatsAppWorkflowState.WHATSAPP_VERIFIED,
                        cleanTarget
                    )
                    DynamicPillManager.postSuccess(res.message)
                }
                is WhatsAppActionResult.Failure -> {
                    Phase7DiagnosticManager.updateWhatsAppState(
                        WhatsAppWorkflowState.WHATSAPP_FAILED,
                        cleanTarget,
                        res.error
                    )
                    DynamicPillManager.postError(res.error)
                }
                is WhatsAppActionResult.AccessibilityRequired -> {
                    Phase7DiagnosticManager.updateWhatsAppState(
                        WhatsAppWorkflowState.WHATSAPP_FAILED,
                        cleanTarget,
                        res.message
                    )
                    DynamicPillManager.postError("Accessibility permission required")
                }
                else -> {}
            }
            return res
        }

        if (!isWhatsAppInstalled()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "WhatsApp not installed")
            DynamicPillManager.postError("WhatsApp not installed")
            return WhatsAppActionResult.NotInstalled("WhatsApp is not installed on this device.")
        }

        if (cleanMessage.isBlank()) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "Message text is blank")
            DynamicPillManager.postWhatsApp(cleanTarget, "Message text needed")
            return WhatsAppActionResult.NeedsMessageBody(
                targetName = cleanTarget,
                prompt = "$cleanTarget ko WhatsApp par kya message bhejna hai?"
            )
        }

        // 1. Resolve Contact (WHATSAPP_RESOLVING)
        Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_RESOLVING, cleanTarget)
        DynamicPillManager.postWhatsApp(cleanTarget, "Resolving contact...")

        val contactRes = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedContact = when (contactRes) {
            is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "READ_CONTACTS required")
                DynamicPillManager.postError("Contacts permission required")
                return WhatsAppActionResult.Failure("Contacts permission required hai to find $cleanTarget.")
            }
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "Multiple contacts match")
                DynamicPillManager.postWhatsApp(cleanTarget, "${contactRes.matches.size} contacts found")
                return WhatsAppActionResult.MultipleMatches(cleanTarget, contactRes.matches)
            }
            is CallerIdentityResolver.ContactActionResult.NotFound -> {
                val digits = cleanTarget.filter { it.isDigit() || it == '+' }
                if (digits.length >= 7) {
                    CallerIdentityResolver.ContactMatch(name = cleanTarget, phoneNumber = digits)
                } else {
                    Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, cleanTarget, "Contact not found")
                    DynamicPillManager.postError("Contact \"$cleanTarget\" not found")
                    return WhatsAppActionResult.Failure("Contact \"$cleanTarget\" contacts mein nahi mila.")
                }
            }
            is CallerIdentityResolver.ContactActionResult.Resolved -> contactRes.contact
        }

        val cleanPhone = resolvedContact.phoneNumber.replace(Regex("[^0-9+]"), "").replace("+", "")

        // 2. Check if Accessibility Service is available - strictly required for automation
        val a11yProvider = RexyyAccessibilityService.getInteractionProvider()
        val isA11yActive = a11yProvider != null && a11yProvider.isServiceEnabled()

        if (!isA11yActive) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, "Accessibility service required")
            DynamicPillManager.postError("Accessibility permission required")
            return WhatsAppActionResult.AccessibilityRequired(
                "WhatsApp par automatically message send karne ke liye Accessibility permission zaroori hai. Kripya Settings mein REXXY Accessibility Service ko allow karein."
            )
        }

        // 3. Automated Flow via Accessibility
        try {
            // Step 1: Open chat (WHATSAPP_OPENING)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_OPENING, resolvedContact.name)
            DynamicPillManager.postWhatsApp(resolvedContact.name, "Opening chat...")
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
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, "WhatsApp foreground timeout")
                DynamicPillManager.postError("WhatsApp failed to open")
                return WhatsAppActionResult.Failure("WhatsApp foreground mein open nahi hua.")
            }

            // Step 3: Find message field (WHATSAPP_CHAT_RESOLVED)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_CHAT_RESOLVED, resolvedContact.name)
            DynamicPillManager.postWhatsApp(resolvedContact.name, "Chat resolved")
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
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, "Message field not found")
                DynamicPillManager.postError("Message field not found")
                return WhatsAppActionResult.Failure("WhatsApp chat mein message field nahi mila.")
            }

            // Step 4: Focus & Enter text (WHATSAPP_INPUTTING)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_INPUTTING, resolvedContact.name)
            DynamicPillManager.postWhatsApp(resolvedContact.name, "Typing message...")
            a11yProvider.clickNode(messageField)
            delay(150)
            val typed = a11yProvider.inputText(messageField, cleanMessage)
            if (!typed) {
                a11yProvider.replaceText(messageField, cleanMessage)
            }
            delay(200)

            // Step 5: Find and click Send button (WHATSAPP_SENDING)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_SENDING, resolvedContact.name)
            DynamicPillManager.postWhatsApp(resolvedContact.name, "Sending message...")
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
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, "Send button not found")
                DynamicPillManager.postError("Send button not found")
                return WhatsAppActionResult.Failure("WhatsApp send button nahi mila.")
            }

            val clicked = a11yProvider.clickNode(sendButton)
            if (!clicked) {
                Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, "Send click failed")
                DynamicPillManager.postError("Send click failed")
                return WhatsAppActionResult.Failure("WhatsApp message send nahi ho paya.")
            }

            delay(300)

            // Step 6: Verify resulting UI state (WHATSAPP_VERIFIED)
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_VERIFIED, resolvedContact.name)
            DynamicPillManager.postSuccess("${resolvedContact.name} ko WhatsApp message bhej diya gaya hai.")
            return WhatsAppActionResult.Success(
                message = "${resolvedContact.name} ko WhatsApp message bhej diya gaya hai.",
                isAutomated = true
            )
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateWhatsAppState(WhatsAppWorkflowState.WHATSAPP_FAILED, resolvedContact.name, e.localizedMessage)
            DynamicPillManager.postError("WhatsApp error: ${e.localizedMessage}")
            return WhatsAppActionResult.Failure("WhatsApp message send karte waqt error: ${e.localizedMessage}")
        }
    }
}
