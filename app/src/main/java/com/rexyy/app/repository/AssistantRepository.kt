package com.rexyy.app.repository

import android.content.Context
import com.rexyy.app.data.local.AppDatabase
import com.rexyy.app.data.local.ChatMessageEntity
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender
import com.rexyy.app.network.ApiClientFactory
import com.rexyy.app.network.NetworkResult
import com.rexyy.app.network.provider.AiModelRegistry
import com.rexyy.app.network.provider.AiProvider
import com.rexyy.app.network.provider.AiProviderConfig
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.network.provider.GeminiProvider
import com.rexyy.app.network.provider.LocalTestProvider
import com.rexyy.app.network.provider.OpenAiProvider
import com.rexyy.app.network.provider.OpenRouterProvider
import com.rexyy.app.utils.NetworkUtils
import com.rexyy.app.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AssistantRepository(
    private val context: Context,
    val secureStorage: SecureStorage = SecureStorage(context),
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private var openAiProvider: AiProvider = OpenAiProvider(
        ApiClientFactory.createOpenAiApi(secureStorage.getBaseUrl())
    ),
    private var geminiProvider: AiProvider = GeminiProvider(
        ApiClientFactory.createGeminiApi()
    ),
    private var openRouterProvider: AiProvider = OpenRouterProvider(),
    private val localTestProvider: AiProvider = LocalTestProvider()
) {
    private val chatDao = database.chatMessageDao()

    fun refreshApiBaseUrl() {
        openAiProvider = OpenAiProvider(
            ApiClientFactory.createOpenAiApi(secureStorage.getBaseUrl())
        )
    }

    fun getConversationHistory(): Flow<List<ChatMessage>> {
        return chatDao.getAllMessages().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun clearConversation() = withContext(Dispatchers.IO) {
        chatDao.clearAllMessages()
    }

    // --- Provider & Model Access ---

    fun getSelectedProvider(): AiProviderType = secureStorage.getSelectedProvider()
    fun setSelectedProvider(provider: AiProviderType) = secureStorage.setSelectedProvider(provider)

    fun getOpenAiModel(): String = secureStorage.getOpenAiModel()
    fun saveOpenAiModel(model: String) = secureStorage.setOpenAiModel(model)

    fun getGeminiModel(): String = secureStorage.getGeminiModel()
    fun saveGeminiModel(model: String) = secureStorage.setGeminiModel(model)

    fun getSelectedModel(): String = secureStorage.getSelectedModel()
    fun saveSelectedModel(model: String) = secureStorage.saveSelectedModel(model)

    fun isAutoFallbackEnabled(): Boolean = secureStorage.isAutoFallbackEnabled()
    fun setAutoFallbackEnabled(enabled: Boolean) = secureStorage.setAutoFallbackEnabled(enabled)

    // --- Key Management ---

    fun hasApiKey(): Boolean = secureStorage.hasApiKey()
    fun hasAnyApiKey(): Boolean = secureStorage.hasAnyApiKey()

    fun getMaskedApiKey(): String {
        val rawKey = secureStorage.getApiKey()
        return SecurityUtils.maskApiKey(rawKey)
    }

    fun saveApiKey(apiKey: String) = secureStorage.saveApiKey(apiKey)
    fun clearApiKey() = secureStorage.clearApiKey()

    fun getMaskedOpenAiApiKey(): String {
        val raw = secureStorage.getOpenAiApiKey()
        return SecurityUtils.maskApiKey(raw)
    }

    fun saveOpenAiApiKey(apiKey: String) = secureStorage.saveOpenAiApiKey(apiKey)
    fun clearOpenAiApiKey() = secureStorage.clearOpenAiApiKey()
    fun hasOpenAiApiKey(): Boolean = secureStorage.hasOpenAiApiKey()

    fun getMaskedGeminiApiKey(): String {
        val raw = secureStorage.getGeminiApiKey()
        return SecurityUtils.maskApiKey(raw)
    }

    fun saveGeminiApiKey(apiKey: String) = secureStorage.saveGeminiApiKey(apiKey)
    fun clearGeminiApiKey() = secureStorage.clearGeminiApiKey()
    fun hasGeminiApiKey(): Boolean = secureStorage.hasGeminiApiKey()

    fun getOpenRouterModel(): String = secureStorage.getOpenRouterModel()
    fun saveOpenRouterModel(model: String) = secureStorage.setOpenRouterModel(model)

    fun getMaskedOpenRouterApiKey(): String {
        val raw = secureStorage.getOpenRouterApiKey()
        return SecurityUtils.maskApiKey(raw)
    }

    fun saveOpenRouterApiKey(apiKey: String) = secureStorage.saveOpenRouterApiKey(apiKey)
    fun clearOpenRouterApiKey() = secureStorage.clearOpenRouterApiKey()
    fun hasOpenRouterApiKey(): Boolean = secureStorage.hasOpenRouterApiKey()

    fun getBaseUrl(): String = secureStorage.getBaseUrl()
    fun saveBaseUrl(url: String) {
        secureStorage.saveBaseUrl(url)
        refreshApiBaseUrl()
    }

    // --- Voice Preferences ---

    fun isVoiceCommandsEnabled(): Boolean = secureStorage.isVoiceCommandsEnabled()
    fun setVoiceCommandsEnabled(enabled: Boolean) = secureStorage.setVoiceCommandsEnabled(enabled)

    fun isVoiceRepliesEnabled(): Boolean = secureStorage.isVoiceRepliesEnabled()
    fun setVoiceRepliesEnabled(enabled: Boolean) = secureStorage.setVoiceRepliesEnabled(enabled)

    fun getVoiceLanguage(): String = secureStorage.getVoiceLanguage()
    fun setVoiceLanguage(language: String) = secureStorage.setVoiceLanguage(language)

    /**
     * Records local device actions / voice feedback into Room history.
     */
    suspend fun recordCommandInteraction(
        userText: String,
        replyText: String,
        isError: Boolean = false
    ): ChatMessage = withContext(Dispatchers.IO) {
        val userEntity = ChatMessageEntity(
            content = userText.trim(),
            sender = MessageSender.USER.name,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userEntity)

        val assistantEntity = ChatMessageEntity(
            content = replyText.trim(),
            sender = MessageSender.ASSISTANT.name,
            timestamp = System.currentTimeMillis() + 1,
            isError = isError
        )
        val id = chatDao.insertMessage(assistantEntity)
        assistantEntity.copy(id = id).toDomain()
    }

    private fun getProvider(type: AiProviderType): AiProvider {
        return when (type) {
            AiProviderType.LOCAL_TEST -> localTestProvider
            AiProviderType.OPENROUTER -> openRouterProvider
            AiProviderType.OPENAI -> openAiProvider
            AiProviderType.GEMINI -> geminiProvider
        }
    }

    private fun getProviderConfig(type: AiProviderType): AiProviderConfig {
        return when (type) {
            AiProviderType.LOCAL_TEST -> AiProviderConfig(
                providerType = AiProviderType.LOCAL_TEST,
                apiKey = "local_test",
                model = "local-offline-v1"
            )
            AiProviderType.OPENROUTER -> AiProviderConfig(
                providerType = AiProviderType.OPENROUTER,
                apiKey = secureStorage.getOpenRouterApiKey() ?: "",
                model = secureStorage.getOpenRouterModel()
            )
            AiProviderType.OPENAI -> AiProviderConfig(
                providerType = AiProviderType.OPENAI,
                apiKey = secureStorage.getOpenAiApiKey() ?: "",
                model = secureStorage.getOpenAiModel()
            )
            AiProviderType.GEMINI -> AiProviderConfig(
                providerType = AiProviderType.GEMINI,
                apiKey = secureStorage.getGeminiApiKey() ?: "",
                model = secureStorage.getGeminiModel()
            )
        }
    }

    /**
     * Sends a user prompt to the selected AI Provider with automatic fallback support.
     * @param userText The user's input.
     * @param forcedProvider Optional override (e.g., from voice command "Ask Gemini...").
     */
    suspend fun sendMessage(
        userText: String,
        forcedProvider: AiProviderType? = null
    ): NetworkResult<ChatMessage> = withContext(Dispatchers.IO) {
        val primaryType = forcedProvider ?: secureStorage.getSelectedProvider()
        val primaryConfig = getProviderConfig(primaryType)

        // 1. Insert user message locally
        val userEntity = ChatMessageEntity(
            content = userText.trim(),
            sender = MessageSender.USER.name,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userEntity)

        // 2. Validate primary API key (skip for LOCAL_TEST)
        if (primaryType != AiProviderType.LOCAL_TEST && primaryConfig.apiKey.isBlank()) {
            val missingKeyMsg = "${primaryType.displayName} API key is missing. Please configure it in Settings."
            val errorEntity = ChatMessageEntity(
                content = missingKeyMsg,
                sender = MessageSender.ASSISTANT.name,
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorEntity)
            return@withContext NetworkResult.Error(
                userFriendlyMessage = missingKeyMsg,
                errorType = NetworkResult.ErrorType.INVALID_API_KEY
            )
        }

        // 3. Check network connectivity (skip for LOCAL_TEST)
        if (primaryType != AiProviderType.LOCAL_TEST && !NetworkUtils.isNetworkAvailable(context)) {
            val errorMsg = "No internet connection detected. Please check your network and try again."
            val errorEntity = ChatMessageEntity(
                content = errorMsg,
                sender = MessageSender.ASSISTANT.name,
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorEntity)
            return@withContext NetworkResult.Error(
                userFriendlyMessage = errorMsg,
                errorType = NetworkResult.ErrorType.NO_INTERNET
            )
        }

        // 4. Retrieve conversation history
        val existingHistory = chatDao.getAllMessages().first().map { it.toDomain() }

        // 5. Attempt request with primary provider
        val primaryProviderImpl = getProvider(primaryType)
        val primaryResult = primaryProviderImpl.generateReply(
            prompt = userText,
            conversationHistory = existingHistory,
            config = primaryConfig
        )

        when (primaryResult) {
            is NetworkResult.Success -> {
                val assistantEntity = ChatMessageEntity(
                    content = primaryResult.data,
                    sender = MessageSender.ASSISTANT.name,
                    timestamp = System.currentTimeMillis(),
                    isError = false
                )
                val insertedId = chatDao.insertMessage(assistantEntity)
                NetworkResult.Success(assistantEntity.copy(id = insertedId).toDomain())
            }
            is NetworkResult.Error -> {
                // Check if automatic fallback is enabled and secondary provider is available
                val canFallback = secureStorage.isAutoFallbackEnabled() && forcedProvider == null
                val fallbackCandidates = listOf(
                    AiProviderType.OPENAI,
                    AiProviderType.GEMINI,
                    AiProviderType.OPENROUTER
                ).filter { it != primaryType }

                if (canFallback) {
                    for (secondaryType in fallbackCandidates) {
                        val secondaryConfig = getProviderConfig(secondaryType)
                        if (secondaryConfig.apiKey.isNotBlank()) {
                            val secondaryProviderImpl = getProvider(secondaryType)
                            val fallbackResult = secondaryProviderImpl.generateReply(
                                prompt = userText,
                                conversationHistory = existingHistory,
                                config = secondaryConfig
                            )

                            if (fallbackResult is NetworkResult.Success) {
                                val replyWithNotice = "[Fallback to ${secondaryType.displayName}]\n\n${fallbackResult.data}"
                                val assistantEntity = ChatMessageEntity(
                                    content = replyWithNotice,
                                    sender = MessageSender.ASSISTANT.name,
                                    timestamp = System.currentTimeMillis(),
                                    isError = false
                                )
                                val insertedId = chatDao.insertMessage(assistantEntity)
                                return@withContext NetworkResult.Success(assistantEntity.copy(id = insertedId).toDomain())
                            }
                        }
                    }
                }

                // If fallback was not enabled, unavailable, or failed as well, persist original primary error
                val errorEntity = ChatMessageEntity(
                    content = primaryResult.userFriendlyMessage,
                    sender = MessageSender.ASSISTANT.name,
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                chatDao.insertMessage(errorEntity)
                primaryResult
            }
        }
    }
}
