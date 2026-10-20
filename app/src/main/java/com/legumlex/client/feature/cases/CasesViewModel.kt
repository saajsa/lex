package com.legumlex.client.feature.cases

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface CasesUiState {
    data object Loading : CasesUiState
    // The Matters list always fetches the full set; the screen filters + bands
    // it client-side (so all chip counts, the cross-matter hearing spotlight and
    // the smart sections derive from one dataset).
    data class Ready(val cases: List<CaseSummary>) : CasesUiState
    data class Error(val message: String) : CasesUiState
}

class CasesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CasesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<CasesUiState>(CasesUiState.Loading)
    val state: StateFlow<CasesUiState> = _state.asStateFlow()

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
            _state.value = CasesUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    private suspend fun fetch(preserveCurrent: Boolean) {
        repo.list(CaseFilter.ALL)
            .onSuccess { _state.value = CasesUiState.Ready(it) }
            .onFailure { e ->
                val msg = when (e) {
                    is HttpException -> when (e.code()) {
                        401, 403   -> "Your session has expired. Please sign in again."
                        in 500..599 -> "A server error occurred. Please try again shortly."
                        else        -> "Your matters could not be loaded (error ${e.code()})."
                    }
                    else -> "The server could not be reached. Please check your connection and try again."
                }
                if (!(preserveCurrent && _state.value is CasesUiState.Ready)) {
                    _state.value = CasesUiState.Error(msg)
                }
            }
    }
}
