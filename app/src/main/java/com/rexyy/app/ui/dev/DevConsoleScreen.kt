package com.rexyy.app.ui.dev

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rexyy.app.device.DeviceTelemetryManager
import com.rexyy.app.router.RexyyCommandRouter
import com.rexyy.app.service.RexyyAssistantServiceState
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
import com.rexyy.app.voice.VoiceCommand

data class RouteTrace(
    val timestamp: String,
    val inputPhrase: String,
    val resolvedCommand: String,
    val routeClass: String
)

@Composable
fun DevConsoleScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryManager = remember { DeviceTelemetryManager(context) }
    val telemetry by telemetryManager.telemetry.collectAsState()

    val bgState by RexyyAssistantServiceState.currentState.collectAsState()
    val isBgRunning by RexyyAssistantServiceState.serviceRunning.collectAsState()
    val lastBgCmd by RexyyAssistantServiceState.lastRecognizedCommand.collectAsState()
    val lastBgFeedback by RexyyAssistantServiceState.lastExecutionFeedback.collectAsState()

    var testInput by remember { mutableStateOf("") }
    val traces = remember {
        mutableStateListOf(
            RouteTrace("INIT", "System Online", "VoiceCommandExecutor ready", "SystemBootstrap"),
            RouteTrace("TEST", "YouTube pe cricket search karo", "AppSearch(app=youtube, query=cricket)", "CommandRouter"),
            RouteTrace("TEST", "Ramzan ko WhatsApp pe bol kal milte hain", "WhatsAppMessage(target=Ramzan, body=kal milte hain)", "CommandRouter"),
            RouteTrace("TEST", "Battery kitni hai", "GetBattery()", "CommandRouter")
        )
    }

    fun runTestSimulation(query: String) {
        if (query.isBlank()) return
        val routed = RexyyCommandRouter.route(query)
        val routeName = routed::class.simpleName ?: "Unknown"
        val desc = when (routed) {
            is VoiceCommand.AppSearch -> "AppSearch(app=${routed.targetApp}, query='${routed.query}')"
            is VoiceCommand.WhatsAppMessage -> "WhatsAppMessage(target='${routed.target}', body='${routed.body}')"
            is VoiceCommand.CallContact -> "CallContact(target='${routed.target}')"
            is VoiceCommand.OpenApp -> "OpenApp(name='${routed.appName}')"
            is VoiceCommand.GetBattery -> "GetBattery()"
            is VoiceCommand.GetDate -> "GetDate()"
            is VoiceCommand.GetTime -> "GetTime()"
            is VoiceCommand.ToggleFlashlight -> "ToggleFlashlight(turnOn=${routed.turnOn})"
            is VoiceCommand.Stop -> "Stop()"
            is VoiceCommand.AiChat -> "ForwardToAi(prompt='${routed.prompt}')"
            else -> routed.toString()
        }

        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        traces.add(0, RouteTrace(timeStr, query, desc, routeName))
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("dev_console_back_button")
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
                        text = "REXYY DEV CONSOLE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = RexyyCyanPrimary
                        )
                    )
                    Text(
                        text = "ROUTER DIAGNOSTICS & STATE TRACE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RexyyTextMuted,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Background Service State Monitor Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = RexyyDarkSurface),
                border = BorderStroke(1.dp, RexyyDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isBgRunning) RexyyNeonGreen else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BACKGROUND SERVICE: ${if (isBgRunning) "ACTIVE" else "INACTIVE"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBgRunning) RexyyNeonGreen else Color(0xFFFF8A80)
                                )
                            )
                        }
                        Text(
                            text = "STATE: $bgState",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = RexyyCyanLight
                            )
                        )
                    }

                    if (lastBgCmd.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Last Voice Input: \"$lastBgCmd\"",
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyTextSecondary)
                        )
                    }
                    if (lastBgFeedback.isNotBlank()) {
                        Text(
                            text = "Feedback: $lastBgFeedback",
                            style = MaterialTheme.typography.bodySmall.copy(color = RexyyCyanLight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Simulation Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    placeholder = { Text("Simulate natural speech...", color = RexyyTextMuted, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RexyyCyanPrimary,
                        unfocusedBorderColor = RexyyDarkBorder,
                        focusedTextColor = RexyyTextPrimary,
                        unfocusedTextColor = RexyyTextPrimary,
                        cursorColor = RexyyCyanPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        runTestSimulation(testInput)
                        testInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RexyyCyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Test Route",
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fast Test Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TestPill("YouTube pe search") { runTestSimulation("YouTube pe cricket search karo") }
                TestPill("WhatsApp bol") { runTestSimulation("Ramzan ko WhatsApp pe bol hello") }
                TestPill("Battery kitni") { runTestSimulation("Battery kitni hai") }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Routing Logs
            Text(
                text = "INTENT ROUTER TRACE LOGS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RexyyCyanLight
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RexyyDarkSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(traces) { trace ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${trace.timestamp}] ${trace.routeClass}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RexyyCyanPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "IN: \"${trace.inputPhrase}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = RexyyTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                text = "OUT: ${trace.resolvedCommand}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = RexyyNeonGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestPill(
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RexyyDarkSurfaceVariant),
        border = BorderStroke(1.dp, RexyyDarkBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = RexyyCyanLight,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
