package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// -----------------------------------------------------------------------------
// The Calendar (REQ-008 round 4, canvas 6a; it was Today): months across the top, a week with the chosen
// day as a black pill and a dot where cases are listed, then a black sheet with the day and its cases as
// a timeline. The week is Sunday to Saturday; a swipe moves it by a week.
// -----------------------------------------------------------------------------

internal val SheetBlack = Color(0xFF111110)
private val SheetMuted = Color(0xFFA3A19A)
private val SheetSoft = Color(0xFFD6D4CF)
private val SheetLine = Color(0xFF3A3936)
private val EyeRed = Color(0xFFA3261B)

/** The Sunday on or before a date (the week starts on Sunday, as the canvas has it). */
internal fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())

/** The seven days of the week a date is in, Sunday first. */
internal fun weekDays(d: LocalDate): List<LocalDate> = weekStart(d).let { s -> (0 until 7).map { s.plusDays(it.toLong()) } }

/** A swipe moves the chosen day by a week, so the black pill stays on the same weekday. */
internal fun shiftWeek(d: LocalDate, weeks: Int): LocalDate = d.plusWeeks(weeks.toLong())

private fun monthName(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** The month before, the month of the date, the month after, by name ("October", "November", "December"). */
internal fun monthsAround(d: LocalDate): Triple<String, String, String> =
    Triple(monthName(d.minusMonths(1)), monthName(d), monthName(d.plusMonths(1)))

/** "Today", or "Monday, 9 Nov": the sheet's heading. */
internal fun sheetHeading(d: LocalDate, today: LocalDate): String =
    if (d == today) "Today" else d.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

internal fun listedText(n: Int): String = "$n ${plural(n, "case", "cases")} listed"

/** "3 cases · eCourts checked 7:05 AM": the checked time only on today. */
internal fun daySubline(count: Int, isToday: Boolean, checked: String?): String =
    "$count ${plural(count, "case", "cases")}" + if (isToday && checked != null) " · eCourts checked $checked" else ""

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

/** The two parties of a case: the petitioner and respondent when the server names both, else the title split at "v." / "vs" / "v/s". */
internal fun parties(c: BoardCase): Pair<String, String>? {
    if (c.petitioner.isNotBlank() && c.respondent.isNotBlank()) return c.petitioner.trim() to c.respondent.trim()
    val m = Regex("""^(.+?)\s+(?:v\.?|vs\.?|v/s)\s+(.+)$""", RegexOption.IGNORE_CASE).find(c.title.trim()) ?: return null
    return m.groupValues[1].trim() to m.groupValues[2].trim()
}

/** The months across the top: the month of the chosen day in bold, its neighbours faded. Tap for the picker. */
@Composable
internal fun MonthsRow(date: LocalDate, onPick: () -> Unit) {
    val (prev, cur, next) = monthsAround(date)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(top = 10.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(prev, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = Color(0xFFC9C6BF), maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-28).dp))
        Text(cur, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Lex.colors.foreground, maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-28).dp))
        Text(next, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = Color(0xFFC9C6BF), maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-28).dp))
    }
}

/** The week: letters, dates, the chosen day as a black pill, a dot under each day that has cases. Swipe sideways for another week. */
@Composable
internal fun WeekRow(selected: LocalDate, today: LocalDate, counts: Map<String, Int>, onPick: (LocalDate) -> Unit, onSwipe: (Int) -> Unit) {
    val days = weekDays(selected)
    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().pointerInput(selected) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = { if (drag < -80f) onSwipe(1) else if (drag > 80f) onSwipe(-1); drag = 0f },
                onDragCancel = { drag = 0f },
            ) { _, amount -> drag += amount }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Lex.colors.mutedForeground)
            }
        }
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.Top) {
            days.forEach { d ->
                val on = d == selected
                val listed = (counts[BoardDates.api(d)] ?: 0) > 0
                Column(
                    Modifier.weight(1f).clickable { onPick(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (on) Column(
                        Modifier.size(width = 38.dp, height = 54.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF1D1D1B)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (d == today) "TODAY" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), fontSize = 8.5.sp, letterSpacing = 0.8.sp, color = Color.White, maxLines = 1, softWrap = false)
                        Text(d.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                    } else {
                        Text(
                            d.dayOfMonth.toString(), fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp),
                            color = if (d == today) Lex.colors.foreground else if (listed) Lex.colors.foreground else Lex.colors.mutedForeground,
                        )
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (listed) Color(0xFF1D1D1B) else Color.Transparent))
                    }
                }
            }
        }
    }
}

private val VsContent = mapOf(
    "vs" to InlineTextContent(Placeholder(28.sp, 17.sp, PlaceholderVerticalAlign.Center)) {
        Box(Modifier.size(width = 26.dp, height = 17.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, Color(0xFF8A877F), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text("VS", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color(0xFF8A877F), maxLines = 1, softWrap = false)
        }
    },
)

private fun titleText(c: BoardCase): AnnotatedString = buildAnnotatedString {
    val p = parties(c)
    if (p == null) append(c.title.ifBlank { c.cnr })
    else { append(p.first); append(" "); appendInlineContent("vs", "VS"); append(" "); append(p.second) }
}

/** One case on the day's timeline: case number, the parties with a small VS between, the judge, the court. Full text. */
@Composable
internal fun TimelineCase(c: BoardCase, onOpen: () -> Unit, onAnswer: (NeedsEye, DateAnswer) -> Unit, today: LocalDate) {
    val eye = c.needsEye && c.ecourtsDate != null
    val numberColour = if (eye) Color(0xFF55524C) else SheetMuted
    val titleColour = if (eye) Lex.colors.foreground else Color.White
    val judgeColour = if (eye) Color(0xFF3D3B37) else SheetSoft
    val courtColour = if (eye) Color(0xFF55524C) else SheetMuted
    val body: @Composable () -> Unit = {
        Text(c.heading, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = numberColour)
        Text(titleText(c), inlineContent = VsContent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = titleColour)
        c.judgeShown.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = judgeColour) }
        c.courtShown.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.5.sp, color = courtColour) }
    }
    Box(Modifier.fillMaxWidth().padding(start = 22.dp)) {
        // The node on the rail: a white dot, or a red one on the case that needs a look.
        Box(
            Modifier.offset(x = (-22).dp, y = if (eye) 16.dp else 5.dp).size(if (eye) 13.dp else 9.dp).clip(CircleShape)
                .background(if (eye) EyeRed else Color.White).then(if (eye) Modifier.border(2.dp, SheetBlack, CircleShape) else Modifier),
        )
        if (eye) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F2EE)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("NEEDS YOUR EYE", fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = EyeRed)
            body()
            val ne = NeedsEye(c.id, c.cnr, c.caseNo, c.title, c.ecourtsDate, c.nextDate)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(eyeLine(ne, today), fontSize = 12.sp, color = EyeRed, modifier = Modifier.weight(1f))
                IconAction(Icons.Outlined.Check, "eCourts is right: accept its date", { onAnswer(ne, DateAnswer.EcourtsRight) }, look = ActionLook.Ink, size = 36.dp)
                IconAction(Icons.Outlined.EventAvailable, "Keep my date", { onAnswer(ne, DateAnswer.KeepMine) }, look = ActionLook.Outline, size = 36.dp)
            }
        } else Column(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(3.dp)) { body() }
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
