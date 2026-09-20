package com.rexyy.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    @field:Json(name = "model") val model: String,
    @field:Json(name = "messages") val messages: List<ApiMessage>,
    @field:Json(name = "temperature") val temperature: Double = 0.7,
    @field:Json(name = "max_tokens") val maxTokens: Int = 2000
)

@JsonClass(generateAdapter = true)
data class ApiMessage(
    @field:Json(name = "role") val role: String, // "system", "user", "assistant"
    @field:Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @field:Json(name = "id") val id: String? = null,
    @field:Json(name = "choices") val choices: List<ChoiceDto>? = null,
    @field:Json(name = "error") val error: ApiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    @field:Json(name = "index") val index: Int? = null,
    @field:Json(name = "message") val message: ApiMessage? = null,
    @field:Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    @field:Json(name = "message") val message: String? = null,
    @field:Json(name = "type") val type: String? = null,
    @field:Json(name = "code") val code: String? = null
)
