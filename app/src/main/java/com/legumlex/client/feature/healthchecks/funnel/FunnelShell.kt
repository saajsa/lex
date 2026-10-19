package com.legumlex.client.feature.healthchecks.funnel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.SourceSans

/**
 * 6-dot progress ribbon. Past = small filled, current = larger filled,
 * future = hollow grey. Purely decorative — the active step is driven by
 * the server-persisted current_step.
 */
@Composable
fun FunnelProgress(
    step: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        (1..6).forEach { i ->
            val isCurrent = i == step
            val isPast    = i < step
            val sz by animateDpAsState(
                targetValue = if (isCurrent) 10.dp else 7.dp,
                label = "dot-$i",
            )
            Box(
                modifier = Modifier
                    .size(sz)
                    .clip(CircleShape)
                    .then(
                        if (isCurrent || isPast) Modifier.background(PrimaryText)
                        else Modifier.border(1.5.dp, BorderStrong, CircleShape)
                    ),
            )
        }
    }
}

/** Eyebrow + H1 question + optional sub. Used inside every step's scrollable body. */
@Composable
fun FunnelStepHeader(
    step: AhcStep,
    question: String,
    sub: String? = null,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)) {
        Text(
            text = "Step ${step.ordinal1based} of 6 · ${step.title}",
            color = SecondaryText,
            fontFamily = SourceSans,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = question,
            color = PrimaryText,
            fontFamily = SourceSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.5).sp,
            lineHeight = 26.sp,
        )
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = sub,
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Funnel screen wrapper — sticky top bar (back · centred 6-dot progress with
 * "Step N of 6" label · Save & exit), scrollable body, and an optional sticky
 * action bar at the bottom.
 */
@Composable
fun FunnelScreen(
    step: AhcStep,
    onBack: () -> Unit,
    onSaveAndExit: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The app-wide header supplies the brand mark + back arrow; gesture/system
    // back exits the funnel to match it.
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface1),
    ) {
        // ── Step ribbon ────────────────────────────────────────────
        // (Back lives in the app-wide header now; this sub-bar keeps the
        //  wizard-specific progress ribbon + Save & exit affordance.)
        Surface(color = Surface1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    FunnelProgress(step = step.ordinal1based)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Step ${step.ordinal1based} of 6",
                        color = SecondaryText,
                        fontFamily = SourceSans,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Save & exit",
                    color = SecondaryText,
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onSaveAndExit)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        HorizontalDivider(color = BorderColor)

        // ── Scrollable body ────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScrollIfPossible(),
            content = content,
        )

        // ── Sticky action bar ──────────────────────────────────────
        if (action != null) {
            HorizontalDivider(color = BorderColor)
            Surface(color = Surface1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    action()
                }
            }
        }
    }
}

// Most step bodies don't need scroll, but Intake does — let the body decide.
// This is a tiny shim so the wrapper doesn't impose a scroll modifier blindly.
@Composable
private fun Modifier.verticalScrollIfPossible(): Modifier = this
