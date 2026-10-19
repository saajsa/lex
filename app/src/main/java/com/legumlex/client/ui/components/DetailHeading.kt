package com.legumlex.client.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.TertiaryText

/**
 * In-content page heading for screens whose top app bar was removed in favour of
 * the app-wide centered-logo header. Carries the eyebrow + title that used to
 * live in the bar, plus any contextual actions (overflow, share, info) on the
 * right. Back navigation now lives in the app header, so it is intentionally
 * absent here.
 */
@Composable
fun DetailHeading(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EyebrowText(eyebrow, color = TertiaryText)
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
