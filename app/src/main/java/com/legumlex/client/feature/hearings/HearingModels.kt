package com.legumlex.client.feature.hearings

import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.Lex
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class HearingStatus(val label: String, private val tone: (LexColors) -> StatusPalette) {
    SCHEDULED   ("Scheduled",     { it.blue }),
    IN_PROGRESS ("In Progress",   { it.warning }),
    ADJOURNED   ("Adjourned",     { it.info }),
    POSTPONED   ("Postponed",     { it.info }),
    COMPLETED   ("Completed",     { it.success }),
    CANCELLED   ("Cancelled",     { it.danger }),
    NO_APPEARANCE("No Appearance", { it.danger }),
    UNKNOWN     ("Pending",       { it.neutral });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)

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
