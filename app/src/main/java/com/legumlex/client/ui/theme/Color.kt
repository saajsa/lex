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
)

val LightLexColors = LexColors(
    isDark              = false,
    background          = Color(0xFFFFFFFF),
    card                = Color(0xFFFFFFFF),
    foreground          = Zinc950,
    secondaryForeground = Zinc700,
    mutedForeground     = Zinc500,
    faint               = Zinc400,
    muted               = Zinc100,
    secondary           = Zinc100,
    border              = Color(0xFFECECEE), // between zinc-100 and zinc-200
    input               = Zinc200,
    ring                = Zinc400,
    primary             = Zinc950,
    primaryForeground   = Color(0xFFFFFFFF),
    success = StatusPalette(Color(0xFF22C55E), Color(0xFFDCFCE7), Color(0xFF166534)),
    warning = StatusPalette(Color(0xFFEAB308), Color(0xFFFEF9C3), Color(0xFF854D0E)),
    info    = StatusPalette(Color(0xFF8B5CF6), Color(0xFFEDE9FE), Color(0xFF5B21B6)),
    blue    = StatusPalette(Color(0xFF3B82F6), Color(0xFFDBEAFE), Color(0xFF1E40AF)),
    danger  = StatusPalette(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFF991B1B)),
    neutral = StatusPalette(Zinc500, Zinc100, Zinc900),
)

// Dark: layered, not inverted. Mylex's own dark (zinc-950 page and cards,
// white text, a white primary button) read on a phone as a negative of the
// light theme. Here the page is charcoal and each surface above it a step
// lighter, so depth is carried by tone, as light mode carries it by hairline:
//   page #141417 · card #1C1C20 · inset #232328 · chip/track #2A2A30
// Text is off-white rather than white, the primary button a soft light grey,
// and the status colours are 400-shade text on a faint tint of their own hue.
val DarkLexColors = LexColors(
    isDark              = true,
    background          = Color(0xFF141417),
    card                = Color(0xFF1C1C20),
    foreground          = Color(0xFFE8E8EB),
    secondaryForeground = Color(0xFFC2C2C8),
    mutedForeground     = Color(0xFF8E8E96),
    faint               = Color(0xFF63636B),
    muted               = Color(0xFF232328),
    secondary           = Color(0xFF2A2A30),
    border              = Color(0xFF2C2C32),
    input               = Color(0xFF3A3A41),
    ring                = Color(0xFF52525B),
    primary             = Color(0xFFE4E4E7),
    primaryForeground   = Color(0xFF18181B),
    success = StatusPalette(Color(0xFF22C55E), Color(0x2922C55E), Color(0xFF4ADE80)),
    warning = StatusPalette(Color(0xFFEAB308), Color(0x29EAB308), Color(0xFFFACC15)),
    info    = StatusPalette(Color(0xFF8B5CF6), Color(0x298B5CF6), Color(0xFFA78BFA)),
    blue    = StatusPalette(Color(0xFF3B82F6), Color(0x293B82F6), Color(0xFF60A5FA)),
    danger  = StatusPalette(Color(0xFFEF4444), Color(0x29EF4444), Color(0xFFF87171)),
    neutral = StatusPalette(Color(0xFF8E8E96), Color(0xFF2A2A30), Color(0xFFD4D4D8)),
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
