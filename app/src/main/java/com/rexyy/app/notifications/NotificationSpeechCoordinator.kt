package com.rexyy.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

object NotificationSpeechCoordinator {

    private val _isSpeakingNotification = MutableStateFlow(false)
    val isSpeakingNotification: StateFlow<Boolean> = _isSpeakingNotification.asStateFlow()

    private val speechListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun registerListener(listener: (Boolean) -> Unit) {
        if (!speechListeners.contains(listener)) {
            speechListeners.add(listener)
        }
    }

    fun unregisterListener(listener: (Boolean) -> Unit) {
        speechListeners.remove(listener)
    }

    fun onNotificationSpeechStarted() {
        _isSpeakingNotification.value = true
        for (listener in speechListeners) {
            try {
                listener(true)
            } catch (_: Exception) {}
        }
    }

    fun onNotificationSpeechEnded() {
        _isSpeakingNotification.value = false
        for (listener in speechListeners) {
            try {
                listener(false)
            } catch (_: Exception) {}
        }
    }
}
