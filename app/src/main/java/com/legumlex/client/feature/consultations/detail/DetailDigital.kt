package com.legumlex.client.feature.consultations.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.components.ConsStatusBadge
import com.legumlex.client.feature.digital_office.DoSession
import com.legumlex.client.feature.digital_office.DoSessionStatus
import com.legumlex.client.ui.components.DetailHeading
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Detail screen for DIGITAL_OFFICE consultations.
 *
 * Walk-in semantics: the session is "right now" — there's no future
 * scheduled time. State is driven by the backing digital_office_sessions
 * row (`doSession`), not by an appointment row. The Join button is hot
 * whenever the session is CONFIRMED or IN_PROGRESS and we have a Meet link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailDigital(
    consultation: Consultation,
    doSession: DoSession?,
    loading: Boolean,
    attachmentsSlot: @Composable () -> Unit = {},
    topBarActions: @Composable () -> Unit = {},
    onEnterLobby: () -> Unit = {},
    onBack: () -> Unit,
    onOpenSpec: () -> Unit = {},
) {
    val spacing = LocalSpacing.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s5)
                .padding(bottom = spacing.s8),
            verticalArrangement = Arrangement.spacedBy(spacing.s4),
        ) {
            DetailHeading(
                eyebrow = "Consultation · #${consultation.id}",
                title = consultation.tag?.takeIf { it.isNotBlank() } ?: "Video Session",
                trailing = {
                    IconButton(onClick = onOpenSpec) {
                        Icon(Icons.Outlined.Info, null, tint = TertiaryText)
                    }
                    topBarActions()
                },
            )
            ConsStatusBadge(consultation.status)

            when {
                loading -> SkeletonHero(spacing)
                doSession != null -> {
                    SessionStateCard(doSession, spacing)
                    EnterLobbySection(doSession, spacing, onEnterLobby)
                    SessionDetailsCard(doSession, spacing)
                }
                else -> NoSessionCard(spacing)
            }

            // Consultation note (the Gemini-summarised brief from intake).
            if (!consultation.note.isNullOrBlank()) {
                Card(
                    colors  = CardDefaults.cardColors(containerColor = Surface1),
                    shape   = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(spacing.s4)) {
                        EyebrowText("What You Told Us", color = TertiaryText)
                        Spacer(Modifier.height(spacing.s2))
                        Text(consultation.note, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    }
                }
            }

            // Attachments — agenda docs / supporting material for the meeting.
            attachmentsSlot()
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Hero state cards — each session status has its own treatment
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionStateCard(s: DoSession, spacing: Spacing) {
    val (icon, tint, title, sub) = when (s.status) {
        DoSessionStatus.IN_PROGRESS, DoSessionStatus.CONFIRMED -> StateInfo(
            icon  = Icons.Outlined.VideoCall,
            tint  = StatusSettled.solid,
            title = "Session Live",
            sub   = "Your advocate is waiting. Tap below to join.",
        )
        DoSessionStatus.AWAITING_INTAKE -> StateInfo(
            icon  = Icons.Outlined.Edit,
            tint  = StatusOngoing.solid,
            title = "Awaiting Brief",
            sub   = "Submit your brief from the Digital Office lobby to continue.",
        )
        DoSessionStatus.PENDING_PAYMENT, DoSessionStatus.APPROVED_AWAITING_PAYMENT -> StateInfo(
            icon  = Icons.Outlined.Payments,
            tint  = StatusOngoing.solid,
            title = "Accepted · Fee Due",
            sub   = "Your advocate has accepted. Pay the fee's invoice on the firm's website; the call opens once it is paid.",
        )
        DoSessionStatus.AWAITING_APPROVAL -> StateInfo(
            icon  = Icons.Outlined.Schedule,
            tint  = StatusOngoing.solid,
            title = "Awaiting Approval",
            sub   = "Your advocate is reviewing your brief" +
                (s.approvalWindowMinutes?.let { " and has $it minutes to respond." } ?: "."),
        )
        DoSessionStatus.DECLINED_BY_ADVOCATE -> StateInfo(
            icon  = Icons.Outlined.Cancel,
            tint  = StatusDanger.solid,
            title = "Advocate Declined",
            sub   = s.failureReason ?: "Your advocate is unable to take this session at present.",
        )
        DoSessionStatus.COMPLETED -> StateInfo(
            icon  = Icons.Outlined.EventAvailable,
            tint  = TertiaryText,
            title = "Session Ended",
            sub   = s.endedAt?.let { "Ended ${formatRelativePast(it)}" } ?: "This session has ended.",
        )
        DoSessionStatus.CANCELLED -> StateInfo(
            icon  = Icons.Outlined.Cancel,
            tint  = StatusDanger.solid,
            title = "Session Cancelled",
            sub   = s.failureReason ?: "This session was cancelled.",
        )
        DoSessionStatus.PAYMENT_FAILED, DoSessionStatus.MEET_FAILED -> StateInfo(
            icon  = Icons.Outlined.ErrorOutline,
            tint  = StatusDanger.solid,
            title = "The Session Could Not Start",
            sub   = s.failureReason ?: "Please contact us so we can connect you with counsel.",
        )
        DoSessionStatus.ADVOCATE_OFFLINE, DoSessionStatus.ADVOCATE_BUSY -> StateInfo(
            icon  = Icons.Outlined.PersonOff,
            tint  = StatusDanger.solid,
            title = "Counsel Unavailable",
            sub   = s.failureReason ?: "Your advocate stepped away before the session could start.",
        )
        DoSessionStatus.EXPIRED -> StateInfo(
            icon  = Icons.Outlined.Timer,
            tint  = TertiaryText,
            title = "Request Lapsed",
            sub   = s.failureReason ?: if (s.approvedAt != null)
                "Your advocate accepted, but the fee was not paid in time, so the request lapsed. No session took place."
            else "Your advocate did not respond in time, so the request lapsed. Nothing was charged.",
        )
        DoSessionStatus.UNKNOWN -> StateInfo(
            icon  = Icons.Outlined.HelpOutline,
            tint  = TertiaryText,
            title = "Session",
            sub   = "This session's status is not available at present.",
        )
    }

    val bg = when (s.status) {
        DoSessionStatus.IN_PROGRESS, DoSessionStatus.CONFIRMED -> StatusSettled.softBg
        DoSessionStatus.PAYMENT_FAILED, DoSessionStatus.MEET_FAILED,
        DoSessionStatus.ADVOCATE_OFFLINE, DoSessionStatus.ADVOCATE_BUSY,
        DoSessionStatus.CANCELLED -> StatusDanger.softBg
        else -> Surface1
    }

    Card(
        colors  = CardDefaults.cardColors(containerColor = bg),
        shape   = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier              = Modifier.padding(spacing.s6).fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(spacing.s3))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tint,
            )
            Spacer(Modifier.height(spacing.s1))
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class StateInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val title: String,
    val sub: String,
)

// ───────────────────────────────────────────────────────────────────────────
// Join button — hot whenever we have a Meet link + session is live or ready
// ───────────────────────────────────────────────────────────────────────────

// Entry into the immersive pre-call lobby — shown whenever the session is live
// or approaching (the lobby owns the queue/countdown + the Google Meet handoff).
@Composable
private fun EnterLobbySection(s: DoSession, spacing: Spacing, onEnterLobby: () -> Unit) {
    val lobbyRelevant = s.status in setOf(
        DoSessionStatus.CONFIRMED,
        DoSessionStatus.IN_PROGRESS,
        DoSessionStatus.AWAITING_APPROVAL,
        DoSessionStatus.APPROVED_AWAITING_PAYMENT,
    )
    if (!lobbyRelevant) return

    val ready = s.status == DoSessionStatus.CONFIRMED || s.status == DoSessionStatus.IN_PROGRESS

    Button(
        onClick = onEnterLobby,
        shape   = RoundedCornerShape(12.dp),
        colors  = ButtonDefaults.buttonColors(
            containerColor = StatusSettled.solid,
            contentColor   = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Outlined.VideoCall, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(spacing.s2))
        Text(
            if (ready) "Enter The Video Lobby" else "Go To The Waiting Lobby",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Session details — advocate, times, fee
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionDetailsCard(s: DoSession, spacing: Spacing) {
    Card(
        colors  = CardDefaults.cardColors(containerColor = Surface1),
        shape   = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s4)) {
            EyebrowText("Session", color = TertiaryText)
            Spacer(Modifier.height(spacing.s3))

            if (s.advocate != null) {
                DetailRow(label = "Advocate", value = s.advocate.name)
            }
            s.startedAt?.let {
                DetailRow(label = "Started", value = formatDateTime(it))
            }
            s.endedAt?.let {
                DetailRow(label = "Ended", value = formatDateTime(it))
            }
            DetailRow(
                label = "Fee",
                value = if (s.sessionFee > 0)
                    com.legumlex.client.feature.finance.formatMoney(s.sessionFee, "₹").removeSuffix(".00")
                else "Complimentary",
            )
            if (!s.failureReason.isNullOrBlank()
                && s.status !in setOf(DoSessionStatus.CONFIRMED, DoSessionStatus.IN_PROGRESS,
                                       DoSessionStatus.COMPLETED)) {
                DetailRow(label = "Note", value = s.failureReason, valueColor = StatusDanger.solid)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = PrimaryText,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TertiaryText)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Empty / loading
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SkeletonHero(spacing: Spacing) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1),
    )
}

@Composable
private fun NoSessionCard(spacing: Spacing) {
    Card(
        colors  = CardDefaults.cardColors(containerColor = Surface1),
        shape   = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier              = Modifier.padding(spacing.s6).fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.HelpOutline, null, tint = TertiaryText, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(spacing.s3))
            Text(
                "No live session attached",
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s1))
            Text(
                "This consultation is not linked to a Digital Office session. " +
                "Contact us to be connected with counsel.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ───────────────────────────────────────────────────────────────────────────

private fun formatDateTime(dt: LocalDateTime): String =
    dt.format(DateTimeFormatter.ofPattern("d MMM · h:mm a"))

private fun formatRelativePast(dt: LocalDateTime): String {
    val now = LocalDateTime.now()
    val mins = java.time.Duration.between(dt, now).toMinutes()
    return when {
        mins < 1     -> "moments ago"
        mins < 60    -> "$mins min ago"
        mins < 1440  -> "${mins / 60} h ago"
        else         -> dt.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}
