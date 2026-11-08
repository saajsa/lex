package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.legumlex.client.ui.theme.Lex

/** The confirm for Sign Out, opened from the Counsel header's Sign Out button. */
@Composable
fun SignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Out?") },
        text = { Text("Signing out removes your sign-in and the documents opened on this device. You can sign in again at any time.") },
        confirmButton = { TextButton(onConfirm) { Text("Sign Out", color = Lex.colors.danger.softText) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

/** The credits chip in the Counsel header: the balance, red when the server says it is low. Opens Credits. */
@Composable
fun CounselCreditsChip(compact: Boolean = false, onClick: () -> Unit) {
    val chip by BoardHub.chip.collectAsState()
    val access by BoardHub.access.collectAsState()
    val balance = chip?.balance ?: (access as? BoardAccess.Yes)?.summary?.credits ?: return
    val low = chip?.low == true
    val red = Lex.colors.danger
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    // "1,097" with a small raised C, always the same compact chip.
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(creditsText(balance))
            append("\u2009")
            pushStyle(androidx.compose.ui.text.SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript, fontSize = 10.sp))
            append("C")
            pop()
        },
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = if (low) red.softText else Lex.colors.foreground,
        modifier = Modifier.padding(end = if (compact) 2.dp else 6.dp)
            .semantics { contentDescription = "${creditsText(balance)} credits" }
            .androidxClip(shape)
            .background(if (low) red.softBg else androidx.compose.ui.graphics.Color.Transparent)
            .androidxBorder(shape, if (low) red.solid else Lex.colors.input)
            .androidxClick(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

private fun Modifier.androidxClip(s: androidx.compose.ui.graphics.Shape) = this.then(Modifier.clip(s))
private fun Modifier.androidxBorder(s: androidx.compose.ui.graphics.Shape, c: androidx.compose.ui.graphics.Color) = this.then(Modifier.border(1.dp, c, s))
private fun Modifier.androidxClick(f: () -> Unit) = this.then(Modifier.clickable(onClick = f))
