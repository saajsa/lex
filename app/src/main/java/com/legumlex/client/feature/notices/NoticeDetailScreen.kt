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
private data class RailStep(val title: String, val desc: String?, val icon: Int, val state: StepState)

@Composable
fun NoticeDetailScreen(
    noticeId: String,
    onBack: () -> Unit,
    onMessageAdvocate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRespond: (String, String?) -> Unit,
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
            is NoticeDetailUiState.Ready -> Body(s.notice, approving, approveError, vm::approve, onMessageAdvocate, onOpen, onRespond, onOpenDocument, onOpenCase)
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
    onRespond: (String, String?) -> Unit,
    onOpenDocument: (String, String, String?) -> Unit,
    onOpenCase: (String) -> Unit,
) {
    val sum = n.summary
    val c = dirColors(sum.direction)
    val out = sum.direction == NoticeDirection.OUTBOUND

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---------- casehead ----------
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CaseDirBadge(sum.direction)
                    StatusChip(sum)
                    // The label already says so when the server words it ("Awaiting Your Approval").
                    if (sum.needsApproval && sum.statusLabel.isBlank()) NeedsYouTag(sum.direction)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    sum.referenceNumber?.let { RefChip(it) }
                    sum.createdAt?.let {
                        Text("·", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
                        Text("Created ${it.toLocalDate().format(LONG)}", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
                    }
                }
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

            // ---------- rail ----------
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 15.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Text("PROGRESS", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 1.4.sp, color = LlColor.InkFaint)
                    if (!out) Text("Tracks Your Reply", fontFamily = Inter, fontSize = 9.sp, color = LlColor.InkFaint)
                }
                val steps = railSteps(n)
                steps.forEachIndexed { i, step -> RailRow(step, sum.direction, last = i == steps.lastIndex) }
                // The portal's own "what happens next" sentence, verbatim.
                n.nextStep?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1)
                            .border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }

            // ---------- approval gate: draft preview (tap to read inline) ----------
            // The draft is the notice document by its tag, not whichever file came first.
            val draftDoc = n.documents.firstOrNull { docKind(it, draftStage = true) == DocKind.DRAFT } ?: n.documents.firstOrNull()
            if (sum.needsApproval) {
                Section("Review The Draft") {
                    Panel {
                        Column(
                            Modifier.fillMaxWidth()
                                .then(if (draftDoc != null) Modifier.clickable { onOpenDocument(sum.id, draftDoc.id, draftDoc.name) } else Modifier)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            DraftSheet()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_ms_visibility), null, tint = LlColor.InkFaint, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (draftDoc != null) "Tap To Read The Full Notice" else "Draft Not Yet Shared", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
                            }
                        }
                    }
                }
            }

            // ---------- key facts ----------
            Section(if (out) "Recipient" else "The Claim") {
                Panel {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        if (out) {
                            Kv("Recipient", n.recipientName ?: sum.counterparty)
                            n.recipientAddress?.let { Kv("Address", it) }
                            n.recipientContact?.let { Kv("Contact", it) }
                        } else {
                            Kv("Sender", n.senderName ?: sum.counterparty)
                            n.senderAddress?.let { Kv("Address", it) }
                            n.senderAdvocateName?.let { Kv("Advocate", it) }
                        }
                        sum.noticeType?.let { Kv("Type", it) }
                        if (!out) n.receivedDate?.let { Kv("Received", it.format(LONG)) }
                        money(sum.claimAmount)?.let { Kv("Claim", it, amt = true) }
                        sum.responseDueDate?.let { Kv("Reply Date", it.format(LONG)) }
                    }
                }
            }

            // ---------- service details (outbound, once dispatched) ----------
            if (out && (n.serviceMethod != null || n.trackingNumber != null || n.dispatchDate != null || n.deliveryDate != null ||
                    n.deemedServedDate != null || n.waitingEndDate != null || n.returnDate != null)) {
                Section("Service Details") {
                    Panel {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            n.serviceMethod?.let { Kv("Method", it, strong = true) }
                            n.trackingNumber?.let { Kv("Tracking", it, mono = true) }
                            n.dispatchDate?.let { Kv("Dispatched", it.format(LONG)) }
                            n.deliveryDate?.let { Kv("Delivered", it.format(LONG)) }
                            n.receivedByPerson?.let { Kv("Received By", it) }
                            n.returnDate?.let { Kv("Returned", it.format(LONG)) }
                            n.returnReason?.let { Kv("Reason", it) }
                            n.deemedServedDate?.let { Kv("Deemed Served", it.format(LONG)) }
                            n.waitingEndDate?.let { Kv("Waiting Ends", it.format(LONG)) }
                        }
                    }
                    // How many days of the statutory waiting period remain — information only.
                    n.waitingEndDate?.let { end ->
                        val left = ChronoUnit.DAYS.between(todayIst(), end)
                        if (left >= 0 && !sum.isClosed && !sum.isAnswered()) {
                            Text(
                                if (left == 0L) "The waiting period ends today." else "$left ${if (left == 1L) "day" else "days"} of the waiting period remain.",
                                fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                            )
                        }
                    }
                }
            }

            // ---------- remarks ----------
            n.notes?.let { r ->
                Section("Remarks") {
                    Panel { Text(r, fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.Ink, modifier = Modifier.padding(14.dp)) }
                }
            }

            // ---------- activity (oldest first) ----------
            if (n.activity.isNotEmpty()) {
                Section("Activity") {
                    Panel {
                        n.activity.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Ink, modifier = Modifier.weight(1f))
                                    a.date?.let { Text(it.format(LONG), fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint) }
                                }
                                a.detail?.let { Spacer(Modifier.height(2.dp)); Text(it, fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted) }
                            }
                        }
                    }
                }
            }

            // ---------- documents (tap to view inline) ----------
            // A "Legal Notice Document" is a DRAFT while the notice is pre-dispatch
            // (status Draft) and the FINAL served notice once sent — differentiated below.
            if (n.documents.isNotEmpty()) {
                val draftStage = sum.status == "Draft"
                Section("Documents") {
                    Panel {
                        n.documents.forEachIndexed { i, doc ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                            DocRow(doc, draftStage) { onOpenDocument(sum.id, doc.id, doc.name) }
                        }
                    }
                }
            }

            // ---------- advocate ----------
            n.signingAdvocateName?.takeIf { it.isNotBlank() }?.let { adv ->
                Section("Your Advocate") {
                    Panel {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2), contentAlignment = Alignment.Center) {
                                Text(initials(adv), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LlColor.InkMuted)
                            }
                            Spacer(Modifier.width(11.dp))
                            Text(adv, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink, modifier = Modifier.weight(1f))
                            n.consultationId?.let { cid ->
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).clickable { onMessageAdvocate(cid) }, contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_ms_forum), null, tint = LlColor.SlateMid, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ---------- responses (nested) ----------
            if (n.children.isNotEmpty()) {
                Section(if (out) "Responses" else "Our Response") {
                    Panel {
                        n.children.forEachIndexed { i, child ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                            ResponseRow(child) { onOpen(child.id) }
                        }
                    }
                }
            }

            // ---------- convert callout (outbound, response window open) ----------
            if (out && !sum.isClosed && !sum.isAnswered() && sum.responseDueDate != null && !n.convertedToCase) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
                        .background(LlColor.VioletSoft).padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_ms_gavel), null, tint = LlColor.Violet, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("If No Reply Comes By ${sum.responseDueDate.format(LONG)}", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Violet)
                        Text("Your advocate will advise on what follows.", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
                    }
                }
            }
            if (n.convertedToCase) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VioletSoft)
                        .clickable(enabled = !n.caseId.isNullOrBlank()) { n.caseId?.let(onOpenCase) }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_ms_gavel), null, tint = LlColor.Violet, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Text("This Notice Became A Case", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Violet, modifier = Modifier.weight(1f))
                    if (!n.caseId.isNullOrBlank()) Icon(painterResource(R.drawable.ic_ms_chevron_right), "Open the case", tint = LlColor.Violet, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ---------- contextual footer ----------
        Footer(n, approving, approveError, onApprove, onMessageAdvocate, onRespond, onOpenDocument)
    }
}

/* ----------------------------------------------------------------- footer */

@Composable
private fun Footer(
    n: NoticeDetail, approving: Boolean, approveError: String?,
    onApprove: () -> Unit, onMessageAdvocate: (String) -> Unit, onRespond: (String, String?) -> Unit,
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
            // inbound awaiting a reply
            !out && n.children.isEmpty() && !sum.isClosed -> {
                PrimaryBtn("Respond To This Notice", painterResource(R.drawable.ic_ms_reply)) { onRespond(sum.id, n.senderName ?: sum.counterparty) }
            }
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), fontFamily = Inter, fontSize = 10.sp, letterSpacing = 1.4.sp, color = LlColor.InkFaint, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 9.dp))
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp))) { content() }
}

@Composable
private fun Kv(k: String, v: String, mono: Boolean = false, amt: Boolean = false, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k.uppercase(), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, modifier = Modifier.width(88.dp))
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

@Composable
private fun DocRow(doc: NoticeDocument, draftStage: Boolean, onClick: () -> Unit) {
    val kind = docKind(doc, draftStage)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(kind.bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(kind.icon), null, tint = kind.fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(doc.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Ink, maxLines = 1)
                if (kind.draft) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(LlColor.AmberSoft).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("DRAFT", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp, color = LlColor.Amber)
                    }
                }
            }
            // the document KIND, not the raw tag — differentiates draft / notice / proof / etc.
            Text(kind.label, fontFamily = Inter, fontSize = 10.sp, color = kind.fg)
        }
        // inline view affordance (eye), not download
        Icon(painterResource(R.drawable.ic_ms_visibility), null, tint = LlColor.SlateMid, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DraftSheet() {
    Column(
        Modifier.width(150.dp).height(194.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).padding(16.dp),
    ) {
        Text("LEGAL NOTICE", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, color = LlColor.Ink)
        Spacer(Modifier.height(10.dp))
        listOf(0.96f, 0.86f, 0.74f, 1f, 0.96f, 0.86f, 0.96f, 0.52f).forEach {
            Box(Modifier.fillMaxWidth(it).height(4.dp).clip(RoundedCornerShape(2.dp)).background(LlColor.Paper2))
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun ResponseRow(n: NoticeSummary, onClick: () -> Unit) {
    val c = dirColors(n.direction)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.soft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_ms_reply), null, tint = c.solid, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("To " + n.counterparty, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Ink)
            Text(listOfNotNull(n.referenceNumber, noticeStatusTitle(n)).joinToString("  ·  "), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
        }
        Icon(painterResource(R.drawable.ic_ms_chevron_right), null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RailRow(step: RailStep, dir: NoticeDirection, last: Boolean) {
    val c = dirColors(dir)
    val dotBg = when (step.state) { StepState.DONE -> LlColor.Verdant; StepState.NOW -> c.solid; StepState.TODO -> Lex.colors.card }
    val dotBorder = when (step.state) { StepState.DONE -> LlColor.Verdant; StepState.NOW -> c.solid; StepState.TODO -> LlColor.BorderStrong }
    val dotIcon = if (step.state == StepState.DONE) R.drawable.ic_ms_check else step.icon
    val dotTint = if (step.state == StepState.TODO) LlColor.InkFaint else Lex.colors.card
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(dotBg).border(2.dp, dotBorder, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(dotIcon), null, tint = dotTint, modifier = Modifier.size(12.dp))
            }
            if (!last) Box(Modifier.width(2.dp).height(if (step.desc != null) 30.dp else 22.dp).background(if (step.state == StepState.DONE) LlColor.VerdantMid else LlColor.Paper2))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.padding(bottom = if (last) 2.dp else 15.dp)) {
            Text(
                step.title, fontFamily = Inter,
                fontWeight = if (step.state == StepState.TODO) FontWeight.Normal else FontWeight.SemiBold,
                fontSize = 13.sp,
                color = when (step.state) { StepState.TODO -> LlColor.InkFaint; StepState.NOW -> c.solid; else -> LlColor.Ink },
            )
            step.desc?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
            }
        }
    }
}

/* --------------------------------------------------------- rail builder */

private fun railSteps(n: NoticeDetail): List<RailStep> {
    val s = n.summary
    val created = s.createdAt?.toLocalDate()?.format(LONG)
    return if (s.direction == NoticeDirection.OUTBOUND) {
        if (s.status == "Cancelled") {
            return listOf(
                RailStep("Submitted", created, R.drawable.ic_ms_check, StepState.DONE),
                RailStep("Cancelled", null, R.drawable.ic_ms_lock, StepState.NOW),
            )
        }
        // A returned notice that the law deems served moves on to the waiting period.
        val returned = s.status == "Returned" && n.deemedServedDate == null
        val served = n.isServed || s.isServed || n.deemedServedDate != null || s.status in listOf("Served", "Delivered")
        val outcome = s.status in listOf("Response Received", "No Response", "Expired")
        val now = when {
            outcome -> 7                         // every step done
            s.needsApproval -> 2
            s.status in listOf("Draft", "Reserved") -> 1
            returned -> 4                        // stuck at service
            served -> 5                          // waiting period running
            s.status in listOf("Dispatched", "Sent") -> 4
            else -> 1
        }
        val serviceTitle = when {
            returned -> "Returned Undelivered"
            n.deemedServedDate != null -> "Deemed Served"
            else -> "Served"
        }
        val serviceDesc = when {
            returned -> listOfNotNull(n.returnDate?.let { "Returned ${it.format(LONG)}" }, n.returnReason, "your advocate will advise").joinToString(" · ")
            n.deemedServedDate != null -> "Deemed served ${n.deemedServedDate.format(LONG)}"
            else -> n.deliveryDate?.format(LONG)
        }
        val waitEnd = n.waitingEndDate ?: s.responseDueDate
        val waitDesc = waitEnd?.let { end ->
            val left = ChronoUnit.DAYS.between(todayIst(), end)
            if (now == 5 && left >= 0) "Ends ${end.format(LONG)} · $left ${if (left == 1L) "day" else "days"} left" else "Ends ${end.format(LONG)}"
        }
        val outcomeTitle = when (s.status) {
            "Response Received" -> "Response Received"
            "No Response" -> "No Response"
            "Expired" -> "Waiting Period Over"
            else -> "Reply Or Waiting Period Ends"
        }
        val titles = listOf(
            Triple("Submitted", created, R.drawable.ic_ms_check),
            Triple("Advocate Drafting", null, R.drawable.ic_ms_draw),
            Triple("Draft Ready For Your Review", if (now == 2) "Waiting for you" else null, R.drawable.ic_ms_draw),
            Triple("Dispatched", n.dispatchDate?.format(LONG), R.drawable.ic_ms_send),
            Triple(serviceTitle, serviceDesc, R.drawable.ic_ms_mark_email_read),
            Triple("Waiting Period", waitDesc, R.drawable.ic_ms_schedule),
            Triple(outcomeTitle, null, R.drawable.ic_ms_forum),
        )
        titles.mapIndexed { i, t -> RailStep(t.first, t.second, t.third, stateFor(i, now)) }
    } else {
        val child = n.children.firstOrNull { it.direction == NoticeDirection.OUTBOUND } ?: n.children.firstOrNull()
        val now = when {
            child == null -> 1
            child.status in listOf("Response Received", "No Response", "Expired") -> 4
            child.status in listOf("Dispatched", "Sent", "Served", "Delivered") -> 3
            child.status == "Draft" -> 2
            else -> 2
        }
        val titles = listOf(
            Triple("Received & Logged", (n.receivedDate?.format(LONG) ?: created), R.drawable.ic_ms_check),
            Triple("Advocate Reviewing", if (now == 1) "In progress" else null, R.drawable.ic_ms_rate_review),
            Triple("Response Drafted For Your Review", null, R.drawable.ic_ms_draw),
            Triple("Response Sent", s.responseDueDate?.let { "Reply date ${it.format(LONG)}" }, R.drawable.ic_ms_send),
        )
        titles.mapIndexed { i, t -> RailStep(t.first, t.second, t.third, stateFor(i, now)) }
    }
}

private fun stateFor(index: Int, now: Int): StepState =
    when { index < now -> StepState.DONE; index == now -> StepState.NOW; else -> StepState.TODO }

private fun initials(name: String): String {
    val clean = name.replace(Regex("(?i)^(adv\\.?|advocate|mr\\.?|ms\\.?|mrs\\.?)\\s+"), "").trim()
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "AD"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
    }
}

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
