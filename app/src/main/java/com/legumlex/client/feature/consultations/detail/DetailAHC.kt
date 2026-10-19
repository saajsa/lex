package com.legumlex.client.feature.consultations.detail

import com.legumlex.client.ahc.theme.LlDimen
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.AhcDetailBody
import com.legumlex.client.ahc.components.IconButtonSquare
import com.legumlex.client.ahc.components.LlAvatar
import com.legumlex.client.ahc.components.StatusChip
import com.legumlex.client.ahc.model.AhcActions
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlText
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ahc.theme.RiskLevel
import com.legumlex.client.feature.consultations.AhcAnalysis
import com.legumlex.client.feature.consultations.awaitingPayment
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.RiskLevel as ConsultRiskLevel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Agreement Health Check detail. Warm-paper header (claret "Agreement Check"
 * chip + status word + serif title, matching the report body) over the design
 * package body (AhcDetailBody), driven by a single AhcUiState. Working wiring —
 * inline viewer, download, overflow menu, file picker, close — is preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailAHC(
    consultation: Consultation,
    ahcData: AhcAnalysis?,
    ahcLoading: Boolean,
    hasAttachments: Boolean = false,
    ahcDocumentLoading: Boolean = false,
    onDownloadDocument: () -> Unit = {},
    onUploadAgreement: () -> Unit = {},
    onOpenInline: () -> Unit = {},
    onOpenLinkedConsultation: (String) -> Unit = {},
    onMessageAdvocate: () -> Unit = {},
    onRequestClose: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBookConsultation: () -> Unit = onMessageAdvocate,
    attachmentsSlot: @Composable () -> Unit = {},
    topBarActions: @Composable () -> Unit = {},
    onBack: () -> Unit,
    onOpenSpec: () -> Unit = {},
) {
    // The agreement always exists once an AHC bundle is present (the funnel
    // stored it before the consultation was created); it lives in the funnel's
    // workflow_data, not tblfiles, so gate Preview on the bundle, not on
    // tblfiles attachments.
    val hasDocument = hasAttachments || ahcData != null

    val remainingSeconds = rememberAhcRemainingSeconds(consultation.createdAt)
    val state = buildAhcUiState(
        consultation = consultation,
        ahc = ahcData,
        ahcLoading = ahcLoading,
        hasDocument = hasDocument,
        remainingSeconds = remainingSeconds,
    )
    val actions = AhcActions(
        onBack = onBack,
        onOverflow = {},
        onPreviewAgreement = onOpenInline,
        onDownloadReport = onDownloadDocument,
        onMessageAdvocate = onMessageAdvocate,
        onTalkToAdvocate = onMessageAdvocate,
        onBookConsultation = onBookConsultation,
        onGoToConsultation = { consultation.linkedConsultationId?.let(onOpenLinkedConsultation) },
        onFindingClick = {},
        onRetry = onRetry,
        onRetrySection = { onRetry() },
        onClose = onRequestClose,
    )

    Scaffold(
        topBar = {
            AhcWarmHeader(
                consultation = consultation,
                ahcData = ahcData,
                ahcLoading = ahcLoading,
                onBack = onBack,
                onOpenLinkedConsultation = onOpenLinkedConsultation,
                topBarActions = topBarActions,
            )
        },
        containerColor = LlColor.Paper,
    ) { inner ->
        if (consultation.funnelAction == "reupload") {
            UnreadableBody(consultation, onUploadAgreement, onMessageAdvocate, Modifier.fillMaxSize().padding(inner))
        } else AhcDetailBody(
            state = state,
            actions = actions,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        )
    }
}

/* ============================================================================
 *  AHC header — implements "AHC Header Designs".
 *
 *  One purpose-built top bar per lifecycle state, built on three layers:
 *    1. Shared spine     — back · "Agreement Check" kind-chip + context stamp ·
 *                          truncating serif title · doc view/download · overflow.
 *    2. Status-rail row  — the only part that changes: a left meta and a right
 *                          signal carrying the state's primary information —
 *                          released → "Health score" + score pill;
 *                          in-review → state chip + live SLA countdown;
 *                          escalated → advocate avatar (+ booked consultation).
 *    3. Signature line   — a 4px band-tinted rail when there's a quantity to
 *                          show (risk % / SLA %), a 1px hairline otherwise.
 *  Color in the header is meaning only; the spine stays ink-on-paper.
 * ========================================================================== */

private const val AHC_SLA_SECONDS = 30 * 60   // 30-minute review window

@Composable
private fun AhcWarmHeader(
    consultation: Consultation,
    ahcData: AhcAnalysis?,
    ahcLoading: Boolean,
    onBack: () -> Unit,
    onOpenLinkedConsultation: (String) -> Unit,
    topBarActions: @Composable () -> Unit = {},
) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val stampFmt = remember { DateTimeFormatter.ofPattern("d MMM, h:mm a") }

    // Lifecycle state, in precedence order. Closed wins over everything; an
    // escalation supersedes a released score; a present score means released.
    val closed = consultation.closedAt != null
    val escalated = !closed && consultation.isEscalated
    val released = !closed && !escalated &&
        (consultation.releasedAt != null || ahcData?.released == true)
    // Unpaid fee invoice: the review has not started, so no SLA clock runs.
    val awaitingPay = consultation.awaitingPayment()
    val loading = !closed && !escalated && !released && ahcLoading && ahcData == null
    val pending = !closed && !escalated && !released && !loading

    // Live, signed SLA seconds (negative once the 30-min window elapses) — drives
    // the countdown chip and the depleting / overdue bottom rail.
    val signed = rememberSignedRemaining(consultation.createdAt)
    val remaining = signed.coerceAtLeast(0)
    val overdueMin = ((-signed) / 60).coerceAtLeast(0)

    val title = ahcData?.agreementName?.takeIf { it.isNotBlank() }
        ?: consultation.tag?.takeIf { it.isNotBlank() }
        ?: "Agreement Check"

    // Released report → clean Screen-07 app bar (eyebrow chip + released stamp +
    // serif title). Back/logo come from the app-wide header above; the verdict,
    // parties, terms and document all live in the body summary below.
    if (released) {
        ReleasedAppBar(title)
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(LlColor.Paper)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // ---------- context card (back lives in the app-wide header) ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LlColor.Surface)
                .border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                // kind chip + context stamp / state badge
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(
                        "Agreement Check",
                        softBg = LlColor.ClaretSoft,
                        text = LlColor.ClaretMid,
                        leadingIcon = Icons.Filled.Description,
                    )
                    when {
                        escalated -> StateChip(
                            label = if (consultation.linkedConsultationId != null) "Booked" else "Escalated",
                            soft = LlColor.SlateSoft,
                            text = LlColor.SlateMid,
                            icon = if (consultation.linkedConsultationId != null)
                                Icons.Filled.EventAvailable else Icons.Filled.RecordVoiceOver,
                        )
                        released -> Text(
                            "Released" + consultation.releasedAt?.let { " ${it.format(timeFmt)}" }.orEmpty(),
                            style = LlText.mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        closed -> Text("Closed", style = LlText.mono)
                        else -> Text(
                            "Submitted" + consultation.createdAt?.let { " ${it.format(timeFmt)}" }.orEmpty(),
                            style = LlText.mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))
                Text(
                    title,
                    style = LlText.appBarTitle.copy(fontSize = 19.5.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // factual one-line description (neutral context, never the verdict).
                // For a released report the synopsis moves into the body score hero,
                // so the header card stays a clean app bar (Screen 07).
                if (!released) ahcData?.agreementSummary?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        it,
                        style = LlText.noteBody.copy(fontSize = 12.5.sp, color = LlColor.InkMuted),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }

                // rich meta — agreement type · parties · submitted · pages/size
                val submitted = consultation.createdAt?.format(stampFmt)
                val docLine = docMetaLine(ahcData)
                val hasMeta = ahcData?.agreementType != null || ahcData?.parties != null ||
                    submitted != null || docLine != null
                // Released reports carry parties/terms/doc in the body summary, so
                // the header drops the stacked meta rows entirely.
                if (!released && hasMeta) {
                    Spacer(Modifier.height(11.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ahcData?.agreementType?.let { MetaRow(Icons.Outlined.Description, it) }
                        ahcData?.parties?.let { MetaRow(Icons.Outlined.Groups, it) }
                        submitted?.let { MetaRow(Icons.Outlined.Schedule, "Submitted $it") }
                        docLine?.let { MetaRow(Icons.Outlined.InsertDriveFile, it) }
                    }
                }

                // ---------- status-rail row (left meta · right signal) ----------
                // Released drops the rail (its score now leads the body hero); the
                // rail still carries countdown / escalation context for live states.
                if (!closed && !released) {
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
                    when {
                        escalated -> EscalatedRail(consultation, onOpenLinkedConsultation)
                        loading   -> LoadingRail()
                        awaitingPay -> AwaitingPaymentRail()
                        consultation.funnelAction == "reupload" -> UnreadableRail(consultation.funnelLabel ?: "We Could Not Read It")
                        else      -> PendingRail(remaining = remaining, overdueMin = overdueMin)
                    }
                }
            }

            // ---------- signature line — 4px band rail flush to the card's bottom edge ----------
            when {
                released -> Unit   // clean app bar for the released report; score leads the body
                escalated -> SignalRail(fill = 1f, color = LlColor.SlateBorder)
                loading   -> SignalRail(fill = 1f, color = LlColor.Paper2)
                pending && awaitingPay -> SignalRail(fill = 1f, color = LlColor.AmberMid)
                pending   ->
                    if (signed > 0) SignalRail(
                        fill = (remaining.toFloat() / AHC_SLA_SECONDS).coerceIn(0f, 1f),
                        color = if (remaining <= 300) LlColor.AmberMid else LlColor.VerdantMid,
                    ) else SignalRail(fill = 1f, color = LlColor.AmberMid)
                else -> Unit   // closed: no signature rail
            }
        }
    }
}

/** Screen 07 app bar for a released report: flat (no card) — "Agreement Check"
 *  chip + released stamp eyebrow, the serif title, and the overflow. Back + logo
 *  are supplied by the app-wide header above. */
@Composable
private fun ReleasedAppBar(
    title: String,
) {
    Column(
        Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
    ) {
        StatusChip(
            "Agreement Check",
            softBg = LlColor.ClaretSoft,
            text = LlColor.ClaretMid,
            leadingIcon = Icons.Filled.Description,
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = LlText.appBarTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** One context line in the header card: faint glyph + muted value. */
@Composable
private fun MetaRow(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(15.dp).padding(top = 1.dp), tint = LlColor.InkFaint)
        Text(
            text,
            style = LlText.label.copy(fontSize = 12.sp, color = LlColor.InkMuted),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "3 pages · 84 KB" from the submitted-document metadata, or null. */
private fun docMetaLine(ahc: AhcAnalysis?): String? {
    val pages = ahc?.agreementPageCount?.takeIf { it > 0 }
        ?.let { "$it page" + if (it == 1) "" else "s" }
    val size = ahc?.agreementSizeBytes?.takeIf { it > 0L }?.let { bytes ->
        val kb = bytes / 1024.0
        if (kb < 1024) "${kb.roundToInt()} KB" else "%.1f MB".format(kb / 1024.0)
    }
    val parts = listOfNotNull(pages, size)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/* ---- status-rail rows ----------------------------------------------------- */

/** Released: "Health score" label + band-colored score pill. */
@Composable
private fun ReleasedRail(ahcData: AhcAnalysis?) {
    val band = healthBand(ahcData?.riskLevel, ahcData?.riskScore)
    RailRow {
        Text(
            "Health score",
            style = LlText.label.copy(fontSize = 11.5.sp, color = LlColor.InkMuted),
            modifier = Modifier.weight(1f),
        )
        ScorePill(score = ahcData?.riskScore ?: 0, band = band)
    }
}

/** Unpaid: the fee's invoice is paid on the website before the review begins. */
private fun titled(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

@Composable
private fun UnreadableRail(label: String) {
    RailRow {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StateChip(label = titled(label), soft = LlColor.AmberSoft, text = LlColor.Amber, icon = Icons.Filled.HourglassTop)
            Text("The review waits for another copy", style = LlText.label.copy(fontSize = 11.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The agreement could not be read (the funnel's unusable / failed step): no
 * clock runs, nothing is "almost there". The portal's own words, and the way
 * to send another copy.
 */
@Composable
private fun UnreadableBody(consultation: Consultation, onUpload: () -> Unit, onMessage: () -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(14.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Description, null, tint = LlColor.Amber, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(titled(consultation.funnelLabel ?: "We could not read it"), style = LlText.emptyTitle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(consultation.funnelDetail ?: "Please upload another copy and we will take a look.", style = LlText.body, textAlign = TextAlign.Center)
        }
        androidx.compose.material3.Button(onClick = onUpload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Send Another Copy")
        }
        androidx.compose.material3.TextButton(onClick = onMessage, modifier = Modifier.fillMaxWidth()) {
            Text("Message Your Advocate")
        }
    }
}

@Composable
private fun AwaitingPaymentRail() {
    RailRow {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateChip(
                label = "Awaiting Payment",
                soft = LlColor.AmberSoft,
                text = LlColor.Amber,
                icon = Icons.Filled.HourglassTop,
            )
            Text(
                "The review starts once the fee invoice is paid",
                style = LlText.label.copy(fontSize = 11.5.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** In review / running late: state chip + reassurance label + live countdown. */
@Composable
private fun PendingRail(remaining: Int, overdueMin: Int) {
    val overdue = remaining <= 0
    RailRow {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateChip(
                label = if (overdue) "Running Late" else "In Review",
                soft = if (overdue) LlColor.AmberSoft else LlColor.VerdantSoft,
                text = if (overdue) LlColor.Amber else LlColor.Verdant,
                icon = if (overdue) Icons.Filled.HourglassTop else Icons.Outlined.Visibility,
            )
            Text(
                if (overdue) "Almost there" else "Report ready soon",
                style = LlText.label.copy(fontSize = 11.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        LiveChip(overdue = overdue, remaining = remaining, overdueMin = overdueMin)
    }
}

/** Escalated: advocate avatar + name, or a booked-consultation jump. */
@Composable
private fun EscalatedRail(consultation: Consultation, onOpenLinkedConsultation: (String) -> Unit) {
    val booked = consultation.linkedConsultationId != null
    val name = consultation.assignedTo?.takeIf { it.isNotBlank() } ?: "Your advocate"
    val initials = name.split(" ").mapNotNull { it.firstOrNull() }
        .take(2).joinToString("").uppercase().ifBlank { "AR" }
    RailRow {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LlAvatar(initials, size = 34.dp, bg = LlColor.Slate, avatarUrl = consultation.assignedToAvatarUrl)
            if (booked) {
                Text(
                    "Consultation #${consultation.linkedConsultationId}",
                    style = LlText.bodyTitle.copy(fontSize = 12.5.sp, color = LlColor.Ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "$name wants to talk this through",
                    style = LlText.label.copy(fontSize = 11.5.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (booked) IconButtonSquare(
            Icons.AutoMirrored.Filled.ArrowForward, "Open consultation",
            { consultation.linkedConsultationId?.let(onOpenLinkedConsultation) },
            tint = LlColor.SlateMid,
        )
    }
}

/** Loading: shimmer-less neutral placeholders in the exact title/score slots. */
@Composable
private fun LoadingRail() {
    RailRow {
        SkeletonBox(width = 84.dp, height = 12.dp)
        SkeletonBox(width = 92.dp, height = 28.dp)
    }
}

/** Shared rail-row scaffold: 11dp top gap (below the card divider), spread. */
@Composable
private fun RailRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Spacer(Modifier.height(11.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/* ---- header primitives ---------------------------------------------------- */

/** .scorepill — band-tinted health-score pill ("88 /100 Low"). */
@Composable
private fun ScorePill(score: Int, band: RiskLevel) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(band.soft)
            .border(1.dp, band.border, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$score", style = LlText.heroVerdict.copy(fontFamily = Inter, fontSize = 18.sp, color = band.solid))
        Text("/100", style = LlText.mono.copy(fontSize = 9.5.sp, color = band.mid))
        Spacer(Modifier.width(2.dp))
        Text(band.label.substringBefore(" risk"), style = LlText.bodyStrong.copy(fontSize = 11.sp, color = band.solid))
    }
}

/** .livechip — pulsing dot + mono countdown (mm:ss) or "+N min" when overdue. */
@Composable
private fun LiveChip(overdue: Boolean, remaining: Int, overdueMin: Int) {
    val soft = if (overdue) LlColor.AmberSoft else LlColor.VerdantSoft
    val border = if (overdue) LlColor.AmberBorder else LlColor.VerdantBorder
    val text = if (overdue) LlColor.Amber else LlColor.Verdant
    val dot = if (overdue) LlColor.AmberMid else LlColor.VerdantMid
    val value = if (overdue) "+$overdueMin min" else "%d:%02d".format(remaining / 60, remaining % 60)
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(soft)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(dot)
        Text(value, style = LlText.mono.copy(fontFamily = Inter, fontSize = 14.sp, color = text))
    }
}

/** .statechip — uppercase soft-bg badge with a leading glyph. */
@Composable
private fun StateChip(label: String, soft: Color, text: Color, icon: ImageVector) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(soft).padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = text)
        Text(label.uppercase(), style = LlText.eyebrow.copy(color = text, fontSize = 10.sp))
    }
}

/** .livechip .pulse — a small dot whose opacity gently breathes. */
@Composable
private fun PulsingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Box(Modifier.size(7.dp).alpha(a).clip(RoundedCornerShape(50)).background(color))
}

/** .rail — 4px signature line flush to the card's bottom edge: band-colored
 *  fill + neutral remainder. The card's clip rounds its bottom corners. */
@Composable
private fun SignalRail(fill: Float, color: Color) {
    val f = fill.coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().height(4.dp)) {
        if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(color))
        if (f < 1f) Box(Modifier.weight(1f - f).fillMaxHeight().background(LlColor.Paper2))
    }
}

/** Neutral placeholder block for the loading header. */
@Composable
private fun SkeletonBox(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
}

/* ---- helpers -------------------------------------------------------------- */

/** Backend `risk_level` is authoritative for color; if absent, derive a band
 *  from the 0–100 health score (higher = safer). */
private fun healthBand(level: ConsultRiskLevel?, score: Int?): RiskLevel {
    // The two RiskLevel enums (data-layer vs. theme palette) share entry names.
    if (level != null) return RiskLevel.valueOf(level.name)
    val s = score ?: return RiskLevel.MEDIUM
    return when {
        s >= 70 -> RiskLevel.LOW
        s >= 40 -> RiskLevel.MEDIUM
        else    -> RiskLevel.HIGH
    }
}

/**
 * Live 30-minute review countdown for the in-review body card. Recomputes every
 * second from `createdAt + 30m`, clamped at zero. Returns 0 with no timestamp.
 */
@Composable
private fun rememberAhcRemainingSeconds(createdAt: LocalDateTime?): Int {
    if (createdAt == null) return 0
    val target = remember(createdAt) { createdAt.plusMinutes(30) }
    val remaining by produceState(initialValue = secondsUntil(target), key1 = target) {
        while (true) {
            value = secondsUntil(target)
            if (value <= 0) break
            delay(1000)
        }
    }
    return remaining
}

private fun secondsUntil(target: LocalDateTime): Int =
    Duration.between(LocalDateTime.now(), target).seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/**
 * Signed SLA seconds for the header — positive while the 30-min window remains,
 * negative once it elapses (so the rail can flip to a calm amber "running late"
 * and the chip can tick "+N min"). Keeps ticking past zero, capped at -24h.
 */
@Composable
private fun rememberSignedRemaining(createdAt: LocalDateTime?): Int {
    if (createdAt == null) return 0
    val target = remember(createdAt) { createdAt.plusMinutes(30) }
    val value by produceState(initialValue = signedSecondsUntil(target), key1 = target) {
        while (true) {
            value = signedSecondsUntil(target)
            delay(1000)
        }
    }
    return value
}

private fun signedSecondsUntil(target: LocalDateTime): Int =
    Duration.between(LocalDateTime.now(), target).seconds.coerceIn(-86_400L, Int.MAX_VALUE.toLong()).toInt()
