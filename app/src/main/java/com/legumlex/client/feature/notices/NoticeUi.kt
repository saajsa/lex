package com.legumlex.client.feature.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.SourceSans

/** Direction → colour. Outbound = slate (you're sending), Inbound = amber (needs you). */
data class DirColors(val solid: Color, val mid: Color, val soft: Color)

fun dirColors(d: NoticeDirection): DirColors = when (d) {
    NoticeDirection.OUTBOUND -> DirColors(LlColor.Slate, LlColor.SlateMid, LlColor.SlateSoft)
    NoticeDirection.INBOUND  -> DirColors(LlColor.Amber, LlColor.AmberMid, LlColor.AmberSoft)
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, mono: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(
            text,
            fontFamily = if (mono) SourceSans else SourceSans,
            fontWeight = if (mono) FontWeight.Medium else FontWeight.Bold,
            fontSize = 10.sp,
            color = fg,
        )
    }
}

@Composable
fun DirectionBadge(d: NoticeDirection) {
    val c = dirColors(d)
    Chip(if (d == NoticeDirection.OUTBOUND) "OUTBOUND" else "INBOUND", c.soft, c.solid)
}

/** Indian (INR) currency format — digits grouped by lakh/crore: ₹2,10,000 · ₹1,46,500
 *  · ₹1,00,00,000. Exact rupee figure (no lakh/crore abbreviation) so claim amounts
 *  stay precise. */
fun money(amount: Double?): String? {
    if (amount == null || amount <= 0.0) return null
    return "₹" + indianGrouping(Math.round(amount))
}

fun indianGrouping(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val last3 = s.substring(s.length - 3)
    val rest = s.substring(0, s.length - 3)
    val sb = StringBuilder()
    var i = rest.length
    while (i > 0) {
        val start = maxOf(0, i - 2)
        if (sb.isNotEmpty()) sb.insert(0, ",")
        sb.insert(0, rest.substring(start, i))
        i = start
    }
    return "$sb,$last3"
}
