package com.rexyy.app.pill

sealed class RexyyPillState {
    abstract val title: String
    abstract val subtitle: String
    open val isExpanded: Boolean = false
    open val isError: Boolean = false
    open val isSuccess: Boolean = false

    // Core Assistant Lifecycle States
    data object BackgroundActive : RexyyPillState() {
        override val title = "REXXY Active"
        override val subtitle = "Background assistant armed"
    }

    data object WakeStandby : RexyyPillState() {
        override val title = "REXXY Standby"
        override val subtitle = "Listening for \"Hello Rex\""
    }

    data class WakeDetected(val phrase: String = "Hello Rex") : RexyyPillState() {
        override val title = "Wake Detected"
        override val subtitle = "Yes, I'm listening..."
        override val isExpanded = true
    }

    data object CommandListening : RexyyPillState() {
        override val title = "Listening..."
        override val subtitle = "Speak your command"
        override val isExpanded = true
    }

    data class CommandRecognized(val commandText: String) : RexyyPillState() {
        override val title = "Command Recognized"
        override val subtitle = "\"$commandText\""
        override val isExpanded = true
    }

    data class Processing(val commandText: String = "") : RexyyPillState() {
        override val title = "Processing..."
        override val subtitle = if (commandText.isNotBlank()) "\"$commandText\"" else "Analyzing command"
        override val isExpanded = true
    }

    data class Executing(val actionText: String) : RexyyPillState() {
        override val title = "Executing..."
        override val subtitle = actionText
        override val isExpanded = true
    }

    data class Verifying(val verifyText: String = "Verifying...") : RexyyPillState() {
        override val title = "Verifying..."
        override val subtitle = verifyText
        override val isExpanded = true
    }

    data class Success(val message: String) : RexyyPillState() {
        override val title = "Done"
        override val subtitle = message
        override val isExpanded = true
        override val isSuccess = true
    }

    data class Error(val errorMessage: String) : RexyyPillState() {
        override val title = "Error"
        override val subtitle = errorMessage
        override val isExpanded = true
        override val isError = true
    }

    // Contextual Real-Time States
    data class WhatsAppMessage(val target: String, val statusText: String) : RexyyPillState() {
        override val title = "WhatsApp"
        override val subtitle = if (target.isNotBlank()) "$target: $statusText" else statusText
        override val isExpanded = true
    }

    data class Call(val target: String, val statusText: String) : RexyyPillState() {
        override val title = "Phone Call"
        override val subtitle = if (target.isNotBlank()) "$statusText $target" else statusText
        override val isExpanded = true
    }

    data class Sms(val recipient: String, val statusText: String) : RexyyPillState() {
        override val title = "SMS"
        override val subtitle = if (recipient.isNotBlank()) "$statusText to $recipient" else statusText
        override val isExpanded = true
    }

    data class Notification(val appName: String, val summary: String) : RexyyPillState() {
        override val title = "Notification"
        override val subtitle = if (summary.isNotBlank()) "$appName: $summary" else "New notification from $appName"
        override val isExpanded = true
    }

    data class Charging(val isCharging: Boolean, val batteryPct: Int) : RexyyPillState() {
        override val title = "Charging"
        override val subtitle = if (isCharging) "Charging started ($batteryPct%)" else "Charging disconnected ($batteryPct%)"
        override val isExpanded = true
    }

    data class Network(val isConnected: Boolean) : RexyyPillState() {
        override val title = "Network"
        override val subtitle = if (isConnected) "Connected" else "Network disconnected"
        override val isExpanded = true
        override val isError = !isConnected
    }

    data object MicrophoneDisabled : RexyyPillState() {
        override val title = "Mic Disabled"
        override val subtitle = "Microphone access is disabled"
        override val isExpanded = true
        override val isError = true
    }

    data object SystemForeground : RexyyPillState() {
        override val title = "REXXY"
        override val subtitle = "Foreground active"
    }
}
