package com.legumlex.client.feature.consultations.components

import com.legumlex.client.ahc.theme.LlColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.Surface2

// Smart-section header — eyebrow + count chip. Optional accent variant for the
// "Needs your action" section: orange leading dot + orange-tinted count chip,
// per the cons.jsx <SectionHead accent /> design.
@Composable
fun SectionHeader(
    label: String,
    count: Int? = null,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accentOrange    = LlColor.AmberMid
    val accentTextOnSft = LlColor.Amber
    val accentSoftBg    = LlColor.AmberSoft

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accent) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentOrange),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label.uppercase(),
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.32.sp,
            color = if (accent) accentTextOnSft else SecondaryText,
        )
        count?.let {
            Spacer(Modifier.width(8.dp))
            Surface(
                color = if (accent) accentSoftBg else Surface2,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    it.toString(),
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    color = if (accent) accentTextOnSft else SecondaryText,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}
