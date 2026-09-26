package com.rexyy.app.pill.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.pill.DynamicPillManager
import com.rexyy.app.pill.RexyyPillState
import com.rexyy.app.ui.theme.RexyyCyanLight
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyNeonGreen
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary

@Composable
fun RexyyDynamicPill(
    modifier: Modifier = Modifier,
    onPillClick: (() -> Unit)? = null
) {
    val pillState by DynamicPillManager.pillState.collectAsState()
    val isExpanded by DynamicPillManager.isExpanded.collectAsState()

    val accentColor by animateColorAsState(
        targetValue = getAccentColorForState(pillState),
        animationSpec = tween(300),
        label = "pill_accent_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pill_energy")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pill_pulse"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xEB0A0F1D), // Dark glass translucent
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .testTag("dynamic_pill")
            .semantics { contentDescription = "REXXY Dynamic Pill: ${pillState.title}, ${pillState.subtitle}" }
            .animateContentSize(animationSpec = tween(350))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (onPillClick != null) {
                    onPillClick()
                } else {
                    DynamicPillManager.toggleExpanded()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            Color(0x00000000),
                            accentColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                ExpandedPillContent(
                    state = pillState,
                    accentColor = accentColor,
                    pulseScale = pulseScale
                )
            } else {
                CollapsedPillContent(
                    state = pillState,
                    accentColor = accentColor,
                    pulseScale = pulseScale
                )
            }
        }
    }
}

@Composable
private fun CollapsedPillContent(
    state: RexyyPillState,
    accentColor: Color,
    pulseScale: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.heightIn(min = 28.dp)
    ) {
        // Glowing status dot
        Box(
            modifier = Modifier
                .size(9.dp)
                .scale(if (state is RexyyPillState.WakeStandby || state is RexyyPillState.CommandListening) pulseScale else 1f)
                .clip(CircleShape)
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "REXXY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontSize = 11.sp,
                color = RexyyCyanLight
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "•",
            color = RexyyTextMuted,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = state.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = RexyyTextSecondary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpandedPillContent(
    state: RexyyPillState,
    accentColor: Color,
    pulseScale: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .padding(vertical = 2.dp)
    ) {
        // State Icon Container
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            val icon = getIconForState(state)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(18.dp)
                    .scale(if (state is RexyyPillState.CommandListening || state is RexyyPillState.Executing) pulseScale else 1f)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // State Title & Subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        fontSize = 10.sp,
                        color = accentColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = RexyyTextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // REXXY logo mini badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0x33101B2E)
        ) {
            Text(
                text = "REXXY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = RexyyCyanLight
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun getAccentColorForState(state: RexyyPillState): Color {
    return when {
        state.isError -> Color(0xFFFF5252) // Coral Red
        state.isSuccess -> RexyyNeonGreen // Neon Green
        state is RexyyPillState.WakeDetected -> Color(0xFF00E5FF)
        state is RexyyPillState.CommandListening -> Color(0xFF00E5FF)
        state is RexyyPillState.CommandRecognized -> Color(0xFF80D8FF)
        state is RexyyPillState.Processing -> Color(0xFF82B1FF)
        state is RexyyPillState.Executing -> Color(0xFF40C4FF)
        state is RexyyPillState.Verifying -> Color(0xFFFFD740) // Amber Yellow
        state is RexyyPillState.Charging -> Color(0xFF69F0AE)
        state is RexyyPillState.Notification -> Color(0xFFFFAB40) // Soft Orange
        state is RexyyPillState.WhatsAppMessage -> Color(0xFF25D366) // WhatsApp Green
        state is RexyyPillState.Call -> Color(0xFF00E676)
        state is RexyyPillState.Sms -> Color(0xFF448AFF)
        state is RexyyPillState.MicrophoneDisabled -> Color(0xFFFF5252)
        else -> RexyyCyanPrimary
    }
}

private fun getIconForState(state: RexyyPillState): ImageVector {
    return when (state) {
        is RexyyPillState.BackgroundActive -> Icons.Filled.PlayArrow
        is RexyyPillState.WakeStandby -> Icons.Filled.Mic
        is RexyyPillState.WakeDetected -> Icons.Filled.Mic
        is RexyyPillState.CommandListening -> Icons.Filled.Mic
        is RexyyPillState.CommandRecognized -> Icons.Filled.PlayArrow
        is RexyyPillState.Processing -> Icons.Filled.Refresh
        is RexyyPillState.Executing -> Icons.Filled.PlayArrow
        is RexyyPillState.Verifying -> Icons.Filled.Refresh
        is RexyyPillState.Success -> Icons.Filled.CheckCircle
        is RexyyPillState.Error -> Icons.Filled.Error
        is RexyyPillState.WhatsAppMessage -> Icons.Filled.Sms
        is RexyyPillState.Call -> Icons.Filled.Call
        is RexyyPillState.Sms -> Icons.Filled.Sms
        is RexyyPillState.Notification -> Icons.Filled.Notifications
        is RexyyPillState.Charging -> Icons.Filled.BatteryChargingFull
        is RexyyPillState.Network -> if (state.isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff
        is RexyyPillState.MicrophoneDisabled -> Icons.Filled.MicOff
        is RexyyPillState.SystemForeground -> Icons.Filled.PlayArrow
    }
}
