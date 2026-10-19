package com.legumlex.client.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.Zinc300
import com.legumlex.client.ui.theme.Zinc500
import com.legumlex.client.ui.theme.Zinc50
import kotlinx.coroutines.delay

// Screen 01 — branded splash on the snow background, continuous with the
// native (windowSplashScreen) launch screen. Canonical LJ monogram + wordmark
// in client polarity (ink mark on snow tile), ~1.4s, version pinned bottom.
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    LaunchedEffect(Unit) {
        delay(1400)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc50),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Snow tile + ink glyph, 88dp · radius 20dp — matches design 05 · Splash.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Zinc50),
                contentAlignment = Alignment.Center,
            ) {
                LegumMark(
                    size      = 52.dp,
                    fillColor = AccentInk,
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Legum Lex",
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = (-0.9).sp,
                color = AccentInk,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "FOR CLIENTS",
                style = MaterialTheme.typography.labelMedium,
                color = Zinc500,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                letterSpacing = 2.4.sp,
            )
            // Hairline — the only secondary element on the splash.
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 1.dp)
                    .background(Zinc300),
            )
        }
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodySmall,
            color = Zinc500,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}
