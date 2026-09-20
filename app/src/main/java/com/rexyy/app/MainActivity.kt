package com.rexyy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rexyy.app.ui.RexyyMainScreen
import com.rexyy.app.ui.theme.RexyyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RexyyTheme {
                RexyyMainScreen()
            }
        }
    }
}
