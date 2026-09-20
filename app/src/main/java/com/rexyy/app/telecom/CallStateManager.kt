package com.rexyy.app.telecom

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TelephonyState {
    IDLE,
    RINGING,
    ACTIVE,
    DISCONNECTED
}

data class CallStatus(
    val state: TelephonyState = TelephonyState.IDLE,
    val callerName: String? = null,
    val phoneNumber: String? = null
)

class CallStateManager(private val context: Context) {

    private val _callStatus = MutableStateFlow(CallStatus())
    val callStatus: StateFlow<CallStatus> = _callStatus.asStateFlow()

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    init {
        try {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            registerListener()
        } catch (_: Exception) {}
    }

    private fun registerListener() {
        try {
            @Suppress("DEPRECATION")
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> {
                            val resolvedName = CallerIdentityResolver.resolveCallerName(context, phoneNumber)
                            _callStatus.value = CallStatus(
                                state = TelephonyState.RINGING,
                                callerName = resolvedName,
                                phoneNumber = phoneNumber
                            )
                        }
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            _callStatus.value = _callStatus.value.copy(state = TelephonyState.ACTIVE)
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            _callStatus.value = CallStatus(state = TelephonyState.IDLE)
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (_: Exception) {}
    }

    fun getCurrentRingingCallerAnnouncement(): String {
        val current = _callStatus.value
        return if (current.state == TelephonyState.RINGING) {
            when {
                !current.callerName.isNullOrBlank() -> "Incoming call from ${current.callerName}."
                !current.phoneNumber.isNullOrBlank() -> "Incoming call from ${current.phoneNumber}."
                else -> "Incoming call from an unknown number."
            }
        } else {
            "No active incoming call detected."
        }
    }

    fun isRinging(): Boolean = _callStatus.value.state == TelephonyState.RINGING

    fun release() {
        try {
            @Suppress("DEPRECATION")
            phoneStateListener?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {}
    }
}
