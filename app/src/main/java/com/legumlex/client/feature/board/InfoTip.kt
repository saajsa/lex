package com.legumlex.client.feature.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.launch

/**
 * The "i" for a long description: a small symbol that opens a popover with the text on a tap (a long
 * press does the same). Long sentences live behind it, so a screen stays short. The popover stays inside
 * the window (OnScreenTooltip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTip(text: String, modifier: Modifier = Modifier, label: String = "More information", hit: Dp = 32.dp) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        modifier = modifier,
        positionProvider = OnScreenTooltip,
        tooltip = { PlainTooltip { Text(text) } },
        state = state,
    ) {
        Box(
            Modifier.size(hit).clip(androidx.compose.foundation.shape.CircleShape)
                .clickable { scope.launch { state.show() } }.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Info, contentDescription = null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(18.dp)) }
    }
}
