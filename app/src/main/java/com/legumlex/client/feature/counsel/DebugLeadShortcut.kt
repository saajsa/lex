package com.legumlex.client.feature.counsel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.auth.SessionRole

/**
 * DEBUG BUILDS ONLY. Walks the counsel registration with a lead session already stored on the phone (a dev lead seeded for
 * testing), asking the server's triage with that token instead of going through Google. It draws nothing in a release build
 * (BuildConfig.DEBUG is false there) and only when the stored session is a lead's. DebugLeadShortcutTest keeps it that way.
 */
@Composable
fun DebugLeadShortcut(enabled: Boolean, onUse: () -> Unit) {
    if (!BuildConfig.DEBUG) return
    if (!SessionRole.isLead) return
    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
        Text("Debug · Use Stored Lead Session", color = Color(0xFF8A877F), fontSize = 12.sp,
            modifier = Modifier.clickable(enabled = enabled, onClick = onUse))
    }
}
