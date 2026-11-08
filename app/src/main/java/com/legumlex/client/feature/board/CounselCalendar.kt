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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// -----------------------------------------------------------------------------
// Today as the calendar flow (REQ-008 round 3, canvas 5a): a week strip of day chips with the
// number listed on each (board/days), the chosen day's card, and that day's cases (board/day).
// The strip's pattern is the staff app's DateChipStrip, in the client kit: radius 8, black on gold
// for the chosen day, the Mylex palette elsewhere.
// -----------------------------------------------------------------------------

/** The strip shows a week of days. */
internal const val StripDays = 7

/** The window that puts the chosen day in the middle of the week (the web's strip does the same). */
internal fun windowStart(selected: LocalDate): LocalDate = selected.minusDays(3)

internal fun stripDays(start: LocalDate, n: Int = StripDays): List<LocalDate> = (0 until n).map { start.plusDays(it.toLong()) }

/** Previous and next move the window by a week, as on the web. */
internal fun shiftWindow(start: LocalDate, weeks: Int): LocalDate = start.plusWeeks(weeks.toLong())

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "6 – 12 Nov", or "29 Oct – 4 Nov" across a month. */
internal fun windowLabel(start: LocalDate, n: Int = StripDays): String {
    val end = start.plusDays((n - 1).toLong())
    return if (start.month == end.month && start.year == end.year) "${start.dayOfMonth} – ${end.format(DAY_MONTH)}"
    else "${start.format(DAY_MONTH)} – ${end.format(DAY_MONTH)}"
}

/** "NOVEMBER 2026". */
internal fun monthHeading(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).uppercase()

/** "MONDAY, 9 NOV", the chosen day's card heading. */
internal fun dayHeading(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)).uppercase()

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

internal fun listedText(n: Int): String = "$n ${plural(n, "case", "cases")} listed"

/** "11 cases tracked · 99 days of checks left"; the days part is left out when no case is on the daily check. */
internal fun checksLine(tracked: Int, daysLeft: Int?): String {
    val head = "$tracked ${plural(tracked, "case", "cases")} tracked"
    val tail = when {
        daysLeft == null -> null
        daysLeft == 0 -> "less than a day of checks left"
        else -> "$daysLeft ${plural(daysLeft, "day", "days")} of checks left"
    }
    return if (tail == null) head else "$head · $tail"
}

/** The slim line under the day's cases. */
internal fun eyeText(n: Int): String = if (n == 1) "1 date needs your eye" else "$n dates need your eye"

/** board/days: the number listed on each date, zeros included. */
internal fun parseDays(d: Map<String, Any?>): Map<String, Int> =
    d["days"].list().associate { it["date"].str() to it["listed"].int() }

@Composable
internal fun MonthHeading(month: String, line: String) {
    Column {
        Text(month, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp, color = Lex.colors.foreground)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
    }
}

/** The week: arrows and the range on a line of their own, then seven chips of equal width. */
@Composable
internal fun CalendarStrip(
    selected: LocalDate, start: LocalDate, counts: Map<String, Int>, today: LocalDate,
    onPick: (LocalDate) -> Unit, onShift: (Int) -> Unit, onJump: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "Previous Week", { onShift(-1) }, size = 40.dp)
            Text(
                windowLabel(start), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Lex.colors.secondaryForeground,
            )
            IconAction(Icons.AutoMirrored.Outlined.ArrowForward, "Next Week", { onShift(1) }, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            IconAction(Icons.Outlined.CalendarMonth, "Jump To A Date", onJump, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            stripDays(start).forEach { d ->
                DayChip(d, counts[BoardDates.api(d)], d == selected, d == today, Modifier.weight(1f)) { onPick(d) }
            }
        }
    }
}

@Composable
private fun DayChip(date: LocalDate, listed: Int?, selected: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val ink = if (selected) androidx.compose.ui.graphics.Color.Black else Lex.colors.foreground
    val soft = if (selected) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f) else Lex.colors.mutedForeground
    Column(
        modifier.clip(shape)
            .background(if (selected) CounselGold else Lex.colors.card)
            .border(1.dp, if (selected) CounselGold else Lex.colors.border, shape)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase(), fontSize = 10.sp, letterSpacing = 0.8.sp, color = soft, maxLines = 1)
        Text(date.dayOfMonth.toString(), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, color = ink)
        // A fixed line for "Today" so the chips stay one height.
        Text(if (isToday) "Today" else " ", fontSize = 9.sp, lineHeight = 11.sp, color = soft, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (listed != null && listed > 0) Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(if (selected) androidx.compose.ui.graphics.Color.White else Lex.colors.muted).padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text(listed.toString(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.Black) }
        }
    }
}

/** The chosen day: weekday and date, "Today" when it is, how many are listed, when eCourts was checked (today), and Print for it. */
@Composable
internal fun DayCard(date: LocalDate, listed: Int?, today: LocalDate, checked: String?, onPrint: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(dayHeading(date) + if (date == today) " · TODAY" else "", color = Lex.colors.secondaryForeground)
            Text(
                if (listed == null) "…" else listedText(listed),
                fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Lex.colors.foreground,
            )
            if (date == today) Text(
                checked?.let { "eCourts checked $it" } ?: "eCourts not checked yet",
                style = MaterialTheme.typography.labelMedium, color = Lex.colors.mutedForeground,
            )
        }
        IconAction(Icons.Outlined.Print, "Print this day's cause list", onPrint, look = ActionLook.Gold)
    }
}

/** One of the day's cases: "case no · title", then "court · bench", a red dot when the date needs a look. */
@Composable
internal fun DayCaseRow(c: BoardCase, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOf(c.heading, c.title).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (c.courtLine.isNotBlank()) Text(c.courtLine, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (c.needsEye) Box(Modifier.padding(start = 10.dp).size(9.dp).clip(CircleShape).background(Lex.colors.danger.solid))
    }
}

/** The slim red line: how many dates need a look. */
@Composable
internal fun EyeLine(n: Int, open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val red = Lex.colors.danger
    Row(
        Modifier.fillMaxWidth().clip(shape).background(red.softBg).border(1.dp, red.solid.copy(alpha = 0.35f), shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(eyeText(n), style = MaterialTheme.typography.titleSmall, color = red.softText, modifier = Modifier.weight(1f))
        androidx.compose.material3.Icon(
            if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = if (open) "Hide" else "Show", tint = red.softText,
            modifier = Modifier.size(20.dp),
        )
    }
}
