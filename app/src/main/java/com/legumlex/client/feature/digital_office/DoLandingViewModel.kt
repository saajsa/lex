package com.legumlex.client.feature.digital_office

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.ChamberState
import com.legumlex.client.feature.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Digital Office landing screen — the "advocate profile" the
 * client reaches from the Home chamber card. Single fetch of the chamber
 * roster, then pick the requested advocate (or the first one) to render.
 *
 * Deliberately thin: no polling. The Home card already polls the chamber
 * every 30 s, so by the time the client lands here the data they saw is at
 * most a few seconds stale. The walk-in CTA hands off to DoIntake which
 * does its own fresh load before submitting.
 */
class DoLandingViewModel(
    app: Application,
    private val initialAdvocateStaffId: Int?,
) : AndroidViewModel(app) {

    private val repo = HomeRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<DoLandingUiState>(DoLandingUiState.Loading)
    val state: StateFlow<DoLandingUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = DoLandingUiState.Loading
            val chamber = repo.loadChamber()
            if (chamber == null) {
                _state.value = DoLandingUiState.Error("Chamber unavailable. Pull to retry.")
                return@launch
            }
            val pick = chamber.advocates.firstOrNull { it.staffId == initialAdvocateStaffId }
                ?: chamber.advocates.firstOrNull { !it.isBusy }
                ?: chamber.advocates.firstOrNull()
            _state.value = DoLandingUiState.Ready(chamber = chamber, advocate = pick)
        }
    }
}

sealed interface DoLandingUiState {
    data object Loading : DoLandingUiState
    data class Ready(val chamber: ChamberState, val advocate: ChamberAdvocate?) : DoLandingUiState
    data class Error(val message: String) : DoLandingUiState
}
