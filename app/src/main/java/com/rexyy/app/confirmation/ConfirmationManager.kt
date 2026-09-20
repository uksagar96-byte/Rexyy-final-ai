package com.rexyy.app.confirmation

import com.rexyy.app.voice.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConfirmationRequest(
    val prompt: String,
    val command: VoiceCommand
)

class ConfirmationManager {

    private val _pending = MutableStateFlow<ConfirmationRequest?>(null)
    val pending: StateFlow<ConfirmationRequest?> = _pending.asStateFlow()

    fun requestConfirmation(prompt: String, command: VoiceCommand) {
        _pending.value = ConfirmationRequest(prompt, command)
    }

    fun confirmPending(): VoiceCommand? {
        val current = _pending.value
        _pending.value = null
        return current?.command
    }

    fun cancelPending(): Boolean {
        if (_pending.value != null) {
            _pending.value = null
            return true
        }
        return false
    }

    fun hasPending(): Boolean = _pending.value != null
}
