package com.legumlex.client.feature.registry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.Mono
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.KeyValueRow
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.StatusPalette

/* ============================================================================
 *  Registry — the contract register, as Mylex's /mylex/register shows it: the
 *  figures, what is coming up (overdue first, then the next 30 days), and the
 *  agreements. Read-only; adding and editing agreements is on the portal.
 * ========================================================================== */

@Composable
private fun statusPalette(status: String): StatusPalette = when (status.lowercase()) {
    "active" -> Lex.colors.success
    "expired", "terminated" -> Lex.colors.neutral
    else -> Lex.colors.neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistryScreen(onOpenAgreement: (String) -> Unit) {
    val vm: RegistryViewModel = viewModel()
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val stale by vm.staleReason.collectAsState()

    when (val s = state) {
        Loadable.Loading -> Box(Modifier.fillMaxSize().background(Lex.colors.background), Alignment.Center) {
            CircularProgressIndicator(color = Lex.colors.foreground, strokeWidth = 2.dp)
        }
        is Loadable.Failed -> Failed(s.message, vm::load)
        is Loadable.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
            RegistryBody(s.data, stale, vm::load, onOpenAgreement)
        }
    }
}

/** A reload failed: what is shown is the last copy that loaded. */
@Composable
private fun StaleNote(reason: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Lex.colors.warning.softBg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Not refreshed: $reason", style = MaterialTheme.typography.bodySmall, color = Lex.colors.warning.softText, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Try Again", color = Lex.colors.foreground) }
    }
}

@Composable
private fun RegistryBody(d: RegistryOverview, stale: String?, onRetry: () -> Unit, onOpenAgreement: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    // The statuses actually on the register (active, expired, terminated, superseded), in that order.
    val statuses = listOf("active", "expired", "terminated", "superseded")
        .filter { st -> st == "active" || d.agreements.any { it.status == st } } +
        d.agreements.map { it.status }.distinct().filter { it !in setOf("active", "expired", "terminated", "superseded") }
    val current = if (filter in statuses) filter else "all"   // a status that has left the register falls back to All
    val shown = d.agreements.filter { current == "all" || it.status == current }
    val anyOverdue = d.upcoming.any { it.bucket == "overdue" || (it.daysToDue ?: 0) < 0 }

    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "Registry", title = "Register")
        stale?.let { StaleNote(it, onRetry) }

        // The figures, two by two — the portal's own six, less value on file.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Overdue", "${d.overdue}", "Deadlines already past their date",
                Modifier.weight(1f).fillMaxHeight(), valueColor = if (d.overdue > 0) Lex.colors.danger.solid else Lex.colors.foreground)
            StatCard("Due In 30 Days", "${d.soon}", "Obligations falling due this month", Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Notice Window Closing", "${d.notice30}", "Agreements needing notice within 30 days", Modifier.weight(1f).fillMaxHeight())
            StatCard("Expiring", "${d.expiring90}", "Agreements whose term ends within 90 days", Modifier.weight(1f).fillMaxHeight())
        }

        // Coming up: overdue first, then the next thirty days.
        LexCard(title = "Coming Up", trailing = {
            if (d.upcoming.isNotEmpty()) StatusBadge("${d.upcoming.size}", if (anyOverdue) Lex.colors.danger else Lex.colors.warning)
        }) {
            if (d.upcoming.isEmpty()) {
                LexEmpty("Nothing Coming Up", "No deadline is overdue or due in the next 30 days.", icon = Icons.Outlined.TaskAlt)
            } else {
                d.upcoming.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(color = Lex.colors.border)
                    DeadlineRow(o, showAgreement = true, onClick = { if (o.agreementId.isNotBlank()) onOpenAgreement(o.agreementId) })
                }
            }
        }

        LexCard(title = "Agreements", trailing = {
            Text("${d.agreements.size} On Record", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        }) {
            if (d.agreements.isEmpty()) {
                LexEmpty("No Agreements On Your Register", "Agreements you add on the portal are tracked here, with their deadlines.",
                    icon = Icons.Outlined.Inventory2)
            } else {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SegmentedToggle(
                        options = listOf("all" to "All") + statuses.map { it to title(it) },
                        selected = current, onSelect = { filter = it },
                    )
                }
                HorizontalDivider(color = Lex.colors.border)
                if (shown.isEmpty()) {
                    LexEmpty("No ${title(current)} Agreements", "None of your agreements is ${title(current).lowercase()}.")
                }
                shown.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = Lex.colors.border)
                    AgreementRow(a) { onOpenAgreement(a.id) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AgreementRow(a: RegisterAgreement, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(a.counterparty ?: "Counterparty Not Known Yet", a.counterpartyType?.let { title(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
            )
            a.reference?.let {
                Text(it, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.expiry?.let { "Ends ${it.long()}" } ?: "No End Date Recorded",
                    style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                endsNote(a.expiry)?.let {
                    Text("  ·  $it", style = MaterialTheme.typography.bodySmall,
                        color = if (a.expiry!!.isBefore(java.time.LocalDate.now())) Lex.colors.danger.softText else Lex.colors.mutedForeground)
                }
            }
            if (a.openDeadlines > 0) {
                Spacer(Modifier.height(6.dp))
                StatusBadge("${a.openDeadlines} Open ${if (a.openDeadlines == 1) "Deadline" else "Deadlines"}", Lex.colors.neutral)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(title(a.status), statusPalette(a.status))
            Spacer(Modifier.height(8.dp))
            Text(compactInr(a.value, a.currency), style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
        }
    }
}

@Composable
private fun DeadlineRow(o: Deadline, showAgreement: Boolean, onClick: (() -> Unit)? = null) {
    val overdue = o.status == "open" && (o.daysToDue ?: 0) < 0
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            StatusBadge(title(o.kindLabel), if (o.status == "open") Lex.colors.warning else Lex.colors.neutral)
            Spacer(Modifier.height(6.dp))
            Text(o.title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            o.amount?.let {
                Text(fullInr(it, o.currency), style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
            }
            if (showAgreement && o.agreementTitle != null) {
                Text(o.agreementTitle, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            } else if (o.description != null) {
                Text(o.description, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(o.due.long(), style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
            Text(
                when (o.status) { "done" -> "Done"; "waived" -> "Waived"; else -> dueNote(o.daysToDue) },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) Lex.colors.danger.softText else Lex.colors.mutedForeground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistryAgreementScreen() {
    val vm: RegistryAgreementViewModel = viewModel()
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val stale by vm.staleReason.collectAsState()
    when (val s = state) {
        Loadable.Loading -> Box(Modifier.fillMaxSize().background(Lex.colors.background), Alignment.Center) {
            CircularProgressIndicator(color = Lex.colors.foreground, strokeWidth = 2.dp)
        }
        is Loadable.Failed -> Failed(s.message, vm::load)
        is Loadable.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
            AgreementBody(s.data, stale, vm::load)
        }
    }
}

@Composable
private fun AgreementBody(d: RegistryDetail, stale: String?, onRetry: () -> Unit) {
    val a = d.agreement
    val today = java.time.LocalDate.now()
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "Agreement Register", title = a.title)
        stale?.let { StaleNote(it, onRetry) }

        // Who it is with, and the two dates that matter: when it ends, and
        // when notice has to be given by.
        LexCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.counterparty ?: "Counterparty Not Known Yet", style = MaterialTheme.typography.titleMedium,
                        color = Lex.colors.foreground, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(title(a.status), statusPalette(a.status))
                }
                a.counterpartyType?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("They are your ${title(it).lowercase()}.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
            }
            HorizontalDivider(color = Lex.colors.border)
            Row(Modifier.height(IntrinsicSize.Min)) {
                DateCell("Ends", a.expiry, endsNote(a.expiry), a.expiry?.isBefore(today) == true, Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.border))
                DateCell("Notice By", a.noticeBy,
                    a.noticeBy?.let { if (it.isBefore(today)) "Window Closed" else dueNote(java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt()) },
                    a.noticeBy?.isBefore(today) == true, Modifier.weight(1f))
            }
        }

        LexCard(title = "What This Commits You To", trailing = {
            if (d.deadlines.isNotEmpty()) StatusBadge("${d.deadlines.size}", Lex.colors.neutral)
        }) {
            if (d.deadlines.isEmpty()) {
                LexEmpty("No Deadlines Recorded", "Nothing on this agreement has a date to keep yet.")
            } else {
                d.deadlines.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(color = Lex.colors.border)
                    DeadlineRow(o, showAgreement = false)
                }
            }
        }

        LexCard(title = "Agreement Details") {
            val rows = listOfNotNull(
                a.reference?.let { Triple("Reference", it, true) },
                a.category?.let { Triple("Category", title(it), false) },
                Triple("Starts", a.effective.long(), false),
                Triple("Ends", a.expiry.long(), false),
                a.termMonths?.takeIf { it > 0 }?.let { Triple("Term", monthsLabel(it), false) },
                a.signed?.let { Triple("Signed", it.long(), false) },
                Triple("At The End", renewalLabel(a.renewalType), false),
                a.noticeDays?.let { Triple("Notice Period", "$it ${if (it == 1) "Day" else "Days"}", false) },
                a.renewalTermMonths?.takeIf { a.renewalType != "none" && it > 0 }?.let { Triple("Renews For", monthsLabel(it), false) },
                Triple("Value", compactInr(a.value, a.currency), false),
                Triple("Signed Copy", if (a.hasSignedCopy) "On File" else "Not On File", false),
                a.jurisdiction?.let { Triple("Jurisdiction", it, false) },
                a.ownerName?.let { Triple("Owner", it, false) },
            )
            rows.forEachIndexed { i, (k, v, mono) ->
                if (i > 0) HorizontalDivider(color = Lex.colors.border)
                KeyValueRow(k, v, mono = mono)
            }
        }

        a.paymentTerms?.let { Prose("Payment Terms", it) }
        a.governingLaw?.let { Prose("Governing Law", it) }
        a.notes?.let { Prose("Notes", it) }

        Text(
            "Adding deadlines, recording what happened and replacing the signed copy are done on the client portal.",
            style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DateCell(label: String, date: java.time.LocalDate?, note: String?, alarm: Boolean, modifier: Modifier) {
    Column(modifier.background(Lex.colors.muted.copy(alpha = 0.5f)).padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(4.dp))
        Text(date.long(), style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (alarm) Lex.colors.danger.softText else Lex.colors.mutedForeground)
        }
    }
}

@Composable
private fun Prose(title: String, body: String) {
    LexCard(title = title) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LexEmpty("The Register Could Not Be Loaded", message.ifBlank { "Please try again." })
        TextButton(onClick = onRetry) { Text("Try Again", color = Lex.colors.foreground) }
    }
}
