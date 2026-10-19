package com.legumlex.client.feature.consultations.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.PillShape
import com.legumlex.client.ahc.theme.SourceSans

@Composable
fun UrgentBadge(modifier: Modifier = Modifier) {
    Surface(
        color = AccentInk,
        contentColor = InverseText,
        shape = PillShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "URGENT",
                fontFamily = SourceSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 9.5.sp,
                letterSpacing = 0.76.sp,
            )
        }
    }
}
