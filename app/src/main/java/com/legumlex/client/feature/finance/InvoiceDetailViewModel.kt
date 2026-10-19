package com.legumlex.client.feature.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.payment.RazorpayOptions
import com.legumlex.client.feature.payment.RazorpayResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface InvoiceDetailUiState {
    data object Loading : InvoiceDetailUiState
    data class Ready(
        val detail: InvoiceDetail,
        /** True while we're hitting /pay or /pay/verify. UI disables the button. */
        val paying: Boolean = false,
        /** Editable rupee text for the amount field (no symbol/grouping). */
        val payAmount: String = "",
    ) : InvoiceDetailUiState {
        /** Outstanding balance — the most the client can pay now. */
        val balance: Double get() = detail.invoice.leftToPay
        /** Parsed entered amount, or null when blank/garbage. */
        val enteredAmount: Double? get() = payAmount.trim().replace(",", "").toDoubleOrNull()
        /** Inline validation message, or null when the amount is payable. */
        val amountError: String? get() {
            val a = enteredAmount ?: return "Enter a valid amount"
            return when {
                a <= 0.0                 -> "Enter a valid amount"
                a < 1.0                  -> "Minimum ₹1"
                a > balance + 0.001      -> "Can't exceed the balance"
                else                     -> null
            }
        }
        val amountValid: Boolean get() = amountError == null
        /** True when the client is paying less than the full balance. */
        val isPartial: Boolean get() = (enteredAmount ?: balance) < balance - 0.001
    }
    data class Error(val message: String) : InvoiceDetailUiState
}

sealed interface InvoicePaymentEvent {
    data class Failed(val message: String) : InvoicePaymentEvent
    data class Info(val message: String) : InvoicePaymentEvent
    /** Hand these to openRazorpayCheckout(activity, options). */
    data class LaunchCheckout(val options: RazorpayOptions) : InvoicePaymentEvent
}

class InvoiceDetailViewModel(
    app: Application,
    val invoiceId: String,
) : AndroidViewModel(app) {
    private val repo = InvoicesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<InvoiceDetailUiState>(InvoiceDetailUiState.Loading)
    val state: StateFlow<InvoiceDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InvoicePaymentEvent>()
    val events: SharedFlow<InvoicePaymentEvent> = _events.asSharedFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = InvoiceDetailUiState.Loading
        repo.detail(invoiceId)
            .onSuccess { _state.value = InvoiceDetailUiState.Ready(it, payAmount = plainAmount(it.invoice.leftToPay)) }
            .onFailure { _state.value = InvoiceDetailUiState.Error(friendlyMessage(it)) }
    }

    /** The amount field changed — keep digits and a single decimal point only. */
    fun onAmountChange(text: String) {
        val current = _state.value as? InvoiceDetailUiState.Ready ?: return
        var seenDot = false
        val cleaned = buildString {
            for (ch in text) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !seenDot -> { seenDot = true; append(ch) }
                }
            }
        }
        _state.value = current.copy(payAmount = cleaned)
    }

    /**
     * Kick off payment for the entered amount. The Pay button calls this; we hit
     * /pay (with the chosen amount) to get the Razorpay options and emit
     * LaunchCheckout for the screen to feed into the SDK. Result flows back via
     * handlePaymentResult().
     */
    fun startPayment() = viewModelScope.launch {
        val current = _state.value as? InvoiceDetailUiState.Ready ?: return@launch
        if (current.paying) return@launch
        val err = current.amountError
        if (err != null) {
            _events.emit(InvoicePaymentEvent.Failed(err))
            return@launch
        }
        _state.value = current.copy(paying = true)
        repo.startPayment(invoiceId, current.payAmount)
            .onSuccess { options ->
                _events.emit(InvoicePaymentEvent.LaunchCheckout(options))
            }
            .onFailure { e ->
                _state.value = current.copy(paying = false)
                _events.emit(InvoicePaymentEvent.Failed(friendlyMessage(e)))
            }
    }

    fun handlePaymentResult(result: RazorpayResult) = viewModelScope.launch {
        val current = _state.value as? InvoiceDetailUiState.Ready ?: return@launch
        when (result) {
            is RazorpayResult.Success -> {
                repo.verifyPayment(invoiceId, result.orderId, result.paymentId, result.signature)
                    .onSuccess {
                        _events.emit(InvoicePaymentEvent.Info("Payment recorded"))
                        // Refresh — paid records + status flip server-side; reset
                        // the amount field to whatever balance remains.
                        repo.detail(invoiceId).onSuccess {
                            _state.value = InvoiceDetailUiState.Ready(
                                it, paying = false, payAmount = plainAmount(it.invoice.leftToPay),
                            )
                        }.onFailure {
                            _state.value = current.copy(paying = false)
                        }
                    }
                    .onFailure { e ->
                        _state.value = current.copy(paying = false)
                        _events.emit(InvoicePaymentEvent.Failed(friendlyMessage(e)))
                    }
            }
            is RazorpayResult.Failed -> {
                _state.value = current.copy(paying = false)
                _events.emit(InvoicePaymentEvent.Failed(result.message))
            }
        }
    }
}
