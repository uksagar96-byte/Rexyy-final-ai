package com.rexyy.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.theme.RexyyDarkBorder
import com.rexyy.app.ui.theme.RexyyDarkSurface
import com.rexyy.app.ui.theme.RexyyDarkSurfaceVariant
import com.rexyy.app.ui.theme.RexyyErrorRed
import com.rexyy.app.ui.theme.RexyyPurpleSecondary
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary
import com.rexyy.app.voice.VoiceState

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    voiceState: VoiceState = VoiceState.IDLE,
    onMicClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pulse animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        color = RexyyDarkSurface,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, RexyyDarkBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = when {
                            isLoading -> "REXYY is responding..."
                            voiceState == VoiceState.LISTENING -> "Listening... Speak now"
                            voiceState == VoiceState.PROCESSING -> "Processing voice..."
                            voiceState == VoiceState.SPEAKING -> "Speaking aloud..."
                            else -> "Ask REXYY or give a voice command..."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = RexyyTextMuted)
                    )
                },
                enabled = !isLoading && voiceState != VoiceState.LISTENING,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (inputText.isNotEmpty() && !isLoading) {
                        IconButton(
                            onClick = { onInputChange("") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear input",
                                tint = RexyyTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSendClick()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RexyyDarkSurfaceVariant,
                    unfocusedContainerColor = RexyyDarkSurfaceVariant,
                    disabledContainerColor = RexyyDarkSurfaceVariant.copy(alpha = 0.5f),
                    focusedBorderColor = RexyyCyanPrimary,
                    unfocusedBorderColor = RexyyDarkBorder,
                    disabledBorderColor = RexyyDarkBorder.copy(alpha = 0.5f),
                    focusedTextColor = RexyyTextPrimary,
                    unfocusedTextColor = RexyyTextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Microphone / Voice Control Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (voiceState == VoiceState.LISTENING) {
                            Modifier
                                .scale(pulseScale)
                                .background(RexyyErrorRed.copy(alpha = 0.2f))
                                .border(2.dp, RexyyErrorRed, CircleShape)
                        } else if (voiceState == VoiceState.SPEAKING) {
                            Modifier
                                .background(RexyyPurpleSecondary.copy(alpha = 0.25f))
                                .border(1.5.dp, RexyyPurpleSecondary, CircleShape)
                        } else {
                            Modifier.background(RexyyDarkSurfaceVariant)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_mic_button")
                ) {
                    when (voiceState) {
                        VoiceState.LISTENING -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listening - Tap to cancel",
                                tint = RexyyErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        VoiceState.PROCESSING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = RexyyCyanPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        VoiceState.SPEAKING -> {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Speaking - Tap to stop",
                                tint = RexyyPurpleSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        VoiceState.IDLE -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Start voice input",
                                tint = RexyyCyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            val canSend = inputText.isNotBlank() && !isLoading && voiceState != VoiceState.LISTENING

            // Send Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) RexyyCyanPrimary else RexyyDarkBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) RexyyDarkBackground else RexyyTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
