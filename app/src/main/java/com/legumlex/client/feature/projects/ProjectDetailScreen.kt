package com.legumlex.client.feature.projects

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Project Detail.html" (Plot/Lexcore). No tabs, single scroll: masthead
// → "At a glance" bento → Tasks (Open·Done) → "More" drill rows. The bento tiles
// and More rows push the dedicated Milestones / Files / Discussions / Activity
// routes; tasks open individually. Back + centred logo come from the app-wide
// header (MainActivity); this screen draws no top bar of its own.
// -----------------------------------------------------------------------------

private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretBorderFix = Color(0xFFF4D9DF)
private val AmberBorderFix = Color(0xFFF3E3C4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit = {},   // back is handled by the app-wide header; kept for nav compatibility
    onOpenTask: (String, Boolean) -> Unit = { _, _ -> },
    onOpenMilestones: (String) -> Unit = {},
    onOpenFiles: (String) -> Unit = {},
    onOpenDiscussions: (String) -> Unit = {},
    onOpenActivity: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ProjectDetailViewModel = viewModel(
        key = "project-$projectId",
        factory = viewModelFactory { initializer { ProjectDetailViewModel(app = app, projectId = projectId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val toast by vm.toast.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }

    Scaffold(
        containerColor = LlColor.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is ProjectDetailUiState.Loading -> ProjectDetailSkeleton()
                is ProjectDetailUiState.Error -> ProjectDetailError(s.message, vm::load)
                is ProjectDetailUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh) {
                    ProjectDetailReady(s, vm, onOpenTask, onOpenMilestones, onOpenFiles, onOpenDiscussions, onOpenActivity)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailReady(
    s: ProjectDetailUiState.Ready,
    vm: ProjectDetailViewModel,
    onOpenTask: (String, Boolean) -> Unit,
    onOpenMilestones: (String) -> Unit,
    onOpenFiles: (String) -> Unit,
    onOpenDiscussions: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val detail = s.detail
    val settings = detail.settings
    val pid = detail.summary.id
    val today = remember { LocalDate.now() }
    val actionInFlight by vm.actionInFlight.collectAsState()

    var seg by rememberSaveable(pid) { mutableStateOf("open") }
    var showSheet by remember { mutableStateOf(false) }
    val openTask = { id: String -> onOpenTask(id, settings.commentOnTasks) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Masthead(detail)
        AtAGlance(
            detail, s.discussions.size, today,
            onMilestones = { onOpenMilestones(pid) },
            onFiles = { onOpenFiles(pid) },
            onDiscussions = { onOpenDiscussions(pid) },
        )
        TasksSection(s.tasks, seg, { seg = it }, settings.createTasks, { showSheet = true }, openTask)
        MoreRows(
            detail, s,
            onOpenMilestones = { onOpenMilestones(pid) },
            onOpenFiles = { onOpenFiles(pid) },
            onOpenDiscussions = { onOpenDiscussions(pid) },
            onOpenActivity = { onOpenActivity(pid) },
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState, containerColor = LlColor.Paper) {
            CreateTaskSheet(
                projectRef = "PRJ-$pid", milestones = s.milestones, inFlight = actionInFlight,
                onCancel = { showSheet = false },
                onSubmit = { name, start, due, prio, ms, desc -> vm.createTask(name, start, due, prio, ms, desc, onDone = { showSheet = false }) },
            )
        }
    }
}

// =============================================================================
// Masthead (in-content heading; the app header sits above it)
// =============================================================================

@Composable
private fun Masthead(detail: ProjectDetail) {
    val s = detail.summary
    Column(Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("PROJECT", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.13.em, color = LlColor.InkFaint)
            Text("PRJ-${s.id}", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.13.em, color = ClaretMidFix)
        }
        Spacer(Modifier.height(9.dp))
        Text(s.name, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = (-0.02).em, lineHeight = 28.sp, color = LlColor.Ink)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusPill(s.status.label, statusTone(s.status))
            (s.nextMilestone?.dueDate ?: s.deadline)?.let {
                Text("Due ${it.format(dowFmt)}", fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint)
            }
        }
    }
}

// =============================================================================
// At a glance — bento
// =============================================================================

@Composable
private fun AtAGlance(detail: ProjectDetail, discussionCount: Int, today: LocalDate, onMilestones: () -> Unit, onFiles: () -> Unit, onDiscussions: () -> Unit) {
    val s = detail.summary
    val done = (s.tasksTotal - s.tasksOpen).coerceAtLeast(0)
    val pacing = s.nextMilestone?.dueDate ?: s.deadline
    val days = pacing?.let { if (it.isBefore(today)) null else ChronoUnit.DAYS.between(today, it) }

    Slabel("At a glance", first = true)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hero tile (informational — tasks sit just below; not a navigation target)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LlColor.Surface)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp)).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 15.dp),
        ) {
            Text("OVERALL PROGRESS", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("${s.progress}%", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-0.03).em, lineHeight = 38.sp, color = LlColor.Ink)
                Column(Modifier.weight(1f).padding(bottom = 3.dp)) {
                    Text(if (s.tasksTotal == 0) "No tasks yet" else "$done of ${s.tasksTotal} tasks complete", fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = LlColor.Ink)
                    Text("${s.tasksOpen} open", fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(s.status.meter()))
            }
            s.nextMilestone?.let { ms ->
                val soon = days != null && days <= 3
                val cdBg = if (soon) ClaretSoftFix else LlColor.AmberSoft
                val cdBorder = if (soon) ClaretBorderFix else AmberBorderFix
                val accent = if (soon) LlColor.Claret else LlColor.Amber
                val accentMid = if (soon) ClaretMidFix else LlColor.AmberMid
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Column(
                        Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(11.dp)).background(cdBg).border(LlDimen.HairlineWidth, cdBorder, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (days == null || days <= 0) "•" else "$days", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = accent)
                        Text(when (days) { null -> "DUE"; 1L -> "DAY"; else -> "DAYS" }, fontFamily = SourceSans, fontSize = 8.sp, letterSpacing = 0.12.em, color = accentMid, modifier = Modifier.padding(top = 3.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accentMid))
                            Text("NEAREST DEADLINE", fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.13.em, color = accentMid)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(ms.name, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ms.dueDate?.let {
                            val word = when (days) { null -> ""; 0L -> "Today · "; 1L -> "Tomorrow · "; else -> "In $days days · " }
                            Text(word + it.format(dowFmt), fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        // 2×2 metric tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(Modifier.weight(1f), Icons.Outlined.Checklist, "Open tasks", "${detail.counts.tasksOpen}", "of ${detail.counts.tasksTotal} total", false, onClick = null)
            MetricTileText(Modifier.weight(1f), Icons.Outlined.Flag, "Next milestone", s.nextMilestone?.name ?: "—", s.nextMilestone?.dueDate?.let { "Due ${it.format(dowFmt)}" }, true, onClick = onMilestones)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(Modifier.weight(1f), Icons.Outlined.Folder, "Files", "${detail.counts.filesTotal}", null, false, onClick = onFiles)
            MetricTile(Modifier.weight(1f), Icons.Outlined.Forum, "Discussions", "$discussionCount", null, false, onClick = onDiscussions)
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, icon: ImageVector, key: String, number: String, sub: String?, alert: Boolean, onClick: (() -> Unit)?) {
    val base = modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(14.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(14.dp))
    Box((if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp)) {
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        Column {
            TileKey(icon, key)
            Text(number, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 34.sp, color = LlColor.Ink, modifier = Modifier.padding(top = 14.dp))
            sub?.let { Text(it, fontFamily = SourceSans, fontSize = 10.sp, color = if (alert) ClaretMidFix else LlColor.InkFaint, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun MetricTileText(modifier: Modifier, icon: ImageVector, key: String, value: String, sub: String?, alert: Boolean, onClick: (() -> Unit)?) {
    val base = modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(14.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(14.dp))
    Box((if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp)) {
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        Column {
            TileKey(icon, key)
            Text(value, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 19.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 13.dp))
            sub?.let { Text(it, fontFamily = SourceSans, fontSize = 10.sp, color = if (alert) ClaretMidFix else LlColor.InkFaint, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun TileKey(icon: ImageVector, key: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(14.dp))
        Text(key.uppercase(), fontFamily = SourceSans, fontSize = 9.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
    }
}

// =============================================================================
// Tasks
// =============================================================================

@Composable
private fun TasksSection(tasks: List<TaskSummary>, seg: String, onSeg: (String) -> Unit, canCreate: Boolean, onCreate: () -> Unit, onOpenTask: (String) -> Unit) {
    val open = tasks.filter { it.status != TaskStatus.Complete }
    val done = tasks.filter { it.status == TaskStatus.Complete }
    val list = if (seg == "done") done else open

    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("TASKS", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        CountChip(tasks.size)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        if (canCreate) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(ClaretSoftFix).border(LlDimen.HairlineWidth, ClaretBorderFix, RoundedCornerShape(8.dp)).clickable(onClick = onCreate).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Add, null, tint = ClaretMidFix, modifier = Modifier.size(15.dp))
                Text("New task", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = ClaretMidFix)
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegBtn("Open", open.size, seg == "open", Modifier.weight(1f)) { onSeg("open") }
            SegBtn("Done", done.size, seg == "done", Modifier.weight(1f)) { onSeg("done") }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            EmptySection(Icons.Outlined.Checklist, if (seg == "done") "Nothing done yet" else "No open tasks", if (canCreate && seg == "open") "Add a task with New task, or your chamber will add tasks as the work moves." else "Tasks will appear here.")
        } else {
            Card {
                list.forEachIndexed { i, t ->
                    TaskRow(t) { onOpenTask(t.id) }
                    if (i < list.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                }
            }
        }
    }
}

@Composable
private fun SegBtn(label: String, count: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(8.dp)).background(if (on) LlColor.Ink else Color.Transparent).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) LlColor.Paper else LlColor.InkMuted)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontFamily = SourceSans, fontSize = 10.sp, color = if (on) LlColor.Paper.copy(alpha = 0.7f) else LlColor.InkFaint)
    }
}

@Composable
private fun TaskRow(t: TaskSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusPill(t.status.label, taskTone(t.status), small = true)
                priorityTag(t.priority)?.let { (label, tone) ->
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tone.first).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(tone.second))
                        Text(label.uppercase(), fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.08.em, color = tone.second)
                    }
                }
            }
            Text(t.name, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, color = if (t.status == TaskStatus.Complete) LlColor.InkMuted else LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
            val meta = buildList {
                if (t.checklistTotal > 0) add("${t.checklistDone}/${t.checklistTotal} ✓")
                if (t.commentsCount > 0) add("${t.commentsCount} comment${if (t.commentsCount == 1) "" else "s"}")
                t.milestoneName?.let { add(it) }
                t.dueDate?.let { add("due ${it.format(dayFmt)}") }
            }.joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint, modifier = Modifier.padding(top = 8.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.size(18.dp))
    }
}

// =============================================================================
// More — drill-down rows (open dedicated routes)
// =============================================================================

@Composable
private fun MoreRows(detail: ProjectDetail, s: ProjectDetailUiState.Ready, onOpenMilestones: () -> Unit, onOpenFiles: () -> Unit, onOpenDiscussions: () -> Unit, onOpenActivity: () -> Unit) {
    val settings = detail.settings
    Slabel("More")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (settings.viewMilestones) {
            val next = detail.summary.nextMilestone
            DrillRow(Icons.Outlined.Flag, "Milestones", detail.counts.milestonesTotal, false,
                next?.let { "Next · ${it.name}" + (it.dueDate?.let { d -> " · due ${d.format(dowFmt)}" } ?: "") } ?: "No upcoming milestone", onOpenMilestones)
        }
        if (settings.viewFiles) {
            DrillRow(Icons.Outlined.Folder, "Files", detail.counts.filesTotal, false,
                s.files.firstOrNull()?.let { "Latest · ${it.originalFileName ?: it.fileName}" } ?: "No files yet", onOpenFiles)
        }
        if (settings.viewDiscussions) {
            DrillRow(Icons.Outlined.Forum, "Discussions", s.discussions.size, s.discussions.isNotEmpty(),
                s.discussions.firstOrNull()?.let { "Latest · ${it.subject}" + (it.authorName?.let { a -> " · $a" } ?: "") } ?: "No discussions yet", onOpenDiscussions)
        }
        if (settings.viewActivityLog) {
            DrillRow(Icons.Outlined.History, "Activity", null, false,
                s.activity.firstOrNull()?.let { (it.actorName?.let { a -> "$a · " } ?: "") + humanizeKey(it.descriptionKey) } ?: "No activity yet", onOpenActivity)
        }
    }
}

@Composable
private fun DrillRow(icon: ImageVector, label: String, count: Int?, unread: Boolean, preview: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (unread) ClaretSoftFix else LlColor.Paper1).border(LlDimen.HairlineWidth, if (unread) ClaretBorderFix else LlColor.Border, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (unread) ClaretMidFix else LlColor.InkMuted, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, letterSpacing = (-0.01).em, color = LlColor.Ink)
                count?.takeIf { it > 0 }?.let {
                    Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(if (unread) ClaretMidFix else LlColor.Paper2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                        Text("$it", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = if (unread) Color.White else LlColor.InkMuted)
                    }
                }
            }
            Text(preview, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.size(19.dp))
    }
}

// =============================================================================
// Create-task sheet
// =============================================================================

@Composable
private fun CreateTaskSheet(
    projectRef: String, milestones: List<Milestone>, inFlight: Boolean,
    onCancel: () -> Unit,
    onSubmit: (name: String, startDate: String, dueDate: String?, priority: Int, milestone: Int, description: String?) -> Unit,
) {
    val context = LocalContext.current
    val iso = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(2) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    val start = remember { LocalDate.now() }

    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
        Text("$projectRef · NEW", fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 0.14.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(5.dp))
        Text("New task", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink)

        FieldLabel("Task name")
        OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("e.g. Send revised logo files") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp))

        FieldLabel("Details (optional)")
        OutlinedTextField(value = desc, onValueChange = { desc = it }, placeholder = { Text("Add any context for your chamber…") }, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp))

        FieldLabel("Priority")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ProjectPriority.ALL.forEach { p ->
                val on = priority == p.code
                Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) LlColor.Ink else LlColor.Surface).border(LlDimen.HairlineWidth, if (on) LlColor.Ink else LlColor.Border, RoundedCornerShape(10.dp)).clickable { priority = p.code }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(priorityDot(p.code)))
                    Spacer(Modifier.width(5.dp))
                    Text(p.label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = if (on) LlColor.Paper else LlColor.InkMuted)
                }
            }
        }

        FieldLabel("Due date (optional)")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp))
                .clickable { val b = due ?: LocalDate.now(); android.app.DatePickerDialog(context, { _, y, mo, d -> due = LocalDate.of(y, mo + 1, d) }, b.year, b.monthValue - 1, b.dayOfMonth).show() }
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Outlined.CalendarToday, null, tint = LlColor.InkFaint, modifier = Modifier.size(17.dp))
            Text(due?.let { it.format(dayYearFmt) } ?: "Choose a date", fontFamily = SourceSans, fontSize = 13.5.sp, color = if (due == null) LlColor.InkFaint else LlColor.Ink)
        }

        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).clickable(onClick = onCancel).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.InkMuted)
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (name.isBlank() || inFlight) LlColor.BorderStrong else LlColor.Claret)
                    .clickable(enabled = name.isNotBlank() && !inFlight) { onSubmit(name.trim(), start.format(iso), due?.format(iso), priority, 0, desc.trim().ifBlank { null }) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (inFlight) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text("Create task", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint, modifier = Modifier.padding(top = 15.dp, bottom = 7.dp))
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Slabel(name: String, first: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (first) 16.dp else 20.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun CountChip(n: Int) {
    Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text("$n", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card), content = content)
}

@Composable
private fun StatusPill(label: String, tone: Pair<Color, Color>, small: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(7.dp)).background(tone.first).padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 2.dp else 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(if (small) 5.dp else 6.dp).clip(RoundedCornerShape(50)).background(tone.second))
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = if (small) 9.sp else 10.sp, letterSpacing = 0.02.em, color = tone.second)
    }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.BorderStrong, LlShape.Card).padding(vertical = 32.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(13.dp))
        Text(title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 248.dp))
    }
}

@Composable
private fun ProjectDetailSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp)) {
            Sk(72.dp, 13.dp); Spacer(Modifier.height(13.dp)); Sk(modifier = Modifier.fillMaxWidth(0.78f), height = 22.dp, radius = 7.dp); Spacer(Modifier.height(13.dp)); Sk(120.dp, 16.dp, 7.dp)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Sk(86.dp, 11.dp); Spacer(Modifier.height(12.dp))
            Sk(modifier = Modifier.fillMaxWidth(), height = 150.dp, radius = 16.dp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Sk(modifier = Modifier.weight(1f), height = 104.dp, radius = 14.dp); Sk(modifier = Modifier.weight(1f), height = 104.dp, radius = 14.dp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Sk(modifier = Modifier.weight(1f), height = 104.dp, radius = 14.dp); Sk(modifier = Modifier.weight(1f), height = 104.dp, radius = 14.dp) }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun ProjectDetailError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load this project", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun statusTone(s: ProjectStatus): Pair<Color, Color> = when (s) {
    ProjectStatus.InProgress -> LlColor.SlateSoft to LlColor.SlateMid
    ProjectStatus.OnHold     -> LlColor.AmberSoft to LlColor.Amber
    ProjectStatus.Finished   -> LlColor.VerdantSoft to LlColor.Verdant
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
}

private fun taskTone(s: TaskStatus): Pair<Color, Color> = when (s) {
    TaskStatus.Complete         -> LlColor.VerdantSoft to LlColor.Verdant
    TaskStatus.AwaitingFeedback -> LlColor.AmberSoft to LlColor.Amber
    TaskStatus.InProgress, TaskStatus.Testing -> LlColor.SlateSoft to LlColor.SlateMid
    TaskStatus.NotStarted, TaskStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
}

private fun ProjectStatus.meter(): Color = when (this) {
    ProjectStatus.InProgress -> LlColor.SlateMid
    ProjectStatus.OnHold     -> LlColor.AmberMid
    ProjectStatus.Finished   -> LlColor.VerdantMid
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.InkFaint
}

private fun priorityTag(priority: Int?): Pair<String, Pair<Color, Color>>? = when (priority) {
    4 -> "Urgent" to (ClaretSoftFix to ClaretMidFix)
    3 -> "High" to (LlColor.AmberSoft to LlColor.Amber)
    2 -> "Medium" to (LlColor.SlateSoft to LlColor.SlateMid)
    else -> null
}

private fun priorityDot(code: Int): Color = when (code) {
    4 -> ClaretMidFix; 3 -> LlColor.AmberMid; 2 -> LlColor.SlateMid; else -> LlColor.InkFaint
}

private fun humanizeKey(key: String): String =
    key.removePrefix("project_activity_").removePrefix("not_").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Activity" }
