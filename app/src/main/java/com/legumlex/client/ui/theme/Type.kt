package com.legumlex.client.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.SourceSans

// This Material typography now uses the single bundled type system: Source Sans 3
// for UI/body/labels, Geist Mono for numerics. (It previously declared Space
// Grotesk / Inter / JetBrains Mono via the downloadable Google Fonts provider,
// which fell back to the system font without Play Services and was too wide.)

// Mapping: handoff style → Material 3 typography slot
// Display     → displayMedium (36sp, used sparingly)
// H1          → headlineMedium (28sp)
// H2          → headlineSmall (22sp)
// H3          → titleMedium (16sp)
// Body        → bodyMedium (14sp)
// Small       → bodySmall (12.5sp)
// Eyebrow     → labelSmall (10sp, uppercase + tracked)
val LegumTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-1.08).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.44).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.16).sp
    ),
    titleSmall = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp, lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.5.sp
    ),
    labelLarge = TextStyle(
        // Button text — uppercase tracked at use sites where needed
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SourceSans, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.32.sp
    ),
    labelSmall = TextStyle(
        // Eyebrow — apply .uppercase() at use site
        fontFamily = SourceSans, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp
    ),
)

val MonoStyle = TextStyle(
    fontFamily = SourceSans, fontWeight = FontWeight.Medium,
    fontSize = 12.sp, letterSpacing = 0.24.sp
)
