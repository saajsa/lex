package com.legumlex.client.feature.hearings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface HearingDetailUiState {
    data object Loading : HearingDetailUiState
    data class Ready(val hearing: Hearing) : HearingDetailUiState
    data class Error(val message: String) : HearingDetailUiState
}

class HearingDetailViewModel(
    app: Application,
    val hearingId: String,
) : AndroidViewModel(app) {
    private val repo = HearingsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<HearingDetailUiState>(HearingDetailUiState.Loading)
    val state: StateFlow<HearingDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HearingDetailUiState.Loading
            repo.detail(hearingId)
                .onSuccess { _state.value = HearingDetailUiState.Ready(it) }
                .onFailure { e ->
                    val msg = when (e) {
                        is HttpException -> when (e.code()) {
                            401, 403   -> "Your session has expired. Please sign in again."
                            404        -> "This hearing is no longer available."
                            in 500..599 -> "A server error occurred. Please try again shortly."
                            else        -> "This hearing could not be loaded (error ${e.code()})."
                        }
                        else -> "The server could not be reached. Please check your connection and try again."
                    }
                    _state.value = HearingDetailUiState.Error(msg)
                }
        }
    }
}
