package com.legumlex.client.feature.projects

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
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
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

// LlColor.ClaretSoft / ClaretMid are broken placeholders; use the real tints.
private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretBorderFix = Color(0xFFF4D9DF)
private val AmberBorderFix = Color(0xFFF3E3C4)

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

private fun ProjectSummary.deadlineDays(today: LocalDate): Long? {
    val d = pacingDate() ?: return null
    if (d.isBefore(today)) return null
    return ChronoUnit.DAYS.between(today, d)
}

private fun ProjectSummary.tasksDone(): Int = (tasksTotal - tasksOpen).coerceAtLeast(0)

private data class Band(val name: String, val due: Boolean, val items: List<ProjectSummary>)

@Composable
private fun ProjectsReady(
    projects: List<ProjectSummary>,
    filter: ProjectFilter,
    onFilter: (ProjectFilter) -> Unit,
    onOpen: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val active = remember(projects) { projects.filterNot { it.isFinished() } }
    val finished = remember(projects) { projects.filter { it.isFinished() } }

    val dueWeek = remember(active, today) {
        active.filter { (it.deadlineDays(today) ?: Long.MAX_VALUE) <= 7 }
            .sortedBy { it.pacingDate() }
    }
    val activeRest = remember(active, dueWeek) {
        (active - dueWeek.toSet()).sortedWith(
            compareBy({ it.pacingDate() == null }, { it.pacingDate() }),
        )
    }
    // Spotlight: nearest upcoming deadline across all active projects.
    val spotlight = remember(active, today) {
        active.filter { it.deadlineDays(today) != null }.minByOrNull { it.pacingDate()!! }
    }

    val bands = remember(filter, dueWeek, activeRest, finished) {
        when (filter) {
            ProjectFilter.ALL -> listOf(
                Band("Due this week", true, dueWeek),
                Band("Active", false, activeRest),
                Band("Finished", false, finished),
            )
            ProjectFilter.ACTIVE -> listOf(
                Band("Due this week", true, dueWeek),
                Band("Active", false, activeRest),
            )
            ProjectFilter.CLOSED -> listOf(Band("Finished", false, finished))
        }.filter { it.items.isNotEmpty() }
    }

    if (projects.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("intro") { PageIntro(filter, active.size, finished.size, projects.size, showCount = false) }
            item("empty") { ProjectsEmpty() }
        }
        return
    }

    val showSpotlight = filter != ProjectFilter.CLOSED && spotlight != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("intro") { PageIntro(filter, active.size, finished.size, projects.size, showCount = true) }

        if (showSpotlight) {
            item("spotlight") { Spotlight(spotlight!!, today) { onOpen(spotlight.id) } }
        }

        item("filters") {
            FilterBar(all = projects.size, active = active.size, finished = finished.size, selected = filter, onSelect = onFilter)
        }

        if (bands.isEmpty()) {
            item("empty-filter") { FilteredEmpty(filter) }
        } else {
            bands.forEachIndexed { bi, band ->
                item("head-${band.name}") { SectionHeader(band.name, band.items.size, band.due, first = bi == 0) }
                items(band.items, key = { it.id }) { p ->
                    Box(Modifier.padding(start = LlDimen.ScreenPad, end = LlDimen.ScreenPad, bottom = 10.dp)) {
                        ProjectRow(p, today, band.due) { onOpen(p.id) }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Page intro
// =============================================================================

@Composable
private fun PageIntro(filter: ProjectFilter, active: Int, finished: Int, total: Int, showCount: Boolean) {
    val sub = when (filter) {
        ProjectFilter.ALL -> "Your transactional & advisory work."
        ProjectFilter.ACTIVE -> "The work still in flight."
        ProjectFilter.CLOSED -> "Work your chamber has closed out."
    }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
        Text("PROJECTS", fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.16.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(9.dp))
        Text("Your Projects", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, letterSpacing = (-0.025).em, lineHeight = 30.sp, color = LlColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text(sub, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 20.sp, color = LlColor.InkMuted, modifier = Modifier.widthIn(max = 330.dp))
        if (showCount) {
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (filter) {
                    ProjectFilter.CLOSED -> CountFrag(finished, "finished")
                    ProjectFilter.ACTIVE -> CountFrag(active, "active")
                    else -> {
                        CountFrag(active, "active")
                        Text("·", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.BorderStrong)
                        CountFrag(total, "total")
                    }
                }
            }
        }
    }
}

@Composable
private fun CountFrag(n: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$n ", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
        Text(label, fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
    }
}

// =============================================================================
// Nearest-deadline spotlight
// =============================================================================

@Composable
private fun Spotlight(p: ProjectSummary, today: LocalDate, onClick: () -> Unit) {
    val days = p.deadlineDays(today) ?: 0L
    val soon = days <= 3
    val band = if (soon) ClaretMidFix else LlColor.AmberMid
    val boxBg = if (soon) ClaretSoftFix else LlColor.AmberSoft
    val boxBorder = if (soon) ClaretBorderFix else AmberBorderFix
    val accent = if (soon) LlColor.Claret else LlColor.Amber
    val accentMid = if (soon) ClaretMidFix else LlColor.AmberMid

    val dateLabel = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }
    val eyebrow = if (days == 1L) "Nearest deadline · Tomorrow" else "Nearest deadline"
    val milestone = p.nextMilestone?.name

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp).background(band))
        Column(Modifier.padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (soon) PulseDot(accentMid, 7.dp) else Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accentMid))
                Text(eyebrow.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = accent)
            }
            Spacer(Modifier.height(11.dp))
            Text(p.name, fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp, letterSpacing = (-0.015).em, lineHeight = 24.sp, color = LlColor.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            milestone?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Flag, null, tint = accent, modifier = Modifier.size(13.dp))
                    Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // progress meter row
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("${p.tasksDone()}/${p.tasksTotal} tasks", fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint)
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                    Box(Modifier.fillMaxWidth(p.progress.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.status.meterColor()))
                }
                Text("${p.progress}%", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // countdown chip
                Column(
                    Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(11.dp)).background(boxBg)
                        .border(LlDimen.HairlineWidth, boxBorder, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (days == 0L) {
                        Text("Today", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = accent)
                    } else {
                        Text("$days", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = accent)
                        Text(if (days == 1L) "DAY" else "DAYS", fontFamily = SourceSans, fontSize = 8.sp, letterSpacing = 0.12.em, color = accentMid, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("$dateLabel · ${p.pacingDate()!!.format(dowFmt)}",
                        fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 17.sp, color = LlColor.Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(milestone?.let { "Milestone · $it" } ?: "Deadline", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(LlColor.Paper1),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = LlColor.InkMuted, modifier = Modifier.size(19.dp)) }
            }
        }
    }
}

// =============================================================================
// Filter bar
// =============================================================================

// Display order All · Active · Finished; CLOSED renders as "Finished".
private val filterOrder = listOf(ProjectFilter.ALL, ProjectFilter.ACTIVE, ProjectFilter.CLOSED)
private fun ProjectFilter.barLabel(): String = if (this == ProjectFilter.CLOSED) "Finished" else label

@Composable
private fun FilterBar(all: Int, active: Int, finished: Int, selected: ProjectFilter, onSelect: (ProjectFilter) -> Unit) {
    val counts = mapOf(ProjectFilter.ALL to all, ProjectFilter.ACTIVE to active, ProjectFilter.CLOSED to finished)
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterOrder.forEach { f ->
                FilterChip(f.barLabel(), counts[f] ?: 0, selected == f) { onSelect(f) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) LlColor.Ink else LlColor.Paper)
            .border(LlDimen.HairlineWidth, if (selected) LlColor.Ink else LlColor.Border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) LlColor.Paper else LlColor.InkMuted)
        if (count > 0) {
            Box(
                Modifier.heightIn(min = 17.dp).widthIn(min = 17.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) LlColor.Paper.copy(alpha = 0.2f) else LlColor.Paper2).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                    color = if (selected) LlColor.Paper else LlColor.InkMuted)
            }
        }
    }
}

// =============================================================================
// Section header
// =============================================================================

@Composable
private fun SectionHeader(name: String, count: Int, due: Boolean, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (first) 16.dp else 18.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.5.sp, letterSpacing = 0.15.em,
            color = if (due) LlColor.Claret else LlColor.InkFaint)
        Box(
            Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp))
                .background(if (due) ClaretSoftFix else LlColor.Paper2).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = if (due) ClaretMidFix else LlColor.InkMuted)
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

// =============================================================================
// Project row
// =============================================================================

private enum class DueTone { IMMINENT, WEEK, NORMAL, QUIET }

@Composable
private fun ProjectRow(p: ProjectSummary, today: LocalDate, dueBand: Boolean, onClick: () -> Unit) {
    val done = p.isFinished()
    Box(
        Modifier.fillMaxWidth().clip(LlShape.Card)
            .background(if (done) LlColor.Paper1 else LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .clickable(onClick = onClick),
    ) {
        if (dueBand) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(LlDimen.HeroStripeWidth).background(LlColor.Claret))
        Column(Modifier.padding(start = if (dueBand) 18.dp else 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("PRJ-${p.id}", fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 0.1.em,
                    color = LlColor.InkFaint, modifier = Modifier.padding(top = 3.dp))
                StatusPill(p.status)
            }
            Text(p.name, fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 21.sp,
                color = if (done) LlColor.InkMuted else LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 11.dp))
            // sub: tasks done / total
            Text(
                if (p.tasksTotal == 0) "No tasks yet" else "${p.tasksDone()}/${p.tasksTotal} tasks",
                fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint,
                modifier = Modifier.padding(top = 8.dp),
            )
            // progress meter
            Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                    Box(Modifier.fillMaxWidth(p.progress.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(p.status.meterColor()))
                }
                Text(
                    if (p.status == ProjectStatus.Cancelled) "—" else "${p.progress}%",
                    fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.InkMuted,
                )
            }
            DeadlineLine(p, today, done)
        }
    }
}

@Composable
private fun DeadlineLine(p: ProjectSummary, today: LocalDate, done: Boolean) {
    val days = p.deadlineDays(today)
    val tone = when {
        done -> DueTone.QUIET
        days == null -> DueTone.QUIET
        days <= 3 -> DueTone.IMMINENT
        days <= 7 -> DueTone.WEEK
        else -> DueTone.NORMAL
    }
    val (icon, text, color) = when (tone) {
        DueTone.IMMINENT -> Triple(
            Icons.Filled.Flag,
            when (days) {
                0L -> "${dueLabel(p)} · Today"
                1L -> "${dueLabel(p)} · Tomorrow"
                else -> "${dueLabel(p)} · due ${p.pacingDate()!!.format(dowFmt)}"
            },
            ClaretMidFix,
        )
        DueTone.WEEK -> Triple(Icons.Filled.Flag, "${dueLabel(p)} · due ${p.pacingDate()!!.format(dowFmt)}", LlColor.AmberMid)
        DueTone.NORMAL -> Triple(Icons.Outlined.Flag, "${dueLabel(p)} · due ${p.pacingDate()!!.format(dowFmt)}", LlColor.Ink)
        DueTone.QUIET -> {
            if (done) {
                val finished = p.status == ProjectStatus.Finished
                Triple(
                    if (finished) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    if (finished) "Completed" else "Cancelled",
                    LlColor.InkFaint,
                )
            } else if (p.status == ProjectStatus.OnHold) {
                Triple(Icons.Outlined.History, "On hold", LlColor.InkFaint)
            } else {
                Triple(Icons.Outlined.Schedule, "In progress", LlColor.InkFaint)
            }
        }
    }

    Column(Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val iconAlpha = if (tone == DueTone.IMMINENT) pulseAlpha() else 1f
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).alpha(iconAlpha))
            Text(
                text,
                fontFamily = if (tone == DueTone.NORMAL || tone == DueTone.QUIET) SourceSans else SourceSans,
                fontWeight = if (tone == DueTone.QUIET) FontWeight.Normal else FontWeight.SemiBold,
                fontSize = if (tone == DueTone.QUIET || tone == DueTone.NORMAL) 11.sp else 11.5.sp,
                color = color, lineHeight = 16.sp,
            )
        }
    }
}

private fun dueLabel(p: ProjectSummary): String = p.nextMilestone?.name ?: "Deadline"

@Composable
private fun StatusPill(status: ProjectStatus) {
    val (bg, fg) = when (status) {
        ProjectStatus.InProgress -> LlColor.SlateSoft to LlColor.SlateMid
        ProjectStatus.OnHold     -> LlColor.AmberSoft to LlColor.Amber
        ProjectStatus.Finished   -> LlColor.VerdantSoft to LlColor.Verdant
        ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.Paper2 to LlColor.InkMuted
    }
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(status.label, fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = fg)
    }
}

private fun ProjectStatus.meterColor(): Color = when (this) {
    ProjectStatus.InProgress -> LlColor.SlateMid
    ProjectStatus.OnHold     -> LlColor.AmberMid
    ProjectStatus.Finished   -> LlColor.VerdantMid
    ProjectStatus.NotStarted, ProjectStatus.Cancelled, ProjectStatus.Unknown -> LlColor.InkFaint
}

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).alpha(pulseAlpha()).clip(RoundedCornerShape(50)).background(color))
}

@Composable
private fun pulseAlpha(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulseAlpha",
    )
    return a
}

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
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.SlateSoft)
                .border(LlDimen.HairlineWidth, Color(0xFFD6E3F3), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WorkOutline, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("No projects yet", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "When your chamber opens a piece of transactional work — a filing, a contract, a compliance retainer — it'll appear here, with its tasks, milestones and progress kept up to date.",
            fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(11.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Forum, null, tint = LlColor.InkFaint, modifier = Modifier.size(16.dp))
            Text("Need something done? Raise it in Consultations", fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
        }
    }
}

@Composable
private fun FilteredEmpty(filter: ProjectFilter) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WorkOutline, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            when (filter) {
                ProjectFilter.ACTIVE -> "No active projects"
                ProjectFilter.CLOSED -> "No finished projects"
                else -> "No projects"
            },
            fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text("Nothing in this view right now.", fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProjectsSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
            Sk(74.dp, 11.dp); Spacer(Modifier.height(11.dp)); Sk(172.dp, 28.dp, 7.dp); Spacer(Modifier.height(11.dp)); Sk(228.dp, 13.dp)
        }
        // spotlight skeleton (with its progress-meter line)
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(16.dp)).padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 17.dp),
        ) {
            Sk(96.dp, 11.dp); Spacer(Modifier.height(13.dp)); Sk(modifier = Modifier.fillMaxWidth(0.82f), height = 18.dp); Spacer(Modifier.height(9.dp)); Sk(modifier = Modifier.fillMaxWidth(0.58f), height = 11.dp)
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Sk(modifier = Modifier.weight(1f), height = 5.dp, radius = 3.dp); Sk(34.dp, 11.dp)
            }
            Spacer(Modifier.height(14.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2)); Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sk(52.dp, 44.dp, 11.dp)
                Column(Modifier.weight(1f)) { Sk(modifier = Modifier.fillMaxWidth(0.6f), height = 13.dp); Spacer(Modifier.height(7.dp)); Sk(modifier = Modifier.fillMaxWidth(0.74f), height = 11.dp) }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(66, 82, 90).forEach { Sk(it.dp, 34.dp, 999.dp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).padding(bottom = 10.dp)
                    .clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Sk(70.dp, 14.dp); Sk(80.dp, 18.dp, 7.dp) }
                Spacer(Modifier.height(12.dp)); Sk(modifier = Modifier.fillMaxWidth(0.82f), height = 16.dp)
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Sk(modifier = Modifier.weight(1f), height = 5.dp, radius = 3.dp); Sk(30.dp, 11.dp)
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
private fun ProjectsError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load your projects", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
