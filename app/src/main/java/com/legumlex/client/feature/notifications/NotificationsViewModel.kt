package com.legumlex.client.feature.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class NotifFilter { ALL, UNREAD }

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NotificationsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _filter = MutableStateFlow(NotifFilter.ALL)
    val filter: StateFlow<NotifFilter> = _filter.asStateFlow()

    // Source of truth — the full feed; the visible state is derived from it.
    private var all: List<AppNotification> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = NotificationsUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch(preserve = true)
            _isRefreshing.value = false
        }
    }

    fun setFilter(f: NotifFilter) {
        if (_filter.value == f) return
        _filter.value = f
        render()
    }

    fun markRead(id: String) {
        val target = all.firstOrNull { it.id == id } ?: return
        if (target.isRead) return
        all = all.map { if (it.id == id) it.copy(isRead = true) else it }
        NotificationHub.decrement()
        render()
        viewModelScope.launch { repo.markRead(id) }
    }

    fun markAllRead() {
        if (all.none { !it.isRead }) return
        all = all.map { it.copy(isRead = true) }
        NotificationHub.clear()
        render()
        viewModelScope.launch { repo.markAllRead() }
    }

    private suspend fun fetch(preserve: Boolean = false) {
        repo.load()
            .onSuccess { ready ->
                all = ready.bands.flatMap { it.items }
                NotificationHub.set(ready.unreadCount)
                render()
            }
            .onFailure { e ->
                if (preserve && _state.value is NotificationsUiState.Ready) return@onFailure
                _state.value = NotificationsUiState.Error(messageFor(e))
            }
    }

    private fun render() {
        val visible = when (_filter.value) {
            NotifFilter.ALL    -> all
            NotifFilter.UNREAD -> all.filter { !it.isRead }
        }
        val grouped = repo.group(visible)
        // unreadCount always reflects the full feed, not the filtered view.
        _state.value = grouped.copy(unreadCount = all.count { !it.isRead })
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            401         -> "Your session expired. Sign in again."
            403         -> "Your account doesn't include notifications yet. Ask the firm to give you access."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn't reach the server (HTTP ${e.code()})."
        }
        else -> "Couldn't reach the server. Check your connection."
    }
}
