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
fun ConversationThreadScreen(consultationId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ConsultationDetailViewModel = viewModel(
        key     = "cons-$consultationId",
        factory = viewModelFactory {
            initializer { ConsultationDetailViewModel(app = app, consultationId = consultationId) }
        },
    )
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

    Scaffold(
        bottomBar = {
            ThreadComposer(
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
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        val replies = (state as? ConsultationDetailUiState.Ready)?.consultation?.replies.orEmpty()
        if (state is ConsultationDetailUiState.Loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            return@Scaffold
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
                        "No messages yet. Your reply will start the thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
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
            // Read receipt eyebrow (currently a static "Sent" — real receipts later).
            if (isClient) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "SENT",
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
                    onClick = { /* attach later */ },
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
                    placeholder = { Text("Write a message", style = MaterialTheme.typography.bodyMedium) },
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
