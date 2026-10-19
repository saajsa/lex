package com.legumlex.client.feature.digital_office

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.legumlex.client.ahc.theme.Fraunces
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.ChamberState
import com.legumlex.client.feature.home.Sitting
import com.legumlex.client.feature.home.parseChamber
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/* ============================================================================
 *  The Digital Office — the Legum Lex chamber page (digital_office/views/
 *  public/chamber_site.php, cases/assets/css/src/site.tw.css), on a phone.
 *
 *  The desk first, since the call is what the tab is for: "At the desk." in
 *  Fraunces with How It Works beside it, the door's state and tally, the
 *  Google Meet call panel the client knocks from (their own camera and
 *  microphone live in it once they turn them on, see SelfView), the
 *  advocates, the sitting clock, and the chamber's register in boxes. Light
 *  mode is monochrome ink as the page is; dark mode takes the page's gold.
 *
 *  What the page cannot know, the app adds in the same language: the one-time
 *  registration while it is outstanding, and a session already in flight.
 * ========================================================================== */

class DigitalOfficeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val _state = MutableStateFlow<ChamberState?>(null)
    val state: StateFlow<ChamberState?> = _state.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var poll: Job? = null

    suspend fun fetch() {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            parseChamber(api.dashboardChamber()["data"] as? Map<String, Any?>)
        }.onSuccess { _state.value = it; _failed.value = false }
         .onFailure { if (_state.value == null) _failed.value = true }
    }

    fun refresh() {
        viewModelScope.launch { _refreshing.value = true; fetch(); _refreshing.value = false }
    }

    // Presence is a three-minute heartbeat; every 30 seconds keeps the desk
    // honest while the tab is on screen, and stops when it is not.
    fun startPolling() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch { while (isActive) { fetch(); delay(30_000) } }
    }

    fun stopPolling() { poll?.cancel(); poll = null }
}

// ---- The page's own colours -------------------------------------------------

/** The chamber page's accent: ink on light (its monochrome), gold on dark. */
private class ChamberInk(val gold: Color, val onGold: Color, val line: Color, val wash: Color)

@Composable
private fun chamberInk(): ChamberInk {
    val c = Lex.colors
    return if (c.isDark) ChamberInk(Color(0xFFF3C25E), Color(0xFF1B1406), Color(0x38FFECC4), Color(0x0AFFFFFF))
    else ChamberInk(c.foreground, Color.White, Color(0x29000000), Color(0x0A000000))
}

private val Free = Color(0xFF3FAE7C)
private val Busy = Color(0xFFE0A339)

// Google Meet's own greys, as the page's call panel draws them.
private val MeetFrame = Color(0xFF202124)
private val MeetStage = Color(0xFF3C4043)
private val MeetIdle = Color(0xFF5F6368)
private val MeetIcon = Color(0xFFE8EAED)
private val MeetMuted = Color(0xFFBDC1C6)
private val MeetGreen = Color(0xFF1E8E3E)
private val MeetBlue = Color(0xFF1A73E8)
private val MeetRed = Color(0xFFEA4335)

private fun serif(size: Int, color: Color) =
    TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size * 1.08f).sp,
        letterSpacing = (-0.02f * size).sp, color = color)

// ---- The screen -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalOfficeScreen(
    onWalkIn: (advocateStaffId: Int?) -> Unit,
    onOpenLanding: (advocateStaffId: Int?) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onAskAdvocate: () -> Unit,
) {
    val vm: DigitalOfficeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val failed by vm.failed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    DisposableEffect(vm) { vm.startPolling(); onDispose { vm.stopPolling() } }

    // The cabin queue: the same banner and sheet Home shows.
    val waitroomVm = sharedDoWaitroomViewModel()
    val waitroomRows by waitroomVm.rows.collectAsState()
    var openRow by remember { mutableStateOf<DoWaitroomRow?>(null) }
    val event by waitroomVm.event.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(event) { event?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); waitroomVm.clearEvent() } }

    val s = state
    when {
        s == null && failed -> Column(
            Modifier.fillMaxSize().background(Lex.colors.background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexEmpty("The Digital Office Could Not Be Reached", "Please check your connection and try again.")
            TextButton(onClick = vm::refresh) { Text("Try Again", color = Lex.colors.foreground) }
        }
        s == null -> Box(Modifier.fillMaxSize().background(Lex.colors.background), Alignment.Center) {
            CircularProgressIndicator(color = Lex.colors.foreground, strokeWidth = 2.dp)
        }
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
                DoWaitroomBanner(rows = waitroomRows, onTap = { openRow = it })
                Chamber(s, onWalkIn, onOpenLanding, onOpenConsultation, onAskAdvocate)
            }
        }
    }

    openRow?.let { row ->
        DoWaitroomSheet(
            row = row,
            onStartSession = { id -> openRow = null; onWalkIn(id) },
            onLeave = { wid -> waitroomVm.leave(wid); openRow = null },
            onDismiss = { openRow = null },
        )
    }
}

/** Where the day stands against the sitting hours, in the chamber's time zone. */
private data class Clock(val phase: String, val share: Float, val today: Int, val minute: Int)

private fun clockAt(h: Sitting, now: ZonedDateTime): Clock {
    val d = now.dayOfWeek.value
    val m = now.hour * 60 + now.minute
    val phase = when {
        d !in h.days -> "off"
        m < h.from -> "before"
        m < h.to -> "during"
        else -> "after"
    }
    val share = when (phase) {
        "during" -> (m - h.from).toFloat() / (h.to - h.from).coerceAtLeast(1)
        "after" -> 1f
        else -> 0f
    }
    return Clock(phase, share, d, m)
}

private fun clockTime(m: Int): String {
    val h = m / 60; val mm = m % 60
    return "${if (h % 12 == 0) 12 else h % 12}${if (mm > 0) ":%02d".format(mm) else ""} ${if (h >= 12) "PM" else "AM"}"
}

private fun span(m: Int): String {
    val h = m / 60; val mm = m % 60
    return if (h > 0) "$h h" + (if (mm > 0) " $mm min" else "") else "$mm min"
}

private val dayNames = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The clock's line, as the page writes it. */
private fun clockSay(h: Sitting, c: Clock, empty: Boolean): String = when (c.phase) {
    "during" -> if (empty) "Sitting hours · no one at the desk yet" else "Sitting now · ${span(h.to - c.minute)} left"
    "before" -> "Opens at ${clockTime(h.from)} · in ${span(h.from - c.minute)}"
    else -> {
        var next = dayNames[h.days.first()]
        for (i in 1..7) {
            val day = ((c.today - 1 + i) % 7) + 1
            if (day in h.days) { next = if (i == 1) "tomorrow" else dayNames[day]; break }
        }
        "Opens $next at ${clockTime(h.from)}"
    }
}

/** "Mon to Sat · 3:30 to 9 PM, India time" */
private fun hoursLine(h: Sitting): String {
    val short = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val days = if (h.days.size > 1) "${short[h.days.first()]} to ${short[h.days.last()]}" else short[h.days.first()]
    val from = clockTime(h.from).let { if ((h.from >= 720) == (h.to >= 720)) it.removeSuffix(" AM").removeSuffix(" PM") else it }
    val zone = if (h.tz == "Asia/Kolkata") "India time" else h.tz
    return "$days · $from to ${clockTime(h.to)}, $zone"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chamber(
    c: ChamberState,
    onWalkIn: (Int?) -> Unit,
    onOpenLanding: (Int?) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onAskAdvocate: () -> Unit,
) {
    val ink = chamberInk()
    var showHow by remember { mutableStateOf(false) }

    // The clock repaints itself every 30 seconds; it never asks the server.
    val zone = remember(c.sitting.tz) { runCatching { ZoneId.of(c.sitting.tz) }.getOrDefault(ZoneId.of("Asia/Kolkata")) }
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) { while (true) { now = ZonedDateTime.now(zone); delay(30_000) } }
    val clock = clockAt(c.sitting, now)

    val open = c.isOpen && c.roster.isNotEmpty()

    // The desk comes first: the call is what the tab is for.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!c.hasAccess) Registration(c, ink) { onWalkIn(null) }
        c.activeSession?.let { a ->
            InSession(ink) { a.consultationId?.let { onOpenConsultation(it.toString()) } ?: onWalkIn(null) }
        }
        Desk(c, open, clock, ink, onWalkIn, onOpenLanding, onAskAdvocate, onHow = { showHow = true })
    }

    if (showHow) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showHow = false }, sheetState = sheet, containerColor = Lex.colors.card) {
            HowItWorks(ink) { showHow = false }
        }
    }
}

/** A word in an outlined pill, as the page's headline sets "chamber," and "open." */
@Composable
private fun PillWord(word: String, size: Int, ink: ChamberInk) {
    Box(
        Modifier.border(1.5.dp, ink.line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 1.dp),
    ) { Text(word, style = serif(size, Lex.colors.foreground)) }
}

@Composable
private fun Pill(label: String, icon: ImageVector?, bg: Color, fg: Color, line: Color?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50)).background(bg)
            .then(if (line != null) Modifier.border(1.5.dp, line, RoundedCornerShape(50)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

// ---- Registration, while it is outstanding --------------------------------------

/** The one-time registration that opens the door, with what a consultation costs. */
@Composable
private fun Registration(c: ChamberState, ink: ChamberInk, onRegister: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Lex.colors.card)
            .border(1.5.dp, ink.gold, RoundedCornerShape(28.dp)).padding(20.dp),
    ) {
        Text("Registering opens the door.", style = serif(26, Lex.colors.foreground))
        Spacer(Modifier.height(8.dp))
        Text("Registration is once. A consultation is charged only after an advocate has read what you wrote and agreed to see you.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
        Spacer(Modifier.height(14.dp))
        Figures(listOf(
            Figure("₹${"%,d".format(c.onboardingFee.toLong())}", null, "Registration, once"),
            Figure("₹${"%,d".format(c.clientSessionFee.toLong())}", null, "Each consultation, once accepted"),
        ), ink)
        Spacer(Modifier.height(14.dp))
        Pill("Register And Enter", Icons.AutoMirrored.Outlined.ArrowForward, ink.gold, ink.onGold, null, onRegister, Modifier.fillMaxWidth())
    }
}

// ---- A session already in flight -----------------------------------------------

@Composable
private fun InSession(ink: ChamberInk, onGo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Lex.colors.card)
            .border(1.5.dp, ink.gold, RoundedCornerShape(28.dp)).padding(22.dp),
    ) {
        Text("You are in the chamber.", style = serif(28, Lex.colors.foreground))
        Spacer(Modifier.height(8.dp))
        Text("You have already knocked. What you wrote, the advocate's answer and your Google Meet link are on the session.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
        Spacer(Modifier.height(16.dp))
        Pill("Go To My Session", Icons.AutoMirrored.Outlined.ArrowForward, ink.gold, ink.onGold, null, onGo, Modifier.fillMaxWidth())
    }
}

// ---- The desk -----------------------------------------------------------------

@Composable
private fun Desk(
    c: ChamberState,
    open: Boolean,
    clock: Clock,
    ink: ChamberInk,
    onWalkIn: (Int?) -> Unit,
    onOpenLanding: (Int?) -> Unit,
    onAskAdvocate: () -> Unit,
    onHow: () -> Unit,
) {
    val fg = Lex.colors.foreground
    val muted = Lex.colors.mutedForeground
    val roster = if (open) c.roster else emptyList()
    // The first advocate free is chosen until the visitor chooses.
    var chosenId by remember { mutableStateOf<Int?>(null) }
    val chosen = roster.firstOrNull { it.staffId == chosenId } ?: roster.firstOrNull { !it.isBusy } ?: roster.firstOrNull()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Lex.colors.card)
            .border(1.dp, Lex.colors.border, RoundedCornerShape(28.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("At the desk.", style = serif(30, fg), modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, ink.line, RoundedCornerShape(50))
                    .clickable(onClick = onHow).padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PlayCircle, null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("How It Works", style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
        Spacer(Modifier.height(12.dp))

        // The door's state, and the tally of who is at the desk.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatePill(open, ink)
            if (roster.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                val freeNow = roster.count { !it.isBusy }
                val waiting = roster.sumOf { it.queueCount }
                Text(
                    buildAnnotatedString {
                        fun part(n: Int, label: String) {
                            withStyle(SpanStyle(fontFamily = Fraunces, fontSize = 18.sp, color = fg)) { append("$n") }
                            append(" $label")
                        }
                        part(roster.size, "at the desk"); append("   ")
                        part(freeNow, "free now")
                        if (waiting > 0) { append("   "); part(waiting, "waiting") }
                    },
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        CallPanel(
            a = chosen,
            shutLine = clockSay(c.sitting, clock, roster.isEmpty()),
            onKnock = { chosen?.let { onWalkIn(it.staffId) } },
            onWait = { chosen?.let { onOpenLanding(it.staffId) } },
        )
        if (chosen == null) {
            TextButton(onClick = onAskAdvocate, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)) {
                Text("Ask Your Advocate Instead", style = MaterialTheme.typography.labelLarge, color = fg)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = fg, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        roster.forEach { a ->
            AdvocateCard(a, selected = a.staffId == chosen?.staffId) { chosenId = a.staffId }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            if (roster.isEmpty()) "Advocates appear here only while they are at the desk. When one is, choose them to see them in the call."
            else if (roster.size > 1) "Advocates appear here only while they are at the desk. Choose one to see them in the call."
            else "Advocates appear here only while they are at the desk.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        Spacer(Modifier.height(14.dp))

        SittingClock(c.sitting, clock, roster.isEmpty(), ink)
        Register(c, ink)
    }
}

/** A pulsing pip: the page's "live" mark. */
@Composable
private fun Pip(color: Color, live: Boolean) {
    val t = rememberInfiniteTransition(label = "pip")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pip-p")
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (live) Box(Modifier.size(16.dp).scale(0.5f + p).alpha((1f - p) * 0.5f).clip(CircleShape).background(color))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun StatePill(open: Boolean, ink: ChamberInk) {
    Row(
        Modifier.border(1.5.dp, if (open) Free.copy(alpha = 0.55f) else ink.line, RoundedCornerShape(50))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pip(if (open) Free else Lex.colors.faint, live = open)
        Spacer(Modifier.width(6.dp))
        Text(if (open) "CHAMBER OPEN" else "CHAMBER SHUT", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = Lex.colors.foreground)
    }
}

/** The sitting clock: the hours, how long is left or to go, and the week. */
@Composable
private fun SittingClock(h: Sitting, c: Clock, empty: Boolean, ink: ChamberInk) {
    val fg = Lex.colors.foreground
    val muted = Lex.colors.mutedForeground
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink.wash)
            .border(1.dp, Lex.colors.border, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text(clockSay(h, c, empty), style = MaterialTheme.typography.titleSmall, color = fg)
        Spacer(Modifier.height(2.dp))
        Text(hoursLine(h), style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(16.dp))

        // The bar: the sitting so far, and a pulsing dot for now.
        val track = fg.copy(alpha = 0.10f)
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(track))
            if (c.share > 0f) {
                Box(Modifier.fillMaxWidth(c.share.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(ink.gold))
            }
            if (c.phase == "during") {
                val x = (maxWidth - 14.dp) * c.share
                Box(Modifier.offset(x = x)) { NowDot(ink) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(clockTime(h.from), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text(clockTime(h.to), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Spacer(Modifier.height(12.dp))

        // The week: today filled, the days gone faded, days off dashed.
        val dash = Lex.colors.faint
        val line = ink.line
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, letter ->
                val d = i + 1
                val today = d == c.today
                val sits = d in h.days
                Box(
                    Modifier.size(28.dp).alpha(if (d < c.today) 0.45f else 1f).clip(CircleShape)
                        .background(if (today) ink.gold else Color.Transparent)
                        .drawBehind {
                            if (!today) drawRoundRect(
                                color = if (sits) line else dash,
                                cornerRadius = CornerRadius(size.minDimension / 2),
                                style = Stroke(width = 1.5.dp.toPx(), pathEffect = if (sits) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (today) ink.onGold else if (sits) fg else dash)
                }
            }
        }
    }
}

@Composable
private fun NowDot(ink: ChamberInk) {
    val t = rememberInfiniteTransition(label = "now")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "now-p")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp).scale(0.8f + p * 0.9f).alpha((1f - p) * 0.4f).clip(CircleShape).background(ink.gold))
        Box(Modifier.size(14.dp).clip(CircleShape).background(Lex.colors.card).padding(2.5.dp).clip(CircleShape).background(ink.gold))
    }
}

/** An advocate at the desk: portrait ringed green when free, amber when busy. */
@Composable
private fun AdvocateCard(a: ChamberAdvocate, selected: Boolean, onClick: () -> Unit) {
    val fg = Lex.colors.foreground
    val ring = if (a.isBusy) Busy else Free
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) fg else Lex.colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).border(2.dp, ring, CircleShape).padding(3.dp).clip(CircleShape).background(Lex.colors.muted),
            contentAlignment = Alignment.Center) {
            if (a.avatarUrl != null) {
                AsyncImage(model = a.avatarUrl, contentDescription = a.name, contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.5f), modifier = Modifier.fillMaxSize())
            } else {
                Text(initials(a), style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Lex.colors.mutedForeground)) { append("Adv. ") }
                    append(fullName(a))
                },
                style = serif(20, fg), maxLines = 1,
            )
            a.designation?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(ring))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        !a.isBusy -> "Free now"
                        a.queueCount > 0 -> "In session · ${a.queueCount} waiting"
                        else -> "In session"
                    },
                    style = MaterialTheme.typography.labelMedium, color = Lex.colors.secondaryForeground,
                )
            }
        }
    }
}

private fun fullName(a: ChamberAdvocate) =
    "${a.firstName} ${a.lastName}".trim().ifBlank { a.name.removePrefix("Adv. ") }

private fun initials(a: ChamberAdvocate) =
    ((a.firstName.take(1) + a.lastName.take(1)).ifBlank { fullName(a).take(1) }).uppercase()

/** A figure in its own box: the value large, its unit small, what it counts beneath. */
private data class Figure(val value: String, val unit: String?, val label: String)

@Composable
private fun Figures(items: List<Figure>, ink: ChamberInk) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { f ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(ink.wash)
                    .border(1.dp, Lex.colors.border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        append(f.value)
                        f.unit?.let { withStyle(SpanStyle(fontFamily = Inter, fontSize = 13.sp, color = Lex.colors.mutedForeground)) { append(" $it") } }
                    },
                    style = serif(26, Lex.colors.foreground), maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(f.label, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = Lex.colors.mutedForeground)
            }
        }
    }
}

/** From the chamber's register: counts only, each shown once there are enough. */
@Composable
private fun Register(c: ChamberState, ink: ChamberInk) {
    val r = c.register
    val figures = buildList {
        r.held?.let { add(Figure("$it", null, "Consultations held" + (r.since?.let { s -> ", since $s" } ?: ""))) }
        r.replyMin?.let { add(Figure("$it", "min", "A typical reply to a knock")) }
        add(Figure("${r.duration}", "min", "Each consultation, on Google Meet"))
    }
    Spacer(Modifier.height(18.dp))
    Text("FROM THE CHAMBER'S REGISTER", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
        color = Lex.colors.mutedForeground)
    Spacer(Modifier.height(10.dp))
    Figures(figures, ink)
}

// ---- The call panel -------------------------------------------------------------

/**
 * Who the visitor would see on Google Meet: the chosen advocate on the stage,
 * "You" in the corner, and Meet's own bar. Knock knocks for the one chosen;
 * busy, it reads In Session and opens their waiting room instead.
 */
@Composable
private fun CallPanel(a: ChamberAdvocate?, shutLine: String, onKnock: () -> Unit, onWait: () -> Unit) {
    val me = rememberSelfView()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MeetFrame).padding(8.dp)) {
        // Portrait while someone is in it; shorter while it stands empty.
        Box(Modifier.fillMaxWidth().aspectRatio(if (a == null) 1.25f else 0.92f).clip(RoundedCornerShape(14.dp)).background(MeetStage)) {
            if (a == null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MeetingRoom, null, tint = MeetMuted, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No one is in the call yet.", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(shutLine, style = MaterialTheme.typography.bodySmall, color = MeetMuted, textAlign = TextAlign.Center)
                }
            } else {
                val photo = a.avatarLarge ?: a.avatarUrl
                if (photo != null) {
                    AsyncImage(model = photo, contentDescription = a.name, contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.5f), modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.align(Alignment.Center).size(120.dp).clip(CircleShape).background(MeetIdle), contentAlignment = Alignment.Center) {
                        Text(initials(a), style = serif(44, Color.White))
                    }
                }
                // Name and designation, bottom left, with Meet's talking bars.
                val shade = Shadow(Color(0x99000000), blurRadius = 8f)
                // A flat plate under it: a white shirt would swallow white text.
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(12.dp))
                        .background(MeetFrame.copy(alpha = 0.72f)).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TalkingBars()
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Adv. ${fullName(a)}", style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium, shadow = shade), color = Color.White)
                        a.designation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, shadow = shade), color = MeetIcon)
                        }
                    }
                }
            }
            // Meet's chip, top left.
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(MeetFrame.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Videocam, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Google Meet", style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Color.White)
            }
            // "You", top right, 3:4: the client's own camera once they turn it on.
            // Not while the chamber is shut, as on the Legum Lex page.
            if (a != null) Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).width(84.dp).aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp)).background(MeetIdle).border(1.5.dp, Color.White, RoundedCornerShape(10.dp))
                    .clickable(enabled = !me.camOn, onClick = me.toggleCam),
            ) {
                if (me.camOn) {
                    CameraSelf(Modifier.fillMaxSize())
                } else {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VideocamOff, null, tint = MeetIcon, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to see yourself", style = TextStyle(fontFamily = Inter, fontSize = 9.5.sp, lineHeight = 12.sp),
                            color = MeetMuted, textAlign = TextAlign.Center)
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("You", style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        shadow = Shadow(Color(0x99000000), blurRadius = 6f)), color = Color.White)
                }
                // The microphone: Meet's red slash when off, its level when on.
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape)
                    .background(if (me.micOn) MeetBlue else MeetRed), contentAlignment = Alignment.Center) {
                    if (me.micOn) Level(me.level, 10.dp)
                    else Icon(Icons.Outlined.MicOff, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }

        // Meet's bar.
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic and camera are the client's own, and work; captions are Meet's, shown for the likeness.
            Toggle(if (me.micOn) Icons.Outlined.Mic else Icons.Outlined.MicOff, me.micOn, if (me.micOn) "Turn off microphone" else "Turn on microphone", me.toggleMic)
            Toggle(if (me.camOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff, me.camOn, if (me.camOn) "Turn off camera" else "Turn on camera", me.toggleCam)
            Box(Modifier.size(38.dp).clip(CircleShape).background(MeetStage), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ClosedCaption, null, tint = MeetIcon, modifier = Modifier.size(18.dp))
            }
            val busy = a?.isBusy == true
            val dim = if (a == null) 0.6f else 1f
            Row(
                Modifier.height(38.dp).clip(RoundedCornerShape(50))
                    .background(if (a == null) MeetIdle.copy(alpha = 0.6f) else if (busy) MeetIdle else MeetGreen)
                    .clickable(enabled = a != null) { if (busy) onWait() else onKnock() }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Call, null, tint = Color.White.copy(alpha = dim), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (busy) "In Session" else "Knock", style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = dim))
            }
        }
    }
}

/** A bar button: grey when on, Meet's red when off. */
@Composable
private fun Toggle(icon: ImageVector, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (on) MeetStage else MeetRed).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) MeetIcon else Color.White, modifier = Modifier.size(18.dp)) }
}

/** The client's own microphone level: three bars that follow their voice. */
@Composable
private fun Level(level: Float, height: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.6f, 1f, 0.6f).forEach { k ->
            Box(Modifier.width(2.dp).height(height * (0.25f + 0.75f * (level * k).coerceIn(0f, 1f) * 1.6f).coerceIn(0.25f, 1f))
                .clip(RoundedCornerShape(1.dp)).background(Color.White))
        }
    }
}

/** Meet's speaking indicator: three bars in a blue disc. */
@Composable
private fun TalkingBars() {
    val t = rememberInfiniteTransition(label = "talk")
    val hs = listOf(0, 180, 360).map { d ->
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(520, delayMillis = d), RepeatMode.Reverse), label = "bar$d")
    }
    Box(Modifier.size(24.dp).clip(CircleShape).background(MeetBlue), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            hs.forEach { h ->
                Box(Modifier.width(2.5.dp).height(12.dp * h.value).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
    }
}

// ---- How it works -----------------------------------------------------------------

@Composable
private fun HowItWorks(ink: ChamberInk, onToDesk: () -> Unit) {
    val fg = Lex.colors.foreground
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding()) {
        Text("Four steps", style = serif(34, fg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("& face to", style = serif(34, fg))
            Spacer(Modifier.width(6.dp))
            PillWord("face.", 34, ink)
        }
        Spacer(Modifier.height(10.dp))
        Text("Nothing is charged until an advocate has read what you wrote and agreed to see you.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
        Spacer(Modifier.height(18.dp))
        listOf(
            Step(Icons.AutoMirrored.Outlined.Login, "Knock", "you are signed in",
                "Choose an advocate who is at the desk. You are already signed in, so they know who is asking."),
            Step(Icons.Outlined.EditNote, "Say what has happened", "your words",
                "In your own words. The advocate reads it before deciding to take the call."),
            Step(Icons.Outlined.ReceiptLong, "See the fee, and confirm", "before you pay",
                "The fee for the consultation is shown here, before you pay, and only once the advocate has agreed."),
            Step(Icons.Outlined.Videocam, "Face to face", "Google Meet",
                "The consultation opens in Google Meet, with the advocate who read your words, in the Meet app on your phone."),
        ).forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = Lex.colors.border, modifier = Modifier.padding(vertical = 14.dp))
            Row {
                Text("%02d".format(i + 1), style = serif(24, Lex.colors.faint), modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.icon, null, tint = fg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.title, style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(s.chip, style = MaterialTheme.typography.labelSmall, color = Lex.colors.secondaryForeground,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Lex.colors.muted).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.text, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Pill("Back To The Desk", Icons.AutoMirrored.Outlined.ArrowForward, ink.gold, ink.onGold, null, onToDesk, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

private data class Step(val icon: ImageVector, val title: String, val chip: String, val text: String)
