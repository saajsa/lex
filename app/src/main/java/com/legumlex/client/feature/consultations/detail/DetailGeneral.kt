package com.legumlex.client.feature.consultations.detail

import com.legumlex.client.ahc.theme.Mono
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.components.LlAvatar
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.ConsultationAttachment
import com.legumlex.client.feature.consultations.ConsultationDetailUiState
import com.legumlex.client.feature.consultations.ConsultationDetailViewModel
import com.legumlex.client.feature.consultations.ConsultationStatus
import com.legumlex.client.feature.consultations.Reply
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// -----------------------------------------------------------------------------
// Screen 03 · ConsultationDetailScreen · General Inquiry  ("Client General
// Inquiry.html" — Plot Design). An asynchronous Q&A: the client asks, the
// assigned advocate answers, and the two trade follow-ups over hours or days.
// The conversation IS the screen — everything else frames the thread. The
// app-wide header (centred logo) + footer (bottom nav) are provided by
// MainActivity; the composer is pinned above that nav.
// -----------------------------------------------------------------------------

@Composable
fun DetailGeneral(
    vm: ConsultationDetailViewModel,
    consultation: Consultation,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenSpec: () -> Unit = {},
    /** Withdraw / Close, as the other consultation kinds have them. */
    topBarActions: @Composable () -> Unit = {},
    onNewConsultation: () -> Unit = onBack,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSending by vm.isSending.collectAsState()
    val st by vm.state.collectAsState()
    val attachments = (st as? ConsultationDetailUiState.Ready)?.attachments ?: emptyList()

    // Resolved is final as the web desk has it: a further question is a new one.
    val isLocked = consultation.status in setOf(
        ConsultationStatus.COMPLETED,
        ConsultationStatus.CLOSED,
        ConsultationStatus.CANCELLED,
        ConsultationStatus.DECLINED,
    )
    val phase = phaseOf(consultation)

    // Surface failed-reply toasts.
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            if (ev is com.legumlex.client.feature.consultations.ReplyEvent.Failed) {
                android.widget.Toast.makeText(context, ev.message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    var composing by rememberSaveable { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.uploadAttachment(uri) }

    Scaffold(
        containerColor = LlColor.Paper,
        // The app-wide header already consumes the status-bar inset; without this
        // the nested Scaffold re-adds it as a fixed empty band under the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Composer(
                value = composing,
                onChange = { composing = it },
                onSend = {
                    val msg = composing.trim()
                    if (msg.isNotBlank()) { vm.sendReply(msg); composing = "" }
                },
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onNewConsultation = onNewConsultation,
                isSending = isSending,
                locked = isLocked,
                resolved = consultation.status == ConsultationStatus.COMPLETED,
                advocate = advName(consultation.assignedTo),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
        ) {
            // ── masthead (kind chip + ref + serif subject) ──────────────────
            Masthead(consultation, onOpenSpec, topBarActions)

            // ── status hero (left colour band) ──────────────────────────────
            Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = 2.dp)) {
                HeroStatus(phase, consultation)
            }

            // ── linked matter (only when tied to a case) ────────────────────
            if (!consultation.linkedCaseId.isNullOrBlank()) {
                Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    LinkedMatterCard(consultation) { onOpenCase(consultation.linkedCaseId!!) }
                }
            }

            // ── conversation (the spine) ────────────────────────────────────
            Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                SectionHead("Conversation")
                Spacer(Modifier.height(9.dp))
                if (consultation.replies.isEmpty()) {
                    DCard { ThreadEmpty(phase, consultation) }
                } else {
                    Thread(
                        consultation = consultation,
                        phase = phase,
                        isSending = isSending,
                        onOpenThread = { onOpenThread(consultation.id) },
                    )
                }
            }

            // ── shared files (two-way, tap to open) ─────────────────────────
            if (attachments.isNotEmpty()) {
                Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    SectionHead("Shared Files · ${attachments.size}")
                    Spacer(Modifier.height(9.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attachments.forEach { att -> SharedFileRow(att) { vm.openAttachment(att) } }
                    }
                }
            }

            // ── advocate card ───────────────────────────────────────────────
            if (!consultation.assignedTo.isNullOrBlank()) {
                Column(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, top = LlDimen.SectionGap)) {
                    SectionHead("Your Advocate")
                    Spacer(Modifier.height(9.dp))
                    DCard { AdvocateCard(consultation, phase) }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

// =============================================================================
// Phase derivation — drives the hero tint + composer lock
// =============================================================================

private enum class GenPhase { AWAITING, ACTIVE, RESOLVED, CLOSED, DECLINED }

private fun phaseOf(c: Consultation): GenPhase {
    val advocateReplied = c.replies.any { !it.fromClient }
    return when (c.status) {
        ConsultationStatus.DECLINED -> GenPhase.DECLINED
        ConsultationStatus.CLOSED, ConsultationStatus.CANCELLED -> GenPhase.CLOSED
        ConsultationStatus.COMPLETED -> GenPhase.RESOLVED
        ConsultationStatus.PENDING_REVIEW -> if (advocateReplied) GenPhase.ACTIVE else GenPhase.AWAITING
        else -> GenPhase.ACTIVE
    }
}

// =============================================================================
// Masthead (neutral General chip + ref + serif subject)
// =============================================================================

@Composable
private fun Masthead(c: Consultation, onOpenSpec: () -> Unit, actions: @Composable () -> Unit) {
    val isNotice = c.kind == com.legumlex.client.feature.consultations.ConsultationKind.LEGAL_NOTICE
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = LlDimen.ScreenPad, end = 6.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LlColor.Paper2)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        if (isNotice) Icons.Outlined.Gavel else Icons.Outlined.Forum,
                        null, tint = LlColor.InkMuted, modifier = Modifier.size(13.dp),
                    )
                    Text(
                        c.kind.label.uppercase(),
                        fontFamily = Inter, fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp, letterSpacing = 0.06.em, color = LlColor.InkMuted,
                    )
                }
                Text(
                    "#${c.id}",
                    fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint,
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                c.tag?.takeIf { it.isNotBlank() } ?: c.kind.label,
                fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp, letterSpacing = (-0.02).em, lineHeight = 25.sp, color = LlColor.Ink,
            )
        }
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenSpec),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, "Details", tint = LlColor.InkMuted, modifier = Modifier.size(21.dp))
        }
        actions()
    }
}

// =============================================================================
// Hero status (left colour band)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroStatus(phase: GenPhase, c: Consultation) {
    data class Spec(val band: Color, val icon: ImageVector, val title: String, val hint: String?)

    val adv = advName(c.assignedTo)
    // Only what the advocate wrote for the client; never the consultation's own text.
    val reason = c.advocateNote?.takeIf { it.isNotBlank() }
    val spec = when (phase) {
        GenPhase.AWAITING -> Spec(
            LlColor.AmberMid, Icons.Outlined.HourglassTop, "Awaiting Your Advocate",
            "Submitted ${c.createdAt?.let { com.legumlex.client.feature.consultations.relativeAgo(it) } ?: "recently"}. $adv will see it shortly — the firm usually replies within 30 minutes during chamber hours.",
        )
        GenPhase.ACTIVE -> Spec(LlColor.SlateMid, Icons.Outlined.Forum, "With Your Advocate", null)
        GenPhase.RESOLVED -> Spec(
            LlColor.Verdant, Icons.Outlined.TaskAlt, "Resolved",
            "$adv marked your question resolved. The conversation stays here for your records; anything further is a new question.",
        )
        GenPhase.CLOSED -> Spec(
            LlColor.InkFaint, Icons.Outlined.Inventory2, "Closed",
            "This enquiry was closed. The full conversation stays here for your records.",
        )
        GenPhase.DECLINED -> Spec(
            LlColor.Claret, Icons.Outlined.Block, "Declined",
            reason ?: "The firm could not take it on.",
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(spec.icon, null, tint = LlColor.Ink, modifier = Modifier.size(19.dp))
                    Text(spec.title, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = LlColor.Ink)
                }
                if (c.isUrgent && (phase == GenPhase.AWAITING || phase == GenPhase.ACTIVE)) UrgentPill()
            }

            // The original question, carried as the emotional anchor.
            val question = c.note?.takeIf { it.isNotBlank() && phase != GenPhase.DECLINED }
            if (question != null) {
                Text(
                    "“$question”",
                    fontFamily = Inter, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, lineHeight = 22.sp, color = LlColor.Ink,
                    modifier = Modifier.padding(top = 11.dp),
                )
            }

            if (spec.hint != null && (phase != GenPhase.ACTIVE)) {
                Text(spec.hint, fontFamily = Inter, fontSize = 12.sp, lineHeight = 18.sp,
                    color = LlColor.InkMuted, modifier = Modifier.padding(top = 9.dp))
            }

            // Meta facts.
            val meta = buildList {
                c.createdAt?.let { add("Submitted" to it.format(stampFmt)) }
                if (phase == GenPhase.RESOLVED || phase == GenPhase.CLOSED) {
                    c.closedAt?.let { add((if (phase == GenPhase.RESOLVED) "Resolved" else "Closed") to it.format(stampFmt)) }
                } else {
                    c.acknowledgedAt?.let { add("Acknowledged" to it.format(stampFmt)) }
                }
            }
            if (meta.isNotEmpty() || !c.assignedTo.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().padding(top = 13.dp).height(1.dp).background(LlColor.Paper2))
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    meta.forEach { (k, v) -> MetaItem(k, v, mono = false) }
                    c.assignedTo?.takeIf { it.isNotBlank() }?.let {
                        MetaItem(if (phase == GenPhase.CLOSED) "Handled by" else "Assigned to", adv, mono = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(key: String, value: String, mono: Boolean) {
    Column {
        Text(key.uppercase(), fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.08.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontFamily = if (mono) Mono else Inter,
            fontWeight = if (mono) FontWeight.Normal else FontWeight.SemiBold,
            fontSize = if (mono) 11.sp else 12.sp,
            color = LlColor.Ink,
        )
    }
}

@Composable
private fun UrgentPill() {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(ClaretSoftFix).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.PriorityHigh, null, tint = ClaretMidFix, modifier = Modifier.size(12.dp))
        Text("MARKED URGENT", fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 0.06.em, color = ClaretMidFix)
    }
}

// =============================================================================
// Linked matter
// =============================================================================

@Composable
private fun LinkedMatterCard(c: Consultation, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FolderOpen, null, tint = LlColor.InkMuted, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f)) {
            Text("LINKED MATTER", fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(3.dp))
            Text(com.legumlex.client.ui.caseTitleAnnotated(c.linkedCaseTitle ?: "Linked case", LlColor.InkFaint),
                fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = LlColor.Ink, maxLines = 1)
            c.linkedCaseNumber?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkMuted)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.InkFaint, modifier = Modifier.size(20.dp))
    }
}

// =============================================================================
// Conversation thread
// =============================================================================

@Composable
private fun Thread(
    consultation: Consultation,
    phase: GenPhase,
    isSending: Boolean,
    onOpenThread: () -> Unit,
) {
    val all = consultation.replies
    val hasEarlier = all.size > 4
    val visible = if (hasEarlier) all.takeLast(4) else all

    Column {
        if (phase == GenPhase.RESOLVED) {
            ResolvedBanner(consultation)
            Spacer(Modifier.height(16.dp))
        }
        if (hasEarlier) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(LlColor.Surface)
                        .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenThread)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.ExpandLess, null, tint = LlColor.InkMuted, modifier = Modifier.size(15.dp))
                    Text("View earlier messages", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, color = LlColor.InkMuted)
                }
            }
        }

        var lastDate: LocalDate? = null
        visible.forEachIndexed { idx, r ->
            val d = r.createdAt?.toLocalDate()
            if (d != null && d != lastDate) {
                DaySeparator(d)
                lastDate = d
            }
            val prev = visible.getOrNull(idx - 1)
            val continuation = prev != null &&
                prev.fromClient == r.fromClient &&
                prev.authorName == r.authorName &&
                prev.createdAt?.toLocalDate() == r.createdAt?.toLocalDate()
            val sending = isSending && idx == visible.lastIndex && r.fromClient
            Bubble(r, continuation = continuation, sending = sending)
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate) {
    val label = if (date == LocalDate.now()) "Today · ${date.format(daySepFmt)}" else date.format(daySepFullFmt)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        Text(label.uppercase(), fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun Bubble(r: Reply, continuation: Boolean, sending: Boolean) {
    val me = r.fromClient
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = if (me) Arrangement.End else Arrangement.Start,
    ) {
        if (me) {
            Spacer(Modifier.weight(0.16f))
            BubbleColumn(r, me = true, continuation = continuation, sending = sending, Modifier.weight(0.84f))
            Spacer(Modifier.width(9.dp))
            BubbleAvatar(r, me = true, continuation = continuation)
        } else {
            BubbleAvatar(r, me = false, continuation = continuation)
            Spacer(Modifier.width(9.dp))
            BubbleColumn(r, me = false, continuation = continuation, sending = sending, Modifier.weight(0.84f))
            Spacer(Modifier.weight(0.16f))
        }
    }
}

@Composable
private fun BubbleAvatar(r: Reply, me: Boolean, continuation: Boolean) {
    Box(Modifier.size(30.dp).alpha(if (continuation) 0f else 1f)) {
        LlAvatar(
            initials = initialsOf(r.authorName),
            size = 30.dp,
            bg = if (me) LlColor.Ink else LlColor.Slate,
            avatarUrl = if (me) null else r.authorImage,
        )
    }
}

@Composable
private fun BubbleColumn(r: Reply, me: Boolean, continuation: Boolean, sending: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
        if (!continuation) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (me) "You" else advName(r.authorName),
                    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = LlColor.Ink,
                )
                r.createdAt?.let {
                    Text(it.format(timeFmt), fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 0.02.em, color = LlColor.InkFaint)
                }
            }
            Spacer(Modifier.height(5.dp))
        }

        val shape = if (me) {
            RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp, bottomEnd = if (continuation) 13.dp else 4.dp, bottomStart = 13.dp)
        } else {
            RoundedCornerShape(topStart = if (continuation) 13.dp else 4.dp, topEnd = 13.dp, bottomEnd = 13.dp, bottomStart = 13.dp)
        }
        Box(
            Modifier
                .alpha(if (sending) 0.55f else 1f)
                .clip(shape)
                .then(
                    if (me) Modifier.background(LlColor.Ink)
                    else Modifier.background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, shape),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                r.message,
                fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp,
                color = if (me) LlColor.Paper else LlColor.Ink,
            )
        }

        // Client message status stamp.
        if (me) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (sending) {
                    Icon(Icons.Outlined.Schedule, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
                    Text("Sending…", fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint)
                } else {
                    // Read only once the advocate has opened it (read_at).
                    val read = r.readAt != null
                    Icon(if (read) Icons.Outlined.DoneAll else Icons.Outlined.Done, null, tint = LlColor.SlateMid, modifier = Modifier.size(13.dp))
                    Text(if (read) "Read · ${r.readAt!!.format(timeFmt)}" else "Sent${r.createdAt?.let { " · ${it.format(timeFmt)}" } ?: ""}",
                        fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint)
                }
            }
        }
    }
}

@Composable
private fun ResolvedBanner(c: Consultation) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LlColor.VerdantSoft)
            .border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Verdant),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.TaskAlt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Column {
            Text("Marked resolved by ${advName(c.assignedTo)}", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, lineHeight = 16.sp, color = LlColor.Ink)
            c.closedAt?.let {
                Spacer(Modifier.height(2.dp))
                Text(it.format(stampFmt), fontFamily = Inter, fontSize = 10.sp, color = LlColor.Verdant)
            }
        }
    }
}

@Composable
private fun ThreadEmpty(phase: GenPhase, c: Consultation) {
    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.AmberSoft)
                .border(LlDimen.HairlineWidth, LlColor.AmberSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.MarkEmailUnread, null, tint = LlColor.AmberMid, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.height(15.dp))
        Text(
            if (phase == GenPhase.CLOSED) "This Enquiry Is Closed" else "Your Question Is With ${advName(c.assignedTo)}",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            letterSpacing = (-0.01).em, color = LlColor.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            if (phase == GenPhase.CLOSED) "The conversation will appear here once it begins."
            else "${advName(c.assignedTo)} will respond shortly — the firm usually replies within 30 minutes during chamber hours. You'll get a notification the moment there's an answer.",
            fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp, color = LlColor.InkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// =============================================================================
// Shared files
// =============================================================================

@Composable
private fun SharedFileRow(att: ConsultationAttachment, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                .background(if (att.fromClient) LlColor.SlateSoft else LlColor.Paper2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                fileIcon(att.fileType),
                null,
                tint = if (att.fromClient) LlColor.SlateMid else LlColor.InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(att.displayName, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = LlColor.Ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(att.uploadedBy.takeIf { it.isNotBlank() }, att.uploadedAt?.format(daySepFmt)).joinToString(" · "),
                fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint,
            )
        }
        Icon(Icons.Outlined.OpenInNew, null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
    }
}

// =============================================================================
// Advocate card (with SLA line)
// =============================================================================

@Composable
private fun AdvocateCard(c: Consultation, phase: GenPhase) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LlAvatar(initials = initialsOf(c.assignedTo), size = 44.dp, avatarUrl = c.assignedToAvatarUrl)
        Column(Modifier.weight(1f)) {
            Text(advName(c.assignedTo), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
            c.assignedToDesignation?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
            }
            if (phase != GenPhase.CLOSED && phase != GenPhase.DECLINED) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(LlColor.Verdant))
                    Text("Usually replies within 30 minutes", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
                }
            }
        }
    }
}

// =============================================================================
// Composer (pinned above the app-wide bottom nav)
// =============================================================================

@Composable
private fun Composer(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onNewConsultation: () -> Unit,
    isSending: Boolean,
    locked: Boolean,
    resolved: Boolean = false,
    advocate: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LlColor.Paper)
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        if (locked) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1)
                        .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
                    Text(
                        if (resolved) "$advocate marked this question resolved. Anything further is a new question."
                        else "This enquiry is closed. Ask a new question to continue.",
                        fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted,
                    )
                }
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LlColor.Ink)
                        .clickable(onClick = onNewConsultation).padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ask A New Question", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
                }
            }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper)
                    .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AttachFile, null, tint = LlColor.InkMuted, modifier = Modifier.size(20.dp)) }

            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                    .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text("Reply to $advocate…", fontFamily = Inter, fontSize = 13.sp, color = LlColor.InkFaint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = LlColor.Ink),
                    cursorBrush = SolidColor(LlColor.Claret),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val canSend = value.trim().isNotBlank() && !isSending
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(50))
                    .background(if (canSend) LlColor.Claret else LlColor.Paper2)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ArrowUpward, null,
                    tint = if (canSend) Color.White else LlColor.InkFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// =============================================================================
// Shared helpers
// =============================================================================

// LlColor.ClaretSoft / ClaretMid are broken placeholders (white / black); use the
// design's real claret tints (--claret-s #fdf2f4 / --claret-m #be2e4a) locally.
private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretSoft
private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretMid

private val stampFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val daySepFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val daySepFullFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE · d MMM")

private fun fileIcon(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Outlined.Image
    mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private fun initialsOf(name: String?): String =
    name?.split(" ")?.filter { it.isNotBlank() }
        ?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { "?" } ?: "?"

/** The advocate's name as recorded (the portal adds no prefix). */
private fun advName(name: String?): String {
    val n = name?.trim()?.takeIf { it.isNotBlank() } ?: return "your advocate"
    return n   // as recorded, as the portal shows it
}

@Composable
private fun SectionHead(text: String) {
    Text(text.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp, letterSpacing = 0.1.em, color = LlColor.Ink, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun DCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
        content = content,
    )
}
