package com.legumlex.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.PillShape
import com.legumlex.client.ui.theme.StatusPalette

// Soft-tinted pill with a colored dot. Used everywhere a status is shown:
// case cards, case detail header, hearing cards, etc.
@Composable
fun StatusBadge(
    label: String,
    palette: StatusPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = palette.softBg,
        contentColor = palette.softText,
        shape = PillShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.softText,
            )
        }
    }
}
