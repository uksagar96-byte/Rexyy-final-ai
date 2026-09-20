package com.rexyy.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rexyy.app.ui.components.ChatInputBar
import com.rexyy.app.ui.components.MessageBubble
import com.rexyy.app.ui.components.RexyyTopBar
import com.rexyy.app.ui.components.TypingIndicator
import com.rexyy.app.ui.theme.RexyyCyanLight
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.theme.RexyyDarkBorder
import com.rexyy.app.ui.theme.RexyyDarkSurface
import com.rexyy.app.ui.theme.RexyyDarkSurfaceVariant
import com.rexyy.app.ui.theme.RexyyErrorBubble
import com.rexyy.app.ui.theme.RexyyErrorRed
import com.rexyy.app.ui.theme.RexyyPurpleSecondary
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary
import com.rexyy.app.voice.VoiceState

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearChat: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismissError: () -> Unit,
    onStartVoiceInput: () -> Unit = {},
    onCancelVoiceInput: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onMicrophonePermissionDenied: () -> Unit = {},
    onConfirmAction: () -> Unit = {},
    onCancelAction: () -> Unit = {},
    onCancelTask: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showClearConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartVoiceInput()
        } else {
            onMicrophonePermissionDenied()
        }
    }

    val handleMicClick: () -> Unit = {
        when (uiState.voiceState) {
            VoiceState.LISTENING -> onCancelVoiceInput()
            VoiceState.SPEAKING -> onStopSpeaking()
            VoiceState.PROCESSING -> { /* In flight */ }
            VoiceState.IDLE -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    onStartVoiceInput()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    // Automatically scroll to latest message
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            RexyyTopBar(
                currentModel = uiState.currentModel,
                selectedProvider = uiState.selectedProvider,
                onClearChatClick = { showClearConfirmation = true },
                onSettingsClick = onSettingsClick,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Live voice state banner
                AnimatedVisibility(
                    visible = uiState.voiceState != VoiceState.IDLE,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = RexyyDarkSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.voiceState == VoiceState.LISTENING) RexyyErrorRed.copy(alpha = 0.5f)
                            else RexyyCyanPrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_state_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            when (uiState.voiceState) {
                                VoiceState.LISTENING -> {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(RexyyErrorRed)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = uiState.voiceStatusMessage ?: "Listening... Speak in English or Hindi",
                                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextPrimary),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onCancelVoiceInput) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = RexyyErrorRed))
                                    }
                                }
                                VoiceState.PROCESSING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = RexyyCyanPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = uiState.voiceStatusMessage ?: "Processing...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextPrimary),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                VoiceState.SPEAKING -> {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = RexyyPurpleSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = uiState.voiceStatusMessage ?: "REXYY is speaking...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextPrimary),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onStopSpeaking) {
                                        Text("Stop", style = MaterialTheme.typography.labelMedium.copy(color = RexyyPurpleSecondary))
                                    }
                                }
                                VoiceState.IDLE -> Unit
                            }
                        }
                    }
                }

                // Pending Confirmation Card
                if (uiState.pendingConfirmation != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                        border = BorderStroke(1.5.dp, RexyyCyanPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "CONFIRMATION REQUIRED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = RexyyCyanPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.pendingConfirmation.prompt,
                                style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextPrimary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = onCancelAction) {
                                    Text("Cancel", color = RexyyTextMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onConfirmAction,
                                    colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary)
                                ) {
                                    Text("Confirm", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Active Task Progress Card
                if (uiState.activeTaskPlan != null && !uiState.activeTaskPlan.isFinished) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                        border = BorderStroke(1.dp, RexyyCyanPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TASK: ${uiState.activeTaskPlan.title}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = RexyyCyanPrimary
                                    )
                                )
                                TextButton(onClick = onCancelTask) {
                                    Text("Cancel", color = RexyyErrorRed, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            uiState.activeTaskPlan.steps.forEachIndexed { idx, step ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    val statusText = when (step.status) {
                                        com.rexyy.app.task.TaskStepStatus.RUNNING -> "⏳ Running..."
                                        com.rexyy.app.task.TaskStepStatus.SUCCESS -> "✓"
                                        com.rexyy.app.task.TaskStepStatus.FAILED -> "✗"
                                        com.rexyy.app.task.TaskStepStatus.CANCELLED -> "—"
                                        com.rexyy.app.task.TaskStepStatus.QUEUED -> "•"
                                    }
                                    Text(
                                        text = "${idx + 1}. ${step.description}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall.copy(color = RexyyCyanLight)
                                    )
                                }
                            }
                        }
                    }
                }

                ChatInputBar(
                    inputText = uiState.inputText,
                    onInputChange = onInputChange,
                    onSendClick = onSendMessage,
                    isLoading = uiState.isLoading,
                    voiceState = uiState.voiceState,
                    onMicClick = handleMicClick
                )
            }
        },
        containerColor = RexyyDarkBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner if any
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = RexyyErrorBubble),
                    border = BorderStroke(1.dp, RexyyErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("error_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = "Error icon",
                            tint = RexyyErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss error",
                                tint = RexyyTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Message list or Empty State
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                EmptyStateView(
                    onSuggestionClick = { suggestion ->
                        onInputChange(suggestion)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("chat_message_list")
                ) {
                    items(
                        items = uiState.messages,
                        key = { message -> message.id }
                    ) { message ->
                        MessageBubble(message = message)
                    }

                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog to clear conversation
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This will delete all current messages in the chat history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearChat()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Clear", color = RexyyErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
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
private fun EmptyStateView(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Open YouTube",
        "Alarm 7 baje lagao",
        "Search Google for latest tech news",
        "Explain quantum computing in simple terms"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .testTag("empty_state_view")
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RexyyDarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = "AI Assistant Icon",
                tint = RexyyCyanPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "REXYY Voice & AI Ready",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = RexyyTextPrimary,
                letterSpacing = 0.5.sp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap the microphone or type below. Supports voice commands and AI conversation in English & Hindi.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = RexyyTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { prompt ->
                SuggestionChip(
                    onClick = { onSuggestionClick(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextPrimary)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = if (prompt.contains("YouTube") || prompt.contains("alarm") || prompt.contains("Google"))
                                Icons.Outlined.Mic
                            else
                                Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = RexyyPurpleSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = RexyyDarkSurface
                    ),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(0.95f)
                )
            }
        }
    }
}
