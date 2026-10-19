package com.legumlex.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Client app uses softer radii than the lawyer-facing Bizlink CRM.
val LegumShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // chip outlines
    small      = RoundedCornerShape(10.dp),   // inline tiles, small chips
    medium     = RoundedCornerShape(14.dp),   // standard card, button, input
    large      = RoundedCornerShape(20.dp),   // hero cards, bottom sheets
    extraLarge = RoundedCornerShape(24.dp),   // splash card, app icon
)

val PillShape = RoundedCornerShape(percent = 50)
