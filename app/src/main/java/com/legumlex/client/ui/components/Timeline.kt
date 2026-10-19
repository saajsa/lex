package com.legumlex.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Lex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * One stop on a timeline: a rail on the left (when it falls, and a dotted line
 * on to the next stop, with the gap between them on a pill), and a card on the
 * right (a leading block, the title with its status, a subtitle, and a meta
 * line). Consultations lead with the advocate's photo; hearings with a date
 * block. Flat: hairline borders, no shadows.
 */
@Composable
fun TimelineEntry(
    rail: String,
    railSub: String? = null,
    gapToNext: String? = null,
    isLast: Boolean,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    status: Pair<String, Color>? = null,
    meta: String? = null,
    metaIcon: ImageVector = Icons.Outlined.Schedule,
    muted: Boolean = false,
    stripe: Color? = null,
    onClick: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val accent = Lex.colors.info.solid
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // ── Rail ──
        Column(Modifier.width(66.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Text(rail, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = if (muted) Lex.colors.mutedForeground else accent, textAlign = TextAlign.Center, maxLines = 1)
            if (railSub != null) Text(railSub, style = MaterialTheme.typography.labelSmall, color = Lex.colors.mutedForeground, maxLines = 1)
            if (!isLast) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val line = Lex.colors.input
                    Canvas(Modifier.fillMaxHeight().width(2.dp).padding(vertical = 6.dp)) {
                        drawLine(
                            color = line, start = Offset(size.width / 2, 0f), end = Offset(size.width / 2, size.height),
                            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                    if (gapToNext != null) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Lex.colors.card)
                                .border(1.dp, Lex.colors.border, RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(gapToNext, fontSize = 9.5.sp, color = Lex.colors.secondaryForeground, maxLines = 1)
                        }
                    }
                }
            }
        }
        // ── Card ──
        Box(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 14.dp)) {
            LexCard(onClick = onClick) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    if (stripe != null) Box(Modifier.width(3.dp).fillMaxHeight().background(stripe))
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        leading()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // The status on its own line: beside a long title it cut
                            // the title short.
                            if (status != null) Row(Modifier.padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(status.second))
                                Spacer(Modifier.width(5.dp))
                                Text(status.first, style = MaterialTheme.typography.labelSmall, color = status.second, maxLines = 1)
                            }
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                color = if (muted) Lex.colors.mutedForeground else Lex.colors.foreground,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            if (meta != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(metaIcon, null, tint = if (muted) Lex.colors.mutedForeground else accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = if (muted) Lex.colors.mutedForeground else accent,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            extra()
                        }
                    }
                }
            }
        }
    }
}

/** The hearing's leading block: the day large, the month above, the year below. */
@Composable
fun DateBlock(date: LocalDate?, muted: Boolean = false) {
    val fg = if (muted) Lex.colors.mutedForeground else Lex.colors.foreground
    Column(
        Modifier.width(52.dp).height(62.dp).clip(RoundedCornerShape(12.dp)).background(Lex.colors.muted)
            .border(1.dp, Lex.colors.border, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (date == null) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = fg)
        } else {
            Text(date.format(DateTimeFormatter.ofPattern("MMM")).uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold,
                color = Lex.colors.info.solid, letterSpacing = 0.6.sp)
            Text("${date.dayOfMonth}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg, lineHeight = 21.sp)
            Text("${date.year}", fontSize = 9.sp, color = Lex.colors.mutedForeground)
        }
    }
}

/** "3 days", "2 wk", "5 mo": the gap between two stops, for the rail's pill. */
fun gapLabel(a: LocalDate?, b: LocalDate?): String? {
    if (a == null || b == null) return null
    val d = kotlin.math.abs(ChronoUnit.DAYS.between(a, b))
    return when {
        d == 0L -> "Same day"
        d == 1L -> "1 day"
        d < 14 -> "$d days"
        d < 60 -> "${d / 7} wk"
        d < 730 -> "${d / 30} mo"
        else -> "${d / 365} yr"
    }
}
