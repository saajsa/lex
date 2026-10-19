package com.legumlex.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Mylex's radii (Tailwind, --radius 0.5rem): sm 4, md 6, lg 8, xl 12.
val LegumShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // rounded-sm: small badges
    small      = RoundedCornerShape(6.dp),    // rounded-md: badges, chips
    medium     = RoundedCornerShape(8.dp),    // rounded-lg: buttons, fields, inner tiles
    large      = RoundedCornerShape(12.dp),   // rounded-xl: cards (kt-card)
    extraLarge = RoundedCornerShape(12.dp),   // bottom sheets, dialogs
)

val PillShape = RoundedCornerShape(percent = 50)
