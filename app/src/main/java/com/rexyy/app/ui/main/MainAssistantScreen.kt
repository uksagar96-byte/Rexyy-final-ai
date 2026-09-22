package com.rexyy.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.rexyy.app.ui.chat.CommandPillState
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

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchTarget by remember { mutableStateOf("youtube") }

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
                                else if (uiState.isRexyyActivated) RexyyNeonGreen
                                else RexyyAmberWarning
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "REXYY // PHONE COMMANDER",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = RexyyCyanPrimary
                            )
                        )
                        Text(
                            text = if (uiState.isRexyyActivated) {
                                if (isBgRunning) "ONLINE // BACKGROUND ACTIVE" else "ONLINE // READY"
                            } else {
                                "STANDBY // ACTIVATION REQUIRED"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (uiState.isRexyyActivated) RexyyNeonGreen else RexyyAmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
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

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // 2. DYNAMIC-ISLAND STYLE COMMAND PILL
            // ==========================================
            DynamicCommandPill(
                uiState = uiState,
                isBgRunning = isBgRunning,
                onMicClick = {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dynamic_command_pill")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Pills Bar: FPS, BG Service, Battery, RAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prominent Real FPS Indicator
                HudPill(
                    label = "⚡ ${telemetry.fps} FPS",
                    color = if (telemetry.fps >= 50) RexyyNeonGreen else if (telemetry.fps >= 30) RexyyCyanLight else RexyyAmberWarning,
                    modifier = Modifier.weight(1.0f)
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
                    label = "🔋 ${telemetry.batteryPercent}%",
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

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 3. ACTIVATION HERO OR 3D AVATAR CORE
            // ==========================================
            if (!uiState.isRexyyActivated) {
                // Not Activated: Prominent Activation Center Hero
                ActivationHeroCard(
                    onActivateClick = { showVerificationSheet = true },
                    onLearnMoreClick = { onShowIntro(true) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Activated: Interactive 3D Holographic Core
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
                            .padding(vertical = 18.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = "[ ◈ REXYY CORE // ACTIVE ◈ ]",
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
                            size = 190.dp,
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

                        Spacer(modifier = Modifier.height(12.dp))

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
                                .fillMaxWidth(0.92f)
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

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 4. REAL-TIME TELEMETRY MATRIX (HUD GRID)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE TELEMETRY & HARDWARE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = RexyyCyanLight
                    )
                )
                Text(
                    text = "FPS: ${telemetry.fps} // REAL SENSORS",
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
                    icon = Icons.Outlined.Speed,
                    title = "FPS",
                    value = "${telemetry.fps}",
                    sub = if (telemetry.fps >= 50) "Smooth 60Hz" else "Real Frame",
                    accent = if (telemetry.fps >= 50) RexyyNeonGreen else RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )

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
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMiniCard(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "AUDIO",
                    value = "${telemetry.mediaVolumePercent}%",
                    sub = "Volume",
                    accent = Color(0xFF2979FF),
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.Bolt,
                    title = "BRIGHTNESS",
                    value = "${telemetry.screenBrightnessPercent}%",
                    sub = "Display",
                    accent = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.StorageIcon,
                    title = "STORAGE",
                    value = "${telemetry.availableStorageGb}G",
                    sub = "Free of ${telemetry.totalStorageGb}G",
                    accent = Color(0xFFAB47BC),
                    modifier = Modifier.weight(1f)
                )

                TelemetryMiniCard(
                    icon = Icons.Outlined.Memory,
                    title = "CPU",
                    value = "${telemetry.cpuLoadPercent}%",
                    sub = "${telemetry.cpuCores} Cores",
                    accent = RexyyCyanLight,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 5. REAL SYSTEM UTILITIES (NO DEMOS)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM CONTROLS & UTILITIES",
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
                SystemUtilityChip(
                    icon = Icons.Outlined.FlashlightOn,
                    label = "Toggle Flashlight",
                    onClick = { toggleTorchDirect(context) }
                )
                SystemUtilityChip(
                    icon = Icons.Outlined.Settings,
                    label = "System Settings",
                    onClick = { onQuickCommand("Open settings") }
                )
                SystemUtilityChip(
                    icon = Icons.Outlined.Wifi,
                    label = "Wi-Fi Settings",
                    onClick = { onQuickCommand("Open wifi settings") }
                )
                SystemUtilityChip(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Max Volume",
                    onClick = { onQuickCommand("Volume 100") }
                )
                SystemUtilityChip(
                    icon = Icons.Outlined.Security,
                    label = "Verification Center",
                    onClick = { showVerificationSheet = true }
                )
                SystemUtilityChip(
                    icon = Icons.Outlined.Bolt,
                    label = "Replay Activation",
                    onClick = onReplayActivation
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 6. COMMAND DOCK & NAVIGATION
            // ==========================================
            // Text Command Input Field + Direct Send
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) RexyyCyanPrimary else RexyyTextMuted
                        )
                    }

                    OutlinedTextField(
                        value = commandInputText,
                        onValueChange = { commandInputText = it },
                        placeholder = {
                            Text("Type command (e.g. 'brightness 80', 'open camera')", color = RexyyTextMuted, fontSize = 12.sp)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Row: Control Center & Chat Console
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Control Center Launcher
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
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
                                text = "Hardware & Sensors",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RexyyTextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Chat Console Launcher
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                    border = BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
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
        // 7. CAPABILITY VERIFICATION CENTER MODAL
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
        // 8. CINEMATIC ACTIVATION OVERLAY
        // ==========================================
        if (uiState.showActivationCinematic) {
            CinematicActivationOverlay(
                onFinished = onDismissActivationCinematic
            )
        }

        // ==========================================
        // 9. INTRODUCTORY MODAL (WHAT IS REXYY?)
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
                            text = "REXXY is a professional personal AI assistant and Android phone commander.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = RexyyTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "• Local-First Phone Control: Simple phone actions (torch, volume, brightness, apps, alarms, calls, WhatsApp, accessibility) execute locally with zero latency, without relying on cloud models.\n\n" +
                                    "• Deterministic Security: Dangerous actions require your explicit confirmation before executing.\n\n" +
                                    "• Notification Intelligence: Listens to incoming notifications and announces who messaged you.\n\n" +
                                    "• Full Telemetry: Real frame rate (FPS), CPU, RAM, battery charging, and network telemetry.\n\n" +
                                    "• Seamless Fallback: Conversational knowledge queries route to advanced generative AI (Gemini / OpenAI / OpenRouter).",
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
                            showVerificationSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary)
                    ) {
                        Text("Proceed to Activation", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onShowIntro(false) }) {
                        Text("Close", color = RexyyTextMuted)
                    }
                }
            )
        }

        // ==========================================
        // 10. UNIVERSAL SEARCH DIALOG
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

// ==========================================
// DYNAMIC-ISLAND STYLE COMMAND PILL
// ==========================================
@Composable
private fun DynamicCommandPill(
    uiState: ChatUiState,
    isBgRunning: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillState = uiState.commandPillState
    val isListening = uiState.voiceState == VoiceState.LISTENING

    val (borderColor, containerColor) = when {
        pillState == CommandPillState.LISTENING || isListening -> Pair(RexyyCyanPrimary, RexyyDarkSurfaceVariant)
        pillState == CommandPillState.WORKING -> Pair(Color(0xFF80D8FF), RexyyDarkSurface)
        pillState == CommandPillState.SUCCESS -> Pair(RexyyNeonGreen, RexyyDarkSurface)
        pillState == CommandPillState.ERROR || uiState.errorMessage != null -> Pair(Color(0xFFFF5252), RexyyDarkSurface)
        else -> Pair(RexyyDarkBorder, RexyyDarkSurface)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.clickable { onMicClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Leading indicator
                when {
                    pillState == CommandPillState.LISTENING || isListening -> {
                        PulsingWaveIndicator()
                    }
                    pillState == CommandPillState.WORKING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RexyyCyanPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    pillState == CommandPillState.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = RexyyNeonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    pillState == CommandPillState.ERROR || uiState.errorMessage != null -> {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isBgRunning) RexyyNeonGreen else RexyyCyanLight)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text payload
                val displayText = when {
                    pillState == CommandPillState.LISTENING || isListening -> "LISTENING // SPEAK YOUR COMMAND..."
                    pillState == CommandPillState.WORKING -> "EXECUTING: ${uiState.lastRecognizedCommand ?: "Processing"}"
                    pillState == CommandPillState.SUCCESS -> uiState.lastActionFeedback ?: "Command executed successfully"
                    pillState == CommandPillState.ERROR || uiState.errorMessage != null -> uiState.errorMessage ?: "Action failed"
                    isBgRunning -> "● LIVE BACKGROUND ASSISTANT ACTIVE"
                    uiState.isRexyyActivated -> "REXXY COMMAND CENTER // STANDBY"
                    else -> "REXXY // TAP ACTIVATE TO INITIALIZE"
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            pillState == CommandPillState.LISTENING || isListening -> RexyyCyanLight
                            pillState == CommandPillState.SUCCESS -> RexyyNeonGreen
                            pillState == CommandPillState.ERROR || uiState.errorMessage != null -> Color(0xFFFF8A80)
                            else -> RexyyTextPrimary
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }

            // Trailing Mic Icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isListening) RexyyCyanPrimary else RexyyDarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Mic",
                    tint = if (isListening) Color.Black else RexyyCyanLight,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PulsingWaveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(modifier = Modifier.size(3.dp, 12.dp).background(RexyyCyanPrimary.copy(alpha = alpha)))
        Box(modifier = Modifier.size(3.dp, 16.dp).background(RexyyCyanPrimary))
        Box(modifier = Modifier.size(3.dp, 8.dp).background(RexyyCyanPrimary.copy(alpha = alpha)))
    }
}

// ==========================================
// ACTIVATION HERO CARD (WHEN NOT ACTIVATED)
// ==========================================
@Composable
private fun ActivationHeroCard(
    onActivateClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(RexyyCyanPrimary, RexyyNeonGreen))),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(RexyyDarkSurfaceVariant)
                    .border(1.5.dp, RexyyCyanPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = RexyyCyanPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "REXXY PHONE COMMANDER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = RexyyCyanPrimary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Local Deterministic System Engine & AI Assistant",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = RexyyTextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Activation CTA
            Button(
                onClick = onActivateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RexyyCyanPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("activate_rexyy_primary_button")
            ) {
                Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACTIVATE REXYY",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onLearnMoreClick) {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = RexyyCyanLight, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("What is REXYY & Privacy Policy", color = RexyyCyanLight, style = MaterialTheme.typography.labelSmall)
            }
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
                Text("Complete & Arm REXYY", color = Color.Black, fontWeight = FontWeight.Bold)
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

    val infiniteTransition = rememberInfiniteTransition(label = "rings")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

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
            // Rotating Holographic Rings
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
                text = "INITIALIZING REXYY...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = RexyyCyanPrimary,
                    fontFamily = FontFamily.Monospace
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stepped Terminal Log Sequence
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
                TerminalLogLine(">> [ONLINE] REXYY ACTIVATED.", active = step >= 4, color = RexyyNeonGreen)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onFinished,
                colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("ENTER COMMAND CENTER", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TerminalLogLine(text: String, active: Boolean, color: Color = RexyyCyanLight) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn()
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

// ==========================================
// SYSTEM HELPER FUNCTIONS & COMPOSABLES
// ==========================================
private fun toggleTorchDirect(context: Context) {
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val cameraId = cameraManager?.cameraIdList?.firstOrNull()
        if (cameraManager != null && cameraId != null) {
            // Note: toggles flashlight via hardware camera manager
            cameraManager.setTorchMode(cameraId, true)
        }
    } catch (_: Exception) {}
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
private fun SystemUtilityChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = Modifier.clickable { onClick() }
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

private val Icons.Outlined.StorageIcon: ImageVector
    get() = Icons.Outlined.Memory
