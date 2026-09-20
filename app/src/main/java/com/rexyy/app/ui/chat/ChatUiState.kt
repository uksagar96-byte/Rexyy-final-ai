package com.rexyy.app.ui.chat

import com.rexyy.app.confirmation.ConfirmationRequest
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.model.ChatMessage
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.task.TaskPlan
import com.rexyy.app.voice.VoiceState

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedProvider: AiProviderType = AiProviderType.OPENAI,
    val currentModel: String = "gpt-4o-mini",
    val openAiModel: String = "gpt-4o-mini",
    val geminiModel: String = "gemini-3.5-flash",
    val openRouterModel: String = "google/gemini-2.5-flash",
    val maskedApiKey: String = "",
    val maskedOpenAiApiKey: String = "",
    val maskedGeminiApiKey: String = "",
    val maskedOpenRouterApiKey: String = "",
    val hasApiKey: Boolean = false,
    val isAutoFallbackEnabled: Boolean = true,
    val voiceState: VoiceState = VoiceState.IDLE,
    val isVoiceCommandsEnabled: Boolean = true,
    val isVoiceRepliesEnabled: Boolean = true,
    val voiceLanguage: String = SecureStorage.VOICE_LANG_DEFAULT,
    val voiceStatusMessage: String? = null,
    val isSetupCompleted: Boolean = false,
    val userName: String = SecureStorage.DEFAULT_USER_NAME,
    val assistantName: String = SecureStorage.DEFAULT_ASSISTANT_NAME,
    val isListening: Boolean = false,
    val lastActionFeedback: String? = null,
    val pendingConfirmation: ConfirmationRequest? = null,
    val pendingMessageTarget: Pair<String, Boolean>? = null, // targetName to isWhatsApp
    val activeTaskPlan: TaskPlan? = null
)
