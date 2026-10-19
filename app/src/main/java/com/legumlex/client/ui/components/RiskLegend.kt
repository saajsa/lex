package com.legumlex.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.feature.consultations.RiskLevel
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.Surface1

private val RISK_DESCS = mapOf(
    RiskLevel.LOW      to "Routine provisions; standard precautions apply. No urgent action needed.",
    RiskLevel.MEDIUM   to "Notable concerns that your advocate should review before you sign.",
    RiskLevel.HIGH     to "Significant red flags. Legal review is strongly recommended.",
    RiskLevel.CRITICAL to "Severe exposure. Do not execute without a thorough legal consultation.",
)

@Composable
fun RiskLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
    ) {
        Text(
            text = "RISK LEVELS EXPLAINED",
            color = SecondaryText,
            fontFamily = SourceSans,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
        )
        HorizontalDivider(color = BorderColor)
        val rows = RiskLevel.values().toList()
        rows.forEachIndexed { i, level ->
            val p = level.palette()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(p.solid),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = p.label.uppercase(),
                        color = p.softText,
                        fontFamily = SourceSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = RISK_DESCS[level].orEmpty(),
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (i < rows.lastIndex) HorizontalDivider(color = BorderSubtle)
        }
    }
}
