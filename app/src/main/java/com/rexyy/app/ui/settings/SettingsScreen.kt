package com.rexyy.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.network.provider.AiModelRegistry
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.service.BackgroundAssistantManager
import com.rexyy.app.service.RexyyAssistantServiceState
import com.rexyy.app.ui.theme.RexyyAmberWarning
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.theme.RexyyDarkBorder
import com.rexyy.app.ui.theme.RexyyDarkSurface
import com.rexyy.app.ui.theme.RexyyDarkSurfaceVariant
import com.rexyy.app.ui.theme.RexyyErrorRed
import com.rexyy.app.ui.theme.RexyyNeonGreen
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary
import com.rexyy.app.utils.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentMaskedKey: String,
    currentModel: String,
    selectedProvider: AiProviderType = AiProviderType.OPENAI,
    currentMaskedOpenAiKey: String = "",
    currentMaskedGeminiKey: String = "",
    currentMaskedOpenRouterKey: String = "",
    openAiModel: String = "gpt-4o-mini",
    geminiModel: String = "gemini-3.5-flash",
    openRouterModel: String = "google/gemini-2.5-flash",
    isAutoFallbackEnabled: Boolean = true,
    isVoiceCommandsEnabled: Boolean = true,
    isVoiceRepliesEnabled: Boolean = true,
    voiceLanguage: String = SecureStorage.VOICE_LANG_DEFAULT,
    onBackClick: () -> Unit,
    onSelectProvider: (AiProviderType) -> Unit = {},
    onUpdateOpenAiApiKey: (String) -> Unit = {},
    onClearOpenAiApiKey: () -> Unit = {},
    onUpdateGeminiApiKey: (String) -> Unit = {},
    onClearGeminiApiKey: () -> Unit = {},
    onUpdateOpenRouterApiKey: (String) -> Unit = {},
    onClearOpenRouterApiKey: () -> Unit = {},
    onUpdateApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateAutoFallbackEnabled: (Boolean) -> Unit = {},
    onUpdateVoiceCommandsEnabled: (Boolean) -> Unit = {},
    onUpdateVoiceRepliesEnabled: (Boolean) -> Unit = {},
    onUpdateVoiceLanguage: (String) -> Unit = {},
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var activeProvider by remember(selectedProvider) { mutableStateOf(selectedProvider) }

    // OpenAI Key State
    var newOpenAiKey by remember { mutableStateOf("") }
    var isOpenAiKeyVisible by remember { mutableStateOf(false) }
    var showClearOpenAiDialog by remember { mutableStateOf(false) }

    // Gemini Key State
    var newGeminiKey by remember { mutableStateOf("") }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var showClearGeminiDialog by remember { mutableStateOf(false) }

    // OpenRouter Key State
    var newOpenRouterKey by remember { mutableStateOf("") }
    var isOpenRouterKeyVisible by remember { mutableStateOf(false) }
    var showClearOpenRouterDialog by remember { mutableStateOf(false) }

    // Model Dropdown
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var chosenModel by remember(currentModel, activeProvider) {
        mutableStateOf(
            when (activeProvider) {
                AiProviderType.OPENROUTER -> openRouterModel
                AiProviderType.OPENAI -> openAiModel
                AiProviderType.GEMINI -> geminiModel
                AiProviderType.LOCAL_TEST -> "local-autonomous"
            }
        )
    }

    // Fallback Switch
    var autoFallbackOn by remember(isAutoFallbackEnabled) { mutableStateOf(isAutoFallbackEnabled) }

    // Voice Switches
    var voiceCommandsOn by remember(isVoiceCommandsEnabled) { mutableStateOf(isVoiceCommandsEnabled) }
    var voiceRepliesOn by remember(isVoiceRepliesEnabled) { mutableStateOf(isVoiceRepliesEnabled) }
    var selectedLanguage by remember(voiceLanguage) { mutableStateOf(voiceLanguage) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val languageOptions = listOf(
        SecureStorage.VOICE_LANG_DEFAULT to "System Default",
        SecureStorage.VOICE_LANG_EN to "English (US)",
        SecureStorage.VOICE_LANG_HI to "Hindi (India)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyTextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = RexyyTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RexyyDarkSurface,
                    titleContentColor = RexyyTextPrimary
                )
            )
        },
        containerColor = RexyyDarkBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 1. AI PROVIDER SELECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_provider_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = "AI Provider",
                            tint = RexyyCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Provider",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Select your primary AI model engine for REXYY conversations.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Local Test Mode Option
                        ProviderOptionCard(
                            title = "Local Test Mode",
                            subtitle = "Zero API Key • Offline Intents & Voice Commands",
                            isSelected = activeProvider == AiProviderType.LOCAL_TEST,
                            isConfigured = true,
                            onClick = {
                                activeProvider = AiProviderType.LOCAL_TEST
                                onSelectProvider(AiProviderType.LOCAL_TEST)
                                Toast.makeText(context, "Provider set to Local Test Mode", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_local_test_option")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // OpenAI Provider Option
                            ProviderOptionCard(
                                title = "OpenAI",
                                subtitle = "GPT-4o & GPT-4o Mini",
                                isSelected = activeProvider == AiProviderType.OPENAI,
                                isConfigured = currentMaskedOpenAiKey.isNotBlank() || (currentMaskedKey.isNotBlank() && selectedProvider == AiProviderType.OPENAI),
                                onClick = {
                                    activeProvider = AiProviderType.OPENAI
                                    onSelectProvider(AiProviderType.OPENAI)
                                    chosenModel = openAiModel
                                    onUpdateModel(openAiModel)
                                    Toast.makeText(context, "Provider set to OpenAI", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("provider_openai_option")
                            )

                            // Google Gemini Provider Option
                            ProviderOptionCard(
                                title = "Google Gemini",
                                subtitle = "Gemini 3.5 & 2.5 Flash",
                                isSelected = activeProvider == AiProviderType.GEMINI,
                                isConfigured = currentMaskedGeminiKey.isNotBlank(),
                                onClick = {
                                    activeProvider = AiProviderType.GEMINI
                                    onSelectProvider(AiProviderType.GEMINI)
                                    chosenModel = geminiModel
                                    onUpdateModel(geminiModel)
                                    Toast.makeText(context, "Provider set to Google Gemini", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("provider_gemini_option")
                            )
                        }

                        // OpenRouter Provider Option
                        ProviderOptionCard(
                            title = "OpenRouter",
                            subtitle = "Multi-Model AI • Claude, Gemini, Llama, Mistral",
                            isSelected = activeProvider == AiProviderType.OPENROUTER,
                            isConfigured = currentMaskedOpenRouterKey.isNotBlank(),
                            onClick = {
                                activeProvider = AiProviderType.OPENROUTER
                                onSelectProvider(AiProviderType.OPENROUTER)
                                chosenModel = openRouterModel
                                onUpdateModel(openRouterModel)
                                Toast.makeText(context, "Provider set to OpenRouter", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_openrouter_option")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 2. API KEYS CONFIGURATION (Both Providers)
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_keys_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "API Keys",
                            tint = RexyyCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "API Keys",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encrypted on-device via Android KeyStore (AES-256 GCM). Keys are never logged or transmitted elsewhere.",
                        style = MaterialTheme.typography.labelSmall.copy(color = RexyyTextMuted)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- OpenAI Key Section ---
                    val openAiMaskedDisplay = if (currentMaskedOpenAiKey.isNotBlank()) {
                        currentMaskedOpenAiKey
                    } else if (selectedProvider == AiProviderType.OPENAI && currentMaskedKey.isNotBlank()) {
                        currentMaskedKey
                    } else ""

                    KeyRowComponent(
                        providerName = "OpenAI API Key",
                        maskedKey = openAiMaskedDisplay,
                        inputValue = newOpenAiKey,
                        isKeyVisible = isOpenAiKeyVisible,
                        placeholder = "sk-...",
                        onInputValueChange = { newOpenAiKey = it },
                        onToggleVisibility = { isOpenAiKeyVisible = !isOpenAiKeyVisible },
                        onSaveKey = {
                            if (SecurityUtils.isValidApiKey(newOpenAiKey)) {
                                onUpdateOpenAiApiKey(newOpenAiKey.trim())
                                onUpdateApiKey(newOpenAiKey.trim())
                                newOpenAiKey = ""
                                Toast.makeText(context, "OpenAI API Key saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid key: at least 10 characters", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearKey = { showClearOpenAiDialog = true },
                        inputTestTag = "openai_key_input",
                        saveTestTag = "save_openai_key_button",
                        deleteTestTag = "delete_openai_key_button"
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // --- Gemini Key Section ---
                    KeyRowComponent(
                        providerName = "Google Gemini API Key",
                        maskedKey = currentMaskedGeminiKey,
                        inputValue = newGeminiKey,
                        isKeyVisible = isGeminiKeyVisible,
                        placeholder = "AIzaSy...",
                        onInputValueChange = { newGeminiKey = it },
                        onToggleVisibility = { isGeminiKeyVisible = !isGeminiKeyVisible },
                        onSaveKey = {
                            if (SecurityUtils.isValidApiKey(newGeminiKey)) {
                                onUpdateGeminiApiKey(newGeminiKey.trim())
                                newGeminiKey = ""
                                Toast.makeText(context, "Gemini API Key saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid key: at least 10 characters", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearKey = { showClearGeminiDialog = true },
                        inputTestTag = "gemini_key_input",
                        saveTestTag = "save_gemini_key_button",
                        deleteTestTag = "delete_gemini_key_button"
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // --- OpenRouter Key Section ---
                    KeyRowComponent(
                        providerName = "OpenRouter API Key",
                        maskedKey = currentMaskedOpenRouterKey,
                        inputValue = newOpenRouterKey,
                        isKeyVisible = isOpenRouterKeyVisible,
                        placeholder = "sk-or-v1-...",
                        onInputValueChange = { newOpenRouterKey = it },
                        onToggleVisibility = { isOpenRouterKeyVisible = !isOpenRouterKeyVisible },
                        onSaveKey = {
                            if (SecurityUtils.isValidApiKey(newOpenRouterKey)) {
                                onUpdateOpenRouterApiKey(newOpenRouterKey.trim())
                                newOpenRouterKey = ""
                                Toast.makeText(context, "OpenRouter API Key saved", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid key: at least 10 characters", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearKey = { showClearOpenRouterDialog = true },
                        inputTestTag = "openrouter_key_input",
                        saveTestTag = "save_openrouter_key_button",
                        deleteTestTag = "delete_openrouter_key_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 3. AI MODEL SELECTION (Dynamic per Provider)
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = "Model",
                            tint = RexyyCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Model (${activeProvider.displayName})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val availableModels = AiModelRegistry.getAvailableModels(activeProvider)
                    val activeModelDisplay = when (activeProvider) {
                        AiProviderType.OPENROUTER -> openRouterModel
                        AiProviderType.OPENAI -> openAiModel
                        AiProviderType.GEMINI -> geminiModel
                        AiProviderType.LOCAL_TEST -> "local-autonomous"
                    }

                    ExposedDropdownMenuBox(
                        expanded = isModelDropdownExpanded,
                        onExpandedChange = { isModelDropdownExpanded = !isModelDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeModelDisplay,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = RexyyDarkSurfaceVariant,
                                unfocusedContainerColor = RexyyDarkSurfaceVariant,
                                focusedBorderColor = RexyyCyanPrimary,
                                unfocusedBorderColor = RexyyDarkBorder,
                                focusedTextColor = RexyyTextPrimary,
                                unfocusedTextColor = RexyyTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("model_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = isModelDropdownExpanded,
                            onDismissRequest = { isModelDropdownExpanded = false },
                            modifier = Modifier.background(RexyyDarkSurface)
                        ) {
                            for (modelName in availableModels) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = modelName,
                                            color = if (modelName == activeModelDisplay) RexyyCyanPrimary else RexyyTextPrimary,
                                            fontWeight = if (modelName == activeModelDisplay) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        chosenModel = modelName
                                        onUpdateModel(modelName)
                                        isModelDropdownExpanded = false
                                        Toast.makeText(context, "Active model: $modelName", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 4. AUTOMATIC FALLBACK TOGGLE
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fallback_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoMode,
                                contentDescription = "Fallback",
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Automatic Fallback",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = RexyyTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "If primary AI fails or limits are reached, REXYY seamlessly retries with secondary provider.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoFallbackOn,
                            onCheckedChange = { isChecked ->
                                autoFallbackOn = isChecked
                                onUpdateAutoFallbackEnabled(isChecked)
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Automatic fallback enabled" else "Automatic fallback disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RexyyDarkBackground,
                                checkedTrackColor = RexyyCyanPrimary,
                                uncheckedThumbColor = RexyyTextMuted,
                                uncheckedTrackColor = RexyyDarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("fallback_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 5. VOICE & SPEECH SETTINGS
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice Settings",
                            tint = RexyyCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice & Speech",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Always Ready (Background Assistant) Toggle
                    val isBgRunning by RexyyAssistantServiceState.serviceRunning.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always Ready (Background Assistant)",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = RexyyTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Listen for 'Hello Rex' or 'Hey Rex' even when the app is closed or minimized",
                                style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isBgRunning,
                            onCheckedChange = { _ ->
                                BackgroundAssistantManager.toggleAssistant(context)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RexyyDarkBackground,
                                checkedTrackColor = RexyyCyanPrimary,
                                uncheckedThumbColor = RexyyTextMuted,
                                uncheckedTrackColor = RexyyDarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("background_assistant_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice Commands Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Commands",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = RexyyTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Recognize device actions (Open YouTube, set alarm, adjust volume)",
                                style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = voiceCommandsOn,
                            onCheckedChange = { isChecked ->
                                voiceCommandsOn = isChecked
                                onUpdateVoiceCommandsEnabled(isChecked)
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Voice commands enabled" else "Voice commands disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RexyyDarkBackground,
                                checkedTrackColor = RexyyCyanPrimary,
                                uncheckedThumbColor = RexyyTextMuted,
                                uncheckedTrackColor = RexyyDarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("voice_commands_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice Replies (TTS) Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Replies (TTS)",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = RexyyTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Read AI responses and command confirmations aloud",
                                style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = voiceRepliesOn,
                            onCheckedChange = { isChecked ->
                                voiceRepliesOn = isChecked
                                onUpdateVoiceRepliesEnabled(isChecked)
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Voice replies enabled" else "Voice replies disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RexyyDarkBackground,
                                checkedTrackColor = RexyyCyanPrimary,
                                uncheckedThumbColor = RexyyTextMuted,
                                uncheckedTrackColor = RexyyDarkSurfaceVariant
                            ),
                            modifier = Modifier.testTag("voice_replies_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speech Language Selection
                    Text(
                        text = "Speech Language",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = RexyyTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = isLanguageDropdownExpanded,
                        onExpandedChange = { isLanguageDropdownExpanded = !isLanguageDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentLangLabel = languageOptions.find { it.first == selectedLanguage }?.second ?: "System Default"

                        OutlinedTextField(
                            value = currentLangLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLanguageDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = RexyyDarkSurfaceVariant,
                                unfocusedContainerColor = RexyyDarkSurfaceVariant,
                                focusedBorderColor = RexyyCyanPrimary,
                                unfocusedBorderColor = RexyyDarkBorder,
                                focusedTextColor = RexyyTextPrimary,
                                unfocusedTextColor = RexyyTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("voice_language_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = isLanguageDropdownExpanded,
                            onDismissRequest = { isLanguageDropdownExpanded = false },
                            modifier = Modifier.background(RexyyDarkSurface)
                        ) {
                            languageOptions.forEach { (langCode, langLabel) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = langLabel,
                                            color = if (langCode == selectedLanguage) RexyyCyanPrimary else RexyyTextPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedLanguage = langCode
                                        onUpdateVoiceLanguage(langCode)
                                        isLanguageDropdownExpanded = false
                                        Toast.makeText(context, "Voice language: $langLabel", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 6. CONVERSATION HISTORY CLEANUP
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data & Privacy",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = RexyyTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All conversation history is stored locally in Room database on your device.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RexyyErrorRed),
                        border = BorderStroke(1.dp, RexyyErrorRed.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = "Clear Chat History",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Messages")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    if (showClearOpenAiDialog) {
        AlertDialog(
            onDismissRequest = { showClearOpenAiDialog = false },
            title = { Text("Remove OpenAI API Key?") },
            text = { Text("Are you sure you want to remove your stored OpenAI API key?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearOpenAiApiKey()
                        showClearOpenAiDialog = false
                        Toast.makeText(context, "OpenAI API Key removed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Remove", color = RexyyErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOpenAiDialog = false }) {
                    Text("Cancel", color = RexyyTextPrimary)
                }
            },
            containerColor = RexyyDarkSurface,
            titleContentColor = RexyyTextPrimary,
            textContentColor = RexyyTextSecondary
        )
    }

    if (showClearGeminiDialog) {
        AlertDialog(
            onDismissRequest = { showClearGeminiDialog = false },
            title = { Text("Remove Gemini API Key?") },
            text = { Text("Are you sure you want to remove your stored Gemini API key?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearGeminiApiKey()
                        showClearGeminiDialog = false
                        Toast.makeText(context, "Gemini API Key removed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Remove", color = RexyyErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearGeminiDialog = false }) {
                    Text("Cancel", color = RexyyTextPrimary)
                }
            },
            containerColor = RexyyDarkSurface,
            titleContentColor = RexyyTextPrimary,
            textContentColor = RexyyTextSecondary
        )
    }

    if (showClearOpenRouterDialog) {
        AlertDialog(
            onDismissRequest = { showClearOpenRouterDialog = false },
            title = { Text("Remove OpenRouter API Key?") },
            text = { Text("Are you sure you want to remove your stored OpenRouter API key?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearOpenRouterApiKey()
                        showClearOpenRouterDialog = false
                        Toast.makeText(context, "OpenRouter API Key removed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Remove", color = RexyyErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOpenRouterDialog = false }) {
                    Text("Cancel", color = RexyyTextPrimary)
                }
            },
            containerColor = RexyyDarkSurface,
            titleContentColor = RexyyTextPrimary,
            textContentColor = RexyyTextSecondary
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This will permanently delete all messages stored in your local conversation history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "Conversation history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = RexyyErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = RexyyTextPrimary)
                }
            },
            containerColor = RexyyDarkSurface,
            titleContentColor = RexyyTextPrimary,
            textContentColor = RexyyTextSecondary
        )
    }
}

@Composable
private fun ProviderOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isConfigured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) RexyyCyanPrimary else RexyyDarkBorder
    val bgColor = if (isSelected) RexyyDarkSurfaceVariant else RexyyDarkSurface

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) RexyyCyanPrimary else RexyyTextPrimary
                    )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConfigured) RexyyNeonGreen else RexyyAmberWarning)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = RexyyTextMuted)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isConfigured) "Key Configured" else "Key Not Set",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isConfigured) RexyyNeonGreen else RexyyAmberWarning,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun KeyRowComponent(
    providerName: String,
    maskedKey: String,
    inputValue: String,
    isKeyVisible: Boolean,
    placeholder: String,
    onInputValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
    inputTestTag: String,
    saveTestTag: String,
    deleteTestTag: String
) {
    val isConfigured = maskedKey.isNotBlank()

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = providerName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = RexyyTextPrimary
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConfigured) RexyyNeonGreen else RexyyAmberWarning)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConfigured) "Configured" else "Not set",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isConfigured) RexyyNeonGreen else RexyyAmberWarning,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        if (isConfigured) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = maskedKey,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = RexyyNeonGreen,
                        fontWeight = FontWeight.Medium
                    )
                )
                TextButton(
                    onClick = onClearKey,
                    modifier = Modifier.testTag(deleteTestTag)
                ) {
                    Text("Remove", color = RexyyErrorRed, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = inputValue,
            onValueChange = onInputValueChange,
            placeholder = { Text(placeholder) },
            label = { Text(if (isConfigured) "Update Key" else "Enter Key") },
            singleLine = true,
            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = RexyyCyanPrimary
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (isKeyVisible) "Hide key" else "Show key",
                        tint = RexyyTextMuted
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RexyyDarkSurfaceVariant,
                unfocusedContainerColor = RexyyDarkSurfaceVariant,
                focusedBorderColor = RexyyCyanPrimary,
                unfocusedBorderColor = RexyyDarkBorder,
                focusedTextColor = RexyyTextPrimary,
                unfocusedTextColor = RexyyTextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSaveKey,
            enabled = inputValue.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = RexyyCyanPrimary,
                contentColor = RexyyDarkBackground
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag(saveTestTag)
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = "Save Key",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save ${providerName.replace(" API Key", "")} Key")
        }
    }
}
