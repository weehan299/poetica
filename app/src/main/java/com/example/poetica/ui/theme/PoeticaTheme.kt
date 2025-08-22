package com.example.poetica.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PoetryDarkColorScheme = darkColorScheme(
    primary = WarmGold,
    secondary = SoftCream,
    tertiary = DeepBrown,
    background = RichBlack,
    surface = WarmGray,
    onPrimary = RichBlack,
    onSecondary = RichBlack,
    onTertiary = SoftCream,
    onBackground = SoftCream,
    onSurface = SoftCream
)

private val PoetryLightColorScheme = lightColorScheme(
    primary = DeepBrown,
    secondary = WarmGold,
    tertiary = SoftRust,
    background = SoftCream,
    surface = WarmWhite,
    onPrimary = SoftCream,
    onSecondary = RichBlack,
    onTertiary = SoftCream,
    onBackground = RichBlack,
    onSurface = RichBlack
)

@Composable
fun PoeticaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for consistent poetry aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        
        darkTheme -> PoetryDarkColorScheme
        else -> PoetryLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PoetryTypography,
        content = content
    )
}