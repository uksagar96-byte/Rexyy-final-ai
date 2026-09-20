package com.rexyy.app

import com.rexyy.app.utils.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {

    @Test
    fun testMaskApiKey() {
        assertEquals("", SecurityUtils.maskApiKey(null))
        assertEquals("", SecurityUtils.maskApiKey(""))
        assertEquals("••••••••", SecurityUtils.maskApiKey("short"))
        
        val maskedOpenAi = SecurityUtils.maskApiKey("sk-proj-1234567890abcdef")
        assertTrue(maskedOpenAi.startsWith("sk-••••"))
        assertTrue(maskedOpenAi.endsWith("cdef"))
        assertFalse(maskedOpenAi.contains("1234567890"))
    }

    @Test
    fun testIsValidApiKey() {
        assertFalse(SecurityUtils.isValidApiKey(null))
        assertFalse(SecurityUtils.isValidApiKey(""))
        assertFalse(SecurityUtils.isValidApiKey("short"))
        assertTrue(SecurityUtils.isValidApiKey("sk-1234567890abcdef"))
    }
}
