package com.legumlex.client.feature.desk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.legumlex.client.ui.theme.LexColors

/*
 * Which colour a desk message's text takes (REQ-017 017.15): from the surface it sits on, never a fixed white or black. Bold words and the highlighted
 * quote were drawn in white, which is right on a dark page and invisible on the light one. Every text colour in the desk now comes from here (or from
 * the same palette through D), and the choice is tested for both themes.
 */

/** What the text sits on. */
enum class DeskSurface { Page, Raised, Bubble, Note, Warn, Accent }

/** What the text is. Strong (bold words, the highlighted quote) is not a different colour from Body: it is the same ink, set heavier. */
enum class DeskRole { Body, Strong, Muted, Error }

/** The surface's fill. Warn is a tint of the danger colour over the page. */
fun deskFill(c: LexColors, s: DeskSurface): Color = when (s) {
    DeskSurface.Page -> c.background
    DeskSurface.Raised -> c.card
    DeskSurface.Bubble -> c.muted
    DeskSurface.Note -> c.secondary
    DeskSurface.Warn -> c.danger.solid.copy(alpha = 0.1f).compositeOver(c.background)
    DeskSurface.Accent -> c.primary
}

/** The text colour for [role] on [surface]: the foreground pairs with every neutral surface, the accent's own foreground with the accent. */
fun deskInk(c: LexColors, s: DeskSurface, role: DeskRole): Color = when (s) {
    DeskSurface.Accent -> c.primaryForeground
    DeskSurface.Warn -> if (role == DeskRole.Muted) c.mutedForeground else c.foreground
    else -> when (role) {
        DeskRole.Body, DeskRole.Strong -> c.foreground
        DeskRole.Muted -> c.mutedForeground
        DeskRole.Error -> c.danger.solid
    }
}

/** WCAG contrast ratio of two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
