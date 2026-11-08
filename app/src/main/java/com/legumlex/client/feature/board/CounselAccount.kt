package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.legumlex.client.feature.more.MenuRow
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex

/** The Counsel workspace's Account: the profile, and Sign Out. The firm's other rows are not part of it. */
@Composable
fun CounselAccountScreen(onProfile: () -> Unit, onSignOut: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "Counsel", title = "Account")
        Column {
            Eyebrow("Your Account", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            LexCard {
                MenuRow(Icons.Outlined.PersonOutline, "Profile", "Your details and your mobile number", onProfile)
                HorizontalDivider(color = Lex.colors.border)
                MenuRow(Icons.AutoMirrored.Outlined.Logout, "Sign Out", "Remove your sign-in from this phone", { confirm = true })
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Sign Out?") },
        text = { Text("Signing out removes your sign-in and the documents opened on this device. You can sign in again at any time.") },
        confirmButton = { TextButton({ confirm = false; onSignOut() }) { Text("Sign Out", color = Lex.colors.danger.softText) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

/** The credits chip in the Counsel header: the balance, red when the server says it is low. Opens Credits. */
@Composable
fun CounselCreditsChip(onClick: () -> Unit) {
    val chip by BoardHub.chip.collectAsState()
    val access by BoardHub.access.collectAsState()
    val balance = chip?.balance ?: (access as? BoardAccess.Yes)?.summary?.credits ?: return
    val low = chip?.low == true
    val red = Lex.colors.danger
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Text(
        "${creditsText(balance)} Credits",
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = if (low) red.softText else Lex.colors.foreground,
        modifier = Modifier.padding(end = 6.dp)
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
