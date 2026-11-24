package com.legumlex.client.feature.home

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.Lex

// REQ-048 (owner, 24 Nov 2026): the Digital Office button on the day row: live (green, a gentle pulse) when an advocate is online, a dead icon when no one is.

/** Who is in the Digital Office, from the home answer (dashboard/chamber online_count). Unknown: the answer did not come. */
enum class OfficePresence { Live, Dead, Unknown }

fun officePresence(chamber: ChamberState?): OfficePresence = when {
    chamber == null -> OfficePresence.Unknown
    chamber.onlineCount > 0 -> OfficePresence.Live
    else -> OfficePresence.Dead
}

/** How the button looks: live is a filled green circle with a white icon, with a pulse ring unless motion is reduced; a dead icon is muted and opens nothing. */
data class OfficeLook(val green: Boolean, val pulse: Boolean, val opens: Boolean, val description: String)

const val OFFICE_DEAD_LINE = "No advocate is in the Digital Office just now."

fun officeLook(p: OfficePresence, reducedMotion: Boolean): OfficeLook = when (p) {
    OfficePresence.Live -> OfficeLook(green = true, pulse = !reducedMotion, opens = true, description = "Digital Office, an advocate is in")
    OfficePresence.Dead -> OfficeLook(green = false, pulse = false, opens = false, description = "Digital Office, no advocate is in")
    // Not known: no claim either way; it opens the office as before.
    OfficePresence.Unknown -> OfficeLook(green = false, pulse = false, opens = true, description = "Digital Office")
}

@Composable
fun OfficeButton(presence: OfficePresence, onOpen: () -> Unit) {
    val reduced = com.legumlex.client.feature.board.rememberReducedMotion()
    val look = officeLook(presence, reduced)
    val context = LocalContext.current
    val green = Lex.colors.success.solid
    val tint = when {
        look.green -> androidx.compose.ui.graphics.Color.White   // white on the filled green
        presence == OfficePresence.Dead -> Lex.colors.mutedForeground.copy(alpha = 0.45f)
        else -> Lex.colors.secondaryForeground
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(role = Role.Button) { if (look.opens) onOpen() else Toast.makeText(context, OFFICE_DEAD_LINE, Toast.LENGTH_SHORT).show() }
            .semantics { contentDescription = look.description },
        contentAlignment = Alignment.Center,
    ) {
        if (look.pulse) {
            val t = rememberInfiniteTransition(label = "office")
            val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
            // A soft ring that grows and fades.
            Box(Modifier.size(36.dp).graphicsLayer { scaleX = 1f + 0.35f * k; scaleY = 1f + 0.35f * k; alpha = 0.45f * (1f - k) }.background(green, CircleShape))
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .then(if (look.green) Modifier.background(green) else Modifier.border(1.dp, Lex.colors.border, CircleShape).background(Lex.colors.background)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Videocam, null, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}
