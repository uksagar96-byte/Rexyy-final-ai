package com.rexyy.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val sender: String, // "USER" or "ASSISTANT" or "SYSTEM"
    val timestamp: Long,
    val isError: Boolean = false
) {
    fun toDomain(): ChatMessage {
        val senderEnum = try {
            MessageSender.valueOf(sender)
        } catch (e: Exception) {
            MessageSender.ASSISTANT
        }
        return ChatMessage(
            id = id,
            content = content,
            sender = senderEnum,
            timestamp = timestamp,
            isError = isError
        )
    }

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = message.id,
                content = message.content,
                sender = message.sender.name,
                timestamp = message.timestamp,
                isError = message.isError
            )
        }
    }
}
