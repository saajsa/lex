package com.legumlex.client.feature.consultations.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.ConsultationKind
import com.legumlex.client.feature.consultations.RiskLevel
import com.legumlex.client.feature.consultations.displayTitle
import com.legumlex.client.feature.consultations.scheduledAt
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.PillShape
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusPending
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Type-aware card variants for the Sessions Home hub. Each variant uses the
// same outer 16dp-radius card with a different inner layout per kind.

private val accentBorder = Color(0xFFFED7AA) // matches Card accent in cons.jsx

@Composable
fun SessionCard(
    c: Consultation,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        shape   = RoundedCornerShape(16.dp),
        border  = BorderStroke(1.dp, if (accent) accentBorder else BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (c.kind) {
                ConsultationKind.GENERAL,
                ConsultationKind.LEGAL_NOTICE   -> CardGeneralBody(c)
                ConsultationKind.AHC            -> CardAHCBody(c)
                ConsultationKind.DOC_REQUEST    -> CardDocReqBody(c)
                ConsultationKind.DIGITAL_OFFICE -> CardDigitalBody(c)
            }
        }
    }
}

@Composable
private fun CardGeneralBody(c: Consultation) {
    Row {
        IconTile(Icons.Outlined.Forum)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.assignedTo?.takeIf { it.isNotBlank() } ?: c.displayTitle(),
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = PrimaryText,
                    maxLines = 1,
                )
                if (c.isUrgent) { Spacer(Modifier.width(6.dp)); UrgentBadge() }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                c.note?.takeIf { it.isNotBlank() } ?: c.displayTitle(),
                fontSize = 13.sp,
                color = PrimaryText,
                maxLines = 2,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            MetaRow(
                status = c,
                date   = c.createdAt?.let(::relativeAgoShort) ?: "—",
                replies = c.replyCount,
            )
        }
    }
}

@Composable
private fun CardAHCBody(c: Consultation) {
    Row {
        IconTile(Icons.Outlined.Description)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow("AGREEMENT HEALTH CHECK")
            Spacer(Modifier.height(3.dp))
            Text(
                c.displayTitle(),
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = PrimaryText,
                maxLines = 2,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                c.riskLevel?.let { RiskMiniChip(it) }
                c.flaggedClauseCount?.takeIf { it > 0 }?.let { n ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$n clauses flagged",
                        fontSize = 11.5.sp,
                        color = SecondaryText,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Mini risk thumbnail — 4 stacked dashes
        c.riskLevel?.let { RiskThumbnail(it) }
    }
}

@Composable
private fun CardDocReqBody(c: Consultation) {
    val total = c.totalDocCount.coerceAtLeast(c.pendingDocCount)
    val uploaded = (total - c.pendingDocCount).coerceAtLeast(0)
    Row {
        IconTile(Icons.Outlined.Checklist)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow("DOCUMENTS REQUESTED")
            Spacer(Modifier.height(3.dp))
            Text(
                c.displayTitle(),
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = PrimaryText,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            // Segmented progress
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(total.coerceAtLeast(1)) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < uploaded) PrimaryText else Surface2),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$uploaded of ${total.coerceAtLeast(1)} uploaded",
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = PrimaryText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Upload now →",
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = PrimaryText,
                )
            }
        }
    }
}

@Composable
private fun CardDigitalBody(c: Consultation) {
    val sched = c.scheduledAt()
    val now = LocalDateTime.now()
    val countdown = sched?.let {
        val mins = ChronoUnit.MINUTES.between(now, it)
        when {
            mins < 0 && mins > -60       -> "Live now"
            mins in 0..14                -> "Starts in $mins min"
            mins in 15..59               -> "In ${mins}m"
            mins < 24 * 60               -> "In ${mins / 60}h ${mins % 60}m"
            else                         -> "${ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate())}d away"
        }
    } ?: "Scheduling…"

    Row {
        IconTile(Icons.Outlined.Videocam)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow("VIDEO SESSION")
            Spacer(Modifier.height(3.dp))
            Text(
                c.displayTitle(),
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = PrimaryText,
                maxLines = 2,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = StatusPending.softBg,
                    shape = PillShape,
                ) {
                    Text(
                        countdown.uppercase(),
                        fontFamily = SourceSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = StatusPending.softText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                sched?.let {
                    Text(
                        it.format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")),
                        fontFamily = SourceSans,
                        fontSize = 11.sp,
                        color = SecondaryText,
                    )
                }
            }
        }
    }
}

// --- Atoms inside cards --------------------------------------------------

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = PrimaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text,
        fontFamily = SourceSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.0.sp,
        color = SecondaryText,
    )
}

@Composable
private fun MetaRow(status: Consultation, date: String, replies: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConsStatusBadge(status.status, compact = true)
        Dot()
        Text(
            date,
            fontFamily = SourceSans,
            fontSize = 11.sp,
            color = SecondaryText,
        )
        if (replies > 0) {
            Dot()
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                null,
                modifier = Modifier.size(12.dp),
                tint = SecondaryText,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                replies.toString(),
                fontSize = 11.sp,
                color = SecondaryText,
            )
        }
    }
}

@Composable
private fun Dot() {
    Spacer(Modifier.width(10.dp))
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(TertiaryText),
    )
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun RiskMiniChip(level: RiskLevel) {
    val (bg, dot, fg) = riskColors(level)
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(
                level.name,
                fontFamily = SourceSans,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = fg,
            )
        }
    }
}

@Composable
private fun RiskThumbnail(level: RiskLevel) {
    // 4 stacked dashes, top to bottom: CRITICAL/HIGH/MEDIUM/LOW. Only the
    // current band is colored — others muted.
    val order = listOf(RiskLevel.CRITICAL, RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.LOW)
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxHeight(),
    ) {
        order.forEach { lv ->
            val isActive = lv == level
            val color = if (isActive) riskColors(lv).second else Surface2
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

internal fun riskColors(level: RiskLevel): Triple<Color, Color, Color> = when (level) {
    RiskLevel.LOW      -> Triple(Color(0xFFD1FAE5), Color(0xFF059669), Color(0xFF065F46))
    RiskLevel.MEDIUM   -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFF92400E))
    RiskLevel.HIGH     -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), Color(0xFF9A3412))
    RiskLevel.CRITICAL -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), Color(0xFF991B1B))
}

private fun relativeAgoShort(ts: LocalDateTime): String {
    val now = LocalDateTime.now()
    val mins = ChronoUnit.MINUTES.between(ts, now)
    return when {
        mins < 1               -> "now"
        mins < 60              -> "${mins}m"
        mins < 60 * 24         -> "${mins / 60}h"
        mins < 60 * 24 * 7     -> "${mins / (60 * 24)}d"
        else                   -> ts.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}
