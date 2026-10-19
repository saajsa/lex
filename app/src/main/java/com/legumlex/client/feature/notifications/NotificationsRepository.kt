package com.legumlex.client.feature.notifications

import com.legumlex.client.data.api.ApiService
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotificationsRepository(private val api: ApiService) {

    /** Loads + groups the feed into time bands. */
    suspend fun load(now: LocalDateTime = LocalDateTime.now()): Result<NotificationsUiState.Ready> = runCatching {
        val resp = api.notifications()
        if (resp["status"] == false) error("Server rejected the request")
        @Suppress("UNCHECKED_CAST")
        val rows = (resp["data"] as? List<Map<String, Any?>>).orEmpty()

        val items = rows.mapNotNull { parse(it, now) }
        group(items)
    }

    suspend fun markRead(id: String): Result<Unit> = runCatching { api.markNotificationRead(id); Unit }

    suspend fun markAllRead(): Result<Unit> = runCatching { api.markAllNotificationsRead(); Unit }

    // ------------------------------------------------------------------ //

    /** Re-group a flat list into ordered time bands (used after local edits). */
    fun group(items: List<AppNotification>): NotificationsUiState.Ready {
        val order = listOf("Today", "Yesterday", "Earlier this week", "Older")
        val grouped = items.groupBy { bandOf(it.createdAt) }
        val bands = order.mapNotNull { key ->
            val rows = grouped[key].orEmpty()
            if (rows.isEmpty()) null
            else NotifBand(key, rows, rows.count { !it.isRead })
        }
        return NotificationsUiState.Ready(
            bands = bands,
            unreadCount = items.count { !it.isRead },
            total = items.size,
        )
    }

    private fun parse(m: Map<String, Any?>, now: LocalDateTime): AppNotification? {
        val id = (m["id"] as? Number)?.toInt()?.toString()
            ?: (m["id"] as? String) ?: return null
        val createdAt = parseDate(m["created_at"] as? String)
        return AppNotification(
            id        = id,
            type      = typeOf(m["type"] as? String),
            severity  = severityOf(m["severity"] as? String),
            title     = (m["title"] as? String).orEmpty().ifBlank { "Notification" },
            subtitle  = (m["subtitle"] as? String).orEmpty(),
            cta       = (m["cta"] as? String).orEmpty().ifBlank { "Open" },
            link      = (m["link"] as? String).orEmpty(),
            createdAt = createdAt,
            timeLabel = relativeLabel(createdAt, now),
            isRead    = m["is_read"] == true || (m["is_read"] as? Number)?.toInt() == 1,
        )
    }

    private fun typeOf(s: String?): NotifType = when (s?.lowercase()) {
        "hearing"     -> NotifType.HEARING
        "invoice"     -> NotifType.INVOICE
        "payment"     -> NotifType.PAYMENT
        "ahc"         -> NotifType.AHC
        "docrequest"  -> NotifType.DOCREQUEST
        "signature"   -> NotifType.SIGNATURE
        "appointment" -> NotifType.APPOINTMENT
        "task"        -> NotifType.TASK
        "project"     -> NotifType.PROJECT
        "message"     -> NotifType.MESSAGE
        "document"    -> NotifType.DOCUMENT
        "chamber"     -> NotifType.CHAMBER
        else          -> NotifType.GENERAL
    }

    private fun severityOf(s: String?): NotifSeverity = when (s?.lowercase()) {
        "critical" -> NotifSeverity.CRITICAL
        "warning"  -> NotifSeverity.WARNING
        "positive" -> NotifSeverity.POSITIVE
        else       -> NotifSeverity.INFO
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        for (f in dateFormats) {
            runCatching { return LocalDateTime.parse(s, f) }
        }
        // date-only fallback
        runCatching { return LocalDate.parse(s).atStartOfDay() }
        return null
    }

    private fun bandOf(dt: LocalDateTime?): String {
        if (dt == null) return "Older"
        val today = LocalDate.now()
        val d = dt.toLocalDate()
        return when {
            d == today                       -> "Today"
            d == today.minusDays(1)          -> "Yesterday"
            d.isAfter(today.minusDays(7))    -> "Earlier this week"
            else                             -> "Older"
        }
    }

    private fun relativeLabel(dt: LocalDateTime?, now: LocalDateTime): String {
        if (dt == null) return ""
        val today = now.toLocalDate()
        val d = dt.toLocalDate()
        return when {
            d == today -> {
                val mins = Duration.between(dt, now).toMinutes().coerceAtLeast(0)
                when {
                    mins < 1   -> "now"
                    mins < 60  -> "${mins}m"
                    else       -> "${mins / 60}h"
                }
            }
            d == today.minusDays(1)        -> timeOfDay(dt)              // yesterday → 6:10p
            d.isAfter(today.minusDays(7))  -> dt.format(weekdayFmt)      // this week → Mon
            else                           -> dt.format(dayMonthFmt)     // older → 12 Jun
        }
    }

    private fun timeOfDay(dt: LocalDateTime): String {
        val h12 = ((dt.hour + 11) % 12) + 1
        val mm = dt.minute.toString().padStart(2, '0')
        val ap = if (dt.hour < 12) "a" else "p"
        return "$h12:$mm$ap"
    }

    private companion object {
        val dateFormats = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        )
        val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}
