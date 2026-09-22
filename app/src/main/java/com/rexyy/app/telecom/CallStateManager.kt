package com.rexyy.app.telecom

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
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
    private var telephonyCallback: Any? = null

    init {
        try {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            registerListener()
        } catch (_: Exception) {}
    }

    private fun registerListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerTelephonyCallbackApi31()
            } else {
                registerLegacyPhoneStateListener()
            }
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackApi31() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        _callStatus.value = CallStatus(
                            state = TelephonyState.RINGING,
                            callerName = "Incoming Call",
                            phoneNumber = null
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
        telephonyCallback = callback
        telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneStateListener() {
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
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun getCurrentRingingCallerAnnouncement(languageSetting: String = com.rexyy.app.data.local.SecureStorage.VOICE_LANG_DEFAULT): String {
        val current = _callStatus.value
        val appLang = com.rexyy.app.utils.RexyyLanguageManager.parseLanguage(languageSetting)
        return if (current.state == TelephonyState.RINGING) {
            val caller = when {
                !current.callerName.isNullOrBlank() -> current.callerName
                !current.phoneNumber.isNullOrBlank() -> current.phoneNumber
                else -> "Unknown Number"
            }
            com.rexyy.app.utils.RexyyLanguageManager.getIncomingCallAnnouncement(caller, appLang)
        } else {
            when (appLang) {
                com.rexyy.app.utils.AppLanguage.HINDI -> "कोई इनकमिंग कॉल नहीं है।"
                com.rexyy.app.utils.AppLanguage.ENGLISH -> "No active incoming call detected."
                com.rexyy.app.utils.AppLanguage.HINGLISH -> "Koi incoming call nahi hai."
            }
        }
    }

    fun isRinging(): Boolean = _callStatus.value.state == TelephonyState.RINGING

    fun release() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (_: Exception) {}
    }
}
