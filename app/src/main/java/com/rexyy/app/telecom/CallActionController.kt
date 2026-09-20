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
    data class Success(val message: String) : TelecomActionResult()
    data class PermissionNeeded(val permission: String, val message: String) : TelecomActionResult()
    data class Unsupported(val reason: String) : TelecomActionResult()
    data class Failure(val error: String) : TelecomActionResult()
}

class CallActionController(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

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
                TelecomActionResult.Success("Call answered.")
            } catch (e: SecurityException) {
                TelecomActionResult.Unsupported("Android OS or device OEM restricted direct call answering: ${e.localizedMessage}")
            } catch (e: Exception) {
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
                    TelecomActionResult.Success("Call rejected.")
                } else {
                    TelecomActionResult.Unsupported("Could not reject call through Telecom manager on this device.")
                }
            } catch (e: SecurityException) {
                TelecomActionResult.Unsupported("Direct call rejection is restricted by device security policies.")
            } catch (e: Exception) {
                TelecomActionResult.Failure("Failed to reject call: ${e.localizedMessage}")
            }
        } else {
            return TelecomActionResult.Unsupported("Direct call rejection requires Android 9.0 or higher.")
        }
    }

    fun callContact(target: String, confirmedDirectCall: Boolean = false): TelecomActionResult {
        val resolvedPhone = CallerIdentityResolver.findPhoneNumberByName(context, target) ?: target
        val cleanNumber = resolvedPhone.replace(" ", "")

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (confirmedDirectCall && hasCallPermission && cleanNumber.isNotBlank()) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(callIntent)
                TelecomActionResult.Success("Calling $target...")
            } catch (e: Exception) {
                launchDialer(cleanNumber, target)
            }
        } else {
            return launchDialer(cleanNumber, target)
        }
    }

    private fun launchDialer(cleanNumber: String, target: String): TelecomActionResult {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = if (cleanNumber.isNotBlank()) Uri.parse("tel:${Uri.encode(cleanNumber)}") else Uri.parse("tel:")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(dialIntent)
            TelecomActionResult.Success("Opening dialer for $target.")
        } catch (e: Exception) {
            TelecomActionResult.Failure("Unable to open dialer: ${e.localizedMessage}")
        }
    }
}
