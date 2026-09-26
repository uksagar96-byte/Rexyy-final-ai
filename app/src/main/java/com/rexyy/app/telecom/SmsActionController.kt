package com.rexyy.app.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

sealed class SmsActionResult {
    data class Success(val message: String, val recipientName: String, val isDirectlySent: Boolean) : SmsActionResult()
    data class NeedsMessageBody(val target: String, val prompt: String) : SmsActionResult()
    data class MultipleMatches(val query: String, val matches: List<CallerIdentityResolver.ContactMatch>) : SmsActionResult()
    data class PermissionNeeded(val permission: String, val message: String) : SmsActionResult()
    data class Failure(val error: String) : SmsActionResult()
}

class SmsActionController(private val context: Context) {

    companion object {
        @Volatile
        var testSmsOverride: ((recipient: String, message: String) -> SmsActionResult)? = null
    }

    fun sendSms(target: String, messageText: String, confirmedSend: Boolean = false): SmsActionResult {
        val cleanTarget = target.trim()
        val cleanMessage = messageText.trim()

        Phase7DiagnosticManager.updateSmsState(
            state = SmsLifecycleState.SMS_PREPARING,
            recipient = cleanTarget,
            messageLength = cleanMessage.length
        )
        com.rexyy.app.pill.DynamicPillManager.postSms(cleanTarget, "Preparing SMS")

        testSmsOverride?.let { overrideFn ->
            val res = overrideFn(cleanTarget, cleanMessage)
            when (res) {
                is SmsActionResult.Success -> {
                    Phase7DiagnosticManager.updateSmsState(
                        state = SmsLifecycleState.SMS_SENT,
                        recipient = cleanTarget,
                        messageLength = cleanMessage.length
                    )
                    com.rexyy.app.pill.DynamicPillManager.postSuccess(res.message)
                }
                is SmsActionResult.Failure -> {
                    Phase7DiagnosticManager.updateSmsState(
                        state = SmsLifecycleState.SMS_FAILED,
                        recipient = cleanTarget,
                        messageLength = cleanMessage.length,
                        error = res.error
                    )
                    com.rexyy.app.pill.DynamicPillManager.postError(res.error)
                }
                is SmsActionResult.PermissionNeeded -> {
                    Phase7DiagnosticManager.updateSmsState(
                        state = SmsLifecycleState.SMS_FAILED,
                        recipient = cleanTarget,
                        messageLength = cleanMessage.length,
                        error = res.message
                    )
                    com.rexyy.app.pill.DynamicPillManager.postError(res.message)
                }
                else -> {}
            }
            return res
        }

        if (cleanTarget.isBlank()) {
            Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_FAILED, null, 0, "Recipient is blank")
            com.rexyy.app.pill.DynamicPillManager.postError("Recipient is blank")
            return SmsActionResult.Failure("Kisko SMS bhejna hai? Please contact ka naam ya number batayein.")
        }

        if (cleanMessage.isBlank()) {
            // NEVER invent a message
            Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_PREPARING, cleanTarget, 0)
            com.rexyy.app.pill.DynamicPillManager.postSms(cleanTarget, "Message text needed")
            return SmsActionResult.NeedsMessageBody(
                target = cleanTarget,
                prompt = "$cleanTarget ko kya SMS bhejna hai?"
            )
        }

        // 1. Resolve contact
        val contactRes = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedMatch = when (contactRes) {
            is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> {
                Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_FAILED, cleanTarget, cleanMessage.length, "READ_CONTACTS required")
                com.rexyy.app.pill.DynamicPillManager.postError("Contacts permission required")
                return SmsActionResult.PermissionNeeded(
                    Manifest.permission.READ_CONTACTS,
                    "Contacts permission required hai. Settings mein Contacts permission allow karein."
                )
            }
            is CallerIdentityResolver.ContactActionResult.NotFound -> {
                val digits = cleanTarget.filter { it.isDigit() || it == '+' }
                if (digits.length >= 3) {
                    CallerIdentityResolver.ContactMatch(name = cleanTarget, phoneNumber = digits)
                } else {
                    Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_FAILED, cleanTarget, cleanMessage.length, "Contact not found")
                    com.rexyy.app.pill.DynamicPillManager.postError("Contact \"$cleanTarget\" not found")
                    return SmsActionResult.Failure("Contact \"$cleanTarget\" contacts mein nahi mila.")
                }
            }
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_FAILED, cleanTarget, cleanMessage.length, "Multiple contacts match")
                com.rexyy.app.pill.DynamicPillManager.postSms(cleanTarget, "${contactRes.matches.size} contacts found")
                return SmsActionResult.MultipleMatches(cleanTarget, contactRes.matches)
            }
            is CallerIdentityResolver.ContactActionResult.Resolved -> {
                contactRes.contact
            }
        }

        val cleanNumber = resolvedMatch.phoneNumber.replace(" ", "").replace("-", "")
        val digitsOnly = cleanNumber.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length < 3) {
            Phase7DiagnosticManager.updateSmsState(SmsLifecycleState.SMS_FAILED, resolvedMatch.name, cleanMessage.length, "Phone number empty or invalid")
            com.rexyy.app.pill.DynamicPillManager.postError("Invalid phone number")
            return SmsActionResult.Failure("${resolvedMatch.name} ke paas koi valid phone number nahi hai.")
        }

        // 2. Check SEND_SMS permission
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (confirmedSend && !hasSmsPermission) {
            Phase7DiagnosticManager.updateSmsState(
                state = SmsLifecycleState.SMS_FAILED,
                recipient = resolvedMatch.name,
                messageLength = cleanMessage.length,
                error = "SEND_SMS permission denied"
            )
            com.rexyy.app.pill.DynamicPillManager.postError("SMS permission denied")
            return SmsActionResult.PermissionNeeded(
                Manifest.permission.SEND_SMS,
                "SMS send karne ke liye Send SMS permission zaroori hai. Settings mein permission allow karein."
            )
        }

        Phase7DiagnosticManager.updateSmsState(
            state = SmsLifecycleState.SMS_SENDING,
            recipient = resolvedMatch.name,
            messageLength = cleanMessage.length
        )
        com.rexyy.app.pill.DynamicPillManager.postSms(resolvedMatch.name, "Sending SMS")

        if (hasSmsPermission) {
            return try {
                @Suppress("DEPRECATION")
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }

                // If message is longer than standard SMS, divide message
                val parts = smsManager.divideMessage(cleanMessage)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanNumber, null, cleanMessage, null, null)
                }

                Phase7DiagnosticManager.updateSmsState(
                    state = SmsLifecycleState.SMS_SENT,
                    recipient = resolvedMatch.name,
                    messageLength = cleanMessage.length
                )
                com.rexyy.app.pill.DynamicPillManager.postSuccess("${resolvedMatch.name} ko SMS bhej diya gaya hai.")
                SmsActionResult.Success(
                    message = "${resolvedMatch.name} ko SMS bhej diya gaya hai.",
                    recipientName = resolvedMatch.name,
                    isDirectlySent = true
                )
            } catch (e: Exception) {
                Phase7DiagnosticManager.updateSmsState(
                    state = SmsLifecycleState.SMS_FAILED,
                    recipient = resolvedMatch.name,
                    messageLength = cleanMessage.length,
                    error = e.localizedMessage
                )
                com.rexyy.app.pill.DynamicPillManager.postError("SMS failed: ${e.localizedMessage}")
                SmsActionResult.Failure("SMS send nahi ho paya: ${e.localizedMessage}")
            }
        } else {
            return openSmsComposer(cleanNumber, resolvedMatch.name, cleanMessage)
        }
    }

    private fun openSmsComposer(cleanNumber: String, recipientName: String, messageText: String): SmsActionResult {
        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(cleanNumber)}")
            putExtra("sms_body", messageText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(sendIntent)
            com.rexyy.app.pill.DynamicPillManager.postSms(recipientName, "Composer opened")
            SmsActionResult.Success(
                message = "$recipientName ke liye SMS composer open ho gaya hai. Tap send to deliver.",
                recipientName = recipientName,
                isDirectlySent = false
            )
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateSmsState(
                state = SmsLifecycleState.SMS_FAILED,
                recipient = recipientName,
                messageLength = messageText.length,
                error = e.localizedMessage
            )
            com.rexyy.app.pill.DynamicPillManager.postError("SMS composer failed: ${e.localizedMessage}")
            SmsActionResult.Failure("SMS composer open nahi ho paya: ${e.localizedMessage}")
        }
    }
}
