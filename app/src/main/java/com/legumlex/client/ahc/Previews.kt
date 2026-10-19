package com.legumlex.client.ahc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.legumlex.client.ahc.model.AhcActions

/* ============================================================================
 *  @Preview gallery — every required state at device width (Pixel-class,
 *  light theme). Open in Android Studio's split/preview pane to review.
 *  Each preview pins width=400dp (≈ a phone screen) with the paper background.
 * ========================================================================== */

private const val W = 400
private val noop = AhcActions()

@Composable
private fun Frame(state: com.legumlex.client.ahc.model.AhcUiState) =
    AhcDetailScreen(state, noop, Modifier.fillMaxSize())

/* ---- STATE 1 · Pending --------------------------------------------------- */
@Preview(name = "01a · Pending — fresh (29:54)", widthDp = W, heightDp = 880, showBackground = true)
@Composable fun PreviewPendingFresh() = Frame(AhcSamples.pendingFresh)

@Preview(name = "01b · Pending — halfway (14:22)", widthDp = W, heightDp = 880, showBackground = true)
@Composable fun PreviewPendingHalfway() = Frame(AhcSamples.pendingHalfway)

@Preview(name = "01c · Overdue — running late", widthDp = W, heightDp = 760, showBackground = true)
@Composable fun PreviewOverdue() = Frame(AhcSamples.overdue)

/* ---- STATE 2 · Released -------------------------------------------------- */
@Preview(name = "02a · Released — LOW (as-is)", widthDp = W, heightDp = 1500, showBackground = true)
@Composable fun PreviewReleasedLow() = Frame(AhcSamples.releasedLow)

@Preview(name = "02b · Released — MODERATE (reference)", widthDp = W, heightDp = 1900, showBackground = true)
@Composable fun PreviewReleasedModerate() = Frame(AhcSamples.releasedModerate)

@Preview(name = "02c · Released — HIGH (soft nudge)", widthDp = W, heightDp = 1900, showBackground = true)
@Composable fun PreviewReleasedHigh() = Frame(AhcSamples.releasedHigh)

@Preview(name = "02d · Released — partial failure", widthDp = W, heightDp = 1900, showBackground = true)
@Composable fun PreviewReleasedPartial() = Frame(AhcSamples.releasedPartialFailure)

/* ---- STATE 3 · Escalated ------------------------------------------------- */
@Preview(name = "03a · Escalated — awaiting booking", widthDp = W, heightDp = 980, showBackground = true)
@Composable fun PreviewEscalatedAwaiting() = Frame(AhcSamples.escalatedAwaiting)

@Preview(name = "03b · Escalated — consultation booked", widthDp = W, heightDp = 1040, showBackground = true)
@Composable fun PreviewEscalatedBooked() = Frame(AhcSamples.escalatedBooked)

/* ---- STATE 4 · Loading / Empty / Error ----------------------------------- */
@Preview(name = "04a · Loading", widthDp = W, heightDp = 700, showBackground = true)
@Composable fun PreviewLoading() = Frame(AhcSamples.loading)

@Preview(name = "04b · Empty", widthDp = W, heightDp = 600, showBackground = true)
@Composable fun PreviewEmpty() = Frame(AhcSamples.empty)

@Preview(name = "04c · Error", widthDp = W, heightDp = 700, showBackground = true)
@Composable fun PreviewError() = Frame(AhcSamples.error)

/* ---- STATE 5 · Closed (terminal) ----------------------------------------- */
@Preview(name = "05a · Closed — completed", widthDp = W, heightDp = 1200, showBackground = true)
@Composable fun PreviewClosedCompleted() = Frame(AhcSamples.closedCompleted)

@Preview(name = "05b · Closed — resolved via consultation", widthDp = W, heightDp = 1100, showBackground = true)
@Composable fun PreviewClosedResolved() = Frame(AhcSamples.closedResolved)

@Preview(name = "05c · Closed — cancelled", widthDp = W, heightDp = 1000, showBackground = true)
@Composable fun PreviewClosedCancelled() = Frame(AhcSamples.closedCancelled)
