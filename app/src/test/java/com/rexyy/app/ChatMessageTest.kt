package com.rexyy.app

import com.rexyy.app.data.local.ChatMessageEntity
import com.rexyy.app.model.ChatMessage
import com.rexyy.app.model.MessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatMessageTest {

    @Test
    fun testEntityDomainConversion() {
        val domain = ChatMessage(
            id = 42L,
            content = "Hello REXYY",
            sender = MessageSender.USER,
            timestamp = 1000L,
            isError = false
        )

        val entity = ChatMessageEntity.fromDomain(domain)
        assertEquals(42L, entity.id)
        assertEquals("Hello REXYY", entity.content)
        assertEquals("USER", entity.sender)
        assertEquals(1000L, entity.timestamp)
        assertFalse(entity.isError)

        val backToDomain = entity.toDomain()
        assertEquals(domain, backToDomain)
    }
}
