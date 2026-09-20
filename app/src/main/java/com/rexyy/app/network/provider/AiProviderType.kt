package com.rexyy.app.network.provider

enum class AiProviderType(val id: String, val displayName: String) {
    LOCAL_TEST("local_test", "Local Test Mode"),
    OPENROUTER("openrouter", "OpenRouter"),
    OPENAI("openai", "OpenAI"),
    GEMINI("gemini", "Google Gemini");

    val isGemini: Boolean get() = this == GEMINI
    val isOpenAi: Boolean get() = this == OPENAI
    val isOpenRouter: Boolean get() = this == OPENROUTER
    val isLocalTest: Boolean get() = this == LOCAL_TEST

    companion object {
        fun fromId(id: String?): AiProviderType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LOCAL_TEST
        }
    }
}
