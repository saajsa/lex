package com.legumlex.client.feature.healthchecks.funnel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Document-first AHC funnel state owner.
 *
 *   upload → extracting (server runs Gemini) → confirm → payment → pending → result
 *
 * The user supplies the document; the AI proposes contract_type + parties +
 * summary; the user confirms or corrects on the Confirm screen.
 */
class AhcFunnelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AhcFunnelRepository(ApiClient.get(app))

    data class State(
        val session: AhcFunnelSession? = null,
        val loading: Boolean = false,
        val saving: Boolean = false,
        /** Pending file rows for the Upload step (in-flight / failed). */
        val pending: List<PendingFileUpload> = emptyList(),
        /** Editable form on the Confirm screen — seeded from extraction. */
        val confirmDraft: AhcConfirmDraft = AhcConfirmDraft(),
        /** Mono tick counter cycled during step=extracting for UI feedback. */
        val processingSubStep: Int = 1,
        val error: String? = null,
        val expired: Boolean = false,
    )

    /** The Confirm screen's editable form. Seeded from `extraction` on entry. */
    data class AhcConfirmDraft(
        val contractType: AhcContractType? = null,
        val parties: String = "",
        val concern: String = "",
    ) {
        val isComplete: Boolean get() = contractType != null
    }

    sealed interface Event {
        data object NavigateToResult : Event
        data object NavigateToHome   : Event
        data class  Toast(val message: String) : Event
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init { load() }

    // -------------------------------------------------------------------------
    // Lifecycle / resume
    // -------------------------------------------------------------------------

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null, expired = false) }
        repo.current()
            .onSuccess { hydrate(it) }
            .onFailure { handleFailure(it) }
        _state.update { it.copy(loading = false) }
    }

    fun startNew() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        repo.start()
            .onSuccess { hydrate(it) }
            .onFailure { handleFailure(it) }
        _state.update { it.copy(loading = false) }
    }

    /**
     * Discard the active draft.
     *
     * @param thenNavigateHome when called from inside the funnel, the user
     *   should land back on Home; when called from the HealthChecks list
     *   (via the Resume banner's X), we just want the banner to disappear —
     *   no navigation, because the user is already where they want to be.
     */
    fun abandon(thenNavigateHome: Boolean = false) = viewModelScope.launch {
        repo.abandon()
        _state.value = State()
        if (thenNavigateHome) _events.tryEmit(Event.NavigateToHome)
    }

    // -------------------------------------------------------------------------
    // Step 1 — Upload
    // -------------------------------------------------------------------------

    fun pickFile(uri: Uri) = viewModelScope.launch {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        val (name, size) = queryFileMeta(resolver, uri)
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (size > 25L * 1024 * 1024) {
            _events.tryEmit(Event.Toast("File exceeds 25 MB"))
            return@launch
        }
        val pendingId = UUID.randomUUID().toString()
        _state.update {
            it.copy(pending = it.pending + PendingFileUpload(
                localId = pendingId,
                displayName = name,
                sizeBytes = size,
                mime = mime,
                state = PendingUploadState.UPLOADING,
            ))
        }
        runCatching {
            resolver.openInputStream(uri).use { stream ->
                stream?.readBytes() ?: error("Could not read file")
            }
        }.onSuccess { bytes ->
            repo.uploadFile(name, mime, bytes)
                .onSuccess { sess ->
                    _state.update { it.copy(pending = it.pending.filterNot { p -> p.localId == pendingId }) }
                    hydrate(sess)
                }
                .onFailure { e -> markPendingFailed(pendingId, e.message) }
        }.onFailure { e -> markPendingFailed(pendingId, e.message) }
    }

    fun removePending(localId: String) {
        _state.update { it.copy(pending = it.pending.filterNot { p -> p.localId == localId }) }
    }

    fun removeServerFile(fileId: String) = viewModelScope.launch {
        repo.deleteFile(fileId).onSuccess { hydrate(it) }.onFailure { handleFailure(it) }
    }

    fun advanceFromUpload() = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null) }
        repo.advance()
            .onSuccess { sess ->
                hydrate(sess)
                // Server moved us into extracting — start the poll loop.
                if (sess.currentStep == AhcStep.EXTRACTING) pollExtractingInternal()
            }
            .onFailure { handleFailure(it) }
        _state.update { it.copy(saving = false) }
    }

    // -------------------------------------------------------------------------
    // Step 2 — Extracting (poll until server transitions to confirm)
    // -------------------------------------------------------------------------

    fun pollExtracting() = viewModelScope.launch { pollExtractingInternal() }

    private suspend fun pollExtractingInternal() {
        var ticks = 0
        while (true) {
            val res = repo.status()
            res.onSuccess { sess -> sess?.let { hydrate(it) } }
            val now = _state.value.session?.currentStep
            if (now != null && now != AhcStep.EXTRACTING) break
            ticks++
            _state.update { it.copy(processingSubStep = (ticks % 4) + 1) }
            delay(3000)
        }
    }

    // -------------------------------------------------------------------------
    // Step 3 — Confirm
    // -------------------------------------------------------------------------

    fun setConfirmType(t: AhcContractType?)  = _state.update { it.copy(confirmDraft = it.confirmDraft.copy(contractType = t)) }
    fun setConfirmParties(v: String)         = _state.update { it.copy(confirmDraft = it.confirmDraft.copy(parties = v)) }
    fun setConfirmConcern(v: String)         = _state.update { it.copy(confirmDraft = it.confirmDraft.copy(concern = v)) }

    fun submitConfirm() = viewModelScope.launch {
        val d = _state.value.confirmDraft
        val type = d.contractType ?: return@launch
        _state.update { it.copy(saving = true, error = null) }
        repo.confirm(type, d.parties, d.concern)
            .onSuccess { hydrate(it) }
            .onFailure { handleFailure(it) }
        _state.update { it.copy(saving = false) }
    }

    // -------------------------------------------------------------------------
    // Step 4 — Payment (stubbed → server auto-skips to pending_review)
    // -------------------------------------------------------------------------

    fun startPayment() = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        repo.payInit()
            .onSuccess { sess ->
                hydrate(sess)
                if (sess.paymentStatus == "skipped") {
                    delay(1500)
                    _state.value.session?.consultationId?.let { /* ignored */ }
                    if (_state.value.session?.currentStep == AhcStep.RESULT)
                        _events.tryEmit(Event.NavigateToResult)
                }
            }
            .onFailure { handleFailure(it) }
        _state.update { it.copy(saving = false) }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun markPendingFailed(localId: String, msg: String?) {
        _state.update { st ->
            st.copy(pending = st.pending.map { p ->
                if (p.localId == localId) p.copy(state = PendingUploadState.FAILED, errorMessage = msg)
                else p
            })
        }
    }

    private fun hydrate(session: AhcFunnelSession?) {
        _state.update { current ->
            // Seed the Confirm draft from extraction the first time we land on
            // step=confirm with extraction present. If the user has already
            // started editing on Confirm, don't clobber their edits.
            val draft = when {
                session?.currentStep == AhcStep.CONFIRM
                    && session.extraction != null
                    && current.confirmDraft.contractType == null -> AhcConfirmDraft(
                        contractType = session.extraction.contractType,
                        parties      = session.extraction.parties,
                        concern      = current.confirmDraft.concern,
                    )
                else -> current.confirmDraft
            }
            current.copy(session = session, confirmDraft = draft, expired = false)
        }
    }

    private fun handleFailure(e: Throwable) {
        if (e is HttpException && e.code() == 410) {
            _state.update { it.copy(expired = true) }
            return
        }
        if (e is AhcFunnelExpired) {
            _state.update { it.copy(expired = true) }
            return
        }
        _state.update { it.copy(error = e.message ?: "Something went wrong") }
    }

    private fun queryFileMeta(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "document"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        return name to size
    }
}
