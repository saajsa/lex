package com.legumlex.client.feature.projects

import com.legumlex.client.data.api.ApiService
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProjectsRepository(private val api: ApiService) {

    suspend fun list(filter: ProjectFilter = ProjectFilter.ACTIVE): Result<List<ProjectSummary>> = runCatching {
        val response = api.projects(status = filter.apiValue)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseSummary)
    }

    suspend fun detail(id: String): Result<ProjectDetail> = runCatching {
        val response = api.project(id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Project not found")
        parseDetail(data)
    }

    suspend fun tasks(projectId: String): Result<List<TaskSummary>> = runCatching {
        val response = api.projectTasks(projectId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseTaskSummary)
    }

    suspend fun milestones(projectId: String): Result<List<Milestone>> = runCatching {
        val response = api.projectMilestones(projectId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseMilestone)
    }

    suspend fun files(projectId: String): Result<List<ProjectFile>> = runCatching {
        val response = api.projectFiles(projectId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseFile)
    }

    suspend fun activity(projectId: String): Result<List<ProjectActivityItem>> = runCatching {
        val response = api.projectActivity(projectId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseActivity)
    }

    suspend fun taskDetail(id: String): Result<TaskDetail> = runCatching {
        val response = api.task(id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Task not found")
        parseTaskDetail(data)
    }

    // --- write / interaction ------------------------------------------------

    /** Post a comment on a task; returns the created comment. */
    suspend fun postTaskComment(taskId: String, content: String): Result<TaskComment> = runCatching {
        val response = api.postTaskComment(taskId, content)
        if (response["status"] != true) error(response["message"]?.toString() ?: "Could not post comment")
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data")
        val author = data["author"] as? Map<String, Any?>
        TaskComment(
            id         = data["id"]?.toString() ?: "",
            content    = data["content"] as? String ?: content,
            dateAdded  = parseDateTime(data["dateadded"] as? String),
            authorType = author?.get("type") as? String ?: "contact",
            authorName = (author?.get("name") as? String)?.takeIf { it.isNotBlank() },
        )
    }

    /** Create a client task; returns the new task id. */
    suspend fun createTask(
        projectId: String,
        name: String,
        startDate: String,
        dueDate: String?,
        priority: Int,
        milestone: Int,
        description: String?,
    ): Result<String> = runCatching {
        val response = api.createProjectTask(projectId, name, startDate, dueDate, priority, milestone, description)
        if (response["status"] != true) error(response["message"]?.toString() ?: "Could not create task")
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
        data?.get("id")?.toString() ?: ""
    }

    /** Upload a file to a project; returns the new file row. */
    suspend fun uploadFile(projectId: String, part: MultipartBody.Part): Result<ProjectFile> = runCatching {
        val response = api.uploadProjectFile(projectId, part)
        if (response["status"] != true) error(response["message"]?.toString() ?: "Upload failed")
        @Suppress("UNCHECKED_CAST")
        val row = response["data"] as? Map<String, Any?> ?: error("No data")
        parseFile(row)
    }

    /** Raw stream of a project file — caller saves to cache and opens. */
    suspend fun downloadFile(fileId: String): ResponseBody = api.downloadProjectFile(fileId)

    suspend fun discussions(projectId: String): Result<List<DiscussionSummary>> = runCatching {
        val response = api.projectDiscussions(projectId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseDiscussionSummary)
    }

    suspend fun discussion(id: String): Result<DiscussionDetail> = runCatching {
        val response = api.projectDiscussion(id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("Discussion not found")
        parseDiscussionDetail(data)
    }

    suspend fun postDiscussionComment(id: String, content: String): Result<DiscussionComment> = runCatching {
        val response = api.postDiscussionComment(id, content)
        if (response["status"] != true) error(response["message"]?.toString() ?: "Could not post comment")
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: error("No data")
        parseDiscussionComment(data)
    }
}

// --- parsers ---------------------------------------------------------------

private fun parseSummary(m: Map<String, Any?>): ProjectSummary {
    val statusCode = m["status"]?.toString()?.toIntOrNull()
    return ProjectSummary(
        id            = m["id"]?.toString() ?: "",
        name          = m["name"] as? String ?: "Untitled",
        status        = ProjectStatus.from(statusCode),
        progress      = m["progress"]?.toString()?.toIntOrNull() ?: 0,
        startDate     = parseDate(m["start_date"] as? String),
        deadline      = parseDate(m["deadline"] as? String),
        billingType   = m["billing_type"]?.toString()?.toIntOrNull(),
        tasksTotal    = m["tasks_total"]?.toString()?.toIntOrNull() ?: 0,
        tasksOpen     = m["tasks_open"]?.toString()?.toIntOrNull() ?: 0,
        nextMilestone = parseNextMilestone(m["next_milestone"]),
    )
}

@Suppress("UNCHECKED_CAST")
private fun parseNextMilestone(raw: Any?): NextMilestoneRef? {
    val m = raw as? Map<String, Any?> ?: return null
    val id = m["id"]?.toString() ?: return null
    return NextMilestoneRef(
        id      = id,
        name    = m["name"] as? String ?: "",
        dueDate = parseDate(m["due_date"] as? String),
    )
}

@Suppress("UNCHECKED_CAST")
private fun parseDetail(m: Map<String, Any?>): ProjectDetail {
    val summary = parseSummary(m)
    val settingsMap = m["settings"] as? Map<String, Any?> ?: emptyMap()
    val countsMap   = m["counts"]   as? Map<String, Any?> ?: emptyMap()

    fun b(k: String) = settingsMap[k] as? Boolean ?: false
    fun i(map: Map<String, Any?>, k: String) = map[k]?.toString()?.toIntOrNull() ?: 0

    return ProjectDetail(
        summary         = summary,
        description     = (m["description"] as? String)?.takeIf { it.isNotBlank() },
        projectCreated  = parseDate(m["project_created"] as? String),
        dateFinished    = parseDateTime(m["date_finished"] as? String),
        projectCost     = (m["project_cost"] as? String)?.toDoubleOrNull(),
        estimatedHours  = (m["estimated_hours"] as? String)?.toDoubleOrNull(),
        settings = ProjectSettings(
            viewTasks            = b("view_tasks"),
            viewMilestones       = b("view_milestones"),
            viewFiles            = b("view_files"),
            viewDiscussions      = b("view_discussions"),
            viewGantt            = b("view_gantt"),
            viewTimesheets       = b("view_timesheets"),
            viewActivityLog      = b("view_activity_log"),
            viewTeamMembers      = b("view_team_members"),
            viewFinanceOverview  = b("view_finance_overview"),
            createTasks          = b("create_tasks"),
            commentOnTasks       = b("comment_on_tasks"),
            uploadFiles          = b("upload_files"),
        ),
        counts = ProjectCounts(
            tasksTotal      = i(countsMap, "tasks_total"),
            tasksOpen       = i(countsMap, "tasks_open"),
            milestonesTotal = i(countsMap, "milestones_total"),
            filesTotal      = i(countsMap, "files_total"),
        ),
    )
}

private fun parseTaskSummary(m: Map<String, Any?>): TaskSummary = TaskSummary(
    id             = m["id"]?.toString() ?: "",
    name           = m["name"] as? String ?: "Untitled",
    status         = TaskStatus.from(m["status"]?.toString()?.toIntOrNull()),
    priority       = m["priority"]?.toString()?.toIntOrNull(),
    startDate      = parseDate(m["startdate"] as? String),
    dueDate        = parseDate(m["duedate"] as? String),
    milestoneName  = (m["milestone_name"] as? String)?.takeIf { it.isNotBlank() },
    checklistTotal = m["checklist_total"]?.toString()?.toIntOrNull() ?: 0,
    checklistDone  = m["checklist_done"]?.toString()?.toIntOrNull() ?: 0,
    commentsCount  = m["comments_count"]?.toString()?.toIntOrNull() ?: 0,
)

private fun parseMilestone(m: Map<String, Any?>): Milestone = Milestone(
    id          = m["id"]?.toString() ?: "",
    name        = m["name"] as? String ?: "",
    dueDate     = parseDate(m["due_date"] as? String),
    startDate   = parseDate(m["start_date"] as? String),
    color       = (m["color"] as? String)?.takeIf { it.isNotBlank() },
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    tasksTotal  = m["tasks_total"]?.toString()?.toIntOrNull() ?: 0,
    tasksDone   = m["tasks_done"]?.toString()?.toIntOrNull() ?: 0,
)

private fun parseFile(m: Map<String, Any?>): ProjectFile {
    val staff   = (m["staff_name"]   as? String)?.takeIf { it.isNotBlank() }
    val contact = (m["contact_name"] as? String)?.takeIf { it.isNotBlank() }
    val external = (m["external"] as? String)?.takeIf { it.isNotBlank() } != null
    return ProjectFile(
        id               = m["id"]?.toString() ?: "",
        fileName         = m["file_name"] as? String ?: "",
        originalFileName = (m["original_file_name"] as? String)?.takeIf { it.isNotBlank() },
        fileType         = (m["filetype"] as? String)?.takeIf { it.isNotBlank() },
        subject          = (m["subject"]  as? String)?.takeIf { it.isNotBlank() },
        dateAdded        = parseDateTime(m["dateadded"] as? String),
        authorName       = staff ?: contact,
        fromClient       = staff == null && contact != null,
        isExternal       = external,
        externalLink     = (m["external_link"] as? String)?.takeIf { it.isNotBlank() },
    )
}

private fun parseActivity(m: Map<String, Any?>): ProjectActivityItem {
    val actor = (m["actor_name"] as? String)?.takeIf { it.isNotBlank() }
        ?: (m["staff_name"]   as? String)?.takeIf { it.isNotBlank() }
        ?: (m["contact_name"] as? String)?.takeIf { it.isNotBlank() }
    return ProjectActivityItem(
        id              = m["id"]?.toString() ?: "",
        descriptionKey  = m["description_key"] as? String ?: "",
        additionalData  = (m["additional_data"] as? String)?.takeIf { it.isNotBlank() },
        actorName       = actor,
        dateAdded       = parseDateTime(m["dateadded"] as? String),
    )
}

@Suppress("UNCHECKED_CAST")
private fun parseTaskDetail(m: Map<String, Any?>): TaskDetail {
    val checklist = (m["checklist"] as? List<Map<String, Any?>>).orEmpty().map { c ->
        ChecklistItem(
            id          = c["id"]?.toString() ?: "",
            description = c["description"] as? String ?: "",
            finished    = c["finished"] as? Boolean ?: false,
            order       = c["list_order"]?.toString()?.toIntOrNull() ?: 0,
        )
    }
    val comments = (m["comments"] as? List<Map<String, Any?>>).orEmpty().map { c ->
        val author = c["author"] as? Map<String, Any?>
        TaskComment(
            id         = c["id"]?.toString() ?: "",
            content    = c["content"] as? String ?: "",
            dateAdded  = parseDateTime(c["dateadded"] as? String),
            authorType = author?.get("type") as? String ?: "staff",
            authorName = (author?.get("name") as? String)?.takeIf { it.isNotBlank() },
        )
    }
    val assignees = (m["assignees"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { a ->
        (a["name"] as? String)?.takeIf { it.isNotBlank() }
    }
    return TaskDetail(
        id            = m["id"]?.toString() ?: "",
        name          = m["name"] as? String ?: "Untitled",
        description   = (m["description"] as? String)?.takeIf { it.isNotBlank() },
        status        = TaskStatus.from(m["status"]?.toString()?.toIntOrNull()),
        priority      = m["priority"]?.toString()?.toIntOrNull(),
        startDate     = parseDate(m["startdate"] as? String),
        dueDate       = parseDate(m["duedate"] as? String),
        dateFinished  = parseDateTime(m["datefinished"] as? String),
        dateAdded     = parseDateTime(m["dateadded"] as? String),
        projectId     = m["project_id"]?.toString() ?: "",
        projectName   = (m["project_name"] as? String)?.takeIf { it.isNotBlank() },
        milestoneName = (m["milestone_name"] as? String)?.takeIf { it.isNotBlank() },
        checklist     = checklist,
        comments      = comments,
        assignees     = assignees,
    )
}

private fun parseDiscussionSummary(m: Map<String, Any?>): DiscussionSummary = DiscussionSummary(
    id            = m["id"]?.toString() ?: "",
    subject       = m["subject"] as? String ?: "",
    description   = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    dateCreated   = parseDateTime(m["datecreated"] as? String),
    lastActivity  = parseDateTime(m["last_activity"] as? String),
    authorName    = (m["author_name"] as? String)?.takeIf { it.isNotBlank() },
    totalComments = m["total_comments"]?.toString()?.toIntOrNull() ?: 0,
)

@Suppress("UNCHECKED_CAST")
private fun parseDiscussionDetail(m: Map<String, Any?>): DiscussionDetail = DiscussionDetail(
    id          = m["id"]?.toString() ?: "",
    subject     = m["subject"] as? String ?: "",
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    dateCreated = parseDateTime(m["datecreated"] as? String),
    comments    = (m["comments"] as? List<Map<String, Any?>>).orEmpty().map(::parseDiscussionComment),
)

private fun parseDiscussionComment(c: Map<String, Any?>): DiscussionComment {
    val author = c["author"] as? Map<String, Any?>
    return DiscussionComment(
        id         = c["id"]?.toString() ?: "",
        content    = c["content"] as? String ?: "",
        created    = parseDateTime(c["created"] as? String),
        authorType = author?.get("type") as? String ?: "staff",
        authorName = (author?.get("name") as? String)?.takeIf { it.isNotBlank() },
    )
}

// --- date helpers (tolerant of Perfex formats) ----------------------------

private val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
private val datetimePatterns = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm",
)

private fun parseDate(s: String?): LocalDate? {
    if (s.isNullOrBlank() || s == "0000-00-00") return null
    for (p in datePatterns) {
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun parseDateTime(s: String?): LocalDateTime? {
    if (s.isNullOrBlank() || s.startsWith("0000")) return null
    for (p in datetimePatterns) {
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}
