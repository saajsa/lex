package com.legumlex.client.ui.theme

import androidx.compose.ui.graphics.Color

// Bizlink neutrals — the spine
val Zinc50  = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

// Semantic — light theme (v1 only; dark deferred to v1.1)
val BodyBg        = Color(0xFFFCFCFC)
val Surface1      = Color(0xFFFFFFFF)
val Surface2      = Color(0xFFFAFAFA)
val PrimaryText   = Zinc900
val SecondaryText = Zinc600
val TertiaryText  = Zinc400
val InverseText   = Zinc50
val BorderColor   = Zinc200
val BorderSubtle  = Zinc100
val BorderStrong  = Zinc300
val BorderActive  = Zinc900
val AccentInk     = Zinc900
val FocusRing     = Color(0x2E18181B)

// Status accents — badges only, never decoration.
data class StatusPalette(val solid: Color, val softBg: Color, val softText: Color)

val StatusOngoing   = StatusPalette(Color(0xFF2563EB), Color(0xFFDBEAFE), Color(0xFF1E40AF))
val StatusSettled   = StatusPalette(Color(0xFF16A34A), Color(0xFFD1FAE5), Color(0xFF065F46))
val StatusPending   = StatusPalette(Color(0xFFD97706), Color(0xFFFEF3C7), Color(0xFF92400E))
val StatusClosed    = StatusPalette(Color(0xFF71717A), Color(0xFFEEF0F3), Color(0xFF18181B))
val StatusAdjourned = StatusPalette(Color(0xFF7C3AED), Color(0xFFEDE9FE), Color(0xFF5B21B6))
val StatusDanger    = StatusPalette(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFFB91C1C))
