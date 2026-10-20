package com.legumlex.client.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.Lex

/**
 * App-wide header — Mylex's top bar on a phone: the mark with "Legum Lex /
 * Client Portal" on the left (a back arrow before it on non-root screens), the
 * bell and the contact's initials on the right; the initials open Profile.
 * Owned by the top-level Scaffold so every screen shares one chrome.
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
    onOffice: (() -> Unit)? = null,
    initials: String? = null,
    avatarUrl: String? = null,
    onProfile: () -> Unit = {},
) {
    // Chrome: a solid step deeper than the page, with a hairline where they meet.
    Surface(color = Lex.colors.chrome, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = if (showBack) 4.dp else 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Lex.colors.foreground)
                    }
                }
                LegumMark(size = 24.dp, fillColor = Lex.colors.foreground)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Legum Lex", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lex.colors.foreground, lineHeight = 18.sp)
                    Text("Client Portal", fontSize = 11.5.sp, color = Lex.colors.mutedForeground, lineHeight = 14.sp)
                }
                // The Digital Office, beside the bell (owner, 2026-10-20: it left
                // the bottom bar when Consult took the centre).
                if (onOffice != null) IconButton(onClick = onOffice) {
                    Icon(Icons.Outlined.Videocam, contentDescription = "Digital Office", tint = Lex.colors.secondaryForeground)
                }
                Box {
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Lex.colors.secondaryForeground)
                    }
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                                .clip(CircleShape)
                                .background(Lex.colors.danger.solid)
                                .border(2.dp, Lex.colors.chrome, CircleShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                if (initials != null) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Lex.colors.muted)
                            .border(1.dp, Lex.colors.border, CircleShape)
                            .clickable(onClick = onProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initials, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Lex.colors.foreground)
                        // Their photo over the initials, when they have one.
                        if (avatarUrl != null) {
                            coil.compose.AsyncImage(
                                model = avatarUrl, contentDescription = "Your profile",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Lex.colors.chromeRule, thickness = 1.dp)
        }
    }
}

@Composable
private fun CountBadge(count: Int, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .offset(x = (-6).dp, y = 6.dp)
            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Lex.colors.chrome, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else count.toString(), fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = fg)
    }
}
