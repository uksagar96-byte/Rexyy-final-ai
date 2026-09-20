package com.rexyy.app.network.provider

import com.rexyy.app.model.ChatMessage
import com.rexyy.app.network.NetworkResult

class LocalTestProvider : AiProvider {

    override val providerType: AiProviderType = AiProviderType.LOCAL_TEST

    override suspend fun generateReply(
        prompt: String,
        conversationHistory: List<ChatMessage>,
        config: AiProviderConfig
    ): NetworkResult<String> {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

        val reply = when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") || lower.contains("namaste") ->
                "Hello! I am REXYY operating in Local Test Mode. All local device controls, alarms, speech synthesis, and settings actions are fully functional. Connect an OpenAI or Gemini API key in Settings to unlock deep generative AI conversations."

            lower.contains("who are you") || lower.contains("what are you") ->
                "I am REXYY, your personal AI assistant. In Local Test Mode, I can control device capabilities, open apps, set alarms, and adjust volume completely offline without an API key."

            lower.contains("help") || lower.contains("what can you do") ->
                "In Local Test Mode, you can test commands like:\n• 'Open YouTube'\n• 'Set alarm for 7 AM'\n• 'Open Settings'\n• 'Increase volume'\n• 'Search Google for...'\n• 'Turn on Bluetooth'\n\nAdd an OpenAI or Gemini API key in Settings for general conversational AI."

            lower.contains("test") ->
                "Local Test Mode is working properly! Audio recognition, Text-to-Speech, and device actions are online."

            else ->
                "REXYY Local Mode: Received \"$trimmed\". This offline mode executes device controls and local tasks. To generate conversational AI answers for this query, configure an OpenAI or Gemini API key in Settings."
        }

        return NetworkResult.Success(reply)
    }
}
