package com.legumlex.client.feature.notices

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Steps across the two-way funnel. FORK chooses direction. */
enum class NoticeStep {
    FORK,
    OUT_PROBLEM, OUT_RECIPIENT, OUT_EXTRAS, OUT_DONE,
    IN_UPLOAD, IN_PROCESSING, IN_CONFIRM, IN_DONE,
}

/** A plain-language problem card mapped to a real notice type. */
data class ProblemCard(val key: String, val title: String, val desc: String, val typeId: Int?)

data class NewNoticeState(
    val step: NoticeStep = NoticeStep.FORK,
    val direction: NoticeDirection? = null,
    val types: List<NoticeType> = emptyList(),
    val typesLoading: Boolean = true,
    val typesError: String? = null,
    val problems: List<ProblemCard> = emptyList(),
    // outbound
    val selectedProblemKey: String? = null,   // which card is highlighted (cards can share a typeId)
    val selectedTypeId: Int? = null,
    val recipientName: String = "",
    val recipientAddress: String = "",
    val recipientContact: String = "",
    val recipientEmail: String = "",
    val claimAmount: String = "",
    val context: String = "",
    val showRecipientError: Boolean = false,
    // inbound
    val pickedFileName: String? = null,
    val extracted: ExtractedNotice? = null,
    val senderName: String = "",
    val senderContact: String = "",
    val claimAmountIn: String = "",
    val responseDueDate: String = "",
    val extractFailed: Boolean = false,
    val senderAddress: String = "",
    val senderAdvocateName: String = "",
    val receivedDate: String = "",
    // response nesting (outbound reply to a received inbound notice)
    val parentNoticeId: String? = null,
    val parentLabel: String? = null,
    // shared
    val isSubmitting: Boolean = false,
    val createdNoticeId: String? = null,
    val createdReference: String? = null,
    val error: String? = null,
)

class NewNoticeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoticesRepository(ApiClient.get(app))
    private val _state = MutableStateFlow(NewNoticeState())
    val state: StateFlow<NewNoticeState> = _state.asStateFlow()

    private var pickedFile: Triple<String, String, ByteArray>? = null

    init { loadTypes() }

    /** The real notice types; a failure is shown with a retry, never silently empty. */
    fun loadTypes() = viewModelScope.launch {
        _state.update { it.copy(typesLoading = true, typesError = null) }
        repo.loadTypes()
            .onSuccess { types ->
                _state.update { it.copy(types = types, problems = buildProblems(types), typesLoading = false) }
            }
            .onFailure {
                _state.update { it.copy(typesLoading = false, typesError = "The notice types could not be loaded.") }
            }
    }

    /** Enter the funnel as an outbound response nested under a received notice. */
    fun initResponse(parentId: String, recipientName: String?) {
        if (_state.value.parentNoticeId == parentId) return
        _state.update {
            it.copy(
                parentNoticeId = parentId,
                parentLabel = recipientName,
                direction = NoticeDirection.OUTBOUND,
                recipientName = recipientName.orEmpty(),
                step = NoticeStep.OUT_PROBLEM,
            )
        }
    }

    // ---- navigation ----
    fun chooseDirection(d: NoticeDirection) {
        // A file picked for one direction must never ride along with the other.
        pickedFile = null
        _state.update {
            it.copy(direction = d, step = if (d == NoticeDirection.OUTBOUND) NoticeStep.OUT_PROBLEM else NoticeStep.IN_UPLOAD,
                pickedFileName = null, extracted = null, extractFailed = false, selectedTypeId = null, selectedProblemKey = null, error = null)
        }
    }

    fun back() = _state.update {
        val prev = when (it.step) {
            NoticeStep.OUT_PROBLEM, NoticeStep.IN_UPLOAD -> NoticeStep.FORK
            NoticeStep.OUT_RECIPIENT -> NoticeStep.OUT_PROBLEM
            NoticeStep.OUT_EXTRAS -> NoticeStep.OUT_RECIPIENT
            NoticeStep.IN_CONFIRM -> NoticeStep.IN_UPLOAD
            else -> it.step
        }
        it.copy(step = prev)
    }

    /** True when back() should pop the screen instead of stepping within the flow. */
    fun atRoot(): Boolean {
        val s = _state.value
        // A response flow has no fork — its first step (OUT_PROBLEM) is the root.
        return s.step == NoticeStep.FORK ||
            (s.parentNoticeId != null && s.step == NoticeStep.OUT_PROBLEM)
    }

    // ---- outbound ----
    fun selectProblem(card: ProblemCard) = _state.update { it.copy(selectedProblemKey = card.key, selectedTypeId = card.typeId) }
    /** The server needs a real type; when a card matches none, the client names it. */
    fun selectType(id: Int) = _state.update { it.copy(selectedTypeId = id) }
    fun continueFromProblem() {
        val s = _state.value
        if (s.selectedProblemKey != null && s.selectedTypeId != null) go(NoticeStep.OUT_RECIPIENT)
    }

    fun setRecipientName(v: String) = _state.update { it.copy(recipientName = v, showRecipientError = false) }
    fun setRecipientAddress(v: String) = _state.update { it.copy(recipientAddress = v) }
    fun setRecipientContact(v: String) = _state.update { it.copy(recipientContact = v) }
    fun setRecipientEmail(v: String) = _state.update { it.copy(recipientEmail = v) }
    fun continueFromRecipient() {
        if (_state.value.recipientName.isBlank()) { _state.update { it.copy(showRecipientError = true) }; return }
        go(NoticeStep.OUT_EXTRAS)
    }

    fun setClaimAmount(v: String) = _state.update { it.copy(claimAmount = v.filter(Char::isDigit).take(13)) }
    fun setContext(v: String) = _state.update { it.copy(context = v) }

    fun submitOutbound() = viewModelScope.launch {
        val s = _state.value
        if (s.selectedTypeId == null) { _state.update { it.copy(error = "Choose the notice type first.") }; return@launch }
        _state.update { it.copy(isSubmitting = true, error = null) }
        repo.create(
            fields = mapOf(
                "notice_direction" to "Outbound",
                "notice_type_id" to s.selectedTypeId?.toString(),
                "parent_notice_id" to s.parentNoticeId,
                "recipient_name" to s.recipientName,
                "recipient_address" to s.recipientAddress,
                "recipient_contact" to s.recipientContact,
                "recipient_email" to s.recipientEmail,
                "claim_amount" to s.claimAmount,
                "notes" to s.context,
            ),
            file = pickedFile,
        ).onSuccess { d ->
            _state.update { it.copy(isSubmitting = false, step = NoticeStep.OUT_DONE,
                createdNoticeId = d.summary.id, createdReference = d.summary.referenceNumber) }
        }.onFailure { e ->
            _state.update { it.copy(isSubmitting = false, error = e.message ?: "Submission failed") }
        }
    }

    // ---- inbound ----
    fun pickFile(uri: Uri) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        val name = queryName(uri)
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = runCatching { resolver.openInputStream(uri).use { it?.readBytes() } }.getOrNull()
        if (bytes == null) { _state.update { it.copy(error = "Could not read that file") }; return@launch }
        pickedFile = Triple(name, mime, bytes)
        _state.update { it.copy(pickedFileName = name, step = NoticeStep.IN_PROCESSING, extractFailed = false, error = null) }

        repo.extract(name, mime, bytes)
            .onSuccess { ex ->
                if (ex == null) {
                    _state.update { it.copy(step = NoticeStep.IN_CONFIRM, extractFailed = true) }
                } else {
                    _state.update {
                        it.copy(
                            step = NoticeStep.IN_CONFIRM,
                            extracted = ex,
                            // Only a type the notice itself names; never a guess.
                            selectedTypeId = ex.noticeTypeId?.takeIf { id -> it.types.any { t -> t.id == id } },
                            senderName = ex.senderName.orEmpty(),
                            senderContact = ex.senderContact.orEmpty(),
                            senderAddress = ex.senderAddress.orEmpty(),
                            senderAdvocateName = ex.senderAdvocateName.orEmpty(),
                            claimAmountIn = digitsOfAmount(ex.claimAmount),
                            responseDueDate = isoOrBlank(ex.responseDueDate),
                            receivedDate = isoOrBlank(ex.receivedDate),
                        )
                    }
                }
            }
            .onFailure { _state.update { it.copy(step = NoticeStep.IN_CONFIRM, extractFailed = true) } }
    }

    fun setSenderName(v: String) = _state.update { it.copy(senderName = v) }
    fun setSenderContact(v: String) = _state.update { it.copy(senderContact = v) }
    fun setClaimAmountIn(v: String) = _state.update { it.copy(claimAmountIn = v.filter(Char::isDigit).take(13)) }
    fun setResponseDueDate(v: String) = _state.update { it.copy(responseDueDate = v) }
    fun setInboundType(id: Int) = _state.update { it.copy(selectedTypeId = id) }

    fun submitInbound() = viewModelScope.launch {
        val s = _state.value
        if (s.selectedTypeId == null) { _state.update { it.copy(error = "Choose the notice type first.") }; return@launch }
        _state.update { it.copy(isSubmitting = true, error = null) }
        repo.create(
            fields = mapOf(
                "notice_direction" to "Inbound",
                "notice_type_id" to s.selectedTypeId?.toString(),
                "sender_name" to s.senderName,
                "sender_contact" to s.senderContact,
                "sender_address" to s.senderAddress,
                "sender_advocate_name" to s.senderAdvocateName,
                "received_date" to s.receivedDate,
                "claim_amount" to s.claimAmountIn,
                "response_due_date" to s.responseDueDate,
            ),
            file = pickedFile,
        ).onSuccess { d ->
            _state.update { it.copy(isSubmitting = false, step = NoticeStep.IN_DONE,
                createdNoticeId = d.summary.id, createdReference = d.summary.referenceNumber) }
        }.onFailure { e ->
            _state.update { it.copy(isSubmitting = false, error = e.message ?: "Submission failed") }
        }
    }

    private fun go(step: NoticeStep) = _state.update { it.copy(step = step) }

    /** Map plain-language cards to real notice types by keyword; never invent ids. */
    private fun buildProblems(types: List<NoticeType>): List<ProblemCard> {
        fun match(vararg kw: String): Int? =
            types.firstOrNull { t -> kw.any { t.name.contains(it, ignoreCase = true) } }?.id
        // No match → null: the client then picks the type themselves (never the
        // alphabetically first type standing in silently).
        val fallback: Int? = null
        return listOf(
            ProblemCard("dues", "Unpaid Cheque Or Dues",
                "Money owed to you, such as a dishonoured cheque, an unpaid invoice or a loan.",
                match("demand", "cheque", "payment") ?: fallback),
            ProblemCard("service", "Bad Service Or Faulty Product",
                "A purchase or service that went wrong.",
                match("service", "deficiency", "consumer") ?: fallback),
            ProblemCard("property", "Property Dispute",
                "Rent, deposit, possession or a boundary issue.",
                match("property", "rent", "tenan") ?: fallback),
            ProblemCard("other", "Something Else",
                "Describe it in a line — your advocate takes it from there.",
                match("other", "general") ?: fallback),
        )
    }

    /** "₹1,50,000.00" → "150000": whole rupees, digits only. */
    private fun digitsOfAmount(raw: String?): String =
        raw?.substringBefore('.')?.filter(Char::isDigit)?.take(13).orEmpty()

    /** An extracted date as yyyy-MM-dd, or blank when it can't be read reliably. */
    private fun isoOrBlank(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isBlank()) return ""
        val patterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "d/M/yyyy", "d-M-yyyy", "dd.MM.yyyy")
        for (p in patterns) {
            val d = runCatching { java.time.LocalDate.parse(t.take(10), java.time.format.DateTimeFormatter.ofPattern(p)) }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(p)) }.getOrNull()
            if (d != null) return d.toString()
        }
        return ""
    }

    private fun queryName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        var name = "document"
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
            }
        }
        return name
    }
}
