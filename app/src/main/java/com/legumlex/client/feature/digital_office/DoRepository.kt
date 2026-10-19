package com.legumlex.client.feature.digital_office

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.feature.consultations.apiError
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Booking-flow wrapper around the customers_api DO endpoints. The flow is
 * always start → intake — the screen never has to think about state
 * machines; success of the intake call is what triggers navigation away.
 */
class DoRepository(private val api: ApiService) {

    /**
     * Returns the new session_id; the fee, if any, is paid after the advocate accepts (feeInvoice).
     *
     * If the server gates the call with `code=advocate_busy, action=join_waitroom`
     * (cabin occupied, caller doesn't hold a prompted row) the failure carries
     * an [AdvocateBusyJoinQueue] so the caller can route into the queue flow
     * instead of surfacing a dead-end error. Other failures fall through to
     * a generic Throwable with the server's message.
     */
    suspend fun startSession(advocateStaffId: Int): Result<StartResult> = runCatching {
        // A refusal arrives as a 4xx, so Retrofit throws before the body is
        // read; the server's code/action live in the error body.
        val response = try {
            api.startDoSession(advocateStaffId.toString())
        } catch (e: retrofit2.HttpException) {
            val err = e.apiError()
            when {
                err.code == "advocate_busy" -> throw AdvocateBusyJoinQueue(advocateStaffId, err.message ?: "This advocate is in another session.")
                err.code == "needs_registration" -> throw NeedsRegistration(err.message, err.action)
                else -> throw e
            }
        }
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
        if (data == null) {
            val code   = response["code"]?.toString()
            val msg    = response["message"]?.toString() ?: "The session could not be started."
            if (code == "advocate_busy") throw AdvocateBusyJoinQueue(advocateStaffId, msg)
            if (code == "needs_registration") throw NeedsRegistration(msg, response["action"]?.toString())
            error(msg)
        }
        // Moshi parses JSON numbers as Double by default, so `23` arrives as
        // 23.0; a raw .toString() yields "23.0", which then breaks the next
        // call (route /sessions/{:num}/intake won't match "23.0"). Coerce
        // through Int to guarantee a clean integer string.
        val sessionId = data["session_id"]?.asIntString() ?: error("No session_id")
        val free      = data["free"] == true || data["free"].asIntOrNull() == 1
        StartResult(sessionId = sessionId, free = free)
    }

    /**
     * The accepted session's fee as an invoice, paid on its public page on
     * the website (the app takes no payments). Paying it starts the session;
     * the Waiting poll sees in_progress. Returns the page's address.
     */
    suspend fun feeInvoice(sessionId: String): Result<String> = runCatching {
        val response = api.doFeeInvoice(sessionId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error(response["message"]?.toString() ?: "The invoice could not be opened.")
        (data["pay_url"] as? String)?.takeIf { it.isNotBlank() } ?: error("The invoice could not be opened.")
    }

    /**
     * Submit intake. Under the approval-gate flow (server migration 106)
     * this NO LONGER returns a Meet link — the server flips status to
     * `awaiting_approval` and the screen must poll [getSession] until the
     * advocate accepts (→ approved_awaiting_payment or in_progress) or
     * declines (→ declined_by_advocate / expired).
     */
    suspend fun submitIntake(sessionId: String, intakeText: String): Result<IntakeResult> = runCatching {
        val response = api.submitDoIntake(sessionId, intakeText)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error(response["message"]?.toString() ?: "Intake failed")
        IntakeResult(
            sessionId      = data["session_id"]?.asIntString() ?: sessionId,
            consultationId = null, // not created until advocate accepts (+ payment if paid)
            meetLink       = null, // minted by /pay on success OR by advocate accept (free)
            briefSummary   = data["brief_summary"]?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    /** Client-driven cancel — see [ApiService.cancelDoSession]. */
    suspend fun cancelSession(sessionId: String): Result<Unit> = runCatching {
        val response = api.cancelDoSession(sessionId)
        val ok = response["status"] == true || response["status"]?.toString() == "true"
        if (!ok) error(response["message"]?.toString() ?: "The request could not be cancelled.")
        Unit
    }

    suspend fun getSession(sessionId: String): Result<DoSession> = runCatching {
        val response = api.getDoSession(sessionId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Session not found")
        parseSession(data)
    }

    /** Per-consultation lookup — used by DetailDigital to render Meet link + status. */
    suspend fun sessionForConsultation(consultationId: String): Result<DoSession?> = runCatching {
        val response = api.consultationDoSession(consultationId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: return@runCatching null
        parseSession(data)
    }

    // ── Waitroom ────────────────────────────────────────────────────────────

    /**
     * Caller's active waitroom rows. The home banner reads the first row;
     * a future "All my queues" sheet can show the rest if a client is queued
     * for more than one advocate at a time.
     */
    suspend fun listWaitroom(): Result<List<DoWaitroomRow>> = runCatching {
        val response = api.getDoWaitroom()
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: return@runCatching emptyList()
        @Suppress("UNCHECKED_CAST")
        val rows = data["rows"] as? List<Map<String, Any?>> ?: emptyList()
        rows.map(::parseWaitroomRow)
    }

    /**
     * Join an advocate's queue. The server enforces preconditions; this
     * surface returns a typed result so the UI can route on cabin_free
     * (book directly) vs advocate_offline.
     */
    suspend fun joinWaitroom(advocateStaffId: Int): Result<DoWaitroomRow> = runCatching {
        // The server refuses with a 409 whose body carries the code
        // (cabin_free / advocate_offline / …); surface it as [WaitroomRefused].
        val response = try {
            api.joinDoWaitroom(advocateStaffId.toString())
        } catch (e: retrofit2.HttpException) {
            val err = e.apiError()
            if (err.code != null) throw WaitroomRefused(err.code, err.message)
            throw e
        }
        val ok = response["status"] == true || response["status"]?.toString() == "true"
        if (!ok) {
            throw WaitroomRefused(response["code"]?.toString() ?: "join_failed", response["message"]?.toString())
        }
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("The queue could not be joined.")
        parseWaitroomRow(data)
    }

    suspend fun leaveWaitroom(waitroomId: Int): Result<Unit> = runCatching {
        val response = api.leaveDoWaitroom(waitroomId.toString())
        val ok = response["status"] == true || response["status"]?.toString() == "true"
        if (!ok) error(response["message"]?.toString() ?: "The queue could not be left.")
        Unit
    }
}

private fun parseWaitroomRow(m: Map<String, Any?>): DoWaitroomRow = DoWaitroomRow(
    waitroomId          = m["waitroom_id"].asIntOrZero(),
    advocateStaffId     = m["advocate_staff_id"].asIntOrZero(),
    advocateName        = (m["advocate_name"] as? String).orEmpty().ifBlank { "Advocate" },
    advocateDesignation = (m["advocate_designation"] as? String).orEmpty(),
    advocateAvatarUrl   = (m["advocate_avatar_url"] as? String)?.takeIf { it.isNotBlank() },
    position            = m["position"].asIntOrZero(),
    status              = DoWaitroomStatus.from(m["status"] as? String),
    joinedAt            = (m["joined_at"] as? String).orEmpty(),
    joinedSecondsAgo    = m["joined_seconds_ago"].asIntOrZero(),
    etaMinutes          = m["eta_minutes"].asIntOrZero(),
    promptExpiresAt     = (m["prompt_expires_at"] as? String)?.takeIf { it.isNotBlank() && it != "null" },
    promptSecondsLeft   = m["prompt_seconds_left"].asIntOrNull(),
)

/** Same Double-coercion problem as [asIntString] but returning Int / 0. */
private fun Any?.asIntOrZero(): Int = asIntOrNull() ?: 0
private fun Any?.asIntOrNull(): Int? {
    if (this == null) return null
    if (this is Number) return this.toInt()
    return this.toString().toDoubleOrNull()?.toInt()
}

data class StartResult(
    val sessionId: String,
    val free: Boolean,
)

/**
 * Thrown by [DoRepository.startSession] when the server tells us the cabin is
 * occupied (and the caller isn't the prompted head). The caller should offer
 * the user a "Join the queue" action instead of surfacing a generic error.
 */
class AdvocateBusyJoinQueue(
    val advocateStaffId: Int,
    message: String,
) : RuntimeException(message)

/** sessions POST 403 code=needs_registration: the client has not registered for the office. */
class NeedsRegistration(message: String?, val registerUrl: String? = null) : RuntimeException(message ?: "Register for the Digital Office on the website first.")

/** waitroom/join refused: [code] is the server's (cabin_free, advocate_offline, …). */
class WaitroomRefused(val code: String, val serverMessage: String?) : RuntimeException(serverMessage ?: code)

/**
 * Coerce a JSON-decoded id value to a clean integer string. Moshi decodes
 * naked JSON numbers as Double — naively calling .toString() on those yields
 * "23.0", which breaks server routes that match {:num}. This forces the
 * "23" / "0" / "" shape the server expects.
 *
 *   Double(23.0)        → "23"
 *   Int(23)             → "23"
 *   Long(23)            → "23"
 *   String("23")        → "23"
 *   String("abc")       → "abc"   (passthrough, never an id)
 *   null                → null
 */
private fun Any?.asIntString(): String? {
    if (this == null) return null
    if (this is Number) return this.toLong().toString()
    val s = this.toString()
    // Last-chance: server sometimes returns numerics as strings ("23.0")
    s.toDoubleOrNull()?.toLong()?.toString()?.let { return it }
    return s
}

data class IntakeResult(
    val sessionId: String,
    val consultationId: String?,
    val meetLink: String?,
    val briefSummary: String?,
)

private fun parseSession(m: Map<String, Any?>): DoSession {
    @Suppress("UNCHECKED_CAST")
    val adv = m["advocate"] as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    return DoSession(
        id             = m["id"]?.asIntString() ?: "",
        status         = DoSessionStatus.from(m["status"] as? String),
        meetLink       = (m["meet_link"] as? String)?.takeIf { it.isNotBlank() },
        sessionFee     = (m["session_fee"] as? Number)?.toDouble() ?: m["session_fee"]?.toString()?.toDoubleOrNull() ?: 0.0,
        sessionNote    = (m["session_note"] as? String)?.takeIf { it.isNotBlank() },
        startedAt      = parseDateTime(m["started_at"] as? String),
        endedAt        = parseDateTime(m["ended_at"] as? String),
        // The client-safe sentence only; failure_reason is the staff wording.
        failureReason  = (m["failure_reason_client"] as? String)?.takeIf { it.isNotBlank() },
        approvalWindowMinutes = m["approval_window_minutes"].asIntOrNull()?.takeIf { it > 0 },
        invoicePayUrl  = ((m["invoice"] as? Map<*, *>)?.get("pay_url") as? String)?.takeIf { it.isNotBlank() },
        appointmentId  = m["appointment_id"]?.asIntString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" },
        consultationId = m["consultation_id"]?.asIntString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" },
        approvedAt     = (m["approved_at"] as? String)?.takeIf { it.isNotBlank() },
        declinedAt     = (m["declined_at"] as? String)?.takeIf { it.isNotBlank() },
        advocate       = adv?.let {
            DoAdvocate(
                staffId   = it["staff_id"].asIntOrZero(),
                name      = (it["name"] as? String).orEmpty(),
                firstName = (it["firstname"] as? String)?.takeIf { s -> s.isNotBlank() },
                lastName  = (it["lastname"]  as? String)?.takeIf { s -> s.isNotBlank() },
                avatarUrl = (it["avatar_url"] as? String)?.takeIf { s -> s.isNotBlank() },
                designation = (it["designation"] as? String)?.takeIf { s -> s.isNotBlank() },
            )
        },
    )
}

private val datetimePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

private fun parseDateTime(s: String?): LocalDateTime? {
    if (s.isNullOrBlank()) return null
    for (p in datetimePatterns) {
        try {
            return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p))
        } catch (_: DateTimeParseException) {
        }
    }
    return null
}
