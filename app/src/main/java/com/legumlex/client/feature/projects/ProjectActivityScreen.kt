package com.legumlex.client.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.UploadFile
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
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
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

// -----------------------------------------------------------------------------
// "Client Project Activity.html" — the plain-language log for a project, pushed
// from the Activity drill-row. Day-grouped timeline (dot + connector + line +
// mono actor·time); dots coloured by kind (task slate, file amber, milestone
// claret, discussion violet). Read-only.
// -----------------------------------------------------------------------------

private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretSoftFix = Color(0xFFFDF2F4)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState
    data class Ready(val projectName: String, val projectRef: String, val items: List<ProjectActivityItem>) : ActivityUiState
    data class Error(val message: String) : ActivityUiState
}

class ProjectActivityViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { load() }
    fun load() { viewModelScope.launch { _state.value = ActivityUiState.Loading; fetch(false) } }
    fun refresh() { if (_isRefreshing.value) return; viewModelScope.launch { _isRefreshing.value = true; fetch(true); _isRefreshing.value = false } }

    private suspend fun fetch(preserve: Boolean) {
        try {
            coroutineScope {
                val detail = async { repo.detail(projectId).getOrThrow() }
                val act = async { repo.activity(projectId).getOrNull().orEmpty() }
                val d = detail.await()
                _state.value = ActivityUiState.Ready(d.summary.name, "PRJ-${d.summary.id}", act.await())
            }
        } catch (e: Throwable) {
            if (!(preserve && _state.value is ActivityUiState.Ready)) {
                _state.value = ActivityUiState.Error(
                    when (e) {
                        is HttpException -> when (e.code()) { 401, 403 -> "Your session expired. Sign in again."; 404 -> "This project is no longer available."; in 500..599 -> "Server error. Try again in a moment."; else -> "Couldn't load activity (HTTP ${e.code()})." }
                        else -> "Couldn't reach the server. Check your connection."
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectActivityScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ProjectActivityViewModel = viewModel(
        key = "activity-$projectId",
        factory = viewModelFactory { initializer { ProjectActivityViewModel(app, projectId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    Scaffold(containerColor = LlColor.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                val ready = state as? ActivityUiState.Ready
                SubBar(ready?.let { "${it.projectRef} · ${it.projectName}" }, onBack)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val s = state) {
                        is ActivityUiState.Loading -> ActSkeleton()
                        is ActivityUiState.Error -> ActError(s.message, vm::load)
                        is ActivityUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                            ActivityReady(s.items)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityReady(items: List<ProjectActivityItem>) {
    if (items.isEmpty()) { ActEmpty(); return }
    val today = remember { LocalDate.now() }
    // Activity comes newest-first; groupBy preserves that day order.
    val groups = remember(items) { items.groupBy { it.dateAdded?.toLocalDate() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        var first = true
        groups.forEach { (date, dayItems) ->
            val isFirst = first; first = false
            item("h-${date ?: "x"}") { DayHeader(dayLabel(date, today), date == today, isFirst) }
            item("g-${date ?: "x"}") {
                Column(Modifier.padding(start = 22.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)) {
                    dayItems.forEachIndexed { i, a -> TimelineItem(a, last = i == dayItems.lastIndex) }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(a: ProjectActivityItem, last: Boolean) {
    val kind = activityKind(a.descriptionKey)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
            Box(Modifier.padding(top = 2.dp).size(14.dp).clip(RoundedCornerShape(50)).background(Color.White).border(2.dp, kind.color, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(kind.color))
            }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(LlColor.Paper2))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 4.dp else 16.dp)) {
            Row(Modifier.clip(RoundedCornerShape(5.dp)).background(kind.bg).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(kind.icon, null, tint = kind.color, modifier = Modifier.size(11.dp))
                Text(kind.label.uppercase(), fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.1.em, color = kind.color)
            }
            Spacer(Modifier.height(7.dp))
            Text(humanize(a.descriptionKey), fontFamily = SourceSans, fontSize = 12.5.sp, lineHeight = 18.sp, color = LlColor.Ink)
            val meta = listOfNotNull(a.actorName, a.dateAdded?.format(timeFmt)).joinToString(" · ")
            if (meta.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(meta, fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint) }
        }
    }
}

private data class ActKind(val color: Color, val bg: Color, val icon: ImageVector, val label: String)

private fun activityKind(key: String): ActKind {
    val k = key.lowercase()
    return when {
        k.contains("milestone") -> ActKind(ClaretMidFix, ClaretSoftFix, Icons.Outlined.Flag, "Milestone")
        k.contains("file") || k.contains("attach") -> ActKind(LlColor.AmberMid, LlColor.AmberSoft, Icons.Outlined.UploadFile, "File")
        k.contains("discussion") || k.contains("comment") -> ActKind(LlColor.Violet, LlColor.VioletSoft, Icons.Outlined.Forum, "Discussion")
        else -> ActKind(LlColor.SlateMid, LlColor.SlateSoft, Icons.Outlined.TaskAlt, "Task")
    }
}

// --- shared bits ----------------------------------------------------------

@Composable
private fun SubBar(contextLine: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.015).em, color = LlColor.Ink)
        }
        contextLine?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun DayHeader(label: String, today: Boolean, first: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (first) 14.dp else 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.14.em, color = if (today) LlColor.Claret else LlColor.InkFaint)
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun ActEmpty() {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.SlateSoft).border(LlDimen.HairlineWidth, Color(0xFFD6E3F3), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.History, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Nothing logged yet", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("As work begins — tasks created, files shared, milestones reached — every step is recorded here, newest first, in plain language.", fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp))
    }
}

@Composable
private fun ActSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        repeat(2) { g ->
            SkA(Modifier.padding(start = 16.dp, top = if (g == 0) 14.dp else 18.dp).width(96.dp), 11.dp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(start = 22.dp, end = 16.dp)) {
                repeat(2) { i ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
                            Box(Modifier.padding(top = 2.dp).size(14.dp).clip(RoundedCornerShape(50)).background(LlColor.Paper2))
                            if (i == 0) Box(Modifier.width(1.5.dp).weight(1f).background(LlColor.Paper2))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
                            SkA(Modifier.width(60.dp), 13.dp); Spacer(Modifier.height(9.dp)); SkA(Modifier.fillMaxWidth(0.85f), 12.dp); Spacer(Modifier.height(7.dp)); SkA(Modifier.fillMaxWidth(0.44f), 10.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkA(modifier: Modifier, height: androidx.compose.ui.unit.Dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(5.dp)).background(LlColor.Paper2))
}

@Composable
private fun ActError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load activity", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// --- helpers --------------------------------------------------------------

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun dayLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Earlier"
    today -> "Today · ${date.format(dayFmt)}"
    today.minusDays(1) -> "Yesterday · ${date.format(dayFmt)}"
    else -> if (date.year == today.year) date.format(dayFmt) else date.format(dayYearFmt)
}

private fun humanize(key: String): String =
    key.removePrefix("project_activity_").removePrefix("not_").replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Activity" }
