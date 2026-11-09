package com.legumlex.client.feature.board

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.R
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFF5C518)
private val InkDark = Color(0xFF151513)
private val RoomDark = Color(0xFF151513)
private val Chalk = Color(0xFFF4F3EF)
private val Ease = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * Whether the Upgrade room is dark. The app's header reads this so the dark room is not cut by a light band: while it is
 * dark the header (and the status bar) go dark with it, and come back with the same fade when the light is on.
 */
object UpgradeRoom {
    val dark = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** The header's palette in the dark room: the room's own colour, light marks and chip. */
val UpgradeRoomColors = com.legumlex.client.ui.theme.DarkLexColors.copy(
    chrome = RoomDark, chromeRule = Color(0xFF2C2B28), foreground = Chalk, secondaryForeground = Color(0xFFCFCDC6),
    mutedForeground = Color(0xFFA3A19A), border = Color(0xFF3A3936), input = Color(0xFF55524C),
)

/** One platform tile: its name and its line icon (SVG paths on a 24 grid, as the canvas draws them). */
private class PlatformTile(val name: String, val paths: List<String>)

private val Tiles = listOf(
    PlatformTile("CASES", listOf("M3 7h6l2 2h10v10H3z")),
    PlatformTile("HEARINGS", listOf("M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 10h16M9 3v4M15 3v4")),
    PlatformTile("CLIENTS", listOf("M6 9a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6", "M15 6.5a2.5 2.5 0 1 1 1 4.8M17 14.5c2.4.6 4 2.8 4 5.5")),
    PlatformTile("DOCUMENTS", listOf("M6 3h8l4 4v14H6z", "M14 3v4h4M9 12h6M9 16h6")),
    PlatformTile("MAIL", listOf("M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M3 8l9 6 9-6")),
    PlatformTile("ACCOUNTS", listOf("M7 5h10M7 9h10M7 5c5 0 5 8 0 8l7 6")),
)

private const val Soon = "COMING SOON"

@OptIn(ExperimentalTextApi::class)
internal val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Medium, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
)

/**
 * Upgrade (REQ-008, canvas 9b, UpgradePlatform): "light the way". It opens dark with the tagline coming into focus and a cord
 * on the right. Drag its pill down, or tap it, to light the room: the page colour comes up, the platform's parts rise in, "Coming
 * soon" types itself, and the Counsel panel slides in. A small switch at the bottom right puts the light out again. It collects
 * nothing and carries no link. Instant when the system's animator scale is 0.
 */
@Composable
fun UpgradeScreen() {
    val still = rememberReducedMotion()
    var lit by remember { mutableStateOf(false) }
    fun ms(v: Int) = if (still) 0 else v

    val room by animateColorAsState(if (lit) Lex.colors.background else RoomDark, tween(ms(900)), label = "room")
    val ink by animateColorAsState(if (lit) InkDark else Chalk, tween(ms(900)), label = "ink")
    val panelX by animateFloatAsState(if (lit) 248f else 300f, tween(ms(1000), delayMillis = ms(1100), easing = Ease), label = "panel")
    val clearance = com.legumlex.client.ui.navigation.LocalCounselClearance.current
    // The header goes dark with the room; leaving the screen puts it back.
    LaunchedEffect(lit) { UpgradeRoom.dark.value = !lit }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { UpgradeRoom.dark.value = false } }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(room).clipToBounds()) {
    // Everything sits between the header's bottom and the bar zone's top, 20 dp in from each. The type scales with the
    // height left, so the whole stack fits on small phones too (about 544 dp of stack at full size).
    val avail = maxHeight - clearance - 40.dp
    val k = (avail.value / 446f).coerceIn(0.72f, 1f)
    // The panel is shorter: 430 dp from 60 dp under the header, never past 16 dp above the bar zone.
    val panelHeight = minOf(430.dp, (maxHeight - 60.dp - clearance - 16.dp).coerceAtLeast(120.dp))
        // The Counsel app, peeking from the right edge once the light is on.
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 60.dp).height(panelHeight).width(300.dp)
                .offset(x = panelX.dp)
                .shadow(14.dp, RoundedCornerShape(22.dp), clip = false).clip(RoundedCornerShape(22.dp)).background(InkDark),
        ) {
            Box(Modifier.padding(start = 18.dp, top = 22.dp).size(width = 22.dp, height = 2.dp).background(Chalk))
            Box(Modifier.padding(start = 18.dp, top = 29.dp).size(width = 22.dp, height = 2.dp).background(Chalk))
            Text("TODAY", fontSize = 11.sp, letterSpacing = 1.2.sp, color = Color(0xFFA3A19A), modifier = Modifier.padding(start = 18.dp, top = 96.dp))
            Box(Modifier.padding(start = 18.dp, top = 126.dp).size(9.dp).clip(CircleShape).background(Gold))
            Box(Modifier.padding(top = 276.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2C2B28)))
            Box(Modifier.padding(top = 366.dp).fillMaxWidth().height(120.dp).background(Color(0xFFE9E7E1)))
        }

        Cord(lit = lit, still = still, onLight = { lit = true })

        Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 60.dp, top = 20.dp, bottom = clearance + 20.dp)) {
            Text(
                "THE FULL FIRM PLATFORM", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (12 * k).sp, letterSpacing = 2.6.sp,
                color = ink, modifier = Modifier.padding(top = 4.dp),
            )
            Column(Modifier.padding(top = (16 * k).dp)) {
                Focus(delayMs = 0, still = still) { ls -> Text("Justice,", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = (30 * k).sp, lineHeight = (32.4f * k).sp, letterSpacing = ls, color = ink) }
                Focus(delayMs = 500, still = still) { ls -> Text("in plain sight.", fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = (30 * k).sp, lineHeight = (32.4f * k).sp, letterSpacing = ls, color = ink) }
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Tiles.chunked(2).forEachIndexed { r, row ->
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, t ->
                            val delay = 350 + 100 * (r * 2 + c)
                            Reveal(lit, delay, still, Modifier.weight(1f)) { TileCard(t, lit, still, delay, k) }
                        }
                    }
                }
            }
            Reveal(lit, 1050, still, Modifier.padding(top = (22 * k).dp)) {
                Text("Your Daily Board is one part of a complete firm platform.", fontSize = (13 * k).sp, lineHeight = (19.5f * k).sp, color = Color(0xFF55524C), modifier = Modifier.widthIn(max = 262.dp))
            }
            Reveal(lit, 1200, still, Modifier.padding(top = 4.dp)) {
                Text("A much bigger journey awaits.", fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = (27 * k).sp, lineHeight = (31 * k).sp, color = ink)
            }
            Reveal(lit, 1400, still, Modifier.padding(top = (18 * k).dp)) { Typed(lit, still, k) }
        }

        // The switch sits bottom right, just above the bar zone: clear of the gold button and of "Coming soon".
        LightSwitch(lit = lit, still = still, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = clearance + 6.dp)) { lit = false }
    }
}

/** A line that rises into place (fades in, up 12 dp) after its delay once lit; it goes at once when the light goes out. */
@Composable
private fun Reveal(lit: Boolean, delayMs: Int, still: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p by animateFloatAsState(if (lit) 1f else 0f, tween(if (still) 0 else 600, delayMillis = if (lit && !still) delayMs else 0, easing = Ease), label = "reveal")
    val px = with(LocalDensity.current) { 12.dp.toPx() }
    Box(modifier.graphicsLayer { alpha = p; translationY = (1f - p) * px }) { content() }
}

/** The tagline's words coming into focus: blur and wide letter spacing settle to sharp over 1.6 s. */
@Composable
private fun Focus(delayMs: Int, still: Boolean, content: @Composable (letterSpacing: androidx.compose.ui.unit.TextUnit) -> Unit) {
    val p = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) { if (!still) { delay(delayMs.toLong()); p.animateTo(1f, tween(1600, easing = Ease)) } }
    Box(Modifier.blur((10f * (1f - p.value)).dp).alpha(p.value)) { content((6f * (1f - p.value)).sp) }
}

/** COMING SOON in tracked capitals after a short gold rule, typed in after the light, with a blinking gold caret right behind it. */
@Composable
private fun Typed(lit: Boolean, still: Boolean, k: Float = 1f) {
    var shown by remember { mutableStateOf(0) }
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(lit, still) {
        if (!lit) { shown = 0; return@LaunchedEffect }
        if (still) { shown = Soon.length; return@LaunchedEffect }
        delay(300)
        for (n in 1..Soon.length) { shown = n; delay(1400L / Soon.length) }
    }
    LaunchedEffect(lit, still) {
        if (still || !lit) return@LaunchedEffect
        while (true) { delay(500); caretOn = !caretOn }
    }
    Row(Modifier.semantics { contentDescription = "Coming soon" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 22.dp, height = 2.dp).background(Gold))
        // The text takes only the width of what is typed, so the caret sits right after it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Soon.take(shown), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (15 * k).sp, letterSpacing = 4.sp, color = InkDark, maxLines = 1, softWrap = false)
            Text("|", fontSize = (15 * k).sp, color = if (lit && (still || caretOn)) Gold else Color.Transparent)
        }
    }
}

/** One tile: a 2-across card with a line icon that draws itself in, and the name in tracked capitals. */
@Composable
private fun TileCard(t: PlatformTile, lit: Boolean, still: Boolean, delayMs: Int, k: Float) {
    val p by animateFloatAsState(
        if (lit) 1f else 0f,
        tween(if (still) 0 else if (lit) 1100 else 0, delayMillis = if (lit && !still) delayMs else 0, easing = Ease), label = "draw",
    )
    Column(
        Modifier.fillMaxWidth().height((66 * k).dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD6D3CC), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = (11 * k).dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        DrawnIcon(t.paths, p, Modifier.size((22 * k).dp))
        Text(t.name, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (11 * k).sp, letterSpacing = 2.sp, color = InkDark, maxLines = 1, softWrap = false)
    }
}

/** A line icon drawn in by path length: [progress] 0 to 1 along each stroke (stroke 1.5 on a 24 grid, round caps and joins). */
@Composable
private fun DrawnIcon(paths: List<String>, progress: Float, modifier: Modifier) {
    val parts = remember(paths) {
        paths.flatMap { d -> d.split("M").filter { it.isNotBlank() }.map { "M$it" } }.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.5f * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        parts.forEach { path ->
            val scaled = Path().apply { addPath(path); transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) }) }
            if (progress >= 0.999f) drawPath(scaled, InkDark, style = stroke)
            else if (progress > 0f) {
                val m = androidx.compose.ui.graphics.PathMeasure(); m.setPath(scaled, false)
                val seg = Path(); m.getSegment(0f, m.length * progress, seg, true)
                drawPath(seg, InkDark, style = stroke)
            }
        }
    }
}

/** The cord on the right and its pill: drag it down (a tick at the bottom) or tap it to light the room. Chevrons pulse below it. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Cord(lit: Boolean, still: Boolean, onLight: () -> Unit) {
    val density = LocalDensity.current
    val max = with(density) { 92.dp.toPx() }
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var ticked by remember { mutableStateOf(false) }
    LaunchedEffect(lit) {
        if (lit) pull.animateTo(max, if (still) tween(0) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        else { ticked = false; pull.animateTo(0f, if (still) tween(0) else tween(400, easing = Ease)) }
    }
    val fade by animateFloatAsState(if (lit) 0f else 1f, if (lit && !still) tween(600, delayMillis = 2200) else tween(0), label = "cordFade")
    val knobColour by animateColorAsState(if (lit) Gold else Chalk, tween(if (still) 0 else 900), label = "knob")
    val pullDp = with(density) { pull.value.toDp() }

    // the cord
    Box(
        Modifier.align(Alignment.TopEnd).padding(end = 30.dp).width(2.dp).height(232.dp + pullDp).alpha(fade)
            .background(if (lit) Color(0xFFC9C6BF) else Color(0xFF8A877F)),
    )
    // the pill
    Box(
        Modifier.align(Alignment.TopEnd).padding(end = 17.dp).offset(y = 228.dp + pullDp).alpha(fade)
            .size(width = 28.dp, height = 58.dp).clip(RoundedCornerShape(14.dp)).background(knobColour)
            .semantics { contentDescription = "Pull to light the way" }
            .pointerInput(lit) { detectTapGestures(onTap = { if (!lit) onLight() }) }
            .pointerInput(lit) {
                if (lit) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { ticked = false },
                    onVerticalDrag = { _, dy ->
                        scope.launch { pull.snapTo((pull.value + dy).coerceIn(0f, max)) }
                        if (pull.value + dy >= max && !ticked) { ticked = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    },
                    onDragEnd = { if (pull.value >= max * 0.55f) onLight() else scope.launch { pull.animateTo(0f, tween(300, easing = Ease)) } },
                    onDragCancel = { scope.launch { pull.animateTo(0f, tween(300, easing = Ease)) } },
                )
            },
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(8.dp).clip(CircleShape).background(InkDark.copy(alpha = 0.55f)))
    }
    // three gold chevrons pulsing downwards, no hint text
    val hint by animateFloatAsState(if (lit) 0f else 1f, tween(if (still) 0 else 400), label = "hint")
    val t = rememberInfiniteTransition(label = "chev")
    Column(Modifier.align(Alignment.TopEnd).padding(end = 21.dp, top = 300.dp).width(20.dp).alpha(hint)) {
        for (i in 0 until 3) {
            val a = if (still) 0.8f else t.animateFloat(
                0.15f, 0.15f,
                infiniteRepeatable(keyframes { durationMillis = 1400; 0.15f at 0; 1f at 560 using LinearEasing; 0.15f at 1400 }, RepeatMode.Restart, StartOffset((180 * i))),
                label = "c$i",
            ).value
            Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
                val w = size.width; val h = size.height
                val p = Path().apply { moveTo(w * 0.25f, h * 0.1f); lineTo(w * 0.5f, h * 0.85f); lineTo(w * 0.75f, h * 0.1f) }
                drawPath(p, Gold.copy(alpha = a), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** The switch at the bottom right: a black pill with a gold dot that puts the light out. Appears after the light is on. */
@Composable
private fun LightSwitch(lit: Boolean, still: Boolean, modifier: Modifier, onOff: () -> Unit) {
    val a by animateFloatAsState(if (lit) 1f else 0f, if (lit && !still) tween(500, delayMillis = 2400) else tween(0), label = "switch")
    if (a <= 0.01f) return
    Box(
        modifier.alpha(a).size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(13.dp)).background(InkDark)
            .semantics { contentDescription = "Switch off the light" }.clickable(onClick = onOff),
    ) {
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(20.dp)
                .shadow(6.dp, CircleShape, ambientColor = Gold, spotColor = Gold).clip(CircleShape).background(Gold),
        )
    }
}
