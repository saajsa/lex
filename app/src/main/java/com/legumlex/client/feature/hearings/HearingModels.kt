package com.legumlex.client.feature.hearings

import com.legumlex.client.ui.theme.StatusAdjourned
import com.legumlex.client.ui.theme.StatusClosed
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.StatusOngoing
import com.legumlex.client.ui.theme.StatusPalette
import com.legumlex.client.ui.theme.StatusPending
import com.legumlex.client.ui.theme.StatusSettled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class HearingStatus(val label: String, val palette: StatusPalette) {
    SCHEDULED   ("Scheduled",     StatusOngoing),
    IN_PROGRESS ("In Progress",   StatusPending),
    ADJOURNED   ("Adjourned",     StatusAdjourned),
    POSTPONED   ("Postponed",     StatusAdjourned),
    COMPLETED   ("Completed",     StatusSettled),
    CANCELLED   ("Cancelled",     StatusDanger),
    NO_APPEARANCE("No Appearance", StatusDanger),
    UNKNOWN     ("Pending",       StatusClosed);

    companion object {
        fun from(raw: String?): HearingStatus = when (raw?.lowercase()?.trim()) {
            "scheduled"     -> SCHEDULED
            "in progress"   -> IN_PROGRESS
            "adjourned"     -> ADJOURNED
            "postponed"     -> POSTPONED
            "completed"     -> COMPLETED
            "cancelled"     -> CANCELLED
            "no appearance" -> NO_APPEARANCE
            else            -> UNKNOWN
        }
    }
}

data class Hearing(
    val id: String,
    val caseId: String,
    val caseTitle: String,
    val caseNumber: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val status: HearingStatus,
    val courtName: String?,
    val courtNumber: String?,
    val judgeName: String?,
    val purpose: String?,
    val nextDate: LocalDate?,
    val nextTime: LocalTime?,
    val adjournmentReason: String?,
    val createdAt: LocalDateTime?,
    val history: List<HearingHistoryEntry> = emptyList(),
)

data class HearingHistoryEntry(
    val id: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val status: HearingStatus,
    val adjournmentReason: String?,
    val createdAt: LocalDateTime?,
)
