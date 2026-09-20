package com.rexyy.app.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            // Redact Authorization and Gemini key headers to ensure no API keys are ever leaked to Logcat
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createOpenAiApi(baseUrl: String = "https://api.openai.com/v1/"): OpenAiApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(OpenAiApi::class.java)
    }

    fun createGeminiApi(baseUrl: String = "https://generativelanguage.googleapis.com/"): com.rexyy.app.network.gemini.GeminiApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(com.rexyy.app.network.gemini.GeminiApi::class.java)
    }

    /**
     * Translates common network and HTTP exceptions into user-friendly NetworkResult errors.
     */
    fun mapExceptionToError(e: Throwable): NetworkResult.Error {
        return when (e) {
            is UnknownHostException -> NetworkResult.Error(
                userFriendlyMessage = "No internet connection detected. Please check your network and try again.",
                errorType = NetworkResult.ErrorType.NO_INTERNET
            )
            is SocketTimeoutException -> NetworkResult.Error(
                userFriendlyMessage = "The AI service timed out while waiting for a response. Please try again.",
                errorType = NetworkResult.ErrorType.TIMEOUT
            )
            is IOException -> NetworkResult.Error(
                userFriendlyMessage = "Connection failed. Please check your network connection.",
                errorType = NetworkResult.ErrorType.NO_INTERNET
            )
            else -> NetworkResult.Error(
                userFriendlyMessage = "An unexpected error occurred: ${e.localizedMessage ?: "Unknown error"}",
                errorType = NetworkResult.ErrorType.UNKNOWN
            )
        }
    }

    /**
     * Translates HTTP status codes into user-friendly explanations.
     */
    fun mapHttpError(code: Int, errorBody: String?): NetworkResult.Error {
        // Try parsing error message from JSON body if present
        var apiMessage: String? = null
        if (!errorBody.isNullOrBlank()) {
            try {
                val adapter = moshi.adapter(ChatCompletionResponse::class.java)
                val parsed = adapter.fromJson(errorBody)
                apiMessage = parsed?.error?.message
            } catch (_: Exception) {
                // Ignore parse failures
            }
        }

        return when (code) {
            401 -> NetworkResult.Error(
                userFriendlyMessage = apiMessage ?: "Invalid or unauthorized API key. Please check your key in Settings.",
                statusCode = code,
                errorType = NetworkResult.ErrorType.INVALID_API_KEY
            )
            429 -> NetworkResult.Error(
                userFriendlyMessage = apiMessage ?: "Rate limit or quota exceeded. Please check your account credits or wait a moment.",
                statusCode = code,
                errorType = NetworkResult.ErrorType.RATE_LIMITED
            )
            in 500..599 -> NetworkResult.Error(
                userFriendlyMessage = "The AI server encountered an internal issue ($code). Please try again in a few moments.",
                statusCode = code,
                errorType = NetworkResult.ErrorType.SERVER_ERROR
            )
            else -> NetworkResult.Error(
                userFriendlyMessage = apiMessage ?: "Request failed with status $code. Please try again.",
                statusCode = code,
                errorType = NetworkResult.ErrorType.UNKNOWN
            )
        }
    }
}
