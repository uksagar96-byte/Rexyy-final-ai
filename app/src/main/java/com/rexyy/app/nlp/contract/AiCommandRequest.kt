package com.rexyy.app.nlp.contract

import java.util.UUID

/**
 * Versioned structured request contract passed from the Android Kotlin Core
 * to the AI / NLP interpretation layer.
 */
data class AiCommandRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val rawInput: String,
    val language: String = "en",
    val contextMetadata: Map<String, String> = emptyMap(),
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
