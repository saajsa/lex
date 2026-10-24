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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.Lex
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

private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.Paper1
private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.Ink   // was the claret accent; red is kept for urgency
private val ClaretBorderFix: Color @androidx.compose.runtime.Composable get() = LlColor.Border
private val AmberBorderFix: Color @androidx.compose.runtime.Composable get() = LlColor.AmberBorder

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
        Masthead(detail, today)
        if (s.failed.isNotEmpty()) FailedSections(s.failed, actionInFlight, vm::refresh)
        AtAGlance(
            detail, s.discussions.size, today,
            onMilestones = { onOpenMilestones(pid) },
            onFiles = { onOpenFiles(pid) },
            onDiscussions = { onOpenDiscussions(pid) },
        )
        if (settings.viewTasks) TasksSection(s.tasks, seg, { seg = it }, settings.createTasks, { showSheet = true }, openTask)
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
        // The app's wizard, full screen over the project (owner, 2026-10-24).
        com.legumlex.client.ui.components.WizardDialog(onDismiss = { showSheet = false }) {
            CreateTaskSheet(
                projectName = detail.summary.name, milestones = s.milestones, inFlight = actionInFlight,
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
private fun Masthead(detail: ProjectDetail, today: LocalDate) {
    val s = detail.summary
    Column(Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("PROJECT", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.13.em, color = LlColor.InkFaint)
        }
        Spacer(Modifier.height(9.dp))
        Text(s.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = (-0.02).em, lineHeight = 28.sp, color = LlColor.Ink)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusPill(s.status.label, statusTone(s.status))
            val milestoneDue = s.nextMilestone?.dueDate
            (milestoneDue ?: s.deadline)?.let {
                // A date already behind us is said to have passed, not merely coloured.
                val passed = it.isBefore(today) && s.status != ProjectStatus.Finished && s.status != ProjectStatus.Cancelled
                val what = if (milestoneDue != null) "Milestone" else "Deadline"
                Text(
                    if (passed) "$what Passed · ${it.format(dowFmt)}" else "Due ${it.format(dowFmt)}",
                    fontFamily = Inter, fontWeight = if (passed) FontWeight.SemiBold else FontWeight.Normal, fontSize = 10.5.sp,
                    color = if (passed) LlColor.Claret else LlColor.InkFaint,
                )
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
    val settings = detail.settings
    val days = s.nextMilestone?.dueDate?.let { ChronoUnit.DAYS.between(today, it) }   // negative once passed

    Slabel("At a Glance", first = true)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hero tile (informational — tasks sit just below; not a navigation target)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Surface)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 15.dp),
        ) {
            Text("OVERALL PROGRESS", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("${s.progress}%", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-0.03).em, lineHeight = 38.sp, color = LlColor.Ink)
                Column(Modifier.weight(1f).padding(bottom = 3.dp)) {
                    Text(if (s.tasksTotal == 0) "No tasks yet" else "$done of ${s.tasksTotal} tasks complete", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = LlColor.Ink)
                    Text("${s.tasksOpen} open", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(s.status.meter()))
            }
            s.nextMilestone?.let { ms ->
                val passed = days != null && days < 0
                val soon = days != null && days <= 3
                val cdBg = if (soon) LlColor.ClaretSoft else LlColor.AmberSoft
                val cdBorder = if (soon) LlColor.ClaretBorder else AmberBorderFix
                val accent = if (soon) LlColor.Claret else LlColor.Amber
                val accentMid = if (soon) LlColor.ClaretMid else LlColor.AmberMid
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Column(
                        Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(cdBg).border(LlDimen.HairlineWidth, cdBorder, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (days == null || days == 0L) "•" else "${kotlin.math.abs(days)}", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = accent)
                        Text(if (days == null) "DUE" else when { days == 0L -> "TODAY"; days == -1L -> "DAY LATE"; days < 0L -> "DAYS LATE"; days == 1L -> "DAY"; else -> "DAYS" }, fontFamily = Inter, fontSize = 8.sp, letterSpacing = 0.12.em, color = accentMid, modifier = Modifier.padding(top = 3.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accentMid))
                            Text(if (passed) "MILESTONE PASSED" else "NEXT MILESTONE", fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.13.em, color = accentMid)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(ms.name, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ms.dueDate?.let {
                            val word = if (days == null) "" else when {
                                days < -1L -> "Passed ${-days} days ago · "
                                days == -1L -> "Passed yesterday · "
                                days == 0L -> "Today · "
                                days == 1L -> "Tomorrow · "
                                else -> "In $days days · "
                            }
                            Text(word + it.format(dowFmt), fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        // Metric tiles, two to a row — only for the parts this project shows its client.
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            if (settings.viewTasks) add { m -> MetricTile(m, Icons.Outlined.Checklist, "Open Tasks", "${detail.counts.tasksOpen}", "of ${detail.counts.tasksTotal} total", false, onClick = null) }
            if (settings.viewMilestones) add { m ->
                val next = s.nextMilestone
                val late = next?.dueDate?.isBefore(today) == true
                MetricTileText(m, Icons.Outlined.Flag, "Next Milestone", next?.name ?: "—", next?.dueDate?.let { if (late) "Passed · ${it.format(dowFmt)}" else "Due ${it.format(dowFmt)}" }, late, onClick = onMilestones)
            }
            if (settings.viewFiles) add { m -> MetricTile(m, Icons.Outlined.Folder, "Files", "${detail.counts.filesTotal}", null, false, onClick = onFiles) }
            if (settings.viewDiscussions) add { m -> MetricTile(m, Icons.Outlined.Forum, "Discussions", "$discussionCount", null, false, onClick = onDiscussions) }
        }
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, icon: ImageVector, key: String, number: String, sub: String?, alert: Boolean, onClick: (() -> Unit)?) {
    val base = modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))
    Box((if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp)) {
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        Column {
            TileKey(icon, key)
            Text(number, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 34.sp, color = LlColor.Ink, modifier = Modifier.padding(top = 14.dp))
            sub?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, color = if (alert) ClaretMidFix else LlColor.InkFaint, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun MetricTileText(modifier: Modifier, icon: ImageVector, key: String, value: String, sub: String?, alert: Boolean, onClick: (() -> Unit)?) {
    val base = modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))
    Box((if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp)) {
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
        Column {
            TileKey(icon, key)
            Text(value, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 19.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 13.dp))
            sub?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, color = if (alert) ClaretMidFix else LlColor.InkFaint, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun TileKey(icon: ImageVector, key: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(14.dp))
        Text(key.uppercase(), fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint)
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
        Text("TASKS", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        CountChip(tasks.size)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
        if (canCreate) {
            // An ordinary action: ink outline, never the danger red.
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp)).clickable(onClick = onCreate).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Add, null, tint = LlColor.Ink, modifier = Modifier.size(15.dp))
                Text("New Task", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegBtn("Open", open.size, seg == "open", Modifier.weight(1f)) { onSeg("open") }
            SegBtn("Done", done.size, seg == "done", Modifier.weight(1f)) { onSeg("done") }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            EmptySection(Icons.Outlined.Checklist, if (seg == "done") "Nothing Done Yet" else "No Open Tasks", if (canCreate && seg == "open") "Add a task with New Task, or your chamber will add tasks as the work moves." else "Tasks will appear here.")
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
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (on) LlColor.Paper else LlColor.InkMuted)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontFamily = Inter, fontSize = 10.sp, color = if (on) LlColor.Paper.copy(alpha = 0.7f) else LlColor.InkFaint)
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
                        Text(label.lowercase().replaceFirstChar { it.uppercase() }, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = tone.second)
                    }
                }
            }
            Text(t.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, color = if (t.status == TaskStatus.Complete) LlColor.InkMuted else LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
            val meta = buildList {
                if (t.checklistTotal > 0) add("${t.checklistDone}/${t.checklistTotal} ✓")
                if (t.commentsCount > 0) add("${t.commentsCount} comment${if (t.commentsCount == 1) "" else "s"}")
                t.milestoneName?.let { add(it) }
                // A due date gone by says so, as the project card does.
                t.dueDate?.let { add(if (it.isBefore(java.time.LocalDate.now()) && t.status != TaskStatus.Complete) "Due date passed ${it.format(dayFmt)}" else "Due ${it.format(dayFmt)}") }
            }.joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, modifier = Modifier.padding(top = 8.dp))
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
                next?.let { "Next · ${it.name}" + (it.dueDate?.let { d -> if (d.isBefore(java.time.LocalDate.now())) " · Passed ${d.format(dowFmt)}" else " · Due ${d.format(dowFmt)}" } ?: "") } ?: "No upcoming milestone", onOpenMilestones)
        }
        if (settings.viewFiles) {
            DrillRow(Icons.Outlined.Folder, "Files", detail.counts.filesTotal, false,
                s.files.firstOrNull()?.let { "Latest · ${it.originalFileName ?: it.fileName}" } ?: "No files yet", onOpenFiles)
        }
        if (settings.viewDiscussions) {
            // Nothing records what the client has read here: no "unread" accent.
            DrillRow(Icons.Outlined.Forum, "Discussions", s.discussions.size, false,
                s.discussions.firstOrNull()?.let { "Latest · ${it.subject}" + (it.authorName?.let { a -> " · $a" } ?: "") } ?: "No discussions yet", onOpenDiscussions)
        }
        if (settings.viewActivityLog) {
            DrillRow(Icons.Outlined.History, "Activity", null, false,
                s.activity.firstOrNull()?.let { (it.actorName?.let { a -> "$a · " } ?: "") + humanizeKey(it.descriptionKey) + (it.additionalData?.let(::plainActivityData)?.takeIf { d -> d.isNotBlank() }?.let { d -> " · $d" } ?: "") } ?: "No activity yet", onOpenActivity)
        }
    }
}

@Composable
private fun DrillRow(icon: ImageVector, label: String, count: Int?, unread: Boolean, preview: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (unread) ClaretSoftFix else LlColor.Paper1).border(LlDimen.HairlineWidth, if (unread) ClaretBorderFix else LlColor.Border, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (unread) ClaretMidFix else LlColor.InkMuted, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, letterSpacing = (-0.01).em, color = LlColor.Ink)
                count?.takeIf { it > 0 }?.let {
                    Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(if (unread) ClaretSoftFix else LlColor.Paper2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                        Text("$it", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = if (unread) LlColor.Claret else LlColor.InkMuted)
                    }
                }
            }
            Text(preview, fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.size(19.dp))
    }
}

// =============================================================================
// Create-task sheet
// =============================================================================

@Composable
private fun CreateTaskSheet(
    projectName: String, milestones: List<Milestone>, inFlight: Boolean,
    onCancel: () -> Unit,
    onSubmit: (name: String, startDate: String, dueDate: String?, priority: Int, milestone: Int, description: String?) -> Unit,
) {
    // A four-step wizard: Details → Priority & Milestone → Dates → Review.
    val context = LocalContext.current
    val iso = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(2) }
    var milestoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    val steps = listOf("Details", "Priority", "Dates", "Review")
    val milestone = milestones.firstOrNull { it.id == milestoneId }
    val datesOk = due == null || !due!!.isBefore(start)
    val canNext = when (step) { 0 -> name.isNotBlank(); 2 -> datesOk; else -> true }

    fun pick(initial: LocalDate, min: LocalDate?, onPick: (LocalDate) -> Unit) {
        val dlg = android.app.DatePickerDialog(context, { _, y, mo, d -> onPick(LocalDate.of(y, mo + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        min?.let { dlg.datePicker.minDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
        dlg.show()
    }

    com.legumlex.client.ui.components.WizardFrame(
        title = "New Task",
        steps = steps,
        step = step,
        ready = canNext,
        saving = inFlight,
        saveLabel = "Create Task",
        savingLabel = "Creating…",
        error = null,
        onClose = onCancel,
        onStep = { step = it },
        onSave = { onSubmit(name.trim(), start.format(iso), due?.format(iso), priority, milestone?.id?.let { jInt(it) } ?: 0, desc.trim().ifBlank { null }) },
    ) {
        when (step) {
            0 -> {
                com.legumlex.client.ui.components.WizardHeading("What is the task?", "For ${projectName}. Your chamber sees it on the project.")
                com.legumlex.client.ui.components.WizardTextField("Task Name", name, { name = it }, placeholder = "e.g. Send revised logo files")
                Spacer(Modifier.height(16.dp))
                com.legumlex.client.ui.components.WizardLabel("Details (Optional)")
                com.legumlex.client.ui.components.WizardTextArea(desc, { desc = it }, "Add any context for your chamber…")
            }
            1 -> {
                com.legumlex.client.ui.components.WizardHeading("How urgent is it?", "Its priority, and the milestone it belongs to, if any.")
                com.legumlex.client.ui.components.WizardLabel("Priority")
                ProjectPriority.ALL.forEach { p ->
                    com.legumlex.client.ui.components.WizardChoice(p.label, priority == p.code) { priority = p.code }
                }
                Spacer(Modifier.height(12.dp))
                com.legumlex.client.ui.components.WizardLabel("Milestone (Optional)")
                if (milestones.isEmpty()) {
                    Text("This project has no milestones, so the task will stand on its own.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = LlColor.InkMuted)
                } else {
                    (listOf<Milestone?>(null) + milestones).forEach { m ->
                        com.legumlex.client.ui.components.WizardChoice(
                            m?.name ?: "No Milestone", milestoneId == m?.id,
                            sub = m?.dueDate?.let { "Due ${it.format(dayYearFmt)}" } ?: "",
                        ) { milestoneId = m?.id }
                    }
                }
            }
            2 -> {
                com.legumlex.client.ui.components.WizardHeading("When?", "The day it starts, and the day it is due, if there is one.")
                com.legumlex.client.ui.components.WizardLabel("Start Date")
                DateField(start.format(dayYearFmt), false) { pick(start, null) { start = it } }
                Spacer(Modifier.height(16.dp))
                com.legumlex.client.ui.components.WizardLabel("Due Date (Optional)")
                DateField(due?.format(dayYearFmt) ?: "Choose a date", due == null) { pick(due ?: start, start) { due = it } }
                if (due != null) Text("Clear the due date", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 10.dp).clickable { due = null })
                if (!datesOk) Text("The due date cannot be earlier than the start date.", fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.Claret, modifier = Modifier.padding(top = 10.dp))
                milestone?.dueDate?.let { md ->
                    if (due?.isAfter(md) == true) Text("This is after the milestone's own date, ${md.format(dayYearFmt)}.", fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.Amber, modifier = Modifier.padding(top = 10.dp))
                }
            }
            else -> com.legumlex.client.ui.components.WizardReview(
                title = "Check and create",
                note = "The task is added to ${projectName}.",
                rows = listOfNotNull(
                    "Task" to name.trim(),
                    desc.trim().takeIf { it.isNotEmpty() }?.let { "Details" to it },
                    "Priority" to (ProjectPriority.ALL.firstOrNull { it.code == priority }?.label ?: "—"),
                    "Milestone" to (milestone?.name ?: "None"),
                    "Starts" to start.format(dayYearFmt),
                    "Due" to (due?.format(dayYearFmt) ?: "No due date"),
                ),
            )
        }
    }
}

@Composable
private fun DateField(text: String, placeholder: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.CalendarToday, null, tint = LlColor.InkFaint, modifier = Modifier.size(17.dp))
        Text(text, fontFamily = Inter, fontSize = 13.5.sp, color = if (placeholder) LlColor.InkFaint else LlColor.Ink)
    }
}

@Composable
private fun FailedSections(failed: Set<ProjectSection>, busy: Boolean, onRetry: () -> Unit) {
    val names = failed.joinToString(", ") { it.name.lowercase() }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(LlColor.AmberSoft)
            .border(LlDimen.HairlineWidth, AmberBorderFix, RoundedCornerShape(10.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = LlColor.Amber, modifier = Modifier.size(18.dp))
        Text("This project's $names could not be loaded. The information below may be incomplete.", fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.Amber, modifier = Modifier.weight(1f))
        Text("Retry", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !busy, onClick = onRetry).padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Slabel(name: String, first: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (first) 16.dp else 20.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun CountChip(n: Int) {
    Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text("$n", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card), content = content)
}

@Composable
private fun StatusPill(label: String, tone: Pair<Color, Color>, small: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tone.first).padding(horizontal = if (small) 7.dp else 9.dp, vertical = if (small) 2.dp else 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(if (small) 5.dp else 6.dp).clip(RoundedCornerShape(50)).background(tone.second))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = if (small) 9.sp else 10.sp, letterSpacing = 0.02.em, color = tone.second)
    }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.BorderStrong, LlShape.Card).padding(vertical = 32.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LlColor.InkFaint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(13.dp))
        Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 248.dp))
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
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Project Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("Try Again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@androidx.compose.runtime.Composable private fun statusTone(s: ProjectStatus): Pair<Color, Color> = when (s) {
    ProjectStatus.InProgress -> LlColor.SlateSoft to LlColor.SlateMid
    ProjectStatus.OnHold     -> LlColor.AmberSoft to LlColor.Amber
    ProjectStatus.Finished   -> LlColor.VerdantSoft to LlColor.Verdant
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
}

@androidx.compose.runtime.Composable private fun taskTone(s: TaskStatus): Pair<Color, Color> = when (s) {
    TaskStatus.Complete         -> LlColor.VerdantSoft to LlColor.Verdant
    TaskStatus.AwaitingFeedback -> LlColor.AmberSoft to LlColor.Amber
    TaskStatus.InProgress, TaskStatus.Testing -> LlColor.SlateSoft to LlColor.SlateMid
    TaskStatus.NotStarted, TaskStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
}

@androidx.compose.runtime.Composable private fun ProjectStatus.meter(): Color = when (this) {
    ProjectStatus.InProgress -> LlColor.SlateMid
    ProjectStatus.OnHold     -> LlColor.AmberMid
    ProjectStatus.Finished   -> LlColor.VerdantMid
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.InkFaint
}

@androidx.compose.runtime.Composable private fun priorityTag(priority: Int?): Pair<String, Pair<Color, Color>>? = when (priority) {
    4 -> "Urgent" to (LlColor.ClaretSoft to LlColor.ClaretMid)
    3 -> "High" to (LlColor.AmberSoft to LlColor.Amber)
    2 -> "Medium" to (LlColor.SlateSoft to LlColor.SlateMid)
    else -> null
}

@androidx.compose.runtime.Composable private fun priorityDot(code: Int): Color = when (code) {
    4 -> LlColor.ClaretMid; 3 -> LlColor.AmberMid; 2 -> LlColor.SlateMid; else -> LlColor.InkFaint
}

private fun humanizeKey(key: String): String =
    key.removePrefix("project_activity_").removePrefix("not_").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Activity" }
