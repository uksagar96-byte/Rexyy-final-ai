package com.rexyy.app.notifications

import com.rexyy.app.utils.AppLanguage
import java.util.regex.Pattern

data class SanitizedNotification(
    val isSensitive: Boolean,
    val sanitizedTitle: String,
    val sanitizedText: String,
    val diagnosticSummary: String,
    val speechAnnouncement: String
)

object NotificationPrivacyFilter {

    // Regex patterns for sensitive credentials, OTPs, PINs, CVVs, banking codes
    private val OTP_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(?:otp|one[- ]?time[- ]?pass(?:word)?|verification[- ]?code|secret[- ]?code|pin|cvv)\\b"),
        Pattern.compile("(?i)\\b(?:code|otp|passcode)\\s*(?:is|:)?\\s*(\\d{4,8})\\b"),
        Pattern.compile("(?i)\\b(?:bank|credit[- ]?card|debit[- ]?card|account[- ]?no|a/c)\\b"),
        Pattern.compile("(?i)\\b(?:withdrawn|debited|credited|balance\\s*is)\\b")
    )

    // Regex matching raw 4-8 digit numbers that look like codes
    private val DIGIT_CODE_REGEX = Pattern.compile("\\b\\d{4,8}\\b")

    /**
     * Checks if notification content contains sensitive or privacy-restricted data.
     */
    fun isSensitiveContent(title: String, text: String): Boolean {
        val combined = "$title $text"
        return OTP_PATTERNS.any { it.matcher(combined).find() }
    }

    /**
     * Sanitizes notification for logging, telemetry and voice announcement.
     * Prevents OTPs, PINs, CVVs and financial details from ever being logged or spoken out loud.
     */
    fun sanitize(
        appName: String,
        rawTitle: String,
        rawText: String,
        language: AppLanguage
    ): SanitizedNotification {
        val sensitive = isSensitiveContent(rawTitle, rawText)

        val cleanTitle = if (sensitive) {
            maskDigitsAndKeywords(rawTitle)
        } else {
            rawTitle.take(60)
        }

        val cleanText = if (sensitive) {
            "[Protected Security / Sensitive Content]"
        } else {
            rawText.take(120)
        }

        val diagnosticSummary = if (sensitive) {
            "Security alert from $appName [Masked for privacy]"
        } else {
            val titlePart = if (cleanTitle.isNotBlank()) ": ${cleanTitle.take(25)}" else ""
            "Notification from $appName$titlePart"
        }

        val speechAnnouncement = if (sensitive) {
            when (language) {
                AppLanguage.HINDI -> "$appName से सिक्योरिटी अलर्ट आया है।"
                AppLanguage.ENGLISH -> "Security alert from $appName."
                AppLanguage.HINGLISH -> "$appName se security alert aaya hai."
            }
        } else {
            when (language) {
                AppLanguage.HINDI -> {
                    if (cleanTitle.isNotBlank() && cleanTitle != appName) {
                        "$appName से $cleanTitle का नोटिफिकेशन आया है।"
                    } else {
                        "$appName से नया नोटिफिकेशन आया है।"
                    }
                }
                AppLanguage.ENGLISH -> {
                    if (cleanTitle.isNotBlank() && cleanTitle != appName) {
                        "Notification from $appName: $cleanTitle."
                    } else {
                        "New notification from $appName."
                    }
                }
                AppLanguage.HINGLISH -> {
                    if (cleanTitle.isNotBlank() && cleanTitle != appName) {
                        "$appName se $cleanTitle ka notification aaya hai."
                    } else {
                        "$appName se new notification aaya hai."
                    }
                }
            }
        }

        return SanitizedNotification(
            isSensitive = sensitive,
            sanitizedTitle = cleanTitle,
            sanitizedText = cleanText,
            diagnosticSummary = diagnosticSummary,
            speechAnnouncement = speechAnnouncement
        )
    }

    private fun maskDigitsAndKeywords(input: String): String {
        var result = DIGIT_CODE_REGEX.matcher(input).replaceAll("******")
        result = result.replace("(?i)\\b(?:otp|cvv|pin|password)\\b".toRegex(), "[HIDDEN]")
        return result.take(50).trim()
    }
}
