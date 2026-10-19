package com.legumlex.client.feature.digital_office

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.feature.payment.RazorpayOptions
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
     * Returns the new session_id and, for the paid path, the Razorpay
     * checkout config so the screen can launch the SDK directly.
     *
     * If the server gates the call with `code=advocate_busy, action=join_waitroom`
     * (cabin occupied, caller doesn't hold a prompted row) the failure carries
     * an [AdvocateBusyJoinQueue] so the caller can route into the queue flow
     * instead of surfacing a dead-end error. Other failures fall through to
     * a generic Throwable with the server's message.
     */
    suspend fun startSession(advocateStaffId: Int): Result<StartResult> = runCatching {
        val response = api.startDoSession(advocateStaffId.toString())
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
        if (data == null) {
            val code   = response["code"]?.toString()
            val action = response["action"]?.toString()
            val msg    = response["message"]?.toString() ?: "Could not start session"
            if (code == "advocate_busy" && action == "join_waitroom") {
                throw AdvocateBusyJoinQueue(advocateStaffId, msg)
            }
            error(msg)
        }
        // Moshi parses JSON numbers as Double by default, so `23` arrives as
        // 23.0; a raw .toString() yields "23.0", which then breaks the next
        // call (route /sessions/{:num}/intake won't match "23.0"). Coerce
        // through Int to guarantee a clean integer string.
        val sessionId = data["session_id"]?.asIntString() ?: error("No session_id")
        val free      = (data["free"]?.toString()?.toBooleanStrictOrNull()) == true
        val options   = if (!free) parseRazorpayOptions(data) else null
        StartResult(sessionId = sessionId, free = free, razorpay = options)
    }

    /**
     * Verify a Razorpay payment. Under the approval-gate flow (server
     * migration 106) this is the FINAL step — on success the server mints
     * the Meet link and flips the session to `in_progress`, so the response
     * carries `meet_link` + `consultation_id` directly. The screen navigates
     * straight to the meeting from here; no separate `submitIntake` follow-up.
     */
    suspend fun verifyPayment(
        sessionId: String,
        orderId: String,
        paymentId: String,
        signature: String,
    ): Result<IntakeResult> = runCatching {
        val response = api.verifyDoPayment(sessionId, orderId, paymentId, signature)
        if ((response["status"]?.toString()?.toBooleanStrictOrNull()) != true) {
            error(response["message"]?.toString() ?: "Payment verification failed")
        }
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("No data in verifyPayment response")
        IntakeResult(
            sessionId      = data["session_id"]?.asIntString() ?: sessionId,
            consultationId = data["consultation_id"]?.asIntString()?.takeIf { it.isNotBlank() && it != "0" },
            meetLink       = data["meet_link"]?.toString()?.takeIf { it.isNotBlank() },
            briefSummary   = null,
        )
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
        val ok = response["status"]?.toString()?.toBooleanStrictOrNull() == true
        if (!ok) error(response["message"]?.toString() ?: "Could not cancel session")
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
        val response = api.joinDoWaitroom(advocateStaffId.toString())
        val ok = response["status"]?.toString()?.toBooleanStrictOrNull() == true
        if (!ok) {
            val code = response["code"]?.toString() ?: "join_failed"
            error(code)
        }
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Server returned no waitroom data")
        parseWaitroomRow(data)
    }

    suspend fun leaveWaitroom(waitroomId: Int): Result<Unit> = runCatching {
        val response = api.leaveDoWaitroom(waitroomId.toString())
        val ok = response["status"]?.toString()?.toBooleanStrictOrNull() == true
        if (!ok) error(response["message"]?.toString() ?: "Could not leave queue")
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
    /** Non-null on the paid path — the screen launches Razorpay with these. */
    val razorpay: RazorpayOptions? = null,
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

@Suppress("UNCHECKED_CAST")
private fun parseRazorpayOptions(data: Map<String, Any?>): RazorpayOptions? {
    val orderId = data["order_id"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val keyId   = data["key_id"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    // JSON numbers arrive as Double via the converter (300 -> 300.0), so
    // "300.0".toIntOrNull() is null. Parse via Number/Double, not toInt.
    val amount  = (data["amount_paise"] as? Number)?.toInt()
        ?: data["amount_paise"]?.toString()?.toDoubleOrNull()?.toInt()
        ?: return null
    val prefill = data["prefill"] as? Map<String, Any?>
    return RazorpayOptions(
        keyId          = keyId,
        orderId        = orderId,
        amountPaise    = amount,
        currency       = (data["currency"] as? String) ?: "INR",
        name           = (data["office_name"] as? String)?.takeIf { it.isNotBlank() } ?: "Legum Lex",
        description    = (data["description"] as? String)?.takeIf { it.isNotBlank() }
                            ?: "Digital Office session fee",
        prefillEmail   = (prefill?.get("email")   as? String)?.takeIf { it.isNotBlank() },
        prefillContact = (prefill?.get("contact") as? String)?.takeIf { it.isNotBlank() },
    )
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
    val rp  = m["razorpay"] as? Map<String, Any?>
    return DoSession(
        id             = m["id"]?.asIntString() ?: "",
        status         = DoSessionStatus.from(m["status"] as? String),
        meetLink       = (m["meet_link"] as? String)?.takeIf { it.isNotBlank() },
        sessionFee     = m["session_fee"]?.toString()?.toDoubleOrNull() ?: 0.0,
        sessionNote    = (m["session_note"] as? String)?.takeIf { it.isNotBlank() },
        startedAt      = parseDateTime(m["started_at"] as? String),
        endedAt        = parseDateTime(m["ended_at"] as? String),
        failureReason  = (m["failure_reason"] as? String)?.takeIf { it.isNotBlank() },
        appointmentId  = m["appointment_id"]?.asIntString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" },
        consultationId = m["consultation_id"]?.asIntString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" },
        approvedAt     = (m["approved_at"] as? String)?.takeIf { it.isNotBlank() },
        declinedAt     = (m["declined_at"] as? String)?.takeIf { it.isNotBlank() },
        razorpay       = rp?.let { parseRazorpayOptions(it) },
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
