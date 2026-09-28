package com.rexyy.app.nlp

import com.rexyy.app.nlp.contract.AiCommandRequest
import com.rexyy.app.nlp.contract.AiCommandResult

/**
 * Interface defining the boundary between Android Kotlin Core and the AI / NLP engine.
 * Decouples NLP interpretation from Android system execution.
 */
interface AiNlpBridge {
    /**
     * Interprets natural language user input into a structured AI command result.
     *
     * @param request The versioned request containing user speech/text and language context.
     * @return Versioned structured interpretation result.
     */
    suspend fun interpret(request: AiCommandRequest): AiCommandResult
}
