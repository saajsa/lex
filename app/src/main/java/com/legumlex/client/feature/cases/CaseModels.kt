package com.legumlex.client.feature.cases

import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.Lex
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class CaseFilter(val apiValue: String?, val label: String) {
    ALL(null, "All"),
    ACTIVE("active", "Active"),
    CLOSED("closed", "Disposed"),
}

/**
 * A case's status in the cases module's own words (case_status:
 * active / pending / disposed), as the portal's case_pill shows them.
 */
enum class CaseStatus(val label: String, private val tone: (LexColors) -> StatusPalette) {
    ONGOING("Active", { it.blue }),
    SETTLED("Disposed", { it.neutral }),
    PENDING("Pending", { it.warning }),
    CLOSED("Disposed",  { it.neutral }),
    UNKNOWN("Pending", { it.neutral });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)

    companion object {
        fun from(raw: String?): CaseStatus = when (raw?.lowercase()?.trim()) {
            // A transferred case is still live (it moved courts), so it reads
            // as Ongoing; a completed case is terminal, so it reads as Closed.
            "active", "ongoing", "transferred"                          -> ONGOING
            "settled", "resolved"                                       -> SETTLED
            "on_hold", "pending"                                        -> PENDING
            "closed", "dismissed", "withdrawn", "disposed", "completed" -> CLOSED
            else                                                        -> UNKNOWN
        }
    }
}

data class CaseSummary(
    val id: String,
    val title: String,
    val number: String,
    val status: CaseStatus,
    val courtName: String?,
    val courtNo: String?,
    val judgeName: String?,
    val filedDate: LocalDate?,
    val hearingCount: Int,
    val nextHearingDate: LocalDate?,
    val nextHearingTime: LocalTime?,
    val nextHearingPurpose: String?,
    val lastActivityAt: LocalDateTime?,
    // Disposition for closed/settled matters — the client's "how did it end?".
    val disposalType: String?,
    val disposalDate: LocalDate?,
)

data class CaseDetail(
    val summary: CaseSummary,
    val filedDate: LocalDate?,
    val court: String?,
    val courtHierarchy: String?,
    val courtNumber: String?,
    val benchType: String?,
    val judge: String?,
    val advocate: String?,
    val advocateDesignation: String?,
    val advocateEmail: String?,
    val advocateAvatarUrl: String?,
    val originConsultationId: String?,
    val disposalReason: String?,
    /** The last day to appeal, as the firm recorded it (information only). */
    val appealDeadline: LocalDate? = null,
    val appealNote: String? = null,
    val parties: Parties,
    val acts: List<CaseAct>,
    val orders: List<CaseOrder>,
    val documents: List<CaseFile>,
    val upcomingHearings: List<HearingRow>,
    val pastHearings: List<HearingRow>,
)

data class Parties(
    val petitioners: List<Party>,
    val respondents: List<Party>,
    /** Interveners, proforma parties and anyone else on the record. */
    val others: List<Party> = emptyList(),
)

data class Party(
    val name: String,
    val address: String?,
    val advocateName: String? = null,
    val role: String? = null,
)

data class CaseAct(val name: String, val sections: String?)

data class CaseOrder(
    val id: String,
    val date: LocalDate?,
    val type: String,          // order | interim | final
    val relief: String?,       // short badge, e.g. "Stay granted"
    val summary: String?,      // AI short summary (card)
    val fullText: String?,     // AI verbatim transcription (toggle)
    val coram: String?,        // bench as printed on the order
    val nextDate: LocalDate?,
    val purposeNext: String?,
    val hasDocument: Boolean,  // the order itself is on file and shared with the client
    val documentName: String?,
    val ocrStatus: String,     // pending | done | failed — gates the summary state
)

data class CaseFile(
    val id: String,
    val name: String,
    val fileType: String?,
    val date: LocalDate?,
    val tag: String,             // relation-type label (Order, Docket, Legal Notice, …)
    val relation: String,        // "case" (filed against the matter) | "hearing"
    val hearingDate: LocalDate? = null,  // for hearing-relation docs
)

data class HearingRow(
    val id: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val status: String,
    val purpose: String?,
    val description: String? = null,   // hearing notes / what happened
    val adjournmentReason: String? = null,
    val nextDate: LocalDate? = null,
    /** "required" | "optional" | "not_required" | null — whether the client is to attend. */
    val clientAttendance: String? = null,
    /** What the advocate has asked the client to bring or do, as written. */
    val clientInstructions: String? = null,
)

/** Hearings that are still to happen: not cancelled, not already heard. */
val HearingRow.isLive: Boolean
    get() = status.trim().lowercase() !in setOf("cancelled", "completed")

data class TimelineEvent(
    val type: String,
    val at: LocalDateTime?,
    val title: String,
    val description: String?,
)
