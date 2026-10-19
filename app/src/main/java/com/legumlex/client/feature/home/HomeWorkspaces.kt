package com.legumlex.client.feature.home

import androidx.compose.foundation.layout.Arrangement
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
 *  workspace entrances, and the consultations' four figures.
 * ========================================================================== */

/** "MONDAY, 19 OCTOBER 2026" over "Good afternoon, Saaj", and the two actions. */
@Composable
internal fun HomeHeader(
    dateLabel: String,
    greeting: String,
    onCheckAgreement: () -> Unit,
    onAskAdvocate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Eyebrow(dateLabel)
        Spacer(Modifier.height(4.dp))
        Text(greeting, style = MaterialTheme.typography.headlineSmall, color = Lex.colors.foreground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        // Stacked: side by side, at half a phone's width, each label wraps.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LegumPrimaryButton(text = "Ask Your Advocate", onClick = onAskAdvocate, leadingIcon = Icons.Outlined.Forum)
            LegumSecondaryButton(text = "Check An Agreement", onClick = onCheckAgreement, leadingIcon = Icons.Outlined.RateReview)
        }
    }
}

/** The three entrances: Litigation (dark), Projects (violet), Registry (teal). */
@Composable
internal fun WorkspaceEntrances(
    ws: Workspaces,
    onLitigation: () -> Unit,
    onProjects: () -> Unit,
    onRegistry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkspaceTile(
            Workspace.Litigation, "Litigation",
            "Your matters before a court — hearings, orders and notices.",
            onEnter = if ("litigation" in ws.offered) onLitigation else null,
        )
        WorkspaceTile(
            Workspace.Projects, "Projects",
            "Work we run for you, and its tasks.",
            onEnter = if ("projects" in ws.offered) onProjects else null,
        )
        WorkspaceTile(
            Workspace.Registry, "Registry",
            "Your agreements and their deadlines.",
            onEnter = if ("registry" in ws.offered) onRegistry else null,
        )
    }
}

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
