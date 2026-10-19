package com.legumlex.client.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.brand.LegumWordmark
import kotlinx.coroutines.delay

/*
 * The splash: the company's own logo as its file has it, the white mark on
 * black (cases/assets/images/legum-lex-logo.png), continuous with the system
 * launch screen (Theme.LegumLex.Starting: the same mark on the same black).
 * The mark assembles as the website's header assembles it, then the wordmark
 * (cases/assets/images/logo.png) comes up beneath it.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val word = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(650)
        word.animateTo(1f, tween(500))
        delay(450)
        onTimeout()
    }

    // The splash is black in either theme: its status-bar icons are light
    // while it shows, and the theme's own come back after it.
    val view = LocalView.current
    val dark = com.legumlex.client.ui.theme.Lex.colors.isDark
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LegumMark(size = 76.dp, fillColor = Color.White, animate = true)
            Spacer(Modifier.height(28.dp))
            LegumWordmark(height = 17.dp, color = Color.White, modifier = Modifier.alpha(word.value))
        }
        if (versionName.isNotEmpty()) {
            Text(
                "v$versionName",
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            )
        }
    }
}
