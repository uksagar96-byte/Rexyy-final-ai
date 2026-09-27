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

        // Determine if title is a usable human name or if it's technical / generic metadata
        val sender = if (!sensitive && cleanTitle.isNotBlank() && cleanTitle != appName && !isTechnicalOrInternal(cleanTitle)) {
            cleanHumanSender(cleanTitle)
        } else {
            ""
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
                    if (sender.isNotBlank()) {
                        "$appName से $sender का नोटिफिकेशन आया है।"
                    } else {
                        "$appName से नया नोटिफिकेशन आया है।"
                    }
                }
                AppLanguage.ENGLISH -> {
                    if (sender.isNotBlank()) {
                        "Notification from $appName: $sender."
                    } else {
                        "New notification from $appName."
                    }
                }
                AppLanguage.HINGLISH -> {
                    if (sender.isNotBlank()) {
                        "$appName se $sender ka notification aaya hai."
                    } else {
                        "$appName se new notification aaya hai."
                    }
                }
            }
        }

        return SanitizedNotification(
            isSensitive = sensitive,
            sanitizedTitle = if (sender.isNotBlank()) sender else cleanTitle,
            sanitizedText = cleanText,
            diagnosticSummary = diagnosticSummary,
            speechAnnouncement = speechAnnouncement
        )
    }

    /**
     * Checks if a title string represents technical metadata, package names, URLs, or internal IDs.
     */
    private fun isTechnicalOrInternal(text: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase().trim()
        if (lower.startsWith("com.") || lower.startsWith("org.") || lower.startsWith("android.")) return true
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true
        if (lower.matches(Regex("^[a-f0-9\\-_]{8,}$"))) return true
        if (lower.matches(Regex("^\\d+$"))) return true
        if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".mp4") || lower.endsWith(".tmp")) return true
        if (lower.contains("null") || lower == "notification" || lower.endsWith("new notifications") || lower.endsWith("new messages")) return true
        return false
    }

    /**
     * Cleans social media usernames, handles, and separators for natural speech synthesis.
     * E.g. "@priya_sharma_98" -> "Priya Sharma"
     */
    private fun cleanHumanSender(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("@")) {
            clean = clean.substring(1)
        }
        // Replace underscores with spaces so TTS does not say "underscore"
        clean = clean.replace("_", " ")
        // Remove trailing numerical IDs if preceded by letters
        clean = clean.replace(Regex("(?<=[a-zA-Z])\\s*\\d+$"), "").trim()
        // If it's a simple single name, preserve it; otherwise capitalize cleanly
        return clean.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun maskDigitsAndKeywords(input: String): String {
        var result = DIGIT_CODE_REGEX.matcher(input).replaceAll("******")
        result = result.replace("(?i)\\b(?:otp|cvv|pin|password)\\b".toRegex(), "[HIDDEN]")
        return result.take(50).trim()
    }
}
