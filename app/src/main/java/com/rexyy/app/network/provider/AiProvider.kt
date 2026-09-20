package com.rexyy.app.network.provider

import com.rexyy.app.model.ChatMessage
import com.rexyy.app.network.NetworkResult

interface AiProvider {
    val providerType: AiProviderType

    suspend fun generateReply(
        prompt: String,
        conversationHistory: List<ChatMessage>,
        config: AiProviderConfig
    ): NetworkResult<String>
}
