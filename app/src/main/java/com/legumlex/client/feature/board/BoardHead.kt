package com.legumlex.client.feature.board

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Lex

// REQ-038, owner 24 Nov 2026: the case page's head. The case number, then the case's title in the same face (a case is identified by its
// title); the next date as an element of its own; a smaller, animated switch.

/** The title's sizes: full size over 2 lines, then smaller over more lines; an ellipsis only at the smallest, after 4 lines. */
internal val TITLE_STEPS = listOf(30 to 2, 26 to 3, 22 to 3, 18 to 4)

/** The next step when the title overflowed at [i]; it stays at the last one (which then ellipsizes). */
internal fun nextTitleStep(i: Int, overflowed: Boolean): Int = if (overflowed && i < TITLE_STEPS.lastIndex) i + 1 else i

/** The case number, then the case title, both in the page heading's condensed face; the title steps down to fit long cause titles. */
@Composable
internal fun CaseHeading(c: BoardCase) {
    val heading = androidx.compose.ui.text.TextStyle(fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, color = Lex.colors.foreground)
    Column {
        Text(c.heading.uppercase(), style = heading.copy(fontSize = 22.sp, lineHeight = 24.sp, color = Lex.colors.secondaryForeground), maxLines = 1, overflow = TextOverflow.Ellipsis)
        var step by remember(c.id, c.title, c.petitioner, c.respondent) { mutableIntStateOf(0) }
        val (sp, lines) = TITLE_STEPS[step]
        Text(
            partiesText(c, Lex.colors.mutedForeground), style = heading.copy(fontSize = sp.sp, lineHeight = (sp + 2).sp),
            maxLines = lines, overflow = TextOverflow.Ellipsis,
            onTextLayout = { r -> step = nextTitleStep(step, r.hasVisualOverflow) },
        )
    }
}

/** The board's switch: 40×24, the thumb slides on a spring and the track's colour crossfades; 48dp to touch, read as a switch; no spring under reduced motion. */
internal const val TOGGLE_W = 40
internal const val TOGGLE_H = 24
internal const val TOGGLE_THUMB = 18

@Composable
internal fun LexToggle(checked: Boolean, enabled: Boolean, name: String, onChange: () -> Unit) {
    val reduced = rememberReducedMotion()
    val pad = (TOGGLE_H - TOGGLE_THUMB) / 2
    val x by animateDpAsState(if (checked) (TOGGLE_W - TOGGLE_THUMB - pad).dp else pad.dp,
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "thumb")
    val track by animateColorAsState(if (checked) Lex.colors.success.solid else Lex.colors.input, if (reduced) snap() else androidx.compose.animation.core.tween(180), label = "track")
    Box(
        Modifier.minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onChange() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(TOGGLE_W.dp, TOGGLE_H.dp).clip(CircleShape).background(if (enabled) track else track.copy(alpha = 0.5f))) {
            Box(Modifier.offset(x = x, y = pad.dp).size(TOGGLE_THUMB.dp).clip(CircleShape).background(Color.White))
        }
    }
}
