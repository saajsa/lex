package com.legumlex.client.ahc.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

/* ============================================================================
 *  LegumLexTheme — LIGHT ONLY. We hand the M3 color scheme our warm-paper
 *  spine so any stock Material component inherits the brand. The bespoke AHC
 *  composables read LlColor / LlText directly rather than the M3 scheme.
 *
 *  Dark mode is intentionally unsupported on the client app (legal-professional,
 *  print-like surface). We ignore isSystemInDarkTheme() and stay light.
 * ========================================================================== */

private val LlLightScheme = lightColorScheme(
    primary          = LlColor.Ink,          // primary action = ink button
    onPrimary        = LlColor.Paper,
    secondary        = LlColor.Claret,        // brand accent
    onSecondary      = LlColor.Paper,
    background        = LlColor.Paper,
    onBackground      = LlColor.Ink,
    surface          = LlColor.Surface,
    onSurface        = LlColor.Ink,
    surfaceVariant   = LlColor.Paper1,
    onSurfaceVariant = LlColor.InkMuted,
    outline          = LlColor.Border,
    outlineVariant   = LlColor.BorderStrong,
    error            = LlColor.ClaretMid,
    onError          = LlColor.Paper,
)

/* Minimal M3 Typography so stock Material components stay on-brand. Defined
 * here (not in Type.kt) to avoid a static-init cycle — object LlText reads the
 * font families declared in Type.kt, so Type.kt must not reference LlText back. */
private val LlTypography = Typography(
    titleLarge  = LlText.appBarTitle,
    titleMedium = LlText.heroVerdict,
    bodyLarge   = LlText.body.copy(fontSize = 14.sp),
    bodyMedium  = LlText.body,
    labelLarge  = LlText.button.copy(color = LlColor.Ink),
    labelSmall  = LlText.eyebrow,
)

@Composable
fun LegumLexTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredDark = isSystemInDarkTheme() // deliberately not applied — light only
    MaterialTheme(
        colorScheme = LlLightScheme,
        typography = LlTypography,
        content = content,
    )
}
