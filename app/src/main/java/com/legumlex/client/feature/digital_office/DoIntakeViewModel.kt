package com.legumlex.client.feature.digital_office

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.parseChamber
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
import com.legumlex.client.feature.consultations.friendlyMessage
import com.legumlex.client.feature.home.ActiveDoSession

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
 *        - Paid session    → server flips to approved_awaiting_payment. VM asks
 *          for the fee's invoice and emits OpenUrl; the client pays it on the
 *          website, and the poll sees the session start.
 *          On signature verify → server mints Meet + flips to in_progress
 *          and the verifyPayment response carries meet_link. VM emits
 *          Submitted.
 *   4. Advocate declines OR cron auto-declines (approval window) → status =
 *      declined_by_advocate / expired. VM transitions to Declined state.
 *   5. Client cancels → VM hits /cancel and transitions to Cancelled.
 *
 * A session already in flight (chamber.active_session, or [resumeSessionId])
 * resumes into Waiting instead of offering a second request; a client who
 * has not registered for the office is sent to the website to register.
 */
sealed interface DoIntakeUiState {
    data object Loading : DoIntakeUiState

    /** Compose-the-brief state. */
    data class Ready(
        val advocate: ChamberAdvocate,
        /** This client's fee (client_session_fee), invoiced only after acceptance. */
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
        /** The advocate accepted: the fee's invoice page, open in the browser. */
        val payUrl: String? = null,
        /** Raising / opening the fee's invoice. */
        val openingInvoice: Boolean = false,
        /** True while the cancel request is in flight. */
        val cancelling: Boolean = false,
        /** Minutes the advocate has to accept (server's approval_window_minutes). */
        val approvalWindowMinutes: Int? = null,
    ) : DoIntakeUiState

    /** Advocate declined, the request lapsed, or the fee was not paid in time. Terminal in-VM. */
    data class Declined(
        val advocate: ChamberAdvocate,
        val reason: String,
        /** True when the advocate had accepted and the payment window lapsed. */
        val lapsedAfterAccept: Boolean = false,
    ) : DoIntakeUiState

    data class Cancelled(val advocateName: String) : DoIntakeUiState

    /** The client has not registered for the office: registration is on the website. */
    data class NeedsRegistration(
        val officeName: String,
        val message: String?,
        /** The server's `action`: the portal page to register on. */
        val registerUrl: String? = null,
    ) : DoIntakeUiState

    data class Error(val message: String) : DoIntakeUiState
}

sealed interface DoIntakeEvent {
    data class Failed(val message: String) : DoIntakeEvent
    /** Session is live — the screen navigates to the meet link / consultation. */
    data class Submitted(val consultationId: String?, val meetLink: String?) : DoIntakeEvent
    /** Open the fee's invoice page in the browser (the app takes no payments). */
    data class OpenUrl(val url: String) : DoIntakeEvent
    /** Cabin occupied — screen offers join-queue action. */
    data class OfferJoinQueue(val advocateStaffId: Int, val advocateName: String) : DoIntakeEvent
}

class DoIntakeViewModel(
    app: Application,
    private val initialAdvocateStaffId: Int?,
    /** A session to resume into Waiting (e.g. reopened from Home). */
    private val resumeSessionId: String? = null,
) : AndroidViewModel(app) {

    private val api  = ApiClient.get(app)
    private val repo = DoRepository(api)

    private val _state = MutableStateFlow<DoIntakeUiState>(DoIntakeUiState.Loading)
    val state: StateFlow<DoIntakeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DoIntakeEvent>()
    val events: SharedFlow<DoIntakeEvent> = _events.asSharedFlow()

    /** Active poll job for the Waiting state. */
    private var pollJob: Job? = null
    /** False while the screen is stopped, so the poll does not run in the background. */
    private var visible = true
    private var officeName = ""

    init { load() }

    fun retry() = load()

    /** Lifecycle hooks from the screen: poll only while it is on screen. */
    fun onScreenStarted() {
        visible = true
        val w = _state.value as? DoIntakeUiState.Waiting ?: return
        if (pollJob?.isActive != true) startPolling(w.sessionId, immediate = true)
    }
    fun onScreenStopped() {
        visible = false
        pollJob?.cancel(); pollJob = null
    }

    private fun load() = viewModelScope.launch {
        _state.value = DoIntakeUiState.Loading
        runCatching { api.dashboardChamber() }
            .onSuccess { resp ->
                @Suppress("UNCHECKED_CAST")
                val raw = resp["data"] as? Map<String, Any?>
                val chamber = raw?.let { parseChamber(it) }
                if (chamber == null) {
                    _state.value = DoIntakeUiState.Error("The Digital Office could not be loaded. Please try again.")
                    return@onSuccess
                }
                officeName = chamber.officeName

                // A request already in flight outranks a new one: resume it.
                val inFlight = resumeSessionId
                    ?: chamber.activeSession?.takeIf { it.isResumable() }?.id?.toString()
                if (inFlight != null && resume(inFlight, chamber.roster, chamber.currency)) return@onSuccess

                if (!chamber.hasAccess) {
                    _state.value = DoIntakeUiState.NeedsRegistration(chamber.officeName, null)
                    return@onSuccess
                }
                if (!chamber.isOpen || chamber.roster.isEmpty()) {
                    _state.value = DoIntakeUiState.Error(
                        "The Digital Office is closed right now. Please try again during chamber hours."
                    )
                    return@onSuccess
                }
                val explicit = chamber.roster.firstOrNull { it.staffId == initialAdvocateStaffId }
                val target = explicit
                    ?: chamber.roster.firstOrNull { !it.isBusy }
                    ?: chamber.roster.firstOrNull()
                if (target == null) {
                    _state.value = DoIntakeUiState.Error(
                        "No counsel are in the chamber right now. Please try again later."
                    )
                    return@onSuccess
                }
                _state.value = DoIntakeUiState.Ready(
                    advocate   = target,
                    sessionFee = chamber.clientSessionFee,
                    currency   = chamber.currency,
                )
            }
            .onFailure { e -> _state.value = DoIntakeUiState.Error(e.friendlyMessage()) }
    }

    private fun ActiveDoSession.isResumable() = id > 0 && DoSessionStatus.from(status) in setOf(
        DoSessionStatus.AWAITING_APPROVAL, DoSessionStatus.APPROVED_AWAITING_PAYMENT,
    )

    /** Enter Waiting for an existing session; false when it is not waiting any more. */
    private suspend fun resume(sessionId: String, roster: List<ChamberAdvocate>, currency: String): Boolean {
        val s = repo.getSession(sessionId).getOrNull() ?: return false
        if (s.status != DoSessionStatus.AWAITING_APPROVAL && s.status != DoSessionStatus.APPROVED_AWAITING_PAYMENT) return false
        val adv = roster.firstOrNull { it.staffId == s.advocate?.staffId } ?: s.advocate?.let {
            ChamberAdvocate(
                staffId = it.staffId, name = it.name, firstName = it.firstName.orEmpty(), lastName = it.lastName.orEmpty(),
                barCouncilId = null, designation = it.designation, avatarUrl = it.avatarUrl, lastSeen = null,
                isBusy = false, sessionStartedAt = null, queueCount = 0, etaMinutes = 0,
            )
        } ?: return false
        _state.value = DoIntakeUiState.Waiting(
            advocate     = adv,
            sessionFee   = s.sessionFee,
            currency     = currency,
            sessionId    = s.id,
            briefSummary = s.sessionNote,
            approvedAt   = s.approvedAt,
            payUrl       = s.invoicePayUrl,
            approvalWindowMinutes = s.approvalWindowMinutes,
        )
        startPolling(s.id, immediate = true)
        return true
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
            when (e) {
                is AdvocateBusyJoinQueue -> _events.emit(DoIntakeEvent.OfferJoinQueue(
                    advocateStaffId = e.advocateStaffId,
                    advocateName    = current.advocate.name,
                ))
                is NeedsRegistration -> _state.value = DoIntakeUiState.NeedsRegistration(officeName, e.message, e.registerUrl)
                else -> _events.emit(DoIntakeEvent.Failed(e.friendlyMessage()))
            }
            return@launch
        }

        // Approval-gate flow: payment ALWAYS happens after advocate accepts.
        // We always submit intake first and let the server decide.
        val intake = repo.submitIntake(started.sessionId, intakeText.trim()).getOrElse { e ->
            _state.value = current.copy(submitting = false)
            _events.emit(DoIntakeEvent.Failed(e.friendlyMessage()))
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
        startPolling(intake.sessionId, immediate = true)
    }

    // ── Polling ───────────────────────────────────────────────────────────

    private fun startPolling(sessionId: String, immediate: Boolean = false) {
        pollJob?.cancel()
        if (!visible) return
        pollJob = viewModelScope.launch {
            // Tight 4-second cadence — the user is staring at "advocate is
            // reviewing" and wants the transition the moment it happens.
            var first = immediate
            while (isActive) {
                if (!first) delay(4_000)
                first = false
                val current = _state.value as? DoIntakeUiState.Waiting ?: break
                if (current.cancelling || current.openingInvoice) continue

                val fresh = repo.getSession(sessionId).getOrNull() ?: continue
                val window = fresh.approvalWindowMinutes ?: current.approvalWindowMinutes
                if (window != current.approvalWindowMinutes) {
                    _state.value = current.copy(approvalWindowMinutes = window)
                }
                when (fresh.status) {
                    DoSessionStatus.AWAITING_APPROVAL -> {
                        // Still waiting — no state change.
                    }
                    DoSessionStatus.APPROVED_AWAITING_PAYMENT -> {
                        // Advocate accepted: the fee is an invoice, paid on its
                        // page on the website. Raised and opened once; paying it
                        // starts the session, which the next poll sees.
                        val w = _state.value as? DoIntakeUiState.Waiting ?: break
                        val known = w.payUrl ?: fresh.invoicePayUrl
                        if (known != null) {
                            if (w.payUrl == null || w.approvedAt == null) _state.value = w.copy(payUrl = known, approvedAt = fresh.approvedAt ?: w.approvedAt)
                        } else {
                            _state.value = w.copy(approvedAt = fresh.approvedAt, openingInvoice = true)
                            repo.feeInvoice(sessionId)
                                .onSuccess { url ->
                                    (_state.value as? DoIntakeUiState.Waiting)?.let { _state.value = it.copy(payUrl = url, openingInvoice = false) }
                                    _events.emit(DoIntakeEvent.OpenUrl(url))
                                }
                                .onFailure { e ->
                                    (_state.value as? DoIntakeUiState.Waiting)?.let { _state.value = it.copy(openingInvoice = false) }
                                    _events.emit(DoIntakeEvent.Failed(e.friendlyMessage()))
                                }
                        }
                    }
                    DoSessionStatus.CONFIRMED,
                    DoSessionStatus.IN_PROGRESS -> {
                        pollJob = null
                        _events.emit(DoIntakeEvent.Submitted(
                            consultationId = fresh.consultationId,
                            meetLink       = fresh.meetLink,
                        ))
                        return@launch
                    }
                    DoSessionStatus.DECLINED_BY_ADVOCATE -> {
                        pollJob = null
                        _state.value = DoIntakeUiState.Declined(
                            advocate = current.advocate,
                            reason   = fresh.failureReason
                                ?: "Your advocate isn't able to take this request right now.",
                        )
                        return@launch
                    }
                    DoSessionStatus.EXPIRED -> {
                        pollJob = null
                        val afterAccept = fresh.approvedAt != null || current.approvedAt != null
                        _state.value = DoIntakeUiState.Declined(
                            advocate = current.advocate,
                            reason   = fresh.failureReason ?: if (afterAccept)
                                "Your advocate accepted, but the fee was not paid in time, so the request lapsed. " +
                                "No session took place; you can send a new request."
                            else
                                "Your advocate didn't respond in time. Please try again later.",
                            lapsedAfterAccept = afterAccept,
                        )
                        return@launch
                    }
                    DoSessionStatus.CANCELLED -> {
                        pollJob = null
                        _state.value = DoIntakeUiState.Cancelled(current.advocate.name)
                        return@launch
                    }
                    DoSessionStatus.MEET_FAILED,
                    DoSessionStatus.ADVOCATE_OFFLINE,
                    DoSessionStatus.ADVOCATE_BUSY,
                    DoSessionStatus.PAYMENT_FAILED -> {
                        pollJob = null
                        _state.value = DoIntakeUiState.Error(
                            fresh.failureReason ?: "This session could not go ahead. Please contact the firm."
                        )
                        return@launch
                    }
                    else -> { /* keep polling — transitional state */ }
                }
            }
        }
    }

    // ── The fee's invoice ─────────────────────────────────────────────────

    /** Open the fee's invoice page again (the browser was closed before paying). */
    fun openInvoice() = viewModelScope.launch {
        val url = (_state.value as? DoIntakeUiState.Waiting)?.payUrl ?: return@launch
        _events.emit(DoIntakeEvent.OpenUrl(url))
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
                (_state.value as? DoIntakeUiState.Waiting)?.let { _state.value = it.copy(cancelling = false) }
                _events.emit(DoIntakeEvent.Failed(e.friendlyMessage()))
            }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
