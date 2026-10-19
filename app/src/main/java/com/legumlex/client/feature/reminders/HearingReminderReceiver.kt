package com.legumlex.client.feature.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.legumlex.client.MainActivity
import com.legumlex.client.R

/**
 * Receives scheduled hearing-reminder broadcasts and posts a system
 * notification that deep-links back to the hearing detail.
 *
 * Stays passive: never schedules anything itself, never hits the network.
 * The scheduler owns the lifecycle. Notifications drop silently if the user
 * has revoked POST_NOTIFICATIONS — we don't want a runtime prompt here.
 */
class HearingReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hearingId = intent.getStringExtra(EXTRA_HEARING_ID).orEmpty()
        val title     = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body      = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val notifId   = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (hearingId.isBlank() || notifId == 0) return

        // Build the deep-link PendingIntent first. We route via MainActivity
        // so the launcher animation still plays and the splash hand-off runs.
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("legumlex://hearing/$hearingId")
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notifId, deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // POST_NOTIFICATIONS on API 33+ — if it's revoked we silently skip;
        // hearing reminders aren't worth blocking on a permission prompt.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notif = NotificationCompat.Builder(context, HEARING_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title.ifBlank { "Upcoming hearing" })
            .setContentText(body.ifBlank { "Tap to view hearing details." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notif)
    }

    companion object {
        const val EXTRA_HEARING_ID     = "hearing_id"
        const val EXTRA_TITLE          = "title"
        const val EXTRA_BODY           = "body"
        const val EXTRA_NOTIFICATION_ID = "notif_id"
    }
}
