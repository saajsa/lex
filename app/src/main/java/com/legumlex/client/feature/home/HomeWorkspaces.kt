package com.legumlex.client.feature.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.components.Workspace
import com.legumlex.client.ui.components.WorkspaceTile
import com.legumlex.client.ui.theme.Lex

/* ============================================================================
 *  The top of Home, as Mylex's home has it (mylex/views/client/index.php):
 *  the date and the greeting with the two ways to start something, the three
 *  workspace entrances (swiped, not stacked), and the consultations' four figures.
 * ========================================================================== */

/**
 * As the owner's reference heads a day: the weekday large, in the page
 * titles' heavy condensed capitals, and beneath it one quiet line with the
 * date and the greeting — "TUESDAY" over "20 October 2026 · Hello, Saaj".
 */
@Composable
internal fun HomeHeader(dateLabel: String, greeting: String) {
    val today = java.time.LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE")).uppercase(),
            fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 0.3.sp, color = Lex.colors.foreground)
        Text("${today.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))} · $greeting",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The entrances: Consult (the desk), Litigation (dark), Projects (violet), Registry (teal),
 * swiped through one at a time rather than stacked, with the next card's edge
 * showing so it is plain there is more, and dots under them.
 */
@Composable
internal fun WorkspaceEntrances(
    ws: Workspaces,
    onConsult: () -> Unit,
    onLitigation: () -> Unit,
    onProjects: () -> Unit,
    onRegistry: () -> Unit,
) {
    val tiles = listOf(
        // Consult first: the Legum Lex desk, where anything new begins.
        Entrance(Workspace.Consult, "Consult", "Ask the advocates",
            "A new question, an agreement to read, or a notice to track.", onConsult, cta = "Ask"),
        Entrance(Workspace.Litigation, "Litigation", "Your matter, your cases with us",
            "Your matters before a court — hearings, orders and notices.", if ("litigation" in ws.offered) onLitigation else null),
        Entrance(Workspace.Projects, "Projects", "Your work with us",
            "Work we run for you, and its tasks.", if ("projects" in ws.offered) onProjects else null),
        Entrance(Workspace.Registry, "Registry", "Your agreements with us",
            "Your agreements and their deadlines.", if ("registry" in ws.offered) onRegistry else null),
    )
    val pager = rememberPagerState { tiles.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            // A card and most of the next, as the owner's reference shows them.
            contentPadding = PaddingValues(start = 16.dp, end = 130.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.CenterVertically,
        ) { i ->
            val t = tiles[i]
            // The card in front is full size and dark; those beside it shrink a little and go light.
            val off = kotlin.math.abs((pager.currentPage - i) + pager.currentPageOffsetFraction)
            val focus = (1f - off).coerceIn(0f, 1f)
            EntranceCard(t, focus, Modifier.graphicsLayer {
                val k = 0.9f + 0.1f * focus
                scaleX = k; scaleY = k
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            tiles.forEachIndexed { i, t ->
                val on = pager.currentPage == i
                val w by animateDpAsState(if (on) 18.dp else 6.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = w, height = 6.dp).clip(RoundedCornerShape(50))
                        .background(if (on) Lex.colors.foreground else Lex.colors.input)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        .semantics { contentDescription = t.title },
                )
            }
        }
    }
}

/**
 * An entrance as the owner's reference draws a card: tall and round, its mark
 * in a circle, the name, a line on what is inside, and a round arrow in. The
 * card in front ([focus] 1) is dark, the others light; a workspace not yet
 * opened for this client says so and has no arrow.
 */
@Composable
private fun EntranceCard(t: Entrance, focus: Float, modifier: Modifier = Modifier) {
    val front = focus > 0.5f
    val bg = androidx.compose.ui.graphics.lerp(Lex.colors.muted, Lex.colors.primary, focus)
    val ink = if (front) Lex.colors.primaryForeground else Lex.colors.foreground
    val sub = ink.copy(alpha = 0.72f)
    val offered = t.onEnter != null
    Column(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(bg)
            .then(if (offered) Modifier.clickable(onClick = t.onEnter!!) else Modifier)
            .padding(20.dp),
    ) {
        // The mark, in a circle of the opposite tone.
        Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (front) Lex.colors.primaryForeground else Lex.colors.primary),
            contentAlignment = Alignment.Center) {
            val markInk = if (front) Lex.colors.primary else Lex.colors.primaryForeground
            if (t.workspace == Workspace.Consult) Text(">_", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = markInk)
            else Icon(entranceIcon(t.workspace), null, tint = markInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(t.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(t.description, fontSize = 13.5.sp, lineHeight = 19.sp, color = sub, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!offered) Text("Not Yet Opened", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sub, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (offered) Box(
                Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (front) Lex.colors.primaryForeground else Lex.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Enter " + t.title,
                    tint = if (front) Lex.colors.primary else Lex.colors.primaryForeground, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun entranceIcon(w: Workspace) = when (w) {
    Workspace.Consult    -> Icons.Outlined.Forum
    Workspace.Litigation -> Icons.Outlined.Gavel
    Workspace.Projects   -> Icons.Outlined.FolderOpen
    Workspace.Registry   -> Icons.Outlined.Description
}

private data class Entrance(val workspace: Workspace, val title: String, val eyebrow: String, val description: String, val onEnter: (() -> Unit)?, val cta: String = "Enter")

/** Consultations · Active · Resolved · Written Reports. */
@Composable
internal fun ConsultationFigures(ws: Workspaces, onOpenConsultations: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Consultations", "${ws.consultations}", "Every session you have raised with us",
                Modifier.weight(1f).fillMaxHeight(), onClick = onOpenConsultations)
            // The portal's figure counts every consultation not yet resolved,
            // including one waiting on the client; the Consultations screen
            // calls the same set Active.
            StatCard(
                "Active", "${ws.awaiting}", "Sessions still open with the firm",
                Modifier.weight(1f).fillMaxHeight(),
                onClick = onOpenConsultations,
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Resolved", "${ws.resolved}", "Sessions closed after an answer",
                Modifier.weight(1f).fillMaxHeight(), onClick = onOpenConsultations)
            StatCard("Written Reports", "${ws.reports}", "Sessions that produced a written analysis",
                Modifier.weight(1f).fillMaxHeight(), onClick = onOpenConsultations)
        }
    }
}
