package com.rexyy.app.router

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Diagnostic trace for local command parsing, entity extraction, execution, and verification.
 */
data class CommandDiagnostic(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawCommand: String,
    val detectedIntent: String,
    val extractedEntities: Map<String, Any?> = emptyMap(),
    val targetApp: String? = null,
    val searchQuery: String? = null,
    val launchMethod: String? = null,
    val searchMethod: String? = null,
    val executionResult: String,
    val verificationResult: String,
    val failureReason: String? = null,
    val latencyMs: Long = 0L
)

object CommandDiagnosticLogger {
    private const val TAG = "RexyyDiagnostic"
    private val _traces = MutableStateFlow<List<CommandDiagnostic>>(emptyList())
    val traces: StateFlow<List<CommandDiagnostic>> = _traces.asStateFlow()

    fun log(trace: CommandDiagnostic) {
        val status = if (trace.failureReason == null) "SUCCESS" else "FAILURE"
        Log.i(TAG, "[$status] Intent: ${trace.detectedIntent} | Cmd: \"${trace.rawCommand}\" | App: ${trace.targetApp} | Query: \"${trace.searchQuery}\" | Launch: ${trace.launchMethod} | Search: ${trace.searchMethod} | Result: ${trace.executionResult} | Verif: ${trace.verificationResult}${trace.failureReason?.let { " | Reason: $it" } ?: ""}")

        _traces.value = (listOf(trace) + _traces.value).take(100)
    }

    fun log(
        rawCommand: String,
        detectedIntent: String,
        extractedEntities: Map<String, Any?> = emptyMap(),
        targetApp: String? = null,
        searchQuery: String? = null,
        launchMethod: String? = null,
        searchMethod: String? = null,
        executionResult: String,
        verificationResult: String,
        failureReason: String? = null,
        latencyMs: Long = 0L
    ) {
        val app = targetApp ?: (extractedEntities["targetApp"] as? String) ?: (extractedEntities["app"] as? String)
        val query = searchQuery ?: (extractedEntities["query"] as? String)
        val lMethod = launchMethod ?: (extractedEntities["launchMethod"] as? String)
        val sMethod = searchMethod ?: (extractedEntities["searchMethod"] as? String)

        log(
            CommandDiagnostic(
                rawCommand = rawCommand,
                detectedIntent = detectedIntent,
                extractedEntities = extractedEntities,
                targetApp = app,
                searchQuery = query,
                launchMethod = lMethod,
                searchMethod = sMethod,
                executionResult = executionResult,
                verificationResult = verificationResult,
                failureReason = failureReason,
                latencyMs = latencyMs
            )
        )
    }

    fun log(
        rawCommand: String,
        detectedIntent: String,
        extractedEntities: Map<String, Any?> = emptyMap(),
        executionResult: String,
        verificationResult: String,
        failureReason: String? = null,
        latencyMs: Long = 0L
    ) {
        log(
            rawCommand = rawCommand,
            detectedIntent = detectedIntent,
            extractedEntities = extractedEntities,
            targetApp = null,
            searchQuery = null,
            launchMethod = null,
            searchMethod = null,
            executionResult = executionResult,
            verificationResult = verificationResult,
            failureReason = failureReason,
            latencyMs = latencyMs
        )
    }

    fun getLatestTrace(): CommandDiagnostic? = _traces.value.firstOrNull()

    fun getLatest(): CommandDiagnostic? = getLatestTrace()

    fun clear() {
        _traces.value = emptyList()
    }
}
