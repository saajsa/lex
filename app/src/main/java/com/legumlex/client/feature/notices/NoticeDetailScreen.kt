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
                action = { androidx.compose.material3.TextButton(onClick = { vm.start(noticeId) }) { Text("Try Again", color = LlColor.Ink) } },
            )
            is NoticeDetailUiState.Ready -> Body(s.notice, approving, vm::approve, onMessageAdvocate, onOpen, onRespond, onOpenDocument, onOpenCase)
        }
    }
}

@Composable
private fun Body(
    n: NoticeDetail,
    approving: Boolean,
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
                    // Hide the response countdown / "overdue" once a response is
                    // recorded — a replied/closed/expired notice isn't "due".
                    val responded = n.children.isNotEmpty() ||
                        sum.status in listOf("Response Received", "No Response", "Expired")
                    if (sum.responseDueDate != null && !sum.isClosed && !responded) CountdownChipDetail(sum.responseDueDate)
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    (if (out) "Notice to " else "Notice from ") + sum.counterparty,
                    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LlColor.Ink,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    sum.referenceNumber?.let { RefChip(it) }
                    sum.createdAt?.let {
                        Text("·", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
                        Text("Created ${it.toLocalDate().format(SHORT)}", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
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
                        Text("In response to a received notice", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Amber)
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
                    if (!out) Text("tracks your reply", fontFamily = Inter, fontSize = 9.sp, color = LlColor.InkFaint)
                }
                val steps = railSteps(n)
                steps.forEachIndexed { i, step -> RailRow(step, sum.direction, last = i == steps.lastIndex) }
            }

            // ---------- approval gate: draft preview (tap to read inline) ----------
            val draftDoc = n.documents.firstOrNull()
            if (sum.needsApproval) {
                Section("Review the draft") {
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
                                Text("Tap to read the full notice", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
                            }
                        }
                    }
                }
            }

            // ---------- key facts ----------
            Section(if (out) "Recipient" else "What they're claiming") {
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
                        money(sum.claimAmount)?.let { Kv("Claim", it, amt = true) }
                        sum.responseDueDate?.let { Kv("Respond by", it.format(LONG), mono = true) }
                    }
                }
            }

            // ---------- service details (outbound, once dispatched) ----------
            if (out && (n.serviceMethod != null || n.trackingNumber != null || n.dispatchDate != null || n.deliveryDate != null)) {
                Section("Service details") {
                    Panel {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            n.serviceMethod?.let { Kv("Method", it, strong = true) }
                            n.trackingNumber?.let { Kv("Tracking", it, mono = true) }
                            n.dispatchDate?.let { Kv("Dispatched", it.format(LONG), mono = true) }
                            n.deliveryDate?.let { Kv("Delivered", it.format(LONG), mono = true) }
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
                Section("Your advocate") {
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
                Section(if (out) "Responses" else "Our response") {
                    Panel {
                        n.children.forEachIndexed { i, child ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                            ResponseRow(child) { onOpen(child.id) }
                        }
                    }
                }
            }

            // ---------- convert callout (outbound, response window open) ----------
            if (out && !sum.isClosed && sum.responseDueDate != null && !n.convertedToCase) {
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
                        Text("No response by ${sum.responseDueDate.format(SHORT)}?", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Violet)
                        Text("Your advocate will take the next step with you. Write to them on this notice.", fontFamily = Inter, fontSize = 11.sp, color = LlColor.Violet.copy(alpha = 0.8f))
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
        Footer(n, approving, onApprove, onMessageAdvocate, onRespond, onOpenDocument)
    }
}

/* ----------------------------------------------------------------- footer */

@Composable
private fun Footer(
    n: NoticeDetail, approving: Boolean,
    onApprove: () -> Unit, onMessageAdvocate: (String) -> Unit, onRespond: (String, String?) -> Unit,
    onOpenDocument: (String, String, String?) -> Unit,
) {
    val sum = n.summary
    val out = sum.direction == NoticeDirection.OUTBOUND
    val primaryDoc = n.documents.firstOrNull()
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    Column(Modifier.fillMaxWidth().background(LlColor.Paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when {
            // approval gate
            sum.needsApproval -> {
                var confirm by remember { mutableStateOf(false) }
                PrimaryBtn("Approve & Send", painterResource(R.drawable.ic_ms_check_circle), LlColor.Slate, loading = approving, onClick = { confirm = true })
                if (confirm) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("Send This Notice?") },
                        text = { Text("Approving sends the notice to ${sum.counterparty.ifBlank { "the other party" }} as drafted. Once it is dispatched it cannot be recalled.") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = false; onApprove() }) { Text("Approve & Send") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Not Yet") } },
                    )
                }
                n.consultationId?.let { cid -> GhostBtn("Request changes") { onMessageAdvocate(cid) } }
            }
            // inbound awaiting a reply
            !out && n.children.isEmpty() && !sum.isClosed -> {
                PrimaryBtn("Respond to this notice", painterResource(R.drawable.ic_ms_reply), LlColor.Slate) { onRespond(sum.id, n.senderName ?: sum.counterparty) }
            }
            // default — view the notice + message advocate
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (primaryDoc != null) {
                    Box(Modifier.weight(1f)) {
                        GhostIconBtn("View notice", painterResource(R.drawable.ic_ms_visibility)) { onOpenDocument(sum.id, primaryDoc.id, primaryDoc.name) }
                    }
                }
                n.consultationId?.let { cid ->
                    Box(Modifier.weight(1.4f)) {
                        PrimaryBtn("Message advocate", painterResource(R.drawable.ic_ms_forum), LlColor.Slate) { onMessageAdvocate(cid) }
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
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp, color = LlColor.Ink)
    }
}

@Composable
private fun PrimaryBtn(label: String, icon: Painter, color: Color, loading: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color).clickable(enabled = !loading, onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp, color = Color.White)
        }
    }
}

@Composable
private fun GhostBtn(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp, color = LlColor.Ink)
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
    val (bg, fg) = when {
        n.isClosed -> LlColor.Paper2 to LlColor.InkMuted
        n.needsApproval -> LlColor.AmberSoft to LlColor.Amber
        n.direction == NoticeDirection.OUTBOUND -> LlColor.SlateSoft to LlColor.SlateMid
        else -> LlColor.AmberSoft to LlColor.Amber
    }
    val label = when {
        n.needsApproval -> if (n.direction == NoticeDirection.INBOUND) "NEEDS REPLY" else "NEEDS YOUR OK"
        n.isClosed -> "CLOSED"
        else -> n.statusLabel.uppercase()
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = fg, maxLines = 1)
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
    val days = ChronoUnit.DAYS.between(LocalDate.now(), due)
    val soon = days in 0..10
    val bg = if (soon) LlColor.ClaretSoft else LlColor.AmberSoft
    val fg = if (soon) LlColor.ClaretMid else LlColor.Amber
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_ms_schedule), null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (days < 0) "overdue" else "$days days left", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = fg)
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
    DRAFT("Draft — awaiting your approval", R.drawable.ic_ms_draw, { it.warning }, draft = true),
    NOTICE("Legal notice", R.drawable.ic_ms_description, { it.danger }),
    PROOF("Proof of service", R.drawable.ic_ms_verified, { it.success }),
    RECEIVED("Received notice", R.drawable.ic_ms_mark_email_unread, { it.warning }),
    SUPPORTING("Supporting document", R.drawable.ic_ms_description, { it.blue });

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
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(LlColor.Amber).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("DRAFT", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp, color = Color.White)
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
            Text(listOfNotNull(n.referenceNumber, n.statusLabel).joinToString("  ·  "), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
        }
        Icon(painterResource(R.drawable.ic_ms_chevron_right), null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RailRow(step: RailStep, dir: NoticeDirection, last: Boolean) {
    val c = dirColors(dir)
    val dotBg = when (step.state) { StepState.DONE -> LlColor.Verdant; StepState.NOW -> c.solid; StepState.TODO -> Color.White }
    val dotBorder = when (step.state) { StepState.DONE -> LlColor.Verdant; StepState.NOW -> c.solid; StepState.TODO -> LlColor.BorderStrong }
    val dotIcon = if (step.state == StepState.DONE) R.drawable.ic_ms_check else step.icon
    val dotTint = if (step.state == StepState.TODO) LlColor.InkFaint else Color.White
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
    val created = s.createdAt?.toLocalDate()?.format(SHORT)
    return if (s.direction == NoticeDirection.OUTBOUND) {
        val now = when {
            s.needsApproval -> 2
            s.status == "Draft" -> 1
            s.status in listOf("Dispatched", "Sent") -> 3
            s.status in listOf("Served", "Delivered") -> 4
            s.status in listOf("Response Received", "No Response", "Expired") -> 5
            else -> 1
        }
        val titles = listOf(
            Triple("Submitted", created, R.drawable.ic_ms_check),
            Triple("Advocate drafting", null, R.drawable.ic_ms_draw),
            Triple("Draft ready — review & approve", if (now == 2) "Waiting for you" else null, R.drawable.ic_ms_draw),
            Triple("Dispatched", n.dispatchDate?.format(SHORT), R.drawable.ic_ms_send),
            Triple("Delivered / served", n.deliveryDate?.format(SHORT), R.drawable.ic_ms_mark_email_read),
            Triple("Response received", s.responseDueDate?.let { "Window ends ${it.format(SHORT)}" }, R.drawable.ic_ms_forum),
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
            Triple("Received & logged", created, R.drawable.ic_ms_check),
            Triple("Advocate reviewing", if (now == 1) "In progress" else null, R.drawable.ic_ms_rate_review),
            Triple("Response drafted — your approval", null, R.drawable.ic_ms_draw),
            Triple("Response sent", s.responseDueDate?.let { "Before ${it.format(SHORT)} deadline" }, R.drawable.ic_ms_send),
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
