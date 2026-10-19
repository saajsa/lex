package com.legumlex.client.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Stock Material components (dialogs, sheets, switches, menus) read the M3
// scheme, so it is built from the same palette as the app's own tokens.
private fun LexColors.scheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary              = primary,
        onPrimary            = primaryForeground,
        primaryContainer     = secondary,
        onPrimaryContainer   = foreground,
        secondary            = secondaryForeground,
        onSecondary          = primaryForeground,
        secondaryContainer   = secondary,
        onSecondaryContainer = foreground,
        tertiary             = mutedForeground,
        background           = background,
        onBackground         = foreground,
        surface              = card,
        onSurface            = foreground,
        surfaceVariant       = muted,
        onSurfaceVariant     = mutedForeground,
        surfaceContainerLowest  = card,
        surfaceContainerLow     = card,
        surfaceContainer        = card,
        surfaceContainerHigh    = card,
        surfaceContainerHighest = muted,
        inverseSurface       = primary,
        inverseOnSurface     = primaryForeground,
        outline              = input,
        outlineVariant       = border,
        error                = danger.solid,
        onError              = primaryForeground,
        errorContainer       = danger.softBg,
        onErrorContainer     = danger.softText,
        scrim                = base.scrim,
    )
}

/** The Mylex look, light or dark as the phone is set. */
@Composable
fun LegumLexTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkLexColors else LightLexColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Bar colours come from enableEdgeToEdge(); only the icons are set here.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalLexColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme(),
            typography  = LegumTypography,
            shapes      = LegumShapes,
            content     = content,
        )
    }
}
