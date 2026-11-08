package com.legumlex.client.feature.board

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.feature.consultations.apiError
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// -----------------------------------------------------------------------------
// The Counsel Home (REQ-008 round 4, canvas 7f and 7e): who the counsel is (a card that folds), the
// specialty ring (one arc for each kind of case, tap one to see it), the milestone bars, and four tiles.
// Everything is read from board/home; the server counts it all.
// -----------------------------------------------------------------------------

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val home: CounselHome) : HomeState
    data class Error(val message: String, val offline: Boolean) : HomeState
}

class CounselHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BoardRepository(app)
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (_state.value !is HomeState.Ready) _state.value = HomeState.Loading
            repo.home()
                .onSuccess { _state.value = HomeState.Ready(it) }
                .onFailure { e -> if (_state.value !is HomeState.Ready) _state.value = HomeState.Error(e.boardMessage(), e.isOffline()) }
        }
    }
}

/** True when the system's animator duration scale is 0 (the phone asks for reduced motion): nothing moves. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

private fun detailsPrefs(context: Context) = context.getSharedPreferences("counsel_home", Context.MODE_PRIVATE)

@Composable
fun CounselHomeScreen(onOpenCalendar: () -> Unit, onOpenCases: () -> Unit) {
    val vm: CounselHomeViewModel = viewModel()
    val state by vm.state.collectAsState()
    when (val s = state) {
        HomeState.Loading -> Box(Modifier.fillMaxSize().background(Lex.colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
        }
        is HomeState.Error -> Box(Modifier.fillMaxSize().background(Lex.colors.background).padding(16.dp), contentAlignment = Alignment.Center) {
            LexCard {
                LexEmpty(
                    title = if (s.offline) "You Are Offline" else "Home Could Not Be Opened", body = s.message,
                    icon = if (s.offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                    action = { LegumSecondaryButton("Try Again", vm::load) },
                )
            }
        }
        is HomeState.Ready -> HomePage(s.home, onOpenCalendar, onOpenCases, vm::load)
    }
}

@Composable
private fun HomePage(h: CounselHome, onOpenCalendar: () -> Unit, onOpenCases: () -> Unit, onReload: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState())
            // A tap anywhere that nothing else takes puts the ring back at rest.
            .pointerInput(Unit) { detectTapGestures { selected = null } }
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileCard(h.profile, onReload)
        ScoreCard(h, selected) { selected = it }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile("Diary", h.tiles.ongoing.toString(), "ongoing", diaryLine(h.tiles), Modifier.weight(1f).fillMaxHeight(), onOpenCases)
            HomeTile("This Month", h.tiles.hearings.toString(), if (h.tiles.hearings == 1) "hearing" else "hearings", busiestLine(h.tiles), Modifier.weight(1f).fillMaxHeight(), onOpenCalendar)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile("Dates Caught", h.tiles.datesCaught.toString(), if (h.tiles.datesCaught == 1) "change" else "changes", "eCourts moved them; you knew first", Modifier.weight(1f).fillMaxHeight(), null)
            HomeTile("Disposed", h.tiles.disposedThisYear.toString(), "this year", oldestLine(h.tiles), Modifier.weight(1f).fillMaxHeight(), null)
        }
    }
}

// ---- the profile card (7f) -------------------------------------------------------------------

@Composable
private fun ProfileCard(p: HomeProfile, onChambersSaved: () -> Unit) {
    val context = LocalContext.current
    var editChambers by rememberSaveable { mutableStateOf(false) }
    if (editChambers) ChambersWizard(onClose = { editChambers = false }, onSaved = { editChambers = false; onChambersSaved() })
    var open by remember { mutableStateOf(detailsPrefs(context).getBoolean("details_open", true)) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1D1D1B)), contentAlignment = Alignment.Center) {
                Text(p.initials.ifBlank { "AD" }, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = CounselGold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.displayName.ifBlank { "Adv." }, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, color = Lex.colors.foreground)
                barLine(p).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground) }
            }
            p.yearsAtBar?.let { y ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { contentDescription = "$y years at the Bar" }) {
                    Text(y.toString(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 24.sp, color = Lex.colors.foreground)
                    Text("YRS", fontSize = 9.5.sp, letterSpacing = 1.sp, color = Lex.colors.mutedForeground)
                }
            }
            IconAction(
                if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                if (open) "Hide Details" else "Show Details",
                { open = !open; detailsPrefs(context).edit().putBoolean("details_open", open).apply() },
                size = 48.dp,
            )
        }
        if (open) Column(
            Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.material3.HorizontalDivider(color = Lex.colors.border)
            DetailRow(Icons.Outlined.Home, p.chambers, "Chambers") {
                IconAction(Icons.Outlined.Edit, "Edit Chambers", { editChambers = true }, size = 48.dp)
            }
            DetailRow(Icons.Outlined.Email, p.email, "Email")
            DetailRow(Icons.Outlined.Phone, p.mobile, "Mobile")
            DetailRow(Icons.Outlined.LocationOn, courtsLine(p), "Courts And Districts")
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, name: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$name: ${text.ifBlank { "Not given" }}" }, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Lex.colors.secondaryForeground, modifier = Modifier.size(20.dp))
        Text(text.ifBlank { "Not given" }, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

// ---- the specialty score (7e) ----------------------------------------------------------------

@Composable
private fun ScoreCard(h: CounselHome, selected: String?, onSelect: (String?) -> Unit) {
    val sc = h.score
    val arcs = remember(sc.specialties) { ringArcs(sc.specialties) }
    val sel = arcs.firstOrNull { it.key == selected }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Specialty Score", style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
            Text("${h.tiles.ongoing} ongoing ${if (h.tiles.ongoing == 1) "case" else "cases"}", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        }
        SpecialtyRing(sc, arcs, sel, onSelect)
        Legend(sc, selected, onSelect)
        androidx.compose.material3.HorizontalDivider(color = Lex.colors.border)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Milestone("CRIMINAL", sc.criminal, sc.nextCriminal, Color(0xFF1D1D1B), Modifier.weight(1f))
            Milestone("CIVIL", sc.civil, sc.nextCivil, Color(0xFF8A877F), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpecialtyRing(sc: HomeScore, arcs: List<RingArc>, sel: RingArc?, onSelect: (String?) -> Unit) {
    val still = rememberReducedMotion()
    // The ring draws itself in, arc by arc, once on first show.
    val total = if (arcs.isEmpty()) 0f else 1f + 0.12f * (arcs.size - 1)
    val draw = remember { Animatable(if (still) total else 0f) }
    LaunchedEffect(arcs.size, still) {
        if (still) draw.snapTo(total) else if (draw.value < total) draw.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing))
    }
    val lead = if (sc.lead == "civil") "civil" else "criminal"
    val leadScore = if (lead == "civil") sc.civil else sc.criminal
    val otherScore = if (lead == "civil") sc.criminal else sc.civil
    val target = sel?.count?.toDouble() ?: leadScore
    // The centre counts up to the chosen arc's count (400 ms), or back to the lead score.
    val shown by animateFloatAsState(target.toFloat(), tween(if (still) 0 else 400), label = "ringCount")
    val big = if (target == Math.floor(target)) shown.roundToInt().toString() else scoreText(shown.toDouble())

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).padding(top = 14.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(arcs) {
                    detectTapGestures { off ->
                        val scale = size.width / 340f
                        val hit = arcAt(arcs, (off.x - size.width / 2f) / scale, (off.y - size.height / 2f) / scale, 128f, 22f)
                        onSelect(hit?.key)
                    }
                },
            ) {
                val scale = size.width / 340f
                val r = 128f * scale
                val c = Offset(size.width / 2f, size.height / 2f)
                if (arcs.isEmpty()) {
                    drawCircle(Color(0xFFDCD9D2), radius = r, center = c, style = Stroke(30f * scale))
                }
                arcs.forEachIndexed { i, a ->
                    val frac = ((draw.value - 0.12f * i) / 1f).coerceIn(0f, 1f)
                    if (frac <= 0f) return@forEachIndexed
                    val on = sel?.key == a.key
                    val w = (if (on) 34f else 30f) * scale
                    drawArc(
                        color = Color(SpecialtyColours[a.key] ?: 0xFFBDB9B0).copy(alpha = if (sel != null && !on) 0.25f else 1f),
                        startAngle = -90f + a.start, sweepAngle = a.sweep * frac, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = Stroke(width = w),
                    )
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (sel != null) sideCaption(sel.side) else lead.uppercase(),
                    fontSize = 11.sp, letterSpacing = 1.5.sp, color = Lex.colors.mutedForeground,
                )
                Text(big, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 60.sp, color = Lex.colors.foreground)
                Text(
                    sel?.label ?: "${if (lead == "civil") "criminal" else "civil"} ${scoreText(otherScore)}",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground, textAlign = TextAlign.Center,
                )
            }
        }
        // The tooltip for the chosen arc.
        sel?.let { a ->
            val sp = sc.specialties.first { it.key == a.key }
            Text(
                sliceWord(sp), fontSize = 12.sp, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF111111)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Legend(sc: HomeScore, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("criminal", "civil").forEach { side ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sc.specialties.filter { it.side == side }.forEach { sp -> LegendRow(sp, sp.label, sp.key, false, selected, Modifier, onSelect) }
                }
            }
        }
        sc.specialties.filter { it.side == "both" }.forEach { sp ->
            LegendRow(sp, "NI Act s.138 · ½ each side", "ni", false, selected, Modifier.fillMaxWidth(), onSelect)
        }
        sc.specialties.filter { it.side == "none" && it.count >= 1 }.forEach { sp ->
            LegendRow(sp, "Not known, scores for neither side", "unknown", true, selected, Modifier.fillMaxWidth(), onSelect)
        }
    }
}

@Composable
private fun LegendRow(sp: Specialty, label: String, colourKey: String, muted: Boolean, selected: String?, modifier: Modifier, onSelect: (String?) -> Unit) {
    val on = selected == sp.key
    // A specialty with no case has no arc to choose.
    val clickable = sp.count > 0
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (on) Color.Black.copy(alpha = 0.06f) else Color.Transparent)
            .alpha(if (selected != null && !on) 0.4f else 1f)
            .then(if (clickable) Modifier.clickable { onSelect(if (on) null else sp.key) } else Modifier)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(SpecialtyColours[colourKey] ?: 0xFFBDB9B0)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (muted) Lex.colors.mutedForeground else Lex.colors.foreground, modifier = Modifier.weight(1f))
        Text(sp.count.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Lex.colors.foreground)
    }
}

/** A side's score against its next step: four bars (10, 25, 50, 100), the steps reached filled. */
@Composable
private fun Milestone(side: String, score: Double, next: Int?, fill: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(side, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Lex.colors.mutedForeground, modifier = Modifier.weight(1f))
            Text(
                scoreText(score) + (next?.let { " / $it" } ?: " · top step"),
                fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Lex.colors.foreground,
            )
        }
        val reached = stepsReached(score)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ScoreSteps.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < reached) fill else Color(0xFFDCD9D2)))
            }
        }
        Row {
            ScoreSteps.forEach { Text(it.toString(), fontSize = 10.5.sp, color = Lex.colors.mutedForeground, modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }
        }
    }
}

@Composable
private fun HomeTile(title: String, number: String, unit: String, line: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
            if (onClick != null) Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open $title", tint = Lex.colors.secondaryForeground, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(number, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 34.sp, color = Lex.colors.foreground)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, minLines = 2)
    }
}
