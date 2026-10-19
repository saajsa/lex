package com.legumlex.client.feature.consultations.detail

import com.legumlex.client.ahc.model.ActivityEntry
import com.legumlex.client.ahc.model.Advocate
import com.legumlex.client.ahc.model.AdvocateNote
import com.legumlex.client.ahc.model.AhcUiState
import com.legumlex.client.ahc.model.ClosureKind
import com.legumlex.client.ahc.model.ClosureLink
import com.legumlex.client.ahc.model.ClosureLinkTarget
import com.legumlex.client.ahc.model.DotState
import com.legumlex.client.ahc.model.EscalationBooking
import com.legumlex.client.ahc.model.Finding
import com.legumlex.client.ahc.model.KeyTerm
import com.legumlex.client.ahc.model.Party
import com.legumlex.client.ahc.model.RecapRow
import com.legumlex.client.ahc.model.SubmittedDoc
import com.legumlex.client.ahc.theme.FindingSeverity
import com.legumlex.client.feature.consultations.AhcAnalysis
import com.legumlex.client.feature.consultations.Consultation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.legumlex.client.ahc.theme.RiskLevel as AhcRiskLevel
import com.legumlex.client.feature.consultations.RiskLevel as AppRiskLevel

/* ============================================================================
 *  Adapter — maps the app's Consultation + AhcAnalysis (the data the JWT
 *  customers_api/v1/consultations/ahc/{id} endpoint actually returns) into the
 *  design package's AhcUiState. Fields the backend doesn't provide (parties,
 *  key terms, per-finding severity, page count/size) are derived or hidden.
 *
 *  Risk direction: the backend sends risk_score as MAGNITUDE (higher = worse,
 *  banded ≤30 LOW … >80 CRITICAL). riskLevel (server-provided) is authoritative
 *  for colour; we fall back to RiskLevel.fromMagnitude(score) when it's absent.
 * ========================================================================== */

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val stampFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun LocalDateTime.atTime(): String = format(timeFmt)
private fun LocalDateTime.atStamp(): String = format(stampFmt)

// The AHC endpoint returns the parties as a single line ("A ↔ B" / "A vs B").
// Split it into a two-sided pair for the vs card; one side or none degrades calmly.
private val partySeparator = Regex("\\s*(?:↔|⇄|\\bvs\\.?\\b|\\bversus\\b)\\s*", RegexOption.IGNORE_CASE)

private fun partiesFrom(raw: String?): List<Party> {
    val s = raw?.trim().orEmpty()
    if (s.isBlank()) return emptyList()
    val parts = s.split(partySeparator).map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> listOf(Party("You", parts[0]), Party("Other party", parts[1]))
        else            -> listOf(Party("Parties", parts.firstOrNull() ?: s))
    }
}

// Key terms from the extraction metadata we actually hold (category + submitted).
// The endpoint doesn't return structured term/fees, so we surface what we have.
private fun keyTermsFrom(ahc: AhcAnalysis?, c: Consultation): List<KeyTerm> = buildList {
    ahc?.agreementType?.takeIf { it.isNotBlank() }?.let { add(KeyTerm("Category", it)) }
    c.createdAt?.let { add(KeyTerm("Submitted", it.atStamp())) }
    (c.releasedAt ?: ahc?.completedAt ?: ahc?.processedAt)?.let { add(KeyTerm("Reviewed", it.atStamp())) }
}

private fun initialsOf(name: String?): String {
    val cleaned = name?.replace(Regex("(?i)^(adv\\.?|advocate|mr\\.?|ms\\.?|mrs\\.?)\\s+"), "")?.trim()
    if (cleaned.isNullOrBlank()) return "AD"
    val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "AD"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun appLevelToAhc(level: AppRiskLevel?, score: Int?): AhcRiskLevel = when (level) {
    AppRiskLevel.LOW      -> AhcRiskLevel.LOW
    AppRiskLevel.MEDIUM   -> AhcRiskLevel.MEDIUM
    AppRiskLevel.HIGH     -> AhcRiskLevel.HIGH
    AppRiskLevel.CRITICAL -> AhcRiskLevel.CRITICAL
    null -> if (score != null) AhcRiskLevel.fromMagnitude(score) else AhcRiskLevel.MEDIUM
}

private fun advocateOf(c: Consultation) = Advocate(
    initials = initialsOf(c.assignedTo),
    name = c.assignedTo?.takeIf { it.isNotBlank() } ?: "Your advocate",
    role = c.assignedToDesignation?.takeIf { it.isNotBlank() } ?: "Advocate",
    avatarUrl = c.assignedToAvatarUrl?.takeIf { it.isNotBlank() },
)

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val kb = bytes / 1024.0
    return if (kb < 1024) "${kb.roundToInt()} KB" else String.format("%.1f MB", kb / 1024.0)
}

private fun agreementNameOf(c: Consultation, ahc: AhcAnalysis?): String =
    ahc?.agreementName?.takeIf { it.isNotBlank() }
        ?: c.tag?.takeIf { it.isNotBlank() }
        ?: "Agreement Check report"

private fun docOf(c: Consultation, ahc: AhcAnalysis?, hasDocument: Boolean) = SubmittedDoc(
    fileName = agreementNameOf(c, ahc),
    sizeLabel = formatBytes(ahc?.agreementSizeBytes),
    pageCount = ahc?.agreementPageCount ?: 0,
    hasDocument = hasDocument,
)

/** Submitted → Reviewed by Lex → Released / Escalated. Only stamped steps shown. */
private fun activityOf(c: Consultation, ahc: AhcAnalysis?): List<ActivityEntry> = buildList {
    c.createdAt?.let { add(ActivityEntry("Submitted for review", it.atStamp(), DotState.DONE)) }
    ahc?.processedAt?.let { add(ActivityEntry("Reviewed by Lex", it.atStamp(), DotState.DONE)) }
    if (c.isEscalated) {
        c.escalatedAt?.let { add(ActivityEntry("Escalated to a consultation", it.atStamp(), DotState.DONE)) }
    } else {
        c.releasedAt?.let { add(ActivityEntry("Report released", it.atStamp(), DotState.DONE)) }
    }
}

/**
 * Build the AHC screen state.
 *
 * @param remainingSeconds live countdown for the in-review state (ticked by the
 *        host); ignored for every other state.
 */
fun buildAhcUiState(
    consultation: Consultation,
    ahc: AhcAnalysis?,
    ahcLoading: Boolean,
    hasDocument: Boolean,
    remainingSeconds: Int,
): AhcUiState {
    val c = consultation
    val name = agreementNameOf(c, ahc)
    val released = c.releasedAt != null || (ahc?.riskScore != null)

    // Still fetching the AHC bundle for an otherwise-undetermined consultation.
    if (ahcLoading && ahc == null && c.closedAt == null && !c.isEscalated &&
        c.releasedAt == null && c.riskScore == null
    ) {
        return AhcUiState.Loading
    }

    return when {
        c.closedAt != null -> closedStateOf(c, ahc, hasDocument)

        c.isEscalated -> AhcUiState.Escalated(
            agreementName = name,
            advocate = advocateOf(c),
            escalatedAtLabel = c.escalatedAt?.atTime() ?: "",
            escalationReason = c.escalationReason?.takeIf { it.isNotBlank() }
                ?: "Your advocate would like to discuss this with you directly.",
            booking = c.linkedConsultationId
                ?.let { EscalationBooking.Booked(reference = "#$it", whenLabel = "Tap to open the consultation") }
                ?: EscalationBooking.AwaitingBooking,
            activity = activityOf(c, ahc),
        )

        released -> {
            // Nullable — a released report may carry no numeric verdict (older
            // / summary-only releases). Never fabricate a score or a band.
            val score = ahc?.riskScore ?: c.riskScore
            val level: AhcRiskLevel? = (ahc?.riskLevel ?: c.riskLevel)?.let { appLevelToAhc(it, score) }
                ?: score?.let { AhcRiskLevel.fromMagnitude(it) }
            val sevForRisks = if (level == AhcRiskLevel.HIGH || level == AhcRiskLevel.CRITICAL)
                FindingSeverity.CRITICAL else FindingSeverity.WARN

            AhcUiState.Released(
                agreementName = name,
                advocate = advocateOf(c),
                releasedAtLabel = (c.releasedAt ?: ahc?.completedAt ?: ahc?.processedAt)?.atTime() ?: "",
                riskScore = score,
                riskLevel = level,
                teaserSummary = ahc?.teaserSummary?.takeIf { it.isNotBlank() }
                    ?: "Your advocate has reviewed this agreement.",
                clausesReviewed = 0,   // backend has no reviewed-count; RiskHero hides when 0
                advocateNote = advocateNoteOf(c),
                parties = partiesFrom(ahc?.parties),
                keyTerms = keyTermsFrom(ahc, c),
                keyRisks = (ahc?.keyRisks ?: emptyList()).map { Finding(it.title, it.detail, sevForRisks) },
                missingProtections = (ahc?.missingClauses ?: emptyList())
                    .map { Finding(it.title, it.detail, FindingSeverity.WARN) },
                recommendations = ahc?.recommendations ?: emptyList(),
                doc = docOf(c, ahc, hasDocument),
                activity = activityOf(c, ahc),
                partialFailureSection = null,
            )
        }

        else -> AhcUiState.Pending(
            agreementName = name,
            doc = docOf(c, ahc, hasDocument),
            advocate = advocateOf(c),
            submittedAtLabel = c.createdAt?.atTime() ?: "",
            etaLabel = c.createdAt?.plusMinutes(30)?.atTime()?.let { "$it IST" } ?: "",
            remainingSeconds = remainingSeconds,
        )
    }
}

private fun closedStateOf(c: Consultation, ahc: AhcAnalysis?, hasDocument: Boolean): AhcUiState.Closed {
    val kind = when (c.closureReason) {
        "completed"                 -> ClosureKind.COMPLETED
        "resolved_via_consultation" -> ClosureKind.RESOLVED_VIA_CONSULTATION
        "converted_to_case"         -> ClosureKind.CONVERTED_TO_CASE
        "cancelled_by_client"       -> ClosureKind.CANCELLED
        else                        -> ClosureKind.OTHER
    }
    val (title, subtitle) = when (kind) {
        ClosureKind.COMPLETED ->
            "Closed · Completed" to "You reviewed the released report and marked this done."
        ClosureKind.RESOLVED_VIA_CONSULTATION ->
            "Closed · Resolved via consultation" to "Escalated to a personal consultation, which has since wrapped up."
        ClosureKind.CONVERTED_TO_CASE ->
            "Closed · Litigation" to "This became a formal court matter your advocate now handles as a case."
        ClosureKind.CANCELLED ->
            "Closed · You cancelled this" to "You stood this request down before it was completed."
        ClosureKind.OTHER ->
            "Closed" to "This Agreement Check has been closed."
    }

    val released = c.releasedAt != null || ahc?.riskScore != null
    val score = ahc?.riskScore ?: c.riskScore
    val level = appLevelToAhc(ahc?.riskLevel ?: c.riskLevel, score)

    val recap = buildList {
        when (kind) {
            ClosureKind.COMPLETED -> {
                if (score != null) add(RecapRow("Verdict", "${level.label} · $score/100"))
                val findings = (ahc?.keyRisks?.size ?: 0) + (ahc?.missingClauses?.size ?: 0)
                if (findings > 0) add(RecapRow("Findings", "$findings flagged"))
                c.releasedAt?.let { add(RecapRow("Released", it.atStamp(), mono = true)) }
            }
            ClosureKind.RESOLVED_VIA_CONSULTATION -> {
                add(RecapRow("Outcome", "Escalated · not released"))
                c.linkedConsultationId?.let { add(RecapRow("Resolved by", "Consultation #$it")) }
            }
            ClosureKind.CANCELLED -> {
                c.closureNote?.takeIf { it.isNotBlank() }?.let { add(RecapRow("Your reason", "“$it”")) }
            }
            else -> {}
        }
    }

    val link = when (kind) {
        ClosureKind.RESOLVED_VIA_CONSULTATION -> c.linkedConsultationId?.let {
            ClosureLink(ClosureLinkTarget.CONSULTATION, "Personal consultation", "#$it", "Open the consultation")
        }
        ClosureKind.CONVERTED_TO_CASE -> (c.linkedCaseId ?: c.linkedConsultationId)?.let { id ->
            ClosureLink(
                ClosureLinkTarget.CASE,
                c.linkedCaseTitle?.takeIf { it.isNotBlank() } ?: "Case",
                c.linkedCaseNumber?.takeIf { it.isNotBlank() } ?: "#$id",
                "Open the case",
            )
        }
        else -> null
    }

    val reportDoc = if (kind == ClosureKind.COMPLETED && released && hasDocument) docOf(c, ahc, true) else null

    val strip = when (kind) {
        ClosureKind.COMPLETED -> if (link == null) "No follow-up was needed." else null
        ClosureKind.CANCELLED -> "Nothing was sent. No follow-up is open."
        else -> null
    }

    val activity = buildList {
        c.createdAt?.let { add(ActivityEntry("Submitted for review", it.atStamp(), DotState.DONE)) }
        if (kind == ClosureKind.RESOLVED_VIA_CONSULTATION) {
            c.escalatedAt?.let { add(ActivityEntry("Escalated to a consultation", it.atStamp(), DotState.DONE)) }
        } else {
            c.releasedAt?.let { add(ActivityEntry("Report released", it.atStamp(), DotState.DONE)) }
        }
        c.closedAt?.let { add(ActivityEntry("Closed", it.atStamp(), DotState.DONE)) }
    }

    return AhcUiState.Closed(
        agreementName = agreementNameOf(c, ahc),
        advocate = advocateOf(c),
        kind = kind,
        title = title,
        subtitle = subtitle,
        closedOnLabel = c.closedAt?.format(dayFmt) ?: "",
        recap = recap,
        link = link,
        reportDoc = reportDoc,
        stripText = strip,
        activity = activity,
    )
}

private fun advocateNoteOf(c: Consultation): AdvocateNote? {
    val raw = c.advocateNote?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val paragraphs = raw.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    if (paragraphs.isEmpty()) return null
    return AdvocateNote(
        paragraphs = paragraphs,
        signedBy = c.assignedTo?.takeIf { it.isNotBlank() } ?: "Your advocate",
        signedAt = c.releasedAt?.atTime() ?: "",
    )
}
