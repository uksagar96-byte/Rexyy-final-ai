package com.rexyy.app.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.device.CapabilityManager
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.ui.avatar.AssistantState
import com.rexyy.app.ui.avatar.RexyyAvatar3D
import com.rexyy.app.ui.chat.ChatUiState
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
import com.rexyy.app.voice.VoiceState

@Composable
fun MainAssistantScreen(
    uiState: ChatUiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onConfirmAction: () -> Unit = {},
    onCancelAction: () -> Unit = {},
    onCancelTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartListening()
    }

    // Map VoiceState to 3D AssistantState
    val assistantState = when {
        uiState.errorMessage != null -> AssistantState.ERROR
        uiState.voiceState == VoiceState.LISTENING -> AssistantState.LISTENING
        uiState.voiceState == VoiceState.PROCESSING || uiState.isLoading -> AssistantState.THINKING
        uiState.voiceState == VoiceState.SPEAKING -> AssistantState.SPEAKING
        else -> AssistantState.IDLE
    }

    val isListening = uiState.voiceState == VoiceState.LISTENING

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Assistant Identity, Active Engine & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isListening) RexyyCyanPrimary else RexyyNeonGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = uiState.assistantName.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = RexyyCyanPrimary
                            )
                        )
                        Text(
                            text = when (uiState.selectedProvider) {
                                AiProviderType.LOCAL_TEST -> "Local Test Mode (Offline)"
                                AiProviderType.GEMINI -> "Google Gemini • ${uiState.geminiModel}"
                                AiProviderType.OPENAI -> "OpenAI • ${uiState.openAiModel}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RexyyTextMuted
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.testTag("open_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Capabilities",
                            tint = RexyyTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = RexyyTextPrimary
                        )
                    }
                }
            }

            // Central Hero: 3D Animated Sphere & Voice Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                // Interactive 3D Sphere (Tap to toggle voice)
                RexyyAvatar3D(
                    state = assistantState,
                    size = 230.dp,
                    onClick = {
                        if (isListening) {
                            onStopListening()
                        } else {
                            if (CapabilityManager.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                onStartListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Status banner
                Text(
                    text = when {
                        uiState.errorMessage != null -> uiState.errorMessage
                        isListening -> "Listening... Speak your command"
                        uiState.voiceState == VoiceState.PROCESSING || uiState.isLoading -> "Thinking..."
                        uiState.voiceState == VoiceState.SPEAKING -> "Speaking reply..."
                        else -> "Ready for your command, ${uiState.userName}"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.errorMessage != null) Color(0xFFFF8A80) else if (isListening) RexyyCyanLight else RexyyTextPrimary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isListening) "Tap the orb to finish speaking" else "Tap orb or hold mic to talk",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = RexyyTextMuted
                    )
                )
            }

            // Pending Confirmation Card (for Call, WhatsApp, SMS actions)
            if (uiState.pendingConfirmation != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.5.dp, RexyyCyanPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CONFIRMATION REQUIRED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = RexyyCyanPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.pendingConfirmation.prompt,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = RexyyTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                                Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Active Multi-Step Task Progress Card
            if (uiState.activeTaskPlan != null && !uiState.activeTaskPlan.isFinished) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyCyanPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TASK IN PROGRESS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = RexyyCyanPrimary
                                )
                            )
                            TextButton(onClick = onCancelTask) {
                                Text("Stop", color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = uiState.activeTaskPlan.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.activeTaskPlan.steps.forEachIndexed { idx, step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                val statusText = when (step.status) {
                                    com.rexyy.app.task.TaskStepStatus.RUNNING -> "⏳ Running..."
                                    com.rexyy.app.task.TaskStepStatus.SUCCESS -> "✓ Done"
                                    com.rexyy.app.task.TaskStepStatus.FAILED -> "✗ Failed"
                                    com.rexyy.app.task.TaskStepStatus.CANCELLED -> "— Cancelled"
                                    com.rexyy.app.task.TaskStepStatus.QUEUED -> "• Queued"
                                }
                                Text(
                                    text = "${idx + 1}. ${step.description}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (step.status == com.rexyy.app.task.TaskStepStatus.SUCCESS) RexyyNeonGreen else RexyyCyanLight
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Quick Device Command Chips (Voice & Tap)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "QUICK ACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = RexyyTextMuted
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.PlayArrow,
                        label = "Open YouTube",
                        onClick = { onQuickCommand("Open YouTube") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.Alarm,
                        label = "Set 7 AM Alarm",
                        onClick = { onQuickCommand("Set alarm for 7:00 AM") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.VolumeUp,
                        label = "Raise Volume",
                        onClick = { onQuickCommand("Increase volume") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.Settings,
                        label = "Open Settings",
                        onClick = { onQuickCommand("Open settings") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Floating Bar: Chat Screen Launcher & Big Mic Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dedicated Chat Screen Launcher Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clickable { onOpenChat() }
                        .testTag("open_chat_screen_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RexyyDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Open Chat Stream",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary
                                )
                            )
                            Text(
                                text = "${uiState.messages.size} messages in log",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Primary Voice Floating Action Button
                FloatingActionButton(
                    onClick = {
                        if (isListening) {
                            onStopListening()
                        } else {
                            if (CapabilityManager.hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                onStartListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    containerColor = if (isListening) Color(0xFFEF5350) else RexyyCyanPrimary,
                    contentColor = RexyyDarkBackground,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("main_mic_fab")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Outlined.Mic,
                        contentDescription = if (isListening) "Stop Listening" else "Start Voice Command",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = modifier
            .height(52.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RexyyCyanLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = RexyyTextPrimary
                )
            )
        }
    }
}
