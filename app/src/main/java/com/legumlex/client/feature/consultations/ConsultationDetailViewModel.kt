package com.legumlex.client.feature.consultations

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.api.uriToFilePart
import com.legumlex.client.feature.documents.DocumentsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface ConsultationDetailUiState {
    data object Loading : ConsultationDetailUiState
    data class Ready(
        val consultation: Consultation,
        val ahcData: AhcAnalysis? = null,
        val ahcLoading: Boolean = false,
        val docRequestBundle: DocRequestBundle? = null,
        val docRequestsLoading: Boolean = false,
        /** Set of doc-request IDs currently being uploaded — drives per-row spinner. */
        val uploadingDocRequestIds: Set<String> = emptySet(),
        val appointmentData: AppointmentData? = null,
        val appointmentLoading: Boolean = false,
        /**
         * Backing Digital Office session for DIGITAL_OFFICE consultations.
         * Replaces appointmentData as the source of truth for the Meet link
         * and live-session status — the appointment row is now an audit
         * record only (see DO architecture notes).
         */
        val doSession: com.legumlex.client.feature.digital_office.DoSession? = null,
        val doSessionLoading: Boolean = false,
        val attachments: List<ConsultationAttachment> = emptyList(),
        val attachmentsLoading: Boolean = false,
        /** True while an attachment upload is in flight — single-slot, not per-file. */
        val attachmentUploading: Boolean = false,
        /** Per-attachment download busy flag, used by the row spinner. */
        val downloadingAttachmentIds: Set<String> = emptySet(),
        /** True while a withdraw/reschedule call is in flight. */
        val actionInFlight: Boolean = false,
        /** Per-row delete spinner for attachments. */
        val deletingAttachmentIds: Set<String> = emptySet(),
        /** Per-doc-request decline spinner. */
        val decliningDocRequestIds: Set<String> = emptySet(),
        /** Per-doc-request "view uploaded file" download spinner. */
        val downloadingDocRequestFileIds: Set<String> = emptySet(),
        /** True while the AHC PDF is being fetched. */
        val ahcDocumentLoading: Boolean = false,
        /**
         * Parent/child consultation linkage — drives the link cards on the
         * detail screen AND the close-confirmation sheet's blocking copy
         * (a parent with an open child can't be closed). Null until loaded;
         * stays null when there's no linkage in either direction.
         */
        val linkage: ConsultationLinkage? = null,
        /** True once the linkage fetch has finished (found or not). */
        val linkageLoaded: Boolean = false,
    ) : ConsultationDetailUiState
    data class Error(val message: String) : ConsultationDetailUiState
}

class ConsultationDetailViewModel(
    app: Application,
    val consultationId: String,
) : AndroidViewModel(app) {
    private val repo = ConsultationsRepository(ApiClient.get(app))
    private val docsRepo = DocumentsRepository(ApiClient.get(app), app)

    private val _state = MutableStateFlow<ConsultationDetailUiState>(ConsultationDetailUiState.Loading)
    val state: StateFlow<ConsultationDetailUiState> = _state.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _events = MutableSharedFlow<ReplyEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ReplyEvent> = _events.asSharedFlow()

    /**
     * Side-channel for events the screen must execute (open file via Intent).
     * Kept separate from ReplyEvent because consumers must hold a Context to
     * act on it — the snackbar consumer can ignore these and vice versa.
     */
    private val _attachmentEvents = MutableSharedFlow<AttachmentEvent>(extraBufferCapacity = 8)
    val attachmentEvents: SharedFlow<AttachmentEvent> = _attachmentEvents.asSharedFlow()

    private val _lifecycleEvents = MutableSharedFlow<DetailLifecycleEvent>()
    val lifecycleEvents: SharedFlow<DetailLifecycleEvent> = _lifecycleEvents.asSharedFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = ConsultationDetailUiState.Loading
        repo.detail(consultationId)
            .onSuccess { consultation ->
                _state.value = ConsultationDetailUiState.Ready(consultation)
                // Fetch kind-specific bundle in parallel.
                when (consultation.kind) {
                    ConsultationKind.AHC            -> loadAhc(consultationId)
                    ConsultationKind.DOC_REQUEST    -> loadDocRequests(consultationId)
                    ConsultationKind.DIGITAL_OFFICE -> loadDoSession(consultationId)
                    else                            -> Unit
                }
                // Attachments are universal across all live consultation kinds.
                if (!consultation.isConverted()) loadAttachments(consultationId)
            }
            .onFailure { e ->
                _state.value = ConsultationDetailUiState.Error(friendly(e))
            }
    }

    /**
     * Quiet refresh (screen resumed, pull-to-refresh): refetch the row and
     * the kind's bundle without dropping to the Loading state, so what the
     * client is reading stays on screen. Falls back to [load] from an error.
     */
    fun refresh() = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready
        if (current == null) { load(); return@launch }
        _refreshing.value = true
        repo.detail(consultationId)
            .onSuccess { refreshed ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(consultation = refreshed)
                when (refreshed.kind) {
                    ConsultationKind.AHC -> repo.ahcAnalysis(consultationId).onSuccess { ahc ->
                        (_state.value as? ConsultationDetailUiState.Ready)?.let { _state.value = it.copy(ahcData = ahc) }
                        maybeStartAhcInReviewPoll()
                    }
                    ConsultationKind.DOC_REQUEST -> refreshDocRequestsQuiet().join()
                    ConsultationKind.DIGITAL_OFFICE ->
                        com.legumlex.client.feature.digital_office.DoRepository(ApiClient.get(getApplication()))
                            .sessionForConsultation(consultationId)
                            .onSuccess { sess -> (_state.value as? ConsultationDetailUiState.Ready)?.let { _state.value = it.copy(doSession = sess) } }
                    else -> Unit
                }
                if (!refreshed.isConverted()) repo.attachments(consultationId).onSuccess { list ->
                    (_state.value as? ConsultationDetailUiState.Ready)?.let { _state.value = it.copy(attachments = list) }
                }
            }
            .onFailure { e -> _events.emit(ReplyEvent.Failed(friendly(e))) }
        _refreshing.value = false
    }

    private val _refreshing = MutableStateFlow(false)
    /** True while a pull-to-refresh / resume refresh is running. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private fun loadAhc(id: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        _state.value = current.copy(ahcLoading = true)
        repo.ahcAnalysis(id)
            .onSuccess { ahc ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(ahcData = ahc, ahcLoading = false)
                // Once the consultation row + AHC bundle have landed, start the
                // in-review poll if the report hasn't been released yet — the
                // 30-min advocate SLA means the user might be watching this
                // screen the whole time and shouldn't have to pull-to-refresh.
                maybeStartAhcInReviewPoll()
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(ahcLoading = false)
            }
    }

    // ── In-review polling ────────────────────────────────────────────────────
    //
    // While the advocate is reviewing an AHC, both the consultation row and
    // the funnel session change without any client-driven action. Polling
    // catches the release the moment it lands, so the user sees the verdict
    // hero appear without leaving the screen. Cancels itself the instant the
    // report is released, the consultation closes, or the screen disposes.

    private var ahcPollJob: Job? = null
    private val ahcPollIntervalMs = 30_000L

    private fun maybeStartAhcInReviewPoll() {
        val ready = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (ready.consultation.kind != ConsultationKind.AHC) return
        if (!shouldKeepPollingAhc(ready)) {
            ahcPollJob?.cancel(); ahcPollJob = null
            return
        }
        if (ahcPollJob?.isActive == true) return
        ahcPollJob = viewModelScope.launch {
            while (isActive) {
                delay(ahcPollIntervalMs)
                val before = _state.value as? ConsultationDetailUiState.Ready ?: break
                if (!shouldKeepPollingAhc(before)) break
                // Refresh consultation row first (so released_at / closed_at /
                // is_escalated lands fastest), then the AHC bundle.
                refreshConsultationOnly()
                repo.ahcAnalysis(consultationId).onSuccess { ahc ->
                    val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                    _state.value = now.copy(ahcData = ahc)
                }
            }
            ahcPollJob = null
        }
    }

    private fun shouldKeepPollingAhc(ready: ConsultationDetailUiState.Ready): Boolean {
        val c = ready.consultation
        // Stop the moment the report is released, the consultation closes,
        // or staff escalates (the linked consultation owns updates from then).
        if (c.closedAt != null) return false
        if (c.isEscalated)      return false
        val released = c.releasedAt != null || ready.ahcData?.released == true
        return !released
    }

    override fun onCleared() {
        super.onCleared()
        ahcPollJob?.cancel(); ahcPollJob = null
        docPollJob?.cancel(); docPollJob = null
    }

    /**
     * Optimistic reply send: appends the message immediately, fires API, on
     * success replaces the optimistic copy with the server's authoritative one.
     * On failure, removes the optimistic copy and surfaces a snackbar event.
     */
    fun sendReply(message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank() || _isSending.value) return

        val current = _state.value as? ConsultationDetailUiState.Ready ?: return
        val optimistic = Reply(
            id          = "tmp-${System.currentTimeMillis()}",
            message     = trimmed,
            fromClient  = true,
            authorName  = "You",
            authorImage = null,
            createdAt   = java.time.LocalDateTime.now(),
        )
        // copy(), not a new Ready: the AHC report, the document checklist,
        // the session and the shared files stay on screen while it sends.
        _state.value = current.copy(
            consultation = current.consultation.copy(
                replies = current.consultation.replies + optimistic,
                replyCount = current.consultation.replyCount + 1,
            )
        )

        viewModelScope.launch {
            _isSending.value = true
            repo.sendReply(consultationId, trimmed)
                .onSuccess { serverReply ->
                    val now = _state.value
                    if (now is ConsultationDetailUiState.Ready) {
                        val replaced = now.consultation.replies.map { r ->
                            if (r.id == optimistic.id) serverReply else r
                        }
                        _state.value = now.copy(consultation = now.consultation.copy(replies = replaced))
                    }
                }
                .onFailure { e ->
                    val now = _state.value
                    if (now is ConsultationDetailUiState.Ready) {
                        _state.value = now.copy(
                            consultation = now.consultation.copy(
                                replies = now.consultation.replies.filter { it.id != optimistic.id },
                                replyCount = (now.consultation.replyCount - 1).coerceAtLeast(0),
                            )
                        )
                    }
                    _events.emit(ReplyEvent.Failed(friendly(e)))
                }
            _isSending.value = false
        }
    }

    private fun loadDocRequests(id: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        _state.value = current.copy(docRequestsLoading = true)
        repo.docRequests(id)
            .onSuccess { bundle ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(docRequestBundle = bundle, docRequestsLoading = false)
                // Keep the checklist live while the user watches it — the
                // advocate can add rows, send reminders, or waive items at any
                // time without a client-driven action.
                maybeStartDocRequestPoll()
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(docRequestsLoading = false)
            }
    }

    /**
     * Refetch the checklist without flipping the loading spinner — used by the
     * poll and by the screen's on-resume refresh. Restarts the poll so a row
     * the advocate just added (taking pending back above zero) keeps it alive.
     */
    private fun refreshDocRequestsQuiet() = viewModelScope.launch {
        repo.docRequests(consultationId).onSuccess { bundle ->
            val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
            _state.value = now.copy(docRequestBundle = bundle)
            maybeStartDocRequestPoll()
        }
    }

    /** Public entry for the screen's ON_RESUME hook + pull-to-refresh. */
    fun refreshDocRequests() {
        val ready = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (ready.consultation.kind != ConsultationKind.DOC_REQUEST) return
        refreshDocRequestsQuiet()
    }

    // ── Doc-request status polling ────────────────────────────────────────────
    //
    // Mirrors the AHC in-review poll. The advocate works the other side of the
    // checklist (adds rows, sends reminders, waives items) with no signal to
    // the client, so we poll while there's outstanding work. Cancels itself
    // when everything is resolved, the consultation closes, or the VM clears.

    private var docPollJob: Job? = null
    private val docPollIntervalMs = 30_000L

    private fun maybeStartDocRequestPoll() {
        val ready = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (ready.consultation.kind != ConsultationKind.DOC_REQUEST) return
        if (!shouldKeepPollingDocRequests(ready)) {
            docPollJob?.cancel(); docPollJob = null
            return
        }
        if (docPollJob?.isActive == true) return
        docPollJob = viewModelScope.launch {
            while (isActive) {
                delay(docPollIntervalMs)
                val before = _state.value as? ConsultationDetailUiState.Ready ?: break
                if (!shouldKeepPollingDocRequests(before)) break
                refreshConsultationOnly()
                repo.docRequests(consultationId).onSuccess { bundle ->
                    val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                    _state.value = now.copy(docRequestBundle = bundle)
                }
            }
            docPollJob = null
        }
    }

    private fun shouldKeepPollingDocRequests(ready: ConsultationDetailUiState.Ready): Boolean {
        if (ready.consultation.closedAt != null) return false
        val bundle = ready.docRequestBundle ?: return false
        // Outstanding work the advocate may resolve from their side.
        return bundle.pending > 0
    }

    /**
     * Download (if not cached) the file the client uploaded for a doc-request
     * row and emit an open event, reusing the attachment-open Intent path.
     */
    fun openDocRequestFile(item: DocRequestItem) {
        if (item.status != DocRequestStatus.UPLOADED) return
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (item.id in current.downloadingDocRequestFileIds) return
        _state.value = current.copy(
            downloadingDocRequestFileIds = current.downloadingDocRequestFileIds + item.id,
        )
        viewModelScope.launch {
            val displayName = item.fulfilledDisplayName
                ?: item.fulfilledFileName ?: "${item.documentName}.pdf"
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir  = File(getApplication<Application>().cacheDir, "docs").apply { mkdirs() }
                    val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val target = File(dir, "dr_${item.id}_$safe")
                    if (!(target.exists() && target.length() > 0)) {
                        repo.downloadDocRequestFile(item.id).byteStream().use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                    target
                }
            }
                .onSuccess { file ->
                    clearDocFileBusy(item.id)
                    _attachmentEvents.emit(AttachmentEvent.Open(file, null))
                }
                .onFailure { e ->
                    clearDocFileBusy(item.id)
                    _events.emit(ReplyEvent.Failed(friendly(e)))
                }
        }
    }

    private fun clearDocFileBusy(id: String) {
        val now = _state.value as? ConsultationDetailUiState.Ready ?: return
        _state.value = now.copy(downloadingDocRequestFileIds = now.downloadingDocRequestFileIds - id)
    }

    /**
     * Upload a file fulfilling a pending doc-request. Marks the row busy
     * while in flight, patches the bundle in place on success, surfaces a
     * snackbar event on failure. We don't optimistically flip the row to
     * "uploaded" because the server is the source of truth on filename +
     * timestamp; the spinner is the user feedback during the round trip.
     */
    fun uploadDocRequest(requestId: String, uri: Uri) {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (requestId in current.uploadingDocRequestIds) return
        _state.value = current.copy(uploadingDocRequestIds = current.uploadingDocRequestIds + requestId)

        viewModelScope.launch {
            val part = uriToFilePart(getApplication(), uri)
            if (part == null) {
                clearBusy(requestId)
                _events.emit(ReplyEvent.Failed("Couldn't read that file. Try another."))
                return@launch
            }
            repo.fulfillDocRequest(requestId, part)
                .onSuccess { updated ->
                    val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                    val bundle = now.docRequestBundle ?: return@onSuccess
                    val items = bundle.items.map { if (it.id == updated.id) updated else it }
                    val uploaded = items.count { it.status == DocRequestStatus.UPLOADED }
                    val waived   = items.count { it.status == DocRequestStatus.WAIVED }
                    _state.value = now.copy(
                        docRequestBundle = bundle.copy(
                            items    = items,
                            uploaded = uploaded,
                            waived   = waived,
                            pending  = (bundle.total - uploaded - waived).coerceAtLeast(0),
                        ),
                        uploadingDocRequestIds = now.uploadingDocRequestIds - requestId,
                    )
                }
                .onFailure { e ->
                    clearBusy(requestId)
                    _events.emit(ReplyEvent.Failed(friendly(e)))
                }
        }
    }

    private fun clearBusy(requestId: String) {
        val now = _state.value as? ConsultationDetailUiState.Ready ?: return
        _state.value = now.copy(uploadingDocRequestIds = now.uploadingDocRequestIds - requestId)
    }

    private fun loadAttachments(id: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        _state.value = current.copy(attachmentsLoading = true)
        repo.attachments(id)
            .onSuccess { list ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(attachments = list, attachmentsLoading = false)
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(attachmentsLoading = false)
            }
    }

    /**
     * Upload a client-authored attachment to this consultation. Prepends the
     * new row optimistically once the server returns it (so it appears at the
     * top of the list, matching the newest-first ordering on the server).
     */
    fun uploadAttachment(uri: Uri) {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (current.attachmentUploading) return
        _state.value = current.copy(attachmentUploading = true)

        viewModelScope.launch {
            val part = uriToFilePart(getApplication(), uri)
            if (part == null) {
                clearAttachmentBusy()
                _events.emit(ReplyEvent.Failed("Couldn't read that file. Try another."))
                return@launch
            }
            repo.uploadAttachment(consultationId, part)
                .onSuccess { row ->
                    val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                    _state.value = now.copy(
                        attachments = listOf(row) + now.attachments,
                        attachmentUploading = false,
                    )
                }
                .onFailure { e ->
                    clearAttachmentBusy()
                    _events.emit(ReplyEvent.Failed(friendly(e)))
                }
        }
    }

    private fun clearAttachmentBusy() {
        val now = _state.value as? ConsultationDetailUiState.Ready ?: return
        _state.value = now.copy(attachmentUploading = false)
    }

    /**
     * Download (if not cached) and open the attachment via the host activity.
     * Emits [AttachmentEvent.Open] on success so the screen can fire an
     * Intent.ACTION_VIEW — the VM intentionally doesn't touch Intents.
     */
    fun openAttachment(att: ConsultationAttachment) {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return
        if (att.id in current.downloadingAttachmentIds) return
        _state.value = current.copy(
            downloadingAttachmentIds = current.downloadingAttachmentIds + att.id,
        )
        viewModelScope.launch {
            docsRepo.downloadById(att.id, att.displayName)
                .onSuccess { file ->
                    clearDownloadBusy(att.id)
                    _attachmentEvents.emit(AttachmentEvent.Open(file, att.fileType.ifBlank { null }))
                }
                .onFailure { e ->
                    clearDownloadBusy(att.id)
                    _events.emit(ReplyEvent.Failed(friendly(e)))
                }
        }
    }

    private fun clearDownloadBusy(id: String) {
        val now = _state.value as? ConsultationDetailUiState.Ready ?: return
        _state.value = now.copy(downloadingAttachmentIds = now.downloadingAttachmentIds - id)
    }

    private fun loadDoSession(id: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        _state.value = current.copy(doSessionLoading = true)
        com.legumlex.client.feature.digital_office.DoRepository(ApiClient.get(getApplication()))
            .sessionForConsultation(id)
            .onSuccess { sess ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(doSession = sess, doSessionLoading = false)
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(doSessionLoading = false)
            }
    }

    @Suppress("unused") // kept for legacy callers; new code uses loadDoSession
    private fun loadAppointment(id: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        _state.value = current.copy(appointmentLoading = true)
        repo.appointment(id)
            .onSuccess { appt ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(appointmentData = appt, appointmentLoading = false)
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(appointmentLoading = false)
            }
    }

    /**
     * Withdraw a pending self-initiated request. Server soft-cancels, so once
     * this succeeds the consultation is no longer client-visible — the screen
     * pops back rather than refetching.
     */
    fun withdraw() = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        if (current.actionInFlight) return@launch
        _state.value = current.copy(actionInFlight = true)
        repo.withdraw(consultationId)
            .onSuccess {
                _events.emit(ReplyEvent.Info("Request withdrawn"))
                _lifecycleEvents.emit(DetailLifecycleEvent.Withdrew)
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready
                if (now != null) _state.value = now.copy(actionInFlight = false)
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /**
     * Terminal client-driven cancel. Uses the lifecycle endpoint added in
     * migration 136 (POST /consultations/{id}/cancel). The server returns
     * 200 on both first call and idempotent re-fires, so a successful
     * Result means the row is closed; we emit Closed and let the screen
     * decide whether to pop or refresh into the closure layout.
     */
    fun cancel(reason: String?) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        if (current.actionInFlight) return@launch
        _state.value = current.copy(actionInFlight = true)
        repo.cancel(consultationId, reason)
            .onSuccess {
                _events.emit(ReplyEvent.Info("Consultation closed"))
                _lifecycleEvents.emit(DetailLifecycleEvent.Closed)
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready
                if (now != null) _state.value = now.copy(actionInFlight = false)
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /**
     * Fetch the parent/child linkage. Called on-demand when the user opens
     * the close sheet (so the sheet can show the "linked child is still
     * open" block) or when the detail screen wants to render the link card.
     * Idempotent — repeated calls overwrite the cached value.
     */
    fun loadLinkage() = viewModelScope.launch {
        if (_state.value !is ConsultationDetailUiState.Ready) return@launch
        repo.linkage(consultationId)
            .onSuccess { l ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(linkage = l, linkageLoaded = true)
            }
            .onFailure {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(linkageLoaded = true)
            }
            // Failures are silent — the close sheet falls back to its
            // generic copy when linkage is null, and the link card simply
            // doesn't render. Surfacing an error here would alarm the user
            // for a soft auxiliary fetch.
    }

    /**
     * Update preferred date/time. Either argument may be null — the server
     * rejects the empty case. On success the consultation is refetched so
     * the detail header reflects the new slot.
     */
    fun updatePreferredSlot(preferredDate: String?, preferredTime: String?) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        if (current.actionInFlight) return@launch
        _state.value = current.copy(actionInFlight = true)
        repo.updatePreferred(consultationId, preferredDate, preferredTime)
            .onSuccess {
                _events.emit(ReplyEvent.Info("Preferred slot updated"))
                // Refetch so the new preferred date/time becomes visible.
                refreshConsultationOnly()
                val now = _state.value as? ConsultationDetailUiState.Ready
                if (now != null) _state.value = now.copy(actionInFlight = false)
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready
                if (now != null) _state.value = now.copy(actionInFlight = false)
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /** Refresh only the consultation row (no doc-requests / appointment refetch). */
    private suspend fun refreshConsultationOnly() {
        repo.detail(consultationId).onSuccess { refreshed ->
            val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
            _state.value = now.copy(consultation = refreshed)
        }
    }

    /**
     * Delete a client-uploaded attachment. Only callable for attachments
     * authored by the client — the UI hides this affordance for staff files.
     */
    fun deleteAttachment(attachment: ConsultationAttachment) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        if (attachment.id in current.deletingAttachmentIds) return@launch
        _state.value = current.copy(
            deletingAttachmentIds = current.deletingAttachmentIds + attachment.id,
        )
        repo.deleteAttachment(attachment.id)
            .onSuccess {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(
                    attachments = now.attachments.filterNot { it.id == attachment.id },
                    deletingAttachmentIds = now.deletingAttachmentIds - attachment.id,
                )
                _events.emit(ReplyEvent.Info("Removed “${attachment.displayName}”"))
                // The server reverts any linked doc_request back to pending —
                // refetch the doc-request bundle so the row flips visibly.
                if (now.consultation.kind == ConsultationKind.DOC_REQUEST) {
                    loadDocRequests(consultationId)
                }
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(
                    deletingAttachmentIds = now.deletingAttachmentIds - attachment.id,
                )
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /**
     * Mark a pending doc-request as declined with a reason — "I don't have
     * this document". Reflects in the bundle as a `WAIVED` row so the
     * progress card updates immediately.
     */
    fun declineDocRequest(requestId: String, reason: String) = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty() || requestId in current.decliningDocRequestIds) return@launch
        _state.value = current.copy(
            decliningDocRequestIds = current.decliningDocRequestIds + requestId,
        )
        repo.declineDocRequest(requestId, trimmedReason)
            .onSuccess {
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                val bundle = now.docRequestBundle
                if (bundle != null) {
                    val items = bundle.items.map { row ->
                        if (row.id == requestId)
                            row.copy(
                                status = DocRequestStatus.WAIVED,
                                waivedReason = trimmedReason,
                            )
                        else row
                    }
                    val uploaded = items.count { it.status == DocRequestStatus.UPLOADED }
                    val waived   = items.count { it.status == DocRequestStatus.WAIVED }
                    _state.value = now.copy(
                        docRequestBundle = bundle.copy(
                            items    = items,
                            uploaded = uploaded,
                            waived   = waived,
                            pending  = (bundle.total - uploaded - waived).coerceAtLeast(0),
                        ),
                        decliningDocRequestIds = now.decliningDocRequestIds - requestId,
                    )
                } else {
                    _state.value = now.copy(
                        decliningDocRequestIds = now.decliningDocRequestIds - requestId,
                    )
                }
                _events.emit(ReplyEvent.Info("Marked as not available"))
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(
                    decliningDocRequestIds = now.decliningDocRequestIds - requestId,
                )
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /**
     * Download the AHC source PDF and emit an open event so the host activity
     * can hand it to the system viewer. Cached under <cache>/ahc/{id}.pdf
     * — re-opens are instant after the first fetch.
     */
    fun downloadAhcDocument() = viewModelScope.launch {
        val current = _state.value as? ConsultationDetailUiState.Ready ?: return@launch
        if (current.ahcDocumentLoading) return@launch
        _state.value = current.copy(ahcDocumentLoading = true)
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val dir = java.io.File(ctx.cacheDir, "ahc").apply { mkdirs() }
                val target = java.io.File(dir, "ahc_${consultationId}.pdf")
                if (!target.exists() || target.length() == 0L) {
                    val body = repo.downloadAhcDocument(consultationId)
                    body.byteStream().use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
                target
            }
        }
            .onSuccess { file ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onSuccess
                _state.value = now.copy(ahcDocumentLoading = false)
                _attachmentEvents.emit(AttachmentEvent.Open(file, "application/pdf"))
            }
            .onFailure { e ->
                val now = _state.value as? ConsultationDetailUiState.Ready ?: return@onFailure
                _state.value = now.copy(ahcDocumentLoading = false)
                _events.emit(ReplyEvent.Failed(friendly(e)))
            }
    }

    /** The server's own sentence when it gave one (ApiError), else plain words. */
    private fun friendly(e: Throwable): String =
        if (e.apiError().http == 404 && e.apiError().message == null) "This consultation is no longer available."
        else e.friendlyMessage()
}

sealed interface ReplyEvent {
    data class Failed(val message: String) : ReplyEvent
    /** Generic success toast — used by withdraw / reschedule / decline. */
    data class Info(val message: String) : ReplyEvent
}

sealed interface AttachmentEvent {
    /** Screen should fire an Intent.ACTION_VIEW on [file], hinting [mime]. */
    data class Open(val file: File, val mime: String?) : AttachmentEvent
}

/**
 * Lifecycle events the screen acts on (navigation). Distinct from
 * [ReplyEvent] which only drives the snackbar.
 */
sealed interface DetailLifecycleEvent {
    /** Consultation withdrew successfully — screen should pop back. */
    data object Withdrew : DetailLifecycleEvent

    /** Consultation closed via the cancel endpoint — screen should pop back
     *  or refresh to the closure layout depending on caller preference. */
    data object Closed : DetailLifecycleEvent
}
