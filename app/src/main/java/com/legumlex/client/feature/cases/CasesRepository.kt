package com.legumlex.client.feature.cases

import com.legumlex.client.data.api.ApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class CasesRepository(private val api: ApiService) {

    suspend fun list(filter: CaseFilter = CaseFilter.ALL): Result<List<CaseSummary>> = runCatching {
        val response = api.cases(status = filter.apiValue)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseSummary)
    }

    suspend fun detail(id: String): Result<CaseDetail> = runCatching {
        val response = api.cases(id = id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Case not found")
        parseDetail(data)
    }

    suspend fun timeline(caseId: String): Result<List<TimelineEvent>> = runCatching {
        val response = api.caseTimeline(caseId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseTimelineEvent)
    }

    /** Stream an order's certified-copy PDF — caller closes the body. */
    suspend fun downloadOrderDocument(orderId: String) = api.downloadCaseOrderDocument(orderId)

    /** Stream a client-visible case file — caller closes the body. */
    suspend fun downloadCaseDocument(fileId: String) = api.downloadCaseDocument(fileId)
}

// --- parsers ---------------------------------------------------------------

private fun parseSummary(m: Map<String, Any?>): CaseSummary = CaseSummary(
    id              = m["id"]?.toString() ?: "",
    title           = m["case_title"] as? String ?: "Untitled",
    number          = m["case_number"] as? String ?: "",
    status          = CaseStatus.from(m["case_status"] as? String ?: m["status"] as? String),
    courtName       = (m["court_name"] as? String)?.takeIf { it.isNotBlank() },
    courtNo         = (m["court_no"] as? String)?.takeIf { it.isNotBlank() },
    judgeName       = (m["judge_name"] as? String)?.takeIf { it.isNotBlank() },
    filedDate       = parseDate(m["date_filed"] as? String),
    hearingCount    = (m["hearing_count"]?.toString()?.toIntOrNull()) ?: 0,
    nextHearingDate = parseDate(m["next_hearing_date"] as? String),
    nextHearingTime = parseTime(m["next_hearing_time"] as? String),
    nextHearingPurpose = (m["next_hearing_purpose"] as? String)?.takeIf { it.isNotBlank() },
    lastActivityAt  = parseDateTime((m["last_updated"] as? String) ?: (m["date_created"] as? String)),
    disposalType    = (m["disposal_type"] as? String)?.takeIf { it.isNotBlank() },
    disposalDate    = parseDate(m["disposal_date"] as? String),
)

@Suppress("UNCHECKED_CAST")
private fun parseDetail(m: Map<String, Any?>): CaseDetail {
    val summary = parseSummary(m)

    val partiesMap = m["parties"] as? Map<String, Any?>
    val petitioners = (partiesMap?.get("petitioners") as? List<Map<String, Any?>>).orEmpty().map(::parseParty)
    val respondents = (partiesMap?.get("respondents") as? List<Map<String, Any?>>).orEmpty().map(::parseParty)

    val upcoming = (m["hearings_upcoming"] as? List<Map<String, Any?>>).orEmpty().map(::parseHearing)
    val past     = (m["hearings_past"]     as? List<Map<String, Any?>>).orEmpty().map(::parseHearing)

    val acts  = (m["acts"]  as? List<Map<String, Any?>>).orEmpty().map(::parseAct)
    val orders = (m["orders"] as? List<Map<String, Any?>>).orEmpty().map(::parseOrder)
    val docs  = (m["documents"] as? List<Map<String, Any?>>).orEmpty().map(::parseCaseFile)

    return CaseDetail(
        summary             = summary,
        filedDate           = parseDate(m["date_filed"] as? String),
        court               = summary.courtName,
        courtHierarchy      = (m["court_hierarchy"] as? String)?.takeIf { it.isNotBlank() },
        courtNumber         = (m["court_no"] as? String)?.takeIf { it.isNotBlank() },
        benchType           = (m["bench_type"] as? String)?.takeIf { it.isNotBlank() },
        judge               = summary.judgeName,
        advocate            = (m["advocate_name"] as? String)?.takeIf { it.isNotBlank() },
        advocateDesignation = (m["advocate_designation"] as? String)?.takeIf { it.isNotBlank() },
        advocateEmail       = (m["advocate_email"] as? String)?.takeIf { it.isNotBlank() },
        advocateAvatarUrl   = (m["advocate_image"] as? String)?.takeIf { it.isNotBlank() },
        originConsultationId = (m["consultation_id"]?.toString())?.takeIf { it.isNotBlank() && it != "0" },
        disposalReason      = (m["disposal_reason"] as? String)?.takeIf { it.isNotBlank() },
        parties             = Parties(petitioners, respondents),
        acts                = acts,
        orders              = orders,
        documents           = docs,
        upcomingHearings    = upcoming,
        pastHearings        = past,
    )
}

private fun parseParty(m: Map<String, Any?>) = Party(
    name         = m["name"] as? String ?: "",
    address      = (m["address"] as? String)?.takeIf { it.isNotBlank() },
    advocateName = (m["advocate_name"] as? String)?.takeIf { it.isNotBlank() },
    role         = (m["role"] as? String)?.takeIf { it.isNotBlank() },
)

private fun parseAct(m: Map<String, Any?>) = CaseAct(
    name     = m["act_name"] as? String ?: "",
    sections = (m["sections"] as? String)?.takeIf { it.isNotBlank() },
)

private fun parseOrder(m: Map<String, Any?>) = CaseOrder(
    id          = m["id"]?.toString() ?: "",
    date        = parseDate(m["order_date"] as? String),
    type        = (m["order_type"] as? String)?.takeIf { it.isNotBlank() } ?: "order",
    relief      = (m["relief"] as? String)?.takeIf { it.isNotBlank() },
    summary     = (m["order_summary"] as? String)?.takeIf { it.isNotBlank() },
    fullText    = (m["full_text"] as? String)?.takeIf { it.isNotBlank() },
    coram       = (m["coram"] as? String)?.takeIf { it.isNotBlank() },
    nextDate    = parseDate(m["next_date"] as? String),
    purposeNext = (m["purpose_next"] as? String)?.takeIf { it.isNotBlank() },
    hasDocument = (m["has_document"] as? Boolean) ?: ((m["has_document"]?.toString() == "1") || (m["has_document"]?.toString() == "true")),
    documentName = (m["document_name"] as? String)?.takeIf { it.isNotBlank() },
    ocrStatus   = (m["ocr_status"] as? String)?.takeIf { it.isNotBlank() } ?: "done",
)

private fun parseCaseFile(m: Map<String, Any?>) = CaseFile(
    id       = m["id"]?.toString() ?: "",
    name     = (m["display_name"] as? String)?.takeIf { it.isNotBlank() }
                ?: (m["file_name"] as? String) ?: "Document",
    fileType = (m["filetype"] as? String)?.takeIf { it.isNotBlank() },
    date     = parseDateTime(m["dateadded"] as? String)?.toLocalDate(),
    tag      = (m["tag"] as? String)?.takeIf { it.isNotBlank() } ?: "Document",
    relation = (m["relation"] as? String)?.takeIf { it.isNotBlank() } ?: "case",
    hearingDate = parseDate(m["hearing_date"] as? String),
)

private fun parseHearing(m: Map<String, Any?>) = HearingRow(
    id      = m["id"]?.toString() ?: "",
    date    = parseDate(m["date"] as? String),
    time    = parseTime(m["time"] as? String),
    status  = (m["status"] as? String)?.takeIf { it.isNotBlank() } ?: "Scheduled",
    purpose = (m["hearing_purpose"] as? String)?.takeIf { it.isNotBlank() },
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    adjournmentReason = (m["adjournment_reason"] as? String)?.takeIf { it.isNotBlank() },
    nextDate = parseDate(m["next_date"] as? String),
)

private fun parseTimelineEvent(m: Map<String, Any?>) = TimelineEvent(
    type        = m["type"] as? String ?: "activity",
    at          = parseDateTime(m["date"] as? String),
    title       = m["title"] as? String ?: "",
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
)

// --- date helpers (shared between Cases parsers; tolerant of Perfex formats) -

private val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
private val timePatterns = listOf("HH:mm:ss", "HH:mm", "h:mm a", "hh:mm a")
private val datetimePatterns = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm",
)

private fun parseDate(s: String?): LocalDate? {
    if (s.isNullOrBlank()) return null
    for (p in datePatterns) {
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun parseTime(s: String?): LocalTime? {
    if (s.isNullOrBlank()) return null
    for (p in timePatterns) {
        try { return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun parseDateTime(s: String?): LocalDateTime? {
    if (s.isNullOrBlank()) return null
    for (p in datetimePatterns) {
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}
