package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex

/**
 * Upgrade (REQ-008 round 3, canvas 5b): Coming Soon. An invitation to associate with Nyay & Co. It collects
 * nothing: no input, no button that does anything. The words are the web's board/upgrade, as approved.
 */
@Composable
fun UpgradeScreen() {
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(eyebrow = "Upgrade", title = "Associate With Nyay & Co.")
        Text(
            "COMING SOON", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            "Nyay & Co. is the law firm Legum Lex works with. Counsel on Legum Lex will be able to apply here to become associate advocates of the firm and take on its matters.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground,
        )
        LexCard {
            UpgradeRow(Icons.Outlined.AccountBalance, "Work With The Firm", "Appear in the firm's matters in your courts, as its associate.")
            HorizontalDivider(color = Lex.colors.border)
            UpgradeRow(Icons.Outlined.CalendarMonth, "One Diary", "The firm's matters join your own cases on this cause list.")
            HorizontalDivider(color = Lex.colors.border)
            UpgradeRow(Icons.Outlined.VerifiedUser, "Your Practice Stays Yours", "Your own clients and cases are not shared with the firm.")
        }
        Text("Applications are not open yet. Nothing is collected on this screen.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
    }
}

@Composable
private fun UpgradeRow(icon: ImageVector, head: String, text: String) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Lex.colors.foreground, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(head, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
        }
    }
}
