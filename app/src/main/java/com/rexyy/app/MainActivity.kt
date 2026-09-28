package com.rexyy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rexyy.app.pill.DynamicPillOverlayManager
import com.rexyy.app.service.BackgroundAssistantManager
import com.rexyy.app.service.RexyyAssistantServiceState
import com.rexyy.app.ui.RexyyMainScreen
import com.rexyy.app.ui.theme.RexyyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-start assistant service if permissions are granted so wake engine is armed
        if (BackgroundAssistantManager.canStartService(this) &&
            !RexyyAssistantServiceState.serviceRunning.value
        ) {
            BackgroundAssistantManager.startAssistant(this)
        }

        setContent {
            RexyyTheme {
                RexyyMainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ensure background assistant service is active while app is in the FOREGROUND
        if (BackgroundAssistantManager.canStartService(this) &&
            !RexyyAssistantServiceState.serviceRunning.value
        ) {
            BackgroundAssistantManager.startAssistant(this)
        }
        // When REXXY app is in the foreground, hide system window overlay to prevent double pill
        DynamicPillOverlayManager.hideOverlay()
    }

    override fun onResume() {
        super.onResume()
        // Re-confirm service is armed while in foreground
        if (BackgroundAssistantManager.canStartService(this) &&
            !RexyyAssistantServiceState.serviceRunning.value
        ) {
            BackgroundAssistantManager.startAssistant(this)
        }
    }

    override fun onStop() {
        super.onStop()
        // When REXXY app is minimized or backgrounded, show overlay using applicationContext
        if (RexyyAssistantServiceState.serviceRunning.value && DynamicPillOverlayManager.canDrawOverlay(this)) {
            DynamicPillOverlayManager.showOverlay(applicationContext)
        }
    }
}
