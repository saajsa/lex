package com.legumlex.client.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.SecondaryText

// Placeholder for the 4-step Registration flow (screens 04-07).
// Built in a later pass once the camera + KYC API surface is in place.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPlaceholderScreen(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    // Back navigation lives in the app-wide header.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.s5),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Registration",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s3))
            Text(
                "The 4-step verification flow will be built once the KYC backend is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s6))
            LegumSecondaryButton(
                text = "Back to sign in",
                onClick = onBack,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}
