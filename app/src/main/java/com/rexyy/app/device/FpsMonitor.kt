package com.rexyy.app.device

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FpsMonitor : Choreographer.FrameCallback {

    private val _currentFps = MutableStateFlow(60)
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

    private var frameCount = 0
    private var lastIntervalTime = 0L
    private var isTracking = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startTracking() {
        if (isTracking) return
        isTracking = true
        frameCount = 0
        lastIntervalTime = System.nanoTime()

        mainHandler.post {
            try {
                Choreographer.getInstance().postFrameCallback(this)
            } catch (_: Exception) {}
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isTracking) return

        frameCount++
        val elapsedNanos = frameTimeNanos - lastIntervalTime

        // Calculate FPS every 1000ms (1 second)
        if (elapsedNanos >= 1_000_000_000L) {
            val fps = ((frameCount * 1_000_000_000.0) / elapsedNanos).toInt()
            _currentFps.value = fps.coerceIn(1, 144)
            frameCount = 0
            lastIntervalTime = frameTimeNanos
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stopTracking() {
        isTracking = false
        mainHandler.post {
            try {
                Choreographer.getInstance().removeFrameCallback(this)
            } catch (_: Exception) {}
        }
    }
}
