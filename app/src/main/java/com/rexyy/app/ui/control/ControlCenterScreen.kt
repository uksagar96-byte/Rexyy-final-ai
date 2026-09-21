package com.rexyy.app.ui.control

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.device.DeviceTelemetryManager
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
fun ControlCenterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryManager = remember { DeviceTelemetryManager(context) }
    val telemetry by telemetryManager.telemetry.collectAsState()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
    var volumeSlider by remember { mutableFloatStateOf(currentVol.toFloat() / maxVolume.toFloat()) }

    var isTorchOn by remember { mutableStateOf(false) }

    fun toggleTorch() {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val id = camManager?.cameraIdList?.firstOrNull()
            if (camManager != null && id != null) {
                isTorchOn = !isTorchOn
                camManager.setTorchMode(id, isTorchOn)
            }
        } catch (_: Exception) {}
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("control_center_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RexyyCyanPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "REXYY CONTROL CENTER",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = RexyyCyanPrimary
                        )
                    )
                    Text(
                        text = "SYSTEM TELEMETRY & HARDWARE CONTROL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RexyyTextMuted,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: System Health Telemetry
            Text(
                text = "REAL-TIME TELEMETRY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryCard(
                    title = "BATTERY",
                    value = "${telemetry.batteryPercent}%",
                    subtitle = if (telemetry.isCharging) "Charging (${telemetry.chargingSource})" else "Discharging",
                    progress = telemetry.batteryPercent / 100f,
                    accentColor = if (telemetry.batteryPercent > 20) RexyyNeonGreen else Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    title = "MEMORY (RAM)",
                    value = "${telemetry.usedRamGb} / ${telemetry.totalRamGb} GB",
                    subtitle = "${telemetry.ramUsagePercent}% Used",
                    progress = telemetry.ramUsagePercent / 100f,
                    accentColor = RexyyCyanPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryCard(
                    title = "STORAGE",
                    value = "${telemetry.freeStorageGb} GB Free",
                    subtitle = "Total: ${telemetry.totalStorageGb} GB",
                    progress = if (telemetry.totalStorageGb > 0) ((telemetry.totalStorageGb - telemetry.freeStorageGb) / telemetry.totalStorageGb).toFloat() else 0.5f,
                    accentColor = Color(0xFF2979FF),
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    title = "NETWORK",
                    value = if (telemetry.isWifiConnected) "Wi-Fi Connected" else if (telemetry.isCellularConnected) "Mobile Data" else "Offline",
                    subtitle = if (telemetry.isWifiConnected) "Active LAN" else telemetry.cellularType,
                    progress = if (telemetry.isWifiConnected || telemetry.isCellularConnected) 1f else 0f,
                    accentColor = if (telemetry.isWifiConnected) RexyyCyanPrimary else if (telemetry.isCellularConnected) RexyyNeonGreen else Color(0xFFFF8A80),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Hardware Controls
            Text(
                text = "HARDWARE SWITCHES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Volume Control Slider Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Media Volume",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = RexyyTextPrimary
                                )
                            )
                        }
                        Text(
                            text = "${(volumeSlider * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RexyyCyanLight
                            )
                        )
                    }

                    Slider(
                        value = volumeSlider,
                        onValueChange = { newVal ->
                            volumeSlider = newVal
                            val targetStream = (newVal * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetStream, 0)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = RexyyCyanPrimary,
                            activeTrackColor = RexyyCyanPrimary,
                            inactiveTrackColor = RexyyDarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Switches Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HardwareSwitchCard(
                    icon = Icons.Outlined.FlashlightOn,
                    label = "Flashlight",
                    status = if (isTorchOn) "ON" else "OFF",
                    isActive = isTorchOn,
                    onClick = { toggleTorch() },
                    modifier = Modifier.weight(1f)
                )

                HardwareSwitchCard(
                    icon = Icons.Outlined.Wifi,
                    label = "Wi-Fi Settings",
                    status = if (telemetry.isWifiConnected) "Connected" else "Open",
                    isActive = telemetry.isWifiConnected,
                    onClick = {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )

                HardwareSwitchCard(
                    icon = Icons.Outlined.Bluetooth,
                    label = "Bluetooth",
                    status = if (telemetry.isBluetoothEnabled) "Active" else "Open",
                    isActive = telemetry.isBluetoothEnabled,
                    onClick = {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: App Shortcuts
            Text(
                text = "FAST LAUNCH MATRIX",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppShortcutCard(
                    icon = Icons.Outlined.PlayArrow,
                    label = "YouTube",
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )

                AppShortcutCard(
                    icon = Icons.Outlined.CameraAlt,
                    label = "Camera",
                    onClick = {
                        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f)
                )

                AppShortcutCard(
                    icon = Icons.Outlined.Phone,
                    label = "Dialer",
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )

                AppShortcutCard(
                    icon = Icons.Outlined.Settings,
                    label = "System",
                    onClick = {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyTextMuted,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accentColor
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = accentColor,
                trackColor = RexyyDarkSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun HardwareSwitchCard(
    icon: ImageVector,
    label: String,
    status: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, if (isActive) RexyyCyanPrimary else RexyyDarkBorder),
        modifier = modifier
            .clickable { onClick() }
            .height(88.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) RexyyCyanPrimary else RexyyTextMuted,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = RexyyTextPrimary
                    ),
                    maxLines = 1
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isActive) RexyyCyanLight else RexyyTextMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AppShortcutCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = modifier
            .clickable { onClick() }
            .height(72.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RexyyCyanLight,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = RexyyTextPrimary,
                    fontSize = 11.sp
                )
            )
        }
    }
}
