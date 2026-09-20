package com.rexyy.app.utils

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)"),
    HINGLISH("hinglish", "Hinglish (Hindi-English)")
}

object RexyyLanguageManager {

    fun parseLanguage(code: String?): AppLanguage {
        return when (code?.lowercase()?.trim()) {
            "hi", "hi-in", "hindi" -> AppLanguage.HINDI
            "en", "en-us", "english" -> AppLanguage.ENGLISH
            else -> AppLanguage.HINGLISH
        }
    }

    fun getWakeResponse(lang: AppLanguage, assistantName: String = "REXYY"): String {
        return when (lang) {
            AppLanguage.HINDI -> "सर, मैं एक्टिव हो गया हूँ। बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
            AppLanguage.ENGLISH -> "Sir, I’m active. How can I help you?"
            AppLanguage.HINGLISH -> "Sir, main active ho gaya hoon. Bataye, kya help chahiye?"
        }
    }

    fun getChargingConnected(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "सर, फोन चार्जिंग पर लग गया है।"
            AppLanguage.ENGLISH -> "Sir, phone is connected to charger."
            AppLanguage.HINGLISH -> "Sir, phone charging par lag gaya hai."
        }
    }

    fun getChargingDisconnected(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "सर, फोन चार्जिंग से डिस्कनेक्ट हो गया है।"
            AppLanguage.ENGLISH -> "Sir, phone is disconnected from charger."
            AppLanguage.HINGLISH -> "Sir, phone charging se disconnect ho gaya hai."
        }
    }

    fun getIncomingCallAnnouncement(callerName: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "सर, $callerName का कॉल आ रहा है।"
            AppLanguage.ENGLISH -> "Sir, $callerName is calling."
            AppLanguage.HINGLISH -> "Sir, $callerName is calling."
        }
    }

    fun getNotificationAnnouncement(appName: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "सर, आपको $appName से एक नोटिफिकेशन मिला है।"
            AppLanguage.ENGLISH -> "Sir, you have received a notification from $appName."
            AppLanguage.HINGLISH -> "Sir, aapko $appName se ek notification mila hai."
        }
    }

    fun getAskWhatsAppMessage(target: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "$target को WhatsApp पर क्या संदेश भेजना है?"
            AppLanguage.ENGLISH -> "What message should I send to $target on WhatsApp?"
            AppLanguage.HINGLISH -> "$target ko WhatsApp par kya message bhejna hai?"
        }
    }

    fun getConfirmWhatsApp(target: String, messageText: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "$target को WhatsApp पर यह संदेश भेजूँ? \"$messageText\""
            AppLanguage.ENGLISH -> "Should I send this WhatsApp message to $target? \"$messageText\""
            AppLanguage.HINGLISH -> "$target ko WhatsApp par ye message bheju? \"$messageText\""
        }
    }

    fun getAskSmsMessage(target: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "$target को क्या SMS भेजना है?"
            AppLanguage.ENGLISH -> "What SMS message should I send to $target?"
            AppLanguage.HINGLISH -> "$target ko kya SMS bhejna hai?"
        }
    }

    fun getConfirmSms(target: String, messageText: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "$target को यह SMS भेजूँ? \"$messageText\""
            AppLanguage.ENGLISH -> "Should I send this SMS to $target? \"$messageText\""
            AppLanguage.HINGLISH -> "$target ko ye SMS bheju? \"$messageText\""
        }
    }

    fun getConfirmCall(target: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.HINDI -> "$target को कॉल करूँ?"
            AppLanguage.ENGLISH -> "Should I call $target?"
            AppLanguage.HINGLISH -> "$target ko call karu?"
        }
    }
}
