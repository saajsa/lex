package com.legumlex.client.feature.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface InvoicesUiState {
    data object Loading : InvoicesUiState
    data class Ready(
        val invoices: List<Invoice>,
        val outstandingTotal: Double,
        val outstandingCount: Int,
        val currencySymbol: String,
    ) : InvoicesUiState
    data class Error(val message: String) : InvoicesUiState
}

class InvoicesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = InvoicesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<InvoicesUiState>(InvoicesUiState.Loading)
    val state: StateFlow<InvoicesUiState> = _state.asStateFlow()

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
            _state.value = InvoicesUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    private suspend fun fetch(preserveCurrent: Boolean) {
        repo.list()
            .onSuccess { invoices ->
                val outstanding = invoices.filter {
                    it.status in setOf(
                        InvoiceStatus.UNPAID,
                        InvoiceStatus.PARTIAL,
                        InvoiceStatus.OVERDUE,
                    )
                }
                _state.value = InvoicesUiState.Ready(
                    invoices         = invoices,
                    outstandingTotal = outstanding.sumOf { it.leftToPay },
                    outstandingCount = outstanding.size,
                    currencySymbol   = invoices.firstOrNull()?.currencySymbol ?: "",
                )
            }
            .onFailure { e ->
                val msg = friendlyMessage(e)
                if (!(preserveCurrent && _state.value is InvoicesUiState.Ready)) {
                    _state.value = InvoicesUiState.Error(msg)
                }
            }
    }
}

internal fun friendlyMessage(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        401, 403   -> "Your session expired. Sign in again."
        404        -> "Not available."
        in 500..599 -> "Server error. Try again in a moment."
        else        -> "Couldn't reach the server (HTTP ${e.code()})."
    }
    else -> "Couldn't reach the server. Check your connection."
}
