package com.legumlex.client.feature.consultations

import com.legumlex.client.data.api.ApiService
import okhttp3.MultipartBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ConsultationsRepository(private val api: ApiService) {

    suspend fun list(): Result<List<Consultation>> = runCatching {
        val response = api.consultations()
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parse)
    }

    suspend fun detail(id: String): Result<Consultation> = runCatching {
        val response = api.consultations(id = id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Consultation not found")
        parse(data)
    }

    /** Withdraw a pending self-initiated request — soft-cancel on the server. */
    suspend fun withdraw(consultationId: String): Result<Unit> = runCatching {
        api.deleteConsultation(consultationId)
        Unit
    }

    /**
     * Terminal cancel — the design's "Close consultation" sheet action.
     * Server returns 200 with the canonical closure_reason / closed_at on
     * both first call and idempotent re-fires, so callers can flip UI state
     * unconditionally on a successful Result.
     */
    suspend fun cancel(
        consultationId: String,
        reason: String? = null,
    ): Result<ClosureSummary> = runCatching {
        val response = api.cancelConsultation(consultationId, reason)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data in response")
        ClosureSummary(
            id             = data["id"].idString() ?: consultationId,
            closedAt       = (data["closed_at"] as? String).orEmpty(),
            closureReason  = (data["closure_reason"] as? String) ?: "cancelled_by_client",
            status         = (data["status"] as? String) ?: "Closed",
        )
    }

    /**
     * Parent / linked child consultation chain — used by the AHC ↔ General
     * link cards on the detail screen and the home action queue. Both sides
     * may be null when there's no linkage in that direction OR when the
     * other side isn't client_visible yet.
     */
    suspend fun linkage(consultationId: String): Result<ConsultationLinkage> = runCatching {
        val response = api.consultationLinkage(consultationId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data in response")
        ConsultationLinkage(
            id     = data["id"].idString() ?: consultationId,
            parent = parseLinkageSide(data["parent"]),
            linked = parseLinkageSide(data["linked"]),
        )
    }

    private fun parseLinkageSide(raw: Any?): LinkageSide? {
        @Suppress("UNCHECKED_CAST")
        val m = raw as? Map<String, Any?> ?: return null
        // Server returns a fully-null block when the row doesn't exist or
        // isn't client_visible. Treat that as "no link", not an empty card.
        val id = m["id"] ?: return null
        return LinkageSide(
            id            = id.idString() ?: return null,
            kind          = ConsultationKind.from(m["kind"] as? String),
            title         = m["title"] as? String,
            status        = m["status"] as? String,
            closedAt      = m["closed_at"] as? String,
            closureReason = m["closure_reason"] as? String,
        )
    }

    /**
     * Update the client's preferred slot. Either field may be null but at
     * least one must be non-null — the server validates and rejects the empty
     * case with 422.
     */
    suspend fun updatePreferred(
        consultationId: String,
        preferredDate: String?,
        preferredTime: String?,
    ): Result<Unit> = runCatching {
        api.updateConsultationPreferred(consultationId, preferredDate, preferredTime)
        Unit
    }

    /** Delete a client-uploaded attachment from the consultation. */
    suspend fun deleteAttachment(fileId: String): Result<Unit> = runCatching {
        api.deleteConsultationAttachment(fileId)
        Unit
    }

    /** Client-initiated waive of a doc-request with a reason. */
    suspend fun declineDocRequest(requestId: String, reason: String): Result<Unit> = runCatching {
        api.declineDocRequest(requestId, reason)
        Unit
    }

    /** Stream the AHC source PDF — caller is responsible for closing the body. */
    suspend fun downloadAhcDocument(consultationId: String) =
        api.downloadAhcDocument(consultationId)

    /** Stream a fulfilled doc-request file — caller closes the body. */
    suspend fun downloadDocRequestFile(requestId: String) =
        api.downloadDocRequestFile(requestId)

    suspend fun create(
        type: String,
        tag: String,
        note: String,
        isUrgent: Boolean,
        priority: String,
        preferredDate: String,
        preferredTime: String,
        caseLinkId: String,
    ): Result<String> = runCatching {
        val response = api.createConsultation(
            type          = type,
            tag           = tag,
            note          = note,
            isUrgent      = if (isUrgent) 1 else 0,
            priority      = priority,
            preferredDate = preferredDate,
            preferredTime = preferredTime,
            caseLinkId    = caseLinkId,
        )
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data in response")
        data["id"].idString() ?: error("No ID in response")
    }

    suspend fun docRequests(consultationId: String): Result<DocRequestBundle> = runCatching {
        val response = api.consultationDocRequests(consultationId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data")
        @Suppress("UNCHECKED_CAST")
        val items = (data["items"] as? List<Map<String, Any?>>).orEmpty().map(::parseDocRequestItem)
        DocRequestBundle(
            items    = items,
            total    = data["total"].asIntOrNull() ?: items.size,
            uploaded = data["uploaded"].asIntOrNull() ?: 0,
            waived   = data["waived"].asIntOrNull() ?: 0,
            pending  = data["pending"].asIntOrNull() ?: 0,
        )
    }

    /**
     * Upload a file fulfilling a pending doc-request. Returns the refreshed
     * DocRequestItem so the screen can patch the row in place without a full
     * checklist refetch.
     */
    suspend fun fulfillDocRequest(
        requestId: String,
        part: MultipartBody.Part,
    ): Result<DocRequestItem> = runCatching {
        val response = api.fulfillDocRequest(requestId, part)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error(response["message"]?.toString() ?: "Upload failed")
        parseDocRequestItem(data)
    }

    /** List files exchanged on the consultation (staff-shared + own uploads). */
    suspend fun attachments(consultationId: String): Result<List<ConsultationAttachment>> = runCatching {
        val response = api.consultationAttachments(consultationId)
        @Suppress("UNCHECKED_CAST")
        val rows = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        rows.map(::parseAttachment)
    }

    /** Upload an attachment authored by the client. Returns the new row. */
    suspend fun uploadAttachment(
        consultationId: String,
        part: MultipartBody.Part,
    ): Result<ConsultationAttachment> = runCatching {
        val response = api.uploadConsultationAttachment(consultationId, part)
        @Suppress("UNCHECKED_CAST")
        val row = response["data"] as? Map<String, Any?>
            ?: error(response["message"]?.toString() ?: "Upload failed")
        parseAttachment(row)
    }

    suspend fun appointment(consultationId: String): Result<AppointmentData?> = runCatching {
        val response = api.consultationAppointment(consultationId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: return@runCatching null
        parseAppointmentData(data)
    }

    suspend fun ahcAnalysis(consultationId: String): Result<AhcAnalysis?> = runCatching {
        val response = api.consultationAhc(consultationId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: return@runCatching null
        parseAhcAnalysis(data)
    }

    suspend fun sendReply(consultationId: String, message: String): Result<Reply> = runCatching {
        val response = api.sendConsultationReply(consultationId, message)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Reply was not echoed back")
        parseReply(data)
    }
}

private fun parse(m: Map<String, Any?>): Consultation {
    val phase           = m["phase"]            as? String ?: "consultation"
    val rawStatus       = m["status"]           as? String ?: "Open"
    val requestStatus   = m["request_status"]   as? String ?: "pending"
    val acknowledgedAt  = parseDateTime(m["acknowledged_at"] as? String)
    val linkedCaseId    = m["case_link_id"].asLongOrNull()?.takeIf { it > 0 }?.toString()
    val kind            = ConsultationKind.from(m["consultation_type"] as? String)
    val pendingDocs     = m["pending_doc_count"].asIntOrNull() ?: 0
    val totalDocs       = m["total_doc_count"].asIntOrNull() ?: 0
    val unreadReplies   = m["has_unread_replies"].asBool()
    val scheduledDate   = parseDate(m["date"] as? String)
    val scheduledTime   = parseTime(m["time"] as? String)
    val now             = java.time.LocalDateTime.now()

    val status = deriveStatus(
        phase = phase,
        rawStatus = rawStatus,
        requestStatus = requestStatus,
        // An advocate who has written has taken it up, acknowledged_at or not.
        acknowledged = acknowledgedAt != null || (m["advocate_reply_count"].asIntOrNull() ?: 0) > 0,
        linkedToCase = linkedCaseId != null,
        kind = kind,
        pendingDocs = pendingDocs,
        scheduledDate = scheduledDate,
        scheduledTime = scheduledTime,
        now = now,
    )

    @Suppress("UNCHECKED_CAST")
    val repliesRaw = (m["replies"] as? List<Map<String, Any?>>).orEmpty()
    @Suppress("UNCHECKED_CAST")
    val docsRaw    = (m["documents"] as? List<Map<String, Any?>>).orEmpty()

    return Consultation(
        id               = m["id"].idString() ?: "",
        tag              = (m["tag"]  as? String)?.takeIf { it.isNotBlank() },
        // The consultation's text as the client may read it (the portal's rule,
        // applied by the server): their own enquiry, their brief to the Digital
        // Office, or the note where the portal shows one. Never the intake note
        // written for the advocate.
        note             = listOf(m["enquiry_text"], m["brief"], m["note"])
            .firstNotNullOfOrNull { (it as? String)?.takeIf { s -> s.isNotBlank() } },
        phase            = phase,
        kind             = kind,
        status           = status,
        priority         = (m["priority"] as? String)?.takeIf { it.isNotBlank() },
        type             = (m["consultation_type"] as? String)?.takeIf { it.isNotBlank() },
        isUrgent         = m["is_urgent"].asBool(),
        createdAt        = parseDateTime(m["date_added"]   as? String),
        lastUpdatedAt    = parseDateTime(m["last_updated"] as? String),
        scheduledDate    = scheduledDate,
        scheduledTime    = scheduledTime,
        preferredDate    = parseDate(m["preferred_date"] as? String),
        preferredTime    = parseTime(m["preferred_time"] as? String),
        acknowledgedAt   = acknowledgedAt,
        assignedTo       = (m["assigned_to"] as? String)?.trim()?.takeIf { it.isNotBlank() },
        assignedToDesignation = (m["assigned_to_designation"] as? String)?.takeIf { it.isNotBlank() },
        assignedToAvatarUrl   = (m["assigned_to_avatar_url"] as? String)?.takeIf { it.isNotBlank() },
        assignedStaffId       = m["assigned_staff_id"].asIntOrNull()?.takeIf { it > 0 },
        linkedCaseId     = linkedCaseId,
        linkedCaseTitle  = (m["linked_case_title"]  as? String)?.takeIf { it.isNotBlank() },
        linkedCaseNumber = (m["linked_case_number"] as? String)?.takeIf { it.isNotBlank() },
        replyCount       = m["reply_count"].asIntOrNull() ?: repliesRaw.size,
        hasUnreadReplies = unreadReplies,
        pendingDocCount  = pendingDocs,
        totalDocCount    = totalDocs,
        riskLevel        = RiskLevel.from(m["risk_level"] as? String),
        riskScore        = m["risk_score"].asIntOrNull(),
        flaggedClauseCount = m["flagged_clause_count"].asIntOrNull(),
        replies          = repliesRaw.map(::parseReply),
        documents        = docsRaw.map(::parseConsultationDoc),
        clientInitiated  = m["client_initiated"].asBool(),
        requestStatus    = requestStatus,
        invoiceId        = m["invoice_id"].asLongOrNull()?.takeIf { it > 0 }?.toString(),
        invoiceNumber    = (m["invoice_number"] as? String)?.takeIf { it.isNotBlank() }
                            ?: m["invoice_number"]?.toString()?.takeIf { it.isNotBlank() },
        invoiceStatus    = (m["invoice_status"] as? String)?.takeIf { it.isNotBlank() }
                            ?: m["invoice_status"]?.toString()?.takeIf { it.isNotBlank() },
        invoiceTotal     = m["invoice_total"].asDoubleOrNull(),
        // Released / Escalated / Closure — added with the Agreement Check Workflow
        // detail surfaces. All optional; null while the consultation is live.
        releasedAt           = parseDateTime(m["released_at"]   as? String),
        closedAt             = parseDateTime(m["closed_at"]     as? String),
        closureReason        = (m["closure_reason"] as? String)?.takeIf { it.isNotBlank() },
        closureNote          = (m["closure_note"]   as? String)?.takeIf { it.isNotBlank() },
        isEscalated          = m["is_escalated"].asBool(),
        escalationReason     = (m["escalation_reason"] as? String)?.takeIf { it.isNotBlank() },
        escalatedAt          = parseDateTime(m["escalated_at"] as? String),
        linkedConsultationId = m["linked_consultation_id"].asLongOrNull()?.takeIf { it > 0 }?.toString(),
        parentConsultationId = m["parent_consultation_id"].asLongOrNull()?.takeIf { it > 0 }?.toString(),
        advocateNote         = (m["advocate_note"] as? String)?.takeIf { it.isNotBlank() },
        funnelStep           = (m["funnel_step"] as? String)?.takeIf { it.isNotBlank() },
        funnelLabel          = ((m["funnel_view"] as? Map<*, *>)?.get("label") as? String)?.takeIf { it.isNotBlank() },
        funnelAction         = ((m["funnel_view"] as? Map<*, *>)?.get("action") as? String)?.takeIf { it.isNotBlank() },
        funnelDetail         = ((m["funnel_view"] as? Map<*, *>)?.get("detail") as? String)?.takeIf { it.isNotBlank() },
        paymentStatus        = (m["payment_status"] as? String)?.takeIf { it.isNotBlank() },
        feeInvoiceId         = m["fee_invoice_id"].asLongOrNull()?.takeIf { it > 0 }?.toString(),
        unreadCount          = m["unread_count"].asIntOrNull() ?: if (unreadReplies) 1 else 0,
    )
}

private fun parseReply(m: Map<String, Any?>) = Reply(
    id          = m["id"].idString() ?: "",
    message     = (m["message"] as? String).orEmpty(),
    fromClient  = m["from_client"].asBool(),
    authorName  = (m["author_name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (m["from_client"].asBool()) "You" else "Your advocate",
    authorImage = (m["author_image"] as? String)?.takeIf { it.isNotBlank() },
    createdAt   = parseDateTime(m["date_added"] as? String),
    readAt      = parseDateTime(m["read_at"] as? String),
)

private fun parseConsultationDoc(m: Map<String, Any?>) = ConsultationDoc(
    id            = m["id"].idString() ?: "",
    fileName      = m["file_name"] as? String ?: "",
    displayName   = (m["display_name"] as? String)?.takeIf { it.isNotBlank() }
                      ?: (m["file_name"] as? String) ?: "Untitled",
    fileExtension = (m["file_extension"] as? String)?.takeIf { it.isNotBlank() },
    mimeType      = (m["filetype"] as? String)?.takeIf { it.isNotBlank() },
    tag           = (m["tag"] as? String)?.takeIf { it.isNotBlank() },
    uploadedAt    = parseDateTime(m["dateadded"] as? String),
)

private fun deriveStatus(
    phase: String,
    rawStatus: String,
    requestStatus: String,
    acknowledged: Boolean,
    linkedToCase: Boolean,
    kind: ConsultationKind,
    pendingDocs: Int,
    scheduledDate: java.time.LocalDate?,
    scheduledTime: java.time.LocalTime?,
    now: java.time.LocalDateTime,
): ConsultationStatus = when {
    // Video sessions (digital_office) are never "converted to a case" — a stray
    // litigation phase or case link must not hijack them into the Converted
    // screen. They close as Completed/Resolved instead (handled below).
    (linkedToCase || phase == "litigation") && kind != ConsultationKind.DIGITAL_OFFICE
                                                   -> ConsultationStatus.CONVERTED
    requestStatus == "rejected"                    -> ConsultationStatus.DECLINED
    rawStatus.equals("Cancelled", ignoreCase = true) -> ConsultationStatus.CANCELLED
    // A closed/resolved video session reads as Completed, never Closed/Converted.
    kind == ConsultationKind.DIGITAL_OFFICE &&
        (rawStatus.equals("Closed", ignoreCase = true) || rawStatus.equals("Resolved", ignoreCase = true))
                                                   -> ConsultationStatus.COMPLETED
    rawStatus.equals("Closed",    ignoreCase = true) -> ConsultationStatus.CLOSED
    rawStatus.equals("Resolved",  ignoreCase = true) -> ConsultationStatus.COMPLETED
    // A released report is the answer given: the portal counts it resolved
    // (Mylex_model::consult_pill), and Home's figures do too.
    rawStatus.equals("Released",  ignoreCase = true) -> ConsultationStatus.COMPLETED
    // Digital Office "live now" — within meeting hour-window
    kind == ConsultationKind.DIGITAL_OFFICE && scheduledDate != null -> {
        val sched = java.time.LocalDateTime.of(scheduledDate, scheduledTime ?: java.time.LocalTime.NOON)
        val mins = java.time.temporal.ChronoUnit.MINUTES.between(now, sched)
        when {
            mins in -60..15 -> ConsultationStatus.LIVE_NOW
            mins > 0        -> ConsultationStatus.SCHEDULED
            else            -> ConsultationStatus.COMPLETED
        }
    }
    requestStatus == "scheduled"                   -> ConsultationStatus.SCHEDULED
    kind == ConsultationKind.DOC_REQUEST && pendingDocs > 0 -> ConsultationStatus.AWAITING_DOCS
    // "Acknowledged" must mean an advocate actually acknowledged it — i.e.
    // acknowledged_at is set. AHC (and other auto-created) consultations are
    // born with request_status='approved' before any human has looked at them,
    // so keying "Acknowledged" off request_status alone mislabels brand-new
    // requests. Approved-but-not-yet-acknowledged stays "Pending review".
    requestStatus == "approved" && acknowledged    -> ConsultationStatus.IN_PROGRESS
    acknowledged                                   -> ConsultationStatus.ACKNOWLEDGED
    else                                           -> ConsultationStatus.PENDING_REVIEW
}

private fun parseDocRequestItem(m: Map<String, Any?>) = DocRequestItem(
    id                  = m["id"].idString() ?: "",
    documentName        = (m["document_name"] as? String).orEmpty(),
    description         = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    isRequired          = m["is_required"]?.let { it.asBool() } ?: true,
    status              = DocRequestStatus.from(m["status"] as? String),
    fulfilledFileName   = (m["fulfilled_file_name"] as? String)?.takeIf { it.isNotBlank() },
    fulfilledDisplayName= (m["fulfilled_display_name"] as? String)?.takeIf { it.isNotBlank() },
    fulfilledAt         = parseDateTime(m["fulfilled_at"] as? String),
    // The server tags a client's own reason "[Client] "; show the reason itself.
    waivedReason        = (m["waived_reason"] as? String)?.removePrefix("[Client]")?.trim()?.takeIf { it.isNotBlank() },
    createdAt           = parseDateTime(m["created_at"] as? String),
    fileSizeBytes       = (m["fulfilled_file_size"] as? Number)?.toLong(),
    fileKind            = (m["fulfilled_file_kind"] as? String)?.takeIf { it.isNotBlank() },
    uploadedBy          = (m["uploaded_by"] as? String)?.takeIf { it.isNotBlank() },
    requestedAt         = parseDateTime(m["requested_at"] as? String),
    waivedBy            = (m["waived_by"] as? Number)?.toInt(),
)

private fun parseAttachment(m: Map<String, Any?>) = ConsultationAttachment(
    id          = m["id"].idString() ?: "",
    displayName = (m["display_name"] as? String)?.takeIf { it.isNotBlank() }
        ?: (m["file_name"] as? String).orEmpty(),
    fileName    = (m["file_name"] as? String).orEmpty(),
    fileType    = (m["filetype"] as? String).orEmpty(),
    tag         = (m["tag"] as? String)?.takeIf { it.isNotBlank() },
    fromClient  = m["from_client"].asBool(),
    uploadedBy  = (m["uploaded_by"] as? String)?.takeIf { it.isNotBlank() } ?: "—",
    uploadedAt  = parseDateTime(m["uploaded_at"] as? String),
)

private fun parseAppointmentData(m: Map<String, Any?>) = AppointmentData(
    id              = m["id"].idString() ?: "",
    date            = parseDate(m["appointment_date"] as? String),
    startTime       = parseTime(m["start_time"] as? String),
    endTime         = parseTime(m["end_time"] as? String),
    durationMinutes = m["duration_minutes"].asIntOrNull(),
    status          = AppointmentStatus.from(m["status"] as? String),
    notes           = (m["notes"] as? String)?.takeIf { it.isNotBlank() },
    meetLink        = (m["meet_link"] as? String)?.takeIf { it.isNotBlank() },
    staffName       = (m["staff_name"] as? String)?.trim()?.takeIf { it.isNotBlank() },
    serviceName     = (m["service_name"] as? String)?.takeIf { it.isNotBlank() },
    sessionStartedAt= parseDateTime(m["session_started_at"] as? String),
    sessionEndedAt  = parseDateTime(m["session_ended_at"] as? String),
)

/* Moshi deserializes every JSON number in an untyped Map as a Double, so a
 * value like 45 arrives as 45.0 — "45.0".toIntOrNull() is null. These coerce
 * Number/Double/String forms safely. Use them for ALL numeric JSON fields. */
private fun Any?.asIntOrNull(): Int? = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull() ?: toDoubleOrNull()?.toInt()
    else      -> null
}
private fun Any?.asLongOrNull(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull() ?: toDoubleOrNull()?.toLong()
    else      -> null
}
/** An id as the server meant it: 45.0 (Moshi's Double) reads "45", never "45.0". */
private fun Any?.idString(): String? = when (this) {
    is Number -> toLong().toString()
    is String -> takeIf { it.isNotBlank() }?.let { it.toDoubleOrNull()?.toLong()?.toString() ?: it }
    else      -> null
}
private fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else      -> null
}
private fun Any?.asBool(): Boolean = when (this) {
    is Boolean -> this
    is Number  -> toInt() == 1
    is String  -> this == "1" || equals("true", true) || toDoubleOrNull()?.toInt() == 1
    else       -> false
}

/* Findings arrive as objects { title, detail } (current) or as plain strings
 * (legacy reports). Coerce both to AhcFindingItem so the UI is uniform. */
private fun parseFindings(raw: Any?): List<AhcFindingItem> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        when (item) {
            is String -> item.trim().takeIf { it.isNotBlank() }?.let { AhcFindingItem(it, "") }
            is Map<*, *> -> {
                val t = (item["title"] as? String)?.trim().orEmpty()
                val d = (item["detail"] as? String)?.trim().orEmpty()
                when {
                    t.isNotBlank() -> AhcFindingItem(t, d)
                    d.isNotBlank() -> AhcFindingItem(d, "")  // detail-only → promote to title
                    else -> null
                }
            }
            else -> null
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun parseAhcAnalysis(m: Map<String, Any?>) = AhcAnalysis(
    riskScore      = m["risk_score"].asIntOrNull(),
    riskLevel      = RiskLevel.from(m["risk_level"] as? String),
    agreementName  = (m["agreement_name"] as? String)?.takeIf { it.isNotBlank() },
    teaserSummary  = (m["teaser_summary"] as? String)?.takeIf { it.isNotBlank() },
    keyRisks       = parseFindings(m["key_risks"]),
    missingClauses = parseFindings(m["missing_clauses"]),
    recommendations= (m["recommendations"] as? List<*>).orEmpty().mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } },
    processedAt    = parseDateTime(m["processed_at"] as? String),
    sessionStatus  = (m["session_status"] as? String)?.takeIf { it.isNotBlank() },
    completedAt    = parseDateTime(m["completed_at"] as? String),
    agreementSizeBytes = (m["document"] as? Map<*, *>)?.get("size").asLongOrNull(),
    agreementPageCount = (m["document"] as? Map<*, *>)?.get("page_count").asIntOrNull(),
    agreementType    = (m["context"] as? Map<*, *>)?.get("agreement_type")?.let { it as? String }?.takeIf { it.isNotBlank() },
    parties          = (m["context"] as? Map<*, *>)?.get("parties")?.let { it as? String }?.takeIf { it.isNotBlank() },
    agreementSummary = (m["context"] as? Map<*, *>)?.get("summary")?.let { it as? String }?.takeIf { it.isNotBlank() },
    released         = m["released"].asBool(),
)

// --- date helpers ---------------------------------------------------------

private val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
private val timePatterns = listOf("HH:mm:ss", "HH:mm", "h:mm a", "hh:mm a")
private val datetimePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

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
