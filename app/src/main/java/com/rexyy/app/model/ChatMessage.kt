package com.rexyy.app.model

data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)
