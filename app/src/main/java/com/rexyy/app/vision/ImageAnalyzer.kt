package com.rexyy.app.vision

import android.graphics.Bitmap
import android.net.Uri

sealed class ImageInput {
    data class FromUri(val uri: Uri) : ImageInput()
    data class FromBitmap(val bitmap: Bitmap) : ImageInput()
    data class FromByteArray(val bytes: ByteArray) : ImageInput() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FromByteArray
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

data class ImageAnalysisResult(
    val description: String,
    val detectedObjects: List<String> = emptyList(),
    val extractedText: String? = null,
    val isSuccess: Boolean = true
)

interface ImageAnalyzer {
    suspend fun analyze(input: ImageInput, prompt: String = "Describe what is visible in this image."): ImageAnalysisResult
}
