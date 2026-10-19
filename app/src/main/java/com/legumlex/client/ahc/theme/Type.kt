package com.legumlex.client.ahc.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.legumlex.client.R

/* ============================================================================
 *  Typography — Mylex's: Inter for everything people read, the system
 *  monospace for reference numbers and codes (CR-110-2023, AHC/2026/72362),
 *  as the portal sets them. One family, weights doing the work: SemiBold
 *  headings, Medium labels, Regular body.
 *
 *  Inter ships as static per-weight TTFs in res/font, not through the Google
 *  Fonts downloadable provider, which falls back to the system font on devices
 *  without Play Services.
 * ========================================================================== */

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Light),               // no lighter static; nearest
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Reference numbers, case numbers, codes. */
val Mono = FontFamily.Monospace

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/* Semantic styles consumed directly by the AHC composables. Names map to the
 * HTML classes they replace, so the visual hierarchy is 1:1. Each carries its
 * colour, so each is read in composition (light or dark). */
object LlText {

    // --- Headings ---
    val appBarTitle: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(   // .appbar-title
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, letterSpacing = (-0.01).em, lineHeight = 24.sp,
        color = LlColor.Ink,
    )
    val heroVerdict: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(   // .vhero-vt
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, color = LlColor.Ink,
    )
    val emptyTitle: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(    // .empty-h / .cd-over-t
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, color = LlColor.Ink,
    )
    val errorTitle: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(    // .fserr-h
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, letterSpacing = (-0.01).em, lineHeight = 26.sp,
        color = LlColor.Ink,
    )
    val noteBody: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(      // .anote-body (advocate's note)
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, color = LlColor.Ink,
    )

    // --- UI ---
    val eyebrow: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(       // .appbar-eyebrow / kicker
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.08.em, color = LlColor.InkMuted,
    )
    val sectionHeader: TextStyle @Composable @ReadOnlyComposable get() = TextStyle( // .dsec-t
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, letterSpacing = 0.08.em, color = LlColor.InkMuted,
    )
    val bodyTitle: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(     // .finding-t / .advc-n / kv strong
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, color = LlColor.Ink,
    )
    val body: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(          // .finding-x / .cd-sub / generic body
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp, color = LlColor.InkMuted,
    )
    val bodyStrong: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(    // .hero-status / verdict-t
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, color = LlColor.Ink,
    )
    val label: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(         // .kv-k / step-t / small body
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, color = LlColor.InkMuted,
    )
    val button: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(        // .btn
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )

    // --- Reference numbers, codes, stamps ---
    val mono: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(          // .cd-eta / .att-m / .refno
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, color = LlColor.InkMuted,
    )
    val monoStamp: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(     // .tl-m / docview-pg
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, color = LlColor.InkFaint,
    )
    val heroScore: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(     // .vscore (the 0–100 number)
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, letterSpacing = (-0.02).em, lineHeight = 40.sp,
    )
    val countdownNum: TextStyle @Composable @ReadOnlyComposable get() = TextStyle(  // .cd-num
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, letterSpacing = (-0.02).em, lineHeight = 40.sp,
        color = LlColor.Ink,
    )
}

// NOTE: the M3 Typography (LlTypography) lives in Theme.kt, NOT here. object
// LlText reads the font families (top-level vals in this file), so this file
// must not reference LlText back — that static-init cycle NPEs whichever side
// loads first.
