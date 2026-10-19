package com.legumlex.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface2

/**
 * Shown when the server refuses (403) a section the contact's account does
 * not include, when it says client_visible=0 on something the client
 * deep-linked into, or when staff hasn't enabled edits ("viewOnly").
 * [message] replaces the default sentence when the reason is known.
 */
@Composable
fun PermissionEmptyState(
    feature: String,
    viewOnly: Boolean = false,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        EyebrowText(text = feature)
        Spacer(Modifier.height(14.dp))
        Text(
            text = message ?: if (viewOnly) {
                "Your advocate hasn't enabled edits on this matter yet."
            } else {
                "Your advocate hasn't shared this with you yet."
            },
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}
