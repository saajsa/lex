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
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.PillShape

data class RiskPalette(val solid: Color, val softBg: Color, val softText: Color, val label: String)

val RiskLow      = RiskPalette(Color(0xFF16A34A), Color(0xFFDCFCE7), Color(0xFF15803D), "Low")
val RiskMedium   = RiskPalette(Color(0xFFD97706), Color(0xFFFEF3C7), Color(0xFF92400E), "Medium")
val RiskHigh     = RiskPalette(Color(0xFFEA580C), Color(0xFFFFEDD5), Color(0xFF9A3412), "High")
val RiskCritical = RiskPalette(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFFB91C1C), "Critical")

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
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (large) 15.sp else 12.5.sp,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = p.label.uppercase(),
                color = p.softText,
                fontFamily = SourceSans,
                fontWeight = FontWeight.Bold,
                fontSize = if (large) 11.sp else 10.sp,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
