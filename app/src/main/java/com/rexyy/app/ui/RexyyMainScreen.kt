package com.rexyy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.ui.chat.ChatScreen
import com.rexyy.app.ui.chat.ChatViewModel
import com.rexyy.app.ui.control.ControlCenterScreen
import com.rexyy.app.ui.dev.DevConsoleScreen
import com.rexyy.app.ui.main.MainAssistantScreen
import com.rexyy.app.ui.navigation.Screen
import com.rexyy.app.ui.permissions.PermissionCenterScreen
import com.rexyy.app.ui.settings.SettingsScreen
import com.rexyy.app.ui.setup.ApiKeySetupScreen
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.welcome.WelcomeScreen
import com.rexyy.app.ui.wizard.SetupWizardScreen

@Composable
fun RexyyMainScreen(
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Determine initial screen based on whether setup wizard has been completed
    var currentScreen by remember(uiState.isSetupCompleted) {
        mutableStateOf(
            if (uiState.isSetupCompleted) {
                Screen.MainAssistant
            } else {
                Screen.Welcome
            }
        )
    }

    // Handle system back navigation
    BackHandler(enabled = currentScreen !is Screen.MainAssistant && currentScreen !is Screen.Welcome) {
        currentScreen = Screen.MainAssistant
    }

    Surface(
        color = RexyyDarkBackground,
        modifier = modifier.fillMaxSize()
    ) {
        when (currentScreen) {
            is Screen.Welcome -> {
                WelcomeScreen(
                    onStartSetupWizard = {
                        currentScreen = Screen.SetupWizard
                    },
                    onSkipToLocalMode = {
                        viewModel.selectProvider(AiProviderType.LOCAL_TEST)
                        viewModel.completeSetup(
                            userName = "Commander",
                            assistantName = "REXYY"
                        )
                        currentScreen = Screen.MainAssistant
                    }
                )
            }

            is Screen.SetupWizard -> {
                SetupWizardScreen(
                    onFinishWizard = { userName, assistantName, provider, apiKey ->
                        viewModel.selectProvider(provider)
                        if (!apiKey.isNullOrBlank()) {
                            if (provider == AiProviderType.GEMINI) {
                                viewModel.updateGeminiApiKey(apiKey)
                            } else if (provider == AiProviderType.OPENAI) {
                                viewModel.updateOpenAiApiKey(apiKey)
                            }
                        }
                        viewModel.completeSetup(userName, assistantName)
                        currentScreen = Screen.MainAssistant
                    },
                    onBackToWelcome = {
                        currentScreen = Screen.Welcome
                    }
                )
            }

            is Screen.MainAssistant -> {
                MainAssistantScreen(
                    uiState = uiState,
                    onStartListening = { viewModel.startVoiceInput() },
                    onStopListening = { viewModel.cancelVoiceInput() },
                    onQuickCommand = { command -> viewModel.executeLocalAction(command, isVoice = false) },
                    onOpenChat = { currentScreen = Screen.Chat },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onOpenPermissions = { currentScreen = Screen.PermissionCenter },
                    onOpenControlCenter = { currentScreen = Screen.ControlCenter },
                    onOpenDevConsole = { currentScreen = Screen.DevConsole },
                    onConfirmAction = { viewModel.confirmPendingAction() },
                    onCancelAction = { viewModel.cancelPendingAction() },
                    onCancelTask = { viewModel.cancelActiveTask() }
                )
            }

            is Screen.Chat -> {
                ChatScreen(
                    uiState = uiState,
                    onInputChange = { viewModel.onInputChange(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onClearChat = { viewModel.clearConversation() },
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onDismissError = { viewModel.clearError() },
                    onStartVoiceInput = { viewModel.startVoiceInput() },
                    onCancelVoiceInput = { viewModel.cancelVoiceInput() },
                    onStopSpeaking = { viewModel.stopSpeaking() },
                    onMicrophonePermissionDenied = { viewModel.onMicrophonePermissionDenied() },
                    onConfirmAction = { viewModel.confirmPendingAction() },
                    onCancelAction = { viewModel.cancelPendingAction() },
                    onCancelTask = { viewModel.cancelActiveTask() },
                    onNavigateBack = { currentScreen = Screen.MainAssistant }
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    currentMaskedKey = uiState.maskedApiKey,
                    currentModel = uiState.currentModel,
                    selectedProvider = uiState.selectedProvider,
                    currentMaskedOpenAiKey = uiState.maskedOpenAiApiKey,
                    currentMaskedGeminiKey = uiState.maskedGeminiApiKey,
                    currentMaskedOpenRouterKey = uiState.maskedOpenRouterApiKey,
                    openAiModel = uiState.openAiModel,
                    geminiModel = uiState.geminiModel,
                    openRouterModel = uiState.openRouterModel,
                    isAutoFallbackEnabled = uiState.isAutoFallbackEnabled,
                    isVoiceCommandsEnabled = uiState.isVoiceCommandsEnabled,
                    isVoiceRepliesEnabled = uiState.isVoiceRepliesEnabled,
                    voiceLanguage = uiState.voiceLanguage,
                    onBackClick = {
                        currentScreen = Screen.MainAssistant
                    },
                    onSelectProvider = { provider ->
                        viewModel.selectProvider(provider)
                    },
                    onUpdateOpenAiApiKey = { key ->
                        viewModel.updateOpenAiApiKey(key)
                    },
                    onClearOpenAiApiKey = {
                        viewModel.clearOpenAiApiKey()
                    },
                    onUpdateGeminiApiKey = { key ->
                        viewModel.updateGeminiApiKey(key)
                    },
                    onClearGeminiApiKey = {
                        viewModel.clearGeminiApiKey()
                    },
                    onUpdateOpenRouterApiKey = { key ->
                        viewModel.updateOpenRouterApiKey(key)
                    },
                    onClearOpenRouterApiKey = {
                        viewModel.clearOpenRouterApiKey()
                    },
                    onUpdateApiKey = { newKey ->
                        viewModel.saveApiKey(newKey)
                    },
                    onClearApiKey = {
                        viewModel.clearApiKey()
                    },
                    onUpdateModel = { newModel ->
                        viewModel.updateModel(newModel)
                    },
                    onUpdateAutoFallbackEnabled = { enabled ->
                        viewModel.setAutoFallbackEnabled(enabled)
                    },
                    onUpdateVoiceCommandsEnabled = { enabled ->
                        viewModel.setVoiceCommandsEnabled(enabled)
                    },
                    onUpdateVoiceRepliesEnabled = { enabled ->
                        viewModel.setVoiceRepliesEnabled(enabled)
                    },
                    onUpdateVoiceLanguage = { lang ->
                        viewModel.setVoiceLanguage(lang)
                    },
                    onClearAllHistory = {
                        viewModel.clearConversation()
                    }
                )
            }

            is Screen.PermissionCenter -> {
                PermissionCenterScreen(
                    onNavigateBack = { currentScreen = Screen.MainAssistant }
                )
            }

            is Screen.ControlCenter -> {
                ControlCenterScreen(
                    onNavigateBack = { currentScreen = Screen.MainAssistant }
                )
            }

            is Screen.DevConsole -> {
                DevConsoleScreen(
                    onNavigateBack = { currentScreen = Screen.MainAssistant }
                )
            }

            is Screen.SetupGuide -> {
                WelcomeScreen(
                    onStartSetupWizard = { currentScreen = Screen.SetupWizard },
                    onSkipToLocalMode = {
                        viewModel.selectProvider(AiProviderType.LOCAL_TEST)
                        viewModel.completeSetup("Commander", "REXYY")
                        currentScreen = Screen.MainAssistant
                    }
                )
            }

            is Screen.Setup -> {
                ApiKeySetupScreen(
                    onApiKeySaved = { key ->
                        viewModel.saveApiKey(key)
                        viewModel.completeSetup("Commander", "REXYY")
                        currentScreen = Screen.MainAssistant
                    },
                    onProviderAndKeySaved = { key, provider ->
                        viewModel.selectProvider(provider)
                        if (provider == AiProviderType.GEMINI) {
                            viewModel.updateGeminiApiKey(key)
                        } else {
                            viewModel.updateOpenAiApiKey(key)
                        }
                        viewModel.completeSetup("Commander", "REXYY")
                        currentScreen = Screen.MainAssistant
                    }
                )
            }
        }
    }
}
