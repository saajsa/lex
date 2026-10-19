package com.legumlex.client.feature.projects

import android.widget.Toast
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Task Detail.html" (Plot/Lexcore). The focused view of one project task,
// opened from a task row on Project Detail. Status first; the conversation is the
// body. A claret "Needs you" strip rises only when the task is blocked on the
// client (Awaiting Feedback). The checklist is read-only (the chamber works it);
// the one client action is commenting (gated). A completed task drops the callout
// and composer for a calm record. App-wide header + nav from MainActivity.
// -----------------------------------------------------------------------------

private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretSoft
private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretMid
private val ClaretBorderFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(taskId: String, canComment: Boolean = true, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as android.app.Application }
    val vm: TaskDetailViewModel = viewModel(
        key = "task-$taskId",
        factory = viewModelFactory { initializer { TaskDetailViewModel(app, taskId, canComment) } },
    )
    val state by vm.state.collectAsState()
    val posting by vm.posting.collectAsState()
    val actionError by vm.actionError.collectAsState()

    LaunchedEffect(actionError) { actionError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.clearActionError() } }

    Scaffold(containerColor = LlColor.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is TaskDetailUiState.Loading -> TaskSkeleton()
                is TaskDetailUiState.Error -> TaskError(s.message, vm::load)
                is TaskDetailUiState.Ready -> TaskReady(s.task, vm.canComment(s.task), posting, onPost = vm::postComment, onBack = onBack)
            }
        }
    }
}

@Composable
private fun TaskReady(task: TaskDetail, canComment: Boolean, posting: Boolean, onPost: (String) -> Unit, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    val complete = task.status == TaskStatus.Complete
    val awaiting = task.status == TaskStatus.AwaitingFeedback

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Masthead(task, today, complete, onBack)
                if (complete) CompletedBanner(task)
                if (awaiting) NeedsYou(canComment)
                KeyFacts(task)
                Checklist(task)
                task.description?.let {
                    Slabel("Description")
                    Card { Text(stripHtml(it) ?: it, fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 22.sp, color = LlColor.Ink, modifier = Modifier.padding(14.dp)) }
                }
                // Comments the firm hides are not shown at all, not shown as "none".
                if (task.canViewComments != false) Conversation(task, canComment)
                Spacer(Modifier.height(8.dp))
            }
        }
        when {
            canComment && !complete -> ComposerBar(posting, onPost)
            complete -> ClosedBar()
        }
    }
}

// =============================================================================
// Masthead
// =============================================================================

@Composable
private fun Masthead(task: TaskDetail, today: LocalDate, complete: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LlColor.Paper).border(0.dp, Color.Transparent).padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TASK IN", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
            Text(task.projectName ?: "Project", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.1.em, color = LlColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(task.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.018).em, lineHeight = 27.sp, color = LlColor.Ink)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatusPill(task.status.label, taskTone(task.status))
            priorityTag(task.priority)?.let { (label, tone) -> PriorityTag(label, tone) }
            val (dueLabel, soon, icon) = dueGlance(task, today, complete)
            if (dueLabel != null) {
                Text("·", fontFamily = Inter, fontSize = 11.sp, color = LlColor.BorderStrong)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(icon, null, tint = if (soon) ClaretMidFix else LlColor.InkFaint, modifier = Modifier.size(13.dp))
                    Text(dueLabel, fontFamily = Inter, fontSize = 10.5.sp, color = if (soon) ClaretMidFix else LlColor.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun CompletedBanner(task: TaskDetail) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft).border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(LlDimen.HairlineWidth, LlColor.VerdantBorder, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Verified, null, tint = LlColor.VerdantMid, modifier = Modifier.size(21.dp))
        }
        Column {
            Text("COMPLETED" + (task.dateFinished?.let { " · ${it.toLocalDate().format(dayYearFmt)}" } ?: ""), fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.14.em, color = LlColor.VerdantMid)
            Spacer(Modifier.height(4.dp))
            Text("This Task Is Done", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = LlColor.Verdant)
        }
    }
}

@Composable
private fun NeedsYou(canComment: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).clip(RoundedCornerShape(12.dp)).background(ClaretSoftFix).border(LlDimen.HairlineWidth, ClaretBorderFix, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(LlColor.Claret))
        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 15.dp, bottom = 15.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(LlDimen.HairlineWidth, ClaretBorderFix, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PriorityHigh, null, tint = LlColor.Claret, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PulseDot(ClaretMidFix, 6.dp)
                    Text("NEEDS YOU", fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.14.em, color = ClaretMidFix)
                }
                Spacer(Modifier.height(5.dp))
                Text("Your Input Is Needed", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.012).em, color = LlColor.Claret, lineHeight = 19.sp)
                Text(if (canComment) "Your advocate is waiting on your reply to move this task forward. Add a comment below."
                     else "Your advocate is waiting on your reply to move this task forward.", fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// =============================================================================
// Key facts
// =============================================================================

@Composable
private fun KeyFacts(task: TaskDetail) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Fact(Modifier.weight(1f), Icons.Outlined.Flag, "Milestone") {
            Text(task.milestoneName ?: "—", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = (-0.008).em, lineHeight = 17.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Fact(Modifier.weight(1f), Icons.Outlined.Group, "Assigned") {
            val who = task.assignees.firstOrNull()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.SlateSoft), contentAlignment = Alignment.Center) {
                    Text(initialsOf(who), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = LlColor.Slate)
                }
                Text(who ?: "Your chamber", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Fact(modifier: Modifier, icon: ImageVector, key: String, value: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
            Text(key.uppercase(), fontFamily = Inter, fontSize = 8.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
        }
        Spacer(Modifier.height(9.dp))
        value()
    }
}

// =============================================================================
// Checklist
// =============================================================================

@Composable
private fun Checklist(task: TaskDetail) {
    if (task.checklist.isEmpty()) return
    val total = task.checklist.size
    val done = task.checklist.count { it.finished }
    val meter = when (task.status) {
        TaskStatus.Complete -> LlColor.VerdantMid
        TaskStatus.AwaitingFeedback -> LlColor.AmberMid
        else -> LlColor.SlateMid
    }
    Slabel("Checklist")
    Card {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(meter))
            }
            Row { Text("$done", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink); Text(" of $total done", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Row(Modifier.padding(start = 15.dp, end = 15.dp, top = 11.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.Lock, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
            Text("WORKED BY YOUR CHAMBER · READ-ONLY", fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
        }
        task.checklist.sortedBy { it.order }.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(LlColor.Paper2))
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (c.finished) LlColor.VerdantMid else Color.Transparent)
                        .border(LlDimen.HairlineWidth, if (c.finished) LlColor.VerdantMid else LlColor.BorderStrong, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (c.finished) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Text(
                    c.description, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp,
                    color = if (c.finished) LlColor.InkFaint else LlColor.Ink,
                    textDecoration = if (c.finished) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

// =============================================================================
// Conversation + composer
// =============================================================================

@Composable
private fun Conversation(task: TaskDetail, canComment: Boolean) {
    Slabel("Conversation", count = task.comments.size.takeIf { it > 0 })
    if (task.comments.isEmpty()) {
        EmptySection(Icons.Outlined.Forum, "No Comments Yet",
            if (canComment) "Have a question about this task, or something to add for your chamber? Start the conversation below."
            else "Comments from your chamber on this task will appear here.")
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        task.comments.forEachIndexed { i, c ->
            Message(c)
            if (i < task.comments.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(26.dp).height(1.dp).background(LlColor.Paper2))
            Text("Comments Are Shared With Your Chamber", fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 0.06.em, color = LlColor.InkFaint)
            Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        }
    }
}

@Composable
private fun Message(c: TaskComment) {
    val you = c.authorType == "contact"
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (you) LlColor.ClaretSoft else LlColor.SlateSoft), contentAlignment = Alignment.Center) {
            Text(initialsOf(c.authorName, you), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (you) LlColor.Claret else LlColor.Slate)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.authorName?.takeIf { it.isNotBlank() } ?: if (you) "You" else "Staff", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = LlColor.Ink)
                Text(if (you) "YOU" else "ADVOCATE", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.06.em, color = if (you) ClaretMidFix else LlColor.SlateMid,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (you) ClaretSoftFix else LlColor.SlateSoft).padding(horizontal = 6.dp, vertical = 1.dp))
                Spacer(Modifier.weight(1f))
                c.dateAdded?.let { Text(it.toLocalDate().format(dayFmt), fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint) }
            }
            Text(stripHtml(c.content) ?: c.content, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerBar(posting: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Row(Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Write a comment…", fontFamily = Inter, fontSize = 13.sp, color = LlColor.InkFaint) }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(12.dp))
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (text.isBlank() || posting) LlColor.BorderStrong else LlColor.Ink).clickable(enabled = !posting && text.isNotBlank()) { onSend(text); text = "" },
                contentAlignment = Alignment.Center,
            ) {
                if (posting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LlColor.Paper)
                else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = LlColor.Paper, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ClosedBar() {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Row(Modifier.fillMaxWidth().background(LlColor.Paper1).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = LlColor.InkFaint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("This Task Is Complete — Comments Are Closed", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
        }
    }
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Slabel(name: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        count?.let {
            Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text("$it", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = LlColor.InkMuted)
            }
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card), content = content)
}

@Composable
private fun StatusPill(label: String, tone: Pair<Color, Color>) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tone.first).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(tone.second))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = tone.second)
    }
}

@Composable
private fun PriorityTag(label: String, tone: Pair<Color, Color>) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tone.first).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(tone.second))
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.08.em, color = tone.second)
    }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.BorderStrong, LlShape.Card).padding(vertical = 30.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 244.dp))
    }
}

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(initialValue = 1f, targetValue = 0.4f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1000), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(a).clip(RoundedCornerShape(50)).background(color))
}

@Composable
private fun TaskSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp)) {
            Sk(180.dp, 11.dp); Spacer(Modifier.height(12.dp)); Sk(modifier = Modifier.fillMaxWidth(0.8f), height = 20.dp, radius = 7.dp); Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sk(104.dp, 18.dp, 7.dp); Sk(62.dp, 18.dp, 6.dp) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(2) { Sk(modifier = Modifier.weight(1f), height = 70.dp, radius = 12.dp) }
        }
        Spacer(Modifier.height(20.dp))
        Sk(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), height = 150.dp, radius = 14.dp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            repeat(2) {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Sk(34.dp, 34.dp, 11.dp); Column(Modifier.weight(1f)) { Sk(modifier = Modifier.fillMaxWidth(0.4f), height = 12.dp); Spacer(Modifier.height(8.dp)); Sk(modifier = Modifier.fillMaxWidth(0.85f), height = 11.dp) }
                }
            }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun TaskError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load this task", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@androidx.compose.runtime.Composable private fun taskTone(s: TaskStatus): Pair<Color, Color> = when (s) {
    TaskStatus.Complete         -> LlColor.VerdantSoft to LlColor.Verdant
    TaskStatus.AwaitingFeedback -> LlColor.AmberSoft to LlColor.Amber
    TaskStatus.InProgress, TaskStatus.Testing -> LlColor.SlateSoft to LlColor.SlateMid
    TaskStatus.NotStarted, TaskStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
}

@androidx.compose.runtime.Composable private fun priorityTag(priority: Int?): Pair<String, Pair<Color, Color>>? = when (priority) {
    4 -> "Urgent" to (ClaretSoftFix to ClaretMidFix)
    3 -> "High" to (LlColor.AmberSoft to LlColor.Amber)
    2 -> "Medium" to (LlColor.SlateSoft to LlColor.SlateMid)
    1 -> "Low" to (LlColor.Paper2 to LlColor.InkMuted)
    else -> null
}

private fun dueGlance(task: TaskDetail, today: LocalDate, complete: Boolean): Triple<String?, Boolean, ImageVector> {
    if (complete) return Triple(task.dateFinished?.let { "Completed ${it.toLocalDate().format(dayFmt)}" }, false, Icons.Outlined.CheckCircle)
    val d = task.dueDate ?: return Triple(null, false, Icons.Outlined.Event)
    val days = ChronoUnit.DAYS.between(today, d)
    val label = when {
        days < 0 -> "Overdue · ${d.format(dayFmt)}"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due in $days days · ${d.format(dowFmt)}"
    }
    return Triple(label, days <= 3, Icons.Outlined.Event)
}

private fun initialsOf(name: String?, you: Boolean = false): String =
    name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { if (you) "Y" else "?" } ?: if (you) "Y" else "?"

private fun stripHtml(s: String?): String? {
    val t = s?.takeIf { it.isNotBlank() } ?: return null
    return t.replace(Regex("<[^>]+>"), "").replace("&#039;", "'").replace("&amp;", "&").replace("&quot;", "\"").replace("&middot;", "·").replace("&nbsp;", " ").trim().ifBlank { null }
}
