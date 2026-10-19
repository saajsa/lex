package com.legumlex.client.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.legumlex.client.ui.theme.TertiaryText

// Uppercase + heavily tracked label. The handoff's "eyebrow" pattern.
// Used for section headers, badge text, stat labels, app-bar greetings.
@Composable
fun EyebrowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TertiaryText,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = style,
    )
}
