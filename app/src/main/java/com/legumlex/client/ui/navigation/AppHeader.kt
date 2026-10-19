package com.legumlex.client.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.PrimaryText

/**
 * App-wide header — a single thin bar carrying the centered Legum Lex mark, with
 * a left back arrow on non-root screens. Owned by the top-level Scaffold so every
 * screen shares one chrome; individual screens no longer draw their own top bar.
 *
 * The bar consumes the status-bar inset (statusBarsPadding), so the Scaffold's
 * content padding places screen bodies cleanly beneath it.
 */
@Composable
fun AppHeader(
    showBack: Boolean,
    onBack: () -> Unit,
    onNotifications: () -> Unit = {},
    unreadCount: Int = 0,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText,
                        )
                    }
                }
                LegumMark(
                    size = 26.dp,
                    modifier = Modifier.align(Alignment.Center),
                    fillColor = AccentInk,
                )
                // App-wide notifications bell with unread badge.
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    IconButton(onClick = onNotifications) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = PrimaryText,
                        )
                    }
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                                .clip(CircleShape)
                                .background(LlColor.Claret)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                fontFamily = Inter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
        }
    }
}
