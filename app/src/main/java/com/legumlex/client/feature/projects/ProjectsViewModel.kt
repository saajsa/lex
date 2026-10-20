package com.legumlex.client.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Ready(val projects: List<ProjectSummary>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

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
            _state.value = ProjectsUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    // Load every project; the screen bands & filters client-side (Due this week /
    // Active / Finished) and needs the full set for accurate filter counts.
    private suspend fun fetch(preserveCurrent: Boolean) {
        repo.list(ProjectFilter.ALL)
            .onSuccess { _state.value = ProjectsUiState.Ready(it) }
            .onFailure { e ->
                val msg = when (e) {
                    is HttpException -> when (e.code()) {
                        401         -> "Your session has expired. Please sign in again."
                        403         -> forbiddenMessage(e, "Your account does not include projects. Ask the firm to give you access.")
                        in 500..599 -> "A server error occurred. Please try again shortly."
                        else        -> "Your projects could not be loaded (error ${e.code()})."
                    }
                    else -> "The server could not be reached. Please check your connection and try again."
                }
                if (!(preserveCurrent && _state.value is ProjectsUiState.Ready)) {
                    _state.value = ProjectsUiState.Error(msg)
                }
            }
    }
}
