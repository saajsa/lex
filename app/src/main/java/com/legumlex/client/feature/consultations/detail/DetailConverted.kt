package com.legumlex.client.feature.consultations.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.cases.CaseDetail
import com.legumlex.client.feature.cases.CaseStatus
import com.legumlex.client.feature.cases.CasesRepository
import com.legumlex.client.feature.cases.Parties
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.ConsultationDoc
import com.legumlex.client.feature.consultations.Reply
import com.legumlex.client.feature.consultations.components.ConsStatusBadge
import com.legumlex.client.feature.consultations.relativeAgo
import com.legumlex.client.ui.components.DetailHeading
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Screen #13 — Converted-to-litigation variant.
// Shown instead of the kind-specific layout when isConverted() == true. The
// inquiry has done its job: it became a court matter. So we lead with a live
// litigation dashboard (pulled from the linked case: court, stage, next
// hearing, advocate, parties) and keep the original request + conversation
// below as the "how this started" record. The full matter is one tap away.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailConverted(
    consultation: Consultation,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenSpec: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current

    // Pull the linked matter so we can show real litigation context inline.
    // Reuses the standard case-detail endpoint — no separate payload. Stays
    // null while loading or if the fetch fails, in which case we fall back to
    // the lightweight title/number link card.
    val caseId = consultation.linkedCaseId
    var matter by remember(caseId) { mutableStateOf<CaseDetail?>(null) }
    LaunchedEffect(caseId) {
        if (!caseId.isNullOrBlank()) {
            CasesRepository(ApiClient.get(context)).detail(caseId)
                .onSuccess { matter = it }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                title = matter?.summary?.title
                    ?: consultation.linkedCaseTitle?.takeIf { it.isNotBlank() }
                    ?: consultation.tag?.takeIf { it.isNotBlank() }
                    ?: consultation.kind.label,
                trailing = {
                    IconButton(onClick = onOpenSpec) {
                        Icon(Icons.Outlined.Info, null, tint = TertiaryText)
                    }
                },
            )

            ConsStatusBadge(consultation.status)

            // ── Litigation dashboard ────────────────────────────────────────
            if (!caseId.isNullOrBlank()) {
                val m = matter
                if (m != null) {
                    MatterMasthead(
                        matter = m,
                        fallbackTitle = consultation.linkedCaseTitle,
                        fallbackNumber = consultation.linkedCaseNumber,
                        onOpen = { onOpenCase(caseId) },
                        spacing = spacing,
                    )
                    NextHearingCard(m, spacing)
                    if (m.advocate?.isNotBlank() == true) AdvocateCard(m, spacing)
                    PartiesCard(m.parties, spacing)
                } else {
                    // Loading / fetch failed — degrade to the simple link card.
                    BasicCaseLink(
                        caseTitle = consultation.linkedCaseTitle,
                        caseNumber = consultation.linkedCaseNumber,
                        onOpen = { onOpenCase(caseId) },
                        spacing = spacing,
                    )
                }
            }

            // ── How this started ────────────────────────────────────────────
            EyebrowText("How this started", color = TertiaryText)
            OriginalRequestCard(consultation, spacing)

            if (consultation.replies.isNotEmpty()) {
                EyebrowText("Conversation history", color = TertiaryText)
                ReadOnlyThread(consultation.replies, spacing)
            }

            if (consultation.documents.isNotEmpty()) {
                EyebrowText("Attached documents (${consultation.documents.size})", color = TertiaryText)
                DocsCard(consultation.documents, spacing)
            }
        }
    }
}

// ─── Matter masthead ────────────────────────────────────────────────────────

@Composable
private fun MatterMasthead(
    matter: CaseDetail,
    fallbackTitle: String?,
    fallbackNumber: String?,
    onOpen: () -> Unit,
    spacing: Spacing,
) {
    val title  = matter.summary.title.takeIf { it.isNotBlank() } ?: fallbackTitle.orEmpty()
    val number = matter.summary.number.takeIf { it.isNotBlank() } ?: fallbackNumber

    Card(
        colors   = CardDefaults.cardColors(containerColor = AccentInk),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s5).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Gavel, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(spacing.s2))
                Text(
                    text  = "Now a court matter",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                CaseStatusChip(matter.summary.status)
            }

            if (title.isNotBlank()) {
                Spacer(Modifier.height(spacing.s3))
                Text(
                    text  = com.legumlex.client.ui.caseTitleAnnotated(title, Color.White.copy(alpha = 0.55f)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
            if (!number.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(number, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }

            // Court / bench
            val courtLine = buildString {
                matter.court?.takeIf { it.isNotBlank() }?.let { append(it) }
                matter.courtNumber?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("Court $it")
                }
            }
            if (courtLine.isNotBlank()) {
                Spacer(Modifier.height(spacing.s3))
                MastheadFact(Icons.Outlined.AccountBalance, courtLine)
            }
            matter.judge?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(spacing.s2))
                MastheadFact(Icons.Outlined.Gavel, it)
            }

            Spacer(Modifier.height(spacing.s4))
            Button(
                onClick = onOpen,
                shape   = RoundedCornerShape(10.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AccentInk),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(spacing.s2))
                Text(
                    text  = "Open full matter",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun MastheadFact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
    }
}

@Composable
private fun CaseStatusChip(status: CaseStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text  = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

// ─── Next hearing ─────────────────────────────────────────────────────────────

@Composable
private fun NextHearingCard(matter: CaseDetail, spacing: Spacing) {
    val date = matter.summary.nextHearingDate
    val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.s4).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (date != null) StatusAdjourned.softBg else Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Event, null,
                    tint = if (date != null) StatusAdjourned.solid else TertiaryText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(spacing.s3))
            Column(Modifier.weight(1f)) {
                EyebrowText("Next hearing", color = TertiaryText)
                Spacer(Modifier.height(2.dp))
                if (date != null) {
                    Text(date.format(dateFmt), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
                    val purpose = matter.summary.nextHearingPurpose?.takeIf { it.isNotBlank() }
                    if (purpose != null) {
                        Text(purpose, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("No hearing scheduled yet", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                }
            }
            if (date != null) {
                Spacer(Modifier.width(spacing.s2))
                CountdownPill(date)
            }
        }
    }
}

@Composable
private fun CountdownPill(date: LocalDate) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
    val (label, palette) = when {
        days < 0   -> "Past" to StatusClosed
        days == 0L -> "Today" to StatusDanger
        days == 1L -> "1 day" to StatusPending
        days <= 7  -> "$days days" to StatusPending
        else       -> "$days days" to StatusAdjourned
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(palette.softBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = palette.solid)
    }
}

// ─── Advocate ─────────────────────────────────────────────────────────────────

@Composable
private fun AdvocateCard(matter: CaseDetail, spacing: Spacing) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.s4).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatar = matter.advocateAvatarUrl
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.matchParentSize().clip(CircleShape))
                } else {
                    Icon(Icons.Outlined.Person, null, tint = TertiaryText, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(spacing.s3))
            Column(Modifier.weight(1f)) {
                EyebrowText("Your advocate", color = TertiaryText)
                Spacer(Modifier.height(2.dp))
                Text(matter.advocate.orEmpty(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
                matter.advocateDesignation?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                }
            }
        }
    }
}

// ─── Parties ──────────────────────────────────────────────────────────────────

@Composable
private fun PartiesCard(parties: Parties, spacing: Spacing) {
    if (parties.petitioners.isEmpty() && parties.respondents.isEmpty()) return
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
            EyebrowText("Parties", color = TertiaryText)
            PartySide("Petitioner", parties.petitioners.map { it.name }, spacing)
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            PartySide("Respondent", parties.respondents.map { it.name }, spacing)
        }
    }
}

@Composable
private fun PartySide(role: String, names: List<String>, spacing: Spacing) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text  = role,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TertiaryText,
            modifier = Modifier.width(90.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(spacing.s2))
        Text(
            text  = names.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Basic link (fallback while the matter loads) ─────────────────────────────

@Composable
private fun BasicCaseLink(
    caseTitle: String?,
    caseNumber: String?,
    onOpen: () -> Unit,
    spacing: Spacing,
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.s4).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentInk),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Gavel, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(spacing.s3))
            Column(Modifier.weight(1f)) {
                Text(
                    text  = if (!caseTitle.isNullOrBlank()) caseTitle else "Linked court matter",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!caseNumber.isNullOrBlank()) {
                    Text(caseNumber, style = MaterialTheme.typography.bodySmall, color = TertiaryText)
                }
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = AccentInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Original request ─────────────────────────────────────────────────────────

@Composable
private fun OriginalRequestCard(c: Consultation, spacing: Spacing) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.padding(spacing.s4),
            verticalArrangement = Arrangement.spacedBy(spacing.s3),
        ) {
            EyebrowText("Original request", color = TertiaryText)

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s2),
            ) {
                Box(
                    modifier         = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Description, null, tint = TertiaryText, modifier = Modifier.size(16.dp))
                }
                Text(
                    text  = c.kind.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SecondaryText,
                )
            }

            if (!c.note.isNullOrBlank()) {
                Text(c.note, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
            }

            val dateLine = buildString {
                c.createdAt?.let { append("Submitted ${relativeAgo(it)}") }
                c.assignedTo?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
            }
            if (dateLine.isNotBlank()) {
                Text(dateLine, style = MaterialTheme.typography.bodySmall, color = TertiaryText)
            }
        }
    }
}

// ─── Read-only conversation ───────────────────────────────────────────────────

@Composable
private fun ReadOnlyThread(replies: List<Reply>, spacing: Spacing) {
    val timeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s4)) {
            replies.takeLast(5).forEach { reply ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (reply.fromClient) Arrangement.End else Arrangement.Start,
                ) {
                    Column(
                        horizontalAlignment = if (reply.fromClient) Alignment.End else Alignment.Start,
                        modifier            = Modifier.widthIn(max = 280.dp),
                    ) {
                        Text(
                            text  = reply.authorName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TertiaryText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(
                                    topStart   = if (reply.fromClient) 12.dp else 4.dp,
                                    topEnd     = if (reply.fromClient) 4.dp else 12.dp,
                                    bottomStart = 12.dp,
                                    bottomEnd   = 12.dp,
                                ))
                                .background(if (reply.fromClient) AccentInk else Surface2)
                                .padding(horizontal = spacing.s3, vertical = spacing.s2),
                        ) {
                            Text(
                                text  = reply.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (reply.fromClient) Color.White else PrimaryText,
                            )
                        }
                        reply.createdAt?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it.format(timeFmt), style = MaterialTheme.typography.labelSmall, color = TertiaryText)
                        }
                    }
                }
            }
            if (replies.size > 5) {
                Text(
                    text  = "${replies.size - 5} earlier messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = TertiaryText,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

// ─── Documents (read-only list) ───────────────────────────────────────────────

@Composable
private fun DocsCard(docs: List<ConsultationDoc>, spacing: Spacing) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Surface1),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
            docs.forEach { doc ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s3),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = TertiaryText, modifier = Modifier.size(18.dp))
                    Text(
                        text     = doc.displayName,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = PrimaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    doc.fileExtension?.uppercase()?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = TertiaryText)
                    }
                }
            }
        }
    }
}
