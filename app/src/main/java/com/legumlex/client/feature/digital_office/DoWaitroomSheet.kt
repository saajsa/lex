package com.legumlex.client.feature.digital_office

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Bottom sheet shown when the user taps the home waitroom banner. Two states:
 *   PROMPTED  primary action = Start session (routes into the existing
 *             DoIntake flow which calls /sessions then /pay then /intake).
 *             Secondary = Leave queue (forfeit the 5-min window).
 *   WAITING   primary action = Leave queue. No "skip ahead" — the cabin is
 *             still occupied; the client just waits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoWaitroomSheet(
    row: DoWaitroomRow,
    onStartSession: (advocateStaffId: Int) -> Unit,
    onLeave: (waitroomId: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                row.advocateName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.25).sp,
            )
            if (row.advocateDesignation.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    row.advocateDesignation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(20.dp))

            when (row.status) {
                DoWaitroomStatus.PROMPTED -> PromptedBody(row)
                else                       -> WaitingBody(row)
            }

            Spacer(Modifier.height(20.dp))

            if (row.status == DoWaitroomStatus.PROMPTED) {
                Button(
                    onClick = { onStartSession(row.advocateStaffId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF065F46),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pay & start intake", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onLeave(row.waitroomId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Forfeit slot & leave queue")
                }
            } else {
                Button(
                    onClick = { onLeave(row.waitroomId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Leave queue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PromptedBody(row: DoWaitroomRow) {
    // Same 1-Hz tick as the banner — the sheet may be open longer than 5 s
    // and the countdown must keep ticking visibly so the user feels the
    // urgency.
    var tick by remember(row.waitroomId) { mutableStateOf(0) }
    LaunchedEffect(row.waitroomId) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val secondsLeft = ((row.promptSecondsLeft ?: 0) - tick).coerceAtLeast(0)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECFDF5))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                "IT'S YOUR TURN",
                color = Color(0xFF065F46),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                mmss(secondsLeft) + " left to start intake",
                color = Color(0xFF047857),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "If the window expires the next person in line moves up. You can rejoin the queue, but you will lose your place.",
                color = Color(0xFF065F46),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun WaitingBody(row: DoWaitroomRow) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "POSITION",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "#${row.position}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "ETA ~${if (row.etaMinutes <= 1) "a minute" else "${row.etaMinutes} min"}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "We'll send you a push the moment the advocate is ready. You can close the app — you'll be notified.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

private fun mmss(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "$m:${if (r < 10) "0$r" else "$r"}"
}
