package com.rexyy.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = RexyyCyanPrimary,
    onPrimary = RexyyDarkBackground,
    secondary = RexyyPurpleSecondary,
    onSecondary = RexyyDarkBackground,
    background = RexyyDarkBackground,
    onBackground = RexyyTextPrimary,
    surface = RexyyDarkSurface,
    onSurface = RexyyTextPrimary,
    surfaceVariant = RexyyDarkSurfaceVariant,
    onSurfaceVariant = RexyyTextSecondary,
    outline = RexyyDarkBorder,
    error = RexyyErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = RexyyCyanDim,
    onPrimary = RexyyDarkBackground,
    secondary = RexyyPurpleSecondary,
    onSecondary = RexyyDarkBackground,
    background = RexyyDarkBackground,
    onBackground = RexyyTextPrimary,
    surface = RexyyDarkSurface,
    onSurface = RexyyTextPrimary,
    surfaceVariant = RexyyDarkSurfaceVariant,
    onSurfaceVariant = RexyyTextSecondary,
    outline = RexyyDarkBorder,
    error = RexyyErrorRed
)

@Composable
fun RexyyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
