package com.legumlex.client

import android.app.Application
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.legumlex.client.push.PushSetup
import com.razorpay.Checkout

class LegumLexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hearing reminders come from the firm, by email and push; the app's
        // own alarms never fired. Installs that created their channel lose it.
        getSystemService<NotificationManager>()?.deleteNotificationChannel("hearing_reminders")
        // Beams / FCM bootstrap — no-op if BEAMS_INSTANCE_ID is blank,
        // so dev builds without push configured still run cleanly.
        PushSetup.init(this)
        // Razorpay's sheet loads faster on first use when warmed (its docs ask for it).
        Checkout.preload(applicationContext)
    }
}
