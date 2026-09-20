package com.rexyy.app.ui.wizard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.rexyy.app.device.CapabilityManager
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.ui.avatar.AssistantState
import com.rexyy.app.ui.avatar.RexyyAvatar3D
import com.rexyy.app.ui.theme.RexyyCyanLight
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.theme.RexyyDarkBorder
import com.rexyy.app.ui.theme.RexyyDarkSurface
import com.rexyy.app.ui.theme.RexyyDarkSurfaceVariant
import com.rexyy.app.ui.theme.RexyyNeonGreen
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary

@Composable
fun SetupWizardScreen(
    onFinishWizard: (userName: String, assistantName: String, provider: AiProviderType, apiKey: String?) -> Unit,
    onBackToWelcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 4

    var userName by remember { mutableStateOf("Commander") }
    var assistantName by remember { mutableStateOf("REXYY") }
    var responseStyle by remember { mutableStateOf("Professional & Concise") }
    var selectedProvider by remember { mutableStateOf(AiProviderType.LOCAL_TEST) }
    var enteredApiKey by remember { mutableStateOf("") }
    var isMicGranted by remember {
        mutableStateOf(CapabilityManager.hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicGranted = granted
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RexyyDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with progress
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep-- else onBackToWelcome()
                        },
                        modifier = Modifier.testTag("wizard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = RexyyTextPrimary
                        )
                    }

                    Text(
                        text = "STEP $currentStep OF $totalSteps",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = RexyyCyanPrimary
                        )
                    )

                    // Step indicator dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 1..totalSteps) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == currentStep) 18.dp else 8.dp, 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (i == currentStep) RexyyCyanPrimary
                                        else if (i < currentStep) RexyyNeonGreen
                                        else RexyyDarkBorder
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "wizard_step_anim"
                ) { step ->
                    when (step) {
                        1 -> StepIdentity(
                            userName = userName,
                            onUserNameChange = { userName = it },
                            assistantName = assistantName,
                            onAssistantNameChange = { assistantName = it },
                            responseStyle = responseStyle,
                            onResponseStyleChange = { responseStyle = it }
                        )
                        2 -> StepPermissions(
                            isMicGranted = isMicGranted,
                            onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                        3 -> StepAiProvider(
                            selectedProvider = selectedProvider,
                            onProviderSelected = { selectedProvider = it },
                            apiKey = enteredApiKey,
                            onApiKeyChange = { enteredApiKey = it }
                        )
                        4 -> StepReview(
                            userName = userName,
                            assistantName = assistantName,
                            responseStyle = responseStyle,
                            provider = selectedProvider,
                            isMicGranted = isMicGranted,
                            hasApiKey = enteredApiKey.isNotBlank() || selectedProvider == AiProviderType.LOCAL_TEST
                        )
                    }
                }
            }

            // Bottom Navigation Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep < totalSteps) {
                    OutlinedButton(
                        onClick = {
                            // Quick skip to next step
                            currentStep++
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RexyyTextMuted),
                        border = BorderStroke(1.dp, RexyyDarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = { currentStep++ },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RexyyCyanPrimary,
                            contentColor = RexyyDarkBackground
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(50.dp)
                            .testTag("wizard_next_button")
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onFinishWizard(
                                userName,
                                assistantName,
                                selectedProvider,
                                enteredApiKey.ifBlank { null }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RexyyNeonGreen,
                            contentColor = RexyyDarkBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("wizard_complete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Launch REXYY Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIdentity(
    userName: String,
    onUserNameChange: (String) -> Unit,
    assistantName: String,
    onAssistantNameChange: (String) -> Unit,
    responseStyle: String,
    onResponseStyleChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Personalize Your Assistant",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary
            )
        )
        Text(
            text = "Choose how REXYY addresses you and establish your preferred conversational tone.",
            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
        )

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("How should REXYY address you?") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = RexyyCyanPrimary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RexyyCyanPrimary,
                unfocusedBorderColor = RexyyDarkBorder,
                focusedTextColor = RexyyTextPrimary,
                unfocusedTextColor = RexyyTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag("wizard_user_name_input")
        )

        OutlinedTextField(
            value = assistantName,
            onValueChange = onAssistantNameChange,
            label = { Text("Assistant Name") },
            leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = RexyyCyanPrimary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RexyyCyanPrimary,
                unfocusedBorderColor = RexyyDarkBorder,
                focusedTextColor = RexyyTextPrimary,
                unfocusedTextColor = RexyyTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag("wizard_assistant_name_input")
        )

        Text(
            text = "Response Personality Style",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary
            )
        )

        val styles = listOf("Professional & Concise", "Analytical & In-Depth", "Friendly & Conversational")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            styles.forEach { style ->
                val isSelected = responseStyle == style
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RexyyDarkSurfaceVariant else RexyyDarkSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RexyyCyanPrimary else RexyyDarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResponseStyleChange(style) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) RexyyCyanPrimary else RexyyDarkBorder)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = style,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) RexyyCyanLight else RexyyTextPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepPermissions(
    isMicGranted: Boolean,
    onRequestMic: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Assistant Device Permissions",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary
            )
        )
        Text(
            text = "REXYY is built for hands-free voice control. Granting microphone permission unlocks natural speech recognition.",
            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
            border = BorderStroke(1.dp, if (isMicGranted) RexyyNeonGreen else RexyyDarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(RexyyDarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = if (isMicGranted) RexyyNeonGreen else RexyyCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Access",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RexyyTextPrimary
                            )
                        )
                        Text(
                            text = if (isMicGranted) "Permission granted • Active" else "Required for speech recognition",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isMicGranted) RexyyNeonGreen else RexyyTextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isMicGranted) {
                    Button(
                        onClick = onRequestMic,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary, contentColor = RexyyDarkBackground),
                        modifier = Modifier.fillMaxWidth().testTag("grant_mic_permission_button")
                    ) {
                        Text("Grant Microphone Access", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = RexyyNeonGreen)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "Microphone is ready for voice interaction",
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyNeonGreen)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
            border = BorderStroke(1.dp, RexyyDarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(RexyyDarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = RexyyCyanPrimary)
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column {
                        Text(
                            text = "On-Device Privacy First",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RexyyTextPrimary
                            )
                        )
                        Text(
                            text = "Audio is processed locally by Android SpeechRecognizer and never sent to third-party ad networks.",
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextMuted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepAiProvider(
    selectedProvider: AiProviderType,
    onProviderSelected: (AiProviderType) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Intelligence Engine",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary
            )
        )
        Text(
            text = "Choose your AI backend. Local Test Mode works instantly without an API key. You can also connect OpenAI or Google Gemini.",
            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
        )

        val providers = listOf(
            Triple(AiProviderType.LOCAL_TEST, "Local Test Mode (No Key Required)", "Offline device control, intents, volume, alarms, and quick tests."),
            Triple(AiProviderType.OPENAI, "OpenAI (GPT-4o, GPT-4o-mini)", "Generative conversation, reasoning, and coding assistance."),
            Triple(AiProviderType.GEMINI, "Google Gemini (Gemini 3.5 Flash)", "High-speed multi-turn generative intelligence from Google.")
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            providers.forEach { (prov, title, desc) ->
                val isSelected = selectedProvider == prov
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RexyyDarkSurfaceVariant else RexyyDarkSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RexyyCyanPrimary else RexyyDarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProviderSelected(prov) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) RexyyCyanPrimary else RexyyDarkBorder)
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RexyyCyanLight else RexyyTextPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextMuted),
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }
                }
            }
        }

        if (selectedProvider != AiProviderType.LOCAL_TEST) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("${selectedProvider.displayName} API Key (Optional)") },
                placeholder = { Text(if (selectedProvider == AiProviderType.OPENAI) "sk-..." else "AIzaSy...") },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = RexyyCyanPrimary) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RexyyCyanPrimary,
                    unfocusedBorderColor = RexyyDarkBorder,
                    focusedTextColor = RexyyTextPrimary,
                    unfocusedTextColor = RexyyTextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("wizard_api_key_input")
            )
            Text(
                text = "Key will be encrypted in hardware Android KeyStore. Leave blank to start in Local Mode.",
                style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextMuted)
            )
        }
    }
}

@Composable
private fun StepReview(
    userName: String,
    assistantName: String,
    responseStyle: String,
    provider: AiProviderType,
    isMicGranted: Boolean,
    hasApiKey: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RexyyAvatar3D(
            state = AssistantState.SUCCESS,
            size = 140.dp
        )

        Text(
            text = "REXYY is Ready to Assist",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary
            )
        )
        Text(
            text = "Your configuration has been initialized. Here is your setup summary:",
            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
            border = BorderStroke(1.dp, RexyyDarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryRow("Commander Name", userName)
                SummaryRow("Assistant Persona", assistantName)
                SummaryRow("Conversation Style", responseStyle)
                SummaryRow("AI Engine", provider.displayName)
                SummaryRow("Microphone", if (isMicGranted) "Enabled" else "Pending")
                SummaryRow("Security Storage", "AES-256 GCM KeyStore")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextMuted))
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextPrimary, fontWeight = FontWeight.Bold))
    }
}
