package com.rexyy.app.pill

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rexyy.app.pill.ui.RexyyDynamicPill

object DynamicPillOverlayManager {

    private const val TAG = "DynamicPillOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var isAttached = false

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    fun isOverlayAttached(): Boolean = isAttached

    fun canDrawOverlay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Attaches the dynamic pill overlay window.
     * Guarantees at most one single active instance.
     */
    fun showOverlay(context: Context) {
        mainHandler.post {
            if (isAttached || !canDrawOverlay(context)) return@post

            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val density = context.resources.displayMetrics.density
                val topMargin = (36 * density).toInt()

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = topMargin
                }

                val owner = OverlayLifecycleOwner()
                overlayLifecycleOwner = owner

                val view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setContent {
                        RexyyDynamicPill()
                    }
                }

                overlayComposeView = view
                wm.addView(view, params)
                isAttached = true
                Log.i(TAG, "Dynamic Pill overlay attached successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach Dynamic Pill overlay: ${e.message}")
                isAttached = false
            }
        }
    }

    /**
     * Removes the dynamic pill overlay window safely.
     */
    fun hideOverlay() {
        mainHandler.post {
            if (!isAttached) return@post

            try {
                overlayComposeView?.let { view ->
                    windowManager?.removeViewImmediate(view)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing Dynamic Pill overlay: ${e.message}")
            } finally {
                overlayComposeView = null
                overlayLifecycleOwner?.destroy()
                overlayLifecycleOwner = null
                isAttached = false
                Log.i(TAG, "Dynamic Pill overlay detached.")
            }
        }
    }
}
