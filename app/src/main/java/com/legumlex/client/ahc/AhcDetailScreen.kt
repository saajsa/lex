package com.legumlex.client.ahc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.components.*
import com.legumlex.client.ahc.model.*
import com.legumlex.client.ahc.theme.*

/* ============================================================================
 *  AhcDetailScreen — the single entry point. Renders ONE AhcUiState across the
 *  whole AHC lifecycle: Pending (+overdue) / Released / Escalated / Loading /
 *  Empty / Error. One vertical scroll of cards; the app bar is shared.
 *
 *  Layout rules honored:
 *   • Top app bar = back + agreement name (truncating) + overflow.
 *   • Risk score anchors RELEASED; countdown anchors PENDING.
 *   • Color carries meaning only; body stays ink-on-paper.
 * ========================================================================== */
@Composable
fun AhcDetailScreen(
    state: AhcUiState,
    actions: AhcActions,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(LlColor.Paper),                 // --paper screen bg
    ) {
        when (state) {
            is AhcUiState.Loading   -> LoadingScreen(actions, showTopBar)
            is AhcUiState.Pending   -> PendingScreen(state, actions, showTopBar)
            is AhcUiState.Released  -> ReleasedScreen(state, actions, showTopBar)
            is AhcUiState.Escalated -> EscalatedScreen(state, actions, showTopBar)
            is AhcUiState.Empty     -> EmptyScreen(state, actions, showTopBar)
            is AhcUiState.Error     -> ErrorScreen(state, actions, showTopBar)
            is AhcUiState.Closed    -> ClosedScreen(state, actions, showTopBar)
        }
    }
}

/**
 * Embeddable variant — renders the same per-state body WITHOUT the package's
 * own AhcTopBar, for hosting inside a screen that already provides a top bar /
 * Scaffold (e.g. ConsultationDetailScreen → DetailAHC). Keeps the warm-paper
 * surface and all per-state content/CTAs.
 */
@Composable
fun AhcDetailBody(
    state: AhcUiState,
    actions: AhcActions,
    modifier: Modifier = Modifier,
) = AhcDetailScreen(state, actions, modifier, showTopBar = false)

/* Shared eyebrow chip + status label used by the app bar. */
@Composable
private fun AhcEyebrow(statusLabel: String?, statusColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(
            "Agreement Check",
            softBg = LlColor.ClaretSoft, text = LlColor.ClaretMid,    // brand = claret
            leadingIcon = Icons.Default.Description,
        )
        if (statusLabel != null) {
            Spacer(Modifier.width(7.dp))
            Text(statusLabel, style = LlText.mono.copy(color = statusColor))
        }
    }
}

private val SectionSpacing = 22.dp

/* --------------------------------------------------------------------------
 *  STATE 1 · PENDING (in-review) — countdown is the anchor. + overdue variant.
 * ------------------------------------------------------------------------ */
@Composable
private fun PendingScreen(state: AhcUiState.Pending, actions: AhcActions, showTopBar: Boolean = true) {
    val mm = state.remainingSeconds.coerceAtLeast(0) / 60
    val ss = state.remainingSeconds.coerceAtLeast(0) % 60
    val timeLabel = "%02d:%02d".format(mm, ss)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showTopBar) item {
            AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow) {
                AhcEyebrow(if (state.isOverdue) "Running late" else null, LlColor.AmberMid)
            }
        }
        // anchor: countdown
        sectionItem(tight = true) {
            CountdownCard(
                ringFraction = state.ringFraction,
                timeLabel = timeLabel,
                submittedAtLabel = state.submittedAtLabel,
                etaLabel = state.etaLabel,
                isOverdue = state.isOverdue,
            )
        }
        // overdue: offer a way to reach the advocate (no alarm)
        if (state.isOverdue) {
            sectionItem { ContextStrip("Need it sooner? You can message your advocate directly any time.") }
        }
        // what you submitted (+ Preview only if has_document)
        if (state.doc.hasDocument) {
            sectionItem {
                SectionHeader("What you submitted", trailing = { DLink("Preview", actions.onPreviewAgreement) })
                Spacer(Modifier.height(9.dp))
                SubmittedDocRow(state.doc, actions.onPreviewAgreement)
            }
        }
        // reassuring 3-step "what happens next"
        sectionItem {
            SectionHeader("What happens next")
            Spacer(Modifier.height(9.dp))
            WhatHappensNext(
                listOf(
                    NextStep("Received and queued", StepState.DONE),
                    NextStep("Lex reads your agreement", StepState.NOW),
                    NextStep("Advocate reviews & releases your report", StepState.TODO),
                )
            )
        }
        advocateSection(state.advocate, actions)
    }
}

/* --------------------------------------------------------------------------
 *  STATE 2 · RELEASED (primary) — risk hero anchors; scannable sections.
 * ------------------------------------------------------------------------ */
@Composable
private fun ReleasedScreen(state: AhcUiState.Released, actions: AhcActions, showTopBar: Boolean = true) {
    val issues = state.keyRisks.size + state.missingProtections.size
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showTopBar) item {
            AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow) {
                AhcEyebrow("Released ${state.releasedAtLabel}", LlColor.VerdantMid)
            }
        }
        // anchor: health-score verdict hero (Screen 07 — score leads)
        sectionItem(tight = true) {
            HealthScoreHero(state.riskScore, state.riskLevel, state.teaserSummary, state.releasedAtLabel)
        }
        // advocate's note (distinct, human) — only if present
        state.advocateNote?.let { note ->
            sectionItem {
                SectionHeader("Your advocate's note")
                Spacer(Modifier.height(9.dp))
                AdvocateNoteCard(note, accent = LlColor.Claret)
            }
        }
        // parties — vs pair (Screen 07)
        if (state.parties.isNotEmpty()) {
            sectionItem {
                SectionHeader("Parties")
                Spacer(Modifier.height(9.dp))
                PartiesVsCard(state.parties)
            }
        }
        // key terms — clean key-value block (Screen 07)
        if (state.keyTerms.isNotEmpty()) {
            sectionItem {
                SectionHeader("Key terms")
                Spacer(Modifier.height(9.dp))
                KeyTermsKvCard(state.keyTerms)
            }
        }
        // key risks
        if (state.keyRisks.isNotEmpty()) {
            sectionItem {
                SectionHeader("Key risks · ${state.keyRisks.size}")
                Spacer(Modifier.height(9.dp))
                FindingList(state.keyRisks, actions.onFindingClick)
            }
        }
        // missing protections (missing_clauses)
        if (state.missingProtections.isNotEmpty()) {
            sectionItem {
                SectionHeader("Missing protections · ${state.missingProtections.size}")
                Spacer(Modifier.height(9.dp))
                FindingList(state.missingProtections, actions.onFindingClick)
            }
        }
        // empty findings, done with dignity — only for a real (scored) verdict,
        // never for an incomplete/unscored release.
        if (state.riskScore != null && state.keyRisks.isEmpty() && state.missingProtections.isEmpty()) {
            sectionItem {
                SectionHeader("Findings")
                Spacer(Modifier.height(9.dp))
                NoIssuesCard()
            }
        }
        // recommended next steps
        if (state.recommendations.isNotEmpty()) {
            sectionItem {
                SectionHeader("Recommended next steps")
                Spacer(Modifier.height(9.dp))
                RecommendationList(state.recommendations)
            }
        }
        // non-fatal: a section failed to load
        state.partialFailureSection?.let { sec ->
            sectionItem { PartialFailureBlock(sec, { actions.onRetrySection(sec) }) }
        }
        // persistent Preview agreement + download
        if (state.doc.hasDocument) {
            sectionItem {
                SectionHeader("The agreement")
                Spacer(Modifier.height(9.dp))
                SubmittedDocRow(state.doc, actions.onPreviewAgreement)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LlGhostButton("Open original", actions.onPreviewAgreement,
                        modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Description, small = true)
                    LlGhostButton("Download report", actions.onDownloadReport,
                        modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Download, small = true)
                }
            }
        }
        // activity
        if (state.activity.isNotEmpty()) {
            sectionItem {
                SectionHeader("Activity")
                Spacer(Modifier.height(9.dp))
                ActivityTimeline(state.activity)
            }
        }
        advocateSection(state.advocate, actions)
    }
}

/* --------------------------------------------------------------------------
 *  Screen 07 · redesigned document summary — health-score verdict hero, a vs
 *  party pair, and a clean key-value terms block. Faithful to the source.
 * ------------------------------------------------------------------------ */

/** Health-score verdict hero: band-tinted card, score leads, then the verdict
 *  + synopsis, with a reviewed sub-line. Neutral fallback when unscored. */
@Composable
private fun HealthScoreHero(score: Int?, level: RiskLevel?, teaser: String, releasedAtLabel: String) {
    // Colour the hero from the score itself when an explicit band wasn't supplied,
    // so a released check is always flat + coloured (never bare Paper1).
    val band = level ?: score?.takeIf { it in 0..100 }?.let { RiskLevel.fromMagnitude(it) }
    val hasVerdict = score != null && score in 0..100 && band != null
    val soft = band?.soft ?: LlColor.Paper1
    val border = band?.border ?: LlColor.Border
    val mid = band?.mid ?: LlColor.InkMuted
    Box(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(soft)
            .border(LlDimen.HairlineWidth, border, LlShape.Card).padding(17.dp),
    ) {
        Column {
            Text("HEALTH SCORE", style = LlText.eyebrow.copy(color = mid, fontSize = 9.5.sp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasVerdict) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", style = LlText.heroScore.copy(color = band!!.solid))
                        Text("/ 100", style = LlText.mono.copy(fontSize = 10.sp))
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(band?.label ?: "Reviewed", style = LlText.heroVerdict)
                    if (teaser.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(teaser, style = LlText.body.copy(color = LlColor.InkMuted))
                    }
                }
            }
        }
    }
}

/** Parties as a serif vs pair (left role+name · "vs" · right role+name). Falls
 *  back to a stacked list when there aren't exactly two sides. */
@Composable
private fun PartiesVsCard(parties: List<Party>) {
    LlCardBody {
        if (parties.size >= 2) {
            val a = parties[0]
            val b = parties[1]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.role.uppercase(), style = LlText.eyebrow.copy(fontSize = 9.sp, color = LlColor.ClaretMid))
                    Spacer(Modifier.height(5.dp))
                    Text(a.name, style = LlText.heroVerdict.copy(fontSize = 14.sp))
                }
                Text("vs", style = LlText.heroVerdict.copy(fontSize = 13.sp, color = LlColor.InkFaint, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(b.role.uppercase(), style = LlText.eyebrow.copy(fontSize = 9.sp, color = LlColor.InkFaint))
                    Spacer(Modifier.height(5.dp))
                    Text(b.name, style = LlText.heroVerdict.copy(fontSize = 14.sp), textAlign = TextAlign.End)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                parties.forEach { p ->
                    Column {
                        Text(p.role.uppercase(), style = LlText.eyebrow.copy(fontSize = 9.sp, color = LlColor.InkFaint))
                        Spacer(Modifier.height(5.dp))
                        Text(p.name, style = LlText.heroVerdict.copy(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

/** Key terms as a clean key-value block; emphasised values render mono + large
 *  (₹ amounts). Mirrors the source summary's terms list. */
@Composable
private fun KeyTermsKvCard(terms: List<KeyTerm>) {
    LlCardBody {
        Column {
            terms.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(t.key, style = LlText.label)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        t.value,
                        style = if (t.emphasis) LlText.countdownNum.copy(fontSize = 15.sp)
                        else LlText.bodyTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        textAlign = TextAlign.End,
                    )
                }
                if (i != terms.lastIndex) LlDivider()
            }
        }
    }
}

/* --------------------------------------------------------------------------
 *  STATE 3 · ESCALATED — calm slate redirection; no risk score. Booking leads.
 * ------------------------------------------------------------------------ */
@Composable
private fun EscalatedScreen(state: AhcUiState.Escalated, actions: AhcActions, showTopBar: Boolean = true) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showTopBar) item {
            AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow) {
                AhcEyebrow("Escalated", LlColor.SlateMid)
            }
        }
        // anchor: slate panel — advocate wants to talk
        sectionItem(tight = true) {
            Row(
                Modifier.fillMaxWidth().clip(LlShape.Card)
                    .background(LlColor.SlateSoft)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Slate),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color.White) }
                Column {
                    Text(
                        "${state.advocate.name} would like to discuss this in person",
                        style = LlText.bodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "After reading the contract, your advocate prefers to walk you through it directly rather than release a written summary.",
                        style = LlText.body,
                    )
                }
            }
        }
        // why (escalation reason) — slate-accented note
        sectionItem {
            SectionHeader("Why")
            Spacer(Modifier.height(9.dp))
            AdvocateNoteCard(
                AdvocateNote(
                    paragraphs = listOf("\u201C${state.escalationReason}\u201D"),
                    signedBy = state.advocate.name, signedAt = state.escalatedAtLabel,
                ),
                accent = LlColor.SlateMid,
            )
        }
        // CTA depends on whether a consultation is already booked
        when (val b = state.booking) {
            is EscalationBooking.AwaitingBooking -> {
                sectionItem {
                    LlPrimaryButton(
                        "Book a consultation about this", actions.onBookConsultation,
                        modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Forum,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Opens a new request with this agreement attached.",
                        style = LlText.label.copy(color = LlColor.InkFaint),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            }
            is EscalationBooking.Booked -> {
                sectionItem {
                    LlCard {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.SlateSoft),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Forum, null, Modifier.size(20.dp), tint = LlColor.SlateMid) }
                            Column(Modifier.weight(1f)) {
                                Text("Consultation ${b.reference}", style = LlText.bodyTitle.copy(fontSize = 12.5.sp))
                                Spacer(Modifier.height(4.dp))
                                Text(b.whenLabel, style = LlText.label.copy(fontSize = 11.sp))
                            }
                            LlPrimaryButton("Go to it", actions.onGoToConsultation, small = true)
                        }
                    }
                }
            }
        }
        // explain why the AHC isn't closed
        sectionItem {
            ContextStrip("This Agreement Check stays open until the consultation wraps up.")
        }
        if (state.activity.isNotEmpty()) {
            sectionItem {
                SectionHeader("Activity")
                Spacer(Modifier.height(9.dp))
                ActivityTimeline(state.activity)
            }
        }
        advocateSection(state.advocate, actions)
    }
}

/* --------------------------------------------------------------------------
 *  STATE 5 · CLOSED (terminal) — subdued recap; no severity, ending timeline.
 * ------------------------------------------------------------------------ */
@Composable
private fun ClosedScreen(state: AhcUiState.Closed, actions: AhcActions, showTopBar: Boolean = true) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showTopBar) item {
            AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow) {
                AhcEyebrow("Closed", LlColor.InkMuted)
            }
        }
        // anchor: close hero (manages its own padding)
        item { CloseHero(state.kind, state.title, state.subtitle, state.closedOnLabel) }

        if (state.recap.isNotEmpty()) {
            sectionItem {
                SectionHeader("Recap")
                Spacer(Modifier.height(9.dp))
                RecapCard(state.recap)
            }
        }
        state.link?.let { link ->
            sectionItem {
                SectionHeader(if (link.target == ClosureLinkTarget.CASE) "Now a case" else "Resolved by")
                Spacer(Modifier.height(9.dp))
                LinkCard(link, actions.onGoToConsultation)
            }
        }
        state.reportDoc?.let { doc ->
            sectionItem {
                SectionHeader("Report")
                Spacer(Modifier.height(9.dp))
                SubmittedDocRow(doc, actions.onPreviewAgreement)
            }
        }
        state.stripText?.let { strip ->
            sectionItem { ContextStrip(strip, neutral = true) }
        }
        if (state.activity.isNotEmpty()) {
            sectionItem {
                SectionHeader("Activity")
                Spacer(Modifier.height(9.dp))
                ActivityTimeline(state.activity)
            }
        }
        sectionItem {
            SectionHeader("Your advocate")
            Spacer(Modifier.height(9.dp))
            AdvocateRow(state.advocate, actions.onMessageAdvocate, readOnly = true)
        }
    }
}

/* --------------------------------------------------------------------------
 *  STATE 4a · LOADING — skeletons in the shape of the released layout.
 * ------------------------------------------------------------------------ */
@Composable
private fun LoadingScreen(actions: AhcActions, showTopBar: Boolean = true) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (showTopBar) item { AhcTopBar("Loading…", actions.onBack, actions.onOverflow) }
        sectionItem(tight = true) { SkeletonBlock(96.dp) }
        sectionItem { SkeletonLine(110.dp); Spacer(Modifier.height(9.dp)); SkeletonBlock(120.dp) }
        sectionItem { SkeletonLine(80.dp); Spacer(Modifier.height(9.dp)); SkeletonBlock(150.dp) }
    }
}

/* --------------------------------------------------------------------------
 *  STATE 4b · EMPTY — submitted but no analysis exists yet (friendly).
 * ------------------------------------------------------------------------ */
@Composable
private fun EmptyScreen(state: AhcUiState.Empty, actions: AhcActions, showTopBar: Boolean = true) {
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Description, null, Modifier.size(27.dp), tint = LlColor.InkMuted) }
            Spacer(Modifier.height(16.dp))
            Text("No report yet", style = LlText.emptyTitle)
            Spacer(Modifier.height(7.dp))
            Text(
                "We haven't started the review for this agreement. It'll appear here the moment it's underway.",
                style = LlText.body, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 250.dp),
            )
        }
    }
}

/* --------------------------------------------------------------------------
 *  STATE 4c · ERROR — recoverable full-screen error.
 * ------------------------------------------------------------------------ */
@Composable
private fun ErrorScreen(state: AhcUiState.Error, actions: AhcActions, showTopBar: Boolean = true) {
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) AhcTopBar(state.agreementName, actions.onBack, actions.onOverflow)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Refresh, null, Modifier.size(34.dp), tint = LlColor.InkMuted) }
            Spacer(Modifier.height(22.dp))
            Text("We couldn't load this", style = LlText.errorTitle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Something went wrong fetching your report. Check your connection and try again.",
                style = LlText.body, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 270.dp),
            )
            Spacer(Modifier.height(26.dp))
            LlPrimaryButton("Try again", actions.onRetry, leadingIcon = Icons.Default.Refresh)
            Spacer(Modifier.height(18.dp))
            Text(state.code, style = LlText.mono)
        }
    }
}

/* ---- shared bits ---------------------------------------------------------- */

private fun androidx.compose.foundation.lazy.LazyListScope.advocateSection(
    advocate: Advocate, actions: AhcActions,
) {
    sectionItem {
        SectionHeader("Your advocate")
        Spacer(Modifier.height(9.dp))
        AdvocateRow(advocate, actions.onMessageAdvocate)
    }
}

/** Wraps an item in the standard .dsec horizontal padding + top gap. */
private fun androidx.compose.foundation.lazy.LazyListScope.sectionItem(
    tight: Boolean = false,
    content: @Composable () -> Unit,
) = item {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LlDimen.ScreenPad)
            .padding(top = if (tight) LlDimen.SectionGapTight else SectionSpacing),
    ) { content() }
}

@Composable
private fun DLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = LlText.bodyTitle.copy(fontSize = 11.sp, color = LlColor.InkMuted),
        modifier = Modifier
            .clip(LlShape.Inset)
            .clickable(onClick = onClick)
            .padding(4.dp),
    )
}

/* ---- Skeletons (loading state) ------------------------------------------- */
@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(LlShape.Card)
            .background(LlColor.Surface)
            .padding(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
    }
}

@Composable
private fun SkeletonLine(width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).height(12.dp).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
}
