package com.rexyy.app.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

sealed class TelecomActionResult {
    data class Success(val message: String, val recipientName: String? = null, val isDirectCall: Boolean = false) : TelecomActionResult()
    data class MultipleMatches(val query: String, val matches: List<CallerIdentityResolver.ContactMatch>) : TelecomActionResult()
    data class PermissionNeeded(val permission: String, val message: String) : TelecomActionResult()
    data class Unsupported(val reason: String) : TelecomActionResult()
    data class Failure(val error: String) : TelecomActionResult()
}

class CallActionController(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    companion object {
        @Volatile
        var testCallOverride: ((target: String, isDirect: Boolean) -> TelecomActionResult)? = null
    }

    fun answerCall(): TelecomActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                return TelecomActionResult.PermissionNeeded(
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    "Answer call permission required to accept incoming calls directly."
                )
            }

            return try {
                @Suppress("DEPRECATION")
                telecomManager?.acceptRingingCall()
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.ACTIVE, "Incoming Call")
                TelecomActionResult.Success("Call answered.")
            } catch (e: SecurityException) {
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, null, e.localizedMessage)
                TelecomActionResult.Unsupported("Android OS or device OEM restricted direct call answering: ${e.localizedMessage}")
            } catch (e: Exception) {
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, null, e.localizedMessage)
                TelecomActionResult.Failure("Failed to answer call: ${e.localizedMessage}")
            }
        } else {
            return TelecomActionResult.Unsupported("Direct call answering is not supported on Android versions below 8.0.")
        }
    }

    fun rejectCall(): TelecomActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAnswerPermission) {
                return TelecomActionResult.PermissionNeeded(
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    "Permission required to reject calls."
                )
            }

            return try {
                @Suppress("DEPRECATION")
                val ended = telecomManager?.endCall() ?: false
                if (ended) {
                    Phase7DiagnosticManager.updateCallState(CallLifecycleState.ENDED)
                    TelecomActionResult.Success("Call rejected.")
                } else {
                    TelecomActionResult.Unsupported("Could not reject call through Telecom manager on this device.")
                }
            } catch (e: SecurityException) {
                TelecomActionResult.Unsupported("Direct call rejection is restricted by device security policies.")
            } catch (e: Exception) {
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, null, e.localizedMessage)
                TelecomActionResult.Failure("Failed to reject call: ${e.localizedMessage}")
            }
        } else {
            return TelecomActionResult.Unsupported("Direct call rejection requires Android 9.0 or higher.")
        }
    }

    fun callContact(target: String, confirmedDirectCall: Boolean = false): TelecomActionResult {
        Phase7DiagnosticManager.updateCallState(CallLifecycleState.REQUESTED, target)

        testCallOverride?.let { overrideFn ->
            val res = overrideFn(target, confirmedDirectCall)
            when (res) {
                is TelecomActionResult.Success -> Phase7DiagnosticManager.updateCallState(CallLifecycleState.STARTED, target)
                is TelecomActionResult.Failure -> Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, target, res.error)
                else -> {}
            }
            return res
        }

        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) {
            Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, null, "Target is blank")
            return TelecomActionResult.Failure("Kisko call lagana hai? Please contact ka naam ya number batayein.")
        }

        // 1. Resolve contact
        val contactResolution = CallerIdentityResolver.resolveContactForAction(context, cleanTarget)
        val resolvedMatch = when (contactResolution) {
            is CallerIdentityResolver.ContactActionResult.PermissionNeeded -> {
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, cleanTarget, "READ_CONTACTS required")
                return TelecomActionResult.PermissionNeeded(
                    Manifest.permission.READ_CONTACTS,
                    "Contacts permission required hai. Settings mein Contacts permission allow karein."
                )
            }
            is CallerIdentityResolver.ContactActionResult.NotFound -> {
                // If not in contacts, check if target itself is a valid phone number
                val digits = cleanTarget.filter { it.isDigit() || it == '+' }
                if (digits.length >= 3) {
                    CallerIdentityResolver.ContactMatch(name = cleanTarget, phoneNumber = digits)
                } else {
                    Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, cleanTarget, "Contact not found")
                    return TelecomActionResult.Failure("Contact \"$cleanTarget\" contacts mein nahi mila.")
                }
            }
            is CallerIdentityResolver.ContactActionResult.Multiple -> {
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, cleanTarget, "Multiple contacts match")
                return TelecomActionResult.MultipleMatches(cleanTarget, contactResolution.matches)
            }
            is CallerIdentityResolver.ContactActionResult.Resolved -> {
                contactResolution.contact
            }
        }

        val cleanNumber = resolvedMatch.phoneNumber.replace(" ", "").replace("-", "")
        if (cleanNumber.isBlank()) {
            Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, resolvedMatch.name, "Phone number is empty")
            return TelecomActionResult.Failure("${resolvedMatch.name} ke paas koi valid phone number nahi hai.")
        }

        // 2. Check CALL_PHONE permission
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (confirmedDirectCall && hasCallPermission) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(callIntent)
                Phase7DiagnosticManager.updateCallState(CallLifecycleState.STARTED, resolvedMatch.name)
                TelecomActionResult.Success("Calling ${resolvedMatch.name}...", recipientName = resolvedMatch.name, isDirectCall = true)
            } catch (e: Exception) {
                launchDialer(cleanNumber, resolvedMatch.name)
            }
        } else {
            return launchDialer(cleanNumber, resolvedMatch.name)
        }
    }

    private fun launchDialer(cleanNumber: String, recipientName: String): TelecomActionResult {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = if (cleanNumber.isNotBlank()) Uri.parse("tel:${Uri.encode(cleanNumber)}") else Uri.parse("tel:")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(dialIntent)
            Phase7DiagnosticManager.updateCallState(CallLifecycleState.STARTED, recipientName)
            TelecomActionResult.Success("Opening dialer for $recipientName.", recipientName = recipientName, isDirectCall = false)
        } catch (e: Exception) {
            Phase7DiagnosticManager.updateCallState(CallLifecycleState.FAILED, recipientName, e.localizedMessage)
            TelecomActionResult.Failure("Unable to open dialer: ${e.localizedMessage}")
        }
    }
}
