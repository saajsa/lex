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
 * "MONDAY, 19 OCTOBER 2026" over "Good afternoon, Saaj", and Consult: the
 * Legum Lex desk, as the site's masthead button opens it (with its "AI" tag).
 * Having an agreement read is one of the desk's services, as on the site.
 */
@Composable
internal fun HomeHeader(
    dateLabel: String,
    greeting: String,
    onConsult: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Eyebrow(dateLabel)
        Spacer(Modifier.height(4.dp))
        Text(greeting, style = MaterialTheme.typography.headlineSmall, color = Lex.colors.foreground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.medium).background(Lex.colors.primary)
                .clickable(onClick = onConsult).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Forum, null, tint = Lex.colors.primaryForeground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Consult", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = Lex.colors.primaryForeground)
            Spacer(Modifier.width(8.dp))
            Text("AI", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1B1406),
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFF3C25E)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/**
 * The three entrances: Litigation (dark), Projects (violet), Registry (teal),
 * swiped through one at a time rather than stacked, with the next card's edge
 * showing so it is plain there is more, and dots under them.
 */
@Composable
internal fun WorkspaceEntrances(
    ws: Workspaces,
    onLitigation: () -> Unit,
    onProjects: () -> Unit,
    onRegistry: () -> Unit,
) {
    val tiles = listOf(
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
            contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
        ) { i ->
            val t = tiles[i]
            WorkspaceTile(t.workspace, t.title, t.description, onEnter = t.onEnter, eyebrow = t.eyebrow,
                modifier = Modifier.heightIn(min = 158.dp))
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

private data class Entrance(val workspace: Workspace, val title: String, val eyebrow: String, val description: String, val onEnter: (() -> Unit)?)

/** Consultations · Awaiting An Answer · Resolved · Written Reports. */
@Composable
internal fun ConsultationFigures(ws: Workspaces, onOpenConsultations: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Consultations", "${ws.consultations}", "Every session you have raised with us",
                Modifier.weight(1f).fillMaxHeight(), onClick = onOpenConsultations)
            StatCard(
                "Awaiting An Answer", "${ws.awaiting}", "Sessions an advocate has still to answer",
                Modifier.weight(1f).fillMaxHeight(),
                valueColor = if (ws.awaiting > 0) Lex.colors.danger.solid else Lex.colors.foreground,
                chip = {
                    if (ws.awaiting > 0) StatusChip("With The Firm", Lex.colors.warning, Icons.Outlined.Schedule)
                    else StatusChip("None Outstanding", Lex.colors.success, Icons.Outlined.CheckCircle)
                },
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
