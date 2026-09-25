package com.rexyy.app.telecom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ContactsLookupState {
    STANDBY,
    FOUND,
    MULTIPLE,
    NOT_FOUND,
    PERMISSION_REQUIRED
}

enum class CallLifecycleState {
    IDLE,
    REQUESTED,
    STARTED,
    ACTIVE,
    ENDED,
    FAILED
}

enum class SmsLifecycleState {
    IDLE,
    PREPARING,
    SENDING,
    SENT,
    FAILED
}

enum class WhatsAppWorkflowState {
    IDLE,
    OPENING,
    CHAT_FOUND,
    TEXT_ENTERED,
    SENT,
    VERIFIED,
    FAILED
}

enum class NotificationAccessStatus {
    ACCESS_ACTIVE,
    ACCESS_INACTIVE,
    BLOCKED_UNAVAILABLE
}

enum class ChargingState {
    DISCONNECTED,
    CHARGING
}

object Phase7DiagnosticManager {

    // --- Contacts ---
    private val _contactsState = MutableStateFlow(ContactsLookupState.STANDBY)
    val contactsState: StateFlow<ContactsLookupState> = _contactsState.asStateFlow()

    private val _lastContactQuery = MutableStateFlow<String?>(null)
    val lastContactQuery: StateFlow<String?> = _lastContactQuery.asStateFlow()

    private val _lastContactMatchCount = MutableStateFlow(0)
    val lastContactMatchCount: StateFlow<Int> = _lastContactMatchCount.asStateFlow()

    fun updateContactsState(state: ContactsLookupState, query: String?, matchCount: Int = 0) {
        _contactsState.value = state
        _lastContactQuery.value = query
        _lastContactMatchCount.value = matchCount
    }

    // --- Calls ---
    private val _callState = MutableStateFlow(CallLifecycleState.IDLE)
    val callState: StateFlow<CallLifecycleState> = _callState.asStateFlow()

    private val _lastCallTarget = MutableStateFlow<String?>(null)
    val lastCallTarget: StateFlow<String?> = _lastCallTarget.asStateFlow()

    private val _lastCallError = MutableStateFlow<String?>(null)
    val lastCallError: StateFlow<String?> = _lastCallError.asStateFlow()

    fun updateCallState(state: CallLifecycleState, target: String? = null, error: String? = null) {
        _callState.value = state
        if (target != null) _lastCallTarget.value = sanitizeTarget(target)
        _lastCallError.value = error
    }

    // --- SMS ---
    private val _smsState = MutableStateFlow(SmsLifecycleState.IDLE)
    val smsState: StateFlow<SmsLifecycleState> = _smsState.asStateFlow()

    private val _lastSmsRecipient = MutableStateFlow<String?>(null)
    val lastSmsRecipient: StateFlow<String?> = _lastSmsRecipient.asStateFlow()

    private val _lastSmsLength = MutableStateFlow(0)
    val lastSmsLength: StateFlow<Int> = _lastSmsLength.asStateFlow()

    private val _lastSmsError = MutableStateFlow<String?>(null)
    val lastSmsError: StateFlow<String?> = _lastSmsError.asStateFlow()

    fun updateSmsState(state: SmsLifecycleState, recipient: String? = null, messageLength: Int = 0, error: String? = null) {
        _smsState.value = state
        if (recipient != null) _lastSmsRecipient.value = sanitizeTarget(recipient)
        _lastSmsLength.value = messageLength
        _lastSmsError.value = error
    }

    // --- WhatsApp ---
    private val _whatsAppState = MutableStateFlow(WhatsAppWorkflowState.IDLE)
    val whatsAppState: StateFlow<WhatsAppWorkflowState> = _whatsAppState.asStateFlow()

    private val _lastWhatsAppTarget = MutableStateFlow<String?>(null)
    val lastWhatsAppTarget: StateFlow<String?> = _lastWhatsAppTarget.asStateFlow()

    private val _lastWhatsAppError = MutableStateFlow<String?>(null)
    val lastWhatsAppError: StateFlow<String?> = _lastWhatsAppError.asStateFlow()

    fun updateWhatsAppState(state: WhatsAppWorkflowState, target: String? = null, error: String? = null) {
        _whatsAppState.value = state
        if (target != null) _lastWhatsAppTarget.value = sanitizeTarget(target)
        _lastWhatsAppError.value = error
    }

    // --- Notifications ---
    private val _notificationAccessStatus = MutableStateFlow(NotificationAccessStatus.ACCESS_INACTIVE)
    val notificationAccessStatus: StateFlow<NotificationAccessStatus> = _notificationAccessStatus.asStateFlow()

    private val _lastNotificationSource = MutableStateFlow<String?>(null)
    val lastNotificationSource: StateFlow<String?> = _lastNotificationSource.asStateFlow()

    private val _lastNotificationEvent = MutableStateFlow<String?>(null)
    val lastNotificationEvent: StateFlow<String?> = _lastNotificationEvent.asStateFlow()

    private val _lastNotificationError = MutableStateFlow<String?>(null)
    val lastNotificationError: StateFlow<String?> = _lastNotificationError.asStateFlow()

    private val _notificationReconnectAttempts = MutableStateFlow(0)
    val notificationReconnectAttempts: StateFlow<Int> = _notificationReconnectAttempts.asStateFlow()

    private val _lastAnnouncementOutcome = MutableStateFlow<String?>("Standby")
    val lastAnnouncementOutcome: StateFlow<String?> = _lastAnnouncementOutcome.asStateFlow()

    fun updateNotificationState(
        status: NotificationAccessStatus,
        source: String? = null,
        event: String? = null,
        error: String? = null,
        reconnectAttempts: Int? = null,
        outcome: String? = null
    ) {
        _notificationAccessStatus.value = status
        if (source != null) _lastNotificationSource.value = source
        if (event != null) _lastNotificationEvent.value = event
        if (error != null) _lastNotificationError.value = error
        if (reconnectAttempts != null) _notificationReconnectAttempts.value = reconnectAttempts
        if (outcome != null) _lastAnnouncementOutcome.value = outcome
    }

    // --- Charging ---
    private val _chargingState = MutableStateFlow(ChargingState.DISCONNECTED)
    val chargingState: StateFlow<ChargingState> = _chargingState.asStateFlow()

    private val _batteryPercentage = MutableStateFlow(100)
    val batteryPercentage: StateFlow<Int> = _batteryPercentage.asStateFlow()

    private val _lastChargingEvent = MutableStateFlow<String?>(null)
    val lastChargingEvent: StateFlow<String?> = _lastChargingEvent.asStateFlow()

    fun updateChargingState(state: ChargingState, pct: Int = -1, event: String? = null) {
        _chargingState.value = state
        if (pct >= 0) _batteryPercentage.value = pct
        if (event != null) _lastChargingEvent.value = event
    }

    /**
     * Sanitizes sensitive targets/numbers for diagnostics logging (masks phone numbers).
     */
    private fun sanitizeTarget(input: String): String {
        val trimmed = input.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (digitsOnly.length >= 7) {
            val visible = digitsOnly.takeLast(4)
            "***-$visible"
        } else {
            trimmed
        }
    }

    fun resetForTesting() {
        _contactsState.value = ContactsLookupState.STANDBY
        _lastContactQuery.value = null
        _lastContactMatchCount.value = 0

        _callState.value = CallLifecycleState.IDLE
        _lastCallTarget.value = null
        _lastCallError.value = null

        _smsState.value = SmsLifecycleState.IDLE
        _lastSmsRecipient.value = null
        _lastSmsLength.value = 0
        _lastSmsError.value = null

        _whatsAppState.value = WhatsAppWorkflowState.IDLE
        _lastWhatsAppTarget.value = null
        _lastWhatsAppError.value = null

        _notificationAccessStatus.value = NotificationAccessStatus.ACCESS_INACTIVE
        _lastNotificationSource.value = null
        _lastNotificationEvent.value = null
        _lastNotificationError.value = null
        _notificationReconnectAttempts.value = 0
        _lastAnnouncementOutcome.value = "Standby"

        _chargingState.value = ChargingState.DISCONNECTED
        _batteryPercentage.value = 100
        _lastChargingEvent.value = null
    }
}
