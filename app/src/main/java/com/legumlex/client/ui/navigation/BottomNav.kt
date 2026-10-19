package com.legumlex.client.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.TertiaryText

// Icon-only bottom nav. Active tab gets a black circular background; inactive
// tabs are a quiet outlined glyph. Label text is dropped — icons carry the
// meaning (per direct UX feedback that the labels were redundant).
@Composable
fun LegumBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val spacing = LocalSpacing.current

    Surface(
        color = Surface1,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s4, vertical = spacing.s3),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentRoute?.hierarchyContains(tab.route.path) == true
                NavGlyph(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.route.path) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
    val bg by animateColorAsState(
        targetValue = if (selected) AccentInk else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "navBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) InverseText else TertiaryText,
        animationSpec = tween(durationMillis = 200),
        label = "navFg",
    )

    // 48dp touch target wraps a 44dp visual circle — keeps tap area within Material
    // guidance even when the circle itself is smaller for visual density.
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) tab.iconActive else tab.iconInactive,
                    contentDescription = stringResource(tab.labelRes),
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun String.hierarchyContains(prefix: String): Boolean =
    this == prefix || this.startsWith("$prefix/")
