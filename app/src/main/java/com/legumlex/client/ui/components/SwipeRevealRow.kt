package com.legumlex.client.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One revealed action behind a [SwipeRevealRow]. */
data class RevealAction(
    val label: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
    val onClick: () -> Unit,
)

/**
 * A card whose [content] can be swiped left to reveal a strip of [actions]
 * behind it, each in the card's own shape. Tapping an action runs it and
 * snaps the card shut. [actionsAtTop] puts them at the head of a tall card
 * instead of its middle. The staff app's row of the same name, on Mylex shapes.
 */
@Composable
fun SwipeRevealRow(
    actions: List<RevealAction>,
    modifier: Modifier = Modifier,
    actionsAtTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = 76.dp
    val revealPx = with(density) { (actionWidth * actions.size).toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxWidth()) {
        // Drawn only once the card moves, so nothing shows round its corners at rest.
        if (offsetX.value < 0f) Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { action ->
                Column(
                    Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.large)
                        .background(action.bg)
                        .clickable {
                            action.onClick()
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    // A tall row keeps its actions by its head, where the eye is.
                    verticalArrangement = if (actionsAtTop) Arrangement.Top else Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (actionsAtTop) Spacer(Modifier.height(28.dp))
                    Icon(action.icon, null, tint = action.fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(action.label, style = MaterialTheme.typography.labelSmall, color = action.fg)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(actions.size) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + drag).coerceIn(-revealPx, 0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                                offsetX.animateTo(target)
                            }
                        },
                    )
                },
        ) { content() }
    }
}
