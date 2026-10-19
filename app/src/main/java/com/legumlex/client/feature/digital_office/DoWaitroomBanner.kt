package com.legumlex.client.feature.digital_office

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Home-screen waitroom banner. Two distinct shapes:
 *   PROMPTED  loud emerald card with a live mm:ss countdown — tap to act
 *   WAITING   neutral surface card showing position + ETA — tap to manage
 *
 * Auto-hides when [rows] is empty. The countdown ticks locally between the
 * VM's 15 s poll so the user sees a smooth count without burning the wire.
 */
@Composable
fun DoWaitroomBanner(
    rows: List<DoWaitroomRow>,
    onTap: (DoWaitroomRow) -> Unit,
) {
    if (rows.isEmpty()) return
    // Prompted row wins — there is at most one per client at any time.
    val row = rows.firstOrNull { it.status == DoWaitroomStatus.PROMPTED } ?: rows.first()

    when (row.status) {
        DoWaitroomStatus.PROMPTED -> PromptedBanner(row, onTap)
        else                       -> WaitingBanner(row, onTap)
    }
}

@Composable
private fun PromptedBanner(row: DoWaitroomRow, onTap: (DoWaitroomRow) -> Unit) {
    // Local 1-Hz tick — drives mm:ss without forcing a VM reload every second.
    // Server is source of truth; the VM's 15s poll keeps `promptSecondsLeft`
    // honest. When this counter hits zero the VM's next poll will reflect
    // either status=expired or a promotion of the next client.
    var tick by remember(row.waitroomId) { mutableStateOf(0) }
    LaunchedEffect(row.waitroomId) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val secondsLeft = ((row.promptSecondsLeft ?: 0) - tick).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF065F46))  // emerald-800 — high contrast
            .clickable { onTap(row) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "YOUR TURN",
                color = Color(0xFFA7F3D0),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                row.advocateName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.15).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Send your request — ${mmss(secondsLeft)} left",
                color = Color(0xFFA7F3D0),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun WaitingBanner(row: DoWaitroomRow, onTap: (DoWaitroomRow) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onTap(row) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFDBEAFE)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Color(0xFF1E40AF),
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "IN QUEUE · #${row.position}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                row.advocateName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.15).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "ETA ~${if (row.etaMinutes <= 1) "a minute" else "${row.etaMinutes} min"} · tap to manage",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun mmss(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "$m:${if (r < 10) "0$r" else "$r"}"
}
