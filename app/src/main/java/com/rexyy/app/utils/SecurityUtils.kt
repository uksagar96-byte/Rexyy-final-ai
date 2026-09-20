package com.rexyy.app.utils

object SecurityUtils {

    /**
     * Safely masks an API key so that only the last 4 characters are visible.
     * Prevents accidental leakage in UI or crash reports.
     */
    fun maskApiKey(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return ""
        val trimmed = apiKey.trim()
        return if (trimmed.length <= 6) {
            "••••••••"
        } else {
            val prefix = if (trimmed.startsWith("sk-")) "sk-••••" else "••••••••"
            val suffix = trimmed.takeLast(4)
            "$prefix$suffix"
        }
    }

    /**
     * Validates whether an API key has a plausible format without being empty.
     */
    fun isValidApiKey(apiKey: String?): Boolean {
        if (apiKey.isNullOrBlank()) return false
        val trimmed = apiKey.trim()
        return trimmed.length >= 10
    }
}
