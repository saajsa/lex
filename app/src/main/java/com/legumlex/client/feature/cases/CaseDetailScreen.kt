package com.legumlex.client.feature.cases

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ahc.components.LlAvatar
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import com.legumlex.client.ui.caseTitleAnnotated
import com.legumlex.client.ui.formatJudge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Case Detail.html" (Plot Design). The full record of one litigation
// matter — masthead, next-hearing hero (or disposition banner), then tabbed
// panels: Overview · Hearings · Orders · Docs. A matter is advocate-run; the
// client reads, opens, messages, and tracks. App-wide header + nav from
// MainActivity. Cause-titles use the `v.` convention; judges the salutation.
// -----------------------------------------------------------------------------

private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretBorderFix = Color(0xFFF4D9DF)
private val AmberBorderFix = Color(0xFFF3E3C4)
private val VioletSoftBg = Color(0xFFF5F3FF)
private val VioletBorderFix = Color(0xFFE6DDFA)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    caseId: String,
    onBack: () -> Unit = {},   // back is handled by the app-wide header; kept for nav compatibility
    onOpenConsultation: (String) -> Unit = {},
    onOpenDocument: (CaseFile) -> Unit = {},
    onOpenOrderDocument: (CaseOrder) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: CaseDetailViewModel = viewModel(
        key = "case-$caseId",
        factory = viewModelFactory { initializer { CaseDetailViewModel(app = app, caseId = caseId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    var tab by rememberSaveable(caseId) { mutableIntStateOf(0) }

    Scaffold(
        containerColor = LlColor.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is CaseDetailUiState.Loading -> CaseDetailSkeleton()
                is CaseDetailUiState.Error -> CaseDetailError(s.message, vm::load)
                is CaseDetailUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh) {
                    CaseDetailReady(s.detail, s.timeline, tab, { tab = it }, onOpenConsultation, onOpenDocument, onOpenOrderDocument)
                }
            }
        }
    }
}

@Composable
private fun CaseDetailReady(
    detail: CaseDetail,
    timeline: List<TimelineEvent>,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenDocument: (CaseFile) -> Unit,
    onOpenOrderDocument: (CaseOrder) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val disposed = detail.summary.status == CaseStatus.SETTLED || detail.summary.status == CaseStatus.CLOSED
    val nextHearing = remember(detail) {
        detail.upcomingHearings.filter { it.date != null }.minByOrNull { it.date!! }
    }
    val hearingCount = detail.upcomingHearings.size + detail.pastHearings.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
        Masthead(detail)

        if (disposed) {
            DispositionBanner(detail)
        } else if (nextHearing != null) {
            NextHearingHero(nextHearing, detail, today)
        }

        Tabs(tab, onTab, hearingCount, detail.documents.size)

        when (tab) {
            0 -> OverviewPanel(detail, timeline, disposed, onOpenConsultation)
            1 -> HearingsPanel(detail, onOpenOrderDocument)
            else -> DocsPanel(detail, onOpenDocument)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// =============================================================================
// Masthead
// =============================================================================

@Composable
private fun Masthead(detail: CaseDetail) {
    val s = detail.summary
    Column(
        Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp),
    ) {
        // Back is provided by the app-wide header; no in-content breadcrumb.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("MATTER", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.13.em, color = LlColor.InkFaint)
            Text("#${s.id}", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.13.em, color = ClaretMidFix)
        }
        Spacer(Modifier.height(9.dp))
        Text(caseTitleAnnotated(s.title, LlColor.InkFaint), fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, letterSpacing = (-0.022).em, lineHeight = 28.sp, color = LlColor.Ink)
        FlowRowMeta(detail)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowMeta(detail: CaseDetail) {
    val s = detail.summary
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (s.number.isNotBlank()) Text(s.number, fontFamily = SourceSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = LlColor.InkMuted)
        StatusPill(s.status.label, statusTone(s.status))
        val where = listOfNotNull(detail.court, detail.courtNumber?.let { "Ct. $it" }).joinToString(" · ")
        if (where.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.AccountBalance, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
                Text(where, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint)
            }
        }
    }
}

// =============================================================================
// Next-hearing hero
// =============================================================================

@Composable
private fun NextHearingHero(h: HearingRow, detail: CaseDetail, today: LocalDate) {
    val days = h.date?.let { ChronoUnit.DAYS.between(today, it) } ?: 0L
    val soon = days <= 3
    val week = days in 4..7
    val band = when { soon -> ClaretMidFix; week -> LlColor.AmberMid; else -> LlColor.InkFaint }
    val boxBg = when { soon -> ClaretSoftFix; week -> LlColor.AmberSoft; else -> LlColor.Paper1 }
    val boxBorder = when { soon -> ClaretBorderFix; week -> AmberBorderFix; else -> LlColor.Border }
    val numColor = when { soon -> LlColor.Claret; week -> LlColor.Amber; else -> LlColor.Ink }
    val eyebrowColor = when { soon -> LlColor.Claret; week -> LlColor.AmberMid; else -> LlColor.InkMuted }
    val dayWord = when (days) { 0L -> "Today"; 1L -> "Tomorrow"; else -> "In $days days" }

    Box(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .clip(RoundedCornerShape(16.dp)).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp).background(band))
        Column(Modifier.padding(start = 20.dp, end = 18.dp, top = 15.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(band))
                Text("NEXT HEARING · ${dayWord.uppercase()}", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = eyebrowColor)
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(boxBg)
                        .border(LlDimen.HairlineWidth, boxBorder, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (days <= 0L) {
                        Text("Today", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = numColor)
                    } else {
                        Text("$days", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = numColor)
                        Text(if (days == 1L) "DAY" else "DAYS", fontFamily = SourceSans, fontSize = 8.sp, letterSpacing = 0.12.em, color = band, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(hearingDateTime(h), fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 18.sp, color = LlColor.Ink)
                    h.purpose?.let {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("FOR", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.06.em, color = ClaretMidFix,
                                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(ClaretSoftFix).padding(horizontal = 6.dp, vertical = 2.dp))
                            Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = LlColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            val coram = formatJudge(detail.judge, detail.court)
            if (coram != null || detail.courtNumber != null) {
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Gavel, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
                    Text(
                        listOfNotNull(coram, detail.courtNumber?.let { "Court $it" }).joinToString(" · "),
                        fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Disposition banner (closed)
// =============================================================================

@Composable
private fun DispositionBanner(detail: CaseDetail) {
    val s = detail.summary
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .clip(RoundedCornerShape(16.dp)).background(LlColor.VerdantSoft)
            .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Verified, null, tint = LlColor.VerdantMid, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text("DISPOSED · ${(s.disposalType ?: s.status.label).uppercase()}", fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 0.14.em, color = LlColor.VerdantMid)
            Spacer(Modifier.height(5.dp))
            Text(
                listOfNotNull(s.disposalType ?: s.status.label, s.disposalDate?.format(dayYearFmt)).joinToString(" · "),
                fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.01).em, color = LlColor.Verdant, lineHeight = 20.sp,
            )
            detail.disposalReason?.let {
                Spacer(Modifier.height(7.dp))
                Text(it, fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkMuted)
            }
        }
    }
}

// =============================================================================
// Tabs
// =============================================================================

@Composable
private fun Tabs(tab: Int, onTab: (Int) -> Unit, hearings: Int, docs: Int) {
    val labels = listOf("Overview" to 0, "Hearings" to hearings, "Docs" to docs)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(LlColor.Paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            labels.forEachIndexed { i, (label, count) ->
                val on = tab == i
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { onTab(i) }.padding(top = 7.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) LlColor.Ink else LlColor.InkFaint)
                        if (i > 0 && count > 0) Text("$count", fontFamily = SourceSans, fontSize = 9.sp, color = if (on) ClaretMidFix else LlColor.InkFaint)
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (on) LlColor.Claret else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

// =============================================================================
// Overview panel
// =============================================================================

@Composable
private fun OverviewPanel(detail: CaseDetail, timeline: List<TimelineEvent>, disposed: Boolean, onOpenConsultation: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Key details
        Slabel("Key details", first = true)
        Card {
            KdRow(Icons.Outlined.EventAvailable, "Filed", detail.filedDate?.format(dayYearFmt) ?: "—", kdSub(detail))
            KdHair()
            KdRow(Icons.Outlined.AccountBalance, "Court", detail.court ?: "—",
                listOfNotNull(detail.courtNumber?.let { "Court $it" }, detail.benchType).joinToString(" · ").ifBlank { null })
            KdHair()
            KdRow(Icons.Outlined.Gavel, "Presiding", formatJudge(detail.judge, detail.court) ?: "—", null)
            if (detail.acts.isNotEmpty()) {
                KdHair()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    KdIcon(Icons.Outlined.MenuBook)
                    Column(Modifier.weight(1f)) {
                        Text("ACTS & SECTIONS", fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
                        Spacer(Modifier.height(6.dp))
                        FlowRowChips(detail.acts)
                    }
                }
            }
        }

        // Parties
        Slabel("Parties")
        val hasParties = detail.parties.petitioners.isNotEmpty() || detail.parties.respondents.isNotEmpty()
        if (hasParties) {
            Card {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    detail.parties.petitioners.forEach { PartyBlock(it, "Petitioner", opposing = false) }
                    if (detail.parties.petitioners.isNotEmpty() && detail.parties.respondents.isNotEmpty()) PartyVs()
                    detail.parties.respondents.forEach { PartyBlock(it, "Respondent", opposing = true) }
                }
            }
        } else {
            EmptySection(Icons.Outlined.Groups, "No parties listed", "Parties haven't been recorded for this matter yet.")
        }

        // Advocate
        if (!detail.advocate.isNullOrBlank()) {
            Slabel("Your advocate")
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LlAvatar(initials = initialsOf(detail.advocate), size = 46.dp, avatarUrl = detail.advocateAvatarUrl)
                    Column(Modifier.weight(1f)) {
                        Text(detail.advocate, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, letterSpacing = (-0.01).em, color = LlColor.Ink)
                        detail.advocateDesignation?.let { Spacer(Modifier.height(3.dp)); Text(it, fontFamily = SourceSans, fontSize = 11.5.sp, color = LlColor.InkMuted) }
                        detail.advocateEmail?.let { Spacer(Modifier.height(3.dp)); Text(it, fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint) }
                    }
                    val origin = detail.originConsultationId
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(LlColor.Ink)
                            .clickable(enabled = origin != null) { origin?.let(onOpenConsultation) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Forum, null, tint = LlColor.Paper, modifier = Modifier.size(16.dp))
                        Text("Message", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.04.em, color = LlColor.Paper)
                    }
                }
            }
        }

        // Origin
        detail.originConsultationId?.let { cid ->
            Row(
                Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(13.dp)).background(VioletSoftBg)
                    .border(LlDimen.HairlineWidth, VioletBorderFix, RoundedCornerShape(13.dp))
                    .clickable { onOpenConsultation(cid) }.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(LlDimen.HairlineWidth, VioletBorderFix, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NorthEast, null, tint = LlColor.Violet, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("ORIGIN", fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.1.em, color = Color(0xFF7C5FC0))
                    Spacer(Modifier.height(3.dp))
                    Row {
                        Text("Started as Consultation ", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Ink)
                        Text("#$cid", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Violet)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFA48FD6), modifier = Modifier.size(18.dp))
            }
        }

        // Case history
        if (timeline.isNotEmpty()) {
            Slabel("Case history")
            Card { Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
                timeline.forEachIndexed { i, e -> TimelineItem(e, last = i == timeline.lastIndex) }
            } }
        }

        // Rate (closed)
        if (disposed) RateCard()

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PartyBlock(p: Party, defaultRole: String, opposing: Boolean) {
    Column {
        Text((p.role ?: defaultRole).uppercase(), fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(5.dp))
        Text(p.name, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, color = LlColor.Ink)
        p.advocateName?.let {
            Spacer(Modifier.height(4.dp))
            Row {
                Text(if (opposing) "Opposing counsel · " else "Counsel · ", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
                Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
            }
        }
    }
}

@Composable
private fun PartyVs() {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        Text("v.", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = LlColor.InkFaint)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun TimelineItem(e: TimelineEvent, last: Boolean) {
    val tone = timelineTone(e.type)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(13.dp)) {
            Box(Modifier.padding(top = 3.dp).size(13.dp).clip(RoundedCornerShape(50)).background(Color.White).border(2.dp, tone, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(tone))
            }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(LlColor.Paper2))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 2.dp else 16.dp)) {
            Text(e.title, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 17.sp, color = LlColor.Ink)
            e.at?.let { Spacer(Modifier.height(3.dp)); Text(it.format(dayYearFmt), fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint) }
            stripHtml(e.description)?.let {
                Spacer(Modifier.height(5.dp))
                Text(it, fontFamily = SourceSans, fontSize = 11.sp, lineHeight = 16.sp, color = LlColor.InkMuted,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 8.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RateCard() {
    var rating by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("How was your experience?", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
        Spacer(Modifier.height(4.dp))
        Text("This matter is closed. If you'd like, rate how your chamber handled it.", fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 240.dp))
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            (1..5).forEach { i ->
                Icon(Icons.Filled.Star, null, tint = if (i <= rating) LlColor.AmberMid else LlColor.BorderStrong,
                    modifier = Modifier.size(27.dp).clip(RoundedCornerShape(50)).clickable { rating = i })
            }
        }
    }
}

// =============================================================================
// Hearings panel
// =============================================================================

@Composable
private fun HearingsPanel(detail: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit) {
    // Orders are shown inside the hearing they were passed at (matched by date).
    // The final judgement (order_type = final) is excluded — it lives in Docs.
    val inlineOrders = detail.orders.filter { !it.type.equals("final", true) }
    val ordersFor: (HearingRow) -> List<CaseOrder> = { h ->
        inlineOrders.filter { it.date != null && it.date == h.date }
    }
    val matchedIds = (detail.upcomingHearings + detail.pastHearings)
        .flatMap(ordersFor).map { it.id }.toSet()
    val orphanOrders = inlineOrders.filter { it.id !in matchedIds }
    val noHearings = detail.upcomingHearings.isEmpty() && detail.pastHearings.isEmpty()

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (detail.upcomingHearings.isNotEmpty()) {
            Slabel("Upcoming", count = detail.upcomingHearings.size, accent = true, first = true)
            detail.upcomingHearings.forEach { HearingCard(it, ordersFor(it), detail, past = false, onOpenOrderDocument) }
        }
        if (detail.pastHearings.isNotEmpty()) {
            Slabel("Past hearings", count = detail.pastHearings.size, first = detail.upcomingHearings.isEmpty())
            detail.pastHearings.forEach { HearingCard(it, ordersFor(it), detail, past = true, onOpenOrderDocument) }
        }
        // Any order not tied to a listed hearing still surfaces here.
        if (orphanOrders.isNotEmpty()) {
            Slabel("Other orders", count = orphanOrders.size, first = noHearings)
            orphanOrders.forEach { OrderCard(it, detail, onOpenOrderDocument) }
        }
        if (noHearings && orphanOrders.isEmpty()) {
            Slabel("Hearings", first = true)
            EmptySection(Icons.Outlined.Event, "No hearings yet", "Hearing dates, notes and the orders passed at each will appear here.")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HearingCard(h: HearingRow, orders: List<CaseOrder>, detail: CaseDetail, past: Boolean, onOpenOrderDocument: (CaseOrder) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(LlShape.Card)
            .background(if (past) LlColor.Paper1 else LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(h.date?.format(dowFmt) ?: "—", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.01).em, color = if (past) LlColor.InkMuted else LlColor.Ink)
                h.time?.let { Text(it.format(timeFmt), fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkFaint) }
            }
            StatusPill(h.status, hearingTonePill(h.status))
        }
        h.purpose?.let {
            Spacer(Modifier.height(9.dp))
            Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp, color = LlColor.InkMuted)
        }
        // Hearing notes / what happened — sits above the order(s).
        h.description?.let {
            Spacer(Modifier.height(7.dp))
            Text(it, fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkMuted)
        }
        h.adjournmentReason?.let {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(LlColor.AmberSoft).border(LlDimen.HairlineWidth, AmberBorderFix, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(it, fontFamily = SourceSans, fontSize = 11.sp, lineHeight = 16.sp, color = LlColor.Amber)
                h.nextDate?.let { nd -> Spacer(Modifier.height(4.dp)); Text("Next date → ${nd.format(dayYearFmt)}", fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.AmberMid) }
            }
        }
        // Order(s) passed at this hearing — summary + verbatim toggle at the bottom.
        orders.forEach { o -> HearingOrderBlock(o, detail, onOpenOrderDocument) }
    }
}

/** Order rendered inline within its hearing card (no outer card chrome). */
@Composable
private fun HearingOrderBlock(o: CaseOrder, detail: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit) {
    var open by remember(o.id) { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
    Spacer(Modifier.height(11.dp))
    val (tagBg, tagFg, tagText) = orderTag(o.type)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tagText, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.06.em, color = tagFg, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(tagBg).padding(horizontal = 7.dp, vertical = 2.dp))
        o.relief?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Gavel, null, tint = ClaretMidFix, modifier = Modifier.size(12.dp))
                Text(it, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = LlColor.Claret)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OrderSummaryText(o)
    val hasBody = !o.fullText.isNullOrBlank() || !o.summary.isNullOrBlank()
    if (hasBody) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(9.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(9.dp))
                .clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = LlColor.InkMuted, modifier = Modifier.size(15.dp))
            Text(if (open) "Hide order" else "View full order", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.InkMuted)
        }
        AnimatedVisibility(open) { OrderPaper(o, detail, onOpenOrderDocument) }
    } else {
        // No OCR body to expand — surface the document (or its absence) here so
        // the order block never collapses to an unusable badge with no PDF link.
        Spacer(Modifier.height(8.dp))
        OrderDocStatus(o, onOpenOrderDocument)
    }
}

// Tag + accent per order_type (order | interim | final).
private fun orderTag(type: String): Triple<Color, Color, String> = when (type.lowercase()) {
    "interim" -> Triple(AmberSoftFix, LlColor.Amber, "INTERIM ORDER")
    "final"   -> Triple(LlColor.VerdantSoft, LlColor.Verdant, "FINAL ORDER")
    else      -> Triple(LlColor.SlateSoft, LlColor.SlateMid, "ORDER")
}

private val AmberSoftFix = Color(0xFFFFFBEB)

@Composable
private fun OrderCard(o: CaseOrder, detail: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit) {
    var open by remember(o.id) { mutableStateOf(false) }
    val isFinal = o.type.equals("final", true)
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(o.date?.format(dayYearFmt) ?: "—", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
            val (tagBg, tagFg, tagText) = orderTag(o.type)
            Text(tagText, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.06.em, color = tagFg, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(tagBg).padding(horizontal = 7.dp, vertical = 2.dp))
            Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        }
        // Relief badge (e.g. "Stay granted").
        o.relief?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Gavel, null, tint = ClaretMidFix, modifier = Modifier.size(13.dp))
                Text(it, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = LlColor.Claret)
            }
        }
        Spacer(Modifier.height(10.dp))
        OrderSummaryText(o, fontSize = 13.5.sp, lineHeight = 21.sp)
        val nextLine = when {
            o.nextDate != null -> "Next · ${o.nextDate.format(dayYearFmt)}" + (o.purposeNext?.let { " · for $it" } ?: "")
            isFinal -> "Matter disposed — no further dates"
            else -> null
        }
        nextLine?.let {
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (isFinal) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LlColor.InkFaint, modifier = Modifier.size(14.dp))
                Text(it, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkMuted)
            }
        }
        // Expandable verbatim order. Shown when we have either the OCR'd full
        // text or at least a summary to render in the paper frame.
        val hasBody = !o.fullText.isNullOrBlank() || !o.summary.isNullOrBlank()
        if (hasBody) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(9.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(9.dp))
                    .clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = LlColor.InkMuted, modifier = Modifier.size(15.dp))
                Text(if (open) "Hide order" else "View full order", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.InkMuted)
            }
            AnimatedVisibility(open) { OrderPaper(o, detail, onOpenOrderDocument) }
        } else {
            // No OCR body to expand — surface the document (or its absence) so
            // the certified copy stays reachable without a "view full order".
            Spacer(Modifier.height(12.dp))
            OrderDocStatus(o, onOpenOrderDocument)
        }
    }
}

/**
 * The order's summary line. Shows the AI summary when ready; while OCR is still
 * running (ocr_status='pending') it says so rather than implying a permanent gap;
 * a failed extraction and a genuinely empty summary fall back to neutral copy.
 */
@Composable
private fun OrderSummaryText(
    o: CaseOrder,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    when {
        !o.summary.isNullOrBlank() ->
            Text(o.summary, fontFamily = Lora, fontSize = fontSize, lineHeight = lineHeight, color = LlColor.Ink)
        o.ocrStatus == "pending" ->
            Text("Generating summary…", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = fontSize, lineHeight = lineHeight, color = LlColor.InkMuted)
        else ->
            Text("Summary not available yet.", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = fontSize, lineHeight = lineHeight, color = LlColor.InkFaint)
    }
}

/**
 * Document affordance for a court order. Opens the certified-copy PDF when one
 * is on file; otherwise renders an explicit "no certified copy" state so a
 * missing document is surfaced rather than silently omitted.
 */
@Composable
private fun OrderDocStatus(o: CaseOrder, onOpen: (CaseOrder) -> Unit) {
    if (o.hasDocument) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(o) }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.OpenInNew, null, tint = LlColor.Claret, modifier = Modifier.size(14.dp))
            Text("Certified copy", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Claret)
        }
    } else {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.FileDownloadOff, null, tint = LlColor.InkFaint, modifier = Modifier.size(14.dp))
            Text("No certified copy on file", fontFamily = SourceSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = LlColor.InkFaint)
        }
    }
}

@Composable
private fun OrderPaper(o: CaseOrder, detail: CaseDetail, onOpenOrderDocument: (CaseOrder) -> Unit) {
    val coram = o.coram?.takeIf { it.isNotBlank() } ?: formatJudge(detail.judge, detail.court)
    val body = (o.fullText ?: o.summary).orEmpty()
    val paragraphs = body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(11.dp)).background(LlColor.Paper)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp)).padding(15.dp),
    ) {
        // Letterhead
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            detail.court?.let { Text(it, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Ink, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(5.dp))
            Text(listOfNotNull(detail.summary.number.takeIf { it.isNotBlank() }, detail.courtNumber?.let { "Court $it" }).joinToString(" · "),
                fontFamily = SourceSans, fontSize = 9.5.sp, color = LlColor.InkFaint)
            coram?.let {
                Spacer(Modifier.height(7.dp))
                Row { Text("Coram: ", fontFamily = SourceSans, fontSize = 9.5.sp, color = LlColor.InkMuted); Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = LlColor.Ink) }
            }
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Spacer(Modifier.height(12.dp))
        o.date?.let { Text("ORDER · ${it.format(dayYearFmt).uppercase()}", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.1.em, color = ClaretMidFix); Spacer(Modifier.height(8.dp)) }
        // Verbatim body — one block per paragraph.
        paragraphs.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(9.dp))
            Text(p, fontFamily = Lora, fontSize = 12.5.sp, lineHeight = 21.sp, color = LlColor.Ink)
        }
        // Signature + certified copy / missing-document state. Always shown so a
        // missing certified copy is surfaced explicitly rather than omitted.
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            coram?.let { Text(it, fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 11.sp, color = LlColor.InkMuted) }
            OrderDocStatus(o, onOpenOrderDocument)
        }
    }
}

// =============================================================================
// Docs panel
// =============================================================================

@Composable
private fun DocsPanel(detail: CaseDetail, onOpen: (CaseFile) -> Unit) {
    // Split by the document's relationship to the case: filed against the
    // matter itself vs filed against one of its hearings.
    val caseDocs    = detail.documents.filter { it.relation != "hearing" }
    val hearingDocs = detail.documents.filter { it.relation == "hearing" }

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (detail.documents.isEmpty()) {
            Slabel("Case files", first = true)
            EmptySection(Icons.Outlined.FolderOpen, "No documents yet", "Filings, orders and certified copies shared by your chamber will appear here.")
        } else {
            if (caseDocs.isNotEmpty()) {
                Slabel("Filed in the matter", count = caseDocs.size, first = true)
                DocCard(caseDocs, onOpen)
            }
            if (hearingDocs.isNotEmpty()) {
                Slabel("Filed at hearings", count = hearingDocs.size, first = caseDocs.isEmpty())
                DocCard(hearingDocs, onOpen)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DocCard(docs: List<CaseFile>, onOpen: (CaseFile) -> Unit) {
    Card {
        docs.forEachIndexed { i, f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(width = 38.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(ClaretSoftFix).border(LlDimen.HairlineWidth, ClaretBorderFix, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, null, tint = ClaretMidFix, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    // Relation-type label chip (Order / Docket / Legal Notice …).
                    DocTagChip(f.tag)
                    Spacer(Modifier.height(6.dp))
                    Text(f.name, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 17.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val meta = listOfNotNull(
                        f.fileType?.uppercase(),
                        (f.hearingDate ?: f.date)?.format(dayFmt)?.let { if (f.relation == "hearing") "Hearing · $it" else it },
                    ).joinToString("  ·  ")
                    Text(meta, fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint)
                }
                Icon(Icons.Outlined.OpenInNew, null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
            }
            if (i < docs.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        }
    }
}

/** Small label for a document's relation type (its tblfiles tag). */
@Composable
private fun DocTagChip(tag: String) {
    val t = tag.lowercase()
    val (bg, fg) = when {
        t.contains("order")    -> ClaretSoftFix to ClaretMidFix
        t.contains("judg")     -> LlColor.VerdantSoft to LlColor.Verdant
        t.contains("notice")   -> AmberSoftFix to LlColor.Amber
        t.contains("docket")   -> LlColor.SlateSoft to LlColor.SlateMid
        else                   -> LlColor.Paper2 to LlColor.InkMuted
    }
    Text(
        tag.uppercase(),
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.08.em, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Slabel(name: String, count: Int? = null, accent: Boolean = false, first: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = if (first) 14.dp else 18.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = if (accent) LlColor.Claret else LlColor.InkFaint)
        count?.let {
            Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text("$it", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = LlColor.InkMuted)
            }
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card), content = content)
}

@Composable
private fun KdRow(icon: ImageVector, key: String, value: String, sub: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        KdIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(key.uppercase(), fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(3.dp))
            Text(value, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, color = LlColor.Ink)
            sub?.let { Spacer(Modifier.height(3.dp)); Text(it, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkMuted) }
        }
    }
}

@Composable
private fun KdIcon(icon: ImageVector) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = LlColor.InkMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun KdHair() = Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).height(1.dp).background(LlColor.Paper2))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(acts: List<CaseAct>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        acts.forEach { a ->
            Row(
                Modifier.clip(RoundedCornerShape(7.dp)).background(ClaretSoftFix).border(LlDimen.HairlineWidth, ClaretBorderFix, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Bookmark, null, tint = ClaretMidFix, modifier = Modifier.size(12.dp))
                Text(listOfNotNull(a.name, a.sections).joinToString(" · "), fontFamily = SourceSans, fontSize = 10.sp, color = ClaretMidFix)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, tone: Pair<Color, Color>) {
    Row(Modifier.clip(RoundedCornerShape(7.dp)).background(tone.first).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(tone.second))
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = tone.second)
    }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.BorderStrong, LlShape.Card).padding(vertical = 30.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(13.dp))
        Text(title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 240.dp))
    }
}

@Composable
private fun CaseDetailSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp)) {
            Sk(64.dp, 13.dp); Spacer(Modifier.height(13.dp)); Sk(118.dp, 11.dp); Spacer(Modifier.height(11.dp))
            Sk(modifier = Modifier.fillMaxWidth(0.78f), height = 22.dp, radius = 7.dp); Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sk(86.dp, 16.dp); Sk(64.dp, 16.dp, 7.dp) }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp)).padding(start = 20.dp, end = 18.dp, top = 15.dp, bottom = 16.dp)) {
            Sk(120.dp, 11.dp); Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sk(56.dp, 50.dp, 12.dp); Column(Modifier.weight(1f)) { Sk(modifier = Modifier.fillMaxWidth(0.62f), height = 15.dp); Spacer(Modifier.height(8.dp)); Sk(modifier = Modifier.fillMaxWidth(0.42f), height = 11.dp) }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) { repeat(4) { Sk(54.dp, 13.dp) } }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Column(Modifier.padding(16.dp)) {
            Sk(88.dp, 11.dp); Spacer(Modifier.height(12.dp))
            repeat(3) { Sk(modifier = Modifier.fillMaxWidth(), height = 56.dp, radius = 14.dp); Spacer(Modifier.height(11.dp)) }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun CaseDetailError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load this matter", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun hearingDateTime(h: HearingRow): String {
    val d = h.date?.format(dowFmt) ?: "Date TBA"
    return h.time?.let { "$d · ${it.format(timeFmt)}" } ?: d
}

private fun kdSub(detail: CaseDetail): String? {
    val filed = detail.filedDate ?: return "${detail.summary.hearingCount} hearings".takeIf { detail.summary.hearingCount > 0 }
    val days = ChronoUnit.DAYS.between(filed, LocalDate.now()).coerceAtLeast(0)
    val parts = buildList {
        add("Age · $days day${if (days == 1L) "" else "s"}")
        if (detail.summary.hearingCount > 0) add("${detail.summary.hearingCount} hearing${if (detail.summary.hearingCount == 1) "" else "s"}")
    }
    return parts.joinToString(" · ")
}

private fun initialsOf(name: String?): String =
    name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { "?" } ?: "?"

private fun stripHtml(s: String?): String? {
    val t = s?.takeIf { it.isNotBlank() } ?: return null
    return t.replace(Regex("<[^>]+>"), "")
        .replace("&#039;", "'").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&middot;", "·").replace("&nbsp;", " ").trim().ifBlank { null }
}

private fun statusTone(s: CaseStatus): Pair<Color, Color> = when (s) {
    CaseStatus.ONGOING -> LlColor.SlateSoft to LlColor.SlateMid
    CaseStatus.SETTLED -> LlColor.VerdantSoft to LlColor.Verdant
    CaseStatus.PENDING -> LlColor.AmberSoft to LlColor.Amber
    CaseStatus.CLOSED, CaseStatus.UNKNOWN -> LlColor.Paper2 to LlColor.InkMuted
}

private fun hearingTonePill(status: String): Pair<Color, Color> = when (status.lowercase()) {
    "completed" -> LlColor.VerdantSoft to LlColor.Verdant
    "adjourned", "postponed", "scheduled" -> LlColor.AmberSoft to LlColor.Amber
    "cancelled", "no appearance" -> LlColor.Paper2 to LlColor.InkMuted
    "in progress" -> LlColor.SlateSoft to LlColor.SlateMid
    else -> LlColor.SlateSoft to LlColor.SlateMid
}

private fun timelineTone(type: String?): Color {
    val t = type?.lowercase().orEmpty()
    return when {
        t.contains("transfer") -> ClaretMidFix
        t.contains("dispos") || t.contains("settl") || t.contains("complet") -> LlColor.VerdantMid
        t.contains("upgrad") || t.contains("consultation") -> LlColor.Violet
        t.contains("creat") || t.contains("regist") -> LlColor.SlateMid
        else -> LlColor.SlateMid
    }
}
