package com.example.peliselprimazo.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CFilmTheme(
    primaryOverride: String? = null,
    secondaryOverride: String? = null,
    content: @Composable () -> Unit
) {
    val parseColor = { hex: String?, default: Color ->
        try {
            if (hex.isNullOrBlank()) default 
            else Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) { default }
    }

    val dynamicPrimary = parseColor(primaryOverride, RedPrimazo)
    val dynamicSecondary = parseColor(secondaryOverride, GoldPrimazo)

    val colorScheme = darkColorScheme(
        primary = dynamicPrimary,
        secondary = dynamicSecondary,
        background = BlackBackground,
        surface = DarkGraySurface,
        onPrimary = WhiteText,
        onSecondary = BlackBackground,
        onBackground = WhiteText,
        onSurface = WhiteText
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
