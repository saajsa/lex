package com.legumlex.client.feature.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText

@Composable
fun MoreScreen(
    onOpenVault: () -> Unit,
    onOpenInvoices: () -> Unit,
    onSignOut: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s4, bottom = spacing.s5),
    ) {
        EyebrowText("More")
        Spacer(Modifier.height(2.dp))
        Text(
            "Workspace",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryText,
        )

        Spacer(Modifier.height(spacing.s6))

        EyebrowText("Your Stuff")
        Spacer(Modifier.height(spacing.s2))
        MenuGroup {
            MenuRow(
                icon  = Icons.Outlined.Folder,
                label = "Vault",
                sub   = "Documents shared with you, and what you've uploaded",
                onClick = onOpenVault,
            )
            MenuRowDivider()
            MenuRow(
                icon    = Icons.Outlined.Notifications,
                label   = "Notifications",
                sub     = "Updates, reminders, hearing changes",
                comingSoon = true,
            )
            MenuRowDivider()
            MenuRow(
                icon    = Icons.Outlined.Receipt,
                label   = "Invoices",
                sub     = "Outstanding balance and payment history",
                onClick = onOpenInvoices,
            )
        }

        Spacer(Modifier.height(spacing.s6))

        EyebrowText("Account")
        Spacer(Modifier.height(spacing.s2))
        MenuGroup {
            MenuRow(
                icon    = Icons.Outlined.AccountCircle,
                label   = "Profile",
                sub     = "Your details, identity, security",
                comingSoon = true,
            )
            MenuRowDivider()
            MenuRow(
                icon    = Icons.Outlined.SupportAgent,
                label   = "Help & support",
                sub     = "Get in touch with our team",
                comingSoon = true,
            )
        }

        Spacer(Modifier.height(spacing.s6))

        SignOutButton(onClick = onSignOut)
    }
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color  = MaterialTheme.colorScheme.surface,
        shape  = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: (() -> Unit)? = null,
    comingSoon: Boolean = false,
) {
    val enabled = onClick != null && !comingSoon
    val modifier = Modifier.fillMaxWidth()

    Surface(
        onClick = onClick ?: {},
        enabled = enabled,
        color   = MaterialTheme.colorScheme.surface,
        contentColor = PrimaryText,
        shape   = RoundedCornerShape(0.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null,
                    tint = if (comingSoon) TertiaryText else PrimaryText,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (comingSoon) SecondaryText else PrimaryText,
                    )
                    if (comingSoon) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = Surface2,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                        ) {
                            Text(
                                "SOON",
                                style = MaterialTheme.typography.labelSmall,
                                color = TertiaryText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    maxLines = 2,
                )
            }
            if (enabled) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = TertiaryText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MenuRowDivider() {
    HorizontalDivider(color = BorderSubtle)
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape   = RoundedCornerShape(12.dp),
        border  = BorderStroke(1.dp, StatusDanger.softBg),
        colors  = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger.solid),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Sign Out", style = MaterialTheme.typography.labelLarge)
    }
}
