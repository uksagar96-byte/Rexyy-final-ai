package com.rexyy.app.model

data class AiConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
    }
}
