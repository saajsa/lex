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
        /** What is owed to each issuing business, largest first — each bills and is paid separately. */
        val outstandingByIssuer: List<Pair<String, Double>> = emptyList(),
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
                    outstandingByIssuer = outstanding
                        .groupBy { it.issuerName.ifBlank { "Other" } }
                        .map { (name, list) -> name to list.sumOf { it.leftToPay } }
                        .filter { it.second > 0.0 }
                        .sortedByDescending { it.second },
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
        401        -> "Your session has expired. Please sign in again."
        403        -> "Your account does not include billing. Ask the firm to give you access."
        404        -> "Not available."
        in 500..599 -> "A server error occurred. Please try again shortly."
        else        -> "The server returned an error (${e.code()}). Please try again."
    }
    else -> "The server could not be reached. Please check your connection and try again."
}
