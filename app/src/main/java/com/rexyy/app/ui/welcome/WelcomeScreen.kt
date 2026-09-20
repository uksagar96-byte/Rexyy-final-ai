package com.rexyy.app.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.ui.avatar.AssistantState
import com.rexyy.app.ui.avatar.RexyyAvatar3D
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
fun WelcomeScreen(
    onStartSetupWizard: () -> Unit,
    onSkipToLocalMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Branding Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(RexyyNeonGreen)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "REXYY",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = RexyyCyanPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NEXT-GENERATION PERSONAL AI ASSISTANT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = RexyyTextMuted
                    )
                )
            }

            // 3D Avatar Hero Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                RexyyAvatar3D(
                    state = AssistantState.IDLE,
                    size = 200.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Voice First. Autonomous. Private.",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = RexyyTextPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Control your device with voice, automate actions, and engage with high-speed intelligence without requiring an account or API key to start.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = RexyyTextSecondary,
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Feature Highlights
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                FeaturePill(
                    icon = Icons.Outlined.KeyboardVoice,
                    title = "Voice Commands & Natural Speech",
                    description = "Talk naturally in English or Hindi with responsive on-device speech recognition."
                )
                FeaturePill(
                    icon = Icons.Outlined.Speed,
                    title = "Works Immediately Offline",
                    description = "Open apps, set alarms, and adjust settings with full zero-key Local Test Mode."
                )
                FeaturePill(
                    icon = Icons.Outlined.Security,
                    title = "Dual AI Engine & KeyStore Security",
                    description = "Optionally connect OpenAI or Google Gemini keys with hardware AES-256 GCM encryption."
                )
            }

            // Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Button(
                    onClick = onStartSetupWizard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RexyyCyanPrimary,
                        contentColor = RexyyDarkBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_setup_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Launch Setup Wizard",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next"
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSkipToLocalMode,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RexyyCyanLight
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RexyyDarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("skip_to_local_mode_button")
                ) {
                    Text(
                        text = "Skip to Local Test Mode (No API Key)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RexyyDarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    tint = RexyyCyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = RexyyTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = RexyyTextSecondary,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}
