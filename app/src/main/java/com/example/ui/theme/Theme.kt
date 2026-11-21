package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalLyraThemeColors = staticCompositionLocalOf { LyraCyberColors }
val LocalInsideGlassSurface = staticCompositionLocalOf { false }

object LyraTheme {
    val colors: LyraThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLyraThemeColors.current
}

@Composable
fun MyApplicationTheme(
    appThemeStyle: AppThemeStyle = AppThemeStyle.LYRA_CYBER,
    content: @Composable () -> Unit
) {
    val themeColors = themeColorsFor(appThemeStyle)
    val colorScheme = if (themeColors.isLight) {
        lightColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimaryButton,
            primaryContainer = themeColors.primaryMuted,
            onPrimaryContainer = themeColors.textPrimary,
            secondary = themeColors.secondary,
            onSecondary = themeColors.onSecondaryButton,
            secondaryContainer = themeColors.surfaceElevated,
            onSecondaryContainer = themeColors.textPrimary,
            tertiary = themeColors.acceptColor,
            onTertiary = themeColors.onPrimaryButton,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceElevated,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.glassBorder,
            error = themeColors.declineColor,
            onError = themeColors.onPrimaryButton
        )
    } else {
        darkColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimaryButton,
            primaryContainer = themeColors.primaryMuted,
            onPrimaryContainer = themeColors.primaryGlow,
            secondary = themeColors.secondary,
            onSecondary = themeColors.onSecondaryButton,
            secondaryContainer = themeColors.surfaceElevated,
            onSecondaryContainer = themeColors.secondaryGlow,
            tertiary = themeColors.acceptColor,
            onTertiary = themeColors.onPrimaryButton,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceElevated,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.glassBorder,
            error = themeColors.declineColor,
            onError = themeColors.textPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = themeColors.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = themeColors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = themeColors.isLight
                controller.isAppearanceLightNavigationBars = themeColors.isLight
            }
        }
    }

    CompositionLocalProvider(LocalLyraThemeColors provides themeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
