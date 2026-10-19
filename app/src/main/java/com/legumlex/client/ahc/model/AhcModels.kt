package com.legumlex.client.ahc.model

import com.legumlex.client.ahc.theme.FindingSeverity
import com.legumlex.client.ahc.theme.RiskLevel

/* ============================================================================
 *  Domain model for the Agreement Health Check (AHC) detail screen.
 *  Maps to backend `consultations/ahc/{id}` + the consultation row.
 *  ONE screen renders ONE AhcUiState; the screen `when`s over the sealed type.
 * ========================================================================== */

/** Source agreement that was submitted for review. */
data class SubmittedDoc(
    val fileName: String,          // agreement_name, e.g. "TechNova-MSA-signed.pdf"
    val sizeLabel: String,         // "640 KB"
    val pageCount: Int,            // 14
    val hasDocument: Boolean,      // has_document → gates Preview / inline viewer
)

/** The advocate who owns this review. */
data class Advocate(
    val initials: String,          // "AR"
    val name: String,              // "Adv. Anjali Rao"
    val role: String,              // "Associate · Contracts & Advisory"
    val avatarUrl: String? = null, // staff profile image; falls back to initials
)

/** One activity timeline entry (Submitted → Lex read → Released / Escalated). */
data class ActivityEntry(
    val title: String,
    val timestamp: String,         // pre-formatted IST string, e.g. "15 Jun, 9:42 AM"
    val state: DotState,
)
enum class DotState { DONE, CURRENT, TODO }

/** A single contract party for the "Parties & key terms" card. */
data class Party(val role: String, val name: String)   // role = "You" / "Other"

/** A labelled key-term row (kv). `emphasis` renders the value mono+large (₹). */
data class KeyTerm(val key: String, val value: String, val emphasis: Boolean = false)

/** A risk / missing-protection / recommendation line — skimmable, one-line-ish. */
data class Finding(
    val title: String,
    val detail: String,
    val severity: FindingSeverity,
)

/** Advocate's personal commentary (distinct, human, signed). Optional. */
data class AdvocateNote(
    val paragraphs: List<String>,  // plain-English; first/last are intro/outro
    val bullets: List<String> = emptyList(),
    val signedBy: String,          // "Adv. Anjali Rao"
    val signedAt: String,          // "10:08 AM"
)

/* --------------------------------------------------------------------------
 *  The screen state. Every branch is rendered by AhcDetailScreen.
 * ------------------------------------------------------------------------ */
sealed interface AhcUiState {

    /** Common app-bar context present in every non-loading branch. */
    val agreementName: String

    /** Skeletons while consultations/ahc/{id} loads. */
    data object Loading : AhcUiState {
        override val agreementName = "Loading…"
    }

    /**
     * State 1 — report not yet released. The 30-minute SLA is the anchor.
     * `remainingSeconds <= 0` flips the card to the calm "running late" variant.
     */
    data class Pending(
        override val agreementName: String,
        val doc: SubmittedDoc,
        val advocate: Advocate,
        val submittedAtLabel: String,   // "9:42 AM"
        val etaLabel: String,           // "10:12 AM IST"
        val slaTotalSeconds: Int = 30 * 60,
        val remainingSeconds: Int,      // counts down from slaTotalSeconds
    ) : AhcUiState {
        val isOverdue: Boolean get() = remainingSeconds <= 0
        /** 0f..1f fraction of the ring still filled. */
        val ringFraction: Float
            get() = (remainingSeconds.toFloat() / slaTotalSeconds).coerceIn(0f, 1f)
    }

    /**
     * State 2 — PRIMARY. Advocate released a written report.
     * key_risks / missing_clauses / recommendations are kept as separate
     * skimmable sections per the brief.
     */
    data class Released(
        override val agreementName: String,
        val advocate: Advocate,
        val releasedAtLabel: String,     // "10:08 AM"
        val riskScore: Int?,             // 0–100; null = released without a numeric verdict
        val riskLevel: RiskLevel?,       // authoritative for color; null when unknown
        val teaserSummary: String,       // 2–3 plain sentences
        val clausesReviewed: Int,
        val advocateNote: AdvocateNote?, // null → released as-is (no note card)
        val parties: List<Party>,
        val keyTerms: List<KeyTerm>,
        val keyRisks: List<Finding>,
        val missingProtections: List<Finding>,
        val recommendations: List<String>,
        val doc: SubmittedDoc,
        val activity: List<ActivityEntry>,
        /** Non-fatal: one section failed to load — show inline retry, not full error. */
        val partialFailureSection: String? = null,
    ) : AhcUiState

    /**
     * State 3 — advocate chose a personal consultation instead of a text report.
     * No risk score; a path to the (bookable / booked) call leads instead.
     */
    data class Escalated(
        override val agreementName: String,
        val advocate: Advocate,
        val escalatedAtLabel: String,
        val escalationReason: String,    // advocate's quoted "why"
        val booking: EscalationBooking,
        val activity: List<ActivityEntry>,
    ) : AhcUiState

    /** Friendly empty — submitted but no analysis exists yet (edge). */
    data class Empty(
        override val agreementName: String,
    ) : AhcUiState

    /** Recoverable full-screen error. */
    data class Error(
        override val agreementName: String,
        val code: String,                // "AHC-503 · timeout"
    ) : AhcUiState

    /**
     * State 5 — terminal. The check has been closed (completed / resolved via
     * consultation / converted to case / cancelled). The body collapses to a
     * subdued recap: a close hero, a "Closed on" stamp, an optional recap /
     * link / report row, an ending timeline, and a read-only advocate card.
     * No reopen — a new need is a new request.
     */
    data class Closed(
        override val agreementName: String,
        val advocate: Advocate,
        val kind: ClosureKind,
        val title: String,               // "Closed · Completed"
        val subtitle: String,
        val closedOnLabel: String,       // "15 Jun 2026"
        val recap: List<RecapRow>,
        val link: ClosureLink?,          // resolved → consultation, converted → case
        val reportDoc: SubmittedDoc?,    // completed → still-downloadable report
        val stripText: String?,          // "No follow-up was needed."
        val activity: List<ActivityEntry>,
    ) : AhcUiState
}

/** Terminal closure category — drives the close hero's icon, palette and copy. */
enum class ClosureKind { COMPLETED, RESOLVED_VIA_CONSULTATION, CONVERTED_TO_CASE, CANCELLED, OTHER }

/** A labelled recap row in the closed-state summary card. */
data class RecapRow(val key: String, val value: String, val mono: Boolean = false)

/** A tappable link from a closed check to the consultation/case that resolved it. */
data class ClosureLink(
    val target: ClosureLinkTarget,
    val title: String,
    val reference: String,           // "#C-218" / "C-204"
    val meta: String,                // "Completed" / "Open the consultation"
)
enum class ClosureLinkTarget { CONSULTATION, CASE }

/** Escalation booking sub-state — drives which CTA the Escalated screen leads with. */
sealed interface EscalationBooking {
    /** No consultation booked yet — booking CTA leads. */
    data object AwaitingBooking : EscalationBooking
    /** A consultation is booked / in progress — show its reference & "Go to it". */
    data class Booked(val reference: String, val whenLabel: String) : EscalationBooking
}

/* --------------------------------------------------------------------------
 *  Screen-level callbacks. Pure lambdas → testable, preview-friendly.
 * ------------------------------------------------------------------------ */
data class AhcActions(
    val onBack: () -> Unit = {},
    val onOverflow: () -> Unit = {},
    val onPreviewAgreement: () -> Unit = {},   // only wired when has_document
    val onDownloadReport: () -> Unit = {},
    val onMessageAdvocate: () -> Unit = {},
    val onTalkToAdvocate: () -> Unit = {},      // primary CTA on released
    val onBookConsultation: () -> Unit = {},    // escalated · awaiting
    val onGoToConsultation: () -> Unit = {},    // escalated · booked
    val onFindingClick: (Finding) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySection: (String) -> Unit = {},
    val onClose: () -> Unit = {},
)
