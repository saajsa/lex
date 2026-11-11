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
    /** What happened at the hearing, as the advocate noted it. */
    val description: String? = null,
    /** "required" | "optional" | "not_required" | null. */
    val clientAttendance: String? = null,
    /** What the advocate has asked the client to bring or do, as written. */
    val clientInstructions: String? = null,
    /** Purpose of the next date (upcoming_purpose); never this hearing's own purpose. */
    val nextPurpose: String? = null,
    /** What was recorded (Heard, Adjourned, Postponed, No Appearance), or null; the status is then Completed. */
    val outcome: String? = null,
) {
    /** What to show: "Completed · Adjourned" when an outcome is recorded, else the status. */
    val statusLabel: String get() = hearingStatusLabel(status.label, outcome)
}

/** The client's attendance in plain words; null when the advocate hasn't set it. */
fun attendanceLabel(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    "required" -> "Required"
    "optional" -> "Optional"
    "not_required", "not required" -> "Not Required"
    else -> null
}

data class HearingHistoryEntry(
    val id: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val status: HearingStatus,
    val adjournmentReason: String?,
    val createdAt: LocalDateTime?,
    val description: String? = null,
    val nextDate: LocalDate? = null,
    val outcome: String? = null,
) {
    val statusLabel: String get() = hearingStatusLabel(status.label, outcome)
}

/**
 * What to show for a hearing (REQ-016): its outcome when one is recorded, as "Completed · Adjourned" (the server makes a hearing with an outcome
 * Completed and keeps the outcome beside it); otherwise its status as is. An older server sends no outcome: the status shows alone.
 */
fun hearingStatusLabel(status: String, outcome: String?): String {
    val s = status.trim()
    val o = outcome?.trim().orEmpty()
    return if (o.isNotEmpty() && s.equals("Completed", true)) "Completed · $o" else s
}

/** The mark for a hearing: its outcome's when Completed with Adjourned, Postponed or No Appearance recorded, else its own status's (lower case). */
fun hearingMark(status: String, outcome: String?): String {
    val o = outcome?.trim()?.lowercase().orEmpty()
    return if (status.trim().equals("Completed", true) && o in setOf("adjourned", "postponed", "no appearance")) o else status.trim().lowercase()
}
