package com.legumlex.client.feature.hearings

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.StatusPending
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Screen 15 — handoff §Hearing Detail.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearingDetailScreen(
    hearingId: String,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: HearingDetailViewModel = viewModel(
        key     = "hearing-$hearingId",
        factory = viewModelFactory {
            initializer { HearingDetailViewModel(app = app, hearingId = hearingId) }
        },
    )
    val state by vm.state.collectAsState()

    Scaffold(
        bottomBar = {
            if (state is HearingDetailUiState.Ready) {
                StickyActions()
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is HearingDetailUiState.Loading -> HearingDetailSkeleton()
                is HearingDetailUiState.Error   -> HearingDetailError(s.message, vm::load)
                is HearingDetailUiState.Ready   -> HearingDetailContent(s.hearing, onOpenCase = onOpenCase)
            }
        }
    }
}

// --- Ready content -------------------------------------------------------

@Composable
private fun HearingDetailContent(h: Hearing, onOpenCase: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s2, bottom = spacing.s5),
    ) {
        DateBlock(h)

        Spacer(Modifier.height(spacing.s5))

        LinkedCaseCard(h, onClick = { onOpenCase(h.caseId) })

        Spacer(Modifier.height(spacing.s5))

        EyebrowText("Court")
        Spacer(Modifier.height(spacing.s2))
        CourtCard(h)

        if (!h.purpose.isNullOrBlank()) {
            Spacer(Modifier.height(spacing.s5))
            EyebrowText("Purpose")
            Spacer(Modifier.height(spacing.s2))
            Text(
                h.purpose,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText,
            )
        }

        Spacer(Modifier.height(spacing.s5))
        EyebrowText("Status")
        Spacer(Modifier.height(spacing.s2))
        StatusCard(h)
    }
}

@Composable
private fun DateBlock(h: Hearing) {
    val spacing = LocalSpacing.current
    val today = LocalDate.now()
    val daysOut = h.date?.let { ChronoUnit.DAYS.between(today, it).toInt() }

    val (countdownLabel, dotColor) = when {
        daysOut == null     -> null to TertiaryText
        daysOut <  0        -> "Past"     to TertiaryText
        daysOut == 0        -> "Today"    to StatusDanger.solid
        daysOut == 1        -> "Tomorrow" to StatusDanger.solid
        daysOut <= 7        -> "In $daysOut days" to StatusPending.solid
        else                -> "In $daysOut days" to TertiaryText
    }

    Column {
        if (countdownLabel != null) {
            Surface(
                color = AccentInk,
                contentColor = InverseText,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        countdownLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = InverseText,
                    )
                }
            }
            Spacer(Modifier.height(spacing.s3))
        }
        Text(
            text = h.date?.format(DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "Date TBA",
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            letterSpacing = (-1.08).sp,
            color = PrimaryText,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = h.time?.format(DateTimeFormatter.ofPattern("h:mm a 'IST'")) ?: "Time TBA",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = SecondaryText,
        )
    }
}

@Composable
private fun LinkedCaseCard(h: Hearing, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        shape   = RoundedCornerShape(12.dp),
        border  = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                EyebrowText("Case")
                Spacer(Modifier.height(2.dp))
                Text(
                    com.legumlex.client.ui.caseTitleAnnotated(h.caseTitle, PrimaryText.copy(alpha = 0.5f)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText,
                    maxLines = 1,
                )
                if (h.caseNumber.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        h.caseNumber,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter),
                        color = SecondaryText,
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TertiaryText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CourtCard(h: Hearing) {
    Surface(
        color  = MaterialTheme.colorScheme.surface,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            KvRow("Court", h.courtName ?: "—")
            Divider()
            KvRow("Room",  h.courtNumber?.let { "Court $it" } ?: "—")
            Divider()
            KvRow("Judge", com.legumlex.client.ui.formatJudge(h.judgeName, h.courtName) ?: "—")
        }
    }
}

@Composable
private fun StatusCard(h: Hearing) {
    Surface(
        color  = MaterialTheme.colorScheme.surface,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(h.status.label, h.status.palette)
                Spacer(Modifier.width(10.dp))
                Text(h.status.label, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
            if (h.history.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(14.dp))
                h.history.forEachIndexed { i, entry ->
                    HistoryRow(entry)
                    if (i != h.history.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
            if (!h.adjournmentReason.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Divider()
                Spacer(Modifier.height(14.dp))
                EyebrowText("Adjournment Reason")
                Spacer(Modifier.height(4.dp))
                Text(h.adjournmentReason, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HearingHistoryEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(entry.status.palette.solid),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${entry.status.label} · ${entry.date?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "—"}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter),
            color = SecondaryText,
        )
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = BorderSubtle)

// --- Sticky bottom bar ---------------------------------------------------

@Composable
private fun StickyActions() {
    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.s4, vertical = spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(spacing.s3),
        ) {
            LegumSecondaryButton(
                text        = "Calendar",
                leadingIcon = Icons.Outlined.CalendarMonth,
                onClick     = { /* CalendarContract intent, later */ },
                modifier    = Modifier.weight(1f),
            )
            LegumPrimaryButton(
                text        = "Remind Me",
                leadingIcon = Icons.Outlined.NotificationsActive,
                onClick     = { /* WorkManager schedule, later */ },
                modifier    = Modifier.weight(1f),
            )
        }
    }
}

// --- Loading / Error -----------------------------------------------------

@Composable
private fun HearingDetailSkeleton() {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s4),
    ) {
        Box(Modifier.size(width = 80.dp, height = 22.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor))
        Spacer(Modifier.height(spacing.s3))
        Box(Modifier.size(width = 240.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor))
        Spacer(Modifier.height(spacing.s2))
        Box(Modifier.size(width = 140.dp, height = 16.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor))
        Spacer(Modifier.height(spacing.s5))
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(BorderColor))
        Spacer(Modifier.height(spacing.s5))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(BorderColor))
    }
}

@Composable
private fun HearingDetailError(message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(StatusDanger.softBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = StatusDanger.solid, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.s4))
        Text("Couldn't load this hearing",
            style = MaterialTheme.typography.headlineSmall,
            color = PrimaryText,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s2))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s5))
        LegumSecondaryButton(
            text        = "Try again",
            leadingIcon = Icons.Outlined.Refresh,
            onClick     = onRetry,
            modifier    = Modifier.widthIn(max = 280.dp),
        )
    }
}
