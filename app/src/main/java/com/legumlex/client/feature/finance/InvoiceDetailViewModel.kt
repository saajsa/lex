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
 * One invoice, and what paying it offers (the balance, whether part of it may
 * be paid, the gateways): the amount is chosen here and CheckoutActivity
 * carries the payment. Coming back reads both again, so a payment shows at once.
 */
class InvoiceDetailViewModel(
    app: Application,
    val invoiceId: String,
) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val repo = InvoicesRepository(api)

    private val _state = MutableStateFlow<InvoiceDetailUiState>(InvoiceDetailUiState.Loading)
    val state: StateFlow<InvoiceDetailUiState> = _state.asStateFlow()

    private val _pay = MutableStateFlow<PayOptions?>(null)
    val pay: StateFlow<PayOptions?> = _pay.asStateFlow()

    init { load() }

    private fun loadPay() = viewModelScope.launch {
        val id = invoiceId.toIntOrNull() ?: return@launch
        runCatching { payOptionsFrom(api.invoicePayOptions(id)) }.onSuccess { _pay.value = it }
    }

    fun load() = viewModelScope.launch {
        _state.value = InvoiceDetailUiState.Loading
        repo.detail(invoiceId)
            .onSuccess { _state.value = InvoiceDetailUiState.Ready(it); loadPay() }
            .onFailure { _state.value = InvoiceDetailUiState.Error(friendlyMessage(it)) }
    }

    /** Read again without the skeleton (on coming back to the app). */
    fun refresh() = viewModelScope.launch { reread() }

    /** After a payment: read again, then let the checkout close (it waits on this). */
    fun paid() = viewModelScope.launch {
        reread()
        PaymentEvents.refreshed.value = invoiceId.toIntOrNull() ?: 0
    }

    private suspend fun reread() {
        if (_state.value !is InvoiceDetailUiState.Ready) return
        repo.detail(invoiceId).onSuccess { _state.value = InvoiceDetailUiState.Ready(it) }
        val id = invoiceId.toIntOrNull() ?: return
        runCatching { payOptionsFrom(api.invoicePayOptions(id)) }.onSuccess { _pay.value = it }
    }
}
