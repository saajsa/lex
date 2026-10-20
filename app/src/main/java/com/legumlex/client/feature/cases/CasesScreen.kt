package com.legumlex.client.feature.cases

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.text.withStyle
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.PageHeader
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import com.legumlex.client.ahc.theme.Inter
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
private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.Paper1
private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.Ink   // was the claret accent; red is kept for urgency
private val ClaretBorderFix: Color @androidx.compose.runtime.Composable get() = LlColor.Border
private val AmberBorderFix: Color @androidx.compose.runtime.Composable get() = LlColor.AmberBorder

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CasesScreen(onOpenCase: (String) -> Unit, onOpenNotices: () -> Unit = {}) {
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
                    CasesReady(s.cases, filter, { filter = it }, onOpenCase, onOpenNotices)
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

@Composable
private fun CasesReady(
    cases: List<CaseSummary>,
    filter: CaseFilter,
    onFilter: (CaseFilter) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotices: () -> Unit,
) {
    val today = remember { LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")) }
    val active = remember(cases) { cases.filterNot { it.isClosedCase() } }
    val closed = remember(cases) { cases.filter { it.isClosedCase() } }
    val scheduled = remember(active, today) { active.count { it.upcomingHearingDays(today) != null } }
    val shown = remember(filter, cases) {
        when (filter) {
            CaseFilter.ALL -> active.sortedBy { it.nextHearingDate ?: LocalDate.MAX } + closed
            CaseFilter.ACTIVE -> active.sortedBy { it.nextHearingDate ?: LocalDate.MAX }
            CaseFilter.CLOSED -> closed
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") {
            PageHeader(eyebrow = "Litigation", title = "Cases", actions = {
                LexOutlineChip("Notices", Icons.Outlined.MarkEmailUnread, onOpenNotices)
            })
        }
        item("figures") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Matters On Record", "${cases.size}", "Every case the firm holds for you", Modifier.weight(1f).fillMaxHeight())
                    StatCard("Active", "${active.size}", "Cases still before a court or authority", Modifier.weight(1f).fillMaxHeight(),
                        chip = { if (active.isNotEmpty()) StatusChip("In Progress", Lex.colors.neutral) })
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Hearings Scheduled", "$scheduled", "Dates fixed across all your matters", Modifier.weight(1f).fillMaxHeight(),
                        // No date fixed is not good news: a neutral chip, never green.
                        chip = { if (scheduled == 0) StatusChip("None Listed", Lex.colors.neutral) })
                    StatCard("Hearings On Record", "${cases.sumOf { it.hearingCount }}", "Every hearing noted on your matters",
                        Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        if (cases.isEmpty()) {
            item("empty") { LexCard { CasesEmpty() } }
            return@LazyColumn
        }
        item("filter") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedToggle(
                    options = listOf(CaseFilter.ALL to "All", CaseFilter.ACTIVE to "Active", CaseFilter.CLOSED to "Disposed"),
                    selected = filter, onSelect = onFilter,
                )
                Spacer(Modifier.weight(1f))
                Text("${shown.size} ${if (shown.size == 1) "Matter" else "Matters"}",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
            }
        }
        if (shown.isEmpty()) {
            item("empty-filter") { LexCard { FilteredEmpty(filter) } }
        }
        items(shown, key = { it.id }) { c -> CaseCard(c, today) { onOpenCase(c.id) } }
    }
}

/** A matter, as Mylex's case card: stripe, number · court, parties, court · judge, the facts. */
@Composable
private fun CaseCard(c: CaseSummary, today: LocalDate, onClick: () -> Unit) {
    val closed = c.isClosedCase()
    LexCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(4.dp).height(56.dp).clip(RoundedCornerShape(2.dp))
                .background(if (closed) Lex.colors.faint else Lex.colors.success.solid))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.number, fontFamily = Mono, style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground)
                    c.courtName?.let {
                        Text("  •  ${it.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Lex.colors.mutedForeground,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(partiesText(c.title), style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                val where = listOfNotNull(c.courtName, c.judgeName).joinToString(" · ")
                if (where.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(where, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(c.status.label, c.status.palette)
        }
        HorizontalDivider(color = Lex.colors.border)
        // The facts, two by two on a phone (four across on the portal).
        Column(Modifier.background(Lex.colors.muted.copy(alpha = 0.5f))) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Fact("Next Hearing", c.nextHearingDate?.takeIf { !it.isBefore(today) }?.let { fmtDate(it) }
                    ?: if (closed) closedGlance(c).second else "No Hearing Date Fixed", Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.border))
                Fact("Hearings", "${c.hearingCount}", Modifier.weight(1f))
            }
            HorizontalDivider(color = Lex.colors.border)
            Row(Modifier.height(IntrinsicSize.Min)) {
                Fact("Filed", c.filedDate?.let { fmtDate(it) } ?: "Not Recorded", Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.border))
                Fact("Last Update", c.lastActivityAt?.let { relativeAgo(it) } ?: "None Yet", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, maxLines = 2)
    }
}

/** "Rajesh Sharma v. Apex Industries" with the v. set quiet, as the portal does. */
@Composable
private fun partiesText(title: String) = androidx.compose.ui.text.buildAnnotatedString {
    val parts = title.split(Regex("\\s+(?:v/s\\.?|v\\.|vs\\.?|versus)\\s+", RegexOption.IGNORE_CASE), limit = 2)
    append(parts[0])
    if (parts.size == 2) {
        withStyle(androidx.compose.ui.text.SpanStyle(color = Lex.colors.mutedForeground, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)) { append(" v. ") }
        append(parts[1])
    }
}

private fun fmtDate(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))

/** A small outline button for a page header's actions (kt-btn-outline kt-btn-sm). */
@Composable
private fun LexOutlineChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = Lex.colors.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Lex.colors.input)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Lex.colors.foreground, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground)
        }
    }
}

// =============================================================================
// Next-hearing spotlight
// =============================================================================

// =============================================================================
// Section header
// =============================================================================

// =============================================================================
// Matter row
// =============================================================================

private enum class HearTone { IMMINENT, WEEK, NORMAL, QUIET }

private data class Quad(val icon: ImageVector, val text: String, val color: Color, val filled: Boolean)

private fun closedGlance(c: CaseSummary): Pair<ImageVector, String> {
    // Prefer the real disposition (how the matter ended) over generic activity.
    val date = (c.disposalDate ?: c.lastActivityAt?.toLocalDate())?.format(dayFmt)
    val label = c.disposalType?.takeIf { it.isNotBlank() } ?: c.status.label
    val settled = c.status == CaseStatus.SETTLED || label.equals("settled", ignoreCase = true)
    val icon = if (settled) Icons.Outlined.CheckCircle else Icons.Outlined.Gavel
    return icon to listOfNotNull(label, date).joinToString(" · ")
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
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.SlateSoft)
                .border(LlDimen.HairlineWidth, LlColor.SlateBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Gavel, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("No Cases Filed Yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "When your advocate opens a case for you, it will appear here — with its court, judge and every hearing date kept up to date.",
            fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Forum, null, tint = LlColor.InkFaint, modifier = Modifier.size(16.dp))
            Text("Have a question? Raise it in Consultations", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
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
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FolderOpen, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            when (filter) {
                CaseFilter.ACTIVE -> "No Active Matters"
                CaseFilter.CLOSED -> "No Disposed Matters"
                else -> "No Matters"
            },
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text("No records match this view.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp,
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
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 17.dp),
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
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Matters Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
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
