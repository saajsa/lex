package com.legumlex.client.feature.consultations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

// Action-oriented bucketing per the consultations handoff. Section ordering
// is fixed: the client should always see what THEY need to do first, then
// what's active, then upcoming, then archive.
data class SmartSections(
    val needsAction: List<Consultation>,
    val active:      List<Consultation>,
    val scheduled:   List<Consultation>,
    val converted:   List<Consultation>,
    val closed:      List<Consultation>,
) {
    val total: Int get() =
        needsAction.size + active.size + scheduled.size + converted.size + closed.size

    val isEmpty: Boolean get() = total == 0
}

sealed interface SessionsUiState {
    data object Loading : SessionsUiState
    data class Ready(val sections: SmartSections) : SessionsUiState
    data class Error(val message: String) : SessionsUiState
}

class ConsultationsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConsultationsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<SessionsUiState>(SessionsUiState.Loading)
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { load() }

    fun retry() = load()

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch(preserveCurrent = true)
            _isRefreshing.value = false
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = SessionsUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    private suspend fun fetch(preserveCurrent: Boolean) {
        repo.list()
            .onSuccess { items ->
                _state.value = SessionsUiState.Ready(bucketize(items))
            }
            .onFailure { e ->
                val msg = e.friendlyMessage("Your consultations could not be loaded. Please try again.")
                if (!(preserveCurrent && _state.value is SessionsUiState.Ready)) {
                    _state.value = SessionsUiState.Error(msg)
                }
            }
    }

    private fun bucketize(items: List<Consultation>): SmartSections {
        val needsAction = mutableListOf<Consultation>()
        val active      = mutableListOf<Consultation>()
        val scheduled   = mutableListOf<Consultation>()
        val converted   = mutableListOf<Consultation>()
        val closed      = mutableListOf<Consultation>()

        for (c in items) {
            when {
                c.isConverted()        -> converted    += c
                c.isClosed()           -> closed       += c
                c.needsAction()        -> needsAction  += c
                c.isScheduledUpcoming() -> scheduled   += c
                c.isActive()           -> active       += c
                else                   -> active       += c
            }
        }

        // Newest first within each bucket; for scheduled, by scheduled time ascending.
        return SmartSections(
            needsAction = needsAction.sortedByDescending { it.createdAt },
            active      = active.sortedByDescending { it.createdAt },
            scheduled   = scheduled.sortedBy { it.scheduledAt() ?: it.createdAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDateTime() },
            converted   = converted.sortedByDescending { it.createdAt },
            closed      = closed.sortedByDescending { it.createdAt },
        )
    }
}
