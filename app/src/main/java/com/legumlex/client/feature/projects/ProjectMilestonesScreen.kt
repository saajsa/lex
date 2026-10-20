package com.legumlex.client.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Project Milestones.html" — the full milestone plan for a project,
// pushed from the Milestones drill-row on Project Detail. Deadline-first, like
// the Projects list: an overall strip, an Upcoming band (nearest accented), then
// a receded Completed band. Milestones are chamber-run; the client reads.
// -----------------------------------------------------------------------------

private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretMid

sealed interface MilestonesUiState {
    data object Loading : MilestonesUiState
    data class Ready(val projectName: String, val projectRef: String, val milestones: List<Milestone>) : MilestonesUiState
    data class Error(val message: String) : MilestonesUiState
}

class ProjectMilestonesViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<MilestonesUiState>(MilestonesUiState.Loading)
    val state: StateFlow<MilestonesUiState> = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { load() }

    fun load() { viewModelScope.launch { _state.value = MilestonesUiState.Loading; fetch(false) } }
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch { _isRefreshing.value = true; fetch(true); _isRefreshing.value = false }
    }

    private suspend fun fetch(preserve: Boolean) {
        try {
            coroutineScope {
                val detail = async { repo.detail(projectId).getOrThrow() }
                val ms = async { repo.milestones(projectId).getOrThrow() }
                val d = detail.await()
                _state.value = MilestonesUiState.Ready(d.summary.name, d.summary.status.label, ms.await())
            }
        } catch (e: Throwable) {
            if (!(preserve && _state.value is MilestonesUiState.Ready)) {
                _state.value = MilestonesUiState.Error(
                    when (e) {
                        is HttpException -> when (e.code()) { 401 -> "Your session has expired. Please sign in again."; 403 -> forbiddenMessage(e, "Your account does not include this project's milestones. Ask the firm to give you access."); 404 -> "This project is no longer available."; in 500..599 -> "A server error occurred. Please try again shortly."; else -> "The milestones could not be loaded (error ${e.code()})." }
                        else -> "The server could not be reached. Please check your connection and try again."
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMilestonesScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ProjectMilestonesViewModel = viewModel(
        key = "milestones-$projectId",
        factory = viewModelFactory { initializer { ProjectMilestonesViewModel(app, projectId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    Scaffold(containerColor = LlColor.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                val title = (state as? MilestonesUiState.Ready)?.let { it.projectRef + " · " + it.projectName }
                SubBar("Milestones", (state as? MilestonesUiState.Ready)?.milestones?.size, title, onBack)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val s = state) {
                        is MilestonesUiState.Loading -> MsSkeleton()
                        is MilestonesUiState.Error -> MsError(s.message, vm::load)
                        is MilestonesUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                            MilestonesReady(s.milestones)
                        }
                    }
                }
            }
        }
    }
}

private fun Milestone.complete() = tasksTotal > 0 && tasksDone >= tasksTotal

@Composable
private fun MilestonesReady(milestones: List<Milestone>) {
    val today = remember { LocalDate.now() }
    val completed = remember(milestones) { milestones.filter { it.complete() } }
    val upcoming = remember(milestones) {
        milestones.filterNot { it.complete() }.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate }))
    }
    val nearestId = remember(upcoming) { upcoming.firstOrNull { it.dueDate != null }?.id }

    if (milestones.isEmpty()) {
        MsEmpty(); return
    }
    val pct = if (milestones.isEmpty()) 0 else completed.size * 100 / milestones.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // Overall strip
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row { Text("${completed.size} of ${milestones.size}", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ClaretMidFix); Text(" milestones complete", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = LlColor.Ink) }
                Spacer(Modifier.height(9.dp))
                Meter(Modifier.fillMaxWidth(), pct / 100f, LlColor.SlateMid)
            }
            Text("$pct%", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.02).em, color = LlColor.Ink)
        }

        if (upcoming.isNotEmpty()) {
            Slabel("Upcoming", upcoming.size, accent = true)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                upcoming.forEach { MilestoneCard(it, today, isNearest = it.id == nearestId) }
            }
        }
        if (completed.isNotEmpty()) {
            Slabel("Completed", completed.size)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                completed.forEach { MilestoneCard(it, today, isNearest = false) }
            }
        }
    }
}

@Composable
private fun MilestoneCard(m: Milestone, today: LocalDate, isNearest: Boolean) {
    val done = m.complete()
    val days = m.dueDate?.let { ChronoUnit.DAYS.between(today, it) }
    val soon = !done && (isNearest || (days != null && days <= 3))
    val week = !done && !soon && days != null && days in 4..7
    val meterColor = when {
        done -> LlColor.VerdantMid
        soon -> ClaretMidFix
        week -> LlColor.AmberMid
        m.tasksDone > 0 -> LlColor.SlateMid
        else -> LlColor.InkFaint
    }
    val frac = if (m.tasksTotal == 0) 0f else m.tasksDone.toFloat() / m.tasksTotal

    Box(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(if (done) LlColor.Paper1 else LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
    ) {
        if (isNearest) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(LlDimen.HeroStripeWidth).background(LlColor.Claret))
        Column(Modifier.padding(start = if (isNearest) 18.dp else 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(m.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 21.sp, color = if (done) LlColor.InkMuted else LlColor.Ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(9.dp))
                MsStatusPill(m)
            }
            // due line
            val (icon, label, color) = dueGlance(m, today, done, soon, week, days)
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
                Text(label, fontFamily = Inter, fontSize = 10.5.sp, color = color)
            }
            stripHtmlM(m.description)?.let {
                Text(it, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 16.sp, color = LlColor.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meter(Modifier.weight(1f), frac, meterColor)
                Text("${m.tasksDone}/${m.tasksTotal} tasks", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = LlColor.InkMuted)
            }
        }
    }
}

@androidx.compose.runtime.Composable private fun dueGlance(m: Milestone, today: LocalDate, done: Boolean, soon: Boolean, week: Boolean, days: Long?): Triple<ImageVector, String, Color> {
    if (done) return Triple(Icons.Outlined.CheckCircle, m.dueDate?.let { "Reached ${it.format(dayFmt)}" } ?: "Completed", LlColor.InkFaint)
    val d = m.dueDate ?: return Triple(Icons.Outlined.Schedule, "No due date set", LlColor.InkFaint)
    val label = when {
        days != null && days < 0 -> "Overdue · ${d.format(dayFmt)}"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due ${if (week) "this week · " else if (days != null && days <= 3) "in $days days · " else ""}${d.format(dowFmt)}"
    }
    val color = when { soon -> ClaretMidFix; week -> LlColor.AmberMid; else -> LlColor.InkMuted }
    return Triple(if (soon) Icons.Outlined.Flag else Icons.Outlined.Event, label, color)
}

@Composable
private fun MsStatusPill(m: Milestone) {
    val (bg, fg, label) = when {
        m.complete() -> Triple(LlColor.VerdantSoft, LlColor.Verdant, "Complete")
        m.tasksDone > 0 -> Triple(LlColor.SlateSoft, LlColor.SlateMid, "In Progress")
        else -> Triple(LlColor.Paper2, LlColor.InkMuted, "Not Started")
    }
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(fg))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.02.em, color = fg)
    }
}

// --- shared bits ----------------------------------------------------------

@Composable
private fun SubBar(title: String, count: Int?, contextLine: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.015).em, color = LlColor.Ink)
            count?.let {
                Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text("$it", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = LlColor.InkMuted)
                }
            }
        }
        contextLine?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun Slabel(name: String, count: Int, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = if (accent) LlColor.Claret else LlColor.InkFaint)
        Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(if (accent) LlColor.ClaretSoft else LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = if (accent) ClaretMidFix else LlColor.InkMuted)
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun Meter(modifier: Modifier, frac: Float, color: Color) {
    Box(modifier.height(5.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun MsEmpty() {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.SlateSoft).border(LlDimen.HairlineWidth, LlColor.SlateBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Flag, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No milestones yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Your chamber will break this project into milestones — filing dates, approvals, deliverables — and they'll appear here with progress and due dates as work begins.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp))
    }
}

@Composable
private fun MsSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { SkM(Modifier.fillMaxWidth(0.6f), 13.dp); Spacer(Modifier.height(11.dp)); SkM(Modifier.fillMaxWidth(), 5.dp, 3.dp) }
            SkM(Modifier.width(44.dp), 24.dp, 6.dp)
        }
        Spacer(Modifier.height(20.dp))
        repeat(3) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 11.dp).fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card).padding(15.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SkM(Modifier.fillMaxWidth(0.55f), 16.dp, 6.dp); SkM(Modifier.width(78.dp), 18.dp, 7.dp) }
                Spacer(Modifier.height(11.dp)); SkM(Modifier.fillMaxWidth(0.42f), 11.dp)
                Spacer(Modifier.height(13.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { SkM(Modifier.weight(1f), 5.dp, 3.dp); SkM(Modifier.width(50.dp), 11.dp) }
            }
        }
    }
}

@Composable
private fun SkM(modifier: Modifier, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun MsError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Milestones Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun stripHtmlM(s: String?): String? {
    val t = s?.takeIf { it.isNotBlank() } ?: return null
    return t.replace(Regex("<[^>]+>"), "").replace("&#039;", "'").replace("&amp;", "&").replace("&quot;", "\"").replace("&middot;", "·").replace("&nbsp;", " ").trim().ifBlank { null }
}
