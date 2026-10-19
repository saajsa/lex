package com.legumlex.client.ahc.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.StatusPalette

/* ============================================================================
 *  The client app's original names for its colours, now read from the Mylex
 *  palette (ui/theme/Color.kt), light or dark as the theme is.
 *
 *  NEUTRAL SPINE   ink on white — Mylex's zinc
 *  STATUS          claret → red, verdant → green, amber → yellow,
 *                  slate → blue, violet → violet (Metronic's badge colours)
 *                  each = { solid (text), mid (dot/stripe), soft (tint), border }
 *  Colour carries MEANING only (risk, status). The brand accent is monochrome:
 *  use Ink, never Claret, for emphasis that is not a warning.
 * ========================================================================== */

object LlColor {

    // ---- Neutral spine -----------------------------------------------------
    val Ink: Color          @Composable @ReadOnlyComposable get() = Lex.colors.foreground
    val InkMuted: Color     @Composable @ReadOnlyComposable get() = Lex.colors.mutedForeground
    val InkFaint: Color     @Composable @ReadOnlyComposable get() = Lex.colors.faint

    val Paper: Color        @Composable @ReadOnlyComposable get() = Lex.colors.background
    val Paper1: Color       @Composable @ReadOnlyComposable get() = Lex.colors.muted
    val Paper2: Color       @Composable @ReadOnlyComposable get() = Lex.colors.secondary

    val Surface: Color      @Composable @ReadOnlyComposable get() = Lex.colors.card
    val Border: Color       @Composable @ReadOnlyComposable get() = Lex.colors.border
    val BorderStrong: Color @Composable @ReadOnlyComposable get() = Lex.colors.input

    val Stage: Color        @Composable @ReadOnlyComposable get() = Lex.colors.muted
    val DeviceBezel         = Color(0xFF100F0E) // phone frame in previews

    // ---- Claret → red (HIGH risk, overdue) ---------------------------------
    val Claret: Color       @Composable @ReadOnlyComposable get() = Lex.colors.danger.softText
    val ClaretMid: Color    @Composable @ReadOnlyComposable get() = Lex.colors.danger.solid
    val ClaretSoft: Color   @Composable @ReadOnlyComposable get() = Lex.colors.danger.softBg
    val ClaretBorder: Color @Composable @ReadOnlyComposable get() = Lex.colors.danger.edge

    // ---- Verdant → green (settled, LOW risk) -------------------------------
    val Verdant: Color      @Composable @ReadOnlyComposable get() = Lex.colors.success.softText
    val VerdantMid: Color   @Composable @ReadOnlyComposable get() = Lex.colors.success.solid
    val VerdantSoft: Color  @Composable @ReadOnlyComposable get() = Lex.colors.success.softBg
    val VerdantBorder: Color @Composable @ReadOnlyComposable get() = Lex.colors.success.edge

    // ---- Amber → yellow (waiting, MEDIUM risk) -----------------------------
    val Amber: Color        @Composable @ReadOnlyComposable get() = Lex.colors.warning.softText
    val AmberMid: Color     @Composable @ReadOnlyComposable get() = Lex.colors.warning.solid
    val AmberSoft: Color    @Composable @ReadOnlyComposable get() = Lex.colors.warning.softBg
    val AmberBorder: Color  @Composable @ReadOnlyComposable get() = Lex.colors.warning.edge

    // ---- Slate → blue (ongoing) --------------------------------------------
    val Slate: Color        @Composable @ReadOnlyComposable get() = Lex.colors.blue.softText
    val SlateMid: Color     @Composable @ReadOnlyComposable get() = Lex.colors.blue.solid
    val SlateSoft: Color    @Composable @ReadOnlyComposable get() = Lex.colors.blue.softBg
    val SlateBorder: Color  @Composable @ReadOnlyComposable get() = Lex.colors.blue.edge

    // ---- Violet (adjourned) ------------------------------------------------
    val Violet: Color       @Composable @ReadOnlyComposable get() = Lex.colors.info.softText
    val VioletSoft: Color   @Composable @ReadOnlyComposable get() = Lex.colors.info.softBg
}

/** A hairline in the status colour, for a tinted panel's edge. */
val StatusPalette.edge: Color get() = solid.copy(alpha = 0.25f)

/* Risk severity → palette mapping (single source of truth). -----------------
 * NOTE on direction: the visual system displays `risk_score` as a 0–100
 * HEALTH score where HIGHER = SAFER (e.g. 88/100 == "Low risk"). The backend
 * `risk_level` enum is authoritative for COLOR; the number is shown as-is.
 * If your backend instead sends score as risk MAGNITUDE (higher = worse),
 * only `RiskLevel.fromMagnitude` below changes — the palette mapping stays. */
enum class RiskLevel(
    val label: String,
    private val tone: (LexColors) -> StatusPalette,
) {
    LOW     ("Low risk",      { it.success }),
    MEDIUM  ("Moderate risk", { it.warning }),
    HIGH    ("High risk",     { it.danger }),
    CRITICAL("Critical risk", { it.danger });

    val solid: Color  @Composable @ReadOnlyComposable get() = tone(Lex.colors).softText // big number + verdict icon
    val mid: Color    @Composable @ReadOnlyComposable get() = tone(Lex.colors).solid
    val soft: Color   @Composable @ReadOnlyComposable get() = tone(Lex.colors).softBg   // hero background tint
    val border: Color @Composable @ReadOnlyComposable get() = tone(Lex.colors).edge

    companion object {
        /** Backend contract: magnitude where higher == worse. */
        fun fromMagnitude(score: Int): RiskLevel = when {
            score <= 30 -> LOW
            score <= 60 -> MEDIUM
            score <= 80 -> HIGH
            else        -> CRITICAL
        }
    }
}

/* Finding severity → stripe color (Key risks / Missing protections rows). */
enum class FindingSeverity(private val tone: (LexColors) -> StatusPalette) {
    CRITICAL({ it.danger }),
    WARN    ({ it.warning }),
    OK      ({ it.success });

    val stripe: Color @Composable @ReadOnlyComposable get() = tone(Lex.colors).solid
}

/* ---- Spacing / radii / sizing (mirror the HTML scale) -------------------- */
object LlDimen {
    val ScreenPad        = 16.dp   // .dsec horizontal padding
    val CardRadius       = 12.dp   // .card / .dcard — kt-card rounded-xl
    val ButtonRadius     = 8.dp    // .btn — rounded-lg
    val ChipRadius       = 6.dp    // .chip — kt-badge rounded-md
    val InsetRadius      = 6.dp    // .tagchip / btn-sm
    val SectionGap       = 14.dp   // gap between .dsec blocks
    val SectionGapTight  = 11.dp   // .dsec.tight
    val CardPad          = 15.dp   // .dcard-b
    val HairlineWidth    = 1.dp
    val HeroStripeWidth  = 4.dp    // .hero::before
    val RingSize         = 172.dp  // .cd-ring
    val RingThickness    = 13.dp   // .cd-ring inset
    val TouchTarget      = 44.dp   // minimum hit target
}

object LlShape {
    val Card   = RoundedCornerShape(LlDimen.CardRadius)
    val Button = RoundedCornerShape(LlDimen.ButtonRadius)
    val Chip   = RoundedCornerShape(LlDimen.ChipRadius)
    val Inset  = RoundedCornerShape(LlDimen.InsetRadius)
}
