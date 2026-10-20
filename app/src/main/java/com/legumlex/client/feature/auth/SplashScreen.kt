package com.legumlex.client.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.brand.LegumMark
import kotlinx.coroutines.delay

/*
 * The splash, as Apple does its own: the mark alone, still, white on black,
 * edge to edge. No wordmark, no version, no grain. Continuous with the system
 * launch screen (Theme.LegumLex.Starting: the same mark on the same black).
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onTimeout()
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // The size the system launch screen draws it at, so it does not move.
        LegumMark(size = 82.dp, fillColor = Color.White)
    }
}
