package com.legumlex.client.feature.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The banner's words when the server sends no demo_note (Advisor, REQ-055). */
const val DEMO_FALLBACK = "Demo. Nothing here is real."

/** REQ-055: the server marked this sign-in as the demo's (`demo: true`); the line the banner shows, or null when it is not a demo. */
object DemoSession {
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note
    val isDemo: Boolean get() = _note.value != null

    fun set(demo: Boolean, note: String?) { _note.value = demoBanner(demo, note) }
}

/** The banner line: the server's demo_note, else the fallback; null when the sign-in is not a demo. */
internal fun demoBanner(demo: Boolean, note: String?): String? = if (!demo) null else note?.trim()?.takeIf { it.isNotEmpty() } ?: DEMO_FALLBACK

/** A slim line under the header on every signed-in screen of a demo sign-in: an icon, then the words (ink on the muted fill). */
@Composable
fun DemoBanner() {
    val note by DemoSession.note.collectAsState()
    val text = note ?: return
    Row(
        Modifier.fillMaxWidth().background(Lex.colors.muted).padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Science, null, tint = Lex.colors.foreground, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground)
    }
}
