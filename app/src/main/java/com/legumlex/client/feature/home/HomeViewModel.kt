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
     * item stays gone across refreshes, restarts and devices.
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
        // if it fails the row is still hidden this session and the next swipe (or
        // app run) re-attempts; the server filter is the durable source of truth.
        viewModelScope.launch { repo.dismissAction(item.id) }
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

    // ─── Chamber polling (live "Open / Closed" + busy roster) ────────────
    //
    // The full dashboard is fetched once on entry; the chamber slice is the
    // only block that changes frequently enough to need live updates (an
    // advocate signing in, going busy, leaving). Poll JUST that endpoint
    // every 30s while the home screen is on top, and patch only
    // `state.data.chamber` so the rest of the UI doesn't churn.

    private var chamberPollJob: Job? = null

    /**
     * Called from the HomeScreen's DisposableEffect when the composable
     * enters composition. Safe to call repeatedly — re-entry cancels the
     * prior job so we never accumulate parallel pollers.
     *
     * Aggressive 5s cadence — the chamber endpoint is cheap (single
     * SELECT + small joins on the presence table) and the perceived "is
     * Chamber open right now?" latency is what makes the surface feel
     * alive vs dead. If the network can't keep up we'd see it; we don't.
     *
     * While the initial dashboard load is still in flight, the poller
     * short-waits (500ms) so the first chamber update lands within ~1s
     * of the initial load completing — NOT 30s later.
     */
    fun startChamberPolling(intervalMs: Long = 5_000L) {
        chamberPollJob?.cancel()
        chamberPollJob = viewModelScope.launch {
            while (isActive) {
                val ready = _state.value as? HomeUiState.Ready
                if (ready == null) {
                    // Dashboard still loading or errored — short-wait and
                    // re-check rather than burning a full interval.
                    delay(500)
                    continue
                }
                val fresh = repo.loadChamber()
                if (fresh != null) {
                    // Re-read the state under us — it may have changed
                    // (refresh in flight, error, etc.) since we issued the
                    // HTTP call; only patch a still-Ready state.
                    val now = _state.value as? HomeUiState.Ready
                    if (now != null) {
                        _state.value = now.copy(data = now.data.copy(chamber = fresh))
                        android.util.Log.d(
                            "ChamberPoll",
                            "is_open=${fresh.isOpen} online=${fresh.onlineCount} adv=${fresh.advocates.size}"
                        )
                    }
                }
                delay(intervalMs)
            }
        }
    }

    /** Cancel from the LaunchedEffect onDispose path. */
    fun stopChamberPolling() {
        chamberPollJob?.cancel()
        chamberPollJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopChamberPolling()
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
                        401, 403   -> "Your session expired. Sign in again."
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
                    _state.value = HomeUiState.Error(msg)
                }
            }
    }
}
