package com.legumlex.client.ahc.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.legumlex.client.R

/* ============================================================================
 *  Typography — TWO families, used with intent:
 *    Serif  · Lora           → titles / headings (traditional legal authority)
 *    Sans   · Source Sans 3  → everything else: UI, body, labels, numbers,
 *                              dates, reference codes (the former "mono" bits)
 *
 *  Lora + Source Sans 3 are the most-used Google Fonts on lawyer websites
 *  (Source Sans #7, Lora #11) — a legal-conventional, trusted pairing. There is
 *  deliberately NO monospace: law-firm typography doesn't use one, and the mono
 *  (Geist Mono) read as out-of-place on numbers/codes/dates. Those now use
 *  Source Sans so the whole app reads as one coherent, compact type system.
 *
 *  All families ship as STATIC per-weight TTFs bundled in res/font — fonts are
 *  NOT fetched through the Google Fonts downloadable provider, which silently
 *  falls back to the system font on devices without Play Services (that's why
 *  design fonts weren't showing before). Bundling makes them render everywhere.
 * ========================================================================== */

val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Light),               // no lighter static; nearest
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_medium, FontWeight.Medium),
    Font(R.font.lora_semibold, FontWeight.SemiBold),
    Font(R.font.lora_bold, FontWeight.Bold),
)

val SourceSans = FontFamily(
    Font(R.font.sourcesans3_light, FontWeight.Light),
    Font(R.font.sourcesans3_regular, FontWeight.Normal),
    Font(R.font.sourcesans3_medium, FontWeight.Medium),
    Font(R.font.sourcesans3_semibold, FontWeight.SemiBold),
    Font(R.font.sourcesans3_bold, FontWeight.Bold),
)

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/* Semantic styles consumed directly by the AHC composables. Names map to the
 * HTML classes they replace, so the visual hierarchy is 1:1. */
object LlText {

    // --- Serif display ---
    val appBarTitle = TextStyle(            // .appbar-title
        fontFamily = Lora, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, letterSpacing = (-0.02).em, lineHeight = 24.sp,
        color = LlColor.Ink,
    )
    val heroVerdict = TextStyle(            // .vhero-vt
        fontFamily = Lora, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, letterSpacing = (-0.01).em, color = LlColor.Ink,
    )
    val emptyTitle = TextStyle(             // .empty-h / .cd-over-t
        fontFamily = Lora, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, letterSpacing = (-0.01).em, color = LlColor.Ink,
    )
    val errorTitle = TextStyle(             // .fserr-h
        fontFamily = Lora, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, letterSpacing = (-0.015).em, lineHeight = 26.sp,
        color = LlColor.Ink,
    )
    val noteBody = TextStyle(               // .anote-body (advocate's note)
        fontFamily = Lora, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 23.sp, color = LlColor.Ink,
    )

    // --- Sans UI ---
    val eyebrow = TextStyle(                // .appbar-eyebrow / kicker
        fontFamily = SourceSans, fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp, letterSpacing = 0.12.em, color = LlColor.InkFaint,
    )
    val sectionHeader = TextStyle(          // .dsec-t
        fontFamily = SourceSans, fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp, letterSpacing = 0.1.em, color = LlColor.Ink,
    )
    val bodyTitle = TextStyle(              // .finding-t / .advc-n / kv strong
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, color = LlColor.Ink,
    )
    val body = TextStyle(                   // .finding-x / .cd-sub / generic body
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 19.sp, color = LlColor.InkMuted,
    )
    val bodyStrong = TextStyle(             // .hero-status / verdict-t
        fontFamily = SourceSans, fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp, color = LlColor.Ink,
    )
    val label = TextStyle(                  // .kv-k / step-t / small body
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, color = LlColor.InkMuted,
    )
    val button = TextStyle(                 // .btn
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp, letterSpacing = 0.05.em,
    )

    // --- Mono metadata ---
    val mono = TextStyle(                   // .cd-eta / .att-m / .refno
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp, letterSpacing = 0.03.em, color = LlColor.InkFaint,
    )
    val monoStamp = TextStyle(              // .tl-m / docview-pg
        fontFamily = SourceSans, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, color = LlColor.InkFaint,
    )
    val heroScore = TextStyle(              // .vscore (the 0–100 number)
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp, letterSpacing = (-0.03).em, lineHeight = 40.sp,
    )
    val countdownNum = TextStyle(           // .cd-num
        fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, letterSpacing = (-0.02).em, lineHeight = 40.sp,
        color = LlColor.Ink,
    )
}

// NOTE: the M3 Typography (LlTypography) lives in Theme.kt, NOT here. object
// LlText reads the font families (top-level vals in this file), so this file
// must not reference LlText back — that static-init cycle NPEs whichever side
// loads first.
