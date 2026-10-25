package com.legumlex.client.feature.projects

import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.Lex
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate
import java.time.LocalDateTime

enum class ProjectFilter(val apiValue: String?, val label: String) {
    ACTIVE("active", "Active"),
    ALL(null,        "All"),
    CLOSED("closed", "Closed"),
}

enum class ProjectStatus(val code: Int, val label: String, private val tone: (LexColors) -> StatusPalette) {
    NotStarted(1, "Not Started", { it.neutral }),
    InProgress(2, "In Progress", { it.blue }),
    OnHold(3,     "On Hold",     { it.info }),
    Finished(4,   "Finished",    { it.success }),
    Cancelled(5,  "Cancelled",   { it.neutral }),
    Unknown(0,    "Unknown",     { it.neutral });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)

    companion object {
        fun from(code: Int?): ProjectStatus = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

enum class TaskStatus(val code: Int, val label: String, private val tone: (LexColors) -> StatusPalette) {
    NotStarted(1,       "Not Started",       { it.neutral }),
    AwaitingFeedback(2, "Awaiting Feedback", { it.warning }),
    Testing(3,          "Testing",           { it.blue }),
    InProgress(4,       "In Progress",       { it.blue }),
    Complete(5,         "Complete",          { it.success }),
    Unknown(0,          "Unknown",           { it.neutral });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)

    companion object {
        fun from(code: Int?): TaskStatus = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

data class NextMilestoneRef(
    val id: String,
    val name: String,
    val dueDate: LocalDate?,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val status: ProjectStatus,
    val progress: Int,           // 0..100
    val startDate: LocalDate?,
    val deadline: LocalDate?,
    val billingType: Int?,
    val tasksTotal: Int,
    val tasksOpen: Int,
    val nextMilestone: NextMilestoneRef?,
    /** Where a typed project stands (cases Project Types); null for a blank one, or milestones not shared. */
    val stages: ClientStages? = null,
    /** The project's team, when the project shows it to the client. */
    val team: List<TeamMember> = emptyList(),
)

/** One member of a project's team, for a card's avatars. [image] is an absolute URL or null. */
data class TeamMember(val name: String, val initials: String, val image: String?)

/**
 * A typed project's stages as the client sees them: only the ones shown to
 * them, numbered over those, never a planned date. [chip] is the portal's
 * whose-move wording: Waiting On You, With The Firm, With The {authority},
 * Closed, On Hold, Work Stopped.
 */
data class ClientStages(
    val typeName: String,
    val chip: String,
    val current: ClientStage?,
    val currentHidden: Boolean,
    val outcomeName: String?,
    val outcomeLine: String?,
    val stages: List<ClientStage>,
)

data class ClientStage(
    val milestoneId: String,
    val name: String,
    val number: Int?,
    /** pending · current · reached · skipped · not_applicable */
    val state: String,
    val outcome: Boolean,
    val clientLine: String,
    val enteredAt: LocalDate?,
    val reachedAt: LocalDate?,
    val officialDate: LocalDate?,
    val officialDateLabel: String,
    val reference: String,
)

/** Something the firm is waiting on the client for: an open task of theirs on the current stage. */
data class NeedItem(val id: String, val name: String)

data class ProjectSettings(
    val viewTasks: Boolean,
    val viewMilestones: Boolean,
    val viewFiles: Boolean,
    val viewDiscussions: Boolean,
    val viewGantt: Boolean,
    val viewTimesheets: Boolean,
    val viewActivityLog: Boolean,
    val viewTeamMembers: Boolean,
    val viewFinanceOverview: Boolean,
    // Client write permissions — gate the interaction UI.
    val createTasks: Boolean = false,
    val commentOnTasks: Boolean = false,
    val uploadFiles: Boolean = false,
    val viewInvoices: Boolean = false,
    val viewTaskComments: Boolean = false,
    val viewTaskChecklistItems: Boolean = false,
    val viewTaskAttachments: Boolean = false,
    /** The client may start a discussion (the portal's open_discussions). */
    val openDiscussions: Boolean = false,
)

data class ProjectCounts(
    val tasksTotal: Int,
    val tasksOpen: Int,
    val milestonesTotal: Int,
    val filesTotal: Int,
)

data class ProjectDetail(
    val summary: ProjectSummary,
    val description: String?,
    val projectCreated: LocalDate?,
    val dateFinished: LocalDateTime?,
    val projectCost: Double?,
    val estimatedHours: Double?,
    val settings: ProjectSettings,
    val counts: ProjectCounts,
    val needs: List<NeedItem> = emptyList(),
)

data class TaskSummary(
    val id: String,
    val name: String,
    val status: TaskStatus,
    val priority: Int?,
    val startDate: LocalDate?,
    val dueDate: LocalDate?,
    val milestoneName: String?,
    val checklistTotal: Int,
    val checklistDone: Int,
    val commentsCount: Int,
)

data class Milestone(
    val id: String,
    val name: String,
    val dueDate: LocalDate?,
    val startDate: LocalDate?,
    val color: String?,
    val description: String?,
    val tasksTotal: Int,
    val tasksDone: Int,
    /** A project's stage: its state, and when it was started and completed (no planned dates). */
    val stageState: String? = null,
    val enteredAt: LocalDate? = null,
    val reachedAt: LocalDate? = null,
)

data class ProjectFile(
    val id: String,
    val fileName: String,
    val originalFileName: String?,
    val fileType: String?,
    val subject: String?,
    val dateAdded: LocalDateTime?,
    val authorName: String?,    // staff or contact, whichever filled
    val fromClient: Boolean = false,  // uploaded by the client (contact) vs shared by chamber (staff)
    val isExternal: Boolean,
    val externalLink: String?,
)

data class ProjectActivityItem(
    val id: String,
    val descriptionKey: String,
    val additionalData: String?,
    val actorName: String?,
    val dateAdded: LocalDateTime?,
)

data class ChecklistItem(
    val id: String,
    val description: String,
    val finished: Boolean,
    val order: Int,
)

data class TaskComment(
    val id: String,
    val content: String,
    val dateAdded: LocalDateTime?,
    val authorType: String,     // "staff" | "contact"
    val authorName: String?,
)

data class TaskDetail(
    val id: String,
    val name: String,
    val description: String?,
    val status: TaskStatus,
    val priority: Int?,
    val startDate: LocalDate?,
    val dueDate: LocalDate?,
    val dateFinished: LocalDateTime?,
    val dateAdded: LocalDateTime?,
    val projectId: String,
    val projectName: String?,
    val milestoneName: String?,
    val checklist: List<ChecklistItem>,
    val comments: List<TaskComment>,
    val assignees: List<String>,    // staff names only
    /** The project lets the client comment on tasks (tasks/{id} `can_comment`); null when the server does not say. */
    val canComment: Boolean? = null,
    /** The firm shows task comments to the client (tasks/{id} `can_view_comments`); null when the server does not say. */
    val canViewComments: Boolean? = null,
    /** The task's milestone is a stage of a typed project (tasks/{id} `staged`). */
    val staged: Boolean = false,
    /** The client's own task (tasks/{id} `owner` = client): theirs to do, not the firm's. */
    val yours: Boolean = false,
)

// --- Discussions ------------------------------------------------------------

data class DiscussionSummary(
    val id: String,
    val subject: String,
    val description: String?,
    val dateCreated: LocalDateTime?,
    val lastActivity: LocalDateTime?,
    val authorName: String?,
    val totalComments: Int,
)

data class DiscussionComment(
    val id: String,
    val content: String,
    val created: LocalDateTime?,
    val authorType: String,     // "staff" | "contact"
    val authorName: String?,
)

data class DiscussionDetail(
    val id: String,
    val subject: String,
    val description: String?,
    val dateCreated: LocalDateTime?,
    val comments: List<DiscussionComment>,
)

/** A document the client can open in the Files tab (after streaming to cache). */
data class ProjectPriority(val code: Int, val label: String) {
    companion object {
        val ALL = listOf(
            ProjectPriority(1, "Low"),
            ProjectPriority(2, "Medium"),
            ProjectPriority(3, "High"),
            ProjectPriority(4, "Urgent"),
        )
    }
}
