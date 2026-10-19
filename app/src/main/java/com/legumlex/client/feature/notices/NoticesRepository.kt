package com.legumlex.client.feature.notices

import com.legumlex.client.data.api.ApiService
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NoticesRepository(private val api: ApiService) {

    suspend fun loadList(): Result<List<NoticeSummary>> = runCatching {
        rows(api.notices()).map(::parseSummary)
    }

    suspend fun loadDetail(id: String): Result<NoticeDetail> = runCatching {
        val m = obj(api.notice(id)) ?: error("Notice not found")
        parseDetail(m)
    }

    suspend fun loadTypes(): Result<List<NoticeType>> = runCatching {
        rows(api.noticeTypes()).map {
            NoticeType(
                id = num(it["id"]).toInt(),
                name = (it["name"] as? String).orEmpty(),
                waitingPeriodDays = num(it["waiting_period_days"]).toInt(),
                description = it["description"] as? String,
            )
        }
    }

    suspend fun extract(name: String, mime: String, bytes: ByteArray): Result<ExtractedNotice?> = runCatching {
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", name, body)
        val resp = api.extractNotice(part)
        if (resp["status"] == false) return@runCatching null
        val m = obj(resp) ?: return@runCatching null
        ExtractedNotice(
            senderName = m["sender_name"] as? String,
            senderAddress = m["sender_address"] as? String,
            senderContact = m["sender_contact_number"] as? String,
            senderAdvocateName = m["sender_advocate_name"] as? String,
            noticeTypeId = (m["notice_type_id"] as? Number)?.toInt(),
            claimAmount = m["claim_amount"]?.toString(),
            responseDueDate = m["response_due_date"] as? String,
            receivedDate = m["received_date"] as? String,
        )
    }

    suspend fun create(
        fields: Map<String, String?>,
        file: Triple<String, String, ByteArray>? = null,   // name, mime, bytes
    ): Result<NoticeDetail> = runCatching {
        val parts = buildList {
            fields.forEach { (k, v) -> if (!v.isNullOrBlank()) add(MultipartBody.Part.createFormData(k, v)) }
            file?.let { (name, mime, bytes) ->
                add(MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaTypeOrNull())))
            }
        }
        val resp = api.createNotice(parts)
        if (resp["status"] == false) error((resp["message"] as? String) ?: "Could not create the notice")
        parseDetail(obj(resp) ?: error("Malformed response"))
    }

    suspend fun approve(id: String): Result<NoticeDetail> = runCatching {
        val resp = api.approveNotice(id)
        if (resp["status"] == false) error((resp["message"] as? String) ?: "Could not approve")
        parseDetail(obj(resp) ?: error("Malformed response"))
    }

    suspend fun downloadDocument(noticeId: String, fileId: String): okhttp3.ResponseBody =
        api.downloadNoticeDocument(noticeId, fileId)

    // ------------------------------------------------------------------ //

    private fun parseSummary(m: Map<String, Any?>) = NoticeSummary(
        id = num(m["id"]).toLong().toString(),
        referenceNumber = m["reference_number"] as? String,
        direction = NoticeDirection.from(m["direction"] as? String),
        status = (m["status"] as? String).orEmpty(),
        statusLabel = (m["status_label"] as? String).orEmpty(),
        isClosed = m["is_closed"] == true,
        noticeType = m["notice_type"] as? String,
        counterparty = (m["counterparty"] as? String).orEmpty(),
        claimAmount = (m["claim_amount"] as? Number)?.toDouble(),
        responseDueDate = date(m["response_due_date"] as? String),
        createdAt = dateTime(m["created_at"] as? String),
        needsApproval = m["needs_approval"] == true,
        parentNoticeId = idOf(m["parent_notice_id"]),
        isResponse = m["is_response"] == true,
        children = childRows(m["children"]),
        subject = (m["subject"] as? String)?.takeIf { it.isNotBlank() },
        isServed = m["is_served"] == true || (m["is_served"] as? Number)?.toInt() == 1,
    )

    private fun childRows(v: Any?): List<NoticeSummary> =
        (v as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().map(::parseSummary)

    private fun parseDetail(m: Map<String, Any?>) = NoticeDetail(
        summary = parseSummary(m),
        recipientName = m["recipient_name"] as? String,
        recipientAddress = m["recipient_address"] as? String,
        recipientContact = m["recipient_contact"] as? String,
        recipientEmail = m["recipient_email"] as? String,
        senderName = m["sender_name"] as? String,
        senderAddress = m["sender_address"] as? String,
        senderContact = m["sender_contact"] as? String,
        senderAdvocateName = m["sender_advocate_name"] as? String,
        signingAdvocateName = m["signing_advocate_name"] as? String,
        serviceMethod = m["service_method"] as? String,
        trackingNumber = m["tracking_number"] as? String,
        courierServiceName = m["courier_service_name"] as? String,
        dispatchDate = date(m["dispatch_date"] as? String),
        deliveryDate = date(m["delivery_date"] as? String),
        deliveryStatus = m["delivery_status"] as? String,
        waitingPeriodDays = num(m["waiting_period_days"]).toInt(),
        notes = ((m["remarks"] as? String) ?: (m["notes"] as? String))?.takeIf { it.isNotBlank() },
        consultationId = idOf(m["consultation_id"]),
        convertedToCase = m["converted_to_case"] == true,
        caseId = idOf(m["case_id"]),
        approvedAt = dateTime(m["approved_at"] as? String),
        documents = (m["documents"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().map {
            NoticeDocument(
                id = num(it["id"]).toLong().toString(),
                name = (it["display_name"] as? String)?.takeIf { s -> s.isNotBlank() }
                    ?: (it["name"] as? String).orEmpty(),
                tag = it["tag"] as? String,
                filetype = it["filetype"] as? String,
            )
        },
        parent = (m["parent"] as? Map<String, Any?>)?.let(::parseSummary),
        children = childRows(m["children"]),
        subject = (m["subject"] as? String)?.takeIf { it.isNotBlank() },
        receivedDate = date(m["received_date"] as? String),
        deemedServedDate = date(m["deemed_served_date"] as? String),
        waitingEndDate = date(m["waiting_end_date"] as? String),
        isServed = m["is_served"] == true || (m["is_served"] as? Number)?.toInt() == 1,
        returnDate = date(m["return_date"] as? String),
        returnReason = (m["return_reason"] as? String)?.takeIf { it.isNotBlank() },
        receivedByPerson = (m["received_by_person"] as? String)?.takeIf { it.isNotBlank() },
        nextStep = (m["next_step"] as? String)?.takeIf { it.isNotBlank() },
        activity = (m["activity"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().mapNotNull {
            val title = (it["title"] as? String)?.takeIf { t -> t.isNotBlank() } ?: return@mapNotNull null
            NoticeActivity(title, date(it["date"] as? String), (it["detail"] as? String)?.takeIf { d -> d.isNotBlank() })
        },
    )

    /** A positive id whether the server sends it as a number or a string; null otherwise. */
    private fun idOf(v: Any?): String? = when (v) {
        is Number -> v.toLong().takeIf { it > 0 }?.toString()
        is String -> v.trim().toLongOrNull()?.takeIf { it > 0 }?.toString()
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun rows(resp: Map<String, Any?>): List<Map<String, Any?>> {
        if (resp["status"] == false) error((resp["message"] as? String) ?: "Request failed")
        return (resp["data"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(resp: Map<String, Any?>): Map<String, Any?>? = resp["data"] as? Map<String, Any?>

    private fun num(v: Any?): Number = (v as? Number) ?: 0
    private fun date(s: String?): LocalDate? {
        val t = s?.trim().orEmpty(); if (t.isBlank()) return null
        return runCatching { LocalDate.parse(t.substring(0, 10)) }.getOrNull()
    }
    private fun dateTime(s: String?): LocalDateTime? {
        val t = s?.trim().orEmpty(); if (t.isBlank()) return null
        return runCatching { LocalDateTime.parse(t, DATE_TIME) }.getOrNull()
            ?: date(t)?.atStartOfDay()
    }

    private companion object {
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
