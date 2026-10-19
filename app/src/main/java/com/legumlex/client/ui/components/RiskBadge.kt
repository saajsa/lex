package com.legumlex.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.feature.consultations.RiskLevel
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.PillShape

data class RiskPalette(val solid: Color, val softBg: Color, val softText: Color, val label: String)

// Mylex's status colours. High sits between the warning yellow and the
// danger red as Tailwind's orange, so the four levels stay distinguishable.
val RiskLow: RiskPalette      @Composable get() = Lex.colors.success.let { RiskPalette(it.solid, it.softBg, it.softText, "Low") }
val RiskMedium: RiskPalette   @Composable get() = Lex.colors.warning.let { RiskPalette(it.solid, it.softBg, it.softText, "Medium") }
val RiskHigh: RiskPalette     @Composable get() =
    if (Lex.colors.isDark) RiskPalette(Color(0xFFEA580C), Color(0x80431407), Color(0xFFF97316), "High")
    else RiskPalette(Color(0xFFF97316), Color(0xFFFFEDD5), Color(0xFF9A3412), "High")
val RiskCritical: RiskPalette @Composable get() = Lex.colors.danger.let { RiskPalette(it.solid, it.softBg, it.softText, "Critical") }

@Composable
fun RiskLevel.palette(): RiskPalette = when (this) {
    RiskLevel.LOW      -> RiskLow
    RiskLevel.MEDIUM   -> RiskMedium
    RiskLevel.HIGH     -> RiskHigh
    RiskLevel.CRITICAL -> RiskCritical
}

/**
 * Risk pill used everywhere a risk level surfaces.
 * - `numeric` variant prints "72 · HIGH" (AHC: numeric 0-100 score).
 * - `enum` variant prints "HIGH" only (CLM: enum field).
 * - `large` is for the AHC detail hero card.
 */
@Composable
fun RiskBadge(
    level: RiskLevel,
    modifier: Modifier = Modifier,
    score: Int? = null,
    numeric: Boolean = false,
    large: Boolean = false,
) {
    val p = level.palette()
    val hPad = if (large) 14.dp else 10.dp
    val vPad = if (large) 7.dp  else 4.dp
    val dot  = if (large) 9.dp  else 7.dp
    Surface(color = p.softBg, contentColor = p.softText, shape = PillShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = hPad - 2.dp, end = hPad, top = vPad, bottom = vPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(dot).clip(CircleShape).background(p.solid))
            Spacer(Modifier.width(if (large) 8.dp else 6.dp))
            if (numeric && score != null) {
                Text(
                    text = "$score ·",
                    color = p.solid,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (large) 15.sp else 12.5.sp,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = p.label.uppercase(),
                color = p.softText,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = if (large) 11.sp else 10.sp,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
