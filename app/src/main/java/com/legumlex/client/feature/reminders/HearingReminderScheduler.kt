package com.legumlex.client.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.legumlex.client.feature.hearings.Hearing
import com.legumlex.client.feature.hearings.HearingStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules and cancels local notifications for upcoming hearings. Two
 * reminders per hearing:
 *
 *   • "Evening before"  — 18:00 the day before the hearing date.
 *   • "Two hours before" — 2 hours before the hearing's start time. Skipped
 *     if no time is recorded, or if 2h-before would fall before 6 AM.
 *
 * Reminders for past hearings are cancelled. Hearings whose status puts them
 * out of scope (disposed, cancelled, etc.) are skipped — the staff app's
 * hearing-status conventions are mirrored here.
 *
 * Scheduling is idempotent: each (hearing, kind) pair has a stable
 * notification id, so re-syncing replaces the existing alarm in place.
 */
object HearingReminderScheduler {

    /** Re-sync the full reminder set against [hearings]. Call after a list fetch. */
    fun sync(context: Context, hearings: List<Hearing>) {
        val now    = LocalDateTime.now()
        val zone   = ZoneId.systemDefault()
        val alarms = context.getSystemService<AlarmManager>() ?: return

        for (h in hearings) {
            // Cancel both kinds first — handles the case where a hearing was
            // rescheduled (date/time changed) or moved to a terminal status.
            cancelKind(context, alarms, h.id, Kind.EVENING_BEFORE)
            cancelKind(context, alarms, h.id, Kind.TWO_HOURS_BEFORE)

            val date = h.date ?: continue
            if (!h.status.isUpcoming()) continue
            if (date.isBefore(LocalDate.now())) continue

            val title = "Hearing ${formatDayLabel(date)}"
            val whenLabel = if (h.time != null) " at ${formatTimeLabel(h.time)}" else ""

            // Evening-before reminder — 18:00 the previous day.
            val eveningBefore = date.minusDays(1).atTime(18, 0)
            if (eveningBefore.isAfter(now)) {
                schedule(
                    context, alarms,
                    hearingId = h.id,
                    kind      = Kind.EVENING_BEFORE,
                    triggerAtMillis = eveningBefore.atZone(zone).toInstant().toEpochMilli(),
                    title     = title + whenLabel,
                    body      = bodyFor(h, leadingPhrase = "Tomorrow"),
                )
            }

            // Two-hours-before reminder — only when we know the time.
            if (h.time != null) {
                val twoBefore = date.atTime(h.time).minusHours(2)
                if (twoBefore.isAfter(now) && twoBefore.toLocalTime().isAfter(LocalTime.of(6, 0))) {
                    schedule(
                        context, alarms,
                        hearingId = h.id,
                        kind      = Kind.TWO_HOURS_BEFORE,
                        triggerAtMillis = twoBefore.atZone(zone).toInstant().toEpochMilli(),
                        title     = "Hearing in 2 hours" + whenLabel,
                        body      = bodyFor(h, leadingPhrase = "Starts at ${formatTimeLabel(h.time)}"),
                    )
                }
            }
        }
    }

    /** Cancel both reminders for a single hearing — e.g. after a disposal event. */
    fun cancelForHearing(context: Context, hearingId: String) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        cancelKind(context, alarms, hearingId, Kind.EVENING_BEFORE)
        cancelKind(context, alarms, hearingId, Kind.TWO_HOURS_BEFORE)
    }

    // -------------------------------------------------------------------------

    private fun schedule(
        context: Context,
        alarms: AlarmManager,
        hearingId: String,
        kind: Kind,
        triggerAtMillis: Long,
        title: String,
        body: String,
    ) {
        val notifId = notificationId(hearingId, kind)
        // FLAG_UPDATE_CURRENT guarantees PendingIntent.getBroadcast returns
        // non-null on every supported API level; the nullable return type on
        // pendingIntent() is only relevant for the FLAG_NO_CREATE cancel path.
        val pi = pendingIntent(
            context, hearingId, kind, notifId,
            title, body,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        // Try exact + idle wakeup first; if the OS refused exact (API 31+ may
        // deny without USE_EXACT_ALARM), fall back to an inexact alarm. An
        // inexact morning reminder is still useful — Android will fire it
        // within ~10 minutes for foreground apps, longer in Doze.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun cancelKind(context: Context, alarms: AlarmManager, hearingId: String, kind: Kind) {
        val notifId = notificationId(hearingId, kind)
        val pi = pendingIntent(
            context, hearingId, kind, notifId,
            title = "", body = "",
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(
        context: Context,
        hearingId: String,
        kind: Kind,
        notifId: Int,
        title: String,
        body: String,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, HearingReminderReceiver::class.java).apply {
            // Action makes each PI unique against filterEquals — without it,
            // two PIs with the same component would collide regardless of extras.
            action = "reminder:$hearingId:${kind.name}"
            putExtra(HearingReminderReceiver.EXTRA_HEARING_ID, hearingId)
            putExtra(HearingReminderReceiver.EXTRA_TITLE, title)
            putExtra(HearingReminderReceiver.EXTRA_BODY, body)
            putExtra(HearingReminderReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        // requestCode must be stable per (hearing, kind) so re-scheduling
        // replaces the existing PI rather than stacking.
        return PendingIntent.getBroadcast(context, notifId, intent, flags)
    }

    /** Stable 31-bit notification id derived from (hearing id, reminder kind). */
    private fun notificationId(hearingId: String, kind: Kind): Int {
        val seed = (hearingId.hashCode() * 31 + kind.ordinal).and(0x7fffffff)
        // Guarantee non-zero (PendingIntent.getBroadcast(_, 0, ...) is fine,
        // but we treat 0 as "missing" in the receiver, so shift to 1.
        return if (seed == 0) 1 else seed
    }

    private fun bodyFor(h: Hearing, leadingPhrase: String): String {
        val pieces = buildList {
            add(leadingPhrase)
            h.caseTitle.takeIf { it.isNotBlank() }?.let { add(" · $it") }
            h.courtName?.takeIf { it.isNotBlank() }?.let { add(" · $it") }
        }
        return pieces.joinToString("")
    }

    private fun formatDayLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))

    private fun formatTimeLabel(time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern("h:mm a"))

    /** Statuses that are "still on the calendar" — terminal states are skipped. */
    private fun HearingStatus.isUpcoming(): Boolean = when (this) {
        HearingStatus.SCHEDULED, HearingStatus.IN_PROGRESS, HearingStatus.UNKNOWN -> true
        HearingStatus.ADJOURNED, HearingStatus.POSTPONED,
        HearingStatus.COMPLETED, HearingStatus.CANCELLED,
        HearingStatus.NO_APPEARANCE                                                -> false
    }

    private enum class Kind { EVENING_BEFORE, TWO_HOURS_BEFORE }
}
