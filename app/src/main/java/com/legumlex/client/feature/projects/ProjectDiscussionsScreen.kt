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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Project Discussions.html" — threads on a project, pushed from the
// Discussions drill-row. Two views, no tabs: a list of threads, and a single
// thread with its conversation + pinned composer. Threads are chamber-opened;
// the client reads and replies. Same message idiom as the task conversation.
// -----------------------------------------------------------------------------

private val ClaretMidFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretMid
private val ClaretSoftFix: Color @androidx.compose.runtime.Composable get() = LlColor.ClaretSoft

sealed interface DiscUiState {
    data object Loading : DiscUiState
    data class Ready(val projectName: String, val projectRef: String, val discussions: List<DiscussionSummary>) : DiscUiState
    data class Error(val message: String) : DiscUiState
}

class ProjectDiscussionsViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<DiscUiState>(DiscUiState.Loading)
    val state: StateFlow<DiscUiState> = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _thread = MutableStateFlow<DiscussionDetail?>(null)
    val thread: StateFlow<DiscussionDetail?> = _thread.asStateFlow()
    /** The thread that failed to load, so the screen stops waiting for it. */
    val _failed = MutableStateFlow<String?>(null)
    private val _threadLoading = MutableStateFlow(false)
    val threadLoading: StateFlow<Boolean> = _threadLoading.asStateFlow()
    private val _posting = MutableStateFlow(false)
    val posting: StateFlow<Boolean> = _posting.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    init { load() }

    fun load() { viewModelScope.launch { _state.value = DiscUiState.Loading; fetch(false) } }
    fun refresh() { if (_isRefreshing.value) return; viewModelScope.launch { _isRefreshing.value = true; fetch(true); _isRefreshing.value = false } }

    private suspend fun fetch(preserve: Boolean) {
        try {
            coroutineScope {
                val detail = async { repo.detail(projectId).getOrThrow() }
                val disc = async { repo.discussions(projectId).getOrNull().orEmpty() }
                val d = detail.await()
                _state.value = DiscUiState.Ready(d.summary.name, "PRJ-${d.summary.id}", disc.await())
            }
        } catch (e: Throwable) {
            if (!(preserve && _state.value is DiscUiState.Ready)) {
                _state.value = DiscUiState.Error(
                    when (e) {
                        is HttpException -> when (e.code()) { 401, 403 -> "Your session expired. Sign in again."; 404 -> "This project is no longer available."; in 500..599 -> "Server error. Try again in a moment."; else -> "Couldn't load discussions (HTTP ${e.code()})." }
                        else -> "Couldn't reach the server. Check your connection."
                    },
                )
            }
        }
    }

    fun openThread(id: String) {
        viewModelScope.launch {
            _threadLoading.value = true
            repo.discussion(id).onSuccess { _thread.value = it }.onFailure { _toast.value = "Couldn't open the discussion."; _failed.value = id }
            _threadLoading.value = false
        }
    }

    fun closeThread() { _thread.value = null }

    fun postReply(id: String, content: String) {
        val text = content.trim()
        if (text.isEmpty() || _posting.value) return
        viewModelScope.launch {
            _posting.value = true
            repo.postDiscussionComment(id, text)
                .onSuccess { added ->
                    _thread.update { t -> if (t != null && t.id == id) t.copy(comments = t.comments + added) else t }
                    _state.update { cur -> if (cur is DiscUiState.Ready) cur.copy(discussions = cur.discussions.map { if (it.id == id) it.copy(totalComments = it.totalComments + 1) else it }) else cur }
                }
                .onFailure { _toast.value = "Couldn't post your reply. Try again." }
            _posting.value = false
        }
    }
}

// One composable, two routes: the list (discussionId == null) and a single thread
// (discussionId set). Back at every level is the app-wide header — no in-screen back.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDiscussionsScreen(
    projectId: String,
    discussionId: String? = null,
    onBack: () -> Unit = {},
    onOpenThread: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ProjectDiscussionsViewModel = viewModel(
        key = "disc-$projectId",
        factory = viewModelFactory { initializer { ProjectDiscussionsViewModel(app, projectId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val thread by vm.thread.collectAsState()
    val threadLoading by vm.threadLoading.collectAsState()
    val failedThread by vm._failed.collectAsState()
    val posting by vm.posting.collectAsState()
    val toast by vm.toast.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }
    LaunchedEffect(discussionId) { if (discussionId != null) vm.openThread(discussionId) }

    val ready = state as? DiscUiState.Ready

    Scaffold(containerColor = LlColor.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                if (discussionId != null) {
                    Heading(thread?.subject ?: "Discussion", null, "Discussion · ${ready?.projectRef ?: ""} · ${thread?.comments?.size ?: 0} messages")
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            !threadLoading && thread == null && failedThread == discussionId ->
                                com.legumlex.client.ui.components.LexEmpty(
                                    "The Discussion Could Not Be Opened",
                                    "Please check your connection and try again.",
                                    action = { androidx.compose.material3.TextButton(onClick = { vm._failed.value = null; vm.openThread(discussionId) }) {
                                        androidx.compose.material3.Text("Try Again", color = LlColor.Ink) } },
                                )
                            threadLoading || thread == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LlColor.InkMuted) }
                            else -> ThreadContent(thread!!)
                        }
                    }
                    Composer(posting) { vm.postReply(discussionId, it) }
                } else {
                    Heading("Discussions", ready?.discussions?.size, ready?.let { "${it.projectRef} · ${it.projectName}" })
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val s = state) {
                            is DiscUiState.Loading -> DiscSkeleton()
                            is DiscUiState.Error -> DiscError(s.message, vm::load)
                            is DiscUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                                DiscList(s.discussions) { id -> onOpenThread(id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// List
// =============================================================================

@Composable
private fun DiscList(discussions: List<DiscussionSummary>, onOpen: (String) -> Unit) {
    if (discussions.isEmpty()) { DiscEmpty(); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        discussions.forEach { ThreadRow(it) { onOpen(it.id) } }
    }
}

@Composable
private fun ThreadRow(d: DiscussionSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Forum, null, tint = LlColor.InkMuted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(d.subject, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.012).em, lineHeight = 20.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                (d.lastActivity ?: d.dateCreated)?.let { Text(relativeAgo(it), fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val meta = listOfNotNull(d.authorName, "${d.totalComments} ${if (d.totalComments == 1) "reply" else "replies"}").joinToString(" · ")
                Text(meta, fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = LlColor.BorderStrong, modifier = Modifier.size(19.dp))
    }
}

// =============================================================================
// Thread
// =============================================================================

@Composable
private fun ThreadContent(d: DiscussionDetail) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 10.dp)) {
        // opening card
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(15.dp)) {
            Text("STARTED" + (d.dateCreated?.let { " · ${relativeAgo(it)}" } ?: ""), fontFamily = Inter, fontSize = 9.sp, letterSpacing = 0.13.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(6.dp))
            Text(stripHtmlD(d.description) ?: d.subject, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.012).em, lineHeight = 21.sp, color = LlColor.Ink)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (d.comments.isEmpty()) {
                Text("No replies yet.", fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 16.dp))
            } else {
                d.comments.forEachIndexed { i, c ->
                    Message(c)
                    if (i < d.comments.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(26.dp).height(1.dp).background(LlColor.Paper2))
                    Text("Replies are shared with your chamber", fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 0.06.em, color = LlColor.InkFaint)
                    Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
                }
            }
        }
    }
}

@Composable
private fun Message(c: DiscussionComment) {
    val you = c.authorType == "contact"
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (you) LlColor.Claret else LlColor.Slate), contentAlignment = Alignment.Center) {
            Text(initialsOfD(c.authorName, you), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.authorName?.takeIf { it.isNotBlank() } ?: if (you) "You" else "Staff", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = LlColor.Ink)
                Text(if (you) "YOU" else "ADVOCATE", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.06.em, color = if (you) ClaretMidFix else LlColor.SlateMid,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (you) ClaretSoftFix else LlColor.SlateSoft).padding(horizontal = 6.dp, vertical = 1.dp))
                Spacer(Modifier.weight(1f))
                c.created?.let { Text(relativeAgo(it), fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint) }
            }
            Text(stripHtmlD(c.content) ?: c.content, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp, color = LlColor.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(posting: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Row(Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Write a reply…", fontFamily = Inter, fontSize = 13.sp, color = LlColor.InkFaint) }, modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(12.dp))
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

// --- shared bits ----------------------------------------------------------

@Composable
private fun Heading(title: String, count: Int?, contextLine: String?) {
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.015).em, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            count?.let {
                Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text("$it", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = LlColor.InkMuted)
                }
            }
        }
        contextLine?.takeIf { it.isNotBlank() }?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun DiscEmpty() {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.SlateSoft).border(LlDimen.HairlineWidth, LlColor.SlateBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Forum, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No discussions yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("When your chamber needs your input — a clause to confirm, a document to send — they'll open a thread here. You'll be notified, and you can reply right away.", fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 288.dp))
    }
}

@Composable
private fun DiscSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)).padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkD(Modifier.size(38.dp), 38.dp, 11.dp)
                Column(Modifier.weight(1f)) { SkD(Modifier.fillMaxWidth(0.72f), 15.dp, 6.dp); Spacer(Modifier.height(10.dp)); SkD(Modifier.fillMaxWidth(0.5f), 10.dp) }
            }
        }
    }
}

@Composable
private fun SkD(modifier: Modifier, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun DiscError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load discussions", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

// --- helpers --------------------------------------------------------------

private val dayYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun relativeAgo(ts: LocalDateTime): String {
    val mins = ChronoUnit.MINUTES.between(ts, LocalDateTime.now())
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d ago"
        else -> ts.toLocalDate().format(dayYearFmt)
    }
}

private fun initialsOfD(name: String?, you: Boolean): String =
    name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifBlank { if (you) "Y" else "?" } ?: if (you) "Y" else "?"

private fun stripHtmlD(s: String?): String? {
    val t = s?.takeIf { it.isNotBlank() } ?: return null
    return t.replace(Regex("<[^>]+>"), "").replace("&#039;", "'").replace("&amp;", "&").replace("&quot;", "\"").replace("&middot;", "·").replace("&nbsp;", " ").trim().ifBlank { null }
}
