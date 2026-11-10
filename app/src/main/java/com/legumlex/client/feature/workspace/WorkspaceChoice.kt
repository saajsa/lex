package com.legumlex.client.feature.workspace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.feature.board.Cormorant
import com.legumlex.client.ui.brand.LegumMark

private val Ink = Color(0xFF151513)
private val Paper = Color(0xFFF4F3EF)
private val Gold = Color(0xFFF5C518)
private val Mute = Color(0xFF6B6A65)
private val Rule = Color(0xFFDCD9D2)
private val Ease = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * "Where to today?" (REQ-010, 10a): shown at every opening for a person who holds both workspaces. Counsel is the dark card,
 * the Client Portal the white one. The server lists what can be entered; nothing is remembered between openings.
 */
/** The name line: "Name," before "where to today?", or just "Welcome" when no name is known. */
fun greetingName(name: String): String = if (name.isNotBlank()) "${name.trim()}," else "Welcome"

@Composable
fun WorkspaceChoiceScreen(onPick: (Mode) -> Unit, hour: Int = java.time.LocalTime.now().hour) {
    val ws by WorkspaceSession.workspaces.collectAsState()
    val name by WorkspaceSession.name.collectAsState()
    val note by WorkspaceSession.note.collectAsState()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { WorkspaceSession.noteShown() } }
    val enterable = ws?.items.orEmpty().filter { it.enter }
    val context = androidx.compose.ui.platform.LocalContext.current
    // After the process was killed the screen comes back with nothing known: the server is asked again (a person who now holds one
    // workspace goes straight to it).
    LaunchedEffect(Unit) {
        if (WorkspaceSession.workspaces.value == null) {
            WorkspaceSession.open(com.legumlex.client.data.api.ApiClient.get(context))
            val m = WorkspaceSession.mode.value
            if (m != Mode.Unknown) onPick(m)
        }
    }
    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(36.dp))
            Column(Modifier.padding(horizontal = 6.dp)) {
                LegumMark(28.dp, fillColor = Ink)
                Spacer(Modifier.height(28.dp))
                Rise(0) { Text(greeting(hour), fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.4.sp, color = Mute) }
                Spacer(Modifier.height(8.dp))
                Rise(1) {
                    Column {
                        Text(greetingName(name), fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 37.sp, color = Ink)
                        Text("where to today?", fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 37.sp, color = Ink)
                    }
                }
            }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(note, fontSize = 13.sp, color = Mute, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Spacer(Modifier.height(36.dp))
            if (ws == null) androidx.compose.material3.CircularProgressIndicator(Modifier.padding(horizontal = 6.dp).size(24.dp), strokeWidth = 2.dp, color = Mute)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                enterable.forEachIndexed { i, w ->
                    val counsel = w.kind == KIND_COUNSEL
                    Rise(2 + i) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 176.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (counsel) Ink else Color.White)
                                .then(if (counsel) Modifier else Modifier.border(BorderStroke(1.dp, Rule), RoundedCornerShape(12.dp)))
                                .clickable { onPick(if (counsel) Mode.Counsel else Mode.Portal) }
                                .semantics { contentDescription = "Open ${w.label}" }
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                        ) {
                            // The arrow sits at the foot on the right: the text keeps clear of it and the card grows to hold the figures.
                            Column(Modifier.padding(end = 48.dp, bottom = 4.dp)) {
                                Icon(if (counsel) Icons.Outlined.CalendarMonth else Icons.Outlined.FolderOpen, null, tint = if (counsel) Gold else Ink, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.height(22.dp))
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(w.label.uppercase(), fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.4.sp, color = if (counsel) Gold else Mute)
                                    // The firm has not yet verified the enrolment: said beside the name, as on the web.
                                    if (counsel && w.status == "pending") Text(
                                        "WITH OUR REVIEWERS", fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.4.sp, color = Ink,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Gold).padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(if (counsel) "Your Daily Board" else "Your matters with the firm", fontSize = 22.sp, fontWeight = FontWeight.Light, color = if (counsel) Paper else Ink)
                                val lines = statLines(w)
                                if (lines.isNotEmpty()) {
                                    Spacer(Modifier.height(14.dp))
                                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        lines.forEach { (num, words) ->
                                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(num, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (counsel) Paper else Ink)
                                                Text(words, fontSize = 12.sp, color = if (counsel) Color(0xFFA3A19A) else Color(0xFF8A877F))
                                            }
                                        }
                                    }
                                }
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(if (counsel) Gold else Ink), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = if (counsel) Ink else Paper, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The design's rise: each card comes up a little after the one before. */
@Composable
private fun Rise(i: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(i * 100L); a.animateTo(1f, tween(600, easing = Ease)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 14.dp.toPx() }) { content() }
}

/**
 * Profile's Workspace card (REQ-010, 10a and 10b): the server's list with the status in its own word and note, Add Counsel
 * Workspace or Fix And Resubmit where the server says it can be added, Remove where it can be removed, and Switch Workspace
 * for a person who holds both. The server decides which of them show (can_add, can_remove, choose).
 */
@Composable
fun WorkspaceCard(onSwitch: () -> Unit, onAdd: () -> Unit, onRemoved: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repo = remember { WorkspaceRepository(context) }
    val ws by WorkspaceSession.workspaces.collectAsState()
    val mode by WorkspaceSession.mode.collectAsState()
    var confirm by remember { androidx.compose.runtime.mutableStateOf(false) }
    var busy by remember { androidx.compose.runtime.mutableStateOf(false) }
    var error by remember { androidx.compose.runtime.mutableStateOf("") }
    // Read afresh each time Profile opens: the firm may have verified, sent back or suspended the workspace since.
    LaunchedEffect(Unit) { repo.state() }
    val w = ws ?: return
    val counsel = w.counsel
    if (counsel == null && !w.choose) return
    val here = when (mode) { Mode.Counsel -> w.counsel?.label; Mode.Portal -> w.portal?.label; Mode.Unknown -> null }
    com.legumlex.client.ui.components.LexCard(title = "Workspaces") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val body = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            val ink = com.legumlex.client.ui.theme.Lex.colors.foreground
            val soft = com.legumlex.client.ui.theme.Lex.colors.mutedForeground
            if (here != null && w.choose) Text("You are in your $here workspace.", style = body, color = ink)
            if (counsel != null) {
                Text("Counsel: " + statusWord(counsel.status), style = body, color = ink)
                if (counsel.note.isNotBlank()) Text(counsel.note, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = soft)
            }
            if (error.isNotBlank()) Text(error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = com.legumlex.client.ui.theme.Lex.colors.danger.softText)
            if (w.choose) androidx.compose.material3.OutlinedButton(onClick = onSwitch, shape = RoundedCornerShape(8.dp)) { Text("Switch Workspace", color = ink) }
            if (counsel != null && counsel.canAdd) androidx.compose.material3.OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(8.dp)) {
                Text(if (counsel.status == "rejected") "Fix And Resubmit" else "Add Counsel Workspace", color = ink)
            }
            if (counsel != null && counsel.canRemove) androidx.compose.material3.TextButton(onClick = { confirm = true }, enabled = !busy) {
                Text("Remove Counsel Workspace", color = com.legumlex.client.ui.theme.Lex.colors.danger.softText)
            }
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove Counsel Workspace?") },
        text = { Text("Your Counsel workspace and its Daily Board are closed on this account. Your Client Portal stays as it is.") },
        confirmButton = {
            androidx.compose.material3.TextButton({
                confirm = false; busy = true; error = ""
                scope.launch {
                    repo.remove().onSuccess { (_, msg) -> busy = false; onRemoved(msg.ifBlank { "Your Counsel workspace has been removed." }) }
                        .onFailure { busy = false; error = it.message.orEmpty() }
                }
            }) { Text("Remove", color = com.legumlex.client.ui.theme.Lex.colors.danger.softText) }
        },
        dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("Cancel", color = com.legumlex.client.ui.theme.Lex.colors.foreground) } },
        containerColor = com.legumlex.client.ui.theme.Lex.colors.card,
    )
}

/** The server's status word, as a label: pending, rejected, active, suspended, removed. */
fun statusWord(status: String): String = status.replaceFirstChar { it.uppercase() }.ifBlank { "Not Added" }

/**
 * The paused screen, as the web's /board/paused: a counsel-only account whose workspace is paused while the person is engaged with
 * the firm. Sign Out is the only way on; the cases and credits are kept.
 */
@Composable
fun WorkspacePausedScreen(onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
            LegumMark(26.dp, fillColor = Paper)
            Spacer(Modifier.height(10.dp))
            Text("COUNSEL", fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.2.sp, color = Gold)
            Spacer(Modifier.height(30.dp))
            Text("Paused,", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp, color = Paper)
            Text("for now.", fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 36.sp, lineHeight = 40.sp, color = Paper)
            Spacer(Modifier.height(16.dp))
            Text(PAUSED_TEXT, fontSize = 15.sp, lineHeight = 23.sp, color = Color(0xFFA3A19A))
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Paper).clickable(onClick = onSignOut),
                contentAlignment = Alignment.Center,
            ) { Text("Sign Out", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink) }
        }
    }
}

const val PAUSED_TEXT = "Your Counsel workspace is paused while you are engaged with the firm. Your cases and credits are kept."
