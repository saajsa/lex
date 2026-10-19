package com.legumlex.client.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderStrong
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.TertiaryText

// Outlined input shaped to match the handoff: 14dp radius, monochrome borders,
// leading icon at 20dp, optional trailing slot for show/hide etc.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = trailing,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType
        ),
        shape = MaterialTheme.shapes.medium,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = BorderStrong,
            unfocusedBorderColor = BorderColor,
            focusedLabelColor    = PrimaryText,
            unfocusedLabelColor  = SecondaryText,
            focusedTextColor     = PrimaryText,
            unfocusedTextColor   = PrimaryText,
            cursorColor          = PrimaryText,
            focusedContainerColor   = Surface1,
            unfocusedContainerColor = Surface1,
            focusedLeadingIconColor   = SecondaryText,
            unfocusedLeadingIconColor = TertiaryText,
        ),
    )
}
