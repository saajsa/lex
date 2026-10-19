package com.legumlex.client.feature.healthchecks.funnel

import com.legumlex.client.ahc.theme.LlColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.Inter

/**
 * Sticky banner shown on the Home screen above the AHC CTA when there's an
 * in-progress funnel. Tap → resume.
 */
@Composable
fun ResumeFunnelBanner(
    title: String,
    currentStep: AhcStep,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            // The tappable resume target — explicitly NOT wrapping the whole
            // Surface, so the X icon to the right doesn't double-fire resume.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onResume)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = PrimaryText,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Step ${currentStep.ordinal1based} of 6 · ${currentStep.title}",
                        color = SecondaryText,
                        fontFamily = Inter,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.3.sp,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "Resume",
                    color = PrimaryText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = PrimaryText,
                    modifier = Modifier.size(15.dp),
                )
            }
            // Discard target — small enough not to compete with Resume but
            // a 40dp hit target.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDiscard),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Discard draft",
                    tint = SecondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Two-button confirmation dialog for discarding an in-progress draft.
 * Triggered from the X on ResumeFunnelBanner; calls onConfirm to abandon.
 */
@Composable
fun DiscardDraftDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Discard this draft?", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                "Your uploaded document and any details you entered will be deleted. You can always start a fresh agreement check.",
                color = SecondaryText,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Discard",
                    color = LlColor.ClaretMid,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep editing", color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Surface1,
    )
}

/**
 * Emerald left-edge card shown on Home while the funnel is in the processing
 * step. A dot pulses next to the title.
 */
@Composable
fun InProgressHomeBanner(
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .drawLeftEdge(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LlColor.VerdantMid)
                    .alpha(pulse),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Health check in progress",
                    color = PrimaryText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    title,
                    color = SecondaryText,
                    fontFamily = Inter,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Open →",
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

// Thin emerald left edge on the InProgressHomeBanner.
@androidx.compose.runtime.Composable
private fun Modifier.drawLeftEdge(): Modifier {
    val edge = LlColor.VerdantMid
    return drawBehind {
    val edgePx = 3 * density
    drawRect(
        color = edge,
        topLeft = Offset(0f, 0f),
        size = Size(edgePx, size.height),
    )
    }
}
