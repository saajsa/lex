package com.legumlex.client.feature.consultations.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.ConsultationKind
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*
import java.time.format.DateTimeFormatter

// Screen #14 — Spec sheet. Surfaces all consultation metadata in a structured
// read-only form. Rendered as a ModalBottomSheet over the active detail screen.

@Composable
fun ConsultationSpecContent(c: Consultation) {
    val spacing = LocalSpacing.current
    val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    val dtFmt   = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5)
            .padding(bottom = spacing.s8),
        verticalArrangement = Arrangement.spacedBy(spacing.s4),
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .align(Alignment.CenterHorizontally),
            content = {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape    = RoundedCornerShape(50),
                    color    = BorderColor,
                ) {}
            }
        )

        Text(
            text  = "Consultation details",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
        )

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Identification
        SpecSection("Identification") {
            SpecRow(Icons.AutoMirrored.Outlined.Label, "ID", "#${c.id}")
            SpecRow(Icons.Outlined.Category, "Type", c.kind.label)
            SpecRow(Icons.Outlined.AccountTree, "Phase", c.phase.replaceFirstChar { it.uppercase() })
        }

        // Status & flags
        SpecSection("Status") {
            SpecRow(Icons.Outlined.Circle, "Status", c.status.label)
            SpecRow(Icons.Outlined.Flag, "Priority", c.priority ?: "Medium")
            SpecRow(Icons.Outlined.Bolt, "Urgent", if (c.isUrgent) "Yes" else "No")
        }

        // People
        if (!c.assignedTo.isNullOrBlank()) {
            SpecSection("People") {
                SpecRow(Icons.Outlined.Person, "Advocate", c.assignedTo)
            }
        }

        // Dates
        SpecSection("Timeline") {
            c.createdAt?.let    { SpecRow(Icons.Outlined.Schedule,         "Submitted",    it.format(dtFmt)) }
            c.lastUpdatedAt?.let{ SpecRow(Icons.Outlined.Update,           "Last updated", it.format(dtFmt)) }
            c.acknowledgedAt?.let{ SpecRow(Icons.Outlined.DoneAll,         "Acknowledged", it.format(dtFmt)) }
        }

        // Scheduling
        val hasSchedule = c.scheduledDate != null || c.preferredDate != null
        if (hasSchedule) {
            SpecSection("Scheduling") {
                c.preferredDate?.let { d ->
                    val t = c.preferredTime?.format(timeFmt) ?: "Any time"
                    SpecRow(Icons.Outlined.CalendarToday, "Preferred", "${d.format(dateFmt)} · $t")
                }
                c.scheduledDate?.let { d ->
                    val t = c.scheduledTime?.format(timeFmt) ?: ""
                    SpecRow(Icons.Outlined.Event, "Scheduled", "${d.format(dateFmt)}${if (t.isNotBlank()) " · $t" else ""}")
                }
            }
        }

        // Linked matter
        if (!c.linkedCaseId.isNullOrBlank()) {
            SpecSection("Linked matter") {
                c.linkedCaseTitle?.let  { SpecRow(Icons.Outlined.Gavel,  "Title",  it) }
                c.linkedCaseNumber?.let { SpecRow(Icons.Outlined.Tag,     "Number", it) }
            }
        }

        // Counts
        SpecSection("Activity") {
            SpecRow(Icons.Outlined.ChatBubbleOutline, "Replies",   "${c.replyCount}")
            SpecRow(Icons.Outlined.FolderOpen,        "Documents", "${c.totalDocCount}")
            if (c.kind == ConsultationKind.DOC_REQUEST) {
                SpecRow(Icons.Outlined.Pending, "Pending uploads", "${c.pendingDocCount}")
            }
        }

        // AHC risk
        if (c.kind == ConsultationKind.AHC && c.riskScore != null) {
            SpecSection("Risk assessment") {
                c.riskLevel?.let { SpecRow(Icons.Outlined.Shield, "Risk level", it.label) }
                SpecRow(Icons.Outlined.Speed, "Risk score", "${c.riskScore} / 100")
            }
        }
    }
}

@Composable
private fun SpecSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val spacing = LocalSpacing.current
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
            EyebrowText(title, color = TertiaryText)
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            content()
        }
    }
}

@Composable
private fun SpecRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = TertiaryText, modifier = Modifier.size(16.dp))
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = TertiaryText,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = PrimaryText,
        )
    }
}
