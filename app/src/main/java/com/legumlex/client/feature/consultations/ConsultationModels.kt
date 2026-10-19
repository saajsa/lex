package com.legumlex.client.feature.consultations

import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.Lex
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// Type discrimination — drives detail-screen layout selection. Per handoff:
// "Don't try to abstract a 'consultation detail template' — that's a code
// mistake, not a design mistake."
// -----------------------------------------------------------------------------
enum class ConsultationKind(val apiValue: String, val label: String) {
    GENERAL        ("general",          "General Enquiry"),
    // From the Legum Lex desk (Consult); the portal's pill says "Legal Enquiry".
    LEGAL_ENQUIRY  ("legal_enquiry",    "Legal Enquiry"),
    AHC            ("agreement_review", "Agreement Check"),
    LEGAL_NOTICE   ("legal_notice",     "Legal Notice"),
    DOC_REQUEST    ("document_request", "Documents Requested"),
    DIGITAL_OFFICE ("digital_office",   "Video Session");

    companion object {
        fun from(raw: String?): ConsultationKind = when (raw?.lowercase()?.trim()) {
            "agreement_review" -> AHC
            "legal_enquiry"    -> LEGAL_ENQUIRY
            "legal_notice"     -> LEGAL_NOTICE
            "document_request" -> DOC_REQUEST
            "digital_office"   -> DIGITAL_OFFICE
            else               -> GENERAL
        }
    }
}

// -----------------------------------------------------------------------------
// Full 10-state status palette per the consultations handoff. We compose it
// from a server-derived status code + a few computed booleans the repo wires
// up from related entities (pending docs, unread replies, etc.).
// -----------------------------------------------------------------------------
enum class ConsultationStatus(val label: String, private val tone: (LexColors) -> StatusPalette) {
    PENDING_REVIEW("Pending Review",       { it.warning }),
    ACKNOWLEDGED  ("Acknowledged",         { it.blue }),
    IN_PROGRESS   ("In Progress",          { it.blue }),
    SCHEDULED     ("Scheduled",            { it.info }),
    AWAITING_DOCS ("Awaiting Documents",   { it.warning }),
    LIVE_NOW      ("Live Now",             { it.success }),
    COMPLETED     ("Completed",            { it.success }),
    CONVERTED     ("Litigation",            { it.success }),
    CANCELLED     ("Cancelled",            { it.neutral }),
    CLOSED        ("Closed",               { it.neutral }),
    DECLINED      ("Declined",             { it.danger });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)
}

// Filter chips on the Consultations list. The list flattens the
// SmartSections buckets and applies one of these scopes — see
// `applyFilter` in ConsultationsScreen.kt for the mapping.
enum class ConsultationFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    AWAITING_YOU("Awaiting You"),
    DONE("Done"),
    CONVERTED("Converted"),   // legacy entry — retained for any older callers
}

data class Consultation(
    val id: String,
    val tag: String?,
    val note: String?,
    val phase: String,                   // consultation | litigation
    val kind: ConsultationKind,
    val status: ConsultationStatus,
    val priority: String?,
    val type: String?,                   // raw consultation_type
    val isUrgent: Boolean,
    val createdAt: LocalDateTime?,
    val lastUpdatedAt: LocalDateTime?,
    val scheduledDate: LocalDate?,
    val scheduledTime: LocalTime?,
    val preferredDate: LocalDate?,
    val preferredTime: LocalTime?,
    val acknowledgedAt: LocalDateTime?,
    val assignedTo: String?,
    val assignedToDesignation: String? = null,
    val assignedToAvatarUrl: String? = null,
    val assignedStaffId: Int? = null,
    val linkedCaseId: String?,
    val linkedCaseTitle: String?,
    val linkedCaseNumber: String?,
    val replyCount: Int = 0,
    val hasUnreadReplies: Boolean = false,
    val pendingDocCount: Int = 0,
    val totalDocCount: Int = 0,
    val riskLevel: RiskLevel? = null,        // populated for AHC
    val riskScore: Int? = null,              // 0-100, AHC only
    val flaggedClauseCount: Int? = null,     // AHC only
    val replies: List<Reply> = emptyList(),
    val documents: List<ConsultationDoc> = emptyList(),
    val clientInitiated: Boolean = false,
    val requestStatus: String? = null,        // pending | approved | scheduled | rejected
    val invoiceId: String? = null,
    val invoiceNumber: String? = null,
    val invoiceStatus: String? = null,
    val invoiceTotal: Double? = null,
    // Released / Escalated / Closure surfaces (Agreement Check Workflow design).
    // All null while the consultation is in-progress; populated as the staff
    // app advances the lifecycle. Detail screens key on these to swap state.
    val releasedAt: LocalDateTime? = null,
    val closedAt: LocalDateTime? = null,
    val closureReason: String? = null,        // completed | resolved_via_consultation | converted_to_case | cancelled_by_client
    val closureNote: String? = null,
    val isEscalated: Boolean = false,
    val escalationReason: String? = null,
    val escalatedAt: LocalDateTime? = null,
    val linkedConsultationId: String? = null, // child created on escalation
    val parentConsultationId: String? = null, // parent that escalated to this one
    val advocateNote: String? = null,
    /** Agreement Check funnel position (intake/payment/pending_review/result). */
    val funnelStep: String? = null,
    /** Fee invoice state: paid / unpaid / cancelled, or null with no fee invoice. */
    val paymentStatus: String? = null,
    val feeInvoiceId: String? = null,
    /** Shared advocate messages not yet read. */
    val unreadCount: Int = 0,
    /** The portal board's reading of a funnel (Funnel_client_view): label + action. */
    val funnelLabel: String? = null,
    val funnelAction: String? = null,
    val funnelDetail: String? = null,
)

/** Funnel actions that wait on the client, as the portal's board counts them. */
fun Consultation.funnelNeedsYou(): Boolean = funnelAction in setOf("reupload", "answer", "pay", "check_email")

/** True iff the client is allowed to withdraw this consultation themselves. */
fun Consultation.canWithdraw(): Boolean =
    clientInitiated &&
    requestStatus == "pending" &&
    phase == "consultation"

/** True iff the client is allowed to update the preferred slot. */
fun Consultation.canRescheduleSlot(): Boolean =
    requestStatus != "scheduled" && !isClosed() && !isConverted()

/**
 * True iff the client can terminally close this consultation via the cancel
 * endpoint. Broader than canWithdraw() — works at any non-terminal stage,
 * not just while pending. The server still enforces the linked-child-open
 * invariant and litigation phase block; this only filters out the always-no
 * cases so we don't show a menu item that always 409s.
 */
fun Consultation.canClose(): Boolean =
    phase == "consultation" && !isClosed() && !isConverted()

// Quick computed flags for smart-section bucketing.
fun Consultation.needsAction(): Boolean {
    if (status in setOf(
            ConsultationStatus.CONVERTED,
            ConsultationStatus.CLOSED,
            ConsultationStatus.CANCELLED,
            ConsultationStatus.DECLINED,
            ConsultationStatus.COMPLETED,
        )) return false
    return when {
        funnelNeedsYou() -> true
        hasUnreadReplies -> true
        kind == ConsultationKind.DOC_REQUEST && pendingDocCount > 0 -> true
        kind == ConsultationKind.DIGITAL_OFFICE && scheduledDate != null -> {
            val now = LocalDateTime.now()
            val sched = LocalDateTime.of(scheduledDate, scheduledTime ?: LocalTime.NOON)
            val hours = ChronoUnit.HOURS.between(now, sched)
            hours in -1..24 // 24h before to during
        }
        status == ConsultationStatus.AWAITING_DOCS -> true
        else -> false
    }
}

fun Consultation.scheduledAt(): LocalDateTime? {
    val d = scheduledDate ?: return null
    return LocalDateTime.of(d, scheduledTime ?: LocalTime.NOON)
}

// "Active" bucket on the hub.
fun Consultation.isActive(): Boolean =
    !needsAction() && status in setOf(
        ConsultationStatus.PENDING_REVIEW,
        ConsultationStatus.ACKNOWLEDGED,
        ConsultationStatus.IN_PROGRESS,
    )

// "Scheduled & upcoming" bucket.
fun Consultation.isScheduledUpcoming(): Boolean {
    if (status == ConsultationStatus.SCHEDULED) return true
    if (kind == ConsultationKind.DIGITAL_OFFICE && scheduledDate != null) {
        val d = scheduledAt() ?: return false
        return d.isAfter(LocalDateTime.now())
    }
    return false
}

fun Consultation.isConverted(): Boolean = status == ConsultationStatus.CONVERTED

fun Consultation.isClosed(): Boolean =
    status in setOf(
        ConsultationStatus.CLOSED,
        ConsultationStatus.CANCELLED,
        ConsultationStatus.DECLINED,
        ConsultationStatus.COMPLETED,
    )

// One-line user-facing title shown on cards & detail headers.
fun Consultation.displayTitle(): String =
    tag?.takeIf { it.isNotBlank() }
        ?: note?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()?.take(80)
        ?: when (kind) {
            ConsultationKind.AHC            -> "Agreement Check"
            ConsultationKind.LEGAL_NOTICE   -> "Legal Notice"
            ConsultationKind.DOC_REQUEST    -> "Documents requested"
            ConsultationKind.DIGITAL_OFFICE -> "Video session"
            else                            -> "Untitled consultation"
        }

// Short "what to do next" hint for the Needs-action card subline.
fun Consultation.nextActionLabel(): String? = when {
    funnelNeedsYou() && funnelDetail != null           -> funnelDetail
    hasUnreadReplies                                   -> "New message from your advocate"
    kind == ConsultationKind.DOC_REQUEST && pendingDocCount > 0 ->
        "$pendingDocCount document${if (pendingDocCount == 1) "" else "s"} left to upload"
    kind == ConsultationKind.DIGITAL_OFFICE && scheduledDate != null -> {
        val d = scheduledAt() ?: return@nextActionLabel null
        val hours = ChronoUnit.HOURS.between(LocalDateTime.now(), d)
        when {
            hours < 0 -> "Session is live now"
            hours < 1 -> "Session starts in under an hour"
            hours < 24 -> "Session today"
            else -> null
        }
    }
    else -> null
}

// -----------------------------------------------------------------------------
// Conversation thread — replies between client and advocate.
// -----------------------------------------------------------------------------
data class Reply(
    val id: String,
    val message: String,
    val fromClient: Boolean,
    val authorName: String,
    val authorImage: String?,
    val createdAt: LocalDateTime?,
    /** When the advocate read it (client messages only), as the portal records it. */
    val readAt: LocalDateTime? = null,
)

// Lightweight document reference attached to a consultation. Doesn't carry the
// full DocCategory machinery from feature/documents — that's a different surface.
data class ConsultationDoc(
    val id: String,
    val fileName: String,
    val displayName: String,
    val fileExtension: String?,
    val mimeType: String?,
    val tag: String?,
    val uploadedAt: LocalDateTime?,
)

// -----------------------------------------------------------------------------
// AHC-specific data
// -----------------------------------------------------------------------------
enum class RiskLevel(val label: String) {
    LOW("Low risk"),
    MEDIUM("Medium risk"),
    HIGH("High risk"),
    CRITICAL("Critical risk");

    companion object {
        fun from(raw: String?): RiskLevel? = when (raw?.uppercase()?.trim()) {
            "LOW"      -> LOW
            "MEDIUM"   -> MEDIUM
            "HIGH"     -> HIGH
            "CRITICAL" -> CRITICAL
            else -> null
        }
    }
}

// -----------------------------------------------------------------------------
// Doc-Request specific data
// -----------------------------------------------------------------------------
enum class DocRequestStatus(val label: String) {
    PENDING ("Pending upload"),
    UPLOADED("Uploaded"),
    WAIVED  ("Waived");

    companion object {
        fun from(raw: String?) = when (raw?.lowercase()) {
            "uploaded" -> UPLOADED
            "waived"   -> WAIVED
            else       -> PENDING
        }
    }
}

data class DocRequestItem(
    val id: String,
    val documentName: String,
    val description: String?,
    val isRequired: Boolean,
    val status: DocRequestStatus,
    val fulfilledFileName: String?,
    val fulfilledDisplayName: String?,
    val fulfilledAt: java.time.LocalDateTime?,
    val waivedReason: String?,
    val createdAt: java.time.LocalDateTime? = null,
    // Context for the uploaded file (Plot Design 08): size in bytes, a coarse
    // kind ("pdf" | "image" | "file"), and who uploaded it ("you" | "advocate").
    val fileSizeBytes: Long? = null,
    val fileKind: String? = null,
    val uploadedBy: String? = null,
    val requestedAt: java.time.LocalDateTime? = null,
    val waivedBy: Int? = null,
)

data class DocRequestBundle(
    val items: List<DocRequestItem>,
    val total: Int,
    val uploaded: Int,
    val waived: Int,
    val pending: Int,
)

// -----------------------------------------------------------------------------
// Attachments — files exchanged on a consultation, in either direction.
// -----------------------------------------------------------------------------
data class ConsultationAttachment(
    val id: String,
    /** Original / friendly name shown to the user. */
    val displayName: String,
    /** Internal storage name (used by download endpoint, occasionally same as displayName). */
    val fileName: String,
    /** MIME type from the upload; may be blank for older uploads. */
    val fileType: String,
    /** Optional categorisation tag the firm uses (e.g. "Order", "Aadhaar"). */
    val tag: String?,
    /** True if the row originated from the client side. */
    val fromClient: Boolean,
    /** Resolved author label — "You" for the client's own uploads, staff name otherwise. */
    val uploadedBy: String,
    val uploadedAt: java.time.LocalDateTime?,
)

// -----------------------------------------------------------------------------
// Digital-Office (appointment) specific data
// -----------------------------------------------------------------------------
enum class AppointmentStatus(val label: String) {
    PENDING    ("Pending"),
    CONFIRMED  ("Confirmed"),
    COMPLETED  ("Completed"),
    CANCELLED  ("Cancelled"),
    NO_SHOW    ("No show"),
    RESCHEDULED("Rescheduled");

    companion object {
        fun from(raw: String?) = when (raw?.lowercase()) {
            "confirmed"   -> CONFIRMED
            "completed"   -> COMPLETED
            "cancelled"   -> CANCELLED
            "no_show"     -> NO_SHOW
            "rescheduled" -> RESCHEDULED
            else          -> PENDING
        }
    }
}

data class AppointmentData(
    val id: String,
    val date: java.time.LocalDate?,
    val startTime: java.time.LocalTime?,
    val endTime: java.time.LocalTime?,
    val durationMinutes: Int?,
    val status: AppointmentStatus,
    val notes: String?,
    val meetLink: String?,
    val staffName: String?,
    val serviceName: String?,
    val sessionStartedAt: java.time.LocalDateTime?,
    val sessionEndedAt: java.time.LocalDateTime?,
)

/** One report finding: a short title + a one-line plain-English detail. */
data class AhcFindingItem(
    val title: String,
    val detail: String = "",
)

data class AhcAnalysis(
    val riskScore: Int?,
    val riskLevel: RiskLevel?,
    val agreementName: String?,
    val teaserSummary: String?,
    val keyRisks: List<AhcFindingItem>,
    val missingClauses: List<AhcFindingItem>,
    val recommendations: List<String>,
    val processedAt: java.time.LocalDateTime?,
    val sessionStatus: String? = null,
    val completedAt: java.time.LocalDateTime? = null,
    // Source-agreement metadata for the "What you submitted" card.
    val agreementSizeBytes: Long? = null,
    val agreementPageCount: Int? = null,
    // Neutral pre-release context (from funnel extraction) for the header card:
    // what was submitted, not the verdict.
    val agreementType: String? = null,     // e.g. "Lease", "Vendor MSA"
    val parties: String? = null,           // e.g. "Davinder Singh ↔ Lakhwinder Singh"
    val agreementSummary: String? = null,  // factual one-liner
    val released: Boolean = false,         // server: report released to the client
)

/**
 * Shape returned from `POST /v1/consultations/{id}/cancel` and the only
 * lifecycle-write response the client app handles directly. Same fields
 * surface on the first call and on idempotent re-fires, so callers can flip
 * UI state unconditionally on a successful Result.
 */
data class ClosureSummary(
    val id: String,
    val closedAt: String,            // server-formatted "yyyy-MM-dd HH:mm:ss" IST
    val closureReason: String,       // matches the server enum
    val status: String,              // "Closed"
)

/**
 * Parent / linked child consultation pair. Either side is null when there's
 * no linkage in that direction OR when the linked row isn't client_visible
 * yet — `parseLinkageSide` in the repository collapses both cases to null
 * so the UI doesn't render a bare placeholder card.
 */
data class ConsultationLinkage(
    val id: String,
    val parent: LinkageSide?,
    val linked: LinkageSide?,
)

data class LinkageSide(
    val id: String,
    val kind: ConsultationKind,
    val title: String?,
    val status: String?,
    val closedAt: String?,
    val closureReason: String?,
) {
    val isClosed: Boolean get() = !closedAt.isNullOrBlank()
}

/**
 * An Agreement Check whose fee invoice is still unpaid sits at the funnel's
 * payment step: the advocate has not started, so it reads "Awaiting Payment"
 * rather than a review in progress (the invoice is paid on the website).
 */
fun Consultation.awaitingPayment(): Boolean =
    kind == ConsultationKind.AHC && closedAt == null &&
        (funnelStep == "payment" || (paymentStatus == "unpaid" && funnelStep !in setOf("pending_review", "result")))

/** Whether the advocate's review clock is running (the funnel reached review). */
fun Consultation.inAdvocateReview(): Boolean =
    kind != ConsultationKind.AHC || funnelStep == null || funnelStep in setOf("pending_review", "result")

/** The status as the client should read it. */
val Consultation.statusLabel: String
    get() = if (awaitingPayment()) "Awaiting Payment" else status.label
