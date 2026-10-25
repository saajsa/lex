package com.legumlex.client.feature.projects

import com.legumlex.client.data.api.ApiService
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The server's own message on a refused write (403/422), else [fallback]. */
internal fun serverMessage(e: Throwable, fallback: String): String {
    // error(msg) in this repository carries the server's own message; anything else is ours, not theirs.
    val http = e as? retrofit2.HttpException ?: return (e as? IllegalStateException)?.message?.takeIf { it.isNotBlank() } ?: fallback
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    val msg = runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()
    return msg?.takeIf { it.isNotBlank() } ?: when (http.code()) {
        403 -> "Your account does not allow this on this project."
        else -> fallback
    }
}

/** A 403's wording: a tab the firm hid on this project (`code: tab_hidden`) is said as such. */
internal fun forbiddenMessage(e: Throwable, fallback: String): String {
    val body = runCatching { (e as? retrofit2.HttpException)?.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    val code = runCatching { org.json.JSONObject(body).optString("code") }.getOrNull()
    return if (code == "tab_hidden") "The firm has not shared this part of the project with you." else fallback
}

/** A refusal for want of a permission, told as such rather than as an expired session. */
internal fun isForbidden(e: Throwable): Boolean = (e as? retrofit2.HttpException)?.code() == 403

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
            id         = jId(data["id"]) ?: "",
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
        jId(data?.get("id")) ?: ""
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
    val statusCode = jInt(m["status"])
    return ProjectSummary(
        id            = jId(m["id"]) ?: "",
        name          = m["name"] as? String ?: "Untitled",
        status        = ProjectStatus.from(statusCode),
        progress      = jInt(m["progress"]) ?: 0,
        startDate     = parseDate(m["start_date"] as? String),
        deadline      = parseDate(m["deadline"] as? String),
        billingType   = jInt(m["billing_type"]),
        tasksTotal    = jInt(m["tasks_total"]) ?: 0,
        tasksOpen     = jInt(m["tasks_open"]) ?: 0,
        nextMilestone = parseNextMilestone(m["next_milestone"]),
        stages        = parseStages(m["stages"]),
        team          = parseTeam(m["team"]),
    )
}

@Suppress("UNCHECKED_CAST")
private fun parseTeam(raw: Any?): List<TeamMember> {
    // The server sends a site-relative image path; it resolves against the host the app calls.
    val site = com.legumlex.client.BuildConfig.API_BASE_URL.substringBefore("customers_api/").trimEnd('/')
    return (raw as? List<*>)?.mapNotNull { it as? Map<String, Any?> }?.map { x ->
        TeamMember(
            name     = x["name"] as? String ?: "",
            initials = x["initials"] as? String ?: "",
            image    = (x["image"] as? String)?.takeIf { it.isNotBlank() }?.let { "$site/${it.trimStart('/')}" },
        )
    }.orEmpty()
}

@Suppress("UNCHECKED_CAST")
private fun parseStages(raw: Any?): ClientStages? {
    val m = raw as? Map<String, Any?> ?: return null
    fun stage(x: Map<String, Any?>) = ClientStage(
        milestoneId       = jId(x["milestone_id"]) ?: "",
        name              = x["name"] as? String ?: "",
        number            = jInt(x["number"]),
        state             = x["state"] as? String ?: "pending",
        outcome           = jBool(x["outcome"]),
        clientLine        = x["client_line"] as? String ?: "",
        enteredAt         = parseDate((x["entered_at"] as? String)?.take(10)),
        reachedAt         = parseDate((x["reached_at"] as? String)?.take(10)),
        officialDate      = parseDate(x["official_date"] as? String),
        officialDateLabel = x["official_date_label"] as? String ?: "",
        reference         = x["reference"] as? String ?: "",
    )
    val outcome = m["outcome"] as? Map<String, Any?>
    return ClientStages(
        typeName      = m["type_name"] as? String ?: "",
        chip          = m["chip"] as? String ?: "",
        current       = (m["current"] as? Map<String, Any?>)?.let(::stage),
        currentHidden = jBool(m["current_hidden"]),
        outcomeName   = (outcome?.get("name") as? String)?.takeIf { it.isNotBlank() },
        outcomeLine   = (outcome?.get("outcome_line") as? String)?.takeIf { it.isNotBlank() },
        stages        = (m["stages"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(::stage) }.orEmpty(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun parseNextMilestone(raw: Any?): NextMilestoneRef? {
    val m = raw as? Map<String, Any?> ?: return null
    val id = jId(m["id"]) ?: return null
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

    fun b(k: String) = jBool(settingsMap[k])
    fun i(map: Map<String, Any?>, k: String) = jInt(map[k]) ?: 0

    // The detail endpoint carries its task numbers under counts, not on the
    // row as the list does; without this the progress said "No tasks yet"
    // beside an Open Tasks tile of 3.
    val withCounts = summary.copy(
        tasksTotal = jInt(countsMap["tasks_total"]) ?: summary.tasksTotal,
        tasksOpen  = jInt(countsMap["tasks_open"]) ?: summary.tasksOpen,
    )
    return ProjectDetail(
        summary         = withCounts,
        description     = (m["description"] as? String)?.takeIf { it.isNotBlank() },
        projectCreated  = parseDate(m["project_created"] as? String),
        dateFinished    = parseDateTime(m["date_finished"] as? String),
        projectCost     = jDouble(m["project_cost"]),
        estimatedHours  = jDouble(m["estimated_hours"]),
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
            viewInvoices         = b("view_invoices"),
            viewTaskComments     = b("view_task_comments"),
            viewTaskChecklistItems = b("view_task_checklist_items"),
            viewTaskAttachments  = b("view_task_attachments"),
            openDiscussions      = b("open_discussions"),
        ),
        counts = ProjectCounts(
            tasksTotal      = i(countsMap, "tasks_total"),
            tasksOpen       = i(countsMap, "tasks_open"),
            milestonesTotal = i(countsMap, "milestones_total"),
            filesTotal      = i(countsMap, "files_total"),
        ),
        needs = (m["needs"] as? List<*>)?.mapNotNull { n ->
            (n as? Map<String, Any?>)?.let { NeedItem(jId(it["id"]) ?: return@let null, it["name"] as? String ?: "") }
        }.orEmpty(),
    )
}

private fun parseTaskSummary(m: Map<String, Any?>): TaskSummary = TaskSummary(
    id             = jId(m["id"]) ?: "",
    name           = m["name"] as? String ?: "Untitled",
    status         = TaskStatus.from(jInt(m["status"])),
    priority       = jInt(m["priority"]),
    startDate      = parseDate(m["startdate"] as? String),
    dueDate        = parseDate(m["duedate"] as? String),
    milestoneName  = (m["milestone_name"] as? String)?.takeIf { it.isNotBlank() },
    checklistTotal = jInt(m["checklist_total"]) ?: 0,
    checklistDone  = jInt(m["checklist_done"]) ?: 0,
    commentsCount  = jInt(m["comments_count"]) ?: 0,
)

private fun parseMilestone(m: Map<String, Any?>): Milestone = Milestone(
    id          = jId(m["id"]) ?: "",
    name        = m["name"] as? String ?: "",
    dueDate     = parseDate(m["due_date"] as? String),
    startDate   = parseDate(m["start_date"] as? String),
    color       = (m["color"] as? String)?.takeIf { it.isNotBlank() },
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    tasksTotal  = jInt(m["tasks_total"]) ?: 0,
    tasksDone   = jInt(m["tasks_done"]) ?: 0,
    stageState  = (m["stage_state"] as? String)?.takeIf { it.isNotBlank() },
    enteredAt   = parseDate((m["entered_at"] as? String)?.take(10)),
    reachedAt   = parseDate((m["reached_at"] as? String)?.take(10)),
)

private fun parseFile(m: Map<String, Any?>): ProjectFile {
    val staff   = (m["staff_name"]   as? String)?.takeIf { it.isNotBlank() }
    val contact = (m["contact_name"] as? String)?.takeIf { it.isNotBlank() }
    val external = (m["external"] as? String)?.takeIf { it.isNotBlank() } != null
    return ProjectFile(
        id               = jId(m["id"]) ?: "",
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
        id              = jId(m["id"]) ?: "",
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
            id          = jId(c["id"]) ?: "",
            description = c["description"] as? String ?: "",
            finished    = jBool(c["finished"]),
            order       = jInt(c["list_order"]) ?: 0,
        )
    }
    val comments = (m["comments"] as? List<Map<String, Any?>>).orEmpty().map { c ->
        val author = c["author"] as? Map<String, Any?>
        TaskComment(
            id         = jId(c["id"]) ?: "",
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
        id            = jId(m["id"]) ?: "",
        name          = m["name"] as? String ?: "Untitled",
        description   = (m["description"] as? String)?.takeIf { it.isNotBlank() },
        status        = TaskStatus.from(jInt(m["status"])),
        priority      = jInt(m["priority"]),
        startDate     = parseDate(m["startdate"] as? String),
        dueDate       = parseDate(m["duedate"] as? String),
        dateFinished  = parseDateTime(m["datefinished"] as? String),
        dateAdded     = parseDateTime(m["dateadded"] as? String),
        projectId     = jId(m["project_id"]) ?: "",
        projectName   = (m["project_name"] as? String)?.takeIf { it.isNotBlank() },
        milestoneName = (m["milestone_name"] as? String)?.takeIf { it.isNotBlank() },
        checklist     = checklist,
        comments      = comments,
        assignees     = assignees,
        canComment    = m["can_comment"]?.let { jBool(it) },
        canViewComments = m["can_view_comments"]?.let { jBool(it) },
        staged        = jBool(m["staged"]),
        yours         = m["owner"] == "client",
    )
}

private fun parseDiscussionSummary(m: Map<String, Any?>): DiscussionSummary = DiscussionSummary(
    id            = jId(m["id"]) ?: "",
    subject       = m["subject"] as? String ?: "",
    description   = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    dateCreated   = parseDateTime(m["datecreated"] as? String),
    lastActivity  = parseDateTime(m["last_activity"] as? String),
    authorName    = (m["author_name"] as? String)?.takeIf { it.isNotBlank() },
    totalComments = jInt(m["total_comments"]) ?: 0,
)

@Suppress("UNCHECKED_CAST")
private fun parseDiscussionDetail(m: Map<String, Any?>): DiscussionDetail = DiscussionDetail(
    id          = jId(m["id"]) ?: "",
    subject     = m["subject"] as? String ?: "",
    description = (m["description"] as? String)?.takeIf { it.isNotBlank() },
    dateCreated = parseDateTime(m["datecreated"] as? String),
    comments    = (m["comments"] as? List<Map<String, Any?>>).orEmpty().map(::parseDiscussionComment),
)

private fun parseDiscussionComment(c: Map<String, Any?>): DiscussionComment {
    val author = c["author"] as? Map<String, Any?>
    return DiscussionComment(
        id         = jId(c["id"]) ?: "",
        content    = c["content"] as? String ?: "",
        created    = parseDateTime(c["created"] as? String),
        authorType = author?.get("type") as? String ?: "staff",
        authorName = (author?.get("name") as? String)?.takeIf { it.isNotBlank() },
    )
}

// --- JSON number helpers ---------------------------------------------------
// The API client decodes into Map<String, Any?>, so every JSON number arrives
// as a Double: (int) 40 on the server is 40.0 here, and "40.0".toIntOrNull()
// is null. Every number and id goes through these.

internal fun jInt(v: Any?): Int? = when (v) {
    null -> null
    is Number -> v.toInt()
    is Boolean -> if (v) 1 else 0
    else -> v.toString().trim().let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
}

internal fun jDouble(v: Any?): Double? = when (v) {
    null -> null
    is Number -> v.toDouble()
    else -> v.toString().trim().replace(",", "").toDoubleOrNull()
}

internal fun jBool(v: Any?): Boolean = when (v) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v == "1" || v.equals("true", true)
    else -> false
}

/** An id as the routes carry it: 5.0 and "5" are both "5". */
internal fun jId(v: Any?): String? = when (v) {
    null -> null
    is Number -> v.toLong().toString()
    else -> v.toString().trim().let { t -> t.toDoubleOrNull()?.takeIf { d -> d % 1.0 == 0.0 }?.toLong()?.toString() ?: t }
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
