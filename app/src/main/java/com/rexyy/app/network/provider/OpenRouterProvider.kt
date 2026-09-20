package com.rexyy.app.network.provider

import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender
import com.rexyy.app.network.ApiClientFactory
import com.rexyy.app.network.ApiMessage
import com.rexyy.app.network.ChatCompletionRequest
import com.rexyy.app.network.NetworkResult
import com.rexyy.app.network.OpenAiApi
import java.io.IOException

class OpenRouterProvider(
    private val openAiApi: OpenAiApi = ApiClientFactory.createOpenAiApi(BASE_URL)
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.OPENROUTER

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1/"
        const val SYSTEM_PROMPT =
            "You are REXYY, an intelligent, sleek, and highly capable Android phone AI commander and personal assistant. " +
            "You can control the phone, open apps, send WhatsApp messages, make calls, adjust volume and brightness, set alarms and timers. " +
            "Provide helpful, concise, well-formatted, and accurate responses. " +
            "When the user asks you to perform a phone action, acknowledge it helpfully and append the appropriate action tag at the end: " +
            "[ACTION: OPEN_APP(\"<app>\")] or [ACTION: WHATSAPP(\"<target>\", \"<message>\")] or [ACTION: CALL(\"<contact>\")] or [ACTION: ALARM(<hour>, <minute>)] or [ACTION: TIMER(<seconds>)] or [ACTION: CAMERA]. " +
            "Never say 'I can't open apps' or 'I cannot perform phone actions' because you can execute them through REXYY device integration."
    }

    override suspend fun generateReply(
        prompt: String,
        conversationHistory: List<ChatMessage>,
        config: AiProviderConfig
    ): NetworkResult<String> {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) {
            return NetworkResult.Error("OpenRouter API key is missing. Please configure it in Settings.")
        }

        val historyTurns = conversationHistory.takeLast(10).map { msg ->
            ApiMessage(
                role = if (msg.sender == MessageSender.USER) "user" else "assistant",
                content = msg.content
            )
        }

        val requestMessages = mutableListOf<ApiMessage>().apply {
            add(ApiMessage(role = "system", content = SYSTEM_PROMPT))
            addAll(historyTurns)
            add(ApiMessage(role = "user", content = prompt))
        }

        val modelToUse = config.model.ifBlank { AiModelRegistry.DEFAULT_OPENROUTER_MODEL }

        val request = ChatCompletionRequest(
            model = modelToUse,
            messages = requestMessages,
            temperature = 0.7
        )

        return try {
            val response = openAiApi.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val replyContent = body?.choices?.firstOrNull()?.message?.content?.trim()
                if (!replyContent.isNullOrBlank()) {
                    NetworkResult.Success(replyContent)
                } else {
                    NetworkResult.Error("OpenRouter returned an empty response.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val mapped = ApiClientFactory.mapHttpError(response.code(), errorBody)
                if (response.code() == 401) {
                    NetworkResult.Error("Invalid OpenRouter API key. Please check your key at openrouter.ai/keys and update Settings.")
                } else if (response.code() == 402 || response.code() == 429) {
                    NetworkResult.Error("OpenRouter quota/credits exhausted or rate limit reached. Please check your OpenRouter balance.")
                } else {
                    mapped
                }
            }
        } catch (e: IOException) {
            ApiClientFactory.mapExceptionToError(e)
        } catch (e: Exception) {
            NetworkResult.Error("OpenRouter communication error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun testConnection(apiKey: String, model: String): NetworkResult<String> {
        return generateReply(
            prompt = "Hi REXYY, test connection.",
            conversationHistory = emptyList(),
            config = AiProviderConfig(
                providerType = AiProviderType.OPENROUTER,
                apiKey = apiKey,
                model = model.ifBlank { AiModelRegistry.DEFAULT_OPENROUTER_MODEL }
            )
        )
    }
}
