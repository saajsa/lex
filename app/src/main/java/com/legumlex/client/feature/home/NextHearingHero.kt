package com.legumlex.client.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.StatusOngoing
import com.legumlex.client.ui.theme.StatusPending

// Hero card — black surface with light-styled chrome.
// Handoff: 20dp radius, 20dp padding, 4 stacked rows.
@Composable
fun NextHearingHero(
    hearing: HearingPreview,
    onAddToCalendar: () -> Unit,
    onRemindMe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val chipOnDark   = Color.White.copy(alpha = 0.12f)
    val pillBgOnDark = Color.White.copy(alpha = 0.08f)
    val borderOnDark = Color.White.copy(alpha = 0.28f)
    val subText      = Color.White.copy(alpha = 0.72f)

    Surface(
        color = AccentInk,
        contentColor = InverseText,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Row 1: countdown chip + eyebrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountdownChip(label = hearing.countdownLabel, urgency = hearing.urgency, bg = chipOnDark)
                Spacer(Modifier.weight(1f))
                EyebrowText("Next Hearing", color = subText)
            }

            Spacer(Modifier.height(spacing.s4))

            // Row 2: date/time + case title
            Text(
                text = "${hearing.date} · ${hearing.time}",
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = (-0.48).sp,
                color = InverseText,
            )
            Spacer(Modifier.height(spacing.s2))
            Text(
                text = com.legumlex.client.ui.caseTitleAnnotated(hearing.caseTitle, subText),
                style = MaterialTheme.typography.titleMedium,
                color = subText,
                maxLines = 2,
            )

            Spacer(Modifier.height(spacing.s4))

            // Row 3: location pill
            LocationPill(text = hearing.courtName, bg = pillBgOnDark)

            Spacer(Modifier.height(spacing.s5))

            // Row 4: 2-up actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s3),
            ) {
                Button(
                    onClick = onAddToCalendar,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AccentInk,
                    ),
                ) {
                    Text("Add to Calendar", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onRemindMe,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderOnDark),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = InverseText,
                    ),
                ) {
                    Text("Remind Me", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(label: String, urgency: HearingUrgency, bg: Color) {
    val dotColor = when (urgency) {
        HearingUrgency.Today, HearingUrgency.Tomorrow -> StatusDanger.solid
        HearingUrgency.Soon                            -> StatusPending.solid
        HearingUrgency.Later                           -> StatusOngoing.solid
    }
    Surface(
        color = bg,
        contentColor = Color.White,
        shape = RoundedCornerShape(percent = 50),
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
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LocationPill(text: String, bg: Color) {
    Surface(
        color = bg,
        contentColor = Color.White,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}

// Empty-state fallback per handoff: "When no upcoming hearing exists, replace
// the hero with a quiet white card."
@Composable
fun NoHearingCard(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            EyebrowText("Next Hearing")
            Spacer(Modifier.height(8.dp))
            Text(
                "No upcoming hearings",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "We'll notify you the moment one is scheduled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
