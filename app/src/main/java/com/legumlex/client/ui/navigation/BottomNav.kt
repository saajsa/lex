package com.legumlex.client.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

// The bottom bar: black, icons only, with a notch at its centre where Consult
// (the Legum Lex desk) sits raised in a circle. Home and Consultations to the
// left, Notices and More to the right; the Digital Office is in the header
// (owner, 2026-10-20). The current tab is its filled glyph with a dot under it.
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
    fun tab(r: Route) = bottomTabs.first { it.route == r }
    fun on(t: BottomTab) = owner?.hierarchyContains(t.route.path) == true
    // Tabs pop back to Home and keep their state; Home itself clears to the
    // Home screen (navigateTab).
    fun go(t: BottomTab) = navController.navigateTab(t.route.path)

    val bar = Color.Black
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bar, NotchShape)
                .navigationBarsPadding()
                .height(BarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavGlyph(tab(Route.Home), on(tab(Route.Home)), Modifier.weight(1f)) { go(tab(Route.Home)) }
            NavGlyph(tab(Route.Consultations), on(tab(Route.Consultations)), Modifier.weight(1f)) { go(tab(Route.Consultations)) }
            Spacer(Modifier.weight(1f))
            NavGlyph(tab(Route.Notices), on(tab(Route.Notices)), Modifier.weight(1f)) { go(tab(Route.Notices)) }
            NavGlyph(tab(Route.More), on(tab(Route.More)), Modifier.weight(1f)) { go(tab(Route.More)) }
        }
        // Consult, raised: its centre on the bar's top edge, in the notch. It
        // opens the desk, where anything new begins.
        Surface(
            onClick = { navController.navigate(Route.NewConsultation.build()) },
            shape = CircleShape,
            // The counsel desk, in yellow with black type (owner, 2026-10-20),
            // ringed in the bar's black so it sits in the notch.
            color = DeskYellow,
            border = androidx.compose.foundation.BorderStroke(4.dp, bar),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -FabSize / 2)
                .size(FabSize)
                .semantics { contentDescription = "Consult" },
        ) {
            // A prompt with a blinking cursor: ">" steady, the bar on and off
            // every 530 ms, as a terminal's cursor blinks.
            var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) { kotlinx.coroutines.delay(530); on = !on }
            }
            Box(contentAlignment = Alignment.Center) {
                val mono = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 19.sp, color = Color.Black)
                // The cursor is drawn, not typed: a bar on the prompt's baseline.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(">", style = mono)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.padding(bottom = 6.dp).size(width = 10.dp, height = 3.dp)
                        .background(if (on) Color.Black else Color.Transparent))
                }
            }
        }
    }
}

private val BarHeight = 64.dp
private val DeskYellow = Color(0xFFF5C518)
private val FabSize = 56.dp
private val NotchRadius = 36.dp   // the circle, plus the gap around it
private val NotchShoulder = 12.dp

/** The bar with a round notch at the centre of its top edge, its shoulders eased. */
private val NotchShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = with(density) { NotchRadius.toPx() }
        val s = with(density) { NotchShoulder.toPx() }
        val cx = size.width / 2
        val a0 = Math.toRadians(155.0); val a1 = Math.toRadians(25.0)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(cx - r - s, 0f)
            quadraticTo(cx - r, 0f, cx + r * kotlin.math.cos(a0).toFloat(), r * kotlin.math.sin(a0).toFloat())
            arcTo(androidx.compose.ui.geometry.Rect(cx - r, -r, cx + r, r), 155f, -130f, false)
            quadraticTo(cx + r, 0f, cx + r + s, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
private fun NavGlyph(
    tab: BottomTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 200),
        label = "navFg",
    )
    Box(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (selected) tab.iconActive else tab.iconInactive,
                contentDescription = stringResource(tab.labelRes),
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Color.White else Color.Transparent))
        }
    }
}

private fun String.hierarchyContains(prefix: String): Boolean =
    this == prefix || this.startsWith("$prefix/")
