package com.legumlex.client.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.Surface1

// The bottom bar is chrome, as the header is: a solid step deeper than the
// page, a hairline where they meet. Each tab is its glyph over its name in
// small caps; the current one carries a short ink bar above it and full ink.
@Composable
fun LegumBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The tab a screen belongs to is the last tab root on the back stack (a
    // hearing opened from More belongs to More), or its own route's tab.
    val stack by navController.currentBackStack.collectAsState()
    val owner = stack.mapNotNull { it.destination.route }
        .lastOrNull { r -> bottomTabs.any { it.route.path == r } }
        ?: currentRoute
    val spacing = LocalSpacing.current

    Surface(
        color = Lex.colors.chrome,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
        HorizontalDivider(color = Lex.colors.chromeRule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.s4, vertical = spacing.s2),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomTabs.forEach { tab ->
                val selected = owner?.hierarchyContains(tab.route.path) == true
                NavGlyph(
                    tab = tab,
                    selected = selected,
                    // Tabs pop back to Home and keep their state; Home itself
                    // clears to the Home screen (navigateTab).
                    onClick = { navController.navigateTab(tab.route.path) },
                )
            }
        }
        }
    }
}

@Composable
private fun NavGlyph(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        targetValue = if (selected) Lex.colors.foreground else Lex.colors.mutedForeground,
        animationSpec = tween(durationMillis = 200),
        label = "navFg",
    )
    val bar by animateColorAsState(
        targetValue = if (selected) Lex.colors.foreground else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "navBar",
    )
    val label = stringResource(tab.labelRes)
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 72.dp, height = 52.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(22.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(bar))
            Spacer(Modifier.height(7.dp))
            Icon(
                imageVector = if (selected) tab.iconActive else tab.iconInactive,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label.uppercase(),
                fontSize = 9.5.sp,
                letterSpacing = 0.9.sp,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

private fun String.hierarchyContains(prefix: String): Boolean =
    this == prefix || this.startsWith("$prefix/")
