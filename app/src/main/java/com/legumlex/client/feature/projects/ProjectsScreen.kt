package com.legumlex.client.feature.projects

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.RevealAction
import com.legumlex.client.ui.components.SwipeRevealRow
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
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveCircleOutline
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

/**
 * A project's card, as the reference: compact — the name in its chip, the
 * progress bar, whose move it is, the status and the team — and, tapped, open
 * in place: the count pill, the stages as a tree, the status rows and the team
 * by name. Swiped left, it shows Open, which opens the project.
 */
@Composable
private fun ProjectCard(p: ProjectSummary, today: LocalDate, onClick: () -> Unit) {
    var open by rememberSaveable(p.id) { mutableStateOf(false) }
    val done = p.isFinished()
    // A deadline behind us is said to have passed — not while an authority has the work.
    val withAuthority = p.stages?.chip?.let { it.startsWith("With The ") && it != "With The Firm" } == true
    val passed = !done && !withAuthority && p.deadline?.isBefore(today) == true
    SwipeRevealRow(listOf(RevealAction("Open", Icons.AutoMirrored.Outlined.OpenInNew, Lex.colors.primary, Lex.colors.primaryForeground, onClick)), actionsAtTop = open) {
        LexCard(onClick = { open = !open }) {
            Column(Modifier.fillMaxWidth().animateContentSize(tween(260)).padding(14.dp)) {
                if (!open) CardCompact(p, passed) else CardOpen(p, passed)
            }
        }
    }
}

@Composable
private fun CardCompact(p: ProjectSummary, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f, fill = false).clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeTile(22.dp)
            Spacer(Modifier.width(8.dp))
            // The whole name, over as many lines as it takes.
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
        }
        Spacer(Modifier.width(12.dp))
        ProgressBar(p.progress, Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (p.status == ProjectStatus.Cancelled) "—" else "${p.progress}%", style = MaterialTheme.typography.labelMedium, color = Lex.colors.secondaryForeground)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The flag takes what the status and the avatars leave it.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val (flag, alarm) = flagText(p, passed)
            Icon(Icons.Outlined.Flag, null, tint = if (alarm) Lex.colors.danger.solid else Lex.colors.mutedForeground, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(flag, style = MaterialTheme.typography.bodySmall, color = if (alarm) Lex.colors.danger.solid else Lex.colors.secondaryForeground,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.HourglassEmpty, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(p.status.label, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, maxLines = 1, softWrap = false)
        }
        if (p.team.isNotEmpty()) { Spacer(Modifier.width(10.dp)); AvatarStack(p.team) }
    }
}

@Composable
private fun CardOpen(p: ProjectSummary, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypeTile(36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            p.stages?.let { Eyebrow(it.typeName) }
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
        }
    }
    Spacer(Modifier.height(12.dp))
    val st = p.stages
    // The count pill: stages for a typed project, tasks otherwise.
    val ordinary = st?.stages?.filter { !it.outcome }.orEmpty()
    val (doneN, totalN, unit) = if (st != null) Triple(ordinary.count { it.state != "pending" && it.state != "current" }, ordinary.size, "")
        else Triple((p.tasksTotal - p.tasksOpen).coerceAtLeast(0), p.tasksTotal, " tasks")
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, Lex.colors.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("$doneN of $totalN$unit", style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground)
        Spacer(Modifier.width(10.dp))
        ProgressBar(p.progress, Modifier.width(90.dp))
        Spacer(Modifier.width(8.dp))
        Text("${p.progress}%", style = MaterialTheme.typography.labelMedium, color = Lex.colors.secondaryForeground)
    }
    if (st != null) {
        val shown = st.stages.filter { !it.outcome || it.state == "reached" }
        Column(Modifier.padding(start = 12.dp)) {
            shown.forEachIndexed { i, x -> TreeLine(x, last = i == shown.lastIndex) }
        }
    }
    Spacer(Modifier.height(14.dp))
    val (flag, alarm) = flagText(p, passed)
    InfoRow(Icons.Outlined.Flag, if (st != null) "Where It Is" else "Deadline") {
        StatusChip(flag, if (alarm) Lex.colors.danger else if (st?.chip == "Closed") Lex.colors.success else Lex.colors.neutral)
    }
    Spacer(Modifier.height(8.dp))
    InfoRow(Icons.Outlined.HourglassEmpty, "Status") { StatusChip(p.status.label, p.status.palette) }
    if (st != null && p.deadline != null) {
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Outlined.Event, "Deadline") {
            StatusChip(if (passed) "Passed ${pdate(p.deadline)}" else pdate(p.deadline), if (passed) Lex.colors.danger else Lex.colors.neutral)
        }
    }
    if (p.team.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.team.forEach { m ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, Lex.colors.border, RoundedCornerShape(50)).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m, 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(m.name.substringBefore(' ').ifBlank { m.name }, style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground)
                }
            }
        }
    }
}

/** What the flag says: whose move it is for a typed project, else the deadline. True when it is red. */
private fun flagText(p: ProjectSummary, passed: Boolean): Pair<String, Boolean> {
    val st = p.stages
    if (st != null) return (st.outcomeName?.let { "Concluded: $it" } ?: st.chip) to false
    val d = p.deadline ?: return "No Deadline Set" to false
    return (if (passed) "Passed ${sdate(d)}" else "Due ${sdate(d)}") to passed
}

@Composable
private fun TypeTile(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Lex.colors.card).border(1.dp, Lex.colors.border, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.WorkOutline, null, tint = Lex.colors.foreground, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun ProgressBar(pct: Int, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(4.dp)).background(Lex.colors.secondary)) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Lex.colors.success.solid))
    }
}

/** One branch of the stage tree: the rail, the elbow, the state mark and the name. */
@Composable
private fun TreeLine(x: ClientStage, last: Boolean) {
    val rail = Lex.colors.border
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp).fillMaxHeight()) {
            Box(Modifier.width(1.dp).fillMaxHeight(if (last) 0.5f else 1f).background(rail))
            Box(Modifier.align(Alignment.CenterStart).width(12.dp).height(1.dp).background(rail))
        }
        val (icon, tint) = when (x.state) {
            "reached" -> Icons.Filled.CheckCircle to Lex.colors.foreground
            "current" -> Icons.Outlined.RadioButtonChecked to Lex.colors.foreground
            "skipped", "not_applicable" -> Icons.Outlined.RemoveCircleOutline to Lex.colors.mutedForeground
            else -> Icons.Outlined.RadioButtonUnchecked to Lex.colors.mutedForeground
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            x.name, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (x.state == "current") FontWeight.SemiBold else FontWeight.Normal,
            color = if (x.state == "reached" || x.state == "current") Lex.colors.foreground else Lex.colors.mutedForeground,
            modifier = Modifier.padding(vertical = 7.dp),
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground, modifier = Modifier.width(92.dp))
        value()
    }
}

@Composable
private fun AvatarStack(team: List<TeamMember>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        team.take(3).forEach { Avatar(it, 26.dp, ring = true) }
        if (team.size > 3) Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(Lex.colors.muted).border(2.dp, Lex.colors.card, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { Text("+${team.size - 3}", style = MaterialTheme.typography.labelSmall, color = Lex.colors.secondaryForeground) }
    }
}

/** A member's photo, over their initials (which show while it loads, or if it can't). */
@Composable
private fun Avatar(m: TeamMember, size: androidx.compose.ui.unit.Dp, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)).background(Lex.colors.secondary)
            .then(if (ring) Modifier.border(2.dp, Lex.colors.card, RoundedCornerShape(50)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(m.initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold, color = Lex.colors.secondaryForeground, maxLines = 1, softWrap = false)
        m.image?.let {
            coil.compose.AsyncImage(it, m.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)))
        }
    }
}


/** "3 Aug" this year, "3 Aug 2027" otherwise. */
private fun sdate(d: LocalDate): String =
    if (d.year == LocalDate.now().year) d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) else pdate(d)

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

