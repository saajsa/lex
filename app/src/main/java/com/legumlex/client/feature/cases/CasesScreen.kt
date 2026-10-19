package com.legumlex.client.feature.cases

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Matters List.html" (Plot Design). The litigation register — heavier,
// more formal sibling of the Consultations list. The next hearing leads: the
// most imminent hearing across all matters is pulled into a spotlight, then the
// rest band into Hearing-this-week / Active / Closed. A matter is advocate-run;
// the client reads, opens, and watches the next date. App-wide centred-logo
// header + bottom nav are provided by MainActivity.
// -----------------------------------------------------------------------------

// LlColor.ClaretSoft / ClaretMid are broken placeholders; use the real tints.
private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretBorderFix = Color(0xFFF4D9DF)
private val AmberBorderFix = Color(0xFFF3E3C4)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CasesScreen(onOpenCase: (String) -> Unit) {
    val vm: CasesViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    var filter by rememberSaveable { mutableStateOf(CaseFilter.ALL) }

    Scaffold(
        containerColor = LlColor.Paper,
        // The app-wide header already consumes the status-bar inset; without this
        // the nested Scaffold re-adds it as a fixed empty band under the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is CasesUiState.Loading -> CasesSkeleton()
                is CasesUiState.Error   -> CasesError(s.message, vm::retry)
                is CasesUiState.Ready   -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = vm::refresh,
                    modifier     = Modifier.fillMaxSize(),
                ) {
                    CasesReady(s.cases, filter, { filter = it }, onOpenCase)
                }
            }
        }
    }
}

// =============================================================================
// Banding
// =============================================================================

private fun CaseSummary.isClosedCase(): Boolean =
    status == CaseStatus.SETTLED || status == CaseStatus.CLOSED

private fun CaseSummary.upcomingHearingDays(today: LocalDate): Long? {
    val d = nextHearingDate ?: return null
    if (d.isBefore(today)) return null
    return ChronoUnit.DAYS.between(today, d)
}

private data class Band(val name: String, val hearing: Boolean, val items: List<CaseSummary>)

@Composable
private fun CasesReady(
    cases: List<CaseSummary>,
    filter: CaseFilter,
    onFilter: (CaseFilter) -> Unit,
    onOpenCase: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val active = remember(cases) { cases.filterNot { it.isClosedCase() } }
    val closed = remember(cases) { cases.filter { it.isClosedCase() } }

    val hearingWeek = remember(active, today) {
        active.filter { (it.upcomingHearingDays(today) ?: Long.MAX_VALUE) <= 7 }
            .sortedBy { it.nextHearingDate }
    }
    val activeRest = remember(active, hearingWeek) {
        (active - hearingWeek.toSet()).sortedByDescending { it.lastActivityAt }
    }
    // Spotlight: nearest upcoming hearing across all active matters.
    val spotlight = remember(active, today) {
        active.filter { it.upcomingHearingDays(today) != null }.minByOrNull { it.nextHearingDate!! }
    }

    val bands = remember(filter, hearingWeek, activeRest, closed) {
        when (filter) {
            CaseFilter.ALL -> listOf(
                Band("Hearing this week", true, hearingWeek),
                Band("Active", false, activeRest),
                Band("Closed", false, closed),
            )
            CaseFilter.ACTIVE -> listOf(
                Band("Hearing this week", true, hearingWeek),
                Band("Active", false, activeRest),
            )
            CaseFilter.CLOSED -> listOf(Band("Closed", false, closed))
        }.filter { it.items.isNotEmpty() }
    }

    if (cases.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("intro") { PageIntro(filter, active.size, closed.size, cases.size, showCount = false) }
            item("empty") { CasesEmpty() }
        }
        return
    }

    val showSpotlight = filter != CaseFilter.CLOSED && spotlight != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("intro") { PageIntro(filter, active.size, closed.size, cases.size, showCount = true) }

        if (showSpotlight) {
            item("spotlight") {
                Spotlight(spotlight!!, today) { onOpenCase(spotlight.id) }
            }
        }

        item("filters") {
            FilterBar(all = cases.size, active = active.size, closed = closed.size, selected = filter, onSelect = onFilter)
        }

        if (bands.isEmpty()) {
            item("empty-filter") { FilteredEmpty(filter) }
        } else {
            bands.forEachIndexed { bi, band ->
                item("head-${band.name}") { SectionHeader(band.name, band.items.size, band.hearing, first = bi == 0) }
                items(band.items, key = { it.id }) { c ->
                    Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, bottom = 10.dp)) {
                        MatterRow(c, today, band.hearing) { onOpenCase(c.id) }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Page intro
// =============================================================================

@Composable
private fun PageIntro(filter: CaseFilter, active: Int, closed: Int, total: Int, showCount: Boolean) {
    val sub = when (filter) {
        CaseFilter.ALL -> "Your active litigation and closed matters."
        CaseFilter.ACTIVE -> "The cases still in front of the courts."
        CaseFilter.CLOSED -> "Cases your chamber has closed out."
    }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
        Text("MATTERS", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.16.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(9.dp))
        Text("Your Cases", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, letterSpacing = (-0.025).em, lineHeight = 30.sp, color = LlColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(sub, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 20.sp, color = LlColor.InkMuted, modifier = Modifier.widthIn(max = 330.dp))
        if (showCount) {
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (filter) {
                    CaseFilter.CLOSED -> CountFrag(closed, "closed")
                    CaseFilter.ACTIVE -> CountFrag(active, "active")
                    else -> {
                        CountFrag(active, "active")
                        Text("·", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.BorderStrong)
                        CountFrag(total, "total")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountFrag(n: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$n ", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
        Text(label, fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
    }
}

// =============================================================================
// Next-hearing spotlight
// =============================================================================

@Composable
private fun Spotlight(c: CaseSummary, today: LocalDate, onClick: () -> Unit) {
    val days = c.upcomingHearingDays(today) ?: 0L
    val soon = days <= 3
    val band = if (soon) ClaretMidFix else LlColor.AmberMid
    val boxBg = if (soon) ClaretSoftFix else LlColor.AmberSoft
    val boxBorder = if (soon) ClaretBorderFix else AmberBorderFix
    val accent = if (soon) LlColor.Claret else LlColor.Amber
    val accentMid = if (soon) ClaretMidFix else LlColor.AmberMid

    val dateLabel = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }
    val eyebrow = if (days == 1L) "Next hearing · Tomorrow" else "Next hearing"

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp).background(band))
        Column(Modifier.padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (soon) PulseDot(accentMid, 7.dp) else Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accentMid))
                Text(eyebrow.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = accent)
            }
            Spacer(Modifier.height(11.dp))
            Text(com.legumlex.client.ui.caseTitleAnnotated(c.title, LlColor.InkFaint),
                fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp, letterSpacing = (-0.015).em, lineHeight = 24.sp, color = LlColor.Ink)
            c.nextHearingPurpose?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Gavel, null, tint = accent, modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text("For $it", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, color = accent, modifier = Modifier.weight(1f))
                }
            }
            c.courtName?.let {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(
                        it + (c.courtNo?.let { n -> " · Ct. $n" } ?: ""),
                        fontFamily = SourceSans, fontSize = 11.sp, lineHeight = 15.sp, color = LlColor.InkFaint, modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // countdown chip
                Column(
                    Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(11.dp)).background(boxBg)
                        .border(LlDimen.HairlineWidth, boxBorder, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (days == 0L) {
                        Text("Today", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = accent)
                    } else {
                        Text("$days", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = accent)
                        Text(if (days == 1L) "DAY" else "DAYS", fontFamily = SourceSans, fontSize = 8.sp, letterSpacing = 0.12.em, color = accentMid, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("$dateLabel · ${c.nextHearingDate!!.format(dowFmt)}",
                        fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 17.sp, color = LlColor.Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(spotlightMeta(c), fontFamily = SourceSans, fontSize = 11.sp, lineHeight = 15.sp, color = LlColor.InkMuted)
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(LlColor.Paper1),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LlColor.InkMuted, modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

private fun spotlightMeta(c: CaseSummary): String = buildList {
    c.nextHearingTime?.let { add("${it.format(timeFmt)} IST") }
    com.legumlex.client.ui.formatJudge(c.judgeName, c.courtName)?.let { add("before $it") }
}.joinToString(" · ").ifEmpty { "Hearing scheduled" }

// =============================================================================
// Filter bar
// =============================================================================

@Composable
private fun FilterBar(all: Int, active: Int, closed: Int, selected: CaseFilter, onSelect: (CaseFilter) -> Unit) {
    val counts = mapOf(CaseFilter.ALL to all, CaseFilter.ACTIVE to active, CaseFilter.CLOSED to closed)
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CaseFilter.entries.forEach { f ->
                FilterChip(f.label, counts[f] ?: 0, selected == f) { onSelect(f) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) LlColor.Ink else LlColor.Paper)
            .border(LlDimen.HairlineWidth, if (selected) LlColor.Ink else LlColor.Border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) LlColor.Paper else LlColor.InkMuted)
        if (count > 0) {
            Box(
                Modifier.heightIn(min = 17.dp).widthIn(min = 17.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) LlColor.Paper.copy(alpha = 0.2f) else LlColor.Paper2).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                    color = if (selected) LlColor.Paper else LlColor.InkMuted)
            }
        }
    }
}

// =============================================================================
// Section header
// =============================================================================

@Composable
private fun SectionHeader(name: String, count: Int, hearing: Boolean, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (first) 16.dp else 18.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.5.sp, letterSpacing = 0.15.em,
            color = if (hearing) LlColor.Claret else LlColor.InkFaint)
        Box(
            Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp))
                .background(if (hearing) ClaretSoftFix else LlColor.Paper2).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = if (hearing) ClaretMidFix else LlColor.InkMuted)
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

// =============================================================================
// Matter row
// =============================================================================

private enum class HearTone { IMMINENT, WEEK, NORMAL, QUIET }

@Composable
private fun MatterRow(c: CaseSummary, today: LocalDate, hearingBand: Boolean, onClick: () -> Unit) {
    val done = c.isClosedCase()
    Box(
        Modifier.fillMaxWidth().clip(LlShape.Card)
            .background(if (done) LlColor.Paper1 else LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .clickable(onClick = onClick),
    ) {
        if (hearingBand) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(LlDimen.HeroStripeWidth).background(LlColor.Claret))
        Column(Modifier.padding(start = if (hearingBand) 18.dp else 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                // weight(1f) so a long case number wraps onto extra lines instead
                // of pushing the status pill off the card / truncating.
                Text(c.number.ifBlank { "—" }, fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 0.1.em,
                    color = LlColor.InkFaint, modifier = Modifier.weight(1f).padding(top = 3.dp))
                StatusPill(c.status)
            }
            Text(com.legumlex.client.ui.caseTitleAnnotated(c.title, LlColor.InkFaint),
                fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 21.sp,
                color = if (done) LlColor.InkMuted else LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 11.dp))
            // sub: where — court · courtroom (the number already sits top-left)
            val whereText = listOfNotNull(c.courtName, c.courtNo?.let { "Ct. $it" }).joinToString(" · ")
            if (whereText.isNotBlank()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = LlColor.InkFaint, modifier = Modifier.size(12.dp).padding(top = 1.dp))
                    // weight(1f) + no maxLines: a long court · courtroom string wraps
                    // to a second line inside the card rather than being ellipsised.
                    Text(whereText, fontFamily = SourceSans, fontSize = 10.5.sp, lineHeight = 15.sp, color = LlColor.InkFaint, modifier = Modifier.weight(1f))
                }
            }
            HearingLine(c, today, done)
            HistoryFooter(c)
        }
    }
}

// A consistent context footer on every card: how old the matter is and how
// many times it has been before the court — distinct from the "what's next" line.
@Composable
private fun HistoryFooter(c: CaseSummary) {
    val parts = buildList {
        c.filedDate?.let { add("Filed ${it.format(monthYearFmt)}") }
        if (c.hearingCount > 0) add("${c.hearingCount} hearing${if (c.hearingCount == 1) "" else "s"}")
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint,
        modifier = Modifier.padding(top = 9.dp),
    )
}

@Composable
private fun HearingLine(c: CaseSummary, today: LocalDate, done: Boolean) {
    val days = c.upcomingHearingDays(today)
    val tone = when {
        done -> HearTone.QUIET
        days == null -> HearTone.QUIET
        days <= 3 -> HearTone.IMMINENT
        days <= 7 -> HearTone.WEEK
        else -> HearTone.NORMAL
    }
    val (icon, text, color, filled) = when (tone) {
        HearTone.IMMINENT -> Quad(
            Icons.Filled.Event,
            if (days == 0L) "Today · ${hearingWhen(c)}" else if (days == 1L) "Tomorrow · ${hearingWhen(c)}" else "Next hearing · ${hearingWhen(c)}",
            ClaretMidFix, true,
        )
        HearTone.WEEK -> Quad(Icons.Filled.Event, "Next hearing · ${hearingWhen(c)}", LlColor.AmberMid, true)
        HearTone.NORMAL -> Quad(Icons.Filled.Event, "Next hearing · ${hearingWhen(c)}", LlColor.Ink, false)
        HearTone.QUIET -> {
            if (done) {
                val (qi, qt) = closedGlance(c)
                Quad(qi, qt, LlColor.InkFaint, false)
            } else {
                Quad(Icons.Outlined.History, quietActive(c), LlColor.InkFaint, false)
            }
        }
    }

    Column(Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pa = pulseAlpha()
            val iconAlpha = if (tone == HearTone.IMMINENT) pa else 1f
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).alpha(iconAlpha))
            // weight(1f) so the hearing line (date · time · purpose) wraps onto a
            // second line within the card instead of overflowing horizontally.
            Text(
                text,
                fontFamily = if (tone == HearTone.NORMAL || tone == HearTone.QUIET) SourceSans else SourceSans,
                fontWeight = if (tone == HearTone.QUIET) FontWeight.Normal else FontWeight.SemiBold,
                fontSize = if (tone == HearTone.QUIET || tone == HearTone.NORMAL) 11.sp else 11.5.sp,
                color = color, lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class Quad(val icon: ImageVector, val text: String, val color: Color, val filled: Boolean)

private fun hearingWhen(c: CaseSummary): String {
    val d = c.nextHearingDate ?: return ""
    val base = c.nextHearingTime?.let { "${d.format(dowFmt)} · ${it.format(timeFmt)}" } ?: d.format(dowFmt)
    // The purpose turns a bare date into meaning — "24 Jun · 11:00 AM · Admission".
    return c.nextHearingPurpose?.let { "$base · $it" } ?: base
}

private fun quietActive(c: CaseSummary): String =
    c.lastActivityAt?.let { "Awaiting next date · updated ${relativeAgo(it)}" } ?: "Awaiting next hearing date"

private fun closedGlance(c: CaseSummary): Pair<ImageVector, String> {
    // Prefer the real disposition (how the matter ended) over generic activity.
    val date = (c.disposalDate ?: c.lastActivityAt?.toLocalDate())?.format(dayFmt)
    val label = c.disposalType?.takeIf { it.isNotBlank() } ?: c.status.label
    val settled = c.status == CaseStatus.SETTLED || label.equals("settled", ignoreCase = true)
    val icon = if (settled) Icons.Outlined.CheckCircle else Icons.Outlined.Gavel
    return icon to listOfNotNull(label, date).joinToString(" · ")
}

@Composable
private fun StatusPill(status: CaseStatus) {
    val (bg, fg) = when (status) {
        CaseStatus.ONGOING -> LlColor.SlateSoft to LlColor.SlateMid
        CaseStatus.SETTLED -> LlColor.VerdantSoft to LlColor.Verdant
        CaseStatus.PENDING -> LlColor.AmberSoft to LlColor.Amber
        CaseStatus.CLOSED, CaseStatus.UNKNOWN -> LlColor.Paper2 to LlColor.InkMuted
    }
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(status.label, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = fg)
    }
}

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).alpha(pulseAlpha()).clip(RoundedCornerShape(50)).background(color))
}

@Composable
private fun pulseAlpha(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulseAlpha",
    )
    return a
}

// =============================================================================
// Empty / loading / error
// =============================================================================

@Composable
private fun CasesEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.SlateSoft)
                .border(LlDimen.HairlineWidth, Color(0xFFD6E3F3), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Gavel, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("No cases filed yet", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "When your advocate opens a case for you, it'll appear here — with its court, judge and every hearing date kept up to date.",
            fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(11.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Forum, null, tint = LlColor.InkFaint, modifier = Modifier.size(16.dp))
            Text("Have a question? Raise it in Consultations", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
        }
    }
}

@Composable
private fun FilteredEmpty(filter: CaseFilter) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FolderOpen, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            when (filter) {
                CaseFilter.ACTIVE -> "No active matters"
                CaseFilter.CLOSED -> "No closed matters"
                else -> "No matters"
            },
            fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text("Nothing in this view right now.", fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CasesSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
            Sk(74.dp, 11.dp); Spacer(Modifier.height(11.dp)); Sk(172.dp, 28.dp, 7.dp); Spacer(Modifier.height(11.dp)); Sk(228.dp, 13.dp)
        }
        // spotlight skeleton
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp)).padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 17.dp),
        ) {
            Sk(88.dp, 11.dp); Spacer(Modifier.height(13.dp)); Sk(modifier = Modifier.fillMaxWidth(0.82f), height = 18.dp); Spacer(Modifier.height(9.dp)); Sk(modifier = Modifier.fillMaxWidth(0.58f), height = 11.dp)
            Spacer(Modifier.height(14.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2)); Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sk(52.dp, 44.dp, 11.dp)
                Column(Modifier.weight(1f)) { Sk(modifier = Modifier.fillMaxWidth(0.6f), height = 13.dp); Spacer(Modifier.height(7.dp)); Sk(modifier = Modifier.fillMaxWidth(0.74f), height = 11.dp) }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(66, 82, 82).forEach { Sk(it.dp, 34.dp, 999.dp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).padding(bottom = 10.dp)
                    .clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Sk(70.dp, 14.dp); Sk(80.dp, 18.dp, 7.dp) }
                Spacer(Modifier.height(12.dp)); Sk(modifier = Modifier.fillMaxWidth(0.82f), height = 16.dp)
                Spacer(Modifier.height(9.dp)); Sk(modifier = Modifier.fillMaxWidth(0.5f), height = 11.dp)
            }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun CasesError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load your matters", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val monthYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

private fun relativeAgo(ts: LocalDateTime): String {
    val mins = ChronoUnit.MINUTES.between(ts, LocalDateTime.now())
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d ago"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24 * 7)}w ago"
        else -> ts.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
