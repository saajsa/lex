package com.legumlex.client.feature.digital_office

import java.time.LocalDateTime

/**
 * Mirror of the server's digital_office_sessions.status enum. Used to drive
 * which UI state the DO screens render in (booking, intake, waiting room, live).
 */
enum class DoSessionStatus(val raw: String) {
    PENDING_PAYMENT          ("pending_payment"),
    PAYMENT_FAILED           ("payment_failed"),
    AWAITING_INTAKE          ("awaiting_intake"),
    // Approval-gate states (server migration 106):
    AWAITING_APPROVAL        ("awaiting_approval"),
    APPROVED_AWAITING_PAYMENT("approved_awaiting_payment"),
    DECLINED_BY_ADVOCATE     ("declined_by_advocate"),
    CONFIRMED                ("confirmed"),
    IN_PROGRESS              ("in_progress"),
    COMPLETED                ("completed"),
    CANCELLED                ("cancelled"),
    ADVOCATE_OFFLINE         ("advocate_offline"),
    ADVOCATE_BUSY            ("advocate_busy"),
    EXPIRED                  ("expired"),
    MEET_FAILED              ("meet_failed"),
    UNKNOWN                  ("");

    companion object {
        fun from(raw: String?): DoSessionStatus =
            values().firstOrNull { it.raw == raw?.lowercase()?.trim() } ?: UNKNOWN
    }
}

/** Session row shape returned by the DO booking endpoints + the consultation do_session lookup. */
data class DoSession(
    val id: String,
    val status: DoSessionStatus,
    val meetLink: String?,
    val sessionFee: Double,
    val sessionNote: String?,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    /** failure_reason_client: a sentence written for the client, never the staff reason. */
    val failureReason: String?,
    val appointmentId: String?,
    val consultationId: String?,
    val advocate: DoAdvocate?,
    /** When advocate has accepted (free or paid). Null otherwise. */
    val approvedAt: String? = null,
    val declinedAt: String? = null,
    /** Minutes the advocate has to accept, as the server sets it. */
    val approvalWindowMinutes: Int? = null,
    /** The fee's invoice page, once raised (sessions/{id}.invoice.pay_url). */
    val invoicePayUrl: String? = null,
)

data class DoAdvocate(
    val staffId: Int,
    val name: String,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val designation: String? = null,
)

// ── Waitroom (cabin queue) — client side ────────────────────────────────────

enum class DoWaitroomStatus(val raw: String) {
    WAITING ("waiting"),
    PROMPTED("prompted"),
    EXPIRED ("expired"),
    LEFT    ("left"),
    PROMOTED("promoted"),
    UNKNOWN ("");

    companion object {
        fun from(raw: String?): DoWaitroomStatus =
            values().firstOrNull { it.raw == raw?.lowercase()?.trim() } ?: UNKNOWN
    }
}

/**
 * Client-side waitroom row. Position is recomputed server-side so the head
 * is always 1; the home banner reads position + status to decide what to show.
 *
 * Server-supplied seconds-since-joined and seconds-left fields are snapshots
 * at fetch time — the UI ticks them locally between refreshes so the user
 * sees a live countdown without polling every second.
 */
data class DoWaitroomRow(
    val waitroomId: Int,
    val advocateStaffId: Int,
    val advocateName: String,
    val advocateDesignation: String,
    val advocateAvatarUrl: String?,
    val position: Int,
    val status: DoWaitroomStatus,
    val joinedAt: String,
    val joinedSecondsAgo: Int,
    val etaMinutes: Int,
    val promptExpiresAt: String?,
    val promptSecondsLeft: Int?,
)

/** The portal's Digital Office page, where a client registers for the office. */
fun portalOfficeUrl(): String =
    com.legumlex.client.BuildConfig.API_BASE_URL.substringBefore("customers_api/").trimEnd('/') + "/mylex/office"
