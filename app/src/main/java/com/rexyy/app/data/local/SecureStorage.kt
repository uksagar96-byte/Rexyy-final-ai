package com.rexyy.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rexyy.app.BuildConfig
import com.rexyy.app.network.provider.AiProviderType
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(
    context: Context,
    var includeBuildConfigKeys: Boolean = true
) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "rexyy_secure_preferences"
        private const val KEY_ENCRYPTED_API_KEY = "encrypted_api_key"
        private const val KEY_ENCRYPTED_GEMINI_KEY = "encrypted_gemini_key"
        private const val KEY_SELECTED_PROVIDER = "selected_ai_provider"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SELECTED_GEMINI_MODEL = "selected_gemini_model"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_AUTO_FALLBACK = "auto_fallback_enabled"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "RexyySecureKeyAlias_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        private const val KEY_VOICE_COMMANDS_ENABLED = "voice_commands_enabled"
        private const val KEY_VOICE_REPLIES_ENABLED = "voice_replies_enabled"
        private const val KEY_VOICE_LANGUAGE = "voice_language"

        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_RESPONSE_STYLE = "response_style"
        private const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"

        const val DEFAULT_USER_NAME = "Commander"
        const val DEFAULT_ASSISTANT_NAME = "REXYY"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
        const val VOICE_LANG_DEFAULT = "SYSTEM_DEFAULT"
        const val VOICE_LANG_EN = "en-US"
        const val VOICE_LANG_HI = "hi-IN"
    }

    @Volatile
    private var fallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // Fallback for JVM/Robolectric test environments or devices without AndroidKeyStore
            return fallbackKey ?: synchronized(this) {
                fallbackKey ?: run {
                    val fallbackGen = KeyGenerator.getInstance("AES")
                    fallbackGen.init(256)
                    val key = fallbackGen.generateKey()
                    fallbackKey = key
                    key
                }
            }
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return ""

            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            val cipherTextSize = combined.size - IV_LENGTH
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherTextSize)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    // --- AI Provider Management ---

    fun getSelectedProvider(): AiProviderType {
        val storedId = prefs.getString(KEY_SELECTED_PROVIDER, null)
        if (storedId != null) {
            return AiProviderType.fromId(storedId)
        }
        // If no provider explicitly chosen yet, pick Gemini if only Gemini key is available
        return if (hasGeminiApiKey() && !hasOpenAiApiKey()) {
            AiProviderType.GEMINI
        } else {
            AiProviderType.OPENAI
        }
    }

    fun setSelectedProvider(provider: AiProviderType) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.id).apply()
    }

    // --- OpenAI Key Management ---

    fun saveOpenAiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val encrypted = encrypt(trimmed)
        prefs.edit().putString(KEY_ENCRYPTED_API_KEY, encrypted).apply()
    }

    fun getOpenAiApiKey(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_API_KEY, null)
        if (encrypted != null) {
            val decrypted = decrypt(encrypted)
            if (decrypted.isNotBlank()) return decrypted
        }
        if (!includeBuildConfigKeys) return null
        // Fallback to BuildConfig if provided via AI Studio Secrets and not placeholder
        val buildConfigKey = BuildConfig.OPENAI_API_KEY
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_OPENAI_API_KEY" && buildConfigKey != "DEFAULT_VALUE") {
            return buildConfigKey
        }
        return null
    }

    fun hasOpenAiApiKey(): Boolean {
        return !getOpenAiApiKey().isNullOrBlank()
    }

    fun clearOpenAiApiKey() {
        prefs.edit().remove(KEY_ENCRYPTED_API_KEY).apply()
    }

    // --- Gemini Key Management ---

    fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val encrypted = encrypt(trimmed)
        prefs.edit().putString(KEY_ENCRYPTED_GEMINI_KEY, encrypted).apply()
    }

    fun getGeminiApiKey(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_GEMINI_KEY, null)
        if (encrypted != null) {
            val decrypted = decrypt(encrypted)
            if (decrypted.isNotBlank()) return decrypted
        }
        if (!includeBuildConfigKeys) return null
        // Fallback to BuildConfig if provided via AI Studio Secrets and not placeholder
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY" && buildConfigKey != "DEFAULT_VALUE") {
            return buildConfigKey
        }
        return null
    }

    fun hasGeminiApiKey(): Boolean {
        return !getGeminiApiKey().isNullOrBlank()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_ENCRYPTED_GEMINI_KEY).apply()
    }

    // --- Unified Key & Provider Access for Chat & Setup Screens ---

    fun saveApiKey(apiKey: String) {
        when (getSelectedProvider()) {
            AiProviderType.OPENAI -> saveOpenAiApiKey(apiKey)
            AiProviderType.GEMINI -> saveGeminiApiKey(apiKey)
            AiProviderType.LOCAL_TEST -> { /* No API key needed for local test */ }
        }
    }

    fun getApiKey(): String? {
        return when (getSelectedProvider()) {
            AiProviderType.OPENAI -> getOpenAiApiKey() ?: getGeminiApiKey()
            AiProviderType.GEMINI -> getGeminiApiKey() ?: getOpenAiApiKey()
            AiProviderType.LOCAL_TEST -> "local_test_active"
        }
    }

    fun hasApiKey(): Boolean {
        return hasAnyApiKey()
    }

    fun hasAnyApiKey(): Boolean {
        return getSelectedProvider() == AiProviderType.LOCAL_TEST || hasOpenAiApiKey() || hasGeminiApiKey()
    }

    fun clearApiKey() {
        when (getSelectedProvider()) {
            AiProviderType.OPENAI -> clearOpenAiApiKey()
            AiProviderType.GEMINI -> clearGeminiApiKey()
            AiProviderType.LOCAL_TEST -> { /* No-op */ }
        }
    }

    // --- Model Selection Management ---

    fun getOpenAiModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setOpenAiModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    fun getGeminiModel(): String {
        return prefs.getString(KEY_SELECTED_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
    }

    fun setGeminiModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_GEMINI_MODEL, model).apply()
    }

    fun saveSelectedModel(model: String) {
        when (getSelectedProvider()) {
            AiProviderType.OPENAI -> setOpenAiModel(model)
            AiProviderType.GEMINI -> setGeminiModel(model)
            AiProviderType.LOCAL_TEST -> { /* No model selection needed */ }
        }
    }

    fun getSelectedModel(): String {
        return when (getSelectedProvider()) {
            AiProviderType.OPENAI -> getOpenAiModel()
            AiProviderType.GEMINI -> getGeminiModel()
            AiProviderType.LOCAL_TEST -> "local-autonomous"
        }
    }

    // --- Automatic Fallback Setting ---

    fun isAutoFallbackEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_FALLBACK, true)
    }

    fun setAutoFallbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FALLBACK, enabled).apply()
    }

    fun saveBaseUrl(url: String) {
        val formatted = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_BASE_URL, formatted).apply()
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun isVoiceCommandsEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOICE_COMMANDS_ENABLED, true)
    }

    fun setVoiceCommandsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_COMMANDS_ENABLED, enabled).apply()
    }

    fun isVoiceRepliesEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOICE_REPLIES_ENABLED, true)
    }

    fun setVoiceRepliesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_REPLIES_ENABLED, enabled).apply()
    }

    fun getVoiceLanguage(): String {
        return prefs.getString(KEY_VOICE_LANGUAGE, VOICE_LANG_DEFAULT) ?: VOICE_LANG_DEFAULT
    }

    fun setVoiceLanguage(language: String) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE, language).apply()
    }

    // --- Onboarding & Setup Persistence ---

    fun isSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
    }

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim().ifBlank { DEFAULT_USER_NAME }).apply()
    }

    fun getAssistantName(): String {
        return prefs.getString(KEY_ASSISTANT_NAME, DEFAULT_ASSISTANT_NAME) ?: DEFAULT_ASSISTANT_NAME
    }

    fun setAssistantName(name: String) {
        prefs.edit().putString(KEY_ASSISTANT_NAME, name.trim().ifBlank { DEFAULT_ASSISTANT_NAME }).apply()
    }

    fun getResponseStyle(): String {
        return prefs.getString(KEY_RESPONSE_STYLE, "Professional & Concise") ?: "Professional & Concise"
    }

    fun setResponseStyle(style: String) {
        prefs.edit().putString(KEY_RESPONSE_STYLE, style).apply()
    }

    fun getCustomInstructions(): String {
        return prefs.getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
    }

    fun setCustomInstructions(instructions: String) {
        prefs.edit().putString(KEY_CUSTOM_INSTRUCTIONS, instructions.trim()).apply()
    }
}
