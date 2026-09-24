package com.rexyy.app.ui.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rexyy.app.ui.theme.RexyyCyanLight
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyErrorRed
import com.rexyy.app.ui.theme.RexyyNeonGreen
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity 3D-styled animated AI sphere representing REXYY's personality and cognitive states:
 * - IDLE: Gentle breathing pulse with holographic orbit ring
 * - LISTENING: Dynamic expanding soundwave ripples, reactive core
 * - THINKING: Dual orbiting particle gyroscopes spinning with quantum acceleration
 * - SPEAKING: Audio-reactive wave oscillations expanding outward
 * - WORKING: High-tech cyber scanning matrix
 * - SUCCESS: Radiant emerald burst with stabilized core
 * - ERROR: Cautious crimson pulsation with disrupted field
 */
@Composable
fun RexyyAvatar3D(
    state: AssistantState,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_anim")

    // Continuous rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING -> 2000
                    AssistantState.WORKING -> 2800
                    AssistantState.LISTENING -> 3500
                    AssistantState.SPEAKING -> 3200
                    AssistantState.ERROR -> 10000
                    else -> 7500
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Reverse rotation for depth
    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.THINKING -> 2800
                    AssistantState.WORKING -> 4000
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "reverse_rotation"
    )

    // Breathing pulse
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 750
                    AssistantState.SPEAKING -> 550
                    AssistantState.ERROR -> 450
                    else -> 1700
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Wave ripple for listening & speaking
    val rippleFraction by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) 1100 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // Color theme based on state
    val (primaryColor, secondaryColor, coreColor) = when (state) {
        AssistantState.IDLE -> Triple(RexyyCyanPrimary, RexyyCyanLight, Color(0xFF002936))
        AssistantState.LISTENING -> Triple(RexyyCyanLight, Color(0xFF80DEEA), Color(0xFF004D61))
        AssistantState.THINKING -> Triple(Color(0xFFB388FF), Color(0xFF7C4DFF), Color(0xFF261266))
        AssistantState.SPEAKING -> Triple(RexyyNeonGreen, Color(0xFFB9F6CA), Color(0xFF00382E))
        AssistantState.WORKING -> Triple(Color(0xFFFFB74D), Color(0xFFFF9800), Color(0xFFB23B00))
        AssistantState.SUCCESS -> Triple(RexyyNeonGreen, Color(0xFF69F0AE), Color(0xFF144D1A))
        AssistantState.ERROR -> Triple(RexyyErrorRed, Color(0xFFFF8A80), Color(0xFF8E1111))
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("rexyy_3d_avatar")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = (size.toPx() / 2f) * 0.54f

            // 0. Subtle Tech Calibration Ring & Compass Ticks
            val outerRingRadius = radius * 1.58f
            drawCircle(
                color = primaryColor.copy(alpha = 0.18f),
                radius = outerRingRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            // 8 Cardinal Precision Ticks
            for (i in 0 until 8) {
                val tickAngle = Math.toRadians((i * 45.0) + (rotation * 0.25f))
                val cosA = cos(tickAngle).toFloat()
                val sinA = sin(tickAngle).toFloat()
                val tickStart = Offset(center.x + (outerRingRadius - 4.dp.toPx()) * cosA, center.y + (outerRingRadius - 4.dp.toPx()) * sinA)
                val tickEnd = Offset(center.x + (outerRingRadius + 4.dp.toPx()) * cosA, center.y + (outerRingRadius + 4.dp.toPx()) * sinA)
                drawLine(
                    color = primaryColor.copy(alpha = 0.4f),
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 1. Outermost Ambient Glow & Waves (State Reactive)
            if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING || state == AssistantState.WORKING) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.35f * (1.5f - rippleFraction)),
                            primaryColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * rippleFraction * 1.5f
                    ),
                    radius = radius * rippleFraction * 1.5f,
                    center = center
                )
                // Outer ripple wave ring
                drawCircle(
                    color = primaryColor.copy(alpha = (1.4f - rippleFraction).coerceIn(0f, 0.6f)),
                    radius = radius * rippleFraction * 1.35f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 2. Diffuse Ambient Sphere Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.40f),
                        primaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.55f * pulse
                ),
                radius = radius * 1.48f * pulse,
                center = center
            )

            // 3. Orbiting Gyro Ring A (Horizontal Ellipse)
            rotate(degrees = rotation, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.95f),
                            secondaryColor.copy(alpha = 0.12f),
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.18f),
                            primaryColor.copy(alpha = 0.95f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - radius * 1.30f, center.y - radius * 0.48f),
                    size = androidx.compose.ui.geometry.Size(radius * 2.6f, radius * 0.96f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Leading Quantum particle node
                val dotAngle = Math.toRadians(rotation.toDouble())
                val dotX = center.x + (radius * 1.30f) * cos(dotAngle).toFloat()
                val dotY = center.y + (radius * 0.48f) * sin(dotAngle).toFloat()
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.5f),
                    radius = 8.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }

            // 4. Secondary Counter-Rotating Gyro Ring B (Vertical Ellipse)
            rotate(degrees = reverseRotation, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.75f),
                            Color.Transparent,
                            secondaryColor.copy(alpha = 0.85f),
                            Color.Transparent,
                            secondaryColor.copy(alpha = 0.75f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - radius * 0.52f, center.y - radius * 1.30f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 2.6f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Secondary particle
                val revDotAngle = Math.toRadians((reverseRotation * 1.2).toDouble())
                val rDotX = center.x + (radius * 0.52f) * cos(revDotAngle).toFloat()
                val rDotY = center.y + (radius * 1.30f) * sin(revDotAngle).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 3.5.dp.toPx(),
                    center = Offset(rDotX, rDotY)
                )
            }

            // 4b. Subtle Quantum Particle Cloud (8 particles drifting in synchronized orbit)
            for (p in 0 until 6) {
                val pAngle = Math.toRadians(((rotation * (0.8 + p * 0.15)) + (p * 60.0)))
                val pDist = radius * (1.18f + 0.15f * sin((rotation * 0.05f + p).toDouble()).toFloat())
                val px = center.x + pDist * cos(pAngle).toFloat()
                val py = center.y + pDist * 0.82f * sin(pAngle).toFloat()
                drawCircle(
                    color = if (p % 2 == 0) primaryColor.copy(alpha = 0.7f) else secondaryColor.copy(alpha = 0.6f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }

            // 5. Deep 3D Volumetric Core Sphere
            val corePulseRadius = radius * (0.86f * pulse)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.7f),
                        coreColor.copy(alpha = 0.95f),
                        Color(0xFF030712)
                    ),
                    center = Offset(center.x - corePulseRadius * 0.35f, center.y - corePulseRadius * 0.35f),
                    radius = corePulseRadius * 1.2f
                ),
                radius = corePulseRadius,
                center = center
            )

            // 6. Holographic Grid Latitude / Longitude lines
            val numRings = 3
            for (i in 1..numRings) {
                val stepRadius = corePulseRadius * (i.toFloat() / (numRings + 1))
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = stepRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 7. Dynamic Core Iris & Highlight Glint
            val glintCenter = Offset(center.x - corePulseRadius * 0.38f, center.y - corePulseRadius * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryColor.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = glintCenter,
                    radius = corePulseRadius * 0.35f
                ),
                radius = corePulseRadius * 0.35f,
                center = glintCenter
            )

            // 8. Sound wave bars inside core for SPEAKING / LISTENING
            if (state == AssistantState.SPEAKING || state == AssistantState.LISTENING) {
                val barCount = 5
                val barWidth = 3.dp.toPx()
                val totalWidth = barCount * 8.dp.toPx()
                val startX = center.x - (totalWidth / 2f)

                for (b in 0 until barCount) {
                    val barOffset = b * 8.dp.toPx()
                    val waveAmp = if (state == AssistantState.SPEAKING) {
                        sin((rotation * 0.1f + b).toDouble()).toFloat() * 14.dp.toPx()
                    } else {
                        cos((rotation * 0.08f + b).toDouble()).toFloat() * 10.dp.toPx()
                    }
                    val barHeight = (12.dp.toPx() + waveAmp).coerceAtLeast(4.dp.toPx())

                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(startX + barOffset, center.y - barHeight / 2f),
                        end = Offset(startX + barOffset, center.y + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
