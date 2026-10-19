package com.legumlex.client.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ahc.theme.Mono

// Mylex's type, on a phone: Inter throughout, weights doing the work. Page
// titles are Medium (the portal's "Cases", "Good afternoon"), card titles and
// buttons SemiBold/Medium, body Regular; eyebrows are small, Medium, tracked
// and uppercased at the use site ("YOUR WORK WITH US").
//
// Slot → role
// displayMedium   36  big figures (a stat card's number)
// headlineMedium  26  page title
// headlineSmall   20  section/screen title
// titleMedium     16  card title
// titleSmall      14  row title
// bodyLarge       15  reading text
// bodyMedium      14  body
// bodySmall       12.5 descriptors, captions
// labelLarge      14  buttons
// labelMedium     12  badges, field labels
// labelSmall      11  eyebrow (uppercase at use site)
val LegumTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.72).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.52).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.16).sp
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.88.sp
    ),
)

/** Reference numbers and codes, as Mylex sets them. */
val MonoStyle = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
)
