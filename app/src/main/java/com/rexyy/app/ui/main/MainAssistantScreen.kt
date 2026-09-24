package com.rexyy.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.rexyy.app.service.BackgroundAssistantManager
import com.rexyy.app.service.RexyyAssistantServiceState
import com.rexyy.app.ui.avatar.AssistantState
import com.rexyy.app.ui.avatar.RexyyAvatar3D
import com.rexyy.app.ui.chat.ChatUiState
import com.rexyy.app.ui.theme.RexyyAmberWarning
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
import kotlinx.coroutines.delay

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
    onActivateRexyy: () -> Unit = {},
    onDismissActivationCinematic: () -> Unit = {},
    onReplayActivation: () -> Unit = {},
    onShowIntro: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryManager = remember { DeviceTelemetryManager(context) }
    val telemetry by telemetryManager.telemetry.collectAsState()

    val isBgRunning by RexyyAssistantServiceState.serviceRunning.collectAsState()

    var showVerificationSheet by remember { mutableStateOf(false) }
    var commandInputText by remember { mutableStateOf("") }

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
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // 1. TOP BAR: BRANDING & LIVE ASSISTANT STATE
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Identity
                Column {
                    Text(
                        text = "REXXY",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "YOUR AI ASSISTANT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = RexyyCyanLight,
                            fontSize = 10.sp
                        )
                    )
                }

                // Status Badge & Navigation Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Live State Indicator Pill
                    val (stateLabel, stateColor) = when {
                        uiState.errorMessage != null -> Pair("ALERT", Color(0xFFFF5252))
                        isListening -> Pair("LISTENING", RexyyCyanPrimary)
                        uiState.voiceState == VoiceState.PROCESSING || uiState.isLoading -> Pair("WORKING", Color(0xFFB388FF))
                        uiState.voiceState == VoiceState.SPEAKING -> Pair("SPEAKING", RexyyNeonGreen)
                        uiState.isRexyyActivated -> if (isBgRunning) Pair("ONLINE", RexyyNeonGreen) else Pair("STANDBY", RexyyCyanLight)
                        else -> Pair("NOT ARMED", RexyyAmberWarning)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(RexyyDarkSurface)
                            .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(stateColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = stateColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onOpenControlCenter,
                        modifier = Modifier.size(36.dp).testTag("open_control_center_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Control Center",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.size(36.dp).testTag("open_permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Permissions",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenDevConsole,
                        modifier = Modifier.size(36.dp).testTag("open_dev_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = "Dev Console",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp).testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = RexyyTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 2. ACTIVATION BANNER (VISIBLE WHEN INCOMPLETE)
            // ==========================================
            if (!uiState.isRexyyActivated) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(RexyyCyanPrimary, RexyyNeonGreen))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation_incomplete_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RexyyDarkSurfaceVariant)
                                    .border(1.dp, RexyyCyanPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = RexyyCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ACTIVATION REQUIRED",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = RexyyCyanLight
                                    )
                                )
                                Text(
                                    text = "Configure device permissions & automation",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RexyyTextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = { onShowIntro(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RexyyCyanPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("activate_rexyy_primary_button")
                        ) {
                            Text(
                                text = "ACTIVATE",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ==========================================
            // 3. CENTRAL REXXY CORE (PROMINENT HERO)
            // ==========================================
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(
                    1.dp,
                    Brush.radialGradient(
                        colors = listOf(
                            RexyyCyanPrimary.copy(alpha = if (isListening) 0.6f else 0.25f),
                            RexyyDarkBorder
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("central_rexyy_core_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 18.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Header tag above avatar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isListening) RexyyCyanPrimary else RexyyNeonGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isRexyyActivated) "REXXY NEURAL CORE // ONLINE" else "REXXY CORE // STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = RexyyCyanLight,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prominent Large Central 3D Avatar (Size 260.dp)
                    RexyyAvatar3D(
                        state = assistantState,
                        size = 260.dp,
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

                    // Real-time Dynamic Status Line
                    val statusText = when {
                        uiState.errorMessage != null -> uiState.errorMessage
                        isListening -> "LISTENING // SPEAK YOUR COMMAND NOW"
                        uiState.voiceState == VoiceState.PROCESSING || uiState.isLoading -> "PROCESSING // DETERMINISTIC ROUTING"
                        uiState.voiceState == VoiceState.SPEAKING -> "TRANSMITTING SPEECH RESPONSE"
                        !uiState.lastActionFeedback.isNullOrBlank() -> uiState.lastActionFeedback
                        isBgRunning -> "STANDBY // SAY 'HELLO REX' OR TAP CORE"
                        else -> "STANDBY // TAP CORE TO COMMAND"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RexyyDarkSurfaceVariant)
                            .border(
                                1.dp,
                                if (isListening) RexyyCyanPrimary.copy(alpha = 0.8f) else RexyyDarkBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusText ?: "SYSTEM READY",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.errorMessage != null) Color(0xFFFF8A80)
                                else if (isListening) RexyyCyanLight
                                else RexyyTextPrimary,
                                fontSize = 11.sp
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            // ==========================================
            // 4. SECURITY CONFIRMATION (DANGEROUS ACTIONS)
            // ==========================================
            if (uiState.pendingConfirmation != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.5.dp, RexyyAmberWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = RexyyAmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SECURITY CONFIRMATION REQUIRED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = RexyyAmberWarning
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.pendingConfirmation.prompt,
                            style = MaterialTheme.typography.bodyMedium.copy(
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

            // Active Multi-step Task Plan Card
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
            // 5. LIVE SYSTEM TELEMETRY MATRIX (REAL VALUES)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME TELEMETRY MATRIX",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = RexyyCyanLight
                    )
                )
                Text(
                    text = "${telemetry.fps} FPS // LIVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (telemetry.fps >= 50) RexyyNeonGreen else RexyyCyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: FPS, POWER, RAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryCard(
                    icon = Icons.Outlined.Speed,
                    title = "FPS",
                    value = "${telemetry.fps}",
                    sub = if (telemetry.fps >= 50) "Hardware 60Hz" else "Real Frame",
                    accent = if (telemetry.fps >= 50) RexyyNeonGreen else RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Outlined.BatteryChargingFull,
                    title = "POWER",
                    value = "${telemetry.batteryPercent}%",
                    sub = if (telemetry.isCharging) "Charging" else "Battery",
                    accent = if (telemetry.batteryPercent > 20) RexyyNeonGreen else Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Outlined.Memory,
                    title = "RAM",
                    value = "${telemetry.usedRamGb}G",
                    sub = "${telemetry.ramUsagePercent}% Used",
                    accent = RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: STORAGE, NETWORK, VOLUME
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryCard(
                    icon = Icons.Outlined.Storage,
                    title = "STORAGE",
                    value = "${telemetry.freeStorageGb}G",
                    sub = "Free Space",
                    accent = RexyyCyanLight,
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Outlined.Wifi,
                    title = "LINK",
                    value = if (telemetry.isWifiConnected) "Wi-Fi" else if (telemetry.isCellularConnected) telemetry.cellularType else "Offline",
                    sub = if (telemetry.isWifiConnected || telemetry.isCellularConnected) "Connected" else "No Link",
                    accent = if (telemetry.isWifiConnected || telemetry.isCellularConnected) RexyyNeonGreen else Color(0xFFFF8A80),
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Outlined.VolumeUp,
                    title = "AUDIO",
                    value = "${telemetry.mediaVolumePercent}%",
                    sub = "Media Vol",
                    accent = RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 6. CLEAN MANUAL COMMAND ENTRY (NO GIANT MIC)
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInputText,
                        onValueChange = { commandInputText = it },
                        placeholder = {
                            Text(
                                "Enter command or ask REXXY...",
                                color = RexyyTextMuted,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = RexyyTextPrimary,
                            unfocusedTextColor = RexyyTextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_dock_text_field")
                    )

                    IconButton(
                        onClick = {
                            if (commandInputText.isNotBlank()) {
                                onQuickCommand(commandInputText)
                                commandInputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("command_dock_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Send Command",
                            tint = if (commandInputText.isNotBlank()) RexyyCyanPrimary else RexyyTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Links: Control Center & Chat Console
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Control Center Action Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
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
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(RexyyDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Control Center",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "Sensors & Toggles",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                // Chat Console Action Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
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
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(RexyyDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Chat Console",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "Full Logs & AI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7. CAPABILITY VERIFICATION CENTER DIALOG
        // ==========================================
        if (showVerificationSheet) {
            CapabilityVerificationDialog(
                context = context,
                onDismiss = { showVerificationSheet = false },
                onActivateConfirmed = {
                    showVerificationSheet = false
                    onActivateRexyy()
                }
            )
        }

        // ==========================================
        // 8. ACTIVATION CINEMATIC OVERLAY
        // ==========================================
        if (uiState.showActivationCinematic) {
            CinematicActivationOverlay(
                onFinished = onDismissActivationCinematic
            )
        }

        // ==========================================
        // 9. INTRO INFO DIALOG
        // ==========================================
        if (uiState.showIntroDialog) {
            AlertDialog(
                onDismissRequest = { onShowIntro(false) },
                containerColor = RexyyDarkSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Bolt, contentDescription = null, tint = RexyyCyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WHAT IS REXYY?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = RexyyCyanPrimary,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "REXXY is your personal on-device AI assistant and Android system commander.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = RexyyTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "• Local-First Phone Control: Device operations (torch, volume, brightness, apps, alarms, calls, WhatsApp, accessibility) execute locally with zero latency.\n\n" +
                                    "• Deterministic Security: Protected actions require your explicit confirmation before executing.\n\n" +
                                    "• Notification Intelligence: Listens to incoming notifications and announces who messaged you.\n\n" +
                                    "• Real System Telemetry: Real frame rate (FPS), CPU, RAM, battery charging, and network telemetry.\n\n" +
                                    "• Cloud AI Fallback: Complex general knowledge queries route to advanced generative AI (Gemini / OpenAI / OpenRouter).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = RexyyTextSecondary,
                                lineHeight = 18.sp
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onShowIntro(false)
                            onOpenPermissions()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary)
                    ) {
                        Text("Open Capability Center", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onShowIntro(false) }) {
                        Text("Close", color = RexyyTextMuted)
                    }
                }
            )
        }
    }
}

// ==========================================
// TELEMETRY CARD COMPONENT
// ==========================================
@Composable
private fun TelemetryCard(
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
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RexyyTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyTextPrimary,
                    fontSize = 13.sp
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

// ==========================================
// CAPABILITY VERIFICATION CENTER DIALOG
// ==========================================
@Composable
private fun CapabilityVerificationDialog(
    context: Context,
    onDismiss: () -> Unit,
    onActivateConfirmed: () -> Unit
) {
    val capabilities = remember { CapabilityManager.getCapabilities(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RexyyDarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Security, contentDescription = null, tint = RexyyCyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CAPABILITY VERIFICATION",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = RexyyCyanPrimary,
                        letterSpacing = 1.sp
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "REXXY inspects hardware permissions and automation services locally. Everything verified runs directly on your device.",
                    style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                )

                Spacer(modifier = Modifier.height(4.dp))

                capabilities.forEach { cap ->
                    val isGranted = cap.status == com.rexyy.app.device.CapabilityStatus.AVAILABLE

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RexyyDarkSurfaceVariant)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cap.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RexyyTextPrimary
                                )
                            )
                            Text(
                                text = cap.description,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 10.sp
                                ),
                                maxLines = 2
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Granted",
                                tint = RexyyNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    if (cap.requiresAccessibility) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (_: Exception) {}
                                    } else if (cap.id == "notif_access") {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (_: Exception) {}
                                    } else if (cap.id == "write_settings") {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (_: Exception) {}
                                    } else {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text("Configure", color = RexyyCyanLight, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onActivateConfirmed,
                colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary)
            ) {
                Text("Arm & Activate REXXY", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RexyyTextMuted)
            }
        }
    )
}

// ==========================================
// CINEMATIC ACTIVATION OVERLAY
// ==========================================
@Composable
private fun CinematicActivationOverlay(
    onFinished: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(600)
        step = 1
        delay(800)
        step = 2
        delay(800)
        step = 3
        delay(800)
        step = 4
        delay(1200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = RexyyCyanPrimary.copy(alpha = 0.3f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = RexyyNeonGreen.copy(alpha = 0.4f),
                        radius = size.minDimension / 2.8f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = RexyyCyanPrimary,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "INITIALIZING REXXY...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = RexyyCyanPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RexyyDarkSurface)
                    .border(1.dp, RexyyDarkBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TerminalLogLine(">> [SYS_BOOT] STARTING QUANTUM TELEMETRY...", active = step >= 0)
                TerminalLogLine(">> [LOCAL_BUS] DETERMINISTIC ROUTER ENGAGED", active = step >= 1)
                TerminalLogLine(">> [AUDIO_SYNTH] TTS ENGINE PRIMED", active = step >= 2)
                TerminalLogLine(">> [SECURITY] ALL LOCAL GATES ARMED", active = step >= 3)
                TerminalLogLine(">> [ONLINE] REXXY ACTIVATED.", active = step >= 4, color = RexyyNeonGreen)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFinished,
                colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("ENTER COMMAND DASHBOARD", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TerminalLogLine(text: String, active: Boolean, color: Color = RexyyCyanLight) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
