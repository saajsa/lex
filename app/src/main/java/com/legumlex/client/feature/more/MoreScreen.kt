package com.legumlex.client.feature.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.profile.ProfileHub
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex

/**
 * Everything beyond the three workspaces, grouped as Mylex's sidebar groups
 * it: your file (documents, invoices), consultations, litigation's extras,
 * and your account — then Sign Out.
 */
@Composable
fun MoreScreen(
    onOpenVault: () -> Unit,
    onOpenInvoices: () -> Unit,
    onSignOut: () -> Unit,
    onOpenConsultations: () -> Unit = {},
    onAskAdvocate: () -> Unit = {},
    onOpenNotices: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenLitigation: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenRegistry: () -> Unit = {},
) {
    // What this contact may open, as Home has it: a workspace shows when it
    // has something in it and the contact holds its permission; the rest
    // follow the contact's permissions. Until both are read, nothing is hidden.
    val context = LocalContext.current
    val profile by ProfileHub.profile.collectAsState()
    LaunchedEffect(Unit) { if (ProfileHub.profile.value == null) ProfileHub.refresh(context) }
    val offered by produceState<Set<String>?>(initialValue = null) {
        value = runCatching {
            val d = ApiClient.get(context).workspaces()["data"] as? Map<*, *>
            (d?.get("offered") as? List<*>)?.mapNotNull { it as? String }?.toSet()
        }.getOrNull()
    }
    val perms = profile?.permissions?.map { it.lowercase() }?.toSet()
    fun ws(key: String) = offered?.contains(key) ?: true
    fun may(key: String) = perms?.contains(key) ?: true

    var confirmSignOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lex.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "More", title = "Everything Else")
        Spacer(Modifier.height(4.dp))

        val work = listOfNotNull(
            Item(Icons.Outlined.AccountBalance, "Litigation", "Your matters before a court: hearings and orders", onOpenLitigation).takeIf { ws("litigation") },
            Item(Icons.Outlined.Layers, "Projects", "Work we run for you, and its tasks", onOpenProjects).takeIf { ws("projects") },
            Item(Icons.Outlined.Inventory2, "Registry", "Your agreements and their deadlines", onOpenRegistry).takeIf { ws("registry") },
        )
        Group("Your Work With Us", work)
        // Notices sit with the file, not under Litigation: most never reach a
        // court, and a second "Litigation" heading repeated the row above.
        Group("Your File", listOfNotNull(
            Item(Icons.Outlined.Description, "Documents", "The papers the firm has shared with you", onOpenVault).takeIf { may("cases") },
            Item(Icons.Outlined.MarkEmailUnread, "Notices", "Legal notices sent for you, and received", onOpenNotices).takeIf { may("notices") },
            Item(Icons.Outlined.CalendarMonth, "Calendar", "Your hearings, by date", onOpenCalendar).takeIf { may("cases") },
            Item(Icons.Outlined.ReceiptLong, "Invoices", "Your balance, what is due and what you have paid", onOpenInvoices).takeIf { may("invoices") },
        ))
        Group("Ask", listOf(
            Item(Icons.Outlined.QuestionAnswer, "Consult", "Ask the advocates a new question, have an agreement read, or track a notice", onAskAdvocate),
        ))
        Group("Your Account", listOf(
            Item(Icons.Outlined.Person, "Profile", "Your details, your mobile number, your account", onOpenProfile),
            Item(Icons.Outlined.Notifications, "Notifications", "Announcements from the firm, and anything waiting on you", onOpenNotifications),
        ))

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { confirmSignOut = true },
            enabled = !signingOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, Lex.colors.input),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Lex.colors.card, contentColor = Lex.colors.danger.softText),
        ) {
            if (signingOut) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
            } else {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (signingOut) "Signing Out…" else "Sign Out", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign Out?") },
            text = { Text("Signing out removes your sign-in and the documents opened on this device. You can sign in again at any time.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    signingOut = true
                    onSignOut()
                }) { Text("Sign Out", color = Lex.colors.danger.softText) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel", color = Lex.colors.foreground) }
            },
            containerColor = Lex.colors.card,
        )
    }
}

private class Item(val icon: ImageVector, val title: String, val description: String, val onClick: () -> Unit)

/** A group of rows; a group the contact has nothing in is left out. */
@Composable
private fun Group(label: String, items: List<Item>) {
    if (items.isEmpty()) return
    Group(label) {
        items.forEachIndexed { i, it ->
            if (i > 0) Divider()
            MenuRow(it.icon, it.title, it.description, it.onClick)
        }
    }
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column {
        Eyebrow(label, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LexCard { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Lex.colors.border)

@Composable
private fun MenuRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Lex.colors.foreground, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Lex.colors.faint, modifier = Modifier.size(20.dp))
    }
}
