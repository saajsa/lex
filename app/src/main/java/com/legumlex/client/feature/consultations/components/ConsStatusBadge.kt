package com.legumlex.client.feature.consultations.components

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
import com.legumlex.client.feature.consultations.ConsultationStatus
import com.legumlex.client.ui.theme.PillShape
import com.legumlex.client.ahc.theme.Inter

// Matches the cons.jsx <StatusPill /> exactly: tinted soft-bg pill with a
// 6dp leading dot in the solid color and uppercase tracked Space-Grotesk text.
@Composable
fun ConsStatusBadge(
    status: ConsultationStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = status.palette
    Surface(
        color = palette.softBg,
        contentColor = palette.softText,
        shape = PillShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical   = if (compact) 3.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.solid),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.label.uppercase(),
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize   = if (compact) 9.5.sp else 10.sp,
                letterSpacing = 0.8.sp,
                color = palette.softText,
            )
        }
    }
}
