package com.rexyy.app.voice

import com.rexyy.app.network.provider.AiProviderType

sealed class VoiceCommandResult {
    data class Handled(val replyText: String, val openIntentSuccess: Boolean = true) : VoiceCommandResult()
    data class ForwardToAi(val prompt: String, val providerOverride: AiProviderType? = null) : VoiceCommandResult()
    data class RequiresConfirmation(
        val prompt: String,
        val commandToExecute: VoiceCommand
    ) : VoiceCommandResult()
    data class CollectMessageInput(
        val targetName: String,
        val isWhatsApp: Boolean,
        val prompt: String
    ) : VoiceCommandResult()
    data class Error(val errorMessage: String) : VoiceCommandResult()
}
