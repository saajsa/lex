package com.legumlex.client

import android.app.Application
import com.legumlex.client.feature.reminders.ensureHearingReminderChannel
import com.legumlex.client.push.PushSetup

class LegumLexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Notification channels must exist before the first notify() call.
        // Creating them here keeps the channel id available to broadcast
        // receivers that may fire before any Activity is open.
        ensureHearingReminderChannel(this)
        // Beams / FCM bootstrap — no-op if BEAMS_INSTANCE_ID is blank,
        // so dev builds without push configured still run cleanly.
        PushSetup.init(this)
    }
}
