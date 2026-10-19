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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.Surface1

// Icon-only bottom nav (labels were judged redundant). The active tab is drawn
// as Mylex's sidebar draws its current item: a soft grey rounded block with the
// glyph in full ink; the rest are quiet. A hairline separates it from the page.
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
        Column {
        HorizontalDivider(color = Lex.colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.s4, vertical = spacing.s2),
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
}

@Composable
private fun NavGlyph(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Lex.colors.muted else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "navBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Lex.colors.foreground else Lex.colors.mutedForeground,
        animationSpec = tween(durationMillis = 200),
        label = "navFg",
    )

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 60.dp, height = 44.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
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
