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
import androidx.compose.runtime.Composable
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

        Group("Your Work With Us") {
            MenuRow(Icons.Outlined.AccountBalance, "Litigation", "Your matters before a court — hearings, orders and notices", onOpenLitigation)
            Divider()
            MenuRow(Icons.Outlined.Layers, "Projects", "Work we run for you, and its tasks", onOpenProjects)
            Divider()
            MenuRow(Icons.Outlined.Inventory2, "Registry", "Your agreements and their deadlines", onOpenRegistry)
        }
        Group("Your File") {
            MenuRow(Icons.Outlined.Description, "Documents", "Papers shared with you, and what you have uploaded", onOpenVault)
            Divider()
            MenuRow(Icons.Outlined.ReceiptLong, "Invoices", "Your balance, what is due and what you have paid", onOpenInvoices)
        }
        Group("Ask") {
            MenuRow(Icons.Outlined.QuestionAnswer, "Ask Your Advocate", "Start a new consultation", onAskAdvocate)
        }
        Group("Litigation") {
            MenuRow(Icons.Outlined.MarkEmailUnread, "Notices", "Legal notices sent for you, and received", onOpenNotices)
            Divider()
            MenuRow(Icons.Outlined.CalendarMonth, "Calendar", "Hearings and appointments by date", onOpenCalendar)
        }
        Group("Your Account") {
            MenuRow(Icons.Outlined.Person, "Profile", "Your details, your mobile number, your account", onOpenProfile)
            Divider()
            MenuRow(Icons.Outlined.Notifications, "Notifications", "Updates from the firm on your matters", onOpenNotifications)
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, Lex.colors.input),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Lex.colors.card, contentColor = Lex.colors.danger.softText),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
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
