package com.rexyy.app.nlp.contract

/**
 * Versioned structured result contract returned by the AI / NLP interpretation layer
 * to the Android Kotlin Core.
 */
data class AiCommandResult(
    val requestId: String,
    val intent: String,
    val app: String? = null,
    val query: String? = null,
    val entities: Map<String, String> = emptyMap(),
    val confidence: Double = 1.0,
    val requiresConfirmation: Boolean = false,
    val error: String? = null,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isSuccess(): Boolean = error == null && intent.isNotBlank()

    companion object {
        fun error(requestId: String, message: String): AiCommandResult {
            return AiCommandResult(
                requestId = requestId,
                intent = "ERROR",
                confidence = 0.0,
                error = message
            )
        }
    }
}
