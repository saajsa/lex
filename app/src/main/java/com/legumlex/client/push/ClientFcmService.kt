package com.legumlex.client.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import com.legumlex.client.MainActivity
import com.legumlex.client.R
import com.pusher.pushnotifications.fcm.MessagingService

/**
 * Beams hands us the FCM payload via [MessagingService.onMessageReceived].
 * Beams doesn't display anything itself; FCM's auto-display is foreground-
 * skipping and OEM-flaky, so we post the notification ourselves to make
 * behavior identical in foreground, background, and killed state.
 *
 * Data payload routing convention (mirrors server-side push_helper):
 *   route=consultation, consultation_id=42, kind=reply | doc_request | ...
 *   route=invoice,      invoice_id=17
 *   route=hearing,      hearing_id=99
 *   route=project,      project_id=12   (a stage moved, concluded, or needs the client)
 */
class ClientFcmService : MessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notif = remoteMessage.notification
        val data  = remoteMessage.data
        val title = notif?.title ?: data["title"] ?: "Legum Lex"
        val body  = notif?.body  ?: data["body"]  ?: ""

        Log.d(TAG, "show: $title | $body data=$data")

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Forward the data payload so the Activity / NavGraph can route.
            // The route key tells the activity which detail to deep-link to;
            // see the deep-link uri builder below.
            data.forEach { (k, v) -> putExtra(k, v) }
            // Deep-link URI lets Compose Navigation's handleDeepLink pick the
            // right destination without us having to special-case routing in
            // MainActivity. Fall back to no data if we don't recognise route.
            buildDeepLinkUri(data)?.let { setData(it) }
        }
        val pi = PendingIntent.getActivity(
            applicationContext, notificationId(remoteMessage), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(applicationContext, PushSetup.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        // AHC-funnel-specific embellishments. The server's push_helper sends
        // route=ahc with kind=progress|ready|advocate_question for the three
        // funnel notification variants in the design.
        if (data["route"] == "ahc") {
            when (data["kind"]) {
                "progress" -> {
                    val pct = data["progress"]?.toIntOrNull() ?: 0
                    builder.setProgress(100, pct, /* indeterminate = */ pct == 0)
                    builder.setOngoing(true)
                    builder.setAutoCancel(false)
                }
                "ready" -> {
                    builder.addAction(0, "View report", pi)
                }
                "advocate_question" -> {
                    builder.addAction(0, "Reply", pi)
                    builder.addAction(0, "View", pi)
                }
            }
        }

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId(remoteMessage), builder.build())
    }

    /**
     * Build a `legumlex://...` URI from the data payload so the Compose
     * NavController can deep-link straight to the right detail screen via
     * the existing navDeepLink registrations.
     */
    private fun buildDeepLinkUri(data: Map<String, String>): Uri? {
        return when (data["route"]) {
            "consultation" -> data["consultation_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse("legumlex://consultation/$it") }
            "invoice" -> data["invoice_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse("legumlex://invoice/$it") }
            "hearing" -> data["hearing_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse("legumlex://hearing/$it") }
            "project" -> data["project_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse("legumlex://project/$it") }
            else -> null
        }
    }

    /**
     * Stable id per (route, target) so duplicate pushes about the same thing
     * (e.g. multiple replies in a thread before the user taps) collapse into
     * one notification instead of stacking. Falls back to a unique id when
     * route/target isn't present.
     */
    private fun notificationId(m: RemoteMessage): Int {
        val key = (m.data["route"] ?: "?") + ":" + (
            m.data["consultation_id"]
                ?: m.data["invoice_id"]
                ?: m.data["hearing_id"]
                ?: m.data["project_id"]
                ?: System.currentTimeMillis().toString()
        )
        return key.hashCode()
    }

    private companion object { const val TAG = "ClientFcm" }
}
