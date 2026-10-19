package com.legumlex.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* ============================================================================
 *  The Mylex palette (modules/mylex/_build/css/config.ktui.css), light + dark.
 *
 *  Monochrome: the accent is black on light and white on dark; emphasis is
 *  carried by contrast, not hue. Colour is kept for STATUS only — green done,
 *  yellow waiting, red overdue, violet/blue informational — because those carry
 *  meaning (an overdue deadline must not look like a link).
 *
 *  Screens read the semantic names below (PrimaryText, BorderColor, …). Each is
 *  a composable getter onto the palette the theme provides, so a screen follows
 *  the phone's light/dark setting without knowing about it.
 * ========================================================================== */

// Tailwind's zinc scale — the raw spine. Prefer the semantic names.
val Zinc50  = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc700 = Color(0xFF3F3F46)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

/**
 * A status colour, as Metronic's badges draw it: `solid` for a dot, stripe or
 * filled badge; `softBg` + `softText` for the tinted ("light") badge.
 */
@Immutable
data class StatusPalette(val solid: Color, val softBg: Color, val softText: Color)

@Immutable
data class LexColors(
    val isDark: Boolean,
    val background: Color,          // the page
    val card: Color,                // cards, sheets, fields
    val foreground: Color,          // headings and body text
    val secondaryForeground: Color, // body copy one step down
    val mutedForeground: Color,     // captions, descriptors, placeholders
    val faint: Color,               // disabled text, decorative icons
    val muted: Color,               // inset fills, table heads, hover
    val secondary: Color,           // grey chip, track
    val border: Color,              // hairlines
    val input: Color,               // field and outline-button borders
    val ring: Color,                // focus
    val primary: Color,             // the monochrome accent
    val primaryForeground: Color,
    val success: StatusPalette,
    val warning: StatusPalette,
    val info: StatusPalette,        // violet, as Metronic's "info"
    val blue: StatusPalette,
    val danger: StatusPalette,
    val neutral: StatusPalette,
    /** Header and bottom bar: a step deeper than the page, solid (no grain). */
    val chrome: Color = background,
    /** The hairline where the chrome meets the page. */
    val chromeRule: Color = border,
)

/* Warm greys, after the owner's reference (Oct 2026): a pale grey page with
 * charcoal type in light, a charcoal page with off-white type in dark, cards a
 * step lighter than the page, hairline rules between. Colour is held to two
 * muted hues for meaning only (red overdue, green done); waiting, information
 * and neutral read in grey, by their words. A faint grain lies over both
 * (Modifier.grain). */
val LightLexColors = LexColors(
    isDark              = false,
    background          = Color(0xFFE3E3E1),
    card                = Color(0xFFEBEBE9),
    foreground          = Color(0xFF383838),
    secondaryForeground = Color(0xFF4A4A49),
    mutedForeground     = Color(0xFF777775),
    faint               = Color(0xFFA2A29F),
    muted               = Color(0xFFD9D9D6),
    secondary           = Color(0xFFD5D5D2),
    border              = Color(0xFFCACAC7),
    input               = Color(0xFFBBBBB8),
    ring                = Color(0xFF959592),
    primary             = Color(0xFF383838),
    primaryForeground   = Color(0xFFEDEDEB),
    success = StatusPalette(Color(0xFF55785D), Color(0xFFD3DCD4), Color(0xFF3D5A44)),
    warning = StatusPalette(Color(0xFF5E5E5C), Color(0xFFD5D5D2), Color(0xFF383838)),
    info    = StatusPalette(Color(0xFF5E5E5C), Color(0xFFD5D5D2), Color(0xFF383838)),
    blue    = StatusPalette(Color(0xFF5E5E5C), Color(0xFFD5D5D2), Color(0xFF383838)),
    danger  = StatusPalette(Color(0xFFA9483F), Color(0xFFE6D3D0), Color(0xFF86352D)),
    neutral = StatusPalette(Color(0xFF777775), Color(0xFFD5D5D2), Color(0xFF383838)),
    chrome     = Color(0xFFD6D6D3),
    chromeRule = Color(0xFFBDBDBA),
)

val DarkLexColors = LexColors(
    isDark              = true,
    background          = Color(0xFF3A3A3A),
    card                = Color(0xFF424242),
    foreground          = Color(0xFFE4E4E2),
    secondaryForeground = Color(0xFFCDCDCB),
    mutedForeground     = Color(0xFF9E9E9C),
    faint               = Color(0xFF7B7B79),
    muted               = Color(0xFF474747),
    secondary           = Color(0xFF4B4B4B),
    border              = Color(0xFF535353),
    input               = Color(0xFF606060),
    ring                = Color(0xFF7B7B79),
    primary             = Color(0xFFE4E4E2),
    primaryForeground   = Color(0xFF383838),
    success = StatusPalette(Color(0xFF86A88E), Color(0xFF485449), Color(0xFFADC9B3)),
    warning = StatusPalette(Color(0xFFB2B2B0), Color(0xFF4B4B4B), Color(0xFFE4E4E2)),
    info    = StatusPalette(Color(0xFFB2B2B0), Color(0xFF4B4B4B), Color(0xFFE4E4E2)),
    blue    = StatusPalette(Color(0xFFB2B2B0), Color(0xFF4B4B4B), Color(0xFFE4E4E2)),
    danger  = StatusPalette(Color(0xFFD27268), Color(0xFF574341), Color(0xFFE59C93)),
    neutral = StatusPalette(Color(0xFF9E9E9C), Color(0xFF4B4B4B), Color(0xFFE4E4E2)),
    chrome     = Color(0xFF2F2F2F),
    chromeRule = Color(0xFF262626),
)

val LocalLexColors = staticCompositionLocalOf { LightLexColors }

/** The palette in force: `Lex.colors.foreground`, `Lex.colors.success`, … */
object Lex {
    val colors: LexColors
        @Composable @ReadOnlyComposable get() = LocalLexColors.current
}

// ---- Semantic names the screens use ----------------------------------------

val BodyBg: Color        @Composable @ReadOnlyComposable get() = Lex.colors.background
val Surface1: Color      @Composable @ReadOnlyComposable get() = Lex.colors.card
val Surface2: Color      @Composable @ReadOnlyComposable get() = Lex.colors.muted
val PrimaryText: Color   @Composable @ReadOnlyComposable get() = Lex.colors.foreground
val SecondaryText: Color @Composable @ReadOnlyComposable get() = Lex.colors.mutedForeground
val TertiaryText: Color  @Composable @ReadOnlyComposable get() = Lex.colors.faint
val InverseText: Color   @Composable @ReadOnlyComposable get() = Lex.colors.primaryForeground
val BorderColor: Color   @Composable @ReadOnlyComposable get() = Lex.colors.border
val BorderSubtle: Color  @Composable @ReadOnlyComposable get() = Lex.colors.border
val BorderStrong: Color  @Composable @ReadOnlyComposable get() = Lex.colors.input
val BorderActive: Color  @Composable @ReadOnlyComposable get() = Lex.colors.foreground
val AccentInk: Color     @Composable @ReadOnlyComposable get() = Lex.colors.primary
val FocusRing: Color     @Composable @ReadOnlyComposable get() = Lex.colors.ring

// Status — badges, dots and stripes only, never decoration.
val StatusOngoing: StatusPalette   @Composable @ReadOnlyComposable get() = Lex.colors.blue
val StatusSettled: StatusPalette   @Composable @ReadOnlyComposable get() = Lex.colors.success
val StatusPending: StatusPalette   @Composable @ReadOnlyComposable get() = Lex.colors.warning
val StatusClosed: StatusPalette    @Composable @ReadOnlyComposable get() = Lex.colors.neutral
val StatusAdjourned: StatusPalette @Composable @ReadOnlyComposable get() = Lex.colors.info
val StatusDanger: StatusPalette    @Composable @ReadOnlyComposable get() = Lex.colors.danger
