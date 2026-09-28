package com.rexyy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rexyy.app.ui.RexyyMainScreen
import com.rexyy.app.ui.bridge.RexyyCoreControllerImpl
import com.rexyy.app.ui.java.RexyyUiCoordinator
import com.rexyy.app.ui.theme.RexyyTheme

class MainActivity : ComponentActivity() {

    private lateinit var uiCoordinator: RexyyUiCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val coreController = RexyyCoreControllerImpl.getInstance(applicationContext)
        uiCoordinator = RexyyUiCoordinator(coreController)
        uiCoordinator.onActivityCreate(this)

        setContent {
            RexyyTheme {
                RexyyMainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        uiCoordinator.onActivityStart(this)
    }

    override fun onResume() {
        super.onResume()
        uiCoordinator.onActivityResume(this)
    }

    override fun onStop() {
        super.onStop()
        uiCoordinator.onActivityStop(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiCoordinator.onActivityDestroy()
    }
}
