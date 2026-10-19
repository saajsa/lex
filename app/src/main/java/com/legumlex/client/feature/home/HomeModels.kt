package com.legumlex.client.feature.home

import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Hearings & legacy types kept while migrating
data class HearingPreview(
    val countdownLabel: String,
    val urgency: HearingUrgency,
    val date: String,
    val time: String,
    val caseTitle: String,
    val courtName: String,
)

enum class HearingUrgency { Today, Tomorrow, Soon, Later }

data class ActivityItem(
    val icon: ImageVector,
    val title: String,
    val timestamp: String,
)

// ── New home types ────────────────────────────────────────────────────────────

enum class ActionSeverity { Critical, Warning, Info }

enum class ActionKind {
    InvoiceUnpaid, InvoiceOverdue,
    ContractUnsigned,
    DocRequestPending,
    ConsultationAwaitingReply,
    InboundNoticeResponseDue,
    AppointmentPaymentRequired,
    TaskAwaitingFeedback,
    ProjectMilestoneApproaching,
    // Agreement Health Checkup states — surface alongside everything else.
    AhcDraftInProgress, AhcReviewPending, AhcReportReady,
    Unknown,
}

enum class DeepLinkType { Invoice, Contract, Consultation, Case, Notice, Task, Project, AhcFunnel, Unknown }

data class ActionItemDeepLink(val type: DeepLinkType, val id: String)

data class ActionItem(
    val kind: ActionKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val severity: ActionSeverity,
    val dueAt: LocalDate?,
    val deepLink: ActionItemDeepLink,
)

data class ActionItemsBundle(
    val items: List<ActionItem>,
    val critical: Int,
    val warning: Int,
    val info: Int,
) {
    val total get() = items.size
}

/**
 * Stable identity for an action-queue row, used to dismiss it (swipe-away) and
 * keep it hidden across refreshes within the session. Composite of kind + link
 * so an invoice id and a task id that happen to collide stay distinct.
 */
fun ActionItem.dismissKey(): String = "$kind:${deepLink.type}:$id"

/** Recompute a bundle from a filtered item list (counts follow severities). */
fun List<ActionItem>.toBundle(): ActionItemsBundle = ActionItemsBundle(
    items    = this,
    critical = count { it.severity == ActionSeverity.Critical },
    warning  = count { it.severity == ActionSeverity.Warning },
    info     = count { it.severity == ActionSeverity.Info },
)

sealed interface NextEvent {
    val date: LocalDate
    val time: LocalTime?

    data class Hearing(
        val hearingId: Int,
        val caseId: Int,
        val caseTitle: String,
        val caseNumber: String?,
        override val date: LocalDate,
        override val time: LocalTime?,
        val purpose: String?,
        val courtName: String?,
    ) : NextEvent

    data class Appointment(
        val appointmentId: Int,
        val consultationId: Int?,
        val consultationTag: String?,
        val serviceName: String?,
        override val date: LocalDate,
        override val time: LocalTime?,
        val endTime: LocalTime?,
        val durationMinutes: Int?,
        val meetLink: String?,
        val status: String?,
        val isLive: Boolean,
        val isJoinable: Boolean,
    ) : NextEvent
}

data class ChamberAdvocate(
    val staffId: Int,
    val name: String,
    val firstName: String,
    val lastName: String,
    val barCouncilId: String?,
    val designation: String?,
    val avatarUrl: String?,
    val lastSeen: LocalDateTime?,
    val isBusy: Boolean,
    val sessionStartedAt: LocalDateTime?,
    /**
     * Active waitroom rows for this advocate (waiting + prompted). Drives
     * the "1 waiting · ~14 min" pill on the home roster card so clients can
     * decide whether to walk in or queue before opening the intake screen.
     */
    val queueCount: Int,
    /**
     * Indicative minutes until the next free slot. Includes the remaining
     * time on the current session plus one full session per person ahead.
     * Zero when the advocate is free and the queue is empty.
     */
    val etaMinutes: Int,
)

data class ChamberState(
    val isOpen: Boolean,
    val onlineCount: Int,
    val sessionFee: Double,
    val currency: String,
    val hours: String,
    val location: String,
    val sessionCapMinutes: Int,
    val advocates: List<ChamberAdvocate>,
)

data class HomeData(
    val firstName: String,
    val unreadCount: Int,
    val actionItems: ActionItemsBundle,
    val nextEvent: NextEvent?,
    val chamber: ChamberState?,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val data: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
