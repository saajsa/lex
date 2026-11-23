package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// REQ-038 038.4: the cause list for a range of days, as the web's Days Ahead (board/week): 7 or 14 days, Earlier and Later, one PDF.

data class RangeDay(val date: String, val items: List<BoardCase>)
data class BoardRange(val from: String, val to: String, val maxDays: Int, val days: List<RangeDay>)

internal fun parseRange(d: Map<String, Any?>) = BoardRange(
    from = d["from"].str(), to = d["to"].str(), maxDays = d["max_days"].intOrNull() ?: 31,
    days = d["days"].list().map { RangeDay(it["date"].str(), it["items"].list().map(::parseCase)) },
)

/** The last day of a span from [from] (7 or 14 days, the first included). */
internal fun rangeTo(from: LocalDate, span: Int): LocalDate = from.plusDays(span - 1L)

/** "9 Nov to 15 Nov 2026", the web's range line. */
internal fun rangeLine(from: LocalDate, to: LocalDate): String =
    from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " to " + to.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

/** The line over the days, as the web words it. */
internal fun rangeSummary(r: BoardRange): String {
    val n = r.days.sumOf { it.items.size }
    return if (n == 0) "Nothing is listed in these days." else "${creditsText(n)} ${if (n == 1) "listing" else "listings"} in these days. The PDF leaves out the days with none."
}

/** A day's heading: "Today, Monday, 23 November 2026". */
internal fun rangeDayTitle(date: String, today: LocalDate): String {
    val d = BoardDates.parse(date) ?: return date
    return (if (d == today) "Today, " else "") + d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
}

@Composable
fun BoardRangeScreen(from: String, onOpenCase: (Int) -> Unit, onOpenDay: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    val today = BoardDates.today()
    var start by rememberSaveable { mutableStateOf(BoardDates.parse(from)?.let { BoardDates.api(it) } ?: BoardDates.api(today)) }
    var span by rememberSaveable { mutableStateOf(7) }
    var range by remember { mutableStateOf<BoardRange?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pdfBusy by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    val first = BoardDates.parse(start) ?: today
    val last = rangeTo(first, span)

    LaunchedEffect(start, span, tick) {
        range = null; error = null
        repo.range(BoardDates.api(first), BoardDates.api(last)).onSuccess { range = it }.onFailure { error = it.boardMessage() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(eyebrow = "Cause List", title = "Days Ahead") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ start = BoardDates.api(first.minusDays(span.toLong())) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Earlier", tint = Lex.colors.foreground) }
                Text(rangeLine(first, last), style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                IconButton({ start = BoardDates.api(first.plusDays(span.toLong())) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Later", tint = Lex.colors.foreground) }
            }
        }
        item { SegmentedToggle(listOf(7 to "7 Days", 14 to "14 Days"), span, { span = it }) }
        item {
            LegumSecondaryButton(if (pdfBusy) "Making The PDF…" else "Download PDF", {
                pdfBusy = true; pdfError = null
                scope.launch {
                    // The app keeps no downloads folder: the PDF goes to the share sheet, where Save, Print or WhatsApp take it.
                    repo.rangeSheet(BoardDates.api(first), BoardDates.api(last))
                        .onSuccess { shareSheet(context, it, rangeLine(first, last)) }
                        .onFailure { pdfError = it.boardMessage() }
                    pdfBusy = false
                }
            }, enabled = !pdfBusy && range != null)
        }
        pdfError?.let { item { NoteLine(it, danger = true) } }
        val r = range
        when {
            error != null -> item { LexCard { LexEmpty("The Days Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { tick++ }) }) } }
            r == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) } }
            else -> {
                item { NoteLine(rangeSummary(r)) }
                // Each day's heading, then its cases as the same soft tiles as Cases.
                items(r.days, key = { it.date }) { d ->
                    androidx.compose.foundation.layout.Column {
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(rangeDayTitle(d.date, today), style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                            if (d.items.isEmpty()) Text("Nothing listed", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, modifier = Modifier.padding(vertical = 12.dp))
                            else TextButton({ onOpenDay(d.date) }) { Text("Open This Day's List") }
                        }
                        d.items.forEach { c -> CaseRow(c, today) { onOpenCase(c.id) } }
                    }
                }
            }
        }
    }
}
