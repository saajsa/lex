package com.legumlex.client.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light-only this pass. Dark scheme is a v1.1 deliverable per handoff.
private val LightColors = lightColorScheme(
    primary            = AccentInk,
    onPrimary          = InverseText,
    secondary          = SecondaryText,
    onSecondary        = InverseText,
    tertiary           = TertiaryText,
    background         = BodyBg,
    onBackground       = PrimaryText,
    surface            = Surface1,
    onSurface          = PrimaryText,
    surfaceVariant     = Surface2,
    onSurfaceVariant   = SecondaryText,
    outline            = BorderColor,
    outlineVariant     = BorderSubtle,
    error              = StatusDanger.solid,
    onError            = InverseText,
    errorContainer     = StatusDanger.softBg,
    onErrorContainer   = StatusDanger.softText,
)

@Composable
fun LegumLexTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Bar colors are handled by enableEdgeToEdge(); here we only set
            // icon appearance. (statusBarColor/navigationBarColor are
            // deprecated no-ops under edge-to-edge on API 35+.)
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = LightColors,
            typography  = LegumTypography,
            shapes      = LegumShapes,
            content     = content
        )
    }
}
