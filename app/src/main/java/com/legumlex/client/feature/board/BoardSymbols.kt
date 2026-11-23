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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate

// REQ-038, owner 23 Nov 2026: the counsel workspace's own symbols ("ticks"), each small, the same everywhere, named for screen
// readers and explained in one legend (the ⓘ on Cases and on a case). Colour only for green (good) and red (needs you).

/** A symbol's tone: grey, green or red (the app's only colours). */
enum class SymTone { Grey, Green, Red, Ink, Pale }

/** Every symbol the counsel workspace shows. [name] is what a screen reader says and the legend's line explains it. */
enum class Sym(val label: String, val line: String, val tone: SymTone) {
    Checked("Checked", "The next date was checked with eCourts, by a check or a read.", SymTone.Grey),
    CheckedToday("Confirmed Today", "This morning's eCourts check confirmed the date.", SymTone.Green),
    Moved("Date Moved", "eCourts moved the next date today or yesterday.", SymTone.Red),
    NeedsEye("Needs Your Eye", "eCourts shows a different date: choose which is right.", SymTone.Red),
    Undated("No Date", "The case has no next date yet.", SymTone.Grey),
    EdgeRed("Red Edge", "Needs your eye, or the date was moved.", SymTone.Red),
    EdgeInk("Black Edge", "Disposed.", SymTone.Ink),
    EdgeGreen("Green Edge", "Watched daily: checked with eCourts every morning.", SymTone.Green),
    EdgeGrey("Grey Edge", "Not watched.", SymTone.Pale),
    OrderKept("Order Kept", "Kept on the platform: tap to open, free.", SymTone.Green),
    OrderAvailable("Order Not Fetched", "Tap to get it.", SymTone.Grey),
    OrderSealed("Signed Order", "Signed or locked: opens on its own.", SymTone.Grey),
    Folder("Folder", "The folder the case is filed in.", SymTone.Grey),
    Tag("Tag", "#name: a tag on the case.", SymTone.Grey),
}

/** The legend, in the order it reads: dates, orders, filing, then the four status edges. */
val LEGEND: List<Sym> = Sym.entries

/** A case's status, carried by its tile's left edge alone (owner, 23 Nov 2026), one colour each. */
enum class Edge(val sym: Sym) { Red(Sym.EdgeRed), Ink(Sym.EdgeInk), Green(Sym.EdgeGreen), Grey(Sym.EdgeGrey) }

/** Red (needs your eye, or moved today or yesterday), then black (disposed), then green (watched daily), then pale grey (not watched). */
internal fun rowEdge(c: BoardCase, today: LocalDate): Edge = when {
    c.needsEye || movedToday(c, today) -> Edge.Red
    c.disposed -> Edge.Ink
    c.morningCheck -> Edge.Green
    else -> Edge.Grey
}

/** The next date's mark: no date, Needs Your Eye, moved (from the case's Date History), confirmed today, checked earlier, or none. */
internal fun dateMark(c: BoardCase, today: LocalDate): Sym? = when {
    c.nextDate.isNullOrBlank() -> Sym.Undated
    c.needsEye -> Sym.NeedsEye
    movedToday(c, today) -> Sym.Moved
    c.check != null && BoardDates.parse(c.check.forDate) == today -> Sym.CheckedToday
    // A morning check or a read from the court (read_at): the date was checked with eCourts.
    c.check != null || c.readAt != null -> Sym.Checked
    else -> null
}

/**
 * eCourts' check moved the next date recently: today or yesterday (a move found by yesterday's check still matters to a counsel
 * who did not open the app). From the server's date_moved_on (list items); on the case page, from the newest Date History row.
 */
internal fun movedToday(c: BoardCase, today: LocalDate): Boolean {
    val on = BoardDates.parse(c.dateMovedOn)
        ?: c.history.firstOrNull()?.takeIf { it.source == "ecourts_check" && !it.superseded }?.let { BoardDates.parse(it.at) }
        ?: return false
    return !on.isAfter(today) && !on.isBefore(today.minusDays(MOVED_DAYS - 1L))
}

/** How many days the red ↻ stays: today and yesterday. */
internal const val MOVED_DAYS = 2

/** An order's mark. */
internal fun orderMark(o: BoardOrder): Sym = when {
    o.sealed -> Sym.OrderSealed
    o.fetched -> Sym.OrderKept
    else -> Sym.OrderAvailable
}

/** What a long-press on an order says: its label, then what a tap does. */
internal fun orderHint(o: BoardOrder): String = orderLabel(o) + ". " + when (orderMark(o)) {
    Sym.OrderKept -> "Kept, tap to open."
    Sym.OrderSealed -> "Signed, opens on its own."
    else -> "Not fetched yet. Tap to get it."
}

/** The text form of a case's tags: "#Urgent #Bank". */
internal fun tagsLine(c: BoardCase): String = c.tags.joinToString(" ") { "#" + it.name }

/** The count shown on the filter icon: how many of Court, Folder and Tag are set. */
internal fun activeFilters(q: CaseQuery): Int = listOf(q.court.isNotBlank(), q.folder.isNotBlank(), q.tag > 0).count { it }

@Composable
internal fun toneColor(t: SymTone): Color = when (t) {
    SymTone.Green -> Lex.colors.success.solid
    SymTone.Red -> Lex.colors.danger.solid
    SymTone.Grey -> Lex.colors.mutedForeground
    SymTone.Ink -> Lex.colors.foreground
    SymTone.Pale -> Lex.colors.border
}

/** The colour of a status edge. */
@Composable
fun edgeColor(e: Edge): Color = toneColor(e.sym.tone)

private fun iconOf(s: Sym): ImageVector? = when (s) {
    Sym.Checked, Sym.CheckedToday -> Icons.Outlined.Check
    Sym.Moved -> Icons.Outlined.Update
    Sym.NeedsEye -> Icons.Outlined.PriorityHigh
    Sym.Undated -> Icons.Outlined.RadioButtonUnchecked
    // As on the order buttons.
    Sym.OrderKept -> Icons.Outlined.TaskAlt
    Sym.OrderAvailable -> Icons.Outlined.FileDownload
    Sym.OrderSealed -> Icons.Outlined.Lock
    Sym.Folder -> Icons.Outlined.Folder
    Sym.Tag, Sym.EdgeRed, Sym.EdgeInk, Sym.EdgeGreen, Sym.EdgeGrey -> null
}

/** One symbol, small, in its tone, named for screen readers. */
@Composable
fun SymbolIcon(s: Sym, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val tint = toneColor(s.tone)
    when (s) {
        // The legend's edge swatches: a small tile with its left edge in the status colour.
        Sym.EdgeRed, Sym.EdgeInk, Sym.EdgeGreen, Sym.EdgeGrey -> Row(modifier.semantics { contentDescription = s.label }.size(width = 22.dp, height = 16.dp)
            .clip(RoundedCornerShape(3.dp)).background(Lex.colors.background).border(1.dp, Lex.colors.border, RoundedCornerShape(3.dp))) {
            Box(Modifier.width(4.dp).height(16.dp).background(tint))
        }
        Sym.Tag -> Text("#", modifier.semantics { contentDescription = s.label }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
        else -> Icon(iconOf(s)!!, contentDescription = s.label, tint = tint, modifier = modifier.size(size))
    }
}

/** An order button's caption: "Order" alone, or "Order 1", "Order 2" … when the hearing has several (owner, 23 Nov 2026). */
internal fun orderCaption(list: List<BoardOrder>, i: Int): String = orderIndex(list, i)?.let { "Order $it" } ?: "Order"

/**
 * One order as a styled button (owner: "Stylise it. Do not keep it simple."): kept is filled green with a ticked doc; not fetched is an
 * ink outline with a download glyph; signed is a grey outline with a lock; fetching shows a spinner and is disabled. Radius 6, 36dp
 * tall. A tap opens a kept order, or fetches then opens one not fetched. Read aloud with the server's full label and what a tap does.
 */
@Composable
fun OrderButton(caption: String, o: BoardOrder, enabled: Boolean, busy: Boolean, onOpen: (BoardOrder) -> Unit, onGet: (BoardOrder) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val kept = o.fetched && !o.sealed
    val (bg, fg, line) = when {
        kept -> Triple(Lex.colors.success.solid, Color.White, null)
        o.sealed -> Triple(Color.Transparent, Lex.colors.secondaryForeground, Lex.colors.mutedForeground)
        else -> Triple(Color.Transparent, Lex.colors.foreground, Lex.colors.foreground)
    }
    val icon = when {
        kept -> Icons.Outlined.TaskAlt
        o.sealed -> Icons.Outlined.Lock
        else -> Icons.Outlined.FileDownload
    }
    val on = !busy && (enabled || o.fetched)
    Row(
        Modifier.height(36.dp).clip(shape).background(bg).let { if (line != null) it.border(1.dp, line, shape) else it }
            .clickable(enabled = on) { if (o.fetched) onOpen(o) else onGet(o) }
            .semantics { contentDescription = if (busy) "Getting " + orderLabel(o) else orderHint(o) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
        else Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(caption, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** A hearing's orders as buttons in a flowing row under its text. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OrderButtons(orders: List<BoardOrder>, enabled: Boolean, fetchingId: Int?, onOpen: (BoardOrder) -> Unit, onGet: (BoardOrder) -> Unit) {
    if (orders.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orders.forEachIndexed { i, o -> OrderButton(orderCaption(orders, i), o, enabled, o.id == fetchingId, onOpen, onGet) }
    }
}

/** A hearing's orders are numbered 1, 2 … on their icons only when it has more than one (in the server's order, as its labels count). */
internal fun orderIndex(list: List<BoardOrder>, i: Int): Int? = if (list.size > 1) i + 1 else null

/** The legend: every symbol with one line, in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendSheet(onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Lex.colors.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What The Symbols Mean", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
            LEGEND.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { SymbolIcon(s, 18.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(s.label, style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground)
                        Text(s.line, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
