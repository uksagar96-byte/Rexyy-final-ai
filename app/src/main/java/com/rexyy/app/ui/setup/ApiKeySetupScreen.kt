package com.rexyy.app.ui.setup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.ui.theme.RexyyCyanPrimary
import com.rexyy.app.ui.theme.RexyyDarkBackground
import com.rexyy.app.ui.theme.RexyyDarkBorder
import com.rexyy.app.ui.theme.RexyyDarkSurface
import com.rexyy.app.ui.theme.RexyyDarkSurfaceVariant
import com.rexyy.app.ui.theme.RexyyNeonGreen
import com.rexyy.app.ui.theme.RexyyTextMuted
import com.rexyy.app.ui.theme.RexyyTextPrimary
import com.rexyy.app.ui.theme.RexyyTextSecondary
import com.rexyy.app.utils.SecurityUtils

@Composable
fun ApiKeySetupScreen(
    onApiKeySaved: (String) -> Unit,
    onProviderAndKeySaved: (String, AiProviderType) -> Unit = { key, _ -> onApiKeySaved(key) },
    modifier: Modifier = Modifier
) {
    var selectedProvider by remember { mutableStateOf(AiProviderType.OPENAI) }
    var apiKeyText by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = RexyyDarkBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("setup_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Glowing REXYY Brand Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(RexyyDarkSurface)
                    .border(2.dp, RexyyCyanPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = "REXYY Security Key",
                    tint = RexyyCyanPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Welcome to REXYY",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = RexyyCyanPrimary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose your AI provider and enter your API key to activate REXYY.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = RexyyTextSecondary,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Provider selection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SetupProviderCard(
                    title = "OpenAI",
                    subtitle = "GPT-4o & Mini",
                    isSelected = selectedProvider == AiProviderType.OPENAI,
                    onClick = {
                        selectedProvider = AiProviderType.OPENAI
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                )
                SetupProviderCard(
                    title = "Google Gemini",
                    subtitle = "Gemini 3.5 & 2.5",
                    isSelected = selectedProvider == AiProviderType.GEMINI,
                    onClick = {
                        selectedProvider = AiProviderType.GEMINI
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Security assurance card
            Card(
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = "Secure Local Storage",
                        tint = RexyyNeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Encrypted in Android KeyStore (AES-256 GCM) on this device. Keys are never logged or transmitted elsewhere.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RexyyTextSecondary,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // API Key Input Field
            val keyPlaceholder = if (selectedProvider == AiProviderType.OPENAI) "sk-..." else "AIzaSy..."
            val keyLabel = if (selectedProvider == AiProviderType.OPENAI) "OpenAI API Key" else "Google Gemini API Key"

            OutlinedTextField(
                value = apiKeyText,
                onValueChange = {
                    apiKeyText = it
                    if (errorMessage != null) errorMessage = null
                },
                label = { Text(keyLabel) },
                placeholder = { Text(keyPlaceholder) },
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock icon",
                        tint = RexyyCyanPrimary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isKeyVisible = !isKeyVisible },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (isKeyVisible) "Hide API key" else "Show API key",
                            tint = RexyyTextMuted
                        )
                    }
                },
                isError = errorMessage != null,
                supportingText = {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (SecurityUtils.isValidApiKey(apiKeyText)) {
                            onProviderAndKeySaved(apiKeyText.trim(), selectedProvider)
                        } else {
                            errorMessage = "Please enter a valid API key (at least 10 characters)."
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RexyyDarkSurfaceVariant,
                    unfocusedContainerColor = RexyyDarkSurfaceVariant,
                    focusedBorderColor = RexyyCyanPrimary,
                    unfocusedBorderColor = RexyyDarkBorder,
                    focusedTextColor = RexyyTextPrimary,
                    unfocusedTextColor = RexyyTextPrimary,
                    focusedLabelColor = RexyyCyanPrimary,
                    unfocusedLabelColor = RexyyTextSecondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (SecurityUtils.isValidApiKey(apiKeyText)) {
                        onProviderAndKeySaved(apiKeyText.trim(), selectedProvider)
                    } else {
                        errorMessage = "Please enter a valid API key (at least 10 characters)."
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RexyyCyanPrimary,
                    contentColor = RexyyDarkBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_api_key_button")
            ) {
                Text(
                    text = "Save Key & Launch REXYY",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SetupProviderCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) RexyyDarkSurfaceVariant else RexyyDarkSurface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) RexyyCyanPrimary else RexyyDarkBorder
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) RexyyCyanPrimary else RexyyTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = RexyyTextMuted)
            )
        }
    }
}
