package com.rexyy.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rexyy.app.confirmation.ConfirmationManager
import com.rexyy.app.network.NetworkResult
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.repository.AssistantRepository
import com.rexyy.app.router.AiActionTagParser
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.task.TaskExecutor
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandExecutor
import com.rexyy.app.voice.VoiceCommandResult
import com.rexyy.app.voice.VoiceInputManager
import com.rexyy.app.voice.VoiceState
import com.rexyy.app.voice.VoiceTtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val repository: AssistantRepository
) : AndroidViewModel(application) {

    // Secondary constructor to ensure Java bytecode compatibility for AndroidViewModelFactory reflection
    constructor(application: Application) : this(
        application = application,
        repository = AssistantRepository(application)
    )

    private val confirmationManager = ConfirmationManager()
    private val taskExecutor = TaskExecutor(application)

    private val voiceInputManager = VoiceInputManager(
        context = application,
        onListeningStateChanged = { listening ->
            _uiState.update {
                it.copy(
                    voiceState = if (listening) VoiceState.LISTENING else if (it.voiceState == VoiceState.LISTENING) VoiceState.IDLE else it.voiceState,
                    voiceStatusMessage = if (listening) "Listening... Speak now" else null
                )
            }
        },
        onSpeechRecognized = { spokenText ->
            handleVoiceInput(spokenText)
        },
        onError = { errorMsg ->
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.IDLE,
                    voiceStatusMessage = null,
                    errorMessage = errorMsg
                )
            }
        }
    )

    private val voiceTtsManager = VoiceTtsManager(
        context = application,
        onSpeakingStateChanged = { speaking ->
            _uiState.update {
                it.copy(
                    voiceState = if (speaking) VoiceState.SPEAKING else if (it.voiceState == VoiceState.SPEAKING) VoiceState.IDLE else it.voiceState,
                    voiceStatusMessage = if (speaking) "REXYY is speaking..." else null
                )
            }
        }
    )

    private val _uiState = MutableStateFlow(
        ChatUiState(
            hasApiKey = repository.hasApiKey(),
            maskedApiKey = repository.getMaskedApiKey(),
            selectedProvider = repository.getSelectedProvider(),
            currentModel = repository.getSelectedModel(),
            openAiModel = repository.getOpenAiModel(),
            geminiModel = repository.getGeminiModel(),
            openRouterModel = repository.getOpenRouterModel(),
            maskedOpenAiApiKey = repository.getMaskedOpenAiApiKey(),
            maskedGeminiApiKey = repository.getMaskedGeminiApiKey(),
            maskedOpenRouterApiKey = repository.getMaskedOpenRouterApiKey(),
            isAutoFallbackEnabled = repository.isAutoFallbackEnabled(),
            isVoiceCommandsEnabled = repository.isVoiceCommandsEnabled(),
            isVoiceRepliesEnabled = repository.isVoiceRepliesEnabled(),
            voiceLanguage = repository.getVoiceLanguage(),
            isSetupCompleted = repository.secureStorage.isSetupCompleted(),
            userName = repository.secureStorage.getUserName(),
            assistantName = repository.secureStorage.getAssistantName()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadConversationHistory()
        observeConfirmation()
        observeActiveTask()
    }

    private fun loadConversationHistory() {
        viewModelScope.launch {
            repository.getConversationHistory().collect { messageList ->
                _uiState.update { it.copy(messages = messageList) }
            }
        }
    }

    private fun observeConfirmation() {
        viewModelScope.launch {
            confirmationManager.pending.collect { req ->
                _uiState.update { it.copy(pendingConfirmation = req) }
            }
        }
    }

    private fun observeActiveTask() {
        viewModelScope.launch {
            taskExecutor.activeTask.collect { task ->
                _uiState.update { it.copy(activeTaskPlan = task) }
            }
        }
    }

    fun onInputChange(newText: String) {
        _uiState.update { it.copy(inputText = newText, errorMessage = null) }
    }

    /**
     * Unified send method for text input: shares the exact same command router as voice input.
     */
    fun sendMessage() {
        val currentText = _uiState.value.inputText.trim()
        if (currentText.isBlank() || _uiState.value.isLoading) return

        _uiState.update {
            it.copy(
                inputText = "",
                errorMessage = null
            )
        }

        processUnifiedInput(currentText, isVoice = false)
    }

    /**
     * Unified entry point for voice input.
     */
    private fun handleVoiceInput(recognizedText: String) {
        val trimmed = recognizedText.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(voiceState = VoiceState.IDLE, voiceStatusMessage = null) }
            return
        }

        _uiState.update {
            it.copy(
                inputText = trimmed,
                voiceState = VoiceState.PROCESSING,
                voiceStatusMessage = "Processing: \"$trimmed\""
            )
        }

        processUnifiedInput(trimmed, isVoice = true)
    }

    /**
     * Core Command Processing Pipeline shared by BOTH voice and text inputs.
     */
    private fun processUnifiedInput(input: String, isVoice: Boolean) {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Check if user is answering an active confirmation prompt or providing message body
        if (_uiState.value.pendingMessageTarget != null) {
            val (target, isWhatsApp) = _uiState.value.pendingMessageTarget!!
            _uiState.update { it.copy(pendingMessageTarget = null) }
            val nextCmd = if (isWhatsApp) {
                VoiceCommand.WhatsAppMessage(target = target, body = trimmed, rawInput = trimmed)
            } else {
                VoiceCommand.SendMessage(target = target, body = trimmed, rawInput = trimmed)
            }
            executeLocalDeviceCommand(nextCmd, trimmed, isVoice)
            return
        }

        if (confirmationManager.hasPending()) {
            val isAffirmative = lower in listOf("yes", "haan", "ha", "confirm", "send", "call", "bhejo", "karo", "sure", "ok", "yep", "do it")
            val isNegative = lower in listOf("no", "nahi", "cancel", "mat karo", "stop", "nevermind", "nah", "abort")

            if (isAffirmative) {
                confirmPendingAction()
                return
            } else if (isNegative) {
                cancelPendingAction()
                return
            }
        }

        // 2. Route via RexyyCommandRouter (Local-first fast evaluation)
        val command = RexyyCommandRouter.route(trimmed)

        when (command) {
            is VoiceCommand.AiChat -> {
                // Conversational request -> Send to AI model
                _uiState.update { it.copy(inputText = "", isLoading = true) }
                executeAiMessage(command.prompt, isSpokenResponseRequested = isVoice, providerOverride = command.providerOverride)
            }
            is VoiceCommand.MultiStepTask -> {
                // Sequential task plan -> Execute multi-step task
                executeMultiStepTask(command, isVoice)
            }
            else -> {
                // Local phone action -> Execute immediately with zero AI latency
                executeLocalDeviceCommand(command, trimmed, isVoice)
            }
        }
    }

    private fun executeMultiStepTask(command: VoiceCommand.MultiStepTask, isVoice: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    inputText = "",
                    voiceState = VoiceState.PROCESSING,
                    voiceStatusMessage = "Running task: ${command.description}"
                )
            }

            val plan = com.rexyy.app.task.TaskPlan(
                id = java.util.UUID.randomUUID().toString(),
                title = command.description,
                steps = command.steps.mapIndexed { idx, cmd ->
                    com.rexyy.app.task.TaskStep(
                        id = "step_${idx + 1}",
                        description = (cmd as? VoiceCommand.OpenApp)?.let { "Open ${it.appName}" }
                            ?: (cmd as? VoiceCommand.GoogleSearch)?.let { "Search ${it.query}" }
                            ?: "Step ${idx + 1}",
                        command = cmd
                    )
                }
            )

            val result = taskExecutor.executePlan(plan) { updatedPlan ->
                _uiState.update { it.copy(activeTaskPlan = updatedPlan) }
            }

            val replyText = when (result) {
                is VoiceCommandResult.Handled -> result.replyText
                is VoiceCommandResult.Error -> result.errorMessage
                else -> "Task finished."
            }

            repository.recordCommandInteraction(command.rawInput, replyText, isError = result is VoiceCommandResult.Error)

            _uiState.update {
                it.copy(
                    voiceState = if (isVoice && it.isVoiceRepliesEnabled) VoiceState.SPEAKING else VoiceState.IDLE,
                    voiceStatusMessage = null,
                    lastActionFeedback = replyText
                )
            }

            if (isVoice && _uiState.value.isVoiceRepliesEnabled) {
                voiceTtsManager.speak(replyText, _uiState.value.voiceLanguage)
            }
        }
    }

    private fun executeLocalDeviceCommand(command: VoiceCommand, rawInput: String, isVoice: Boolean) {
        viewModelScope.launch {
            val result = VoiceCommandExecutor.execute(command, getApplication())
            when (result) {
                is VoiceCommandResult.Handled -> {
                    repository.recordCommandInteraction(rawInput, result.replyText, isError = false)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            voiceState = if (isVoice && it.isVoiceRepliesEnabled) VoiceState.SPEAKING else VoiceState.IDLE,
                            voiceStatusMessage = null,
                            lastActionFeedback = result.replyText
                        )
                    }
                    if (isVoice && _uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(result.replyText, _uiState.value.voiceLanguage)
                    }
                }
                is VoiceCommandResult.RequiresConfirmation -> {
                    confirmationManager.requestConfirmation(result.prompt, result.commandToExecute)
                    repository.recordCommandInteraction(rawInput, result.prompt, isError = false)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            voiceState = if (isVoice && it.isVoiceRepliesEnabled) VoiceState.SPEAKING else VoiceState.IDLE,
                            voiceStatusMessage = null
                        )
                    }
                    if (isVoice && _uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(result.prompt, _uiState.value.voiceLanguage)
                    }
                }
                is VoiceCommandResult.CollectMessageInput -> {
                    repository.recordCommandInteraction(rawInput, result.prompt, isError = false)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            voiceState = if (isVoice && it.isVoiceRepliesEnabled) VoiceState.SPEAKING else VoiceState.IDLE,
                            voiceStatusMessage = null,
                            lastActionFeedback = result.prompt,
                            pendingMessageTarget = Pair(result.targetName, result.isWhatsApp)
                        )
                    }
                    if (isVoice && _uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(result.prompt, _uiState.value.voiceLanguage)
                    }
                }
                is VoiceCommandResult.ForwardToAi -> {
                    _uiState.update { it.copy(inputText = "", isLoading = true) }
                    executeAiMessage(result.prompt, isSpokenResponseRequested = isVoice, providerOverride = result.providerOverride)
                }
                is VoiceCommandResult.Error -> {
                    repository.recordCommandInteraction(rawInput, result.errorMessage, isError = true)
                    _uiState.update {
                        it.copy(
                            inputText = "",
                            voiceState = VoiceState.IDLE,
                            voiceStatusMessage = null,
                            errorMessage = result.errorMessage
                        )
                    }
                    if (isVoice && _uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(result.errorMessage, _uiState.value.voiceLanguage)
                    }
                }
            }
        }
    }

    fun confirmPendingAction() {
        val confirmedCmd = confirmationManager.confirmPending() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingConfirmation = null) }
            val result = VoiceCommandExecutor.execute(confirmedCmd, getApplication())
            when (result) {
                is VoiceCommandResult.Handled -> {
                    repository.recordCommandInteraction("Confirm", result.replyText, isError = false)
                    _uiState.update { it.copy(lastActionFeedback = result.replyText) }
                    if (_uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(result.replyText, _uiState.value.voiceLanguage)
                    }
                }
                is VoiceCommandResult.Error -> {
                    repository.recordCommandInteraction("Confirm", result.errorMessage, isError = true)
                    _uiState.update { it.copy(errorMessage = result.errorMessage) }
                }
                else -> Unit
            }
        }
    }

    fun cancelPendingAction() {
        if (confirmationManager.cancelPending()) {
            viewModelScope.launch {
                repository.recordCommandInteraction("Cancel", "Action cancelled.", isError = false)
                _uiState.update {
                    it.copy(
                        pendingConfirmation = null,
                        lastActionFeedback = "Action cancelled."
                    )
                }
            }
        }
    }

    fun cancelActiveTask() {
        taskExecutor.cancelCurrentTask()
        _uiState.update { it.copy(activeTaskPlan = null) }
    }

    private fun executeAiMessage(
        promptText: String,
        isSpokenResponseRequested: Boolean,
        providerOverride: AiProviderType? = null
    ) {
        viewModelScope.launch {
            val result = repository.sendMessage(promptText, providerOverride)
            when (result) {
                is NetworkResult.Success -> {
                    val rawContent = result.data.content
                    val parsed = AiActionTagParser.parse(rawContent)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            voiceState = if (isSpokenResponseRequested && it.isVoiceRepliesEnabled) VoiceState.SPEAKING else VoiceState.IDLE,
                            voiceStatusMessage = null
                        )
                    }

                    if (isSpokenResponseRequested && _uiState.value.isVoiceRepliesEnabled) {
                        voiceTtsManager.speak(parsed.displayText, _uiState.value.voiceLanguage)
                    }

                    // If the AI model identified an action, execute it seamlessly!
                    if (parsed.actionToExecute != null) {
                        val actionResult = VoiceCommandExecutor.execute(parsed.actionToExecute, getApplication())
                        if (actionResult is VoiceCommandResult.Handled) {
                            _uiState.update { it.copy(lastActionFeedback = actionResult.replyText) }
                        }
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            voiceState = VoiceState.IDLE,
                            voiceStatusMessage = null,
                            errorMessage = result.userFriendlyMessage
                        )
                    }
                }
            }
        }
    }

    fun startVoiceInput() {
        if (voiceTtsManager.isSpeaking()) {
            voiceTtsManager.stop()
        }
        if (!voiceInputManager.isAvailable()) {
            _uiState.update {
                it.copy(errorMessage = "Speech recognition is not available on this device.")
            }
            return
        }
        voiceInputManager.startListening(_uiState.value.voiceLanguage)
    }

    fun stopVoiceInput() {
        voiceInputManager.stopListening()
    }

    fun cancelVoiceInput() {
        voiceInputManager.cancel()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE, voiceStatusMessage = null) }
    }

    fun stopSpeaking() {
        voiceTtsManager.stop()
        _uiState.update { it.copy(voiceState = VoiceState.IDLE, voiceStatusMessage = null) }
    }

    fun onMicrophonePermissionDenied() {
        _uiState.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                voiceStatusMessage = null,
                errorMessage = "Microphone permission is required to use voice input and voice commands."
            )
        }
    }

    fun setVoiceCommandsEnabled(enabled: Boolean) {
        repository.setVoiceCommandsEnabled(enabled)
        _uiState.update { it.copy(isVoiceCommandsEnabled = enabled) }
    }

    fun setVoiceRepliesEnabled(enabled: Boolean) {
        repository.setVoiceRepliesEnabled(enabled)
        _uiState.update { it.copy(isVoiceRepliesEnabled = enabled) }
        if (!enabled) {
            voiceTtsManager.stop()
        }
    }

    fun setVoiceLanguage(language: String) {
        repository.setVoiceLanguage(language)
        _uiState.update { it.copy(voiceLanguage = language) }
        voiceTtsManager.applyLanguage(language)
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
        }
    }

    // --- Provider and Key Configuration Actions ---

    fun selectProvider(provider: AiProviderType) {
        repository.setSelectedProvider(provider)
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                currentModel = repository.getSelectedModel(),
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey()
            )
        }
    }

    fun updateOpenAiApiKey(apiKey: String) {
        repository.saveOpenAiApiKey(apiKey)
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenAiApiKey = repository.getMaskedOpenAiApiKey()
            )
        }
    }

    fun clearOpenAiApiKey() {
        repository.clearOpenAiApiKey()
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenAiApiKey = ""
            )
        }
    }

    fun updateGeminiApiKey(apiKey: String) {
        repository.saveGeminiApiKey(apiKey)
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedGeminiApiKey = repository.getMaskedGeminiApiKey()
            )
        }
    }

    fun clearGeminiApiKey() {
        repository.clearGeminiApiKey()
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedGeminiApiKey = ""
            )
        }
    }

    fun updateOpenRouterApiKey(apiKey: String) {
        repository.saveOpenRouterApiKey(apiKey)
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenRouterApiKey = repository.getMaskedOpenRouterApiKey()
            )
        }
    }

    fun clearOpenRouterApiKey() {
        repository.clearOpenRouterApiKey()
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenRouterApiKey = ""
            )
        }
    }

    fun updateOpenAiModel(model: String) {
        repository.saveOpenAiModel(model)
        _uiState.update {
            it.copy(
                openAiModel = model,
                currentModel = if (it.selectedProvider == AiProviderType.OPENAI) model else it.currentModel
            )
        }
    }

    fun updateGeminiModel(model: String) {
        repository.saveGeminiModel(model)
        _uiState.update {
            it.copy(
                geminiModel = model,
                currentModel = if (it.selectedProvider == AiProviderType.GEMINI) model else it.currentModel
            )
        }
    }

    fun updateOpenRouterModel(model: String) {
        repository.saveOpenRouterModel(model)
        _uiState.update {
            it.copy(
                openRouterModel = model,
                currentModel = if (it.selectedProvider == AiProviderType.OPENROUTER) model else it.currentModel
            )
        }
    }

    fun setAutoFallbackEnabled(enabled: Boolean) {
        repository.setAutoFallbackEnabled(enabled)
        _uiState.update { it.copy(isAutoFallbackEnabled = enabled) }
    }

    fun saveApiKey(apiKey: String) {
        repository.saveApiKey(apiKey)
        _uiState.update {
            it.copy(
                hasApiKey = true,
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenAiApiKey = repository.getMaskedOpenAiApiKey(),
                maskedGeminiApiKey = repository.getMaskedGeminiApiKey(),
                maskedOpenRouterApiKey = repository.getMaskedOpenRouterApiKey()
            )
        }
    }

    fun clearApiKey() {
        repository.clearApiKey()
        _uiState.update {
            it.copy(
                hasApiKey = repository.hasApiKey(),
                maskedApiKey = repository.getMaskedApiKey(),
                maskedOpenAiApiKey = repository.getMaskedOpenAiApiKey(),
                maskedGeminiApiKey = repository.getMaskedGeminiApiKey(),
                maskedOpenRouterApiKey = repository.getMaskedOpenRouterApiKey()
            )
        }
    }

    fun updateModel(modelName: String) {
        repository.saveSelectedModel(modelName)
        _uiState.update {
            it.copy(
                currentModel = modelName,
                openAiModel = if (it.selectedProvider == AiProviderType.OPENAI) modelName else it.openAiModel,
                geminiModel = if (it.selectedProvider == AiProviderType.GEMINI) modelName else it.geminiModel,
                openRouterModel = if (it.selectedProvider == AiProviderType.OPENROUTER) modelName else it.openRouterModel
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun completeSetup(userName: String, assistantName: String) {
        repository.secureStorage.setUserName(userName)
        repository.secureStorage.setAssistantName(assistantName)
        repository.secureStorage.setSetupCompleted(true)
        _uiState.update {
            it.copy(
                isSetupCompleted = true,
                userName = repository.secureStorage.getUserName(),
                assistantName = repository.secureStorage.getAssistantName()
            )
        }
    }

    fun setUserName(name: String) {
        repository.secureStorage.setUserName(name)
        _uiState.update { it.copy(userName = repository.secureStorage.getUserName()) }
    }

    fun setAssistantName(name: String) {
        repository.secureStorage.setAssistantName(name)
        _uiState.update { it.copy(assistantName = repository.secureStorage.getAssistantName()) }
    }

    fun executeLocalAction(commandText: String, isVoice: Boolean = false) {
        processUnifiedInput(commandText, isVoice = isVoice)
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.destroy()
        voiceTtsManager.shutdown()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as? Application)
                    ?: throw IllegalStateException("Application must be provided in CreationExtras to instantiate ChatViewModel")
                val repository = AssistantRepository(application)
                ChatViewModel(application, repository)
            }
        }
    }
}
