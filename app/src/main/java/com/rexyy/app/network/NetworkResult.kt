package com.rexyy.app.network

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(
        val userFriendlyMessage: String,
        val statusCode: Int? = null,
        val errorType: ErrorType = ErrorType.UNKNOWN
    ) : NetworkResult<Nothing>

    enum class ErrorType {
        NO_INTERNET,
        TIMEOUT,
        INVALID_API_KEY,
        RATE_LIMITED,
        SERVER_ERROR,
        MALFORMED_RESPONSE,
        UNKNOWN
    }
}
