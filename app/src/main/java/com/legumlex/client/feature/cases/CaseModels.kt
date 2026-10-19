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
    CLOSED("closed", "Closed"),
}

enum class CaseStatus(val label: String, private val tone: (LexColors) -> StatusPalette) {
    ONGOING("Ongoing", { it.blue }),
    SETTLED("Settled", { it.success }),
    PENDING("On Hold", { it.warning }),
    CLOSED("Closed",  { it.neutral }),
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
    val hasDocument: Boolean,  // a client-visible certified copy exists
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
)

data class TimelineEvent(
    val type: String,
    val at: LocalDateTime?,
    val title: String,
    val description: String?,
)
