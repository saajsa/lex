package com.legumlex.client.feature.cases

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface CaseDetailUiState {
    data object Loading : CaseDetailUiState
    data class Ready(
        val detail: CaseDetail,
        val timeline: List<TimelineEvent>,
    ) : CaseDetailUiState
    data class Error(val message: String) : CaseDetailUiState
}

class CaseDetailViewModel(
    app: Application,
    val caseId: String,
) : AndroidViewModel(app) {
    private val repo = CasesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<CaseDetailUiState>(CaseDetailUiState.Loading)
    val state: StateFlow<CaseDetailUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = CaseDetailUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch(preserveCurrent = true)
            _isRefreshing.value = false
        }
    }

    private suspend fun fetch(preserveCurrent: Boolean) {
        try {
            coroutineScope {
                // Detail is the canary; timeline is best-effort.
                val detailDeferred   = async { repo.detail(caseId).getOrThrow() }
                val timelineDeferred = async { repo.timeline(caseId).getOrNull() ?: emptyList() }
                val detail   = detailDeferred.await()
                val timeline = timelineDeferred.await()
                _state.value = CaseDetailUiState.Ready(detail, timeline)
            }
        } catch (e: Throwable) {
            val msg = when (e) {
                is HttpException -> when (e.code()) {
                    401, 403   -> "Your session has expired. Please sign in again."
                    404        -> "This case is no longer available."
                    in 500..599 -> "A server error occurred. Please try again shortly."
                    else        -> "This matter could not be loaded (error ${e.code()})."
                }
                else -> "The server could not be reached. Please check your connection and try again."
            }
            if (!(preserveCurrent && _state.value is CaseDetailUiState.Ready)) {
                _state.value = CaseDetailUiState.Error(msg)
            }
        }
    }
}
