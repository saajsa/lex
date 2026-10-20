package com.legumlex.client.feature.lead

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.auth.SessionRole
import com.legumlex.client.feature.consultations.Reply
import com.legumlex.client.feature.consultations.detail.ThreadDaySeparator
import com.legumlex.client.feature.consultations.detail.ThreadComposer
import com.legumlex.client.feature.consultations.detail.ThreadMessage
import com.legumlex.client.feature.consultations.relativeAgo
import com.legumlex.client.feature.more.MenuRow
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * The app for a lead (owner, 2026-10-20): someone signed in with Google whom
 * the firm has no open account for. The desk, the Agreement Check, their
 * enquiries, and the way to become a client; the client app opens by itself
 * once the firm approves them.
 */

@Composable
fun LeadHomeScreen(
    onAsk: () -> Unit,
    onAgreementCheck: () -> Unit,
    onEnquiries: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val name by SessionRole.name.collectAsState()
    val status by SessionRole.status.collectAsState()
    val first = name.trim().substringBefore(' ')
    val repo = remember(context) { LeadRepository(context) }
    val enquiries by produceState<List<LeadEnquiry>?>(initialValue = null) { value = repo.enquiries().getOrNull() }

    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "Welcome", title = if (first.isNotBlank()) "Hello, $first" else "Welcome")
        Text(
            "Ask the firm's advocates a question, or have an agreement read. Your enquiries and their replies are kept here.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground,
        )
        if (status == "awaiting_approval") {
            LexCard(title = "With The Firm For Approval") {
                Text(
                    "Your registration is with the firm. We will write to you when your account opens, and the app will then open it for you.",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, modifier = Modifier.padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Eyebrow("Ask", modifier = Modifier.padding(start = 4.dp))
        LexCard {
            MenuRow(Icons.Outlined.QuestionAnswer, "Ask The Advocates", "Write your question; the firm's advocates reply here.", onAsk)
            HorizontalDivider(color = Lex.colors.border)
            MenuRow(Icons.Outlined.TaskAlt, "Agreement Check", "Have an agreement read before you sign it.", onAgreementCheck)
        }
        Eyebrow("Yours", modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        LexCard {
            val n = enquiries?.size ?: 0
            MenuRow(Icons.Outlined.Forum, "My Enquiries",
                when (n) { 0 -> "The questions you ask, and the advocates' replies."; 1 -> "One enquiry, and the advocates' replies."; else -> "$n enquiries, and the advocates' replies." },
                onEnquiries)
        }
        if (status != "awaiting_approval") {
            Eyebrow("Become A Client", modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            LexCard {
                MenuRow(Icons.Outlined.HowToReg, "Open An Account",
                    "Register with your identity documents. The firm approves every new account; this app then opens as your client app.", onOpenAccount)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun LeadEnquiriesScreen(onOpen: (String) -> Unit, onAsk: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { LeadRepository(context) }
    var list by remember { mutableStateOf<List<LeadEnquiry>?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        repo.enquiries().onSuccess { list = it }.onFailure { error = it.message.orEmpty() }
    }
    Column(Modifier.fillMaxSize().background(Lex.colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(eyebrow = "Yours", title = "My Enquiries")
        when {
            list == null && error.isBlank() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
            }
            error.isNotBlank() -> Text(error, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.danger.softText)
            list.orEmpty().isEmpty() -> LexCard {
                Column(Modifier.padding(16.dp)) {
                    Text("No Enquiries Yet", style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                    Spacer(Modifier.height(4.dp))
                    Text("A question you ask the advocates is kept here, with their replies.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onAsk) { Text("Ask The Advocates") }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list.orEmpty(), key = { it.sessionHash }) { e -> EnquiryCard(e) { onOpen(e.sessionHash) } }
            }
        }
    }
}

@Composable
private fun EnquiryCard(e: LeadEnquiry, onClick: () -> Unit) {
    LexCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(if (e.kind == "ahc") "Agreement Check" else "Enquiry", modifier = Modifier.weight(1f))
                if (e.unread > 0) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Lex.colors.danger.solid))
                    Spacer(Modifier.width(6.dp))
                }
                e.lastActivity?.let { Text(relativeAgo(it), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground) }
            }
            Spacer(Modifier.height(6.dp))
            Text(e.summary.ifBlank { e.title }, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.label, style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                if (e.reference.isNotBlank()) Text(e.reference, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
        }
    }
}

@Composable
fun LeadThreadScreen(hash: String) {
    val context = LocalContext.current
    val repo = remember(context) { LeadRepository(context) }
    val scope = rememberCoroutineScope()
    var thread by remember { mutableStateOf<LeadThread?>(null) }
    var error by remember { mutableStateOf("") }
    var composing by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    suspend fun load() { repo.thread(hash).onSuccess { thread = it; error = "" }.onFailure { error = it.message.orEmpty() } }
    LaunchedEffect(hash) { load() }
    LaunchedEffect(thread?.messages?.size) { thread?.messages?.size?.takeIf { it > 0 }?.let { listState.animateScrollToItem(it + 1) } }

    Column(Modifier.fillMaxSize().background(Lex.colors.background).imePadding()) {
        val t = thread
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (error.isNotBlank()) Text(error, color = Lex.colors.danger.softText)
                else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("head") {
                Column {
                    Eyebrow(t.reference.ifBlank { "Enquiry" })
                    Spacer(Modifier.height(6.dp))
                    Text(t.summary.ifBlank { t.title }, style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                    Spacer(Modifier.height(6.dp))
                    Text(listOf(t.label, t.advocate).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
            }
            val grouped = linkedMapOf<LocalDate, MutableList<Reply>>()
            t.messages.forEach { r -> r.createdAt?.toLocalDate()?.let { grouped.getOrPut(it) { mutableListOf() }.add(r) } }
            grouped.forEach { (d, msgs) ->
                item("d-$d") { ThreadDaySeparator(d) }
                msgs.forEach { r -> item("m-${r.id}") { ThreadMessage(r) } }
            }
            if (t.messages.isEmpty()) item("empty") {
                Text("Your advocate's reply will appear here. We will also write to you by email.",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground, modifier = Modifier.padding(top = 24.dp))
            }
        }
        if (t.canReply) {
            ThreadComposer(
                value = composing, onChange = { composing = it }, isSending = sending, canAttach = false,
                onSend = {
                    val msg = composing.trim()
                    if (msg.isNotBlank()) scope.launch {
                        sending = true
                        repo.reply(hash, msg).onSuccess { composing = ""; load() }.onFailure { error = it.message.orEmpty() }
                        sending = false
                    }
                },
            )
        } else {
            Text(t.closedReason ?: "You can reply once your advocate has written.",
                style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
        if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = Lex.colors.danger.softText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun LeadMoreScreen(onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "More", title = "Your Account")
        val name by SessionRole.name.collectAsState()
        if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
        LexCard(title = "Appearance") {
            val choice by com.legumlex.client.ui.theme.ThemePrefs.choice.collectAsState()
            Column(Modifier.padding(16.dp)) {
                com.legumlex.client.ui.components.SegmentedToggle(
                    options = com.legumlex.client.ui.theme.ThemeChoice.entries.map { it to it.label },
                    selected = choice,
                    onSelect = { com.legumlex.client.ui.theme.ThemePrefs.set(ctx, it) },
                )
                Spacer(Modifier.height(8.dp))
                Text("Kept on this phone.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
        }
        OutlinedButton(
            onClick = { confirm = true }, enabled = !signingOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, Lex.colors.input),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Lex.colors.card, contentColor = Lex.colors.danger.softText),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (signingOut) "Signing Out…" else "Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sign Out?") },
            text = { Text("You can sign in again at any time with the same Google account.") },
            confirmButton = { TextButton(onClick = { confirm = false; signingOut = true; onSignOut() }) { Text("Sign Out", color = Lex.colors.danger.softText) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Lex.colors.foreground) } },
            containerColor = Lex.colors.card,
        )
    }
}
