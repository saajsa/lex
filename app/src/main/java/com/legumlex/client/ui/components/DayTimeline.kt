package com.legumlex.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * The day timeline (owner, 2026-10-20; Notifications and the Action Queue):
 * the date and the day's name at the head, a strip of days with the chosen
 * one a dark pill, and the day's items hung on a rail, the one that matters
 * most a dark card, the rest plain rows.
 */

private val DateLine = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** "Today", "Yesterday", "Tomorrow", or the weekday. */
fun dayTitle(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

/** The head: the date over the day's name, an optional action at the right. */
@Composable
fun DayHead(
    day: LocalDate,
    modifier: Modifier = Modifier,
    title: String = dayTitle(day),
    dateLine: String = DateLine.format(day),
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(dateLine, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
                color = Lex.colors.foreground,
            )
        }
        trailing?.invoke()
    }
}

/** "d MMMM yyyy", for heads such as "Before 21 September 2026". */
fun longDate(d: LocalDate): String = DateLine.format(d)

/**
 * The strip of days. The chosen day is a dark pill with a dot; a day with
 * something pending (unread) is coloured red and dotted, one with items a
 * grey dot. What falls
 * outside the strip's span sits in a chip at its start ([before], e.g.
 * "Earlier") or its end ([after], e.g. "Later"); [selected] null with
 * [edge] -1 or 1 means that chip is chosen. It opens scrolled to the choice.
 */
@Composable
fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate?,
    marked: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    unread: Set<LocalDate> = emptySet(),
    before: String? = null,
    after: String? = null,
    edge: Int = 0,
    edgeUnread: Boolean = false,
    onEdge: (Int) -> Unit = {},
) {
    val list = rememberLazyListState()
    LaunchedEffect(days.size) {
        val i = if (selected == null) (if (edge < 0) 0 else days.size) else days.indexOf(selected) + (if (before != null) 1 else 0)
        if (i >= 0) list.scrollToItem((i - 3).coerceAtLeast(0))
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (before != null) item("before") { EdgeChip(before, selected == null && edge < 0, edgeUnread) { onEdge(-1) } }
        itemsIndexed(days, key = { _, d -> d.toEpochDay() }) { _, d ->
            val on = d == selected
            // A day with something pending is coloured: red ink on a soft red
            // pill, so it stands out from the plain days around it.
            val pending = d in unread && !on
            val ink = when {
                on -> Lex.colors.primaryForeground
                pending -> Lex.colors.danger.softText
                else -> Lex.colors.mutedForeground
            }
            Column(
                Modifier
                    .width(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(when { on -> Lex.colors.primary; pending -> Lex.colors.danger.softBg; else -> Color.Transparent })
                    .clickable { onSelect(d) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${d.dayOfMonth}", fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = ink)
                Spacer(Modifier.height(2.dp))
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), fontSize = 11.sp, color = ink)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(4.dp).clip(CircleShape).background(
                        when {
                            on -> Lex.colors.primaryForeground
                            d in unread -> Lex.colors.danger.solid
                            d in marked -> Lex.colors.mutedForeground
                            else -> Color.Transparent
                        }
                    )
                )
            }
        }
        if (after != null) item("after") { EdgeChip(after, selected == null && edge > 0, false) { onEdge(1) } }
    }
}

/** "Earlier" / "Later": everything beyond the strip's span, as one choice. */
@Composable
private fun EdgeChip(label: String, on: Boolean, unread: Boolean, onClick: () -> Unit) {
    val pending = unread && !on
    val ink = when {
        on -> Lex.colors.primaryForeground
        pending -> Lex.colors.danger.softText
        else -> Lex.colors.mutedForeground
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(23.dp))
            .background(when { on -> Lex.colors.primary; pending -> Lex.colors.danger.softBg; else -> Color.Transparent })
            .border(1.dp, when { on -> Color.Transparent; pending -> Lex.colors.danger.solid; else -> Lex.colors.border }, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("···", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = ink)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(
            when { on -> Lex.colors.primaryForeground; unread -> Lex.colors.danger.solid; else -> Color.Transparent }))
    }
}

/**
 * One item on the rail. [live] fills its circle; [featured] draws it as the
 * dark card; [first]/[last] end the rail at it.
 */
@Composable
fun TimelineItem(
    title: String,
    time: String,
    body: String,
    live: Boolean,
    featured: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val rail = Lex.colors.border
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The rail: a line through, broken at the top of the first and the foot
        // of the last, and the item's circle.
        Box(Modifier.width(36.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(1.5.dp).height(if (featured) 26.dp else 22.dp).background(if (first) Color.Transparent else rail))
                Box(Modifier.width(1.5.dp).weight(1f).background(if (last) Color.Transparent else rail))
            }
            Box(Modifier.padding(top = if (featured) 18.dp else 14.dp)) { RailDot(live = live, featured = featured) }
        }
        if (featured) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp, top = 6.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FeatureBg)
                    .then(if (Lex.colors.isDark) Modifier.border(1.dp, Lex.colors.border, RoundedCornerShape(20.dp)) else Modifier)
                    .clickable(onClick = onClick)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(time, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(body, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.72f), maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
                footer?.let { Spacer(Modifier.height(14.dp)); it() }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (live) Lex.colors.foreground else Lex.colors.secondaryForeground,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(time, fontSize = 12.sp, color = Lex.colors.mutedForeground)
                }
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(body, fontSize = 12.5.sp, color = Lex.colors.mutedForeground, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
                footer?.let { Spacer(Modifier.height(8.dp)); it() }
            }
        }
    }
}

/** The dark card's own ground, in either theme. */
private val FeatureBg = Color(0xFF141414)

@Composable
private fun RailDot(live: Boolean, featured: Boolean) {
    val ink = Lex.colors.foreground
    if (featured) {
        // A ring with a dot in it: the item this day turns on.
        Box(Modifier.size(14.dp).clip(CircleShape).border(1.5.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(ink))
        }
    } else {
        Box(
            Modifier.size(11.dp).clip(CircleShape)
                .background(if (live) ink else Lex.colors.background)
                .border(1.5.dp, if (live) ink else Lex.colors.mutedForeground, CircleShape)
        )
    }
}

/** A day with nothing on it. */
@Composable
fun DayEmpty(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp))
}
