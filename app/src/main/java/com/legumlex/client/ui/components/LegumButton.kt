package com.legumlex.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderStrong
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.Surface1

// Primary CTA, as Mylex's kt-btn: the monochrome accent (black on light,
// white on dark), 8dp radius; 48dp tall for a thumb.
@Composable
fun LegumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentInk,
            contentColor = InverseText,
            disabledContainerColor = AccentInk.copy(alpha = 0.4f),
            disabledContentColor = InverseText.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
        trailingIcon?.let { Spacer(Modifier.width(10.dp)); Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
    }
}

// Secondary CTA, as kt-btn-outline: the field border, card fill.
@Composable
fun LegumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Surface1,
            contentColor = PrimaryText,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
