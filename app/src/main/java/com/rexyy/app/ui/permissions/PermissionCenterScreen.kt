package com.rexyy.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rexyy.app.device.CapabilityManager
import com.rexyy.app.device.CapabilityStatus
import com.rexyy.app.device.DeviceCapability
import com.rexyy.app.ui.theme.RexyyCyanDim
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    onNavigateBack: () -> Unit,
    onActivateRexyy: () -> Unit = {},
    isRexyyActivated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Real list of capabilities queried live from Android APIs
    var capabilities by remember {
        mutableStateOf(CapabilityManager.getCapabilities(context))
    }

    // Refresh function that checks real Android status
    val refreshState = {
        capabilities = CapabilityManager.getCapabilities(context)
    }

    // Re-check real state whenever user returns from an Android Settings screen or dialog
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Explanatory Rationale Dialog state
    var selectedCapabilityForInfo by remember { mutableStateOf<DeviceCapability?>(null) }

    // Activity Result Launchers for Runtime Permissions
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    val phoneCallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshState()
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshState()
    }

    // Capability Counts
    val activeCount = capabilities.count { it.isGranted }
    val totalCount = capabilities.size
    val isCoreReady = capabilities.filter { it.isRequiredForCore }.all { it.isGranted }
    val progress = if (totalCount > 0) activeCount.toFloat() / totalCount else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CAPABILITY CENTER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = RexyyCyanPrimary
                            )
                        )
                        Text(
                            text = "HARDWARE & SYSTEM VERIFICATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                color = RexyyTextMuted
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("permission_center_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, if (isCoreReady) RexyyCyanPrimary else RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SYSTEM READINESS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = RexyyCyanLight
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$activeCount / $totalCount Capabilities Active",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = RexyyTextPrimary
                                )
                            )
                        }

                        // Readiness Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCoreReady) RexyyNeonGreen.copy(alpha = 0.15f) else Color(0xFFFFB74D).copy(alpha = 0.15f),
                            border = BorderStroke(
                                1.dp,
                                if (isCoreReady) RexyyNeonGreen else Color(0xFFFFB74D)
                            )
                        ) {
                            Text(
                                text = if (isCoreReady) "CORE ARMED" else "CORE PENDING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCoreReady) RexyyNeonGreen else Color(0xFFFFB74D),
                                    letterSpacing = 0.8.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isCoreReady) RexyyCyanPrimary else Color(0xFFFFB74D),
                        trackColor = RexyyDarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isCoreReady) {
                            "Core voice recognition and background alerting capabilities are ready. You can now arm REXXY or configure extended automation capabilities below."
                        } else {
                            "Microphone and Notification permissions are required to activate REXXY core voice commands. Tap each pending item below to grant."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RexyyTextSecondary,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            // Section 1: Core Capabilities
            Text(
                text = "1. CORE ASSISTANT CAPABILITIES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                ),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Render Core Capabilities (Microphone & Notifications)
            capabilities.filter { it.isRequiredForCore }.forEach { cap ->
                CapabilityCard(
                    capability = cap,
                    icon = getIconForCapability(cap.id),
                    onInfoClick = { selectedCapabilityForInfo = cap },
                    onActionClick = {
                        handleCapabilityAction(
                            cap = cap,
                            context = context,
                            micLauncher = micLauncher,
                            notifLauncher = notifLauncher,
                            contactsLauncher = contactsLauncher,
                            phoneCallLauncher = phoneCallLauncher,
                            smsLauncher = smsLauncher,
                            cameraLauncher = cameraLauncher,
                            bluetoothLauncher = bluetoothLauncher
                        )
                    }
                )
            }

            // Section 2: Automation & System Services
            Text(
                text = "2. AUTOMATION & ADVANCED INTELLIGENCE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                ),
                modifier = Modifier.padding(top = 8.dp)
            )

            // Render Automation Capabilities
            capabilities.filter { it.category == "Automation & Intelligence" || it.category == "System Controls" }.forEach { cap ->
                CapabilityCard(
                    capability = cap,
                    icon = getIconForCapability(cap.id),
                    onInfoClick = { selectedCapabilityForInfo = cap },
                    onActionClick = {
                        handleCapabilityAction(
                            cap = cap,
                            context = context,
                            micLauncher = micLauncher,
                            notifLauncher = notifLauncher,
                            contactsLauncher = contactsLauncher,
                            phoneCallLauncher = phoneCallLauncher,
                            smsLauncher = smsLauncher,
                            cameraLauncher = cameraLauncher,
                            bluetoothLauncher = bluetoothLauncher
                        )
                    }
                )
            }

            // Section 3: Communications & Hardware
            Text(
                text = "3. HARDWARE & COMMUNICATIONS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                ),
                modifier = Modifier.padding(top = 8.dp)
            )

            // Render Communications & Hardware
            capabilities.filter { it.category == "Communications" || it.category == "Hardware & Sensors" }.forEach { cap ->
                CapabilityCard(
                    capability = cap,
                    icon = getIconForCapability(cap.id),
                    onInfoClick = { selectedCapabilityForInfo = cap },
                    onActionClick = {
                        handleCapabilityAction(
                            cap = cap,
                            context = context,
                            micLauncher = micLauncher,
                            notifLauncher = notifLauncher,
                            contactsLauncher = contactsLauncher,
                            phoneCallLauncher = phoneCallLauncher,
                            smsLauncher = smsLauncher,
                            cameraLauncher = cameraLauncher,
                            bluetoothLauncher = bluetoothLauncher
                        )
                    }
                )
            }

            // Activation Completion Banner / Action
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCoreReady) RexyyCyanDim.copy(alpha = 0.2f) else RexyyDarkSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isCoreReady) RexyyCyanPrimary else RexyyDarkBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRexyyActivated) "REXXY IS ACTIVATED" else "ARM & ACTIVATE REXXY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = if (isCoreReady) RexyyCyanPrimary else RexyyTextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isRexyyActivated) {
                            "All core capabilities are online and verified. You can update or re-verify permissions at any time."
                        } else if (isCoreReady) {
                            "Required core capabilities are armed. Tap below to finalize setup and initialize the holographic core."
                        } else {
                            "Please grant Microphone permission above to activate REXXY core."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RexyyTextMuted,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isCoreReady) {
                                onActivateRexyy()
                                onNavigateBack()
                            }
                        },
                        enabled = isCoreReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RexyyCyanPrimary,
                            contentColor = Color.Black,
                            disabledContainerColor = RexyyDarkSurfaceVariant,
                            disabledContentColor = RexyyTextMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("arm_activate_rexyy_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRexyyActivated) Icons.Filled.CheckCircle else Icons.Outlined.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRexyyActivated) "REXXY ACTIVATED (CONFIRM)" else "ACTIVATE REXXY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Android System App Settings Shortcut
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RexyyCyanLight),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("os_app_settings_button")
            ) {
                Text(
                    text = "Open OS Application Details",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Capability Rationale / Info Dialog
    selectedCapabilityForInfo?.let { cap ->
        AlertDialog(
            onDismissRequest = { selectedCapabilityForInfo = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getIconForCapability(cap.id),
                        contentDescription = null,
                        tint = RexyyCyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = cap.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyTextPrimary
                        )
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = RexyyDarkSurfaceVariant
                    ) {
                        Text(
                            text = "STATUS: ${cap.statusLabel.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (cap.isGranted) RexyyNeonGreen else Color(0xFFFFB74D),
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Why REXXY needs this capability:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyCyanLight
                        )
                    )

                    Text(
                        text = cap.rationale,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = RexyyTextSecondary,
                            lineHeight = 20.sp
                        )
                    )

                    Text(
                        text = "Security Assurance: REXXY processes interactions locally. Permissions can be revoked anytime in Android Settings.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RexyyTextMuted,
                            lineHeight = 16.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = cap
                        selectedCapabilityForInfo = null
                        handleCapabilityAction(
                            cap = c,
                            context = context,
                            micLauncher = micLauncher,
                            notifLauncher = notifLauncher,
                            contactsLauncher = contactsLauncher,
                            phoneCallLauncher = phoneCallLauncher,
                            smsLauncher = smsLauncher,
                            cameraLauncher = cameraLauncher,
                            bluetoothLauncher = bluetoothLauncher
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RexyyCyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (cap.isGranted) "Done" else "Configure")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCapabilityForInfo = null }) {
                    Text("Close", color = RexyyTextSecondary)
                }
            },
            containerColor = RexyyDarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun CapabilityCard(
    capability: DeviceCapability,
    icon: ImageVector,
    onInfoClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (capability.status) {
            CapabilityStatus.AVAILABLE -> RexyyNeonGreen
            CapabilityStatus.NEEDS_SETUP -> Color(0xFFFFD54F)
            else -> Color(0xFFFF8A80)
        },
        label = "status_color"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(
            1.dp,
            if (capability.isGranted) RexyyDarkBorder else statusColor.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capability_card_${capability.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Category
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capability.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RexyyTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Info Button
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Capability info",
                        tint = RexyyTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status bar + Action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = capability.statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                // Action / Toggle Button
                if (capability.isGranted) {
                    Text(
                        text = "READY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyNeonGreen,
                            letterSpacing = 1.sp
                        )
                    )
                } else {
                    OutlinedButton(
                        onClick = onActionClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RexyyCyanLight
                        ),
                        border = BorderStroke(1.dp, RexyyCyanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("action_button_${capability.id}")
                    ) {
                        Text(
                            text = if (capability.status == CapabilityStatus.NEEDS_SETUP) "Setup" else "Grant",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Routes user to the exact Android permission request or settings screen.
 */
private fun handleCapabilityAction(
    cap: DeviceCapability,
    context: Context,
    micLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    notifLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    contactsLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    phoneCallLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    smsLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    bluetoothLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    when (cap.id) {
        "mic" -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        "notif" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppSettings(context)
            }
        }
        "notif_access" -> {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
        "accessibility_nav" -> {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
        "phone_call" -> {
            phoneCallLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
        }
        "contacts" -> contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        "sms" -> smsLauncher.launch(Manifest.permission.SEND_SMS)
        "camera" -> cameraLauncher.launch(Manifest.permission.CAMERA)
        "bluetooth" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
        "write_settings" -> {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
        "battery_opt" -> {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
        else -> openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun getIconForCapability(id: String): ImageVector {
    return when (id) {
        "mic" -> Icons.Filled.Mic
        "notif" -> Icons.Filled.Notifications
        "notif_access" -> Icons.Filled.NotificationsActive
        "accessibility_nav" -> Icons.Outlined.AccessibilityNew
        "phone_call" -> Icons.Filled.Call
        "contacts" -> Icons.Filled.Contacts
        "sms" -> Icons.Filled.Sms
        "camera" -> Icons.Filled.CameraAlt
        "bluetooth" -> Icons.Filled.Bluetooth
        "write_settings" -> Icons.Filled.BrightnessMedium
        "battery_opt" -> Icons.Filled.BatteryAlert
        else -> Icons.Filled.Settings
    }
}
