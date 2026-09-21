package com.rexyy.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.device.CapabilityManager
import com.rexyy.app.device.DeviceTelemetryManager
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.service.BackgroundAssistantManager
import com.rexyy.app.service.RexyyAssistantServiceState
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAssistantScreen(
    uiState: ChatUiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenControlCenter: () -> Unit = {},
    onOpenDevConsole: () -> Unit = {},
    onConfirmAction: () -> Unit = {},
    onCancelAction: () -> Unit = {},
    onCancelTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryManager = remember { DeviceTelemetryManager(context) }
    val telemetry by telemetryManager.telemetry.collectAsState()

    val isBgRunning by RexyyAssistantServiceState.serviceRunning.collectAsState()
    val bgState by RexyyAssistantServiceState.currentState.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchTarget by remember { mutableStateOf("youtube") }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // 1. TOP HUD / STATUS BAR
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Identity & Status Dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) RexyyCyanPrimary
                                else if (isBgRunning) RexyyNeonGreen
                                else RexyyCyanLight
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "REXYY // CONTROL HUD",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = RexyyCyanPrimary
                            )
                        )
                        Text(
                            text = if (isListening) "LISTENING..." else if (isBgRunning) "ALWAYS READY (BG ON)" else "READY (STANDBY)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isListening) RexyyCyanPrimary else if (isBgRunning) RexyyNeonGreen else RexyyTextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Top Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenControlCenter,
                        modifier = Modifier.testTag("open_control_center_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Control Center",
                            tint = RexyyCyanLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenDevConsole,
                        modifier = Modifier.testTag("open_dev_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = "Dev Console",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.testTag("open_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Capabilities",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Pills Bar: AI Model, Background Assistant Toggle, Battery, RAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Model Pill
                HudPill(
                    label = when (uiState.selectedProvider) {
                        AiProviderType.GEMINI -> "GEMINI 2.5"
                        AiProviderType.OPENAI -> "GPT-4O"
                        AiProviderType.OPENROUTER -> "OPENROUTER"
                        AiProviderType.LOCAL_TEST -> "LOCAL ROUTER"
                    },
                    color = RexyyCyanLight,
                    modifier = Modifier.weight(1.1f)
                )

                // Background Assistant Toggle Pill
                HudPill(
                    label = if (isBgRunning) "BG: ON" else "BG: OFF",
                    color = if (isBgRunning) RexyyNeonGreen else RexyyTextMuted,
                    onClick = {
                        BackgroundAssistantManager.toggleAssistant(context)
                    },
                    modifier = Modifier.weight(0.9f)
                )

                // Battery Pill
                HudPill(
                    label = "⚡ ${telemetry.batteryPercent}%",
                    color = if (telemetry.batteryPercent > 20) RexyyNeonGreen else Color(0xFFFF5252),
                    modifier = Modifier.weight(0.9f)
                )

                // RAM Pill
                HudPill(
                    label = "RAM ${telemetry.ramUsagePercent}%",
                    color = Color(0xFF2979FF),
                    modifier = Modifier.weight(0.9f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. CENTER STAGE — INTERACTIVE 3D HUD CORE
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, Brush.radialGradient(listOf(RexyyCyanPrimary.copy(alpha = 0.4f), RexyyDarkBorder))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "[ ◈ REXYY CORE ◈ ]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RexyyCyanLight,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3D Avatar Sphere
                    RexyyAvatar3D(
                        state = assistantState,
                        size = 210.dp,
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic State Banner
                    val statusText = when {
                        uiState.errorMessage != null -> uiState.errorMessage
                        isListening -> "LISTENING // SPEAK COMMAND NOW"
                        uiState.voiceState == VoiceState.PROCESSING || uiState.isLoading -> "PROCESSING // DETERMINISTIC ROUTER"
                        uiState.voiceState == VoiceState.SPEAKING -> "TRANSMITTING SPEECH..."
                        !uiState.lastActionFeedback.isNullOrBlank() -> uiState.lastActionFeedback
                        else -> "SYSTEM READY // SAY 'HELLO REX' OR TAP CORE"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RexyyDarkSurfaceVariant)
                            .border(1.dp, if (isListening) RexyyCyanPrimary else RexyyDarkBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.errorMessage != null) Color(0xFFFF8A80) else if (isListening) RexyyCyanLight else RexyyTextPrimary,
                                fontFamily = FontFamily.Monospace
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Pending Confirmation Card
            if (uiState.pendingConfirmation != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.5.dp, RexyyCyanPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SECURITY CONFIRMATION REQUIRED",
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
                                Text("Confirm Action", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Task In Progress Card
            if (uiState.activeTaskPlan != null && !uiState.activeTaskPlan.isFinished) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyCyanPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE MULTI-STEP TASK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyCyanPrimary
                                )
                            )
                            TextButton(onClick = onCancelTask) {
                                Text("Cancel", color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = uiState.activeTaskPlan.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = RexyyTextPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. REAL-TIME TELEMETRY MATRIX (HUD GRID)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE TELEMETRY OVERVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = RexyyCyanLight
                    )
                )
                Text(
                    text = "LIVE SENSORS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RexyyNeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMiniCard(
                    icon = Icons.Outlined.BatteryChargingFull,
                    title = "POWER",
                    value = "${telemetry.batteryPercent}%",
                    sub = if (telemetry.isCharging) "Charging" else "Battery",
                    accent = if (telemetry.batteryPercent > 20) RexyyNeonGreen else Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.Memory,
                    title = "RAM",
                    value = "${telemetry.usedRamGb}G",
                    sub = "${telemetry.ramUsagePercent}% Used",
                    accent = RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.NetworkCheck,
                    title = "NET",
                    value = if (telemetry.isWifiConnected) "Wi-Fi" else if (telemetry.isCellularConnected) "Data" else "Offline",
                    sub = if (telemetry.isWifiConnected) "Online" else telemetry.cellularType,
                    accent = if (telemetry.isWifiConnected) RexyyCyanPrimary else if (telemetry.isCellularConnected) RexyyNeonGreen else Color(0xFFFF8A80),
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.VolumeUp,
                    title = "AUDIO",
                    value = "${telemetry.mediaVolumePercent}%",
                    sub = "Media",
                    accent = Color(0xFF2979FF),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 4. UNIVERSAL SEARCH & QUICK COMMAND DOCK
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FAST COMMAND LAUNCHER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = RexyyCyanLight
                    )
                )

                TextButton(onClick = { showSearchDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = RexyyCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Universal Search", color = RexyyCyanPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FastActionChip(
                    icon = Icons.Outlined.PlayArrow,
                    label = "YouTube: Cricket",
                    onClick = { onQuickCommand("YouTube pe cricket search karo") }
                )
                FastActionChip(
                    icon = Icons.Outlined.Chat,
                    label = "WhatsApp Ramzan",
                    onClick = { onQuickCommand("Ramzan ko WhatsApp message bhejo") }
                )
                FastActionChip(
                    icon = Icons.Outlined.Alarm,
                    label = "7:00 AM Alarm",
                    onClick = { onQuickCommand("Set alarm for 7:00 AM") }
                )
                FastActionChip(
                    icon = Icons.Outlined.FlashlightOn,
                    label = "Toggle Torch",
                    onClick = { onQuickCommand("Torch chalu karo") }
                )
                FastActionChip(
                    icon = Icons.Outlined.GraphicEq,
                    label = "Check Battery",
                    onClick = { onQuickCommand("Battery kitni hai") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 5. BOTTOM COMMAND BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Control Center Launcher
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable { onOpenControlCenter() }
                        .testTag("open_control_center_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(RexyyDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Control Center",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "Hardware & Telemetry",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Primary Holographic Mic Button
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
                        .size(60.dp)
                        .testTag("main_mic_fab")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Outlined.Mic,
                        contentDescription = if (isListening) "Stop Listening" else "Start Voice Command",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Chat Console Launcher
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable { onOpenChat() }
                        .testTag("open_chat_screen_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Chat Console",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "${uiState.messages.size} msgs in log",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 6. UNIVERSAL SEARCH DIALOG
        // ==========================================
        if (showSearchDialog) {
            AlertDialog(
                onDismissRequest = { showSearchDialog = false },
                containerColor = RexyyDarkSurface,
                title = {
                    Text(
                        text = "UNIVERSAL SEARCH",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyCyanPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Search directly across YouTube, Chrome, Maps, or Spotify:",
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SearchTargetPill("YouTube", "youtube", selectedSearchTarget) { selectedSearchTarget = it }
                            SearchTargetPill("Chrome", "chrome", selectedSearchTarget) { selectedSearchTarget = it }
                            SearchTargetPill("Maps", "maps", selectedSearchTarget) { selectedSearchTarget = it }
                            SearchTargetPill("Spotify", "spotify", selectedSearchTarget) { selectedSearchTarget = it }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Enter search query...", color = RexyyTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RexyyCyanPrimary,
                                unfocusedBorderColor = RexyyDarkBorder,
                                focusedTextColor = RexyyTextPrimary,
                                unfocusedTextColor = RexyyTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                onQuickCommand("Search $searchQuery on $selectedSearchTarget")
                                showSearchDialog = false
                                searchQuery = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary)
                    ) {
                        Text("Search", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSearchDialog = false }) {
                        Text("Cancel", color = RexyyTextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchTargetPill(
    label: String,
    target: String,
    currentTarget: String,
    onSelect: (String) -> Unit
) {
    val isSelected = target == currentTarget
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) RexyyCyanPrimary else RexyyDarkSurfaceVariant)
            .clickable { onSelect(target) }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else RexyyTextSecondary,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun HudPill(
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RexyyDarkSurface)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun TelemetryMiniCard(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RexyyTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyTextPrimary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accent,
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FastActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = Modifier
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RexyyCyanLight,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = RexyyTextPrimary,
                    fontSize = 11.sp
                )
            )
        }
    }
}
