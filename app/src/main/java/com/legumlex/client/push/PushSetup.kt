package com.legumlex.client.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.legumlex.client.BuildConfig
import com.pusher.pushnotifications.PushNotifications

/**
 * Pusher Beams bootstrap for the client app.
 *
 * Beams uses FCM under the hood — make sure google-services.json is in app/.
 * Each contact subscribes to interest `contact-{id}`; the server fires
 * notifications via `do_push_to_client($contactId, …)` in the customers_api
 * module's push_helper.php.
 *
 * Beams instance ID is read from BuildConfig (set in local.properties as
 * BEAMS_INSTANCE_ID). If blank, every call here is a no-op so dev builds
 * without Beams configured still run cleanly.
 */
object PushSetup {
    private const val TAG = "ClientPush"
    const val DEFAULT_CHANNEL_ID = "legum_lex_default"

    private var started = false

    /** Call once from Application.onCreate. Safe to call repeatedly. */
    fun init(context: Context) {
        ensureChannel(context)
        if (started) return
        val id = BuildConfig.BEAMS_INSTANCE_ID
        if (id.isBlank()) {
            Log.w(TAG, "BEAMS_INSTANCE_ID not set — skipping Pusher Beams init")
            return
        }
        runCatching {
            PushNotifications.start(context.applicationContext, id)
            started = true
        }.onFailure { Log.e(TAG, "Beams init failed", it) }
    }

    /** Subscribe this device to the logged-in contact's interest. */
    fun registerContact(contactId: Int) {
        if (!started || contactId < 1) return
        runCatching {
            PushNotifications.addDeviceInterest(interestFor(contactId))
        }.onFailure { Log.e(TAG, "addDeviceInterest failed", it) }
    }

    /** Clear all interests on logout so the next account doesn't inherit them. */
    fun clear() {
        if (!started) return
        runCatching {
            PushNotifications.clearDeviceInterests()
        }.onFailure { Log.e(TAG, "clearDeviceInterests failed", it) }
    }

    /**
     * Notification channels must exist before any notify() call. We create
     * here (not in the FCM service) so cold-boot deliveries that wake the
     * service before any Activity has run still post to the right channel.
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(DEFAULT_CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Replies, document requests, and other updates from your firm."
            }
        )
    }

    private fun interestFor(contactId: Int) = "contact-$contactId"
}
