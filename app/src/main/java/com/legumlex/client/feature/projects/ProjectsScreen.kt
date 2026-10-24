package com.legumlex.client.feature.projects

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.PageHeader
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Projects List.html" (Plot/Lexcore). The transactional & advisory
// register — the progress-led sibling of the litigation Matters list. Where a
// matter is paced by its next hearing, a project is paced by its progress and
// the nearest deadline: the most imminent deadline across active projects is
// pulled into a spotlight, then the rest band into Due-this-week / Active /
// Finished. App-wide centred-logo header + bottom nav are provided by
// MainActivity. A project is chamber-opened; the client tracks progress and
// acts inside it (tasks, comments, files) on the detail screen.
// -----------------------------------------------------------------------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(onOpenProject: (String) -> Unit) {
    val vm: ProjectsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    var filter by rememberSaveable { mutableStateOf(ProjectFilter.ALL) }

    Scaffold(
        containerColor = LlColor.Paper,
        // The app-wide header already consumes the status-bar inset; without this
        // the nested Scaffold re-adds it as a fixed empty band under the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is ProjectsUiState.Loading -> ProjectsSkeleton()
                is ProjectsUiState.Error   -> ProjectsError(s.message, vm::retry)
                is ProjectsUiState.Ready   -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh    = vm::refresh,
                    modifier     = Modifier.fillMaxSize(),
                ) {
                    ProjectsReady(s.projects, filter, { filter = it }, onOpenProject)
                }
            }
        }
    }
}

// =============================================================================
// Banding
// =============================================================================

private fun ProjectSummary.isFinished(): Boolean =
    status == ProjectStatus.Finished || status == ProjectStatus.Cancelled

/** The date that paces the project — its nearest upcoming milestone, else its deadline. */
private fun ProjectSummary.pacingDate(): LocalDate? = nextMilestone?.dueDate ?: deadline

private fun ProjectSummary.tasksDone(): Int = (tasksTotal - tasksOpen).coerceAtLeast(0)

/**
 * Projects, as Mylex's projects screen lays it out: the eyebrow and title,
 * four figures, an All | Active | Finished toggle, and a card per project —
 * a status stripe, the name, progress, and the dates.
 */
@Composable
private fun ProjectsReady(
    projects: List<ProjectSummary>,
    filter: ProjectFilter,
    onFilter: (ProjectFilter) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val active = remember(projects) { projects.filterNot { it.isFinished() } }
    val finished = remember(projects) { projects.filter { it.isFinished() } }
    val shown = remember(filter, projects) {
        val byPace = active.sortedBy { it.pacingDate() ?: LocalDate.MAX }
        when (filter) {
            ProjectFilter.ALL -> byPace + finished
            ProjectFilter.ACTIVE -> byPace
            ProjectFilter.CLOSED -> finished
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") { PageHeader(eyebrow = "Your Work With Us", title = "Projects") }
        item("figures") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Projects", "${projects.size}", "Every project the firm runs for you", Modifier.weight(1f).fillMaxHeight())
                    StatCard("Active", "${active.size}", "Projects still being worked on", Modifier.weight(1f).fillMaxHeight(),
                        chip = { if (active.isNotEmpty()) StatusChip("In Progress", Lex.colors.neutral) })
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val open = projects.sumOf { it.tasksOpen }
                    StatCard("Open Tasks", "$open", "Tasks not yet done across your projects", Modifier.weight(1f).fillMaxHeight(),
                        chip = { if (open == 0) StatusChip("None Open", Lex.colors.success, Icons.Outlined.CheckCircle) })
                    StatCard("Finished", "${finished.size}", "Projects completed or closed", Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        if (projects.isEmpty()) {
            item("empty") { LexCard { ProjectsEmpty() } }
            return@LazyColumn
        }
        item("filter") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedToggle(
                    options = listOf(ProjectFilter.ALL to "All", ProjectFilter.ACTIVE to "Active", ProjectFilter.CLOSED to "Finished"),
                    selected = filter, onSelect = onFilter,
                )
                Spacer(Modifier.weight(1f))
                Text("${shown.size} ${if (shown.size == 1) "Project" else "Projects"}",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
            }
        }
        if (shown.isEmpty()) item("empty-filter") { LexCard { FilteredEmpty(filter) } }
        items(shown, key = { it.id }) { pr -> ProjectCard(pr, today) { onOpenProject(pr.id) } }
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, today: LocalDate, onClick: () -> Unit) {
    val done = p.isFinished()
    LexCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(2.dp)).background(p.status.palette.solid))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                // A typed project says where it stands and whose move it is, as the portal's card.
                p.stages?.let { st ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(st.outcomeName?.let { "Concluded: $it" } ?: st.current?.name ?: st.typeName,
                            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        StatusChip(st.chip, if (st.chip == "Closed") Lex.colors.success else Lex.colors.neutral)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lex.colors.secondary)) {
                        Box(Modifier.fillMaxWidth(p.progress.coerceIn(0, 100) / 100f).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp)).background(Lex.colors.primary))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (p.status == ProjectStatus.Cancelled) "—" else "${p.progress}%",
                        style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(p.status.label, p.status.palette)
        }
        HorizontalDivider(color = Lex.colors.border)
        Column(Modifier.background(Lex.colors.muted.copy(alpha = 0.5f))) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                if (p.stages != null) PFact("Stage", p.stages.outcomeName ?: p.stages.current?.name ?: "—", Modifier.weight(1f))
                else PFact("Next Milestone",
                    p.nextMilestone?.let { m ->
                        val passed = m.dueDate?.isBefore(java.time.LocalDate.now()) == true
                        listOfNotNull(m.name, m.dueDate?.let { (if (passed) "Passed " else "") + pdate(it) }).joinToString(" · ")
                    } ?: "No Milestone Set",
                    Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.border))
                PFact("Tasks", if (p.tasksTotal == 0) "No Tasks Yet" else "${p.tasksDone()} of ${p.tasksTotal} done", Modifier.weight(1f))
            }
            HorizontalDivider(color = Lex.colors.border)
            Row(Modifier.height(IntrinsicSize.Min)) {
                PFact("Started", p.startDate?.let { pdate(it) } ?: "Not Recorded", Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.border))
                // A deadline behind us is said to have passed, not only coloured.
                // Not while the work sits with an authority: that wait is not late work.
                val withAuthority = p.stages?.chip?.let { it.startsWith("With The ") && it != "With The Firm" } == true
                val passed = !done && !withAuthority && p.deadline?.isBefore(today) == true
                PFact(if (passed) "Deadline Passed" else "Deadline", p.deadline?.let { pdate(it) } ?: "No Deadline Set", Modifier.weight(1f),
                    alarm = passed)
            }
        }
    }
}

@Composable
private fun PFact(label: String, value: String, modifier: Modifier, alarm: Boolean = false) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = if (alarm) Lex.colors.danger.solid else Lex.colors.foreground, maxLines = 2)
    }
}

private fun pdate(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))

// =============================================================================
// Empty / loading / error
// =============================================================================

@Composable
private fun ProjectsEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.SlateSoft)
                .border(LlDimen.HairlineWidth, LlColor.SlateBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WorkOutline, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("No Projects Yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "When your chamber opens a piece of transactional work — a filing, a contract, a compliance retainer — it will appear here, with its tasks, milestones and progress kept up to date.",
            fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Need something done? Ask the firm through Consultations.", fontFamily = Inter, fontSize = 11.5.sp,
            color = LlColor.InkFaint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilteredEmpty(filter: ProjectFilter) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WorkOutline, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            when (filter) {
                ProjectFilter.ACTIVE -> "No Active Projects"
                ProjectFilter.CLOSED -> "No Finished Projects"
                else -> "No Projects"
            },
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text("No records match this view.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
    }
}

/** Mirrors the loaded page: header, four figures, the filter row, then project cards. */
@Composable
private fun ProjectsSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column { Sk(110.dp, 11.dp); Spacer(Modifier.height(9.dp)); Sk(140.dp, 26.dp, 7.dp) }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Sk(modifier = Modifier.weight(1f), height = 96.dp, radius = 12.dp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Sk(210.dp, 34.dp, 8.dp); Spacer(Modifier.weight(1f)); Sk(64.dp, 13.dp) }
        repeat(3) {
            Column(
                Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Sk(modifier = Modifier.fillMaxWidth(0.6f), height = 16.dp); Sk(78.dp, 20.dp, 6.dp) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Sk(modifier = Modifier.weight(1f), height = 6.dp, radius = 3.dp); Sk(30.dp, 11.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Sk(modifier = Modifier.weight(1f), height = 30.dp); Sk(modifier = Modifier.weight(1f), height = 30.dp) }
            }
        }
    }
}

@Composable
private fun Sk(width: androidx.compose.ui.unit.Dp? = null, height: androidx.compose.ui.unit.Dp = 14.dp, radius: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier).height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun ProjectsError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Projects Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("Try Again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.01.em, color = LlColor.Paper)
        }
    }
}

