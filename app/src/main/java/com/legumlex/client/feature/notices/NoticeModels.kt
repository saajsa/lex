package com.legumlex.client.feature.notices

import java.time.LocalDate
import java.time.LocalDateTime

/** Direction is the organising axis (and colour): outbound = slate, inbound = amber. */
enum class NoticeDirection { OUTBOUND, INBOUND;
    companion object {
        fun from(s: String?) = if (s?.lowercase() == "inbound") INBOUND else OUTBOUND
    }
}

/** A statutory notice type (from tblcase_notice_types) — never invented client-side. */
data class NoticeType(
    val id: Int,
    val name: String,
    val waitingPeriodDays: Int,
    val description: String?,
)

data class NoticeDocument(
    val id: String,
    val name: String,
    val tag: String?,
    val filetype: String?,
)

/** Compact row for the list. A root (inbound or standalone outbound) may carry
 *  its response notices as [children] — the outbound reply nests under the inbound. */
data class NoticeSummary(
    val id: String,
    val referenceNumber: String?,
    val direction: NoticeDirection,
    val status: String,          // raw server status
    val statusLabel: String,     // humanised
    val isClosed: Boolean,
    val noticeType: String?,
    val counterparty: String,    // recipient (out) or sender (in)
    val claimAmount: Double?,
    val responseDueDate: LocalDate?,
    val createdAt: LocalDateTime?,
    val needsApproval: Boolean,
    val parentNoticeId: String? = null,
    val isResponse: Boolean = false,
    val children: List<NoticeSummary> = emptyList(),
    val subject: String? = null,
    val isServed: Boolean = false,
)

/** One line of the notice's own history, oldest first. */
data class NoticeActivity(val title: String, val date: LocalDate?, val detail: String?)

/** Full tracking detail. */
data class NoticeDetail(
    val summary: NoticeSummary,
    val recipientName: String?,
    val recipientAddress: String?,
    val recipientContact: String?,
    val recipientEmail: String?,
    val senderName: String?,
    val senderAddress: String?,
    val senderContact: String?,
    val senderAdvocateName: String?,
    val signingAdvocateName: String?,
    val serviceMethod: String?,
    val trackingNumber: String?,
    val courierServiceName: String?,
    val dispatchDate: LocalDate?,
    val deliveryDate: LocalDate?,
    val deliveryStatus: String?,
    val waitingPeriodDays: Int,
    val notes: String?,
    val consultationId: String?,
    val convertedToCase: Boolean,
    val caseId: String?,
    val approvedAt: LocalDateTime?,
    val documents: List<NoticeDocument>,
    val parent: NoticeSummary? = null,      // set when this notice is a response
    val children: List<NoticeSummary> = emptyList(),  // response notices nested under this
    val subject: String? = null,
    val receivedDate: LocalDate? = null,
    /** Service the law presumes (e.g. a refused or unclaimed post), when recorded. */
    val deemedServedDate: LocalDate? = null,
    /** The day the statutory waiting period runs out. */
    val waitingEndDate: LocalDate? = null,
    val isServed: Boolean = false,
    val returnDate: LocalDate? = null,
    val returnReason: String? = null,
    val receivedByPerson: String? = null,
    /** The portal's "what happens next" sentence, verbatim; null when empty. */
    val nextStep: String? = null,
    val activity: List<NoticeActivity> = emptyList(),
)

/** AI-extracted fields from an uploaded inbound notice (preview, pre-confirm). */
data class ExtractedNotice(
    val senderName: String?,
    val senderAddress: String?,
    val senderContact: String?,
    val senderAdvocateName: String?,
    val noticeTypeId: Int?,
    val claimAmount: String?,
    val responseDueDate: String?,
    val receivedDate: String?,
)

/** Sort modes offered in the sort sheet. */
enum class NoticeSort(val label: String, val sub: String?) {
    DEADLINE("Deadline — Soonest First", "Reply dates nearest first"),
    RECENT("Recent Activity", "Most recently updated"),
    AMOUNT("Claim Amount — Highest", null),
    STATUS("Status", null),
}

/** Priority bands the list groups into (when grouping is on). */
enum class NoticeBand(val label: String) {
    NEEDS_YOU("Needs You"), IN_PROGRESS("In Progress"), CLOSED("Closed")
}

/** A rendered section — a band header (null when grouping is off) + its root threads. */
data class NoticeSection(val band: NoticeBand?, val roots: List<NoticeSummary>)

sealed interface NoticesUiState {
    data object Loading : NoticesUiState
    data class Ready(
        val sections: List<NoticeSection>,
        val total: Int,        // every notice incl. nested responses
        val threadCount: Int,  // root threads
    ) : NoticesUiState
    data class Error(val message: String) : NoticesUiState
}

sealed interface NoticeDetailUiState {
    data object Loading : NoticeDetailUiState
    data class Ready(val notice: NoticeDetail) : NoticeDetailUiState
    data class Error(val message: String) : NoticeDetailUiState
}

/** List filter chips: All · Outbound · Inbound · Active · Closed. */
enum class NoticeFilter(val label: String) {
    ALL("All"), OUTBOUND("Outbound"), INBOUND("Inbound"), ACTIVE("Active"), CLOSED("Closed")
}
