package com.legumlex.client.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 8px grid — exposed via CompositionLocal so callers can `LocalSpacing.current.s4`.
data class Spacing(
    val s1: Dp = 4.dp,
    val s2: Dp = 8.dp,
    val s3: Dp = 12.dp,
    val s4: Dp = 16.dp,
    val s5: Dp = 20.dp,
    val s6: Dp = 24.dp,
    val s8: Dp = 32.dp,
    val s12: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
