package com.legumlex.client.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The queue behind the header's queue button; reads and writes ActionHub. */
class ActionQueueViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HomeRepository(ApiClient.get(app))

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val dismissJobs = mutableMapOf<String, Job>()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            ActionHub.refresh(getApplication())
                .onSuccess { _error.value = null }
                .onFailure { if (ActionHub.bundle.value == null) _error.value = "Your queue could not be loaded." }
            _loading.value = false
        }
    }

    fun dismiss(item: ActionItem) {
        ActionHub.bundle.value?.let { b -> ActionHub.set(b.items.filterNot { it.dismissKey() == item.dismissKey() }.toBundle()) }
        dismissJobs[item.dismissKey()] = viewModelScope.launch { repo.dismissAction(item.id) }
    }

    fun restore(item: ActionItem) {
        val pending = dismissJobs.remove(item.dismissKey())
        viewModelScope.launch {
            pending?.join()
            repo.restoreAction(item.id)
            ActionHub.refresh(getApplication())
        }
    }
}

/**
 * Everything waiting on the client, behind the header's queue button (it was
 * a section of Home). Swipe a row away to dismiss it, with Undo.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActionQueueScreen(
    onOpenInvoice: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAgreement: (String) -> Unit,
    onOpenAhcFunnel: () -> Unit,
    onAskNew: () -> Unit,
    vm: ActionQueueViewModel = viewModel(),
) {
    val bundle by ActionHub.bundle.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val routes = ActionRoutes(onOpenInvoice, onOpenContract, onOpenConsultation, onOpenCase,
        onOpenNotice, onOpenTask, onOpenProject, onOpenAgreement, onOpenAhcFunnel)
    val dismiss: (ActionItem) -> Unit = { item ->
        vm.dismiss(item)
        scope.launch {
            val r = snackbar.showSnackbar("Removed from your queue.", actionLabel = "Undo", withDismissAction = false)
            if (r == SnackbarResult.ActionPerformed) vm.restore(item)
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = loading && bundle != null, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            val b = bundle
            when {
                b != null -> QueueDays(b.items, onOpen = { openActionItem(ctx, it, routes) }, onDismiss = dismiss, onAskNew = onAskNew)
                error != null -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
                    TextButton(onClick = vm::refresh) { Text("Try Again") }
                }
                else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private val Short = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

/** The strip's "Later": everything due after the month it shows. */
private const val LATER = Long.MAX_VALUE

/**
 * The queue as a day timeline: today and the fortnight ahead. What is overdue
 * or has no date sits under today. The most pressing item of the day is the
 * dark card; critical and warning items fill their circles. Swipe a row away
 * to dismiss it, with Undo.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QueueDays(
    items: List<ActionItem>,
    onOpen: (ActionItem) -> Unit,
    onDismiss: (ActionItem) -> Unit,
    onAskNew: () -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    // A month ahead day by day; anything due after it is under "Later".
    val lastDay = today.plusDays(29)
    val keyOf: (ActionItem) -> Long = { i ->
        val d = i.dueAt?.takeIf { d -> d.isAfter(today) } ?: today
        if (d.isAfter(lastDay)) LATER else d.toEpochDay()
    }
    val byDay = remember(items) { items.groupBy(keyOf) }
    val days = remember(today) { generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList() }
    var day by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(today.toEpochDay()) }
    val later = day == LATER
    val selected = if (later) null else java.time.LocalDate.ofEpochDay(day)
    val sevRank = { i: ActionItem -> when (i.severity) { ActionSeverity.Critical -> 0; ActionSeverity.Warning -> 1; ActionSeverity.Info -> 2 } }
    val list = (byDay[day] ?: emptyList()).sortedWith(compareBy(sevRank).thenBy { it.dueAt ?: java.time.LocalDate.MAX })
    val featured = list.firstOrNull()

    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        item("head") {
            Spacer(Modifier.height(12.dp))
            val count: @Composable () -> Unit = {
                if (items.isNotEmpty()) Text("${items.size} Waiting", style = MaterialTheme.typography.labelLarge, color = Lex.colors.mutedForeground)
            }
            if (later) com.legumlex.client.ui.components.DayHead(lastDay, Modifier.padding(horizontal = 20.dp), title = "Later",
                dateLine = "After " + com.legumlex.client.ui.components.longDate(lastDay), trailing = count)
            else com.legumlex.client.ui.components.DayHead(selected!!, Modifier.padding(horizontal = 20.dp), trailing = count)
            Spacer(Modifier.height(12.dp))
            com.legumlex.client.ui.components.DayStrip(
                days = days,
                selected = selected,
                marked = byDay.keys.filter { it != LATER }.map { java.time.LocalDate.ofEpochDay(it) }.toSet(),
                onSelect = { day = it.toEpochDay() },
                after = if (byDay.containsKey(LATER)) "Later" else null,
                edge = if (later) 1 else 0,
                onEdge = { day = LATER },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (items.isEmpty()) item("none") {
            com.legumlex.client.ui.components.DayEmpty("Nothing is waiting on you. When the firm needs something from you, it appears here.")
            TextButton(onClick = onAskNew, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Ask the Advocates") }
        } else if (list.isEmpty()) item("empty") {
            com.legumlex.client.ui.components.DayEmpty("Nothing is due on this day.")
        }
        itemsIndexed(list, key = { _, i -> i.dismissKey() }) { idx, item ->
            val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { v -> if (v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) { onDismiss(item); true } else false },
            )
            androidx.compose.material3.SwipeToDismissBox(state = state, enableDismissFromStartToEnd = false, backgroundContent = {}) {
                Box(Modifier.background(Lex.colors.background)) {
                    com.legumlex.client.ui.components.TimelineItem(
                        title = item.title,
                        time = when {
                            item.dueAt == null -> "No Date"
                            item.dueAt.isBefore(today) -> "Overdue · " + Short.format(item.dueAt)
                            item.dueAt == today -> "Due Today"
                            item.dueAt.year != today.year -> "Due " + Short.format(item.dueAt) + " " + item.dueAt.year
                            else -> "Due " + Short.format(item.dueAt)
                        },
                        body = item.subtitle,
                        live = item.severity != ActionSeverity.Info,
                        featured = item === featured,
                        first = idx == 0,
                        last = idx == list.lastIndex,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
        item("end") { Spacer(Modifier.height(24.dp)) }
    }
}
