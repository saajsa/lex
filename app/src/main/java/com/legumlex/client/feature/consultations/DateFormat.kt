package com.legumlex.client.feature.consultations

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Shared date helpers used across consultation surfaces. Previously lived
// inside ConsultationsScreen.kt; moved here when that file was rebuilt as
// the Sessions Hub so other consultation screens (detail, thread, etc.)
// can keep referencing them without import gymnastics.

internal fun formatDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("d MMM"))

internal fun relativeAgo(ts: LocalDateTime): String {
    val now = LocalDateTime.now()
    val mins = ChronoUnit.MINUTES.between(ts, now)
    return when {
        mins < 1            -> "just now"
        mins < 60           -> "${mins}m ago"
        mins < 60 * 24      -> "${mins / 60}h ago"
        mins < 60 * 24 * 7  -> "${mins / (60 * 24)}d ago"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24 * 7)}w ago"
        else                -> ts.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
