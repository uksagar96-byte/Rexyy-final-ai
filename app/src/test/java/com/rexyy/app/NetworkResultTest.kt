package com.rexyy.app

import com.rexyy.app.network.ApiClientFactory
import com.rexyy.app.network.NetworkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkResultTest {

    @Test
    fun testTimeoutExceptionMapping() {
        val error = ApiClientFactory.mapExceptionToError(SocketTimeoutException("Read timeout"))
        assertEquals(NetworkResult.ErrorType.TIMEOUT, error.errorType)
        assertTrue(error.userFriendlyMessage.contains("timed out", ignoreCase = true))
    }

    @Test
    fun testUnknownHostExceptionMapping() {
        val error = ApiClientFactory.mapExceptionToError(UnknownHostException("Unable to resolve host"))
        assertEquals(NetworkResult.ErrorType.NO_INTERNET, error.errorType)
        assertTrue(error.userFriendlyMessage.contains("No internet", ignoreCase = true))
    }

    @Test
    fun testHttp401ErrorMapping() {
        val error = ApiClientFactory.mapHttpError(401, """{"error":{"message":"Incorrect API key provided"}}""")
        assertEquals(NetworkResult.ErrorType.INVALID_API_KEY, error.errorType)
        assertEquals(401, error.statusCode)
        assertTrue(error.userFriendlyMessage.contains("Incorrect API key", ignoreCase = true))
    }

    @Test
    fun testHttp429ErrorMapping() {
        val error = ApiClientFactory.mapHttpError(429, null)
        assertEquals(NetworkResult.ErrorType.RATE_LIMITED, error.errorType)
        assertEquals(429, error.statusCode)
        assertTrue(error.userFriendlyMessage.contains("Rate limit", ignoreCase = true))
    }
}
