package com.legumlex.client.feature.consultations.detail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.feature.consultations.ConsultationDetailUiState
import com.legumlex.client.feature.consultations.ConsultationDetailViewModel
import com.legumlex.client.feature.consultations.Reply
import com.legumlex.client.feature.consultations.ReplyEvent
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Screen #11 — Conversation Thread, full-screen.
// Asymmetric layout (NOT bubble chat): client right-aligned with right-edge
// accent, advocate left-aligned with left-edge accent. Plain body text inside
// 2px-bordered cards, day separators, monospace read receipts.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationThreadScreen(
    consultationId: String,
    onBack: () -> Unit,
    /** Where a new question starts once this one is closed (the desk). */
    onNewQuestion: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ConsultationDetailViewModel = viewModel(
        key     = "cons-$consultationId",
        factory = viewModelFactory {
            initializer { ConsultationDetailViewModel(app = app, consultationId = consultationId) }
        },
    )
    // Attach: the same upload the consultation's own composer makes, after
    // the client confirms the file (it is shared with the advocate at once).
    var pendingUpload by remember { mutableStateOf<android.net.Uri?>(null) }
    val attachPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingUpload = uri }

    val state by vm.state.collectAsState()
    val isSending by vm.isSending.collectAsState()
    var composing by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Surface failed sends.
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is ReplyEvent.Failed ->
                    android.widget.Toast.makeText(context, ev.message, android.widget.Toast.LENGTH_LONG).show()
                is ReplyEvent.Info ->
                    android.widget.Toast.makeText(context, ev.message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-scroll to newest message when replies change.
    LaunchedEffect((state as? ConsultationDetailUiState.Ready)?.consultation?.replies?.size) {
        val total = ((state as? ConsultationDetailUiState.Ready)?.consultation?.replies?.size ?: 0)
        if (total > 0) listState.animateScrollToItem(total + 5) // overshoot for headers
    }

    val ready = state as? ConsultationDetailUiState.Ready
    // A closed, declined or resolved consultation takes no further messages,
    // exactly as its detail screen has it.
    val locked = ready?.consultation?.status?.let { it in setOf(
        com.legumlex.client.feature.consultations.ConsultationStatus.COMPLETED,
        com.legumlex.client.feature.consultations.ConsultationStatus.CLOSED,
        com.legumlex.client.feature.consultations.ConsultationStatus.CANCELLED,
        com.legumlex.client.feature.consultations.ConsultationStatus.DECLINED,
        com.legumlex.client.feature.consultations.ConsultationStatus.CONVERTED,
    ) } == true

    Scaffold(
        bottomBar = {
            if (ready == null) {
                // Nothing to reply to until the consultation has loaded.
            } else if (locked) {
                LockedComposer(onNewQuestion)
            } else {
                ThreadComposer(
                    onAttach = { attachPicker.launch(arrayOf("*/*")) },
                    value = composing,
                    onChange = { composing = it },
                    onSend = {
                        val msg = composing.trim()
                        if (msg.isNotBlank()) {
                            vm.sendReply(msg)
                            composing = ""
                        }
                    },
                    isSending = isSending,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        // Edge-to-edge: the composer rides above the keyboard.
        modifier = Modifier.imePadding(),
    ) { inner ->
        val replies = ready?.consultation?.replies.orEmpty()
        when (val s = state) {
            is ConsultationDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                return@Scaffold
            }
            is ConsultationDetailUiState.Error -> {
                Column(
                    Modifier.fillMaxSize().padding(inner).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("The Conversation Could Not Be Loaded", style = MaterialTheme.typography.titleMedium, color = PrimaryText)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.load() }) { Text("Try Again") }
                }
                return@Scaffold
            }
            else -> Unit
        }
        val spacing = LocalSpacing.current
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(
                horizontal = spacing.s4,
                vertical   = spacing.s3,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Group by day for separators.
            val grouped: LinkedHashMap<LocalDate, MutableList<Reply>> = linkedMapOf()
            replies.forEach { r ->
                val d = r.createdAt?.toLocalDate() ?: return@forEach
                grouped.getOrPut(d) { mutableListOf() }.add(r)
            }
            grouped.forEach { (date, msgs) ->
                item(key = "d-${date}") { DaySeparator(date) }
                msgs.forEach { r ->
                    item(key = "m-${r.id}") { ThreadMessage(r) }
                }
            }
            if (replies.isEmpty()) {
                item("empty") {
                    Text(
                        if (locked) "No messages were exchanged on this consultation."
                        else "No messages yet. Your reply will start the thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
    }

    pendingUpload?.let { uri ->
        val name = remember(uri) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "this file"
        }
        AlertDialog(
            onDismissRequest = { pendingUpload = null },
            title = { Text("Share This File?") },
            text = { Text("$name will be added to this consultation and shared with your advocate.") },
            confirmButton = {
                TextButton(onClick = { vm.uploadAttachment(uri); pendingUpload = null }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { pendingUpload = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LockedComposer(onNewQuestion: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            HorizontalDivider(color = BorderSubtle)
            Spacer(Modifier.height(12.dp))
            Text(
                "This consultation is closed to new messages. The conversation stays here for your records.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onNewQuestion,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentInk, contentColor = InverseText),
            ) { Text("Ask A New Question") }
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(color = BorderSubtle, modifier = Modifier.weight(1f))
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            ),
            color = TertiaryText,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        HorizontalDivider(color = BorderSubtle, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThreadMessage(r: Reply) {
    val isClient = r.fromClient
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isClient) Arrangement.End else Arrangement.Start,
    ) {
        if (!isClient) InitialsAvatar(r.authorName, modifier = Modifier.padding(end = 8.dp, top = 18.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(start = if (isClient) 36.dp else 0.dp, end = if (isClient) 0.dp else 36.dp),
            horizontalAlignment = if (isClient) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isClient) "You" else r.authorName.ifBlank { "Your advocate" },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText,
                )
                Spacer(Modifier.width(8.dp))
                r.createdAt?.let {
                    Text(
                        it.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter, fontSize = 10.5.sp),
                        color = TertiaryText,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Asymmetric edge-accent message body:
            // - client: 2dp BLACK border on the RIGHT edge
            // - advocate: 2dp grey border on the LEFT edge
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isClient) Arrangement.End else Arrangement.Start) {
                if (!isClient) {
                    Box(modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 28.dp)
                        .fillMaxHeight()
                        .background(BorderColor),
                    )
                }
                Surface(
                    color = if (isClient) Surface2 else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(
                        topStart     = if (isClient) 12.dp else 0.dp,
                        topEnd       = if (isClient) 0.dp else 12.dp,
                        bottomStart  = if (isClient) 12.dp else 0.dp,
                        bottomEnd    = if (isClient) 0.dp else 12.dp,
                    ),
                ) {
                    Text(
                        r.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                if (isClient) {
                    Box(modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 28.dp)
                        .fillMaxHeight()
                        .background(AccentInk),
                    )
                }
            }
            // Read receipt: read once the advocate has opened it (read_at).
            if (isClient) {
                Spacer(Modifier.height(2.dp))
                Text(
                    if (r.readAt != null) "READ" else "SENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.96.sp,
                    ),
                    color = TertiaryText,
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ').take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifBlank { "·" }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(AccentInk),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = InverseText,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadComposer(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    onAttach: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = BorderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.s3, vertical = spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Surface2),
                ) {
                    Icon(Icons.Outlined.AttachFile, null, tint = PrimaryText, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write A Message", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderColor,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = PrimaryText,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = !isSending && value.trim().isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (value.trim().isNotBlank() && !isSending) AccentInk
                            else AccentInk.copy(alpha = 0.4f)
                        ),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = InverseText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
