package com.legumlex.client.feature.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Notification channel for hearing reminders. Created at app start so it's
 * ready before the first reminder fires. Default importance + sound — clients
 * have asked us repeatedly for "wake up the phone" reminders, not silent ones.
 */
const val HEARING_REMINDER_CHANNEL_ID = "hearing_reminders"
private const val HEARING_REMINDER_CHANNEL_NAME = "Hearing reminders"
private const val HEARING_REMINDER_CHANNEL_DESC =
    "Day-before and morning-of notifications for your upcoming court hearings."

fun ensureHearingReminderChannel(context: Context) {
    val mgr = context.getSystemService<NotificationManager>() ?: return
    if (mgr.getNotificationChannel(HEARING_REMINDER_CHANNEL_ID) != null) return
    val ch = NotificationChannel(
        HEARING_REMINDER_CHANNEL_ID,
        HEARING_REMINDER_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = HEARING_REMINDER_CHANNEL_DESC
        enableLights(true)
        enableVibration(true)
    }
    mgr.createNotificationChannel(ch)
}
