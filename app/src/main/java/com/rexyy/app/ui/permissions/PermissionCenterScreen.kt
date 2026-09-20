package com.rexyy.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.device.CapabilityManager
import com.rexyy.app.device.CapabilityStatus
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(CapabilityManager.hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }
    var hasNotif by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                CapabilityManager.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotif = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Permission & Capability Center",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyCyanPrimary
                        )
                    )
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Assistant Device Capabilities",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyTextPrimary
                )
            )
            Text(
                text = "REXYY uses standard Android permissions to interact with your device. You have full transparency and control over what the assistant can access.",
                style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextSecondary)
            )

            // Microphone Card
            PermissionItemCard(
                icon = Icons.Outlined.Mic,
                title = "Microphone (RECORD_AUDIO)",
                description = "Enables real-time speech-to-text recognition for voice control.",
                isGranted = hasMic,
                actionLabel = "Grant Microphone",
                onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            // Notifications Card (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItemCard(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications (POST_NOTIFICATIONS)",
                    description = "Delivers assistant status alerts, task alarms, and reminders.",
                    isGranted = hasNotif,
                    actionLabel = "Grant Notifications",
                    onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            // Alarms & Clock
            PermissionItemCard(
                icon = Icons.Outlined.Alarm,
                title = "Alarms & Clock (SET_ALARM)",
                description = "Allows REXYY to schedule system alarms and timers on your device.",
                isGranted = true,
                actionLabel = null,
                onAction = {}
            )

            // System App Settings Link
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System App Settings",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To revoke or modify all permissions at any time, visit the Android OS App Info page.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextMuted)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RexyyCyanLight),
                        border = BorderStroke(1.dp, RexyyCyanPrimary)
                    ) {
                        Text("Open OS App Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, if (isGranted) RexyyNeonGreen else RexyyDarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RexyyDarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) RexyyNeonGreen else RexyyCyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RexyyTextPrimary
                        )
                    )
                    Text(
                        text = if (isGranted) "Granted & Active" else "Not Granted",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isGranted) RexyyNeonGreen else RexyyTextMuted
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
            )

            if (!isGranted && actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RexyyCyanPrimary,
                        contentColor = RexyyDarkBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
