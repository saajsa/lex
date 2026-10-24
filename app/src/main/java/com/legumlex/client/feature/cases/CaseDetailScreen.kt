package com.legumlex.client.feature.cases

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.caseTitlePlain
import com.legumlex.client.ui.formatJudge
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BodyBg
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderStrong
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// -----------------------------------------------------------------------------
// One matter, as the client sees it (owner, 2026-10-24): the staff app's case
// page — a head that names the matter, the parties and the figures pinned
// over tabs — in this app's own grey and ink, carrying only what the client
// API gives a client. The client reads, opens and messages; nothing here
// records, uploads or deletes. Tabs: Hearings (what is coming, the orders,
// what has been), Documents, Notices, Counsel (counsel on record, lead
// first). The court, the parties and the acts are under the head's "i".
// -----------------------------------------------------------------------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    caseId: String,
    onBack: () -> Unit = {},   // back is the app-wide header's; kept for the route
    onOpenConsultation: (String) -> Unit = {},
    onOpenDocument: (CaseFile) -> Unit = {},
    onOpenOrderDocument: (CaseOrder) -> Unit = {},
    onOpenHearing: (String) -> Unit = {},
    onOpenNotice: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: CaseDetailViewModel = viewModel(
        key = "case-$caseId",
        factory = viewModelFactory { initializer { CaseDetailViewModel(app = app, caseId = caseId) } },
    )
    val state by vm.state.collectAsState()
    val refreshing by vm.isRefreshing.collectAsState()
    var tab by rememberSaveable(caseId) { mutableStateOf("hearings") }

    Box(Modifier.fillMaxSize().background(BodyBg)) {
        when (val s = state) {
            is CaseDetailUiState.Loading -> Skeleton()
            is CaseDetailUiState.Error -> ErrorState(s.message, vm::load)
            is CaseDetailUiState.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh) {
                Page(s.detail, s.notices, tab, { tab = it }, onOpenConsultation, onOpenDocument, onOpenOrderDocument, onOpenHearing, onOpenNotice)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Page(
    d: CaseDetail,
    notices: List<CaseNotice>,
    tab: String,
    onTab: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenDocument: (CaseFile) -> Unit,
    onOpenOrderDocument: (CaseOrder) -> Unit,
    onOpenHearing: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
) {
    val hearings = d.upcomingHearings.size + d.pastHearings.size
    // The head scrolls away; the parties, the figures and the tabs stay.
    LazyColumn(Modifier.fillMaxSize()) {
        item { Head(d, onOpenConsultation) }
        stickyHeader {
            Pinned(d) {
                Segments(
                    listOf("hearings" to "Hearings", "documents" to "Docket", "notices" to "Notices", "counsel" to "Counsel"),
                    tab, mapOf("hearings" to hearings, "documents" to d.documents.size, "notices" to notices.size, "counsel" to d.counsel.size), onTab,
                )
            }
        }
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 120.dp)) {
                when (tab) {
                    "hearings" -> HearingsTab(d, onOpenOrderDocument, onOpenHearing)
                    "documents" -> DocumentsTab(d, onOpenDocument)
                    "notices" -> NoticesTab(notices, onOpenNotice)
                    else -> CounselTab(d.counsel)
                }
            }
        }
    }
}

// =============================================================================
// Head and the pinned part
// =============================================================================

/** The head: the number with its "i", a way to write to the advocate, and the state's light. */
@Composable
private fun Head(d: CaseDetail, onOpenConsultation: (String) -> Unit) {
    var info by remember { mutableStateOf(false) }
    if (info) InfoDialog(d) { info = false }
    Row(
        Modifier.fillMaxWidth().background(AccentInk).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(InverseText).clickable(onClickLabel = "About this case") { info = true },
            contentAlignment = Alignment.Center,
        ) { Text("i", color = AccentInk, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.width(10.dp))
        Text(d.summary.number.ifBlank { "Matter" }, color = InverseText.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // Messages live in the consultation the matter grew from.
        d.originConsultationId?.let { id -> HeadIcon(Icons.Outlined.Forum, "Message your advocate") { onOpenConsultation(id) } }
        StateLight(d.summary.status)
    }
}

@Composable
private fun HeadIcon(icon: ImageVector, name: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).border(1.5.dp, InverseText.copy(alpha = 0.35f), CircleShape)
            .clickable(onClickLabel = name, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = name, tint = InverseText, modifier = Modifier.size(17.dp)) }
}

/** Green while the matter is before the court; grey once it is disposed. Named for screen readers. */
@Composable
private fun StateLight(s: CaseStatus) {
    val live = s == CaseStatus.ONGOING || s == CaseStatus.PENDING
    Box(
        Modifier.size(22.dp).semantics { contentDescription = s.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background((if (live) Lex.colors.success.solid else TertiaryText).copy(alpha = 0.25f)))
        Box(Modifier.size(11.dp).clip(CircleShape).background(if (live) Lex.colors.success.solid else TertiaryText))
    }
}

/** The pinned part: who is against whom, the figures, then the tabs. */
@Composable
private fun Pinned(d: CaseDetail, tabs: @Composable () -> Unit) {
    val orders = d.orders.size
    Column(Modifier.fillMaxWidth().background(BodyBg)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(AccentInk).padding(bottom = 16.dp),
        ) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) { Versus(d) }
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(Surface1).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Figure(Modifier.weight(1f), d.upcomingHearings.size + d.pastHearings.size, "Hearings")
                Figure(Modifier.weight(1f), orders, "Orders")
                Figure(Modifier.weight(1f), d.documents.size, "Docket")
            }
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) { tabs() }
    }
}

/**
 * The parties side by side, the lead of each with "and N more" as a count —
 * from the record, or from the cause title when no parties were entered.
 */
@Composable
private fun Versus(d: CaseDetail) {
    val p = d.parties
    val fromTitle = caseTitlePlain(d.summary.title).split(" v. ", limit = 2)
    val left = p.petitioners.map { it.name }.ifEmpty { listOfNotNull(fromTitle.getOrNull(0)?.takeIf { fromTitle.size == 2 }) }
    val right = p.respondents.map { it.name }.ifEmpty { listOfNotNull(fromTitle.getOrNull(1)) }
    if (left.isEmpty() && right.isEmpty()) {
        Text(d.summary.title, color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Side(Modifier.weight(1f), if (left.size > 1) "Petitioners" else "Petitioner", left, Alignment.Start)
        Box(
            Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp)).background(InverseText)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) { Text("v.", color = AccentInk, fontFamily = Inter, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 13.sp) }
        Side(Modifier.weight(1f), if (right.size > 1) "Respondents" else "Respondent", right, Alignment.End)
    }
}

@Composable
private fun Side(modifier: Modifier, label: String, names: List<String>, align: Alignment.Horizontal) {
    val textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start
    Column(modifier, horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), color = InverseText.copy(alpha = 0.6f), fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp, letterSpacing = 1.2.sp)
            if (names.size > 1) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(InverseText).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("${names.size}", color = AccentInk, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(names.firstOrNull().orEmpty(), color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            lineHeight = 19.sp, textAlign = textAlign, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (names.size > 1) Text(if (names.size == 2) "and one more" else "and ${names.size - 1} more", color = InverseText.copy(alpha = 0.6f),
            fontFamily = Inter, fontSize = 12.sp, textAlign = textAlign)
    }
}

@Composable
private fun Figure(modifier: Modifier, value: Int, label: String) {
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1)
        Text(label, color = SecondaryText, fontFamily = Inter, fontSize = 11.sp, maxLines = 1)
    }
}

/** The tabs: the client's own toggle group (Mylex kt-toggle-group), stretched across, with counts. */
@Composable
private fun Segments(items: List<Pair<String, String>>, selected: String, badges: Map<String, Int>, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.medium)
            .border(1.dp, Lex.colors.input, MaterialTheme.shapes.medium),
    ) {
        items.forEachIndexed { i, (key, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.input))
            val on = key == selected
            Row(
                Modifier.weight(1f).background(if (on) Lex.colors.muted else Lex.colors.card)
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) PrimaryText else SecondaryText, maxLines = 1)
                val n = badges[key] ?: 0
                if (n > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("$n", style = MaterialTheme.typography.labelSmall, color = TertiaryText)
                }
            }
        }
    }
}

/** The "i": the case's particulars, as the head has no room for them. */
@Composable
private fun InfoDialog(d: CaseDetail, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("This Case", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Rows(listOfNotNull(
                    "Case No." to d.summary.number.ifBlank { "—" },
                    "Court" to (d.court ?: "—"),
                    d.courtNumber?.let { "Court No." to it },
                    d.benchType?.let { "Bench" to it },
                    formatJudge(d.judge, d.court)?.let { "Presiding" to it },
                    d.filedDate?.let { "Filed" to it.format(DayYear) + " · " + age(it) },
                    "Status" to d.summary.status.label,
                ), boxed = false)
                val p = d.parties
                if (p.petitioners.isNotEmpty() || p.respondents.isNotEmpty() || p.others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("PARTIES", color = TertiaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Rows(p.petitioners.map { (it.role ?: "Petitioner") to it.name } + p.respondents.map { (it.role ?: "Respondent") to it.name } +
                        p.others.map { (it.role ?: "Other Party") to it.name }, boxed = false)
                }
                if (d.acts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("ACTS AND SECTIONS", color = TertiaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Rows(d.acts.map { it.name to (it.sections ?: "—") }, boxed = false)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = PrimaryText) } },
        containerColor = Surface1,
    )
}

// =============================================================================
// Hearings
// =============================================================================

/**
 * The hearings, as the staff app lays them out: what is coming as ink cards
 * (the nearest leads), the orders not tied to a listed hearing, then what has
 * been on a quiet timeline with the orders passed at each. A hearing opens in
 * place to show its notes and its orders in full.
 */
@Composable
private fun HearingsTab(d: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit, onOpenHearing: (String) -> Unit) {
    val today = remember { LocalDate.now(IST) }
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    val toggle = { id: String -> open = if (id in open) open - id else open + id }
    val all = d.upcomingHearings + d.pastHearings
    val ordersFor = { h: HearingRow -> d.orders.filter { it.date != null && it.date == h.date } }
    val matched = all.flatMap(ordersFor).map { it.id }.toSet()
    val loose = d.orders.filter { it.id !in matched }
    val upcoming = all.filter { it.date != null && !it.date.isBefore(today) && it.isLive }.sortedBy { it.date }
    val concluded = (all - upcoming.toSet()).sortedByDescending { it.date ?: LocalDate.MIN }
    val disposed = d.summary.status == CaseStatus.SETTLED || d.summary.status == CaseStatus.CLOSED

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (disposed) { Disposal(d); Spacer(Modifier.height(18.dp)) }
        if (upcoming.isNotEmpty()) {
            Section("Coming Up", upcoming.size)
            upcoming.forEachIndexed { i, h ->
                UpcomingCard(h, today, lead = i == 0, expanded = h.id in open, onOpen = { toggle(h.id) },
                    onMore = { if (h.id.isNotBlank()) onOpenHearing(h.id) })
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (loose.isNotEmpty()) {
            Section("Orders On The File", loose.size)
            loose.forEach { o -> OrderLine(o, d, onOpenOrderDocument, showDate = true) }
            Spacer(Modifier.height(16.dp))
        }
        if (concluded.isNotEmpty()) {
            Section("Concluded", concluded.size)
            concluded.forEachIndexed { i, h ->
                ConcludedRow(h, ordersFor(h), d, last = i == concluded.lastIndex, expanded = h.id in open,
                    onOpenOrderDocument = onOpenOrderDocument) { toggle(h.id) }
            }
        }
        if (all.isEmpty() && loose.isEmpty()) Empty(Icons.Outlined.Event, "No Hearings Yet",
            "Hearing dates, what happened at each and the orders passed will appear here.")
    }
}

/** A hearing still to come: ink, the date on a light tile, how soon, the purpose, and what is asked of the client. */
@Composable
private fun UpcomingCard(h: HearingRow, today: LocalDate, lead: Boolean, expanded: Boolean, onOpen: () -> Unit, onMore: () -> Unit) {
    val date = h.date ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AccentInk).clickable(onClick = onOpen).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(if (lead) 64.dp else 56.dp).clip(RoundedCornerShape(12.dp)).background(InverseText).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), color = AccentInk, fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
                Text("${date.dayOfMonth}", color = AccentInk, fontFamily = Inter, fontWeight = FontWeight.Bold,
                    fontSize = if (lead) 24.sp else 20.sp, lineHeight = if (lead) 26.sp else 22.sp)
                Text(date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), color = AccentInk, fontFamily = Inter, fontSize = 9.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(InverseText).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(countdown(date, today), color = AccentInk, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    h.time?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it.format(Time), color = InverseText.copy(alpha = 0.7f), fontFamily = Inter, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(h.purpose?.takeIf { it.isNotBlank() } ?: "Hearing", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = if (lead) 18.sp else 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                tint = InverseText.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
        attendance(h.clientAttendance)?.let {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PersonPinCircle, contentDescription = null, tint = InverseText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your Attendance · $it", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        h.clientInstructions?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(InverseText.copy(alpha = 0.1f)).padding(12.dp)) {
                Text("FROM YOUR ADVOCATE", color = InverseText.copy(alpha = 0.6f), fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(it, color = InverseText, fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        h.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = InverseText.copy(alpha = 0.7f), fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, InverseText.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onMore).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hearing Details", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = InverseText, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/**
 * A hearing that has been: the date in a column, a dot on the line, the
 * purpose with its outcome as an icon, the notes, the next date or why it was
 * adjourned, and the orders passed at it.
 */
@Composable
private fun ConcludedRow(
    h: HearingRow, orders: List<CaseOrder>, d: CaseDetail, last: Boolean, expanded: Boolean,
    onOpenOrderDocument: (CaseOrder) -> Unit, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(40.dp).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(h.date?.dayOfMonth?.toString() ?: "—", color = SecondaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, lineHeight = 18.sp)
            Text(h.date?.month?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)?.uppercase().orEmpty(),
                color = TertiaryText, fontFamily = Inter, fontSize = 10.sp)
            // The year only when it is not this one.
            h.date?.year?.takeIf { it != LocalDate.now(IST).year }?.let {
                Text("$it", color = TertiaryText, fontFamily = Inter, fontSize = 10.sp)
            }
        }
        Box(Modifier.width(18.dp).fillMaxHeight()) {
            if (!last) Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).width(1.dp).fillMaxHeight().background(BorderStrong))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 13.dp).size(8.dp).clip(CircleShape).background(TertiaryText))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 16.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.purpose?.takeIf { it.isNotBlank() } ?: "Hearing", color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Outcome(h.status)
            }
            val meta = listOfNotNull(h.time?.format(Time), h.nextDate?.let { "Next ${it.format(DayYear)}" })
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = TertiaryText, fontFamily = Inter, fontSize = 12.sp)
            h.adjournmentReason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("Adjourned: $it", color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            h.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            if (orders.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                orders.forEach { o -> OrderLine(o, d, onOpenOrderDocument, showDate = false) }
            }
        }
    }
}

/**
 * A hearing's outcome as an icon, never a word (as in the staff app): green
 * for Completed, red for No Appearance, quiet glyphs for the rest; a hearing
 * still marked Scheduled after its date has none. Spoken by screen readers.
 */
@Composable
private fun Outcome(status: String) {
    val muted = SecondaryText
    val (icon, tint) = when (status.trim().lowercase()) {
        "completed" -> Icons.Outlined.CheckCircle to Lex.colors.success.solid
        "no appearance" -> Icons.Outlined.PersonOff to Lex.colors.danger.solid
        "adjourned" -> Icons.Outlined.EventRepeat to muted
        "postponed" -> Icons.Outlined.Schedule to muted
        "in progress" -> Icons.Outlined.HourglassTop to muted
        "cancelled" -> Icons.Outlined.Cancel to muted
        else -> return
    }
    Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(20.dp))
}

/**
 * An order: its kind, the relief, and the summary when there is one. A tap
 * opens it in place — the order as read, and the order itself to open.
 */
@Composable
private fun OrderLine(o: CaseOrder, d: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit, showDate: Boolean) {
    var open by rememberSaveable(o.id) { mutableStateOf(false) }
    val body = !o.fullText.isNullOrBlank() || !o.summary.isNullOrBlank()
    Column(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface2)
            .clickable { if (body) open = !open else if (o.hasDocument) onOpenOrderDocument(o) }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Gavel, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(listOfNotNull(orderKind(o.type), o.date?.takeIf { showDate }?.format(DayYear)).joinToString(" · "),
                color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (body) Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                tint = TertiaryText, modifier = Modifier.size(18.dp))
        }
        o.relief?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        when {
            !o.summary.isNullOrBlank() -> {
                Spacer(Modifier.height(4.dp))
                Text(o.summary, color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp,
                    maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            }
            o.ocrStatus == "pending" -> {
                Spacer(Modifier.height(4.dp))
                Text("Being summarised…", color = TertiaryText, fontFamily = Inter, fontStyle = FontStyle.Italic, fontSize = 12.5.sp)
            }
        }
        if (o.nextDate != null) {
            Spacer(Modifier.height(4.dp))
            Text("Next ${o.nextDate.format(DayYear)}" + (o.purposeNext?.let { " · for $it" } ?: ""), color = TertiaryText,
                fontFamily = Inter, fontSize = 12.sp)
        }
        AnimatedVisibility(open) { OrderPaper(o, d) }
        Spacer(Modifier.height(8.dp))
        CopyLink(o, onOpenOrderDocument)
    }
}

private fun orderKind(type: String): String = when (type.lowercase()) {
    "interim" -> "Interim Order"
    "final" -> "Final Order"
    else -> "Order"
}

/** The order itself, to open; or that it is not on file yet. Never "certified": only a court certifies a copy. */
@Composable
private fun CopyLink(o: CaseOrder, onOpen: (CaseOrder) -> Unit) {
    if (o.hasDocument) Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(AccentInk).clickable { onOpen(o) }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = InverseText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Open Order", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    } else Text("The order is not on file yet", color = TertiaryText, fontFamily = Inter, fontSize = 12.sp)
}

/** The order as read from the paper, on a sheet: the court, the coram, then its paragraphs. */
@Composable
private fun OrderPaper(o: CaseOrder, d: CaseDetail) {
    val coram = o.coram?.takeIf { it.isNotBlank() } ?: formatJudge(d.judge, d.court)
    val paragraphs = (o.fullText ?: o.summary).orEmpty().split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    if (paragraphs.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp)).padding(14.dp),
    ) {
        d.court?.let { Text(it, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        coram?.let { Text("Coram: $it", color = SecondaryText, fontFamily = Inter, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        paragraphs.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Text(p, color = PrimaryText, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp)
        }
    }
}

/** A disposed matter: how it ended, when, why, and the appeal window the firm recorded (information only). */
@Composable
private fun Disposal(d: CaseDetail) {
    val s = d.summary
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().background(AccentInk).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Gavel, contentDescription = null, tint = InverseText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("MATTER DISPOSED", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            s.disposalDate?.let { Text(it.format(DayYear), color = InverseText.copy(alpha = 0.7f), fontFamily = Inter, fontSize = 12.sp) }
        }
        Column(Modifier.padding(16.dp)) {
            Text(s.disposalType ?: s.status.label, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            d.disposalReason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = SecondaryText, fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp)
            }
            if (d.appealDeadline != null || !d.appealNote.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface2).padding(12.dp)) {
                    Text("APPEAL WINDOW", color = SecondaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp)
                    d.appealDeadline?.let { Text("Last day · ${it.format(DayYear)}", color = PrimaryText, fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                    d.appealNote?.takeIf { it.isNotBlank() }?.let { Text(it, color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp,
                        lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

// =============================================================================
// Documents
// =============================================================================

@Composable
private fun DocumentsTab(d: CaseDetail, onOpen: (CaseFile) -> Unit) {
    val inMatter = d.documents.filter { it.relation != "hearing" }
    val atHearings = d.documents.filter { it.relation == "hearing" }
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (d.documents.isEmpty()) {
            Empty(Icons.Outlined.FolderOpen, "Nothing On The Docket Yet", "Filings and papers the firm shares with you on this case will appear here.")
            return@Column
        }
        if (inMatter.isNotEmpty()) {
            Section("Filed In The Matter", inMatter.size)
            inMatter.forEach { FileRow(it, onOpen) }
            Spacer(Modifier.height(16.dp))
        }
        if (atHearings.isNotEmpty()) {
            Section("Filed At Hearings", atHearings.size)
            atHearings.forEach { FileRow(it, onOpen) }
        }
    }
}

@Composable
private fun FileRow(f: CaseFile, onOpen: (CaseFile) -> Unit) {
    val ext = f.name.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..4 }
        ?: f.fileType?.substringAfterLast('/')?.uppercase()?.takeIf { it.length in 2..4 }
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)).clickable { onOpen(f) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(if (ext in setOf("JPG", "JPEG", "PNG")) Icons.Outlined.Image else Icons.Outlined.Description, contentDescription = null,
                tint = PrimaryText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val meta = listOfNotNull(
                f.tag.takeIf { it.isNotBlank() },
                ext,
                (if (f.relation == "hearing") f.hearingDate?.let { "Hearing ${it.format(DayYear)}" } else null) ?: f.date?.format(DayYear),
            )
            Text(meta.joinToString(" · "), color = TertiaryText, fontFamily = Inter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TertiaryText, modifier = Modifier.size(20.dp))
    }
}

// =============================================================================
// Notices
// =============================================================================

/** The case's notices: what was sent or received, to or from whom, and where it stands; a row opens the notice. */
@Composable
private fun NoticesTab(notices: List<CaseNotice>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (notices.isEmpty()) {
            Empty(Icons.Outlined.MarkEmailRead, "No Notices On This Case",
                "A notice the firm sends or receives on this matter appears here, with where it stands.")
            return@Column
        }
        Section("Notices", notices.size)
        notices.forEach { n ->
            Row(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp)).clickable { if (n.id.isNotBlank()) onOpen(n.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                    Icon(if (n.inbound) Icons.Outlined.MoveToInbox else Icons.Outlined.Outbox, contentDescription = if (n.inbound) "Received" else "Sent",
                        tint = PrimaryText, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.type, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(
                        n.counterparty.takeIf { it.isNotBlank() }?.let { if (n.inbound) "From $it" else "To $it" },
                        n.reference.takeIf { it.isNotBlank() },
                    ).joinToString(" · "), color = SecondaryText, fontFamily = Inter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (n.statusLabel.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(n.statusLabel, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Surface2).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

// =============================================================================
// Counsel on record
// =============================================================================

/**
 * Counsel on record, as a fund names its managers (owner, 2026-10-24): one
 * card each — the salutation, the name, the designation, the introduction,
 * and Lead Counsel or Assisting Counsel — the portrait down one side,
 * alternating right, left, right.
 */
@Composable
private fun CounselTab(counsel: List<CounselOnRecord>) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (counsel.isEmpty()) {
            Empty(Icons.Outlined.Groups, "No Counsel On Record", "The advocates handling this matter will appear here.")
            return@Column
        }
        counsel.forEachIndexed { i, c ->
            CounselCard(c, photoRight = i % 2 == 0)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CounselCard(c: CounselOnRecord, photoRight: Boolean) {
    val salute = when (c.honorific.trim().trimEnd('.').lowercase()) {
        "adv" -> "Advocate"
        "sr. adv", "sr adv" -> "Senior Advocate"
        else -> c.honorific.trim()
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 150.dp).clip(RoundedCornerShape(12.dp)).background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
    ) {
        if (!photoRight) Portrait(c)
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.Center) {
            if (salute.isNotBlank()) Text(salute.uppercase(), color = TertiaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp, letterSpacing = 1.sp)
            Text(c.name, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
            if (c.designation.isNotBlank()) Text(c.designation, color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp)
            if (c.intro.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(c.intro, color = SecondaryText, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (c.lead) "Lead Counsel" else "Assisting Counsel",
                color = if (c.lead) InverseText else PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .then(if (c.lead) Modifier.background(AccentInk) else Modifier.border(1.dp, BorderStrong, RoundedCornerShape(6.dp)))
                    .padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (photoRight) Portrait(c)
    }
}

/** The counsel's portrait filling its side of the card, or their initials on grey. */
@Composable
private fun Portrait(c: CounselOnRecord) {
    Box(Modifier.width(118.dp).fillMaxHeight().background(Surface2), contentAlignment = Alignment.Center) {
        if (c.photoUrl != null) {
            coil.compose.AsyncImage(
                model = c.photoUrl, contentDescription = c.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(initials(c.name), color = TertiaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
        }
    }
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Section(text: String, count: Int? = null) {
    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = SecondaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AccentInk).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("$it", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

/** Label and value rows, in a card unless [boxed] is false. */
@Composable
private fun Rows(rows: List<Pair<String, String>>, boxed: Boolean = true) {
    Column(
        if (boxed) Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp) else Modifier.fillMaxWidth(),
    ) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Text(k, color = SecondaryText, fontFamily = Inter, fontSize = 13.5.sp, modifier = Modifier.width(104.dp))
                Text(v, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Empty(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, BorderStrong, RoundedCornerShape(12.dp)).padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = TertiaryText, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = SecondaryText, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(AccentInk).padding(20.dp)) {
            Bar(InverseText.copy(alpha = 0.15f), 120.dp, 12.dp)
            Spacer(Modifier.height(22.dp))
            Bar(InverseText.copy(alpha = 0.15f), 220.dp, 18.dp)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(InverseText.copy(alpha = 0.12f)))
        }
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(Surface2))
            Spacer(Modifier.height(18.dp))
            repeat(3) { Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(Surface2)); Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun Bar(color: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) =
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(color))

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text("Matter Unavailable", color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = SecondaryText, fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(AccentInk).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = InverseText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again", color = InverseText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val DayYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val Time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

private fun countdown(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 7L -> "In $days days"
        days < 14L -> "Next week"
        else -> "In ${days / 7} weeks"
    }
}

/** The client's attendance as the advocate set it, in plain words; null when unset. */
private fun attendance(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    "required" -> "Required"
    "optional" -> "Optional"
    "not_required", "not required" -> "Not Required"
    else -> null
}

private fun age(filed: LocalDate): String {
    val days = ChronoUnit.DAYS.between(filed, LocalDate.now(IST)).coerceAtLeast(0)
    return if (days < 60) "$days days" else if (days < 730) "${days / 30} months" else "${days / 365} years"
}

private fun initials(name: String?): String =
    name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { "?" } ?: "?"
