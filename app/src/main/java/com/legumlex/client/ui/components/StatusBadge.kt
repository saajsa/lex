package com.legumlex.client.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.StatusPalette

// Mylex's status chip (kt-badge kt-badge-light): tinted fill, deep text, 6dp
// corners, the label as written — Title Case ("Active", "Awaiting The Firm").
// Used everywhere a status is shown: case cards, headers, hearing rows.
@Composable
fun StatusBadge(
    label: String,
    palette: StatusPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = palette.softBg,
        contentColor = palette.softText,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.softText,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
