package com.legumlex.client.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
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

    Box(Modifier.fillMaxSize().padding(top = 0.dp)) {
        PullToRefreshBox(isRefreshing = loading && bundle != null, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                PageHeader(eyebrow = "Waiting On You", title = "Action Queue", modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(14.dp))
                val b = bundle
                when {
                    b != null -> ActionQueueBlock(
                        bundle = b,
                        onOpenInvoice = onOpenInvoice,
                        onOpenContract = onOpenContract,
                        onOpenConsultation = onOpenConsultation,
                        onOpenCase = onOpenCase,
                        onOpenNotice = onOpenNotice,
                        onOpenTask = onOpenTask,
                        onOpenProject = onOpenProject,
                        onOpenAgreement = onOpenAgreement,
                        onOpenAhcFunnel = onOpenAhcFunnel,
                        onAskNew = onAskNew,
                        onDismiss = { item ->
                            vm.dismiss(item)
                            scope.launch {
                                val r = snackbar.showSnackbar("Removed from your queue.", actionLabel = "Undo", withDismissAction = false)
                                if (r == SnackbarResult.ActionPerformed) vm.restore(item)
                            }
                        },
                        showHeader = false,
                    )
                    error != null -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
                        TextButton(onClick = vm::refresh) { Text("Try Again") }
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
