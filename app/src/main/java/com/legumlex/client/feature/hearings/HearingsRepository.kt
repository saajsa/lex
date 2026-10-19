package com.legumlex.client.feature.hearings

import com.legumlex.client.data.api.ApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class HearingsRepository(private val api: ApiService) {

    suspend fun list(): Result<List<Hearing>> = runCatching {
        val response = api.hearings(id = null)
        extractList(response)
    }

    suspend fun upcoming(): Result<List<Hearing>> = runCatching {
        val response = api.upcomingHearings()
        extractList(response)
    }

    suspend fun detail(id: String): Result<Hearing> = runCatching {
        val response = api.hearings(id = id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Hearing not found")
        parseHearing(data)
    }

    private fun extractList(response: Map<String, Any?>): List<Hearing> {
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        return data.map(::parseHearing)
    }
}

internal fun parseHearing(m: Map<String, Any?>): Hearing {
    @Suppress("UNCHECKED_CAST")
    val historyRaw = m["history"] as? List<Map<String, Any?>> ?: emptyList()
    return Hearing(
        id                = m["id"]?.toString() ?: "",
        caseId            = m["case_id"]?.toString() ?: "",
        caseTitle         = m["case_title"]  as? String ?: "Untitled",
        caseNumber        = m["case_number"] as? String ?: "",
        date              = parseDate(m["date"] as? String),
        time              = parseTime(m["time"] as? String),
        status            = HearingStatus.from(m["status"] as? String),
        courtName         = (m["court_name"] as? String)?.takeIf { it.isNotBlank() },
        courtNumber       = (m["court_no"]   as? String)?.takeIf { it.isNotBlank() },
        judgeName         = (m["judge_name"] as? String)?.takeIf { it.isNotBlank() },
        purpose           = ((m["hearing_purpose"] as? String)
                              ?: (m["upcoming_purpose"] as? String))
                              ?.takeIf { it.isNotBlank() },
        nextDate          = parseDate(m["next_date"] as? String),
        nextTime          = parseTime(m["next_time"] as? String),
        adjournmentReason = (m["adjournment_reason"] as? String)?.takeIf { it.isNotBlank() },
        createdAt         = parseDateTime(m["created_at"] as? String),
        history           = historyRaw.map(::parseHistoryEntry),
    )
}

private fun parseHistoryEntry(m: Map<String, Any?>) = HearingHistoryEntry(
    id                = m["id"]?.toString() ?: "",
    date              = parseDate(m["date"] as? String),
    time              = parseTime(m["time"] as? String),
    status            = HearingStatus.from(m["status"] as? String),
    adjournmentReason = (m["adjournment_reason"] as? String)?.takeIf { it.isNotBlank() },
    createdAt         = parseDateTime(m["created_at"] as? String),
)

private val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
private val timePatterns = listOf("HH:mm:ss", "HH:mm", "h:mm a", "hh:mm a")
private val datetimePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

private fun parseDate(s: String?): LocalDate? {
    if (s.isNullOrBlank()) return null
    for (p in datePatterns) {
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun parseTime(s: String?): LocalTime? {
    if (s.isNullOrBlank()) return null
    for (p in timePatterns) {
        try { return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}

private fun parseDateTime(s: String?): LocalDateTime? {
    if (s.isNullOrBlank()) return null
    for (p in datetimePatterns) {
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}
