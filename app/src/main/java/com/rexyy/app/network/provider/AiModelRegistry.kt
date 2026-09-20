package com.rexyy.app.network.provider

object AiModelRegistry {

    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
    const val DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash"

    val openRouterModels: List<String> = listOf(
        "google/gemini-2.5-flash",
        "google/gemini-2.0-flash-001",
        "openai/gpt-4o-mini",
        "openai/gpt-4o",
        "anthropic/claude-3.5-sonnet",
        "deepseek/deepseek-chat",
        "meta-llama/llama-3.3-70b-instruct"
    )

    val openAiModels: List<String> = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "o3-mini",
        "o1-mini",
        "gpt-3.5-turbo"
    )

    val geminiModels: List<String> = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview",
        "gemini-2.5-flash"
    )

    fun getAvailableModels(provider: AiProviderType): List<String> {
        return when (provider) {
            AiProviderType.OPENROUTER -> openRouterModels
            AiProviderType.OPENAI -> openAiModels
            AiProviderType.GEMINI -> geminiModels
            AiProviderType.LOCAL_TEST -> listOf("local-autonomous")
        }
    }

    fun getDefaultModel(provider: AiProviderType): String {
        return when (provider) {
            AiProviderType.OPENROUTER -> DEFAULT_OPENROUTER_MODEL
            AiProviderType.OPENAI -> DEFAULT_OPENAI_MODEL
            AiProviderType.GEMINI -> DEFAULT_GEMINI_MODEL
            AiProviderType.LOCAL_TEST -> "local-autonomous"
        }
    }

    fun isValidModel(provider: AiProviderType, model: String): Boolean {
        return getAvailableModels(provider).contains(model)
    }
}
