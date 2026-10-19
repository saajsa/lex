package com.legumlex.client.feature.digital_office

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped DoWaitroomViewModel — shared between the home banner and the
 * waitroom sheet so the two surfaces stay in lockstep. Without this each
 * screen would get its own NavBackStackEntry-scoped instance and they'd race.
 */
@Composable
fun sharedDoWaitroomViewModel(): DoWaitroomViewModel {
    val owner = LocalContext.current as ViewModelStoreOwner
    return viewModel(viewModelStoreOwner = owner)
}

/**
 * Backs the client app's waitroom surfaces. The data layer polls every 15 s
 * (cheap; one query). The "you're up" cue is also delivered via Pusher Beams
 * push to interest contact-{id}, so the user is reachable even when the app
 * is in the background. When they tap the push the app routes to the home
 * screen and the banner picks them up from the next poll tick.
 *
 * Public surface:
 *   rows          — full active queue rows for this client
 *   promoted      — the single row whose 5-min pay+intake window is open
 *                   (drives the loud home banner + the action sheet)
 *   navigateToPay — fired when join() returns cabin_free, telling the screen
 *                   to send the user straight to the booking flow instead
 *   event         — toast strings for non-fatal errors / confirmations
 */
class DoWaitroomViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DoRepository(ApiClient.get(app))

    private val _rows = MutableStateFlow<List<DoWaitroomRow>>(emptyList())
    val rows: StateFlow<List<DoWaitroomRow>> = _rows.asStateFlow()

    /** Whichever row is currently prompted (head with active 5-min window), or null. */
    val promoted: DoWaitroomRow? get() = _rows.value.firstOrNull { it.status == DoWaitroomStatus.PROMPTED }

    private val _event = MutableStateFlow<String?>(null)
    val event: StateFlow<String?> = _event.asStateFlow()

    /**
     * Fired when join() succeeded but the server told us the cabin is free —
     * the screen subscribes to this and routes the user straight to the
     * booking flow rather than queueing them behind no one.
     */
    private val _bookDirectly = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val bookDirectly: SharedFlow<Int> = _bookDirectly.asSharedFlow()

    private var pollJob: Job? = null

    init { startPolling() }

    fun clearEvent() { _event.value = null }

    fun refresh() {
        viewModelScope.launch {
            repo.listWaitroom()
                .onSuccess { _rows.value = it }
                .onFailure { Log.e(TAG, "waitroom load failed", it) }
        }
    }

    /**
     * Join an advocate's queue. Routes:
     *   success         → row added to _rows, banner appears on next poll
     *   cabin_free      → emit bookDirectly so the screen sends user to /sessions
     *   advocate_offline → toast
     *   other           → toast with the server code
     */
    fun join(advocateStaffId: Int) {
        viewModelScope.launch {
            repo.joinWaitroom(advocateStaffId)
                .onSuccess { row ->
                    // Optimistic merge so the banner appears without waiting
                    // for the next poll tick.
                    val withoutDup = _rows.value.filterNot {
                        it.advocateStaffId == row.advocateStaffId &&
                            it.status in setOf(DoWaitroomStatus.WAITING, DoWaitroomStatus.PROMPTED)
                    }
                    _rows.value = withoutDup + row
                    _event.value = "Joined queue · position #${row.position}"
                }
                .onFailure { e ->
                    when (e.message) {
                        "cabin_free" -> {
                            _bookDirectly.tryEmit(advocateStaffId)
                            _event.value = "Cabin is free — opening booking…"
                        }
                        "advocate_offline" -> _event.value = "Advocate just stepped out."
                        else               -> _event.value = "Could not join queue: ${e.message ?: "try again"}"
                    }
                }
        }
    }

    fun leave(waitroomId: Int) {
        viewModelScope.launch {
            repo.leaveWaitroom(waitroomId)
                .onSuccess {
                    _rows.value = _rows.value.filterNot { it.waitroomId == waitroomId }
                    _event.value = "Left queue"
                }
                .onFailure {
                    Log.e(TAG, "waitroom leave failed", it)
                    _event.value = "Could not leave queue."
                }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                runCatching { refresh() }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DoWaitroom"
        const val POLL_INTERVAL_MS = 15_000L
    }
}
