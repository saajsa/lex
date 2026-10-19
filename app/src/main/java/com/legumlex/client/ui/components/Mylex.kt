package com.legumlex.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.theme.DarkLexColors
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LocalLexColors
import com.legumlex.client.ui.theme.StatusPalette

/* ============================================================================
 *  The Mylex portal's building blocks, for the app. Each names the Metronic
 *  component it stands for, so a screen ported from a Mylex view can be read
 *  side by side with it:
 *
 *    PageHeader      the toolbar: an eyebrow ("LITIGATION") over the title
 *    LexCard         kt-card — hairline border, 12dp, optional header row
 *    StatCard        the four figures over each list ("Matters On Record")
 *    WorkspaceTile   the workspace entrance (Litigation / Projects / Registry)
 *    SegmentedToggle kt-toggle-group ("All | Active")
 *    KeyValueRow     a profile/detail row: label left, value right
 *    LexEmpty        an empty state that names its own subject
 *
 *  Labels are Title Case; descriptors are sentences that name their subject.
 * ========================================================================== */

/** Small, tracked, uppercase: "YOUR WORK WITH US". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Lex.colors.mutedForeground) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/** The page's eyebrow and title, with room for actions on the right. */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Lex.colors.foreground)
        }
        actions?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
    }
}

/**
 * kt-card. `title` draws the card header (min 56dp, hairline under it) with an
 * optional trailing slot; the content is unpadded so a list can run edge to
 * edge — use [LexCardBody] for padded content.
 */
@Composable
fun LexCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    container: Color = Lex.colors.card,
    borderColor: Color = Lex.colors.border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Lex.colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke(this)
                }
                HorizontalDivider(color = borderColor)
            }
            content()
        }
    }
}

/** kt-card-content: the card's padded body. */
@Composable
fun LexCardBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(16.dp), content = content)
}

/**
 * One of the figures over a list: its label, the number with an optional
 * status chip beside it, and under a hairline a sentence saying what it counts.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Lex.colors.foreground,
    chip: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    LexCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
                if (chip != null) { Spacer(Modifier.width(8.dp)); chip() }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Lex.colors.border)
            Spacer(Modifier.height(10.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A status chip with a leading icon, as the stat cards carry ("5 With The Firm"). */
@Composable
fun StatusChip(label: String, palette: StatusPalette, icon: ImageVector? = null) {
    Surface(color = palette.softBg, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = palette.softText, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.softText)
        }
    }
}

/** The colours of one workspace entrance, light and dark, as Mylex sets them. */
private data class TileLook(val surface: Color, val border: Color, val gate: Color, val dark: Boolean)

// One look for every entrance, from the palette: the card a step off the page,
// a hairline, ink for the accent (Oct 2026: two hues at most, for status).
@Composable
private fun tileLook(@Suppress("UNUSED_PARAMETER") ws: Workspace): TileLook =
    TileLook(Lex.colors.card, Lex.colors.border, Lex.colors.foreground, false)

/**
 * A workspace's entrance: eyebrow, name, what lives there, Enter, and its
 * mark. One dark, two light, so no two read as the same card. A workspace with
 * nothing in it keeps its anatomy, dashed, with no way in.
 */
@Composable
fun WorkspaceTile(
    workspace: Workspace,
    title: String,
    description: String,
    onEnter: (() -> Unit)?,
    modifier: Modifier = Modifier,
    emptyNote: String = "Nothing here yet.",
    eyebrow: String = "Your Work With Us",
    cta: String = "Enter",
) {
    val look = tileLook(workspace)
    val offered = onEnter != null
    val colors = if (look.dark) DarkLexColors else LocalLexColors.current
    CompositionLocalProvider(LocalLexColors provides colors) {
        Box(
            modifier
                .fillMaxWidth()
                // Every entrance the same height, whatever its words; taller
                // only when a large font size needs the room.
                .heightIn(min = WorkspaceTileHeight)
                .clip(MaterialTheme.shapes.large)
                .background(look.surface)
                .border(1.dp, if (offered) look.border else Lex.colors.input, MaterialTheme.shapes.large)
                .then(if (offered) Modifier.clickable(onClick = onEnter!!) else Modifier),
        ) {
            // The accent runs the head of the card.
            Box(Modifier.width(72.dp).height(3.dp).background(if (offered) look.gate else look.gate.copy(alpha = 0.45f)))
            Row(Modifier.fillMaxWidth().heightIn(min = WorkspaceTileHeight).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(eyebrow)
                    Spacer(Modifier.height(6.dp))
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Lex.colors.foreground)
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    if (offered) {
                        // Consult's button is the desk's gold; the workspaces' the theme's ink.
                        val desk = workspace == Workspace.Consult
                        val ctaBg = Lex.colors.primary
                        val ctaFg = Lex.colors.primaryForeground
                        Surface(color = ctaBg, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cta, style = MaterialTheme.typography.labelLarge, color = ctaFg)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = ctaFg, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Text(emptyNote, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (workspace == Workspace.Consult) DeskMark(look.gate, Modifier.size(width = 78.dp, height = 89.dp))
                else GateArt(
                    workspace = workspace,
                    ink = if (offered) look.gate else Lex.colors.mutedForeground,
                    surface = look.surface,
                    modifier = Modifier.size(width = 78.dp, height = 89.dp),
                )
            }
        }
    }
}

/** The height every workspace entrance shares (its minimum, at large font sizes). */
val WorkspaceTileHeight = 188.dp

/** The Legum Lex desk's mark: a gold ">_" tile, with the site's AI tag. */
@Composable
private fun DeskMark(gold: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(gold), contentAlignment = Alignment.Center) {
            Text(">_", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = Lex.colors.primaryForeground)
        }
        Text("AI", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Lex.colors.foreground,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(5.dp)).background(Lex.colors.card)
                .border(1.dp, Lex.colors.border, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

/** kt-toggle-group: "All | Active". */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, Lex.colors.input, MaterialTheme.shapes.medium),
    ) {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.input))
            val on = value == selected
            Box(
                Modifier
                    .background(if (on) Lex.colors.muted else Lex.colors.card)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Lex.colors.foreground else Lex.colors.secondaryForeground,
                )
            }
        }
    }
}

/** A detail row: the label, then the value; an optional trailing mark. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground, modifier = Modifier.weight(0.42f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) Mono else null,
            color = Lex.colors.foreground,
            modifier = Modifier.weight(0.58f),
        )
        trailing?.invoke()
    }
}

/** An empty state whose heading says what is empty ("No Hearings Scheduled"). */
@Composable
fun LexEmpty(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** A way out of the empty or failed state: "Try Again", "Start One". */
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon?.let {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted), contentAlignment = Alignment.Center) {
                Icon(it, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.let { Spacer(Modifier.height(8.dp)); it() }
    }
}
