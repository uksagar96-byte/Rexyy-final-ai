package com.rexyy.app.network.provider

import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender
import com.rexyy.app.network.ApiMessage
import com.rexyy.app.network.ChatCompletionRequest
import com.rexyy.app.network.NetworkResult
import com.rexyy.app.network.OpenAiApi
import java.io.IOException

class OpenAiProvider(
    private val openAiApi: OpenAiApi
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.OPENAI

    companion object {
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
            return NetworkResult.Error("OpenAI API key is missing. Please configure it in Settings.")
        }

        // Build history window (last 10 messages for context)
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

        val request = ChatCompletionRequest(
            model = config.model,
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
                    NetworkResult.Error("OpenAI returned an empty response.")
                }
            } else {
                val code = response.code()
                when (code) {
                    401 -> NetworkResult.Error("Invalid OpenAI API key. Please check your key in Settings.")
                    429 -> NetworkResult.Error("OpenAI rate limit or quota exceeded. Please check your usage plan.")
                    404 -> NetworkResult.Error("Selected OpenAI model '${config.model}' was not found or is unavailable.")
                    in 500..599 -> NetworkResult.Error("OpenAI service temporarily unavailable (HTTP $code).")
                    else -> NetworkResult.Error("OpenAI request failed (HTTP $code).")
                }
            }
        } catch (e: IOException) {
            NetworkResult.Error("Network connection error connecting to OpenAI: ${e.localizedMessage ?: "Check your internet"}")
        } catch (e: Exception) {
            NetworkResult.Error("Unexpected OpenAI error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
