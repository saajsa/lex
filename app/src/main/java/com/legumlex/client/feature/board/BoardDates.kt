package com.legumlex.client.feature.board

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "1 credit", "5 credits" (count grouped Indian style). */
fun creditsLabel(n: Int): String = "${creditsText(n)} ${if (n == 1) "credit" else "credits"}"

/** Credits are counted the Indian way: 2,100 and 1,00,000. */
fun creditsText(n: Int): String {
    val digits = kotlin.math.abs(n.toLong()).toString()
    val grouped = if (digits.length <= 3) digits else {
        val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,${digits.takeLast(3)}"
    }
    return if (n < 0) "-$grouped" else grouped
}

/**
 * Dates on the Daily Board are courts' dates, so they are India's: "today" is
 * today in Asia/Kolkata whatever the phone's own zone says (a phone abroad, or
 * set wrongly, still opens the board on the day the courts are sitting).
 */
object BoardDates {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    private val DAY_LONG = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_SHORT_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)

    /** Today in India. */
    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(IST))

    /** Milliseconds from [now] to the start of the next day in India (plus a second), when "today" changes under an open screen. */
    fun millisToNextDay(now: Instant): Long =
        java.time.Duration.between(now, now.atZone(IST).toLocalDate().plusDays(1).atStartOfDay(IST).toInstant()).toMillis() + 1_000

    fun todayAt(instant: Instant): LocalDate = instant.atZone(IST).toLocalDate()

    /** The date the API takes and gives: yyyy-MM-dd. */
    fun api(d: LocalDate): String = d.toString()

    /** A date from the API, or null when it is blank or not a date. */
    fun parse(s: String?): LocalDate? = s?.trim()?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    /** How many days the strip reaches back and forward from its anchor. */
    const val STRIP_BACK = 7
    const val STRIP_AHEAD = 20

    /**
     * The days of the date strip, oldest first. It is anchored on today, so
     * today is always on it; a date outside that window (picked from the
     * calendar) re-anchors the strip on that date.
     */
    fun strip(selected: LocalDate, today: LocalDate): List<LocalDate> {
        val outside = selected.isBefore(today.minusDays(STRIP_BACK.toLong())) || selected.isAfter(today.plusDays(STRIP_AHEAD.toLong()))
        val anchor = if (outside) selected else today
        val start = anchor.minusDays(STRIP_BACK.toLong())
        return (0..(STRIP_BACK + STRIP_AHEAD)).map { start.plusDays(it.toLong()) }
    }

    /** "Today", "Tomorrow", "Yesterday", else "Sun, 8 Nov 2026". */
    fun heading(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DAY_LONG)
    }

    fun long(d: LocalDate): String = d.format(DAY_LONG)

    /** "8 Nov", or with the year when it is not this year's. */
    fun short(d: LocalDate, today: LocalDate): String = if (d.year == today.year) d.format(DAY_SHORT) else d.format(DAY_SHORT_YEAR)

    /** An API date shown for reading ("8 Nov"); blank stays blank. */
    fun show(s: String?, today: LocalDate): String = parse(s)?.let { short(it, today) }.orEmpty()

    /** The Indian way to write a date: dd-MM-yyyy. */
    fun dmy(d: LocalDate): String = d.format(DMY)

    /** "9 November 2026". */
    fun full(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

    /** Good morning, afternoon or evening, by the hour in India. */
    fun greeting(hour: Int): String = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    /**
     * When eCourts was last checked, for a line like "eCourts checked 7:05 AM": the time, with the day
     * when it was not today. The server's stamp is IST ("2026-11-09 07:05:00" or ISO); null when unreadable.
     */
    /** "Saved copy from 3:20 PM" (today) or "Saved copy from 9 Nov, 3:20 PM": the note on a board shown from the device's copy. */
    fun savedLabel(savedAtMillis: Long, today: LocalDate, zone: java.time.ZoneId = IST): String {
        val at = java.time.Instant.ofEpochMilli(savedAtMillis).atZone(zone)
        val time = at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        return "Saved copy from " + if (at.toLocalDate() == today) time else short(at.toLocalDate(), today) + ", " + time
    }

    fun checkedLabel(stamp: String?, today: LocalDate): String? {
        val t = stamp?.trim()?.takeIf { it.length >= 16 } ?: return null
        val at = runCatching { java.time.LocalDateTime.parse(t.replace(' ', 'T').take(19)) }.getOrNull() ?: return null
        val time = at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        return if (at.toLocalDate() == today) time else short(at.toLocalDate(), today) + ", " + time
    }
}
