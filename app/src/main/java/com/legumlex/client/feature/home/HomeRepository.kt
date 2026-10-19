package com.legumlex.client.feature.home

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class HomeRepository(private val api: ApiService) {

    /**
     * Fetch JUST the chamber block. Used by the home screen's periodic poll
     * so the "Open / Closed" badge + advocate roster reflects real-time
     * presence without a full dashboard refetch. Silent on failure — the
     * caller falls back to whatever chamber state was cached previously.
     */
    suspend fun loadChamber(): ChamberState? =
        runCatching { api.dashboardChamber() }
            .getOrNull()
            ?.let { parseChamber(it.data()) }

    /**
     * Persist a swipe-to-dismiss so the action row stays gone after a refresh.
     * `key` is the item's stable server id (ActionItem.id). Best-effort — the
     * caller already hides the row optimistically; returns false on failure so
     * it can decide whether to keep the in-memory dismissal.
     */
    suspend fun dismissAction(key: String): Boolean =
        runCatching { api.dismissActionItem(key) }.isSuccess


    suspend fun loadDashboard(): Result<HomeData> = runCatching {
        coroutineScope {
            // Profile is the auth canary — if this fails, surface Error.
            val profileDeferred = async { api.dashboardProfile() }
            val actionsDeferred = async { runCatching { api.dashboardActionItems()     }.getOrNull() }
            val nextDeferred    = async { runCatching { api.dashboardNextEvent()       }.getOrNull() }
            val chamberDeferred = async { runCatching { api.dashboardChamber()         }.getOrNull() }
            val unreadDeferred  = async { runCatching { api.notificationsUnreadCount() }.getOrNull() }
            val spacesDeferred  = async { runCatching { api.workspaces()               }.getOrNull() }

            val profile = profileDeferred.await().data()  // throw → Error state
            val actions = actionsDeferred.await().data()
            val next    = nextDeferred.await().data()
            val chamber = chamberDeferred.await().data()
            val unread  = unreadDeferred.await().data()
            val spaces  = spacesDeferred.await().data()

            HomeData(
                firstName    = (profile?.get("firstname") as? String).orEmpty(),
                unreadCount  = (unread?.get("unread_count") as? Number)?.toInt() ?: 0,
                workspaces   = parseWorkspaces(spaces),
                actionItems  = parseActionItems(actions),
                nextEvent    = parseNextEvent(next),
                chamber      = parseChamber(chamber),
            )
        }
    }
}

// ── Response unwrappers ──────────────────────────────────────────────────────

private fun parseWorkspaces(d: Map<String, Any?>?): Workspaces {
    if (d == null) return Workspaces()
    val t = d["tracks"] as? Map<*, *>
    val f = d["figures"] as? Map<*, *>
    fun n(m: Map<*, *>?, k: String) = (m?.get(k) as? Number)?.toInt() ?: 0
    return Workspaces(
        litigation    = n(t, "litigation"),
        projects      = n(t, "projects"),
        registry      = n(t, "registry"),
        offered       = (d["offered"] as? List<*>)?.mapNotNull { it as? String }?.toSet().orEmpty(),
        consultations = n(f, "total"),
        awaiting      = n(f, "awaiting"),
        resolved      = n(f, "resolved"),
        reports       = n(f, "reports"),
    )
}

private fun Map<String, Any?>?.data(): Map<String, Any?>? {
    if (this == null) return null
    @Suppress("UNCHECKED_CAST")
    return this["data"] as? Map<String, Any?>
}

private fun Map<String, Any?>?.dataList(): List<Map<String, Any?>> {
    if (this == null) return emptyList()
    @Suppress("UNCHECKED_CAST")
    return (this["data"] as? List<Map<String, Any?>>) ?: emptyList()
}

private fun Any?.toIntSafe(): Int = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull() ?: 0
    else      -> 0
}

private fun Any?.toDoubleSafe(): Double = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull() ?: 0.0
    else      -> 0.0
}

// ── Action items ─────────────────────────────────────────────────────────────

private fun parseActionItems(data: Map<String, Any?>?): ActionItemsBundle {
    if (data == null) return ActionItemsBundle(emptyList(), 0, 0, 0)
    @Suppress("UNCHECKED_CAST")
    val rawItems = (data["items"] as? List<Map<String, Any?>>) ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val counts   = (data["counts"] as? Map<String, Any?>) ?: emptyMap()

    val items = rawItems.mapNotNull { item ->
        val kindStr  = item["kind"] as? String ?: return@mapNotNull null
        val id       = item["id"] as? String ?: return@mapNotNull null
        val title    = item["title"] as? String ?: return@mapNotNull null
        val subtitle = item["subtitle"] as? String ?: ""
        val sevStr   = item["severity"] as? String ?: "info"
        val dueAt    = (item["due_at"] as? String)?.let { parseLocalDate(it) }
        @Suppress("UNCHECKED_CAST")
        val linkRaw  = item["deep_link"] as? Map<String, Any?>
        val linkType = linkRaw?.get("type") as? String
        val linkId   = linkRaw?.get("id") as? String ?: ""

        ActionItem(
            kind     = parseActionKind(kindStr),
            id       = id,
            title    = title,
            subtitle = subtitle,
            severity = parseSeverity(sevStr),
            dueAt    = dueAt,
            deepLink = ActionItemDeepLink(parseDeepLinkType(linkType), linkId),
        )
    }

    return ActionItemsBundle(
        items    = items,
        critical = counts["critical"].toIntSafe(),
        warning  = counts["warning"].toIntSafe(),
        info     = counts["info"].toIntSafe(),
    )
}

private fun parseActionKind(s: String): ActionKind = when (s) {
    "invoice_unpaid"                -> ActionKind.InvoiceUnpaid
    "invoice_overdue"               -> ActionKind.InvoiceOverdue
    "contract_unsigned"             -> ActionKind.ContractUnsigned
    "doc_request_pending"           -> ActionKind.DocRequestPending
    "consultation_awaiting_reply"   -> ActionKind.ConsultationAwaitingReply
    "inbound_notice_response_due"   -> ActionKind.InboundNoticeResponseDue
    "appointment_payment_required"  -> ActionKind.AppointmentPaymentRequired
    "task_awaiting_feedback"        -> ActionKind.TaskAwaitingFeedback
    "project_milestone_approaching" -> ActionKind.ProjectMilestoneApproaching
    "ahc_draft_in_progress"         -> ActionKind.AhcDraftInProgress
    "ahc_review_pending"            -> ActionKind.AhcReviewPending
    "ahc_report_ready"              -> ActionKind.AhcReportReady
    else                            -> ActionKind.Unknown
}

private fun parseSeverity(s: String): ActionSeverity = when (s) {
    "critical" -> ActionSeverity.Critical
    "warning"  -> ActionSeverity.Warning
    else       -> ActionSeverity.Info
}

private fun parseDeepLinkType(s: String?): DeepLinkType = when (s) {
    "invoice"      -> DeepLinkType.Invoice
    "contract"     -> DeepLinkType.Contract
    "consultation" -> DeepLinkType.Consultation
    "case"         -> DeepLinkType.Case
    "notice"       -> DeepLinkType.Notice
    "task"         -> DeepLinkType.Task
    "project"      -> DeepLinkType.Project
    "ahc_funnel"   -> DeepLinkType.AhcFunnel
    else           -> DeepLinkType.Unknown
}

// ── Next event ───────────────────────────────────────────────────────────────

private fun parseNextEvent(data: Map<String, Any?>?): NextEvent? {
    if (data == null) return null
    val type = data["event_type"] as? String ?: return null
    return when (type) {
        "hearing" -> {
            val date = parseLocalDate(data["date"] as? String ?: return null) ?: return null
            NextEvent.Hearing(
                hearingId  = data["hearing_id"].toIntSafe(),
                caseId     = data["case_id"].toIntSafe(),
                caseTitle  = (data["case_title"] as? String).orEmpty(),
                caseNumber = data["case_number"] as? String,
                date       = date,
                time       = (data["time"] as? String)?.let { parseLocalTime(it) },
                purpose    = data["purpose"] as? String,
                courtName  = data["court_name"] as? String,
            )
        }
        "appointment" -> {
            val date = parseLocalDate(data["date"] as? String ?: return null) ?: return null
            NextEvent.Appointment(
                appointmentId   = data["appointment_id"].toIntSafe(),
                consultationId  = (data["consultation_id"] as? Number)?.toInt(),
                consultationTag = data["consultation_tag"] as? String,
                serviceName     = data["service_name"] as? String,
                date            = date,
                time            = (data["start_time"] as? String)?.let { parseLocalTime(it) },
                endTime         = (data["end_time"] as? String)?.let { parseLocalTime(it) },
                durationMinutes = (data["duration_minutes"] as? Number)?.toInt(),
                meetLink        = data["meet_link"] as? String,
                status          = data["status"] as? String,
                isLive          = data["is_live"] as? Boolean ?: false,
                isJoinable      = data["is_joinable"] as? Boolean ?: false,
            )
        }
        else -> null
    }
}

// ── Chamber (digital office) ─────────────────────────────────────────────────

internal fun parseChamber(data: Map<String, Any?>?): ChamberState? {
    if (data == null) return null
    @Suppress("UNCHECKED_CAST")
    val rawAdvs = (data["advocates"] as? List<Map<String, Any?>>) ?: emptyList()
    val advs = rawAdvs.mapNotNull { a ->
        ChamberAdvocate(
            staffId      = a["staff_id"].toIntSafe(),
            name         = (a["name"] as? String) ?: return@mapNotNull null,
            firstName    = (a["firstname"] as? String).orEmpty(),
            lastName     = (a["lastname"] as? String).orEmpty(),
            barCouncilId = a["bar_council_id"] as? String,
            designation  = a["designation"] as? String,
            avatarUrl    = a["avatar_url"] as? String,
            lastSeen     = (a["last_seen"] as? String)?.let { parseLocalDateTime(it) },
            isBusy       = a["is_busy"] as? Boolean ?: false,
            sessionStartedAt = (a["session_started_at"] as? String)?.let { parseLocalDateTime(it) },
            queueCount   = a["queue_count"].toIntSafe(),
            etaMinutes   = a["eta_minutes"].toIntSafe(),
        )
    }
    return ChamberState(
        isOpen           = data["is_open"] as? Boolean ?: false,
        onlineCount      = data["online_count"].toIntSafe(),
        sessionFee       = data["session_fee"].toDoubleSafe(),
        currency         = (data["currency"] as? String) ?: "INR",
        hours            = (data["hours"] as? String).orEmpty(),
        location         = (data["location"] as? String).orEmpty(),
        sessionCapMinutes= data["session_cap_minutes"].toIntSafe().takeIf { it > 0 } ?: 45,
        advocates        = advs,
    )
}

// ── Date parsing ─────────────────────────────────────────────────────────────

private val datePatterns     = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
private val timePatterns     = listOf("HH:mm:ss", "HH:mm", "h:mm a", "hh:mm a")
private val datetimePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

private fun parseLocalDate(s: String): LocalDate? {
    for (p in datePatterns) {
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)) }
        catch (_: DateTimeParseException) { }
    }
    return null
}

private fun parseLocalTime(s: String): LocalTime? {
    for (p in timePatterns) {
        try { return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) }
        catch (_: DateTimeParseException) { }
    }
    return null
}

private fun parseLocalDateTime(s: String): LocalDateTime? {
    for (p in datetimePatterns) {
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) }
        catch (_: DateTimeParseException) { }
    }
    return null
}
