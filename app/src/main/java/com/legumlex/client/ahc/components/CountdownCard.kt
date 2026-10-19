package com.legumlex.client.ahc.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlText

/* ============================================================================
 *  CountdownCard — the visual anchor of the PENDING state.
 *  A ring depletes 30→0 over the SLA window. The promise is "Lex review",
 *  never "AI". On overdue it flips to a calm amber "Running late" — no alarm.
 * ========================================================================== */

@Composable
fun CountdownCard(
    ringFraction: Float,       // 1f full → 0f empty
    timeLabel: String,         // "29:54"
    submittedAtLabel: String,
    etaLabel: String,
    isOverdue: Boolean,
    modifier: Modifier = Modifier,
) {
    LlCard(modifier) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
            if (isOverdue) OverdueBody(submittedAtLabel, etaLabel)
            else CountingBody(ringFraction, timeLabel, submittedAtLabel, etaLabel)
        }
    }
}

@Composable
private fun CountingBody(
    ringFraction: Float, timeLabel: String, submittedAtLabel: String, etaLabel: String,
) {
    val animated by animateFloatAsState(targetValue = ringFraction, label = "ring")
    // Ring color: verdant while comfortably on time, amber in the last ~5 min.
    val ringColor = if (ringFraction <= 0.16f) LlColor.AmberMid else LlColor.Verdant

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("REPORT READY IN", style = LlText.eyebrow.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(LlDimen.RingSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(LlDimen.RingSize)) {
                val stroke = LlDimen.RingThickness.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                // track (--paper2)
                drawArc(
                    color = LlColor.Paper2, startAngle = -90f, sweepAngle = 360f,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
                // depleting progress arc (ring color by remaining)
                drawArc(
                    color = ringColor, startAngle = -90f, sweepAngle = 360f * animated,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timeLabel, style = LlText.countdownNum)             // .cd-num
                Spacer(Modifier.height(7.dp))
                Text("REMAINING", style = LlText.mono.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
            }
        }
        Spacer(Modifier.height(17.dp))
        Text(
            "Your advocate releases reviews within 30 minutes of submission.",
            style = LlText.body, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 264.dp),
        )
        Spacer(Modifier.height(11.dp))
        Text("Submitted $submittedAtLabel · ETA $etaLabel", style = LlText.mono, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OverdueBody(submittedAtLabel: String, etaLabel: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // amber-soft disc + amber glyph — calm, not alarming
        Box(
            Modifier.size(66.dp).clip(RoundedCornerShape(50)).background(LlColor.AmberSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.HourglassTop, null, Modifier.size(33.dp), tint = LlColor.Amber) }
        Spacer(Modifier.height(15.dp))
        Text("Running late", style = LlText.emptyTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "We're taking a little longer than usual on this one. We'll notify you the moment your report is ready.",
            style = LlText.body, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 264.dp),
        )
        Spacer(Modifier.height(13.dp))
        Text("Submitted $submittedAtLabel · ETA passed $etaLabel", style = LlText.mono, textAlign = TextAlign.Center)
    }
}
