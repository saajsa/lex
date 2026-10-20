package com.legumlex.client.feature.notices

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import com.legumlex.client.ahc.theme.Mono
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.StatusPalette
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.R
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.Inter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class StepState { DONE, NOW, TODO }

@Composable
fun NoticeDetailScreen(
    noticeId: String,
    onBack: () -> Unit,
    onMessageAdvocate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenDocument: (String, String, String?) -> Unit,
    onOpenCase: (String) -> Unit = {},
) {
    val vm: NoticeDetailViewModel = viewModel()
    LaunchedEffect(noticeId) { vm.start(noticeId) }
    val state by vm.state.collectAsState()
    val approving by vm.approving.collectAsState()
    val approveError by vm.approveError.collectAsState()

    // Coming back (e.g. after sending a reply) shows the notice as it now stands.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.resumed()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // No in-screen header — the app-wide header (logo · back · notifications) sits
    // above via the Scaffold. The casehead below serves as the content heading.
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        when (val s = state) {
            is NoticeDetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = LlColor.InkMuted, strokeWidth = 2.dp)
            }
            is NoticeDetailUiState.Error -> com.legumlex.client.ui.components.LexEmpty(
                "The Notice Could Not Be Opened", s.message,
                modifier = Modifier.fillMaxSize().padding(24.dp),
                action = if (s.message == NO_ACCESS) null else ({ androidx.compose.material3.TextButton(onClick = { vm.load() }) { Text("Try Again", color = LlColor.Ink) } }),
            )
            is NoticeDetailUiState.Ready -> Body(s.notice, approving, approveError, vm::approve, onMessageAdvocate, onOpen, onOpenDocument, onOpenCase)
        }
    }
}

@Composable
private fun Body(
    n: NoticeDetail,
    approving: Boolean,
    approveError: String?,
    onApprove: () -> Unit,
    onMessageAdvocate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenDocument: (String, String, String?) -> Unit,
    onOpenCase: (String) -> Unit,
) {
    val sum = n.summary
    val c = dirColors(sum.direction)
    val out = sum.direction == NoticeDirection.OUTBOUND

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---------- casehead ----------
            var info by remember { mutableStateOf(false) }
            if (info) SystemDetails(n) { info = false }
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CaseDirBadge(sum.direction)
                    StatusChip(sum)
                    // The label already says so when the server words it ("Awaiting Your Approval").
                    if (sum.needsApproval && sum.statusLabel.isBlank()) NeedsYouTag(sum.direction)
                    Spacer(Modifier.weight(1f))
                    // The system's own particulars, behind an (i): not part of the story.
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { info = true }, contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = "Notice details", tint = LlColor.InkMuted, modifier = Modifier.size(20.dp))
                    }
                }
                // Hide the response countdown once a response is recorded —
                // a replied/closed/expired notice isn't "due".
                if (sum.responseDueDate != null && !sum.isClosed && !sum.isAnswered()) {
                    Spacer(Modifier.height(8.dp))
                    CountdownChipDetail(sum.responseDueDate)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    (if (out) "Notice To " else "Notice From ") + sum.counterparty,
                    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LlColor.Ink,
                )
                (n.subject ?: sum.subject)?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted)
                }
                Spacer(Modifier.height(8.dp))
                sum.referenceNumber?.let { RefChip(it) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))

            // ---------- parent banner (this is a response) ----------
            n.parent?.let { p ->
                Row(
                    Modifier.fillMaxWidth().background(LlColor.AmberSoft).clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.AmberSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_ms_reply), null, tint = LlColor.Amber, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("In Response To A Received Notice", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Amber)
                        p.referenceNumber?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, color = LlColor.AmberMid) }
                    }
                    Icon(painterResource(R.drawable.ic_ms_chevron_right), null, tint = LlColor.AmberMid, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
            }

            // ---------- the tracker ----------
            // One story, top to bottom: what the screen used to repeat in
            // separate panels (the parties, the claim, service, the advocate,
            // remarks, documents, responses) is told at the step it belongs to.
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp)) {
                val steps = trackSteps(n, onOpen, onOpenCase) { doc -> onOpenDocument(sum.id, doc.id, doc.name) }
                steps.forEachIndexed { i, st -> TrackRow(st, last = i == steps.lastIndex) }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ---------- contextual footer ----------
        Footer(n, approving, approveError, onApprove, onMessageAdvocate, onOpenDocument)
    }
}

/* ----------------------------------------------------------------- footer */

@Composable
private fun Footer(
    n: NoticeDetail, approving: Boolean, approveError: String?,
    onApprove: () -> Unit, onMessageAdvocate: (String) -> Unit,
    onOpenDocument: (String, String, String?) -> Unit,
) {
    val sum = n.summary
    val out = sum.direction == NoticeDirection.OUTBOUND
    val primaryDoc = n.documents.firstOrNull { docKind(it, draftStage = true) == DocKind.DRAFT } ?: n.documents.firstOrNull()
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    Column(Modifier.fillMaxWidth().background(LlColor.Paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when {
            // approval gate
            sum.needsApproval -> {
                var confirm by remember { mutableStateOf(false) }
                // This tells the advocate the client is content with the draft. It
                // promises nothing about sending: dispatch stays with the advocate.
                PrimaryBtn("The Draft Is Fine", painterResource(R.drawable.ic_ms_check_circle), loading = approving, onClick = { confirm = true })
                approveError?.let {
                    Text(it, fontFamily = Inter, fontSize = 11.sp, color = LlColor.ClaretMid)
                }
                if (confirm) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("Tell Your Advocate The Draft Is Fine?") },
                        text = { Text("Your advocate will see that you have read this draft and are content with it. Anything further on this notice stays with them.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = false; onApprove() }) { Text("Confirm") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Not Yet") } },
                    )
                }
                n.consultationId?.let { cid -> GhostBtn("Ask For Changes") { onMessageAdvocate(cid) } }
            }
            // An inbound notice awaiting a reply: the reply is the firm's to
            // draft (a client does not start a notice); the default actions
            // below let the client view it and write to the advocate.
            // default — view the notice + message advocate
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (primaryDoc != null) {
                    Box(Modifier.weight(1f)) {
                        GhostIconBtn("View Notice", painterResource(R.drawable.ic_ms_visibility)) { onOpenDocument(sum.id, primaryDoc.id, primaryDoc.name) }
                    }
                }
                n.consultationId?.let { cid ->
                    Box(Modifier.weight(1.4f)) {
                        PrimaryBtn("Message Advocate", painterResource(R.drawable.ic_ms_forum)) { onMessageAdvocate(cid) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GhostIconBtn(label: String, icon: Painter, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LlColor.Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
    }
}

@Composable
private fun PrimaryBtn(label: String, icon: Painter, loading: Boolean = false, onClick: () -> Unit) {
    // Theme tokens, so the button inverts properly in dark mode.
    val bg = Lex.colors.primary
    val fg = Lex.colors.primaryForeground
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).clickable(enabled = !loading, onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = fg)
        }
    }
}

@Composable
private fun GhostBtn(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
    }
}

/* ----------------------------------------------------------------- pieces */

@Composable
private fun CaseDirBadge(d: NoticeDirection) {
    val c = dirColors(d)
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(c.soft).padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (d == NoticeDirection.OUTBOUND) painterResource(R.drawable.ic_ms_outgoing_mail) else painterResource(R.drawable.ic_ms_mark_email_unread), null, tint = c.solid, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (d == NoticeDirection.OUTBOUND) "OUTBOUND" else "INBOUND", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.5.sp, color = c.solid)
    }
}

@Composable
private fun StatusChip(n: NoticeSummary) {
    // The real status in Title Case, as the portal's notice_pill shows it.
    val (bg, fg) = when {
        n.isClosed || n.status in listOf("Cancelled", "Expired", "No Response") -> LlColor.Paper2 to LlColor.InkMuted
        n.status == "Returned" -> LlColor.AmberSoft to LlColor.Amber
        n.status in listOf("Served", "Delivered", "Response Received") -> LlColor.VerdantSoft to LlColor.Verdant
        else -> LlColor.SlateSoft to LlColor.SlateMid
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(noticeStatusTitle(n), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = fg, maxLines = 1)
    }
}

/** The plain explanation, kept apart from the status: something is waiting on the client. */
@Composable
private fun NeedsYouTag(d: NoticeDirection) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(LlColor.AmberSoft).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(if (d == NoticeDirection.INBOUND) "Reply Ready For You" else "Draft Ready For You",
            fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = LlColor.Amber, maxLines = 1)
    }
}

@Composable
private fun RefChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun CountdownChipDetail(due: LocalDate) {
    val days = ChronoUnit.DAYS.between(todayIst(), due)
    // A passed date is stated plainly: neutral tone, and the advocate advises.
    val (bg, fg) = when {
        days < 0 -> LlColor.Paper2 to LlColor.InkMuted
        days <= 10 -> LlColor.ClaretSoft to LlColor.ClaretMid
        else -> LlColor.AmberSoft to LlColor.Amber
    }
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_ms_schedule), null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(when { days < 0 -> "Response Date Passed — Your Advocate Will Advise"; days == 0L -> "Reply Date Is Today"; days == 1L -> "1 Day To Reply Date"; else -> "$days Days To Reply Date" }, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = fg)
    }
}



@Composable
private fun Kv(k: String, v: String, mono: Boolean = false, amt: Boolean = false, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k.uppercase(), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, modifier = Modifier.width(118.dp))
        Text(
            v,
            fontFamily = if (mono) Mono else Inter,
            fontWeight = if (amt || strong || mono) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.5.sp, color = LlColor.Ink, modifier = Modifier.weight(1f),
        )
    }
}

private enum class DocKind(val label: String, val icon: Int, private val tone: (LexColors) -> StatusPalette, val draft: Boolean = false) {
    DRAFT("Draft — For Your Review", R.drawable.ic_ms_draw, { it.warning }, draft = true),
    NOTICE("Legal Notice", R.drawable.ic_ms_description, { it.neutral }),
    PROOF("Proof Of Service", R.drawable.ic_ms_verified, { it.success }),
    RECEIVED("Received Notice", R.drawable.ic_ms_mark_email_unread, { it.warning }),
    SUPPORTING("Supporting Document", R.drawable.ic_ms_description, { it.blue });

    val bg: Color @Composable get() = tone(Lex.colors).softBg
    val fg: Color @Composable get() = tone(Lex.colors).softText
}

/** Classify a notice document — the legal-notice PDF is a DRAFT while the notice is
 *  pre-dispatch (status Draft), and the final served notice once sent. */
private fun docKind(doc: NoticeDocument, draftStage: Boolean): DocKind {
    val t = doc.tag?.lowercase().orEmpty()
    return when {
        t.contains("proof") || t.contains("service") || t.contains("return") -> DocKind.PROOF
        t.contains("received") -> DocKind.RECEIVED
        t.contains("support") -> DocKind.SUPPORTING
        t.contains("legal notice") || t.contains("notice") -> if (draftStage) DocKind.DRAFT else DocKind.NOTICE
        else -> if (draftStage) DocKind.DRAFT else DocKind.NOTICE
    }
}




/* ------------------------------------------------------------- tracker */

private class TrackLink(val label: String, val onClick: () -> Unit)

private class TrackStep(
    val title: String,
    val lines: List<String>,
    val links: List<TrackLink>,
    val icon: Int,
    val state: StepState,
)

/** One step: a large circle on a thick rail (the owner's reference), the title, then what belongs to it. */
@Composable
private fun TrackRow(step: TrackStep, last: Boolean) {
    val ink = Lex.colors.foreground
    val paper = Lex.colors.background
    val done = step.state == StepState.DONE
    val now = step.state == StepState.NOW
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            // The rail runs on to the next step, dark through what is done.
            if (!last) Box(Modifier.padding(top = 20.dp).width(4.dp).fillMaxHeight()
                .background(if (done) ink else Lex.colors.border))
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(when { done || now -> ink; else -> Lex.colors.muted })
                    .border(if (now) 4.dp else 1.5.dp, when { now -> ink.copy(alpha = 0.25f); done -> ink; else -> Lex.colors.border }, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(if (done) R.drawable.ic_ms_check else step.icon), null,
                    tint = if (done || now) paper else Lex.colors.mutedForeground, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = if (last) 4.dp else 22.dp)) {
            Text(step.title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp,
                color = if (step.state == StepState.TODO) Lex.colors.mutedForeground else ink)
            step.lines.forEach {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp,
                    color = if (step.state == StepState.TODO) Lex.colors.faint else Lex.colors.mutedForeground)
            }
            step.links.forEach { l ->
                Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = l.onClick).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(l.label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ink)
                    Icon(painterResource(R.drawable.ic_ms_chevron_right), null, tint = ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** The particulars only the record needs, shown on the (i). */
@Composable
private fun SystemDetails(n: NoticeDetail, onDismiss: () -> Unit) {
    val s = n.summary
    val rows = listOfNotNull(
        s.referenceNumber?.let { "Reference" to it },
        "Direction" to (if (s.direction == NoticeDirection.OUTBOUND) "Outbound" else "Inbound"),
        s.noticeType?.let { "Type" to it },
        "Status" to noticeStatusTitle(s),
        s.createdAt?.let { "Created On" to it.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")) },
        n.serviceMethod?.let { "Service" to it },
        n.courierServiceName?.trim()?.ifBlank { null }?.let { "Carrier" to it },
        n.trackingNumber?.let { "Tracking" to it },
        n.deliveryStatus?.let { "Delivery Status" to it },
        n.waitingPeriodDays.takeIf { it > 0 }?.let { "Waiting Period" to "$it days" },
        n.approvedAt?.let { "Approved On" to it.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")) },
        n.consultationId?.let { "Consultation" to "#$it" },
        n.caseId?.takeIf { n.convertedToCase }?.let { "Case" to "#$it" },
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notice Details") },
        text = { Column { rows.forEach { (k, v) -> Kv(k, v, mono = k == "Tracking" || k == "Reference") } } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

/** The steps, each carrying what the old panels said about it. */
private fun trackSteps(
    n: NoticeDetail,
    onOpen: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    openDoc: (NoticeDocument) -> Unit,
): List<TrackStep> {
    val s = n.summary
    val out = s.direction == NoticeDirection.OUTBOUND
    val created = s.createdAt?.toLocalDate()?.format(LONG)
    val draftStage = s.status == "Draft"
    val docs = n.documents.groupBy { docKind(it, draftStage) }
    val used = mutableSetOf<String>()
    fun docLinks(kind: DocKind, label: (NoticeDocument) -> String) =
        docs[kind].orEmpty().map { d -> used += d.id; TrackLink(label(d)) { openDoc(d) } }
    // Ours only on a notice we send; on a received one it is the sender's.
    val adv = n.signingAdvocateName?.takeIf { it.isNotBlank() && out }
    val claim = listOfNotNull(s.noticeType?.trim()?.ifBlank { null }, money(s.claimAmount)?.let { "Claim $it" }).joinToString(" · ").ifBlank { null }
    fun daysLeft(d: LocalDate): String {
        val left = ChronoUnit.DAYS.between(todayIst(), d)
        return when { left < 0 -> "passed"; left == 0L -> "today"; left == 1L -> "1 day left"; else -> "$left days left" }
    }

    val steps: MutableList<TrackStep>
    var now: Int
    if (out) {
        if (s.status == "Cancelled") {
            return listOf(
                TrackStep("Submitted", listOfNotNull(created?.let { "On $it" }), emptyList(), R.drawable.ic_ms_check, StepState.DONE),
                TrackStep("Cancelled", listOfNotNull(n.notes), emptyList(), R.drawable.ic_ms_lock, StepState.NOW),
            )
        }
        val returned = s.status == "Returned" && n.deemedServedDate == null
        val served = n.isServed || s.isServed || n.deemedServedDate != null || s.status in listOf("Served", "Delivered")
        val outcome = s.status in listOf("Response Received", "No Response", "Expired")
        now = when {
            outcome -> 6
            s.needsApproval -> 2
            s.status in listOf("Draft", "Reserved") -> 1
            returned -> 4
            served -> 5
            s.status in listOf("Dispatched", "Sent") -> 4
            else -> 1
        }
        val waitEnd = n.waitingEndDate ?: s.responseDueDate
        val open = !s.isClosed && !s.isAnswered()
        steps = mutableListOf(
            TrackStep("Submitted",
                listOfNotNull(created?.let { "On $it" }, "To ${n.recipientName ?: s.counterparty}", n.recipientAddress, claim),
                docLinks(DocKind.SUPPORTING) { "Supporting Document · ${it.name}" }, R.drawable.ic_ms_check, StepState.TODO),
            TrackStep("Advocate Drafting", listOfNotNull(adv?.let { "Prepared by $it" }), emptyList(), R.drawable.ic_ms_draw, StepState.TODO),
            TrackStep("Draft Ready For Your Review",
                listOfNotNull(
                    if (now == 2) "Waiting for you: read the draft, then tell your advocate it is fine or ask for changes." else null,
                    n.approvedAt?.let { "You confirmed it on ${it.toLocalDate().format(LONG)}" },
                ),
                if (now == 2) docLinks(DocKind.DRAFT) { "Read The Draft" } else emptyList(), R.drawable.ic_ms_rate_review, StepState.TODO),
            TrackStep("Dispatched",
                listOfNotNull(
                    n.dispatchDate?.let { "On ${it.format(LONG)}" },
                    listOf(n.serviceMethod, n.courierServiceName).mapNotNull { it?.trim()?.ifBlank { null } }.joinToString(" · ").ifBlank { null },
                    n.trackingNumber?.trim()?.ifBlank { null }?.let { "Tracking $it" },
                ),
                emptyList(), R.drawable.ic_ms_send, StepState.TODO),
            TrackStep(
                when { returned -> "Returned Undelivered"; n.deemedServedDate != null -> "Deemed Served"; else -> "Served" },
                listOfNotNull(
                    n.deliveryDate?.let { "Delivered ${it.format(LONG)}" },
                    n.receivedByPerson?.let { "Received by $it" },
                    n.returnDate?.let { "Returned ${it.format(LONG)}" },
                    n.returnReason,
                    n.deemedServedDate?.let { "Deemed served ${it.format(LONG)}" },
                    if (returned) "Your advocate will advise on what follows." else null,
                ),
                docLinks(DocKind.PROOF) { "Proof Of Service" }, R.drawable.ic_ms_mark_email_read, StepState.TODO),
            TrackStep("Waiting Period",
                listOfNotNull(
                    waitEnd?.let { if (it.isBefore(todayIst())) "Ended ${it.format(LONG)}" else "Ends ${it.format(LONG)}" + if (now == 5) " · ${daysLeft(it)}" else "" },
                    if (open && now == 5 && waitEnd != null && !waitEnd.isBefore(todayIst())) "If no reply comes by then, your advocate will advise on what follows." else null,
                ),
                emptyList(), R.drawable.ic_ms_schedule, StepState.TODO),
            TrackStep(
                when (s.status) { "Response Received" -> "Response Received"; "No Response" -> "No Response"; "Expired" -> "Waiting Period Over"; else -> "Reply Or Waiting Period Ends" },
                emptyList(),
                n.children.map { c -> TrackLink("Response · ${listOfNotNull(c.referenceNumber, noticeStatusTitle(c)).joinToString(" · ")}") { onOpen(c.id) } } +
                    listOfNotNull(if (n.convertedToCase) TrackLink("This Notice Became A Case") { n.caseId?.let(onOpenCase) } else null),
                R.drawable.ic_ms_forum, StepState.TODO),
        )
        if (outcome) now = steps.size   // every step done
    } else {
        val child = n.children.firstOrNull { it.direction == NoticeDirection.OUTBOUND } ?: n.children.firstOrNull()
        now = when {
            child == null -> 1
            child.status in listOf("Response Received", "No Response", "Expired") -> 4
            child.status in listOf("Dispatched", "Sent", "Served", "Delivered") -> 3
            else -> 2
        }
        val due = s.responseDueDate
        steps = mutableListOf(
            TrackStep("Received & Logged",
                listOfNotNull(
                    (n.receivedDate?.format(LONG) ?: created)?.let { "On $it" },
                    "From ${n.senderName ?: s.counterparty}",
                    n.senderAddress,
                    n.senderAdvocateName?.let { "Sent through $it" },
                    claim,
                ),
                docLinks(DocKind.RECEIVED) { "The Notice Received" }, R.drawable.ic_ms_mark_email_unread, StepState.TODO),
            TrackStep("Advocate Reviewing",
                // On a received notice the signing advocate is the other side's
                // (the one who signed it), so no name is given here.
                listOfNotNull(
                    if (now == 1) "Your advocate is reading the notice and will advise on a reply." else null,
                    due?.let { "Reply due by ${it.format(LONG)}" + if (now <= 2 && !s.isClosed) " · ${daysLeft(it)}" else "" },
                ),
                emptyList(), R.drawable.ic_ms_rate_review, StepState.TODO),
            TrackStep("Response Drafted For Your Review",
                listOfNotNull(child?.referenceNumber),
                listOfNotNull(child?.let { c -> TrackLink("Open The Response") { onOpen(c.id) } }), R.drawable.ic_ms_draw, StepState.TODO),
            TrackStep("Response Sent",
                listOfNotNull(child?.takeIf { now >= 3 }?.let { noticeStatusTitle(it) }),
                listOfNotNull(if (n.convertedToCase) TrackLink("This Notice Became A Case") { n.caseId?.let(onOpenCase) } else null),
                R.drawable.ic_ms_send, StepState.TODO),
        )
    }
    // The step in hand also carries the portal's "what happens next" and the advocate's remarks.
    val at = now.coerceAtMost(steps.lastIndex)
    steps[at] = steps[at].let { st ->
        TrackStep(st.title, st.lines + listOfNotNull(n.nextStep, n.notes?.let { "From your advocate: $it" }), st.links, st.icon, st.state)
    }
    // Any document not placed at its step belongs with the first.
    val rest = n.documents.filter { it.id !in used && docKind(it, draftStage) != DocKind.DRAFT && docKind(it, draftStage) != DocKind.NOTICE }
    if (rest.isNotEmpty()) steps[0] = steps[0].let { st ->
        TrackStep(st.title, st.lines, st.links + rest.map { d -> TrackLink(d.name) { openDoc(d) } }, st.icon, st.state)
    }
    return steps.mapIndexed { i, st -> TrackStep(st.title, st.lines, st.links, st.icon, stateFor(i, now)) }
}

private fun stateFor(index: Int, now: Int): StepState =
    when { index < now -> StepState.DONE; index == now -> StepState.NOW; else -> StepState.TODO }


private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
