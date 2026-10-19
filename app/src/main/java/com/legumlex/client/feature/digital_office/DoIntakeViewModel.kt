package com.legumlex.client.feature.digital_office

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.parseChamber
import com.legumlex.client.feature.payment.RazorpayOptions
import com.legumlex.client.feature.payment.RazorpayResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Drives the DO intake screen end-to-end with the approval-gate flow
 * (server migration 106):
 *
 *   1. User picks an advocate + writes a brief (Ready state).
 *   2. Submit → /sessions → /intake → status = awaiting_approval.
 *      VM enters Waiting state and polls /sessions/{id} every 4 seconds.
 *   3. Advocate accepts:
 *        - Free session    → server flips to in_progress + meet_link.
 *          VM emits Submitted; screen navigates to Meet.
 *        - Paid session    → server flips to approved_awaiting_payment with
 *          a razorpay block. VM emits LaunchCheckout; screen fires Razorpay.
 *          On signature verify → server mints Meet + flips to in_progress
 *          and the verifyPayment response carries meet_link. VM emits
 *          Submitted.
 *   4. Advocate declines OR cron auto-declines (2 min) → status =
 *      declined_by_advocate / expired. VM transitions to Declined state.
 *   5. Client cancels → VM hits /cancel and transitions to Cancelled.
 */
sealed interface DoIntakeUiState {
    data object Loading : DoIntakeUiState

    /** Compose-the-brief state. */
    data class Ready(
        val advocate: ChamberAdvocate,
        val sessionFee: Double,
        val currency: String,
        /** True while we're hitting start+intake. UI disables submit. */
        val submitting: Boolean = false,
    ) : DoIntakeUiState

    /** Intake submitted; advocate is reviewing. Screen shows wait UI + a Cancel affordance. */
    data class Waiting(
        val advocate: ChamberAdvocate,
        val sessionFee: Double,
        val currency: String,
        val sessionId: String,
        val briefSummary: String?,
        /** Server-side accept timestamp once we observe it (mid-payment). */
        val approvedAt: String? = null,
        /** True between launching Razorpay and getting verifyPayment result. */
        val verifyingPayment: Boolean = false,
        /** True while the cancel request is in flight. */
        val cancelling: Boolean = false,
    ) : DoIntakeUiState

    /** Advocate declined OR cron auto-declined the request. Terminal in-VM. */
    data class Declined(
        val advocate: ChamberAdvocate,
        val reason: String,
    ) : DoIntakeUiState

    data class Cancelled(val advocateName: String) : DoIntakeUiState

    data class Error(val message: String) : DoIntakeUiState
}

sealed interface DoIntakeEvent {
    data class Failed(val message: String) : DoIntakeEvent
    /** Session is live — the screen navigates to the meet link / consultation. */
    data class Submitted(val consultationId: String?, val meetLink: String?) : DoIntakeEvent
    /** Screen should hand these to the Razorpay SDK and forward the result back. */
    data class LaunchCheckout(val options: RazorpayOptions) : DoIntakeEvent
    /** Cabin occupied — screen offers join-queue action. */
    data class OfferJoinQueue(val advocateStaffId: Int, val advocateName: String) : DoIntakeEvent
}

class DoIntakeViewModel(
    app: Application,
    private val initialAdvocateStaffId: Int?,
) : AndroidViewModel(app) {

    private val api  = ApiClient.get(app)
    private val repo = DoRepository(api)

    private val _state = MutableStateFlow<DoIntakeUiState>(DoIntakeUiState.Loading)
    val state: StateFlow<DoIntakeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DoIntakeEvent>()
    val events: SharedFlow<DoIntakeEvent> = _events.asSharedFlow()

    /** Active poll job for the Waiting state. */
    private var pollJob: Job? = null

    init { load() }

    fun retry() = load()

    private fun load() = viewModelScope.launch {
        _state.value = DoIntakeUiState.Loading
        runCatching { api.dashboardChamber() }
            .onSuccess { resp ->
                @Suppress("UNCHECKED_CAST")
                val raw = resp["data"] as? Map<String, Any?>
                val chamber = raw?.let { parseChamber(it) }
                if (chamber == null || !chamber.isOpen || chamber.advocates.isEmpty()) {
                    _state.value = DoIntakeUiState.Error(
                        "The Digital Office is closed right now. Please try again during chamber hours."
                    )
                    return@onSuccess
                }
                val explicit = chamber.advocates.firstOrNull { it.staffId == initialAdvocateStaffId }
                val target = explicit
                    ?: chamber.advocates.firstOrNull { !it.isBusy }
                    ?: chamber.advocates.firstOrNull()
                if (target == null) {
                    _state.value = DoIntakeUiState.Error(
                        "No counsel are in the chamber right now. Please try again later."
                    )
                    return@onSuccess
                }
                _state.value = DoIntakeUiState.Ready(
                    advocate   = target,
                    sessionFee = chamber.sessionFee,
                    currency   = chamber.currency,
                )
            }
            .onFailure { e -> _state.value = DoIntakeUiState.Error(friendly(e)) }
    }

    // ── Submit ────────────────────────────────────────────────────────────

    fun submit(intakeText: String) = viewModelScope.launch {
        val current = _state.value as? DoIntakeUiState.Ready ?: return@launch
        if (current.submitting) return@launch
        if (intakeText.trim().length < 20) {
            _events.emit(DoIntakeEvent.Failed("Please describe your matter in at least 20 characters."))
            return@launch
        }

        _state.value = current.copy(submitting = true)
        val started = repo.startSession(current.advocate.staffId).getOrElse { e ->
            _state.value = current.copy(submitting = false)
            if (e is AdvocateBusyJoinQueue) {
                _events.emit(DoIntakeEvent.OfferJoinQueue(
                    advocateStaffId = e.advocateStaffId,
                    advocateName    = current.advocate.name,
                ))
            } else {
                _events.emit(DoIntakeEvent.Failed(friendly(e)))
            }
            return@launch
        }

        // Approval-gate flow: payment ALWAYS happens after advocate accepts,
        // even for what the server reports as paid. The `free` flag still
        // matters because it determines whether the polling will see an
        // approved_awaiting_payment state at all (free goes direct to
        // in_progress; paid stops at the payment step). We always submit
        // intake first and let the server decide.
        val intake = repo.submitIntake(started.sessionId, intakeText.trim()).getOrElse { e ->
            _state.value = current.copy(submitting = false)
            _events.emit(DoIntakeEvent.Failed(friendly(e)))
            return@launch
        }

        // Enter Waiting and start polling.
        _state.value = DoIntakeUiState.Waiting(
            advocate     = current.advocate,
            sessionFee   = current.sessionFee,
            currency     = current.currency,
            sessionId    = intake.sessionId,
            briefSummary = intake.briefSummary,
        )
        startPolling(intake.sessionId)
    }

    // ── Polling ───────────────────────────────────────────────────────────

    private fun startPolling(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Tight 4-second cadence — the user is staring at "advocate is
            // reviewing" and wants the transition the moment it happens. The
            // server endpoint is cheap.
            while (isActive) {
                delay(4_000)
                val current = _state.value as? DoIntakeUiState.Waiting ?: break
                if (current.cancelling || current.verifyingPayment) continue

                val fresh = repo.getSession(sessionId).getOrNull() ?: continue
                when (fresh.status) {
                    DoSessionStatus.AWAITING_APPROVAL -> {
                        // Still waiting — no state change.
                    }
                    DoSessionStatus.APPROVED_AWAITING_PAYMENT -> {
                        // Advocate accepted; razorpay block is on the response.
                        val rp = fresh.razorpay
                        if (rp == null) {
                            // Defensive: server should always include this; if it
                            // doesn't, fall back to a soft error.
                            _state.value = DoIntakeUiState.Error(
                                "Couldn't start payment — please contact the firm."
                            )
                            pollJob?.cancel(); pollJob = null
                            return@launch
                        }
                        _state.value = current.copy(
                            approvedAt       = fresh.approvedAt,
                            verifyingPayment = true,
                        )
                        _events.emit(DoIntakeEvent.LaunchCheckout(rp))
                        // Keep polling alive — verifyPayment result drives the
                        // next transition. Cancel the poll only when we leave
                        // Waiting altogether.
                    }
                    DoSessionStatus.CONFIRMED,
                    DoSessionStatus.IN_PROGRESS -> {
                        // Free session: advocate accepted → server minted Meet
                        // immediately. Navigate.
                        pollJob?.cancel(); pollJob = null
                        _events.emit(DoIntakeEvent.Submitted(
                            consultationId = fresh.consultationId,
                            meetLink       = fresh.meetLink,
                        ))
                        return@launch
                    }
                    DoSessionStatus.DECLINED_BY_ADVOCATE -> {
                        pollJob?.cancel(); pollJob = null
                        _state.value = DoIntakeUiState.Declined(
                            advocate = current.advocate,
                            reason   = fresh.failureReason
                                ?: "Your advocate isn't able to take this request right now.",
                        )
                        return@launch
                    }
                    DoSessionStatus.EXPIRED -> {
                        // Auto-declined by cron sweep — same user-facing message.
                        pollJob?.cancel(); pollJob = null
                        _state.value = DoIntakeUiState.Declined(
                            advocate = current.advocate,
                            reason   = "Your advocate didn't respond in time. Please try again later.",
                        )
                        return@launch
                    }
                    DoSessionStatus.CANCELLED -> {
                        pollJob?.cancel(); pollJob = null
                        _state.value = DoIntakeUiState.Cancelled(current.advocate.name)
                        return@launch
                    }
                    DoSessionStatus.MEET_FAILED,
                    DoSessionStatus.ADVOCATE_OFFLINE,
                    DoSessionStatus.ADVOCATE_BUSY,
                    DoSessionStatus.PAYMENT_FAILED -> {
                        pollJob?.cancel(); pollJob = null
                        _state.value = DoIntakeUiState.Error(
                            fresh.failureReason ?: "Couldn't complete this session. Please contact the firm."
                        )
                        return@launch
                    }
                    else -> { /* keep polling — transitional state */ }
                }
            }
        }
    }

    // ── Razorpay result (post-approval payment) ───────────────────────────

    fun handlePaymentResult(result: RazorpayResult) = viewModelScope.launch {
        val current = _state.value as? DoIntakeUiState.Waiting ?: return@launch

        when (result) {
            is RazorpayResult.Success -> {
                val verified = repo.verifyPayment(
                    current.sessionId, result.orderId, result.paymentId, result.signature,
                )
                verified
                    .onSuccess { intake ->
                        // Server has now minted the Meet link and flipped to
                        // in_progress in the same call. Navigate.
                        pollJob?.cancel(); pollJob = null
                        _events.emit(DoIntakeEvent.Submitted(
                            consultationId = intake.consultationId,
                            meetLink       = intake.meetLink,
                        ))
                    }
                    .onFailure { e ->
                        // Payment verified call failed — likely a server-side
                        // race (advocate went offline, advocate_busy, meet_failed).
                        // The next poll tick will surface the canonical state.
                        _state.value = current.copy(verifyingPayment = false)
                        _events.emit(DoIntakeEvent.Failed(friendly(e)))
                    }
            }
            is RazorpayResult.Failed -> {
                // User cancelled / Razorpay errored out. Release the spinner
                // and let polling continue — they can retry by re-tapping
                // when the screen re-shows the Razorpay sheet via the next
                // approved_awaiting_payment poll (the order_id is the same;
                // the SDK will pick it back up).
                _state.value = current.copy(verifyingPayment = false)
                _events.emit(DoIntakeEvent.Failed(result.message))
            }
        }
    }

    // ── Client-side cancel ────────────────────────────────────────────────

    fun cancelWhileWaiting() = viewModelScope.launch {
        val current = _state.value as? DoIntakeUiState.Waiting ?: return@launch
        if (current.cancelling) return@launch
        _state.value = current.copy(cancelling = true)
        repo.cancelSession(current.sessionId)
            .onSuccess {
                pollJob?.cancel(); pollJob = null
                _state.value = DoIntakeUiState.Cancelled(current.advocate.name)
            }
            .onFailure { e ->
                _state.value = current.copy(cancelling = false)
                _events.emit(DoIntakeEvent.Failed(friendly(e)))
            }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    private fun friendly(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403   -> "Your session expired. Sign in again."
            409        -> "Couldn't reach the advocate just now. Please refresh and try again."
            422        -> "Couldn't accept your request. Please check the form and try again."
            501        -> "Paid sessions aren't supported in the app yet. Use the web portal."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn't reach the server (HTTP ${e.code()})."
        }
        else -> "Couldn't reach the server. Check your connection."
    }
}
