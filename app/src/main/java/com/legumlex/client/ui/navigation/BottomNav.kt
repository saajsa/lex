package com.legumlex.client.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Print
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.grain
import androidx.compose.foundation.layout.windowInsetsBottomHeight

// The bottom bar: black, icons only, with a notch at its centre where Consult
// (the Legum Lex desk) sits raised in a circle. Home and Consultations to the
// left, Notices and More to the right; the Digital Office is in the header
// (owner, 2026-10-20). The current tab is its filled glyph with a dot under it.
@Composable
fun LegumBottomBar(navController: NavHostController, lead: Boolean = false, counsel: Boolean = false) {
    val tabs = if (lead) leadTabs else if (counsel) counselTabs else bottomTabs
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The tab a screen belongs to is the last tab root on the back stack (a
    // hearing opened from More belongs to More), or its own route's tab.
    val stack by navController.currentBackStack.collectAsState()
    val owner = stack.mapNotNull { it.destination.route }
        .lastOrNull { r -> tabs.any { it.route.path == r } }
        ?: currentRoute
    fun tab(r: Route) = tabs.first { it.route == r }
    fun on(t: BottomTab) = owner?.hierarchyContains(t.route.path) == true
    // Tabs pop back to Home and keep their state; Home itself clears to the
    // Home screen (navigateTab).
    fun go(t: BottomTab) = navController.navigateTab(t.route.path)

    if (counsel) {
        CounselBar(
            isOn = { r -> on(tab(r)) },
            onTab = { r -> go(tab(r)) },
            onPrint = {
                navController.navigate(Route.BoardSheet.build(com.legumlex.client.feature.board.BoardHub.chosen.value ?: com.legumlex.client.feature.board.BoardDates.api(com.legumlex.client.feature.board.BoardDates.today()), Route.BoardSheet.OWN, "Cause List"))
            },
            tabOf = { r -> tab(r) },
        )
        return
    }
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
            if (lead) {
                // Home and Enquiries on the left, More alone on the right.
                NavGlyph(tab(Route.LeadHome), on(tab(Route.LeadHome)), Modifier.weight(1f)) { go(tab(Route.LeadHome)) }
                NavGlyph(tab(Route.LeadEnquiries), on(tab(Route.LeadEnquiries)), Modifier.weight(1f)) { go(tab(Route.LeadEnquiries)) }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                NavGlyph(tab(Route.LeadMore), on(tab(Route.LeadMore)), Modifier.weight(1f)) { go(tab(Route.LeadMore)) }
            } else {
                NavGlyph(tab(Route.Home), on(tab(Route.Home)), Modifier.weight(1f)) { go(tab(Route.Home)) }
                NavGlyph(tab(Route.Consultations), on(tab(Route.Consultations)), Modifier.weight(1f)) { go(tab(Route.Consultations)) }
                Spacer(Modifier.weight(1f))
                NavGlyph(tab(Route.Notices), on(tab(Route.Notices)), Modifier.weight(1f)) { go(tab(Route.Notices)) }
                NavGlyph(tab(Route.More), on(tab(Route.More)), Modifier.weight(1f)) { go(tab(Route.More)) }
            }
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

/**
 * The Cause List button's printer: every ~3 s a sheet feeds out of it and draws back, black on the gold, in
 * the spirit of Consult's blinking cursor. Static when the system's animator duration scale is 0 (reduce motion).
 */
@Composable
private fun PrintGlyph() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val feed = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1700)
            feed.animateTo(1f, tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            kotlinx.coroutines.delay(300)
            feed.animateTo(0f, tween(350))
        }
    }
    // The sheet slides out of the printer's lower slot and stays inside the glyph's own 26 dp box.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp)) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.fillMaxSize())
            val sheetH = 7.dp * feed.value
            if (sheetH > 0.dp) Box(
                Modifier.align(Alignment.TopCenter).offset(y = 15.dp).size(width = 7.dp, height = sheetH)
                    .background(Color(0xFFF5C518))
                    .border(1.2.dp, Color.Black),
            )
        }
    }
}


// ---- the Counsel bar: a floating near-black bar with a concave notch and a raised gold square ------------------------

private val CounselBarColor = Color(0xFF111110)
private val CounselBarHeight = 64.dp
private val CounselBarMargin = 14.dp          // left, right and bottom: the page shows around the bar
private val CounselOverhang = 26.dp           // how far the gold button rises above the bar
private val CounselFabSize = 56.dp
private val NotchHalf = 62.dp                 // the dip's half width, shoulders included
private val NotchDepth = 34.dp

/** Height the bar asks of the screen above the system navigation inset: bar, margin and the button's rise. */
val CounselBarZone = CounselBarHeight + CounselBarMargin + CounselOverhang

/** Rounded bar (fully rounded ends) with a smooth concave dip at the centre of its top edge, the shoulders eased. */
private val CounselBarShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val w = size.width; val h = size.height
        val r = h / 2f
        val cx = w / 2f
        val hw = with(density) { NotchHalf.toPx() }
        val d = with(density) { NotchDepth.toPx() }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(r, 0f)
            lineTo(cx - hw, 0f)
            // down into the dip and up again: an eased S on each side
            cubicTo(cx - hw * 0.55f, 0f, cx - hw * 0.62f, d, cx, d)
            cubicTo(cx + hw * 0.62f, d, cx + hw * 0.55f, 0f, cx + hw, 0f)
            lineTo(w - r, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, 0f, w, h), -90f, 180f, false)
            lineTo(r, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, h), 90f, 180f, false)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
private fun CounselBar(isOn: (Route) -> Boolean, onTab: (Route) -> Unit, onPrint: () -> Unit, tabOf: (Route) -> BottomTab) {
    Column(Modifier.fillMaxWidth()) {
    Box(
        Modifier.fillMaxWidth()
            .padding(start = CounselBarMargin, end = CounselBarMargin, bottom = CounselBarMargin)
            .height(CounselBarHeight + CounselOverhang),
    ) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(CounselBarHeight)
                .background(CounselBarColor, CounselBarShape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).fillMaxHeight()) {
                CounselGlyph(tabOf(Route.Home), isOn(Route.Home), Modifier.weight(1f)) { onTab(Route.Home) }
                CounselGlyph(tabOf(Route.CounselCalendar), isOn(Route.CounselCalendar), Modifier.weight(1f)) { onTab(Route.CounselCalendar) }
            }
            Spacer(Modifier.width(NotchHalf * 2 - 16.dp))
            Row(Modifier.weight(1f).fillMaxHeight()) {
                CounselGlyph(tabOf(Route.BoardCredits), isOn(Route.BoardCredits), Modifier.weight(1f)) { onTab(Route.BoardCredits) }
                CounselGlyph(tabOf(Route.Upgrade), isOn(Route.Upgrade), Modifier.weight(1f)) { onTab(Route.Upgrade) }
            }
        }
        // Cause List: a solid gold rounded square, half above the bar, in the dip.
        val fabShape = RoundedCornerShape(18.dp)
        Surface(
            onClick = onPrint, shape = fabShape, color = DeskYellow, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopCenter).size(CounselFabSize).semantics { contentDescription = "Cause List" },
        ) { PrintGlyph() }
    }
    // The system navigation inset below the bar carries the page's own colour and grain, so it does not read as a lighter band.
    // In the dark Upgrade room the band goes dark with the room.
    val roomDark by com.legumlex.client.feature.board.UpgradeRoom.dark.collectAsState()
    val bandStill = com.legumlex.client.feature.board.rememberReducedMotion()
    val bandColour by animateColorAsState(if (roomDark) Color(0xFF151513) else com.legumlex.client.ui.theme.Lex.colors.background, tween(if (bandStill) 0 else 900), label = "band")
    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars)
        .background(bandColour).grain())
    }
}

@Composable
private fun CounselGlyph(tab: BottomTab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fg by animateColorAsState(
        targetValue = if (selected) DeskYellow else Color.White.copy(alpha = 0.78f),
        animationSpec = tween(durationMillis = 200), label = "counselNavFg",
    )
    Box(modifier.fillMaxHeight().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (selected) tab.iconActive else tab.iconInactive,
            contentDescription = stringResource(tab.labelRes), tint = fg, modifier = Modifier.size(if (tab.route == Route.Home) 27.dp else 24.dp),
        )
    }
}

/** How much room a Counsel screen leaves at its foot so its last item clears the floating bar and the raised button. */
val LocalCounselClearance = androidx.compose.runtime.compositionLocalOf { 16.dp }
