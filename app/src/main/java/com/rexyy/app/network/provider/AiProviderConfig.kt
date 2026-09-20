package com.rexyy.app.network.provider

data class AiProviderConfig(
    val providerType: AiProviderType,
    val apiKey: String,
    val model: String
)
