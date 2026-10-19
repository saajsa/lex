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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.PillShape

@Composable
fun ExpiryChip(days: Int, modifier: Modifier = Modifier) {
    val urgent = days < 7
    val p = if (urgent) RiskCritical else RiskHigh
    Surface(color = p.softBg, contentColor = p.softText, shape = PillShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(p.solid))
            Spacer(Modifier.width(6.dp))
            Text(
                "EXPIRES IN ",
                color = p.softText,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.0.sp,
            )
            Text(
                "${days}d",
                color = p.softText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
            )
        }
    }
}
