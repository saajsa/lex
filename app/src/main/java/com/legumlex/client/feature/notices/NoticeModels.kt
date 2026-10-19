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
)

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
    DEADLINE("Deadline — soonest first", "Don't miss a response window"),
    RECENT("Recent activity", "Most recently updated"),
    AMOUNT("Claim amount — highest", null),
    STATUS("Status", null),
}

/** Priority bands the list groups into (when grouping is on). */
enum class NoticeBand(val label: String) {
    NEEDS_YOU("Needs you"), IN_PROGRESS("In progress"), CLOSED("Closed")
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
