package com.legumlex.client.ahc.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* ============================================================================
 *  Legum Lex · "Bizlink-paper" design tokens (LIGHT THEME ONLY · Material 3)
 *  ---------------------------------------------------------------------------
 *  This is the claret / serif / warm-paper system used across the client app
 *  (see `Agreement Check Workflow.html`). Every colored element in the AHC
 *  screen references a token below — never a raw hex literal at the call site.
 *
 *  NEUTRAL SPINE        warm zinc/stone, ink-on-paper
 *  STATUS PALETTES      claret / verdant / amber / slate / violet
 *                       each = { solid, mid, soft }  (soft = tint background)
 *  Color carries MEANING only (risk severity, status) — body stays ink-on-paper.
 * ========================================================================== */

object LlColor {

    // ---- Neutral spine -----------------------------------------------------
    val Ink          = Color(0xFF1C1917) // primary text / --ink / AccentInk
    val InkMuted     = Color(0xFF57534E) // secondary text / --ink-m
    val InkFaint     = Color(0xFFA8A29E) // tertiary text, hairlines / --ink-f

    val Paper        = Color(0xFFFAF9F7) // body / screen background / --paper
    val Paper1       = Color(0xFFF5F2EE) // hover / inset fill / --p1
    val Paper2       = Color(0xFFEDE9E4) // chip / track / divider fill / --p2

    val Surface      = Color(0xFFFFFFFF) // card surface (white on paper)
    val Border       = Color(0xFFE7E2DB) // 1px card border / --b
    val BorderStrong = Color(0xFFD4CDC5) // stronger hairline / --bs

    val Stage        = Color(0xFFE7E3DB) // canvas behind devices (previews only)
    val DeviceBezel  = Color(0xFF100F0E) // phone frame in previews

    // ---- Claret (brand / Agreement Check / HIGH risk) ----------------------
    val Claret       = Color(0xFF7D1930) // --claret  (solid)
    val ClaretMid    = Color(0xFFBE2E4A) // --claret-m
    val ClaretSoft   = Color(0xFFFDF2F4) // --claret-s (tint bg)
    val ClaretBorder = Color(0xFFF4D9DF) // hero hairline on claret-soft

    // ---- Verdant (settled / positive / LOW risk) ---------------------------
    val Verdant      = Color(0xFF1A5C3A) // --verdant
    val VerdantMid   = Color(0xFF22864A) // --verdant-m
    val VerdantSoft  = Color(0xFFF0FAF4) // --verdant-s
    val VerdantBorder= Color(0xFFCDEDDB) // hero / release hairline

    // ---- Amber (pending / overdue / MEDIUM risk) ---------------------------
    val Amber        = Color(0xFF92400E) // --amber  (text/solid)
    val AmberMid     = Color(0xFFB45309) // --amber-m
    val AmberSoft    = Color(0xFFFFFBEB) // --amber-s
    val AmberBorder  = Color(0xFFF0E2C4) // hero hairline on amber-soft

    // ---- Slate (ongoing / escalation redirect) -----------------------------
    val Slate        = Color(0xFF1E3A5F) // --slate
    val SlateMid     = Color(0xFF34598A) // --slate-m
    val SlateSoft    = Color(0xFFEFF4FB) // --slate-s
    val SlateBorder  = Color(0xFFD6E0EC) // strip / panel hairline

    // ---- Violet (adjourned — reserved, not used on this screen) ------------
    val Violet       = Color(0xFF4C1D95) // --violet
    val VioletSoft   = Color(0xFFF5F3FF) // --violet-s
}

/* Risk severity → palette mapping (single source of truth). -----------------
 * NOTE on direction: the visual system displays `risk_score` as a 0–100
 * HEALTH score where HIGHER = SAFER (e.g. 88/100 == "Low risk"). The backend
 * `risk_level` enum is authoritative for COLOR; the number is shown as-is.
 * If your backend instead sends score as risk MAGNITUDE (higher = worse),
 * only `RiskLevel.fromMagnitude` below changes — the palette mapping stays. */
enum class RiskLevel(
    val label: String,
    val solid: Color,   // big number + filled verdict icon
    val mid: Color,
    val soft: Color,    // hero background tint
    val border: Color,
) {
    LOW     ("Low risk",      LlColor.Verdant,   LlColor.VerdantMid, LlColor.VerdantSoft, LlColor.VerdantBorder),
    MEDIUM  ("Moderate risk", LlColor.Amber,     LlColor.AmberMid,   LlColor.AmberSoft,   LlColor.AmberBorder),
    HIGH    ("High risk",     LlColor.Claret,    LlColor.ClaretMid,  LlColor.ClaretSoft,  LlColor.ClaretBorder),
    CRITICAL("Critical risk", LlColor.Claret,    LlColor.ClaretMid,  LlColor.ClaretSoft,  LlColor.ClaretBorder);

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
enum class FindingSeverity(val stripe: Color) {
    CRITICAL(LlColor.Claret),     // .finding.crit
    WARN    (LlColor.AmberMid),   // .finding.warn
    OK      (LlColor.Verdant),    // .finding.ok
}

/* ---- Spacing / radii / sizing (mirror the HTML scale) -------------------- */
object LlDimen {
    val ScreenPad        = 16.dp   // .dsec horizontal padding
    val CardRadius       = 14.dp   // .card / .dcard
    val ButtonRadius     = 9.dp    // .btn
    val ChipRadius       = 7.dp    // .chip
    val InsetRadius      = 8.dp    // .tagchip / btn-sm
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
