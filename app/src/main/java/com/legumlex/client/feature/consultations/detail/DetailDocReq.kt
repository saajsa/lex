package com.legumlex.client.feature.consultations.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.components.LlAvatar
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.DocRequestBundle
import com.legumlex.client.feature.consultations.DocRequestItem
import com.legumlex.client.feature.consultations.DocRequestStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// -----------------------------------------------------------------------------
// Screen 08 · ConsultationDetailScreen · Documents  ("Client Consultation
// Documents.html" — Plot Design). A progress-led checklist body inside the
// shared consultation shell. The app-wide header (centred logo) + footer
// (bottom nav) are provided by MainActivity. The advocate uploads the requested
// documents; the client reviews each inline and can mark "Not Available To Me".
// -----------------------------------------------------------------------------

@Composable
fun DetailDocReq(
    consultation: Consultation,
    bundle: DocRequestBundle?,
    loading: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    onDecline: (DocRequestItem) -> Unit = {},
    decliningIds: Set<String> = emptySet(),
    onOpenFile: (DocRequestItem) -> Unit = {},
    onRefresh: () -> Unit = {},
    onMessageAdvocate: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Scaffold(
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        containerColor = LlColor.Paper,
        // The app-wide header already consumes the status-bar inset; without this
        // the nested Scaffold re-adds it as a fixed empty band under the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 22.dp),
        ) {
            // ── masthead (eyebrow + serif title) — sits directly under the
            //    app-wide header, no extra gap ──────────────────────────────
            AppbarBlock(consultation)

            if (loading) {
                LoadingBody()
                return@Column
            }

            val phase = bundle.phase()

            // ── hero status (no gap above — tight to the masthead) ──────────
            Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = 0.dp)) {
                HeroStatus(phase, bundle)
            }

            // ── freshness cue (live, non-nagging) ───────────────────────────
            FreshnessRow(onRefresh)

            // ── progress card (the emotional anchor) ────────────────────────
            if (bundle != null) {
                Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    ProgressCard(bundle)
                }
            }

            // ── warm privacy note (fresh) / celebration (complete) ──────────
            when (phase) {
                DocPhase.FRESH -> Box(
                    Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGapTight),
                ) { WarmNote() }
                DocPhase.COMPLETE -> Box(
                    Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGapTight),
                ) { CelebrateBanner() }
                else -> Unit
            }

            // ── checklist ────────────────────────────────────────────────────
            if (bundle != null && bundle.items.isNotEmpty()) {
                Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    SectionHead("Documents · ${bundle.items.size}")
                    Spacer(Modifier.height(9.dp))
                    // Rows created in the initial request share ~one timestamp;
                    // a row added notably later is one the advocate appended.
                    val baseline = bundle.items.mapNotNull { it.createdAt }.minOrNull()
                    DCard {
                        bundle.items.forEachIndexed { idx, item ->
                            DocRequestRow(
                                item = item,
                                isNew = item.isJustAdded(baseline),
                                declining = item.id in decliningIds,
                                onOpenFile = { onOpenFile(item) },
                                onDecline = { onDecline(item) },
                            )
                            if (idx < bundle.items.lastIndex) HorizontalHair()
                        }
                    }
                }
            } else if (bundle != null) {
                Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    EmptyChecklist()
                }
            }

            // ── advocate note ───────────────────────────────────────────────
            // The request's instructions (the portal's "Notes & Instructions"),
            // or what the advocate wrote for the client.
            val fromAdvocate = consultation.advocateNote?.takeIf { it.isNotBlank() }
            val advNote = fromAdvocate ?: consultation.note?.takeIf { it.isNotBlank() }
            if (advNote != null) {
                Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    SectionHead(if (fromAdvocate != null) "Advocate's Notes" else "Notes & Instructions")
                    Spacer(Modifier.height(9.dp))
                    DCard { AdvocateNote(consultation, advNote) }
                }
            }

            // ── advocate card ───────────────────────────────────────────────
            if (!consultation.assignedTo.isNullOrBlank()) {
                Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    SectionHead("Your advocate")
                    Spacer(Modifier.height(9.dp))
                    DCard { AdvocateCard(consultation, onMessageAdvocate) }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

// =============================================================================
// Phase derivation
// =============================================================================

private enum class DocPhase { FRESH, AWAITING, COMPLETE }

private fun DocRequestBundle?.phase(): DocPhase {
    val b = this ?: return DocPhase.AWAITING
    if (b.total > 0 && b.pending == 0) return DocPhase.COMPLETE
    if (b.uploaded == 0 && b.waived == 0) return DocPhase.FRESH
    return DocPhase.AWAITING
}

// =============================================================================
// Masthead (eyebrow + serif title only — no info / overflow actions)
// =============================================================================

@Composable
private fun AppbarBlock(consultation: Consultation) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(LlColor.VioletSoft)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.Checklist, null, tint = LlColor.Violet, modifier = Modifier.size(13.dp))
                Text(
                    "DOCUMENTS",
                    fontFamily = Inter, fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp, letterSpacing = 0.06.em, color = LlColor.Violet,
                )
            }
            Text(
                "#${consultation.id}",
                fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            consultation.tag?.takeIf { it.isNotBlank() } ?: "Documents requested",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, lineHeight = 24.sp, color = LlColor.Ink,
        )
    }
}

// =============================================================================
// Hero status (left colour band)
// =============================================================================

@Composable
private fun HeroStatus(phase: DocPhase, bundle: DocRequestBundle?) {
    data class HeroSpec(val band: Color, val icon: ImageVector, val title: String, val hint: String)

    val needed = bundle?.pending ?: 0
    val spec = when (phase) {
        DocPhase.COMPLETE -> HeroSpec(
            LlColor.Verdant, Icons.Outlined.TaskAlt, "All documents received",
            "Your advocate has received every document requested. You will be informed of the next step.",
        )
        DocPhase.FRESH -> HeroSpec(
            LlColor.SlateMid, Icons.Outlined.WavingHand, "Your advocate is preparing these",
            "These documents are being collated for your matter. Each may be reviewed here as it is added.",
        )
        DocPhase.AWAITING -> HeroSpec(
            LlColor.AmberMid, Icons.Outlined.FolderOpen, "Documents in progress",
            "${if (needed > 0) "$needed " else ""}document${if (needed == 1) "" else "s"} still being added. Review the ones ready below.",
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(LlDimen.HeroStripeWidth).background(spec.band))
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 15.dp, bottom = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(spec.icon, null, tint = LlColor.Ink, modifier = Modifier.size(19.dp))
                Text(spec.title, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = LlColor.Ink)
            }
            Spacer(Modifier.height(8.dp))
            Text(spec.hint, fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp, color = LlColor.InkMuted)
        }
    }
}

// =============================================================================
// Freshness row
// =============================================================================

@Composable
private fun FreshnessRow(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = LlDimen.SectionGapTight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(LlColor.Verdant))
        Text("Up to date", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.03.em, color = LlColor.InkFaint)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onRefresh)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.InkMuted, modifier = Modifier.size(14.dp))
            Text("Refresh", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkMuted)
        }
    }
}

// =============================================================================
// Progress card
// =============================================================================

@Composable
private fun ProgressCard(bundle: DocRequestBundle) {
    val done = bundle.uploaded + bundle.waived
    val fraction = if (bundle.total > 0) done.toFloat() / bundle.total.toFloat() else 0f
    val complete = fraction >= 1f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done of ${bundle.total} ", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink)
                Text("ready", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.InkMuted)
            }
            Text("${(fraction * 100).toInt()}%", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = if (complete) LlColor.Verdant else LlColor.InkMuted)
        }
        Spacer(Modifier.height(13.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(4.dp)),
            color = if (complete) LlColor.Verdant else LlColor.Ink,
            trackColor = LlColor.Paper2,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LegendItem(LlColor.Verdant, bundle.uploaded, "uploaded")
            LegendItem(LlColor.InkFaint, bundle.waived, "waived")
            LegendItem(LlColor.AmberMid, bundle.pending, "pending")
        }
    }
}

@Composable
private fun LegendItem(dot: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Text("$count", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
        Text(label, fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
    }
}

// =============================================================================
// Warm note (fresh) + Celebrate (complete)
// =============================================================================

@Composable
private fun WarmNote() {
    Row(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.SlateSoft).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = LlColor.SlateMid, modifier = Modifier.size(20.dp))
        Text(
            "Everything here is private to you and your advocate, and stored securely.",
            fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp, color = LlColor.SlateMid,
        )
    }
}

@Composable
private fun CelebrateBanner() {
    Row(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.VerdantSoft)
            .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, LlShape.Card).padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Verdant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Column {
            Text("All Documents Received", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, letterSpacing = (-0.01).em, color = LlColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text("All documents for this matter are in. Tap any to review it.",
                fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted)
        }
    }
}

// =============================================================================
// Checklist row
// =============================================================================

// A row added notably after the initial request batch (advocate appended it
// later). Pending-only — once fulfilled/waived the "new" tint is moot.
private fun DocRequestItem.isJustAdded(baseline: LocalDateTime?): Boolean {
    if (status != DocRequestStatus.PENDING) return false
    val created = createdAt ?: return false
    val base = baseline ?: return false
    return created.isAfter(base.plusHours(1))
}

@Composable
private fun DocRequestRow(
    item: DocRequestItem,
    isNew: Boolean,
    declining: Boolean,
    onOpenFile: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isNew) LlColor.SlateSoft else LlColor.Surface)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                DocRequestStatus.UPLOADED -> Icon(Icons.Filled.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(25.dp))
                DocRequestStatus.WAIVED -> Icon(Icons.Outlined.DoNotDisturbOn, null, tint = LlColor.InkFaint, modifier = Modifier.size(25.dp))
                DocRequestStatus.PENDING -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = LlColor.AmberMid, modifier = Modifier.size(25.dp))
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.documentName,
                    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                    letterSpacing = (-0.003).em,
                    color = if (item.status == DocRequestStatus.WAIVED) LlColor.InkMuted else LlColor.Ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isRequired && item.status == DocRequestStatus.PENDING) RequiredPill()
                if (isNew) NewPill()
            }

            if (!item.description.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(item.description, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkMuted)
            }

            when (item.status) {
                DocRequestStatus.UPLOADED -> {
                    Spacer(Modifier.height(6.dp))
                    FileMeta(item)
                    Spacer(Modifier.height(9.dp))
                    FilePill(item.fulfilledDisplayName ?: item.fulfilledFileName ?: "View document", onOpenFile)
                }
                DocRequestStatus.WAIVED -> {
                    if (!item.waivedReason.isNullOrBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(item.waivedReason, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkFaint)
                    }
                }
                DocRequestStatus.PENDING -> {
                    item.requestedAt?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Requested ${it.format(dayFmt)}", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
                    }
                    Spacer(Modifier.height(11.dp))
                    if (declining) BusyStrip("Updating…") else DeclineButton(onDecline)
                }
            }
        }
    }
}

@Composable
private fun RequiredPill() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(LlColor.AmberSoft).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text("REQUIRED", fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp, letterSpacing = 0.07.em, color = LlColor.Amber)
    }
}

@Composable
private fun NewPill() {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(LlColor.SlateSoft).padding(start = 5.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = LlColor.SlateMid, modifier = Modifier.size(11.dp))
        Text("JUST ADDED", fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp, letterSpacing = 0.06.em, color = LlColor.SlateMid)
    }
}

/** Context line for an uploaded file: kind · size · who · when. */
@Composable
private fun FileMeta(item: DocRequestItem) {
    val parts = buildList {
        item.fileKind?.let { add(kindLabel(it)) }
        item.fileSizeBytes?.let { add(humanSize(it)) }
        when (item.uploadedBy) {
            "you"      -> add("Uploaded by you")
            "advocate" -> add("Added by your advocate")
        }
        item.fulfilledAt?.let { add(it.format(dayFmt)) }
    }
    if (parts.isEmpty()) return
    Text(parts.joinToString("  ·  "), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
}

@Composable
private fun FilePill(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LlColor.VerdantSoft)
            .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Visibility, null, tint = LlColor.Verdant, modifier = Modifier.size(16.dp))
        Text(name, fontFamily = Inter, fontSize = 11.sp, color = LlColor.Verdant, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Text("View", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.Verdant)
    }
}

@Composable
private fun DeclineButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Outlined.Block, null, tint = LlColor.InkFaint, modifier = Modifier.size(16.dp))
        Text("Not Available", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun BusyStrip(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = LlColor.InkMuted)
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = LlColor.InkMuted)
    }
}

// =============================================================================
// Advocate note + card
// =============================================================================

@Composable
private fun AdvocateNote(consultation: Consultation, note: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LlAvatar(
            initials = initialsOf(consultation.assignedTo),
            size = 36.dp,
            avatarUrl = consultation.assignedToAvatarUrl,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(advName(consultation.assignedTo),
                    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = LlColor.Ink)
                consultation.assignedToDesignation?.takeIf { it.isNotBlank() }?.let {
                    Text("· $it", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, maxLines = 1)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(note, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp, color = LlColor.InkMuted)
        }
    }
}

@Composable
private fun AdvocateCard(consultation: Consultation, onMessage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LlAvatar(
            initials = initialsOf(consultation.assignedTo),
            size = 46.dp,
            avatarUrl = consultation.assignedToAvatarUrl,
        )
        Column(Modifier.weight(1f)) {
            Text(advName(consultation.assignedTo),
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
            Text(
                consultation.assignedToDesignation?.takeIf { it.isNotBlank() } ?: "Your assigned advocate",
                fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Ink).clickable(onClick = onMessage)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, null, tint = LlColor.Paper, modifier = Modifier.size(15.dp))
            Text("MESSAGE", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

private fun initialsOf(name: String?): String =
    name?.split(" ")?.filter { it.isNotBlank() }
        ?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { "?" } ?: "?"

/** The advocate's name as recorded (the portal adds no prefix). */
private fun advName(name: String?): String {
    val n = name?.trim()?.takeIf { it.isNotBlank() } ?: return "Your advocate"
    return n   // as recorded, as the portal shows it
}

// =============================================================================
// Shared pieces
// =============================================================================

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun kindLabel(kind: String): String = when (kind) {
    "pdf"   -> "PDF"
    "image" -> "Image"
    else    -> "File"
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    return "${(mb * 10).roundToInt() / 10.0} MB"
}

@Composable
private fun SectionHead(text: String) {
    Text(text.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp, letterSpacing = 0.1.em, color = LlColor.Ink, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun DCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
        content = content,
    )
}

@Composable
private fun HorizontalHair() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
}

@Composable
private fun EmptyChecklist() {
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(vertical = 38.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Checklist, null, tint = LlColor.InkMuted, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("No documents yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        Text("No documents have been shared for this matter yet. You will be notified when they are.",
            fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun LoadingBody() {
    Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
        Column(
            Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
                .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(17.dp),
        ) {
            SkBar(150.dp, 20.dp)
            Spacer(Modifier.height(13.dp))
            SkBar(Modifier.fillMaxWidth(), 9.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { SkBar(74.dp, 11.dp); SkBar(64.dp, 11.dp); SkBar(80.dp, 11.dp) }
        }
        Spacer(Modifier.height(LlDimen.SectionGap))
        Column(
            Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
        ) {
            repeat(3) { i ->
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(LlColor.Paper2))
                    Column(Modifier.weight(1f)) {
                        SkBar(140.dp, 13.dp)
                        Spacer(Modifier.height(7.dp))
                        SkBar(Modifier.fillMaxWidth(), 10.dp)
                    }
                }
                if (i < 2) HorizontalHair()
            }
        }
    }
}

@Composable private fun SkBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) =
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))

@Composable private fun SkBar(modifier: Modifier, height: androidx.compose.ui.unit.Dp) =
    Box(modifier.height(height).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
