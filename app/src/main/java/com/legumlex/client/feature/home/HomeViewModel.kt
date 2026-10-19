package com.legumlex.client.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HomeRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Action-queue rows the client swiped away. Kept in-memory for instant,
    // flicker-free hiding (a pull-to-refresh or chamber poll re-emitting state
    // won't resurrect a just-dismissed row before the next fetch). The dismissal
    // is ALSO persisted server-side (see dismissAction) so it survives restarts
    // and refreshes — the server then omits it from action_items entirely.
    private val dismissedKeys = mutableSetOf<String>()

    init { load() }

    /**
     * Swipe-to-dismiss from the home action queue. Hides the row immediately
     * (optimistic, in-memory) and persists the dismissal server-side so the
     * item stays gone across refreshes, restarts and devices. The screen
     * offers Undo, which calls [restoreAction].
     */
    fun dismissAction(item: ActionItem) {
        dismissedKeys.add(item.dismissKey())
        val ready = _state.value as? HomeUiState.Ready
        if (ready != null) {
            _state.value = HomeUiState.Ready(
                ready.data.copy(actionItems = applyDismissed(ready.data.actionItems)),
            )
        }
        // Persist by the item's stable server id (e.g. "invoice_42"). Best-effort:
        // if it fails the row is still hidden this session.
        dismissJobs[item.dismissKey()] = viewModelScope.launch { repo.dismissAction(item.id) }
    }

    // In-flight dismissals, so an Undo waits for its dismissal to land first.
    private val dismissJobs = mutableMapOf<String, Job>()

    /** Undo a swipe: the server forgets the dismissal and the queue is read again. */
    fun restoreAction(item: ActionItem) {
        dismissedKeys.remove(item.dismissKey())
        val pending = dismissJobs.remove(item.dismissKey())
        viewModelScope.launch {
            pending?.join()
            repo.restoreAction(item.id)
            fetch(preserveCurrent = true)
        }
    }

    private fun applyDismissed(bundle: ActionItemsBundle): ActionItemsBundle {
        if (dismissedKeys.isEmpty()) return bundle
        return bundle.items.filterNot { it.dismissKey() in dismissedKeys }.toBundle()
    }

    /** First-load path: shows the skeleton on initial paint. */
    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            fetch()
        }
    }

    /**
     * Pull-to-refresh path: keeps current content on screen, shows the
     * built-in pull indicator instead of flicking back to a skeleton.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch(preserveCurrent = true)
            _isRefreshing.value = false
        }
    }

    // ─── Next-event polling ───────────────────────────────────────────────
    //
    // The dashboard is read on entry and on resume; the next event is the one
    // block that changes while Home sits open (an appointment turning joinable
    // is what raises the "Join Now" banner), so it alone is polled.

    private var nextEventPollJob: Job? = null

    /** Started when Home is on screen; re-entry replaces the prior job. */
    fun startNextEventPolling(intervalMs: Long = 30_000L) {
        nextEventPollJob?.cancel()
        nextEventPollJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (_state.value !is HomeUiState.Ready) continue
                repo.loadNextEvent().onSuccess { fresh ->
                    // Patch only a still-Ready state; a refresh may be in flight.
                    val now = _state.value as? HomeUiState.Ready
                    if (now != null && now.data.nextEvent != fresh) {
                        _state.value = now.copy(data = now.data.copy(nextEvent = fresh))
                    }
                }
            }
        }
    }

    fun stopNextEventPolling() {
        nextEventPollJob?.cancel()
        nextEventPollJob = null
    }

    /** Back on Home after acting elsewhere: read it again, keeping what is shown. */
    fun onResume() {
        if (_state.value is HomeUiState.Ready) refresh()
    }

    override fun onCleared() {
        super.onCleared()
        stopNextEventPolling()
    }

    private suspend fun fetch(preserveCurrent: Boolean = false) {
        repo.loadDashboard()
            .onSuccess {
                _state.value = HomeUiState.Ready(
                    it.copy(actionItems = applyDismissed(it.actionItems)),
                )
            }
            .onFailure { e ->
                val msg = when (e) {
                    is HttpException -> when (e.code()) {
                        401         -> "Your session expired. Sign in again."
                        403         -> "Your account doesn't include this yet. Ask the firm to give you access."
                        in 500..599 -> "Server error. Try again in a moment."
                        else        -> "Couldn't reach the server (HTTP ${e.code()})."
                    }
                    else -> "Couldn't reach the server. Check your connection."
                }
                // On refresh, keep the stale content visible rather than wiping
                // it for an error screen. Errors are shown only when there's no
                // existing data to fall back to.
                if (preserveCurrent && _state.value is HomeUiState.Ready) {
                    // Could surface a transient snackbar via a separate channel;
                    // for now the next manual refresh re-attempts.
                } else {
                    _state.value = HomeUiState.Error(msg, denied = (e as? HttpException)?.code() == 403)
                }
            }
    }
}
