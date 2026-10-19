package com.legumlex.client.feature.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface InvoiceDetailUiState {
    data object Loading : InvoiceDetailUiState
    data class Ready(val detail: InvoiceDetail) : InvoiceDetailUiState
    data class Error(val message: String) : InvoiceDetailUiState
}

/**
 * One invoice. It is paid on its public page on the website (the app takes no
 * payments itself); coming back to the app reads it again, so a payment just
 * made shows at once.
 */
class InvoiceDetailViewModel(
    app: Application,
    val invoiceId: String,
) : AndroidViewModel(app) {
    private val repo = InvoicesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<InvoiceDetailUiState>(InvoiceDetailUiState.Loading)
    val state: StateFlow<InvoiceDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = InvoiceDetailUiState.Loading
        repo.detail(invoiceId)
            .onSuccess { _state.value = InvoiceDetailUiState.Ready(it) }
            .onFailure { _state.value = InvoiceDetailUiState.Error(friendlyMessage(it)) }
    }

    /** Read again without the skeleton (on return from the browser). */
    fun refresh() = viewModelScope.launch {
        if (_state.value !is InvoiceDetailUiState.Ready) return@launch
        repo.detail(invoiceId).onSuccess { _state.value = InvoiceDetailUiState.Ready(it) }
    }
}
