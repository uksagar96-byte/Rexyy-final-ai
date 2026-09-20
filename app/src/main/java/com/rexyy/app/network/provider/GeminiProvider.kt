package com.rexyy.app.network.provider

import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender
import com.rexyy.app.network.NetworkResult
import com.rexyy.app.network.gemini.GeminiApi
import com.rexyy.app.network.gemini.GeminiContent
import com.rexyy.app.network.gemini.GeminiGenerateContentRequest
import com.rexyy.app.network.gemini.GeminiGenerationConfig
import com.rexyy.app.network.gemini.GeminiPart
import java.io.IOException

class GeminiProvider(
    private val geminiApi: GeminiApi
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.GEMINI

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
            return NetworkResult.Error("Gemini API key is missing. Please configure it in Settings.")
        }

        // Build valid alternating conversation turns for Gemini
        val contentsList = mutableListOf<GeminiContent>()

        // Take up to last 10 non-error messages
        val validHistory = conversationHistory
            .filter { !it.isError && it.content.isNotBlank() }
            .takeLast(10)

        var lastRole: String? = null
        for (msg in validHistory) {
            val role = if (msg.sender == MessageSender.USER) "user" else "model"
            if (role != lastRole) {
                contentsList.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = msg.content))
                    )
                )
                lastRole = role
            }
        }

        // Ensure the current user prompt is added as the final 'user' turn
        if (lastRole == "user" && contentsList.isNotEmpty()) {
            // If the last history turn was also user, replace or append text
            val lastIdx = contentsList.lastIndex
            val existingText = contentsList[lastIdx].parts.firstOrNull()?.text ?: ""
            contentsList[lastIdx] = GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = "$existingText\n$prompt"))
            )
        } else {
            contentsList.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        }

        val request = GeminiGenerateContentRequest(
            contents = contentsList,
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = SYSTEM_PROMPT))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 2048
            )
        )

        return try {
            val response = geminiApi.generateContent(
                model = config.model,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val candidateText = body?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("\n")
                    ?.trim()

                if (!candidateText.isNullOrBlank()) {
                    NetworkResult.Success(candidateText)
                } else {
                    NetworkResult.Error("Gemini returned an empty response.")
                }
            } else {
                val code = response.code()
                when (code) {
                    400 -> NetworkResult.Error("Gemini request error: check model or request parameters.")
                    401, 403 -> NetworkResult.Error("Invalid Gemini API key or unauthorized. Please verify your key in Settings.")
                    429 -> NetworkResult.Error("Gemini rate limit or quota exceeded. Please try again shortly.")
                    404 -> NetworkResult.Error("Selected Gemini model '${config.model}' was not found.")
                    in 500..599 -> NetworkResult.Error("Google Gemini service temporarily unavailable (HTTP $code).")
                    else -> NetworkResult.Error("Gemini request failed (HTTP $code).")
                }
            }
        } catch (e: IOException) {
            NetworkResult.Error("Network error connecting to Google Gemini: ${e.localizedMessage ?: "Check your connection"}")
        } catch (e: Exception) {
            NetworkResult.Error("Unexpected Gemini error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
