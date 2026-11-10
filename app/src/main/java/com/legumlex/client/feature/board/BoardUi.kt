package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.feature.consultations.apiError
import com.legumlex.client.feature.consultations.friendlyMessage
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate

/** True when the server could not be reached at all (no answer, not a refusal). */
fun Throwable.isOffline(): Boolean = apiError().http == null && this !is com.squareup.moshi.JsonEncodingException &&
    this !is com.squareup.moshi.JsonDataException && (this is java.io.IOException || this is java.io.InterruptedIOException)

/**
 * Runs [block] each time the screen comes back to the front (not the first showing, which loads for itself) and at the start of each
 * new day in India, so a board left open overnight, or an app opened again after a day, shows today's.
 */
@Composable
fun RefreshOnResumeAndRollover(block: () -> Unit) {
    val current = androidx.compose.runtime.rememberUpdatedState(block)
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        var first = true
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                BoardHub.rollover(BoardDates.today())
                if (first) first = false else current.value()
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(BoardDates.millisToNextDay(java.time.Instant.now()))
            BoardHub.rollover(BoardDates.today())
            current.value()
        }
    }
}

/** The server's own sentence for a refusal; a plain one when there was no answer. 402 and 403 are shown as sent. */
fun Throwable.boardMessage(): String = friendlyMessage("This could not be done just now. Please try again.")

/** The server's refusal code (insufficient_credits, premium_paused, board_off ...), if it sent one. */
fun Throwable.boardCode(): String? = apiError().code

/** The morning check's chip: green when the case is listed, grey otherwise. */
@Composable
fun checkPalette(check: MorningCheck): StatusPalette = when (check.result) {
    "listed_today", "listed_tomorrow" -> Lex.colors.success
    else -> Lex.colors.neutral
}

/** The morning check's chip for a case, as the server states it. */
@Composable
fun CaseChips(c: BoardCase) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        c.check?.let { StatusChip(it.label, checkPalette(it)) }
    }
}

/** A line under a heading: used for refusals and notes inside screens. */
@Composable
fun NoteLine(text: String, danger: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodySmall,
        color = if (danger) Lex.colors.danger.softText else Lex.colors.secondaryForeground,
        modifier = modifier.fillMaxWidth(),
    )
}
