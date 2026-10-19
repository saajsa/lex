package com.legumlex.client.feature.healthchecks.funnel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.SourceSans

/**
 * Final card of the onboarding flow — quiet success state + a single
 * "Start health check" card. Tap → enters the funnel; "Maybe later" → home.
 */
@Composable
fun OnboardingAhcHandoffScreen(
    userFirstName: String?,
    userEmail: String?,
    onStartHealthCheck: () -> Unit,
    onSkipToHome: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Surface1)) {
        // Success area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "You're all set${userFirstName?.let { ", $it" } ?: ""}.",
                color = PrimaryText,
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (userEmail.isNullOrBlank()) "Your account is active."
                       else "Your account is active. A welcome email has been sent to $userEmail.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        // Handoff card
        Surface(
            color = Surface2,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                EyebrowText("New — Legal Intelligence")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Have an agreement worrying you?",
                    color = PrimaryText,
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = (-0.25).sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Let's run an agreement check now. Upload any contract — we'll flag the risks and your advocate reviews it.",
                    color = SecondaryText,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(16.dp))
                FunnelPrimaryButton(
                    label = "Start agreement check",
                    onClick = onStartHealthCheck,
                    icon = Icons.Outlined.HealthAndSafety,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Maybe later — take me home",
                    color = SecondaryText,
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onSkipToHome)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
