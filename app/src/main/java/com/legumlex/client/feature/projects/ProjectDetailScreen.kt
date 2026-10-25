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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.MaterialTheme
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
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// A project, most important first (owner, 2026-10-25: no repetition, little
// scrolling). A fixed head says what it is and how far along; the tabs below
// hold the rest, each scrolling on its own: Stages (where it stands, what the
// firm needs from you, the stages), Tasks, Files, Discussions. The ⓘ holds the
// rest: the description, the dates, the team and the activity. Back + centred
// logo come from the app-wide header (MainActivity).
// -----------------------------------------------------------------------------

private val AmberBorderFix: Color @androidx.compose.runtime.Composable get() = Lex.colors.border

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
    onOpenThread: (String) -> Unit = {},
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
        containerColor = Lex.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is ProjectDetailUiState.Loading -> ProjectDetailSkeleton()
                is ProjectDetailUiState.Error -> ProjectDetailError(s.message, vm::load)
                is ProjectDetailUiState.Ready ->
                    ProjectDetailReady(s, vm, isRefreshing, onOpenTask, onOpenActivity, onOpenThread)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailReady(
    s: ProjectDetailUiState.Ready,
    vm: ProjectDetailViewModel,
    isRefreshing: Boolean,
    onOpenTask: (String, Boolean) -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val detail = s.detail
    val settings = detail.settings
    val pid = detail.summary.id
    val st = detail.summary.stages
    val today = remember { LocalDate.now() }
    val actionInFlight by vm.actionInFlight.collectAsState()

    var seg by rememberSaveable(pid) { mutableStateOf("open") }
    var showSheet by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val openTask = { id: String -> onOpenTask(id, settings.commentOnTasks) }

    // Only what the firm shares with the client (the project's settings).
    val tabs = buildList {
        if (st != null) add("stages" to "Stages") else if (settings.viewMilestones) add("stages" to "Milestones")
        if (settings.viewTasks) add("tasks" to "Tasks")
        if (settings.viewFiles) add("files" to "Files")
        if (settings.viewDiscussions) add("talk" to "Discussions")
    }
    val badges = mapOf("tasks" to detail.counts.tasksOpen, "files" to detail.counts.filesTotal, "talk" to s.discussions.size)
    var tab by rememberSaveable(pid) { mutableStateOf(tabs.firstOrNull()?.first.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        Head(detail, today, onInfo = { info = true })
        if (s.failed.isNotEmpty()) FailedSections(s.failed, actionInFlight, vm::refresh)
        if (tabs.size > 1) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)) {
                Segments(tabs, tab, badges) { tab = it }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "stages" -> if (st != null) Scrolled(isRefreshing, vm::refresh) { StageTracker(st, detail.needs, openTask) }
                            else ProjectMilestonesScreen(pid, onBack = {}, embedded = true)
                "tasks" -> Scrolled(isRefreshing, vm::refresh) {
                    TasksSection(s.tasks, seg, { seg = it }, settings.createTasks, { showSheet = true }, openTask)
                }
                "files" -> ProjectFilesScreen(pid, onBack = {}, embedded = true)
                "talk" -> ProjectDiscussionsScreen(pid, onOpenThread = onOpenThread, embedded = true)
                else -> Scrolled(isRefreshing, vm::refresh) {
                    EmptySection(Icons.Outlined.WorkOutline, "Nothing More To Show", "The firm shares the progress of this project with you here as the work moves.")
                }
            }
        }
    }

    if (info) {
        InfoDialog(
            detail,
            onActivity = if (settings.viewActivityLog) { { info = false; onOpenActivity(pid) } } else null,
            onClose = { info = false },
        )
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
// The head, the tabs and the ⓘ
// =============================================================================

/**
 * What the project is and how far along: the kind of work, the name, the
 * progress and its status and date. Fixed above the tabs; the ⓘ opens the rest.
 */
@Composable
private fun Head(detail: ProjectDetail, today: LocalDate, onInfo: () -> Unit) {
    val s = detail.summary
    Column(Modifier.fillMaxWidth().background(Lex.colors.background).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                com.legumlex.client.ui.components.Eyebrow(s.stages?.typeName ?: "Project")
                Spacer(Modifier.height(6.dp))
                Text(s.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.02).em, lineHeight = 26.sp, color = Lex.colors.foreground)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Lex.colors.border, RoundedCornerShape(8.dp)).clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Info, "This Project", tint = Lex.colors.foreground, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lex.colors.muted)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0, 100) / 100f).fillMaxHeight().background(s.status.meter()))
            }
            Spacer(Modifier.width(10.dp))
            Text("${s.progress}%", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Lex.colors.foreground)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusPill(s.status.label, statusTone(s.status))
            // A finished project says when it finished, not when it was due.
            val finished = detail.dateFinished?.toLocalDate()?.takeIf { s.status == ProjectStatus.Finished }
            if (finished != null) {
                Text("Completed ${finished.format(dowFmt)}", fontFamily = Inter, fontSize = 11.5.sp, color = Lex.colors.mutedForeground)
            }
            val milestoneDue = s.nextMilestone?.dueDate.takeIf { s.stages == null }
            if (finished == null) (milestoneDue ?: s.deadline)?.let {
                // A date already behind us is said to have passed, not merely coloured.
                val passed = it.isBefore(today) && s.status != ProjectStatus.Finished && s.status != ProjectStatus.Cancelled
                val what = if (milestoneDue != null) "Milestone" else "Deadline"
                Text(
                    if (passed) "$what Passed · ${it.format(dowFmt)}" else "Due ${it.format(dowFmt)}",
                    fontFamily = Inter, fontWeight = if (passed) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.5.sp,
                    color = if (passed) Lex.colors.danger.softText else Lex.colors.mutedForeground,
                )
            }
        }
    }
}

/** The tabs, as the case page draws them: one bordered row of equal segments. */
@Composable
private fun Segments(items: List<Pair<String, String>>, selected: String, badges: Map<String, Int>, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(MaterialTheme.shapes.medium)
            .border(1.dp, Lex.colors.input, MaterialTheme.shapes.medium),
    ) {
        items.forEachIndexed { i, (key, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.input))
            val on = key == selected
            Row(
                Modifier.weight(1f).background(if (on) Lex.colors.muted else Lex.colors.card)
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (on) Lex.colors.foreground else Lex.colors.secondaryForeground, maxLines = 1)
                val n = badges[key] ?: 0
                if (n > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("$n", style = MaterialTheme.typography.labelSmall, color = Lex.colors.mutedForeground)
                }
            }
        }
    }
}

/** A tab's own scroll, pulled down to refresh the project. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrolled(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), content = content)
    }
}

/** The ⓘ: what the head has no room for: the description, the dates, the team, the activity. */
@Composable
private fun InfoDialog(detail: ProjectDetail, onActivity: (() -> Unit)?, onClose: () -> Unit) {
    val s = detail.summary
    val about = remember(detail.description) { detail.description?.let { plainActivityData(it) }.orEmpty() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("This Project", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (about.isNotBlank()) {
                    Text(about, fontFamily = Inter, fontSize = 13.sp, lineHeight = 20.sp, color = Lex.colors.secondaryForeground)
                    Spacer(Modifier.height(14.dp))
                }
                val rows = listOfNotNull(
                    // Only what the head does not already say; as the web's overview.
                    "Reference" to "#${s.id}",
                    detail.projectCost?.takeIf { detail.settings.viewFinanceOverview && s.billingType == 1 && it > 0 }
                        ?.let { "Fee" to com.legumlex.client.feature.finance.formatMoney(it, "₹") },
                    s.startDate?.let { "Started" to it.format(dayYearFmt) },
                    s.deadline?.takeIf { s.stages == null && s.nextMilestone?.dueDate != null }?.let { "Deadline" to it.format(dayYearFmt) },
                    detail.dateFinished?.let { "Finished" to it.toLocalDate().format(dayYearFmt) },
                    s.team.takeIf { it.isNotEmpty() && detail.settings.viewTeamMembers }?.let { t -> "Team" to t.joinToString(", ") { it.name } },
                )
                rows.forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(k, fontFamily = Inter, fontSize = 12.sp, color = Lex.colors.mutedForeground, modifier = Modifier.width(84.dp))
                        Text(v, fontFamily = Inter, fontSize = 13.sp, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                    }
                    if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Lex.colors.muted))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Close", color = Lex.colors.foreground) } },
        dismissButton = onActivity?.let { go -> { androidx.compose.material3.TextButton(onClick = go) { Text("Activity", color = Lex.colors.foreground) } } },
    )
}

@Composable
private fun StageTracker(st: ClientStages, needs: List<NeedItem>, onOpenTask: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        com.legumlex.client.ui.components.LexCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(st.outcomeName?.let { "Concluded: $it" } ?: st.current?.name ?: "—", fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Lex.colors.foreground)
                    }
                    Spacer(Modifier.width(10.dp))
                    com.legumlex.client.ui.components.StatusChip(st.chip,
                        if (st.chip == "Closed") Lex.colors.success else Lex.colors.neutral)
                }
                // The title already says it concluded; the line says how.
                val line = st.outcomeLine?.let { o -> o.replaceFirstChar { it.uppercase() }.let { if (it.endsWith(".")) it else "$it." } }
                    ?: st.current?.takeIf { !st.currentHidden }?.clientLine?.takeIf { it.isNotBlank() }
                line?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp, color = Lex.colors.secondaryForeground)
                }
                st.current?.takeIf { !st.currentHidden && st.outcomeName == null }?.enteredAt?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("In This Stage Since ${it.let(::sday)}", fontFamily = Inter, fontSize = 11.sp, color = Lex.colors.mutedForeground)
                }
                if (st.chip == "Waiting On You") {
                    Spacer(Modifier.height(14.dp))
                    NeedsYou(needs, onOpenTask)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Lex.colors.border))
                Spacer(Modifier.height(14.dp))
                val shown = st.stages.filter { !it.outcome || it.state == "reached" }
                shown.forEachIndexed { i, x ->
                    // Not on a stage the firm keeps from the client: there, "current" is the last one they saw, already done.
                    val here = st.current?.milestoneId == x.milestoneId && st.outcomeName == null && !st.currentHidden
                    StageLine(x, here, last = i == shown.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun NeedsYou(needs: List<NeedItem>, onOpenTask: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted)
            .border(1.dp, Lex.colors.border, RoundedCornerShape(8.dp)).padding(12.dp),
    ) {
        Text("The Firm Needs Something From You", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Lex.colors.foreground)
        Spacer(Modifier.height(6.dp))
        if (needs.isEmpty()) {
            Text("Your advocate will tell you what is needed.", fontFamily = Inter, fontSize = 12.5.sp, color = Lex.colors.secondaryForeground)
        }
        needs.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpenTask(n.id) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(n.name, fontFamily = Inter, fontSize = 13.sp, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = Lex.colors.input, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StageLine(x: ClientStage, here: Boolean, last: Boolean) {
    val quiet = x.state == "skipped" || x.state == "not_applicable"
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val ring = RoundedCornerShape(50)
            Box(
                Modifier.size(22.dp).clip(ring).then(
                    when {
                        here -> Modifier.background(Lex.colors.primary)
                        x.state == "reached" -> Modifier.background(Lex.colors.success.softBg)
                        else -> Modifier.border(1.dp, Lex.colors.input, ring)
                    }
                ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    here -> Box(Modifier.size(7.dp).clip(ring).background(Lex.colors.primaryForeground))
                    x.state == "reached" -> Icon(Icons.Outlined.Check, null, tint = Lex.colors.success.softText, modifier = Modifier.size(13.dp))
                    x.outcome -> Icon(Icons.Outlined.Flag, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(12.dp))
                    quiet -> Text("–", fontFamily = Inter, fontSize = 11.sp, color = Lex.colors.mutedForeground)
                    else -> Text("${x.number ?: ""}", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Lex.colors.mutedForeground)
                }
            }
            if (!last) Box(Modifier.width(1.dp).weight(1f).background(Lex.colors.border))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(x.name, fontFamily = Inter, fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.5.sp,
                    color = if (quiet || x.state == "pending") Lex.colors.mutedForeground else Lex.colors.foreground)
                if (here) {
                    Spacer(Modifier.width(8.dp))
                    com.legumlex.client.ui.components.StatusChip("You Are Here", Lex.colors.neutral)
                }
            }
            val sub = when (x.state) {
                "reached" -> listOfNotNull(x.reachedAt?.let { "Completed ${it.let(::sday)}" } ?: "Completed",
                    x.officialDate?.let { "${x.officialDateLabel.ifBlank { "Date" }} ${it.let(::sday)}" },
                    x.reference.takeIf { it.isNotBlank() }?.let(::refText)).joinToString(" · ")
                "skipped", "not_applicable" -> "Not Needed"
                else -> if (here) listOfNotNull(x.officialDate?.let { "${x.officialDateLabel.ifBlank { "Date" }} ${it.let(::sday)}" },
                    x.reference.takeIf { it.isNotBlank() }?.let(::refText)).joinToString(" · ") else ""
            }
            if (sub.isNotBlank()) Text(sub, fontFamily = Inter, fontSize = 11.sp, color = Lex.colors.mutedForeground, modifier = Modifier.padding(top = 2.dp))
        }
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

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                com.legumlex.client.ui.components.SegmentedToggle(
                    options = listOf("open" to "Open · ${open.size}", "done" to "Done · ${done.size}"),
                    selected = seg, onSelect = onSeg,
                )
            }
            if (canCreate) {
                Spacer(Modifier.width(10.dp))
                // An ordinary action: ink outline, never the danger red.
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted).border(LlDimen.HairlineWidth, Lex.colors.border, RoundedCornerShape(8.dp)).clickable(onClick = onCreate).padding(start = 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Add, null, tint = Lex.colors.foreground, modifier = Modifier.size(15.dp))
                    Text("New Task", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Lex.colors.foreground)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            EmptySection(Icons.Outlined.Checklist, if (seg == "done") "Nothing Done Yet" else "No Open Tasks", if (canCreate && seg == "open") "Add a task with New Task, or the firm adds tasks as the work moves." else "Tasks will appear here.")
        } else {
            Card {
                list.forEachIndexed { i, t ->
                    TaskRow(t) { onOpenTask(t.id) }
                    if (i < list.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Lex.colors.muted))
                }
            }
        }
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
            Text(t.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, color = if (t.status == TaskStatus.Complete) Lex.colors.secondaryForeground else Lex.colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
            val meta = buildList {
                if (t.checklistTotal > 0) add("${t.checklistDone}/${t.checklistTotal} ✓")
                if (t.commentsCount > 0) add("${t.commentsCount} comment${if (t.commentsCount == 1) "" else "s"}")
                t.milestoneName?.let { add(it) }
                // A due date gone by says so, as the project card does.
                t.dueDate?.let { add(if (it.isBefore(java.time.LocalDate.now()) && t.status != TaskStatus.Complete) "Due date passed ${it.let(::sday)}" else "Due ${it.let(::sday)}") }
            }.joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, fontFamily = Inter, fontSize = 10.sp, color = Lex.colors.mutedForeground, modifier = Modifier.padding(top = 8.dp))
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Lex.colors.input, modifier = Modifier.size(18.dp))
    }
}

// =============================================================================
// More — drill-down rows (open dedicated routes)
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
                com.legumlex.client.ui.components.WizardHeading("What is the task?", "For ${projectName}. The firm sees it on the project.")
                com.legumlex.client.ui.components.WizardTextField("Task Name", name, { name = it }, placeholder = "e.g. Send revised logo files")
                Spacer(Modifier.height(16.dp))
                com.legumlex.client.ui.components.WizardLabel("Details (Optional)")
                com.legumlex.client.ui.components.WizardTextArea(desc, { desc = it }, "Add any context for the firm…")
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
                    Text("This project has no milestones, so the task will stand on its own.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = Lex.colors.secondaryForeground)
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
                if (due != null) Text("Clear the due date", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Lex.colors.secondaryForeground, modifier = Modifier.padding(top = 10.dp).clickable { due = null })
                if (!datesOk) Text("The due date cannot be earlier than the start date.", fontFamily = Inter, fontSize = 12.5.sp, color = Lex.colors.danger.softText, modifier = Modifier.padding(top = 10.dp))
                milestone?.dueDate?.let { md ->
                    if (due?.isAfter(md) == true) Text("This is after the milestone's own date, ${md.format(dayYearFmt)}.", fontFamily = Inter, fontSize = 12.5.sp, color = Lex.colors.warning.softText, modifier = Modifier.padding(top = 10.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Lex.colors.card).border(LlDimen.HairlineWidth, Lex.colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.CalendarToday, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(17.dp))
        Text(text, fontFamily = Inter, fontSize = 13.5.sp, color = if (placeholder) Lex.colors.mutedForeground else Lex.colors.foreground)
    }
}

@Composable
private fun FailedSections(failed: Set<ProjectSection>, busy: Boolean, onRetry: () -> Unit) {
    val names = failed.joinToString(", ") { it.name.lowercase() }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Lex.colors.warning.softBg)
            .border(LlDimen.HairlineWidth, AmberBorderFix, RoundedCornerShape(10.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = Lex.colors.warning.softText, modifier = Modifier.size(18.dp))
        Text("This project's $names could not be loaded. The information below may be incomplete.", fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = Lex.colors.warning.softText, modifier = Modifier.weight(1f))
        Text("Retry", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Lex.colors.foreground,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !busy, onClick = onRetry).padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

// =============================================================================
// Shared pieces
// =============================================================================

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(LlShape.Card).background(Lex.colors.card).border(LlDimen.HairlineWidth, Lex.colors.border, LlShape.Card), content = content)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lex.colors.muted).border(LlDimen.HairlineWidth, Lex.colors.input, LlShape.Card).padding(vertical = 32.dp, horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Lex.colors.card).border(LlDimen.HairlineWidth, Lex.colors.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(13.dp))
        Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Lex.colors.foreground)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = Lex.colors.secondaryForeground, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 248.dp))
    }
}

@Composable
private fun ProjectDetailSkeleton() {
    Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
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
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(Lex.colors.muted))
}

@Composable
private fun ProjectDetailError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Lex.colors.background).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Lex.colors.muted).border(LlDimen.HairlineWidth, Lex.colors.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = Lex.colors.secondaryForeground, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Project Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = Lex.colors.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = Lex.colors.secondaryForeground, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Lex.colors.foreground).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = Lex.colors.background, modifier = Modifier.size(18.dp))
            Text("Try Again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = Lex.colors.background)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** "11 Aug" this year, "19 Nov 2025" otherwise, so a stage list reads in order. */
private fun sday(d: LocalDate): String = if (d.year == LocalDate.now().year) d.format(dayFmt) else d.format(dayYearFmt)
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@androidx.compose.runtime.Composable private fun statusTone(s: ProjectStatus): Pair<Color, Color> = when (s) {
    ProjectStatus.InProgress -> Lex.colors.neutral.softBg to Lex.colors.neutral.solid
    ProjectStatus.OnHold     -> Lex.colors.warning.softBg to Lex.colors.warning.softText
    ProjectStatus.Finished   -> Lex.colors.success.softBg to Lex.colors.success.softText
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> Lex.colors.muted to Lex.colors.secondaryForeground
}

@androidx.compose.runtime.Composable private fun taskTone(s: TaskStatus): Pair<Color, Color> = when (s) {
    TaskStatus.Complete         -> Lex.colors.success.softBg to Lex.colors.success.softText
    TaskStatus.AwaitingFeedback -> Lex.colors.warning.softBg to Lex.colors.warning.softText
    TaskStatus.InProgress, TaskStatus.Testing -> Lex.colors.neutral.softBg to Lex.colors.neutral.solid
    TaskStatus.NotStarted, TaskStatus.Unknown -> Lex.colors.muted to Lex.colors.secondaryForeground
}

@androidx.compose.runtime.Composable private fun ProjectStatus.meter(): Color = when (this) {
    ProjectStatus.InProgress -> Lex.colors.neutral.solid
    ProjectStatus.OnHold     -> Lex.colors.warning.solid
    ProjectStatus.Finished   -> Lex.colors.success.solid
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> Lex.colors.mutedForeground
}

@androidx.compose.runtime.Composable private fun priorityTag(priority: Int?): Pair<String, Pair<Color, Color>>? = when (priority) {
    4 -> "Urgent" to (Lex.colors.danger.softBg to Lex.colors.danger.solid)
    3 -> "High" to (Lex.colors.warning.softBg to Lex.colors.warning.softText)
    2 -> "Medium" to (Lex.colors.neutral.softBg to Lex.colors.neutral.solid)
    else -> null
}

@androidx.compose.runtime.Composable private fun priorityDot(code: Int): Color = when (code) {
    4 -> Lex.colors.danger.solid; 3 -> Lex.colors.warning.solid; 2 -> Lex.colors.neutral.solid; else -> Lex.colors.mutedForeground
}

/** A stage's reference; "Ref." only when it does not already name itself ("Registration No. …"). */
private fun refText(r: String): String = if (Regex("""\bNo\.|:""").containsMatchIn(r)) r else "Ref. $r"

private fun humanizeKey(key: String): String =
    key.removePrefix("project_activity_").removePrefix("not_").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Activity" }
