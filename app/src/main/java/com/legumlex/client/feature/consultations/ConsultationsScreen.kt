package com.legumlex.client.feature.consultations

import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.PageHeader
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// -----------------------------------------------------------------------------
// "Client Consultations List.html" (Plot Design). The hub the client lands on
// from the Cases tab — every consultation across all five kinds, grouped into
// action-ordered smart-section bands (Awaiting you · Active · Scheduled · Done)
// so the thing that needs them always sits on top. The app-wide centred-logo
// header and bottom nav are provided by MainActivity; the in-body intro scrolls
// away and the filter bar pins under the header.
// -----------------------------------------------------------------------------

// LlColor.ClaretSoft / ClaretMid are broken placeholders (white / black); use
// the design's real claret tints (--claret-s / --claret-m) locally.
private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretSoft
private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretMid

private val visibleFilters = listOf(
    ConsultationFilter.ALL,
    ConsultationFilter.ACTIVE,
    ConsultationFilter.AWAITING_YOU,
    ConsultationFilter.DONE,
)

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConsultationsScreen(
    onOpenConsultation: (String) -> Unit,
    onNewConsultation: () -> Unit,
) {
    val vm: ConsultationsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    var filter by rememberSaveable { mutableStateOf(ConsultationFilter.ALL) }

    Scaffold(
        containerColor = LlColor.Paper,
        // The app-wide header already consumes the status-bar inset; without this
        // the nested Scaffold re-adds it as a fixed empty band under the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is SessionsUiState.Loading -> ConsultationsSkeleton()
                is SessionsUiState.Error   -> ConsultationsError(s.message, vm::retry)
                is SessionsUiState.Ready   -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = vm::refresh,
                    modifier     = Modifier.fillMaxSize(),
                ) {
                    ConsultationsReady(
                        sections   = s.sections,
                        filter     = filter,
                        onFilter   = { filter = it },
                        onOpen     = onOpenConsultation,
                        onStartNew = onNewConsultation,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Section bands
// =============================================================================

private enum class RowMode { AWAIT, LIVE, NORMAL, DONE }

private data class Band(
    val name: String,
    val await: Boolean,
    val items: List<Consultation>,
)

private fun bandsFor(sections: SmartSections, filter: ConsultationFilter): List<Band> {
    val done = sections.converted + sections.closed
    val all = when (filter) {
        ConsultationFilter.ALL -> listOf(
            Band("Awaiting you", true, sections.needsAction),
            Band("Active", false, sections.active),
            Band("Scheduled", false, sections.scheduled),
            Band("Done", false, done),
        )
        ConsultationFilter.ACTIVE -> listOf(
            Band("Awaiting you", true, sections.needsAction),
            Band("Active", false, sections.active),
            Band("Scheduled", false, sections.scheduled),
        )
        ConsultationFilter.AWAITING_YOU -> listOf(Band("Awaiting you", true, sections.needsAction))
        ConsultationFilter.DONE, ConsultationFilter.CONVERTED -> listOf(Band("Done", false, done))
    }
    return all.filter { it.items.isNotEmpty() }
}

private fun rowMode(c: Consultation, band: Band): RowMode = when {
    c.status == ConsultationStatus.LIVE_NOW -> RowMode.LIVE
    band.await -> RowMode.AWAIT
    band.name == "Done" -> RowMode.DONE
    else -> RowMode.NORMAL
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsultationsReady(
    sections: SmartSections,
    filter: ConsultationFilter,
    onFilter: (ConsultationFilter) -> Unit,
    onOpen: (String) -> Unit,
    onStartNew: () -> Unit,
) {
    val bands = remember(sections, filter) { bandsFor(sections, filter) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("intro") { PageIntro(sections, onStartNew) }
        // Brand-new client: no figures or filters, just a warm invitation.
        if (sections.isEmpty) {
            item("first-run") { FirstRunEmpty(onStartNew) }
            return@LazyColumn
        }
        item("filters") { FilterBar(sections = sections, selected = filter, onSelect = onFilter) }

        if (bands.isEmpty()) {
            item("empty") { FilteredEmpty(filter, onFilter) }
        } else {
            bands.forEachIndexed { bi, band ->
                item("head-${band.name}") { SectionHeader(band.name, band.items.size, band.await, first = bi == 0) }
                items(band.items, key = { it.id }) { c -> ConsultationRow(c, rowMode(c, band)) { onOpen(c.id) } }
            }
            if (filter == ConsultationFilter.AWAITING_YOU) {
                item("caught-up") { AllCaughtUpNote() }
            }
        }
    }
}

// =============================================================================
// Page intro (scrolls away)
// =============================================================================

/** Mylex's consultations header: the title with Ask, then the four figures. */
@Composable
private fun PageIntro(sections: SmartSections, onStartNew: () -> Unit) {
    val active = sections.needsAction.size + sections.active.size + sections.scheduled.size
    val done = sections.converted.size + sections.closed.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(eyebrow = "Your Work With Us", title = "Consultations", actions = {
            Surface(onClick = onStartNew, shape = MaterialTheme.shapes.medium, color = Lex.colors.primary) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Forum, null, tint = Lex.colors.primaryForeground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask", style = MaterialTheme.typography.labelLarge, color = Lex.colors.primaryForeground)
                }
            }
        })
        if (!sections.isEmpty) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Consultations", "${sections.total}", "Every session you have raised with us", Modifier.weight(1f).fillMaxHeight())
                StatCard("Awaiting You", "${sections.needsAction.size}", "Sessions waiting on something from you",
                    Modifier.weight(1f).fillMaxHeight(),
                    valueColor = if (sections.needsAction.isNotEmpty()) Lex.colors.danger.solid else Lex.colors.foreground,
                    chip = { if (sections.needsAction.isEmpty()) StatusChip("Nothing Owed", Lex.colors.success) })
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Active", "$active", "Sessions still with the firm", Modifier.weight(1f).fillMaxHeight())
                StatCard("Done", "$done", "Sessions closed after an answer", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// =============================================================================
// Sticky filter bar
// =============================================================================

@Composable
private fun FilterBar(
    sections: SmartSections,
    selected: ConsultationFilter,
    onSelect: (ConsultationFilter) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        SegmentedToggle(
            options = visibleFilters.map { f -> f to title(f.label) },
            selected = selected, onSelect = onSelect,
        )
    }
}


// =============================================================================
// Section header
// =============================================================================

@Composable
private fun SectionHeader(name: String, count: Int, await: Boolean, first: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = if (first) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(name, color = if (await) Lex.colors.danger.solid else Lex.colors.mutedForeground)
        Spacer(Modifier.width(8.dp))
        StatusBadge("$count", if (await) Lex.colors.danger else Lex.colors.neutral)
    }
}

// =============================================================================
// Row card
// =============================================================================

@Composable
private fun ConsultationRow(c: Consultation, mode: RowMode, onClick: () -> Unit) {
    val stripe = when (mode) {
        RowMode.AWAIT -> Lex.colors.danger.solid
        RowMode.LIVE -> Lex.colors.success.solid
        else -> null
    }
    val done = mode == RowMode.DONE
    LexCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (stripe != null) Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(c.kind)
                    StatusPill(c.status)
                }
                Text(
                    c.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) Lex.colors.mutedForeground else Lex.colors.foreground,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Subline(c)
                Glance(c, mode)
                if (mode == RowMode.AWAIT || mode == RowMode.LIVE) NextActionLine(c, mode)
            }
        }
    }
}

/** The advocate — their photo, or initials — and how long ago it moved. */
@Composable
private fun Subline(c: Consultation) {
    val adv = c.assignedTo?.takeIf { it.isNotBlank() }
    val age = relativeAge(c.lastUpdatedAt ?: c.createdAt)
    if (adv == null && age == null && !c.isUrgent && !c.hasUnreadReplies) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (adv != null) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Lex.colors.muted), contentAlignment = Alignment.Center) {
                Text(adv.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Lex.colors.foreground)
                c.assignedToAvatarUrl?.let {
                    AsyncImage(model = it, contentDescription = adv, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(24.dp).clip(CircleShape))
                }
            }
            Text(adv, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, maxLines = 1)
        }
        if (adv != null && age != null) Text("·", style = MaterialTheme.typography.bodySmall, color = Lex.colors.faint)
        if (age != null) Text(age, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, maxLines = 1)
        if (c.hasUnreadReplies) StatusBadge("New Reply", Lex.colors.danger)
        else if (c.isUrgent) StatusBadge("Needs Response", Lex.colors.danger)
    }
}

@Composable
private fun Glance(c: Consultation, mode: RowMode) {
    when {
        mode == RowMode.LIVE -> Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PulseDot(LlColor.VerdantMid, 7.dp)
            Text("${advShort(c.assignedTo)} is in the room", fontFamily = Inter, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, color = LlColor.Verdant)
        }

        c.kind == ConsultationKind.DOC_REQUEST && c.totalDocCount > 0 -> {
            val ready = (c.totalDocCount - c.pendingDocCount).coerceAtLeast(0)
            val frac = ready.toFloat() / c.totalDocCount.toFloat()
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.width(62.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(LlColor.Violet))
                }
                Row {
                    Text("$ready of ${c.totalDocCount} ", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.Ink)
                    Text("ready", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkMuted)
                }
            }
        }

        c.kind == ConsultationKind.AHC -> {
            val lvl = c.riskLevel?.name?.lowercase()
            val (dot, label) = when {
                lvl?.contains("high") == true -> ClaretMidFix to "High risk"
                lvl?.contains("med") == true -> LlColor.AmberMid to "Medium risk"
                lvl != null -> LlColor.Verdant to "Low risk"
                c.status == ConsultationStatus.PENDING_REVIEW -> LlColor.AmberMid to "Risk pending review"
                else -> return
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(dot))
                Text(label, fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkMuted)
            }
        }

        c.kind == ConsultationKind.DIGITAL_OFFICE && c.scheduledAt() != null -> {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.Event, null, tint = LlColor.Verdant, modifier = Modifier.size(14.dp))
                Text(scheduledGlance(c), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.Ink)
            }
        }
    }
}

@Composable
private fun NextActionLine(c: Consultation, mode: RowMode) {
    val live = mode == RowMode.LIVE
    val icon = when {
        live -> Icons.Outlined.Videocam
        c.hasUnreadReplies -> Icons.Outlined.MarkChatUnread
        c.kind == ConsultationKind.DOC_REQUEST && c.pendingDocCount > 0 -> Icons.Outlined.UploadFile
        else -> Icons.Outlined.ChevronRight
    }
    val text = if (live) "Your session is live — tap to join now"
        else c.nextActionLabel() ?: "Open to continue"
    val tint = if (live) LlColor.Verdant else ClaretMidFix
    Column(Modifier.padding(top = 11.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Row(
            Modifier.padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Text(text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = tint)
        }
    }
}

@Composable
private fun KindBadge(kind: ConsultationKind) {
    val (palette, icon, label) = when (kind) {
        ConsultationKind.GENERAL -> Triple(Lex.colors.neutral, Icons.Outlined.Forum, "General")
        ConsultationKind.LEGAL_ENQUIRY -> Triple(Lex.colors.neutral, Icons.Outlined.Forum, "Legal Enquiry")
        ConsultationKind.AHC -> Triple(Lex.colors.neutral, Icons.Outlined.Description, "Agreement Check")
        ConsultationKind.LEGAL_NOTICE -> Triple(Lex.colors.neutral, Icons.Outlined.Gavel, "Legal Notice")
        ConsultationKind.DOC_REQUEST -> Triple(Lex.colors.neutral, Icons.Outlined.FolderOpen, "Documents")
        ConsultationKind.DIGITAL_OFFICE -> Triple(Lex.colors.neutral, Icons.Outlined.Videocam, "Video Session")
    }
    StatusChip(label, palette, icon)
}

@Composable
private fun StatusPill(status: ConsultationStatus) = StatusBadge(title(status.label), status.palette)

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulseAlpha",
    )
    Box(Modifier.size(size).alpha(a).clip(RoundedCornerShape(50)).background(color))
}

// =============================================================================
// Empty / first-run / caught-up
// =============================================================================

@Composable
private fun AllCaughtUpNote() {
    Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft)
                .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Outlined.TaskAlt, null, tint = LlColor.Verdant, modifier = Modifier.size(20.dp))
            Text(
                buildAnnotatedNote(),
                fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp, color = LlColor.InkMuted,
            )
        }
    }
}

@Composable
private fun buildAnnotatedNote(): String = "Clear these and you're all caught up — nothing else needs you right now."

@Composable
private fun FilteredEmpty(filter: ConsultationFilter, onFilter: (ConsultationFilter) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft)
                .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            if (filter == ConsultationFilter.AWAITING_YOU) "Nothing needs your attention" else "Nothing in this view",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            when (filter) {
                ConsultationFilter.AWAITING_YOU -> "You're all caught up. Your advocate has the ball on everything that's open — we'll notify you the moment something needs you."
                ConsultationFilter.DONE, ConsultationFilter.CONVERTED -> "No completed or converted matters yet."
                else -> "No consultations match this view."
            },
            fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Surface)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))
                .clickable { onFilter(ConsultationFilter.ALL) }
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = LlColor.Ink, modifier = Modifier.size(18.dp))
            Text("VIEW ALL CONSULTATIONS", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Ink)
        }
    }
}

@Composable
private fun FirstRunEmpty(onStartNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 36.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(ClaretSoftFix)
                .border(LlDimen.HairlineWidth, LlColor.ClaretBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Forum, null, tint = LlColor.Claret, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Your chamber is ready when you are", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(
            "You haven't raised anything yet. Ask a question, request a document check, or book a call — your advocate and the team are here.",
            fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onStartNew)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Add, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("START A CONSULTATION", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Skeleton + error
// =============================================================================

@Composable
private fun ConsultationsSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
            Sk(96.dp, 11.dp); Spacer(Modifier.height(11.dp))
            Sk(188.dp, 28.dp, 7.dp); Spacer(Modifier.height(11.dp))
            Sk(240.dp, 13.dp)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(74, 92, 124, 78).forEach { Sk(it.dp, 34.dp, 999.dp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Spacer(Modifier.height(16.dp))
        repeat(4) { i ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                    .clip(LlShape.Card).background(LlColor.Surface)
                    .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(13.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Sk(96.dp, 18.dp); Sk(104.dp, 18.dp, 7.dp)
                }
                Spacer(Modifier.height(12.dp)); Sk(modifier = Modifier.fillMaxWidth(0.78f), height = 17.dp)
                Spacer(Modifier.height(9.dp)); Sk(modifier = Modifier.fillMaxWidth(0.54f), height = 11.dp)
                if (i == 0) { Spacer(Modifier.height(11.dp)); Sk(120.dp, 12.dp) }
            }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2),
    )
}

@Composable
private fun ConsultationsError(message: String, onRetry: () -> Unit) {
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
        Text("Couldn't load", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun relativeAge(at: LocalDateTime?): String? {
    if (at == null) return null
    val d = Duration.between(at, LocalDateTime.now())
    val hours = d.toHours(); val days = d.toDays()
    return when {
        hours < 1 -> "updated just now"
        hours < 24 -> "updated ${hours}h ago"
        days < 7 -> "updated ${days}d ago"
        days < 30 -> "updated ${days / 7}w ago"
        else -> "updated ${at.toLocalDate().format(dayFmt)}"
    }
}

private fun scheduledGlance(c: Consultation): String {
    val d = c.scheduledAt() ?: return ""
    val date = d.toLocalDate(); val today = LocalDate.now()
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(dayFmt)
    }
    return "$day, ${d.format(timeFmt)} IST"
}

private fun advShort(name: String?): String {
    val n = name?.trim()?.takeIf { it.isNotBlank() } ?: return "Your advocate"
    return if (n.startsWith("adv", ignoreCase = true)) n else "Adv. ${n.split(" ").last()}"
}

private fun title(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
