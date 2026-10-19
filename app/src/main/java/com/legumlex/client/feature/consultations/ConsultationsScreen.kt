package com.legumlex.client.feature.consultations

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
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
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
private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretMidFix = Color(0xFFBE2E4A)

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
    // Brand-new client — no filter bar, just a warm invitation.
    if (sections.isEmpty) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("intro") { PageIntro(filter, sections, showCount = false) }
            item("first-run") { FirstRunEmpty(onStartNew) }
        }
        return
    }

    val bands = remember(sections, filter) { bandsFor(sections, filter) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("intro") { PageIntro(filter, sections, showCount = filter == ConsultationFilter.ALL || filter == ConsultationFilter.ACTIVE) }

        item("filters") {
            FilterBar(sections = sections, selected = filter, onSelect = onFilter)
        }

        if (bands.isEmpty()) {
            item("empty") { FilteredEmpty(filter, onFilter) }
        } else {
            bands.forEachIndexed { bi, band ->
                item("head-${band.name}") {
                    SectionHeader(band.name, band.items.size, band.await, first = bi == 0)
                }
                items(band.items, key = { it.id }) { c ->
                    Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, bottom = 10.dp)) {
                        ConsultationRow(c, rowMode(c, band)) { onOpen(c.id) }
                    }
                }
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

@Composable
private fun PageIntro(filter: ConsultationFilter, sections: SmartSections, showCount: Boolean) {
    val activeCount = sections.needsAction.size + sections.active.size + sections.scheduled.size
    val sub = when (filter) {
        ConsultationFilter.AWAITING_YOU ->
            if (sections.needsAction.isEmpty()) "Your advocate has the ball on everything."
            else "${sections.needsAction.size} thing${if (sections.needsAction.size == 1) "" else "s"} need you. Your advocate has the rest."
        ConsultationFilter.DONE, ConsultationFilter.CONVERTED -> "Matters you've closed out with your chamber."
        else -> "Everything you've raised with your chamber, in one place."
    }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
        Text("MY CONSULTATIONS", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.16.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(9.dp))
        Text("Consultations", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, letterSpacing = (-0.025).em, lineHeight = 30.sp, color = LlColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(sub, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 20.sp, color = LlColor.InkMuted,
            modifier = Modifier.widthIn(max = 330.dp))
        if (showCount) {
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountFrag(activeCount, "active")
                Text("·", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.BorderStrong)
                CountFrag(sections.total, "total")
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
// Sticky filter bar
// =============================================================================

@Composable
private fun FilterBar(
    sections: SmartSections,
    selected: ConsultationFilter,
    onSelect: (ConsultationFilter) -> Unit,
) {
    val counts = mapOf(
        ConsultationFilter.ALL to sections.total,
        ConsultationFilter.ACTIVE to (sections.needsAction.size + sections.active.size + sections.scheduled.size),
        ConsultationFilter.AWAITING_YOU to sections.needsAction.size,
        ConsultationFilter.DONE to (sections.converted.size + sections.closed.size),
    )
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleFilters.forEach { f ->
                FilterChip(
                    label = f.label,
                    count = counts[f] ?: 0,
                    selected = selected == f,
                    claret = f == ConsultationFilter.AWAITING_YOU,
                    onClick = { onSelect(f) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, claret: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) LlColor.Ink else LlColor.Paper)
            .border(LlDimen.HairlineWidth, if (selected) LlColor.Ink else LlColor.Border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (selected) LlColor.Paper else LlColor.InkMuted)
        if (count > 0) {
            val badgeBg = when {
                selected -> LlColor.Paper.copy(alpha = 0.2f)
                claret -> LlColor.Claret
                else -> LlColor.Paper2
            }
            val badgeFg = when {
                selected -> LlColor.Paper
                claret -> Color.White
                else -> LlColor.InkMuted
            }
            Box(
                Modifier.heightIn(min = 17.dp).widthIn(min = 17.dp).clip(RoundedCornerShape(9.dp))
                    .background(badgeBg).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = badgeFg)
            }
        }
    }
}

// =============================================================================
// Section header
// =============================================================================

@Composable
private fun SectionHeader(name: String, count: Int, await: Boolean, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (first) 16.dp else 18.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.5.sp, letterSpacing = 0.15.em,
            color = if (await) LlColor.Claret else LlColor.InkFaint)
        Box(
            Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp))
                .background(if (await) ClaretSoftFix else LlColor.Paper2).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp,
                color = if (await) ClaretMidFix else LlColor.InkMuted)
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

// =============================================================================
// Row card
// =============================================================================

@Composable
private fun ConsultationRow(c: Consultation, mode: RowMode, onClick: () -> Unit) {
    val band = when (mode) {
        RowMode.AWAIT -> LlColor.Claret
        RowMode.LIVE -> LlColor.Verdant
        else -> null
    }
    val done = mode == RowMode.DONE
    Box(
        Modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(if (done) LlColor.Paper1 else LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .clickable(onClick = onClick),
    ) {
        if (band != null) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(LlDimen.HeroStripeWidth).background(band))
        }
        Column(Modifier.padding(start = if (band != null) 18.dp else 15.dp, end = 15.dp, top = 13.dp, bottom = 13.dp)) {
            // top: kind badge + status pill
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                KindBadge(c.kind)
                StatusPill(c.status)
            }
            // title
            Text(
                c.displayTitle(),
                fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp, letterSpacing = (-0.012).em, lineHeight = 20.sp,
                color = if (done) LlColor.InkMuted else LlColor.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            // subline
            Subline(c)
            // per-kind glance
            Glance(c, mode)
            // next-action line (await / live)
            if (mode == RowMode.AWAIT || mode == RowMode.LIVE) {
                NextActionLine(c, mode)
            }
        }
    }
}

@Composable
private fun Subline(c: Consultation) {
    val adv = c.assignedTo?.takeIf { it.isNotBlank() }
    val age = relativeAge(c.lastUpdatedAt ?: c.createdAt)
    if (adv == null && age == null && !c.isUrgent && !c.hasUnreadReplies) return
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (c.hasUnreadReplies) PulseDot(ClaretMidFix, 8.dp)
        if (adv != null) Text(adv, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint, maxLines = 1)
        if (adv != null && age != null) Text("·", fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.BorderStrong)
        if (age != null) Text(age, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint, maxLines = 1)
        if (c.isUrgent) {
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(ClaretSoftFix).padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = ClaretMidFix, modifier = Modifier.size(11.dp))
                Text("NEEDS RESPONSE", fontFamily = SourceSans, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = 0.05.em, color = ClaretMidFix)
            }
        }
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
            Text("${advShort(c.assignedTo)} is in the room", fontFamily = SourceSans, fontWeight = FontWeight.Bold,
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
                    Text("$ready of ${c.totalDocCount} ", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.Ink)
                    Text("ready", fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkMuted)
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
                Text(label, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkMuted)
            }
        }

        c.kind == ConsultationKind.DIGITAL_OFFICE && c.scheduledAt() != null -> {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.Event, null, tint = LlColor.Verdant, modifier = Modifier.size(14.dp))
                Text(scheduledGlance(c), fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.Ink)
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
            Text(text, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = tint)
        }
    }
}

@Composable
private fun KindBadge(kind: ConsultationKind) {
    data class Style(val bg: Color, val fg: Color, val icon: ImageVector, val label: String)
    val s = when (kind) {
        ConsultationKind.GENERAL -> Style(LlColor.Paper2, LlColor.InkMuted, Icons.Outlined.Forum, "General")
        ConsultationKind.AHC -> Style(ClaretSoftFix, LlColor.Claret, Icons.Outlined.Description, "Agreement Check")
        ConsultationKind.LEGAL_NOTICE -> Style(LlColor.SlateSoft, LlColor.SlateMid, Icons.Outlined.Gavel, "Legal Notice")
        ConsultationKind.DOC_REQUEST -> Style(LlColor.VioletSoft, LlColor.Violet, Icons.Outlined.FolderOpen, "Documents")
        ConsultationKind.DIGITAL_OFFICE -> Style(LlColor.VerdantSoft, LlColor.Verdant, Icons.Outlined.Videocam, "Video Session")
    }
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(s.bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(s.icon, null, tint = s.fg, modifier = Modifier.size(12.dp))
        Text(s.label.uppercase(), fontFamily = SourceSans, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 0.06.em, color = s.fg)
    }
}

@Composable
private fun StatusPill(status: ConsultationStatus) {
    val (bg, fg) = when (status) {
        ConsultationStatus.PENDING_REVIEW, ConsultationStatus.AWAITING_DOCS -> LlColor.AmberSoft to LlColor.Amber
        ConsultationStatus.ACKNOWLEDGED, ConsultationStatus.IN_PROGRESS, ConsultationStatus.SCHEDULED -> LlColor.SlateSoft to LlColor.SlateMid
        ConsultationStatus.LIVE_NOW, ConsultationStatus.COMPLETED, ConsultationStatus.CONVERTED -> LlColor.VerdantSoft to LlColor.Verdant
        ConsultationStatus.CLOSED, ConsultationStatus.CANCELLED -> LlColor.Paper2 to LlColor.InkMuted
        ConsultationStatus.DECLINED -> ClaretSoftFix to ClaretMidFix
    }
    val live = status == ConsultationStatus.LIVE_NOW
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (live) PulseDot(fg, 6.dp) else Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(status.label, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = fg)
    }
}

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
                fontFamily = SourceSans, fontSize = 12.sp, lineHeight = 17.sp, color = LlColor.InkMuted,
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
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.VerdantSoft)
                .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            if (filter == ConsultationFilter.AWAITING_YOU) "Nothing needs your attention" else "Nothing in this view",
            fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            when (filter) {
                ConsultationFilter.AWAITING_YOU -> "You're all caught up. Your advocate has the ball on everything that's open — we'll notify you the moment something needs you."
                ConsultationFilter.DONE, ConsultationFilter.CONVERTED -> "No completed or converted matters yet."
                else -> "No consultations match this view."
            },
            fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
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
            Text("VIEW ALL CONSULTATIONS", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
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
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(ClaretSoftFix)
                .border(LlDimen.HairlineWidth, Color(0xFFF4D9DF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Forum, null, tint = LlColor.Claret, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Your chamber is ready when you are", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(
            "You haven't raised anything yet. Ask a question, request a document check, or book a call — your advocate and the team are here.",
            fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onStartNew)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Add, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("START A CONSULTATION", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
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
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
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
