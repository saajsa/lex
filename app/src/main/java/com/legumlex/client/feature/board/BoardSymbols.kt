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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Visibility
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
enum class SymTone { Grey, Green, Red }

/** Every symbol the counsel workspace shows. [name] is what a screen reader says and the legend's line explains it. */
enum class Sym(val label: String, val line: String, val tone: SymTone) {
    Checked("Checked", "The next date was checked with eCourts, by a check or a read.", SymTone.Grey),
    CheckedToday("Confirmed Today", "This morning's eCourts check confirmed the date.", SymTone.Green),
    Moved("Date Moved", "eCourts moved the next date today or yesterday.", SymTone.Red),
    NeedsEye("Needs Your Eye", "eCourts shows a different date: choose which is right.", SymTone.Red),
    Undated("No Date", "The case has no next date yet.", SymTone.Grey),
    CheckOn("Daily Check On", "Checked with eCourts every morning.", SymTone.Grey),
    CheckOff("Daily Check Off", "Not checked; its date stays as it is.", SymTone.Grey),
    OrderKept("Order Kept", "Kept on the platform: tap to open, free.", SymTone.Green),
    OrderAvailable("Order Not Fetched", "Tap to get it from eCourts; its cost shows first.", SymTone.Grey),
    OrderSealed("Signed Order", "Signed or locked: opens on its own.", SymTone.Grey),
    Folder("Folder", "The folder the case is filed in.", SymTone.Grey),
    Tag("Tag", "#name: a tag on the case.", SymTone.Grey),
    Ecourts("eCourts", "The case is followed on eCourts (it has a CNR).", SymTone.Grey),
}

/** The legend, in the order it reads: dates, the daily check, orders, filing, source. */
val LEGEND: List<Sym> = Sym.entries

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
    else -> "Not fetched yet" + (shownCost(o.cost)?.let { ", ${creditsLabel(it)}" } ?: "") + ". Tap to get it."
}

/** The text form of a case's tags: "#Urgent #Bank". */
internal fun tagsLine(c: BoardCase): String = c.tags.joinToString(" ") { "#" + it.name }

/** The count shown on the filter icon: how many of Court, Folder and Tag are set. */
internal fun activeFilters(q: CaseQuery): Int = listOf(q.court.isNotBlank(), q.folder.isNotBlank(), q.tag > 0).count { it }

@Composable
private fun toneColor(t: SymTone): Color = when (t) {
    SymTone.Green -> Lex.colors.success.solid
    SymTone.Red -> Lex.colors.danger.solid
    SymTone.Grey -> Lex.colors.mutedForeground
}

private fun iconOf(s: Sym): ImageVector? = when (s) {
    Sym.Checked, Sym.CheckedToday -> Icons.Outlined.Check
    Sym.Moved -> Icons.Outlined.Update
    Sym.NeedsEye -> Icons.Outlined.PriorityHigh
    Sym.Undated -> Icons.Outlined.RadioButtonUnchecked
    Sym.CheckOn -> Icons.Filled.Visibility
    Sym.CheckOff -> Icons.Outlined.Visibility
    Sym.OrderKept -> Icons.Filled.Description
    Sym.OrderAvailable -> Icons.Outlined.Description
    Sym.OrderSealed -> Icons.Outlined.Lock
    Sym.Folder -> Icons.Outlined.Folder
    Sym.Tag, Sym.Ecourts -> null
}

/** One symbol, small, in its tone, named for screen readers. */
@Composable
fun SymbolIcon(s: Sym, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val tint = toneColor(s.tone)
    when (s) {
        Sym.Ecourts -> Box(
            modifier.semantics { contentDescription = s.label }.clip(RoundedCornerShape(4.dp)).border(1.dp, Lex.colors.input, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Text("e", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Lex.colors.secondaryForeground) }
        Sym.Tag -> Text("#", modifier.semantics { contentDescription = s.label }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
        else -> Icon(iconOf(s)!!, contentDescription = s.label, tint = tint, modifier = modifier.size(size))
    }
}

/** A small tappable symbol (an order, the eye): long-press names it; an optional caption beside it ("9"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolAction(s: Sym, hint: String, onClick: () -> Unit, enabled: Boolean = true, caption: String? = null, index: Int? = null) {
    val shape = RoundedCornerShape(6.dp)
    TooltipBox(positionProvider = OnScreenTooltip, tooltip = { PlainTooltip { Text(hint) } }, state = rememberTooltipState()) {
        Row(
            Modifier.clip(shape).border(1.dp, Lex.colors.input, shape).clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = hint }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            index?.let { Text("$it", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Lex.colors.secondaryForeground); Spacer(Modifier.width(3.dp)) }
            SymbolIcon(s, 18.dp)
            caption?.let { Spacer(Modifier.width(3.dp)); Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Lex.colors.secondaryForeground) }
        }
    }
}

/** One order as a small symbol button: open when kept, get (after the cost confirm) when not. */
@Composable
fun OrderSymbol(o: BoardOrder, enabled: Boolean, onOpen: (BoardOrder) -> Unit, onGet: (BoardOrder) -> Unit, index: Int? = null) {
    SymbolAction(orderMark(o), orderHint(o), { if (o.fetched) onOpen(o) else onGet(o) }, enabled = enabled || o.fetched,
        caption = if (o.fetched) null else shownCost(o.cost)?.toString(), index = index)
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
