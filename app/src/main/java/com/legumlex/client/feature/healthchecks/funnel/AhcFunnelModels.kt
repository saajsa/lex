package com.legumlex.client.feature.healthchecks.funnel

import com.legumlex.client.feature.consultations.RiskLevel

/**
 * Document-first state machine. The client uploads first; the AI extracts
 * type/parties/summary; the client confirms (or corrects) before paying.
 */
enum class AhcStep(val ordinal1based: Int, val title: String, val slug: String) {
    UPLOAD        (1, "Upload",     "upload"),
    EXTRACTING    (2, "Reading",    "extracting"),
    CONFIRM       (3, "Confirm",    "confirm"),
    PAYMENT       (4, "Payment",    "payment"),
    PENDING_REVIEW(5, "Review",     "pending_review"),
    RESULT        (6, "Result",     "result");

    companion object {
        fun from(raw: String?): AhcStep = when (raw?.lowercase()) {
            "upload"         -> UPLOAD
            "extracting"     -> EXTRACTING
            "confirm"        -> CONFIRM
            "payment"        -> PAYMENT
            "pending_review" -> PENDING_REVIEW
            "result", "completed" -> RESULT
            else             -> UPLOAD
        }
    }
}

enum class AhcContractType(val key: String, val label: String) {
    SAAS      ("saas",       "SaaS"),
    LEASE     ("lease",      "Lease"),
    EMPLOYMENT("employment", "Employment"),
    NDA       ("nda",        "NDA"),
    CONSULTING("consulting", "Consulting"),
    OTHER     ("other",      "Other");

    companion object {
        fun from(raw: String?): AhcContractType? =
            values().firstOrNull { it.key == raw?.lowercase() }
    }
}

data class AhcDocument(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mime: String,
)

/**
 * Pre-filled fields derived from an initial reading of the uploaded document,
 * plus a confidence score so the Confirm screen can surface a "we're unsure"
 * cue when low. Source of truth remains the user's confirmed values.
 */
data class AhcExtraction(
    val status: String,                       // "ok" | "failed"
    val contractType: AhcContractType?,
    val parties: String,
    val summary: String,
    val riskHint: RiskLevel?,
    val confidence: Float?,                   // 0.0 – 1.0
    val error: String?,
) {
    val ok: Boolean get() = status == "ok"
    /** Low-confidence band (< 0.6) — prompts a softer "looks right?" treatment. */
    val isLowConfidence: Boolean get() = (confidence ?: 0f) < 0.6f
}

/** User-confirmed (or overridden) fields. Only present after PUT /ahc/confirm. */
data class AhcConfirmed(
    val contractType: AhcContractType?,
    val parties: String,
    val concern: String,
)

/**
 * Snapshot of the server's funnel state. The full lifecycle of fields:
 *   - documents: populated from step 1 onward
 *   - extraction: populated after step 2 (extracting) completes
 *   - confirmed: populated after step 3 (confirm) is submitted
 */
data class AhcFunnelSession(
    val sessionHash: String,
    val currentStep: AhcStep,
    val status: String,
    val consultationId: String?,
    val paymentStatus: String,
    val amountPaid: Int,
    val documents: List<AhcDocument>,
    val extraction: AhcExtraction?,
    val confirmed: AhcConfirmed?,
)

/** UI-only — files mid-flight or stored offline (not yet on the server). */
data class PendingFileUpload(
    val localId: String,
    val displayName: String,
    val sizeBytes: Long,
    val mime: String,
    val state: PendingUploadState,
    val progress: Int = 0,
    val errorMessage: String? = null,
)

enum class PendingUploadState { UPLOADING, FAILED, OFFLINE }
