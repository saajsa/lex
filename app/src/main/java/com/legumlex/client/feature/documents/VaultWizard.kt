package com.legumlex.client.feature.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.cases.CasesRepository
import com.legumlex.client.feature.consultations.ConsultationsRepository
import com.legumlex.client.feature.hearings.HearingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.time.format.DateTimeFormatter

// -----------------------------------------------------------------------------
// Vault wizard: a guided narrowing flow rather than a big list.
//
// The client picks an intent on Welcome ("a case file", "a hearing document",
// "shared with me", etc.). For intents that have multiple parent entities
// (cases, hearings, consultations) a Picker step lists those entities so the
// client narrows further. Then a Results step fetches and shows the matching
// documents on demand.
//
// Back navigation pops one step at a time; Back from Welcome exits Vault.
// -----------------------------------------------------------------------------

/** [plural] names what a picker lists, for sentences ("Loading your cases…"). */
enum class VaultIntent(val title: String, val sub: String, val plural: String) {
    CASE_FILE        ("A Case File",           "Anything filed for one of your matters",              "cases"),
    HEARING_DOC      ("A Hearing Document",    "Orders, exhibits or notes from a specific hearing",   "hearings"),
    LEGAL_NOTICE_DOC ("A Legal Notice",        "Notices sent or received on your behalf",             "legal notices"),
    SESSION_DOC      ("A Consultation Document","Files attached to a consultation with the firm",     "consultations"),
    AGREEMENT_DOC    ("An Agreement",          "Contract drafts and signed agreements",               "agreements"),
    SHARED_WITH_ME   ("Shared With Me",        "Documents the firm shared with you directly",         "shared documents"),
    SEARCH_BY_NAME   ("Search By Name",        "If you remember the file name or a keyword",          "documents");
}

data class PickerItem(val id: String, val title: String, val sub: String?)

sealed interface VaultContext {
    val title: String

    data class ForCase(val caseId: String, override val title: String) : VaultContext
    data class ForHearing(val hearingId: String, override val title: String) : VaultContext
    data class ForConsultation(val consultationId: String, override val title: String) : VaultContext
    data object Notices       : VaultContext { override val title = "Legal Notices" }
    data object Agreements    : VaultContext { override val title = "Agreements" }
    data object SharedWithMe  : VaultContext { override val title = "Shared With You" }
    data class Search(val query: String) : VaultContext {
        override val title = "Results for \"$query\""
    }
}

sealed interface VaultStep {
    data object Welcome : VaultStep

    data class PickerLoading(val intent: VaultIntent) : VaultStep
    /** [denied]: the server refused (403) — the account doesn't include it. */
    data class PickerError(val intent: VaultIntent, val message: String, val denied: Boolean = false) : VaultStep
    data class Picker(val intent: VaultIntent, val items: List<PickerItem>) : VaultStep

    data class SearchInput(val query: String = "") : VaultStep

    data class ResultsLoading(val context: VaultContext) : VaultStep
    data class Results(val context: VaultContext, val documents: List<Document>) : VaultStep
    data class ResultsError(val context: VaultContext, val message: String, val denied: Boolean = false) : VaultStep
}

sealed interface DownloadEvent {
    data class Ready(val file: File, val doc: Document) : DownloadEvent
    data class Failed(val message: String, val doc: Document) : DownloadEvent
}

class VaultWizardViewModel(app: Application) : AndroidViewModel(app) {
    private val docsRepo          = DocumentsRepository(ApiClient.get(app), app)
    private val casesRepo         = CasesRepository(ApiClient.get(app))
    private val hearingsRepo      = HearingsRepository(ApiClient.get(app))
    private val consultationsRepo = ConsultationsRepository(ApiClient.get(app))

    private val _stack = MutableStateFlow<List<VaultStep>>(listOf(VaultStep.Welcome))

    // Expose only the top of the wizard stack; the stack itself is an
    // implementation detail used for back navigation history.
    val step: StateFlow<VaultStep> = _stack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultStep.Welcome)

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>()
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    // The load behind the loading step on top. Back cancels it, so a load
    // that finishes later can't overwrite the step the client went back to.
    private var loadJob: Job? = null

    /** Returns true when back was handled, false when wizard is at root (caller should exit). */
    fun back(): Boolean {
        loadJob?.cancel()
        val cur = _stack.value
        if (cur.size <= 1) return false
        _stack.value = cur.dropLast(1)
        return true
    }

    fun goHome() {
        loadJob?.cancel()
        _stack.value = listOf(VaultStep.Welcome)
    }

    fun chooseIntent(intent: VaultIntent) {
        when (intent) {
            VaultIntent.CASE_FILE        -> openPicker(intent)
            VaultIntent.HEARING_DOC      -> openPicker(intent)
            VaultIntent.SESSION_DOC      -> openPicker(intent)
            VaultIntent.LEGAL_NOTICE_DOC -> loadResults(VaultContext.Notices)
            VaultIntent.AGREEMENT_DOC    -> loadResults(VaultContext.Agreements)
            VaultIntent.SHARED_WITH_ME   -> loadResults(VaultContext.SharedWithMe)
            VaultIntent.SEARCH_BY_NAME   -> push(VaultStep.SearchInput())
        }
    }

    fun choosePickerItem(intent: VaultIntent, item: PickerItem) {
        val ctx: VaultContext = when (intent) {
            VaultIntent.CASE_FILE   -> VaultContext.ForCase(item.id, item.title)
            VaultIntent.HEARING_DOC -> VaultContext.ForHearing(item.id, item.title)
            VaultIntent.SESSION_DOC -> VaultContext.ForConsultation(item.id, item.title)
            else                    -> return
        }
        loadResults(ctx)
    }

    fun submitSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        loadResults(VaultContext.Search(q))
    }

    fun retryPicker(intent: VaultIntent) = openPicker(intent)
    fun retryResults(context: VaultContext) = loadResults(context)

    fun openDocument(doc: Document) {
        if (_downloading.value.contains(doc.id)) return
        viewModelScope.launch {
            _downloading.update { it + doc.id }
            val result = docsRepo.download(doc)
            _downloading.update { it - doc.id }
            result.onSuccess { file ->
                _events.emit(DownloadEvent.Ready(file = file, doc = doc))
            }.onFailure { e ->
                val msg = (e as? HttpException)?.let { httpMessage(it) }
                    ?: e.message ?: "Couldn't download. Check your connection."
                _events.emit(DownloadEvent.Failed(message = msg, doc = doc))
            }
        }
    }

    // ------------------------------------------------------------- internals

    private fun openPicker(intent: VaultIntent) {
        val loading = VaultStep.PickerLoading(intent)
        pushLoading(loading)
        loadJob = viewModelScope.launch {
            val itemsResult = when (intent) {
                VaultIntent.CASE_FILE -> casesRepo.list().map { cs ->
                    cs.map { PickerItem(it.id, it.title.ifBlank { "Untitled case" },
                        listOfNotNull(it.number.takeIf { n -> n.isNotBlank() }?.let { n -> "#$n" }, it.courtName)
                            .joinToString(" · ").ifBlank { null }) }
                }
                VaultIntent.HEARING_DOC -> hearingsRepo.list().map { hs ->
                    hs.map {
                        val date = it.date?.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
                        PickerItem(it.id,
                            title = listOfNotNull(date, it.caseTitle.takeIf { t -> t.isNotBlank() }).joinToString(" · ").ifBlank { "Hearing #${it.id}" },
                            sub   = listOfNotNull(it.courtName, it.status.label).joinToString(" · ").ifBlank { null })
                    }
                }
                VaultIntent.SESSION_DOC -> consultationsRepo.list().map { cs ->
                    cs.map {
                        PickerItem(it.id,
                            title = it.tag?.takeIf { t -> t.isNotBlank() }
                                ?: it.note?.lineSequence()?.firstOrNull()?.take(60)
                                ?: "Untitled session",
                            sub   = it.createdAt?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                        )
                    }
                }
                else -> return@launch
            }
            replaceTop(loading, itemsResult.fold(
                onSuccess = { items -> VaultStep.Picker(intent, items) },
                onFailure = { e -> VaultStep.PickerError(intent, e.toFriendly(), denied = e.isDenied()) },
            ))
        }
    }

    private fun loadResults(context: VaultContext) {
        val loading = VaultStep.ResultsLoading(context)
        pushLoading(loading)
        loadJob = viewModelScope.launch {
            val result = when (context) {
                is VaultContext.ForCase         -> docsRepo.list(category = DocCategory.ALL, query = null, caseId = context.caseId)
                is VaultContext.ForHearing      -> docsRepo.listForRel("hearing", context.hearingId)
                is VaultContext.ForConsultation -> docsRepo.listForRel("consultation", context.consultationId)
                VaultContext.Notices            -> docsRepo.list(category = DocCategory.LEGAL_NOTICE)
                VaultContext.Agreements         -> docsRepo.list(category = DocCategory.AGREEMENT)
                VaultContext.SharedWithMe       -> docsRepo.list(category = DocCategory.SHARED)
                is VaultContext.Search          -> docsRepo.list(query = context.query)
            }
            replaceTop(loading, result.fold(
                onSuccess = { docs -> VaultStep.Results(context, docs) },
                onFailure = { e -> VaultStep.ResultsError(context, e.toFriendly(), denied = e.isDenied()) },
            ))
        }
    }

    private fun push(step: VaultStep) {
        _stack.value = _stack.value + step
    }

    /**
     * Push a loading step. A second tap while one is loading replaces it
     * (and cancels its load) rather than stacking two.
     */
    private fun pushLoading(step: VaultStep) {
        loadJob?.cancel()
        val top = _stack.value.last()
        _stack.value = if (top is VaultStep.PickerLoading || top is VaultStep.ResultsLoading) {
            _stack.value.dropLast(1) + step
        } else {
            _stack.value + step
        }
    }

    /** Swap the loading step for its outcome, only while that very step is still on top. */
    private fun replaceTop(expected: VaultStep, step: VaultStep) {
        if (_stack.value.last() !== expected) return
        _stack.value = _stack.value.dropLast(1) + step
    }

    private fun Throwable.isDenied() = (this as? HttpException)?.code() == 403

    private fun httpMessage(e: HttpException): String = when (e.code()) {
        401        -> "Your session expired. Sign in again."
        403        -> "Your account doesn't include these documents. Ask the firm if you expected to see them."
        404        -> "Not available on the server."
        in 500..599 -> "Server error. Try again in a moment."
        else        -> "Couldn't reach the server (HTTP ${e.code()})."
    }

    private fun Throwable.toFriendly(): String =
        (this as? HttpException)?.let { httpMessage(it) } ?: this.message ?: "Couldn't reach the server."
}
