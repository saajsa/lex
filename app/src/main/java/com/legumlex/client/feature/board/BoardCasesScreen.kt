package com.legumlex.client.feature.board

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.KeyValueRow
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LegumTextField
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One case in the list under Today. */
@Composable
internal fun CaseListRow(c: BoardCase, today: java.time.LocalDate, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(c.heading, style = MaterialTheme.typography.labelMedium, color = Lex.colors.mutedForeground, maxLines = 1)
        Text(partiesText(c, Lex.colors.mutedForeground), style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
        Text(
            c.courtLine,
            style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (c.nextDate != null) "Next: ${BoardDates.show(c.nextDate, today)}" else "No Next Date",
                style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground,
            )
            c.check?.let { com.legumlex.client.ui.components.StatusChip(it.label, checkPalette(it)) }
        }
        if (c.folder != null || c.tags.isNotEmpty()) { Spacer(Modifier.height(6.dp)); LabelBadges(c) }
    }
}

/** One case: its details, and what can be done with it. */
@Composable
fun BoardCaseScreen(caseId: Int, onBack: () -> Unit, onCredits: () -> Unit, onOpenOrder: (Int, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf<BoardCase?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf<Int?>(null) }
    var addingCnr by remember { mutableStateOf(false) }
    var settingDate by remember { mutableStateOf(false) }
    var confirmRead by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var refusalCode by remember { mutableStateOf<String?>(null) }
    var checkCost by remember { mutableStateOf<Int?>(null) }
    var answering by remember { mutableStateOf<Pair<NeedsEye, DateAnswer>?>(null) }
    var filing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<String?>(null) }
    var getting by remember { mutableStateOf<BoardOrder?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var balance by remember { mutableStateOf<Int?>(null) }
    val today = BoardDates.today()

    // 038.7: offline (or a server error) shows the copy kept on this device, with when it was saved; a 403 or 404 never does.
    var savedAt by remember { mutableStateOf<Long?>(null) }
    fun load() { scope.launch { repo.caseOrSaved(caseId).onSuccess { (k, at) -> c = k; savedAt = at; error = null }.onFailure { error = it.boardMessage() } } }
    LaunchedEffect(caseId) {
        load()
        repo.credits().onSuccess { cost = shownCost(it.costAdd); checkCost = shownCost(it.costCheck); balance = it.balance }
    }
    val live = savedAt == null
    val openOrder = { o: BoardOrder -> onOpenOrder(o.id, orderLabel(o)) }
    val getOrder = { o: BoardOrder -> refusal = null; refusalCode = null; getting = o }
    // The empty Hearings and Orders tabs carry the same Update From Court action (and confirm) as Details.
    val readLabel = if (busy) "Reading The Court Record…" else "Update From Court Now" + (cost?.let { " (${creditsTitle(it)})" } ?: "")
    val onRead: (() -> Unit)? = if (live && !busy) ({ refusal = null; confirmRead = true }) else null
    val clearance = com.legumlex.client.ui.navigation.LocalCounselClearance.current

    Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
        // The pinned head: the case, its parties and next date, its marks, and the tabs.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeader(eyebrow = "Case", title = c?.heading ?: "Case")
            c?.let { x ->
                Text(partiesText(x, Lex.colors.mutedForeground), style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                caseHeadLine(x, today).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground) }
                CaseChips(x)
                LabelBadges(x)
                savedAt?.let { at -> NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().androidScroll().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = clearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val x = c
            if (x == null) {
                if (error != null) LexCard { LexEmpty("This Case Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { load() }) }) }
                else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) }
            } else {
                refusal?.let {
                    NoteLine(it, danger = true)
                    if (refusalCode == "insufficient_credits") LegumSecondaryButton("Buy Credits", onCredits)
                }
                // Owner, 23 Nov 2026: one page, fewest taps. Hearings with their orders first; the details at the end.
                if (fetching) NoteLine("Getting the order from eCourts. This can take a minute…")
                CaseRecordSection(x.record, x.hasCnr, readLabel, onRead, enabled = live && !fetching, onOpen = openOrder, onGet = getOrder,
                    onCombined = { onOpenOrder(0, "All Kept Orders") })
                Eyebrow("Details", Modifier.padding(top = 8.dp))
                run {
                        if (x.needsEye && x.ecourtsDate != null && live) {
                            NeedsEyeCard(
                                listOf(NeedsEye(x.id, x.cnr, x.caseNo, x.title, x.ecourtsDate, x.nextDate)), today,
                                onKeep = { answering = it to DateAnswer.KeepMine }, onAccept = { answering = it to DateAnswer.EcourtsRight },
                            )
                        }
                        LexCard {
                            KeyValueRow("CNR", cnrLine(x), mono = x.hasCnr)
                            if (x.caseNo.isNotBlank()) KeyValueRow("Case Number", x.caseNo)
                            if (x.caseType.isNotBlank()) KeyValueRow("Case Type", x.caseType)
                            if (x.petitioner.isNotBlank()) KeyValueRow("Petitioner", x.petitioner)
                            if (x.respondent.isNotBlank()) KeyValueRow("Respondent", x.respondent)
                            if (x.courtShown.isNotBlank()) KeyValueRow("Court", x.courtShown)
                            if (x.judgeShown.isNotBlank()) KeyValueRow("Judge", x.judgeShown)
                            if (x.stage.isNotBlank()) KeyValueRow("Stage", x.stage)
                            BoardDates.parse(x.nextDate)?.let { KeyValueRow("Next Date", BoardDates.long(it)) }
                            BoardDates.parse(x.lastDate)?.let { KeyValueRow("Last Date", BoardDates.long(it)) }
                            DailyCheckRow(x.morningCheck, x.hasCnr, checkCost, enabled = !busy && live) { on ->
                                busy = true; refusal = null; refusalCode = null
                                scope.launch {
                                    // The action's reply has no Date History: the case page is read again so the history stays.
                                    repo.caseEdit(x.id, mapOf("morning_check" to if (on) "1" else "0")).onSuccess { c = it.copy(history = x.history); load() }
                                        .onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() }
                                    busy = false
                                }
                            }
                            x.check?.let { KeyValueRow("Last Check", it.label + (BoardDates.parse(it.forDate)?.let { d -> " for ${BoardDates.short(d, today)}" } ?: "")) }
                            if (x.note.isNotBlank()) KeyValueRow("Note", x.note)
                            KeyValueRow("Source", sourceLabel(x.source))
                        }
                        FolderAndTagsCard(x) { if (live) filing = true }
                        LegumSecondaryButton("Share Summary", {
                            refusal = null
                            scope.launch { repo.shareText(x).onSuccess { sharing = it }.onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() } }
                        }, enabled = !busy)
                        // The web case page has no edit: the app carries the Daily Check switch, Add The CNR and Update From Court only.
                        if (!x.hasCnr) {
                            LegumPrimaryButton("Add The CNR (Free)", { addingCnr = true }, enabled = !busy && live)
                            // A hand case's date is the counsel's to set: eCourts has no record of it yet (the web's Set Next Date).
                            LegumSecondaryButton("Set Next Date", { settingDate = true }, enabled = !busy && live)
                        }
                        // A hand case has nothing at eCourts to read, so there is no Update From Court for it (as on the web).
                        if (x.hasCnr) LegumSecondaryButton(if (busy) "Reading The Court Record…" else "Update From Court Now" + (cost?.let { " (${creditsTitle(it)})" } ?: ""), { refusal = null; confirmRead = true }, enabled = !busy && live)
                        LegumSecondaryButton("Remove From Your Diary", { confirmRemove = true }, enabled = !busy && live)
                        DateHistoryCard(x.history, today)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    sharing?.let { text -> ShareSummaryDialog(text) { sharing = null } }
    getting?.let { o ->
        FetchConfirm(o, balance, onCancel = { getting = null }, onGet = {
            getting = null; fetching = true
            scope.launch {
                repo.orderFetch(caseId, o.id).onSuccess { r ->
                    balance = r.balance; BoardHub.applyBalance(r.balance)
                    load()
                    onOpenOrder(r.order.id, orderLabel(r.order))
                }.onFailure {
                    refusal = it.boardMessage(); refusalCode = it.boardCode()
                    // A refused fetch may have been refunded (order_unavailable, ecourts_failed): show the balance as it now stands.
                    repo.credits().onSuccess { c -> balance = c.balance; BoardHub.applyBalance(c.balance) }
                    load()
                }
                fetching = false
            }
        })
    }
    c?.let { x -> if (filing) FileCaseWizard(x, onClose = { filing = false }, onSaved = { filing = false; c = it.copy(history = x.history) }) }

    answering?.let { (n, a) ->
        DateAnswerDialog(n, a, today, onClose = { answering = null }, onDone = { answering = null; load() })
    }
    c?.let { x ->
        if (settingDate) SetDateWizard(x, onClose = { settingDate = false }, onSaved = { settingDate = false; load() })
        if (addingCnr) AddCnrWizard(x.id, x.heading, onClose = { addingCnr = false }, onDone = { load(); addingCnr = false })
        if (confirmRead) AlertDialog(
            onDismissRequest = { confirmRead = false },
            title = { Text("Update From Court Now?") },
            text = { Text("The platform reads this case's record from the court and updates its next date and stage." + (cost?.let { " This costs ${creditsLabel(it)}." } ?: "")) },
            confirmButton = {
                TextButton({
                    confirmRead = false; busy = true; refusal = null; refusalCode = null
                    scope.launch {
                        repo.caseRead(x.id).onSuccess { (nc, bal) ->
                            c = nc.copy(history = x.history); load()
                            BoardHub.applyBalance(bal)
                            Toast.makeText(context, "Updated. Balance: ${creditsLabel(bal)}.", Toast.LENGTH_LONG).show()
                        }.onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() }
                        busy = false
                    }
                }) { Text("Update", color = Lex.colors.foreground) }
            },
            dismissButton = { TextButton({ confirmRead = false }) { Text("Cancel", color = Lex.colors.foreground) } },
            containerColor = Lex.colors.card,
        )
        if (confirmRemove) AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove From Your Diary?") },
            text = { Text("The case and its date history are deleted. Credits already spent on it are not given back. You can add it again later.") },
            confirmButton = {
                TextButton({
                    confirmRemove = false; busy = true
                    scope.launch {
                        repo.caseRemove(x.id).onSuccess { onBack() }.onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() }
                        busy = false
                    }
                }) { Text("Remove", color = Lex.colors.danger.softText) }
            },
            dismissButton = { TextButton({ confirmRemove = false }) { Text("Cancel", color = Lex.colors.foreground) } },
            containerColor = Lex.colors.card,
        )
    }
}

@Composable
private fun Modifier.androidScroll(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

/** The daily eCourts check for one case: on or off, with what it costs each morning (the server's figure). */
@Composable
private fun DailyCheckRow(on: Boolean, hasCnr: Boolean, cost: Int?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Daily eCourts Check", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
            Text(
                dailyCheckLine(on, hasCnr, cost),
                style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
            )
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Where the case's dates came from, newest first, in the server's own labels. */
@Composable
private fun DateHistoryCard(rows: List<DateHistoryRow>, today: java.time.LocalDate) {
    LexCard {
        Text("Date History", style = MaterialTheme.typography.labelLarge, color = Lex.colors.mutedForeground, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (rows.isEmpty()) {
            Text("No date has been recorded for this case yet.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else rows.forEach { r ->
            KeyValueRow(historyLabel(r), BoardDates.parse(r.date)?.let { BoardDates.long(it) } ?: r.date)
        }
    }
}


/** Every case in the diary: the web's chips, Court, Folder and Tag pickers, search, and pages of 100 (REQ-038 038.5). */
@Composable
fun BoardCasesListScreen(onOpenCase: (Int) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    var query by remember { mutableStateOf(CaseQuery()) }
    var offset by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<CasePage?>(null) }
    var filters by remember { mutableStateOf<BoardFilters?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var byHand by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var listSavedAt by remember { mutableStateOf<Long?>(null) }
    if (byHand) HandCaseWizard(onClose = { byHand = false }, onAdded = { byHand = false; tick++; onOpenCase(it.id) })
    if (managing) FoldersAndTagsDialog(onClose = { managing = false }, onChanged = { tick++ })
    val today = BoardDates.today()
    LaunchedEffect(tick) { repo.filters().onSuccess { filters = it } }
    LaunchedEffect(query, offset, tick) {
        if (query.q.isNotBlank()) kotlinx.coroutines.delay(300)
        repo.casePageOrSaved(query, offset).onSuccess { (pg, at) -> page = pg; listSavedAt = at; error = null }.onFailure { error = it.boardMessage() }
    }
    // A new filter or search starts again at the first page.
    val change = { nq: CaseQuery -> query = nq; offset = 0 }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(eyebrow = "Your Diary", title = "Cases") }
        item { LegumSecondaryButton("Add By Hand", { byHand = true }) }
        item { com.legumlex.client.ui.components.LegumTextField(query.q, { change(query.copy(q = it)) }, "Search Cases", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Search) }
        item { CaseFilterBar(filters, query, change) { managing = true } }
        listSavedAt?.let { at -> item { NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") } }
        val p = page
        when {
            error != null && p == null -> item { LexCard { LexEmpty("The Cases Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { error = null; tick++ }) }) } }
            p == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) } }
            p.items.isEmpty() -> item { Text(if (query == CaseQuery()) "No cases yet." else "No case matches.", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground) }
            else -> {
                items(p.items, key = { it.id }) { c -> CaseListRow(c, today) { onOpenCase(c.id) } }
                pagerLine(p)?.let { line ->
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(line, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, modifier = Modifier.weight(1f))
                            if (p.offset > 0) TextButton({ offset = maxOf(0, p.offset - CASES_PAGE) }) { Text("Previous") }
                            if (p.offset + CASES_PAGE < p.total) TextButton({ offset = p.offset + CASES_PAGE }) { Text("Next") }
                        }
                    }
                }
            }
        }
    }
}

/** The CNR row: a hand case has none yet (the web's own words). */
internal fun cnrLine(c: BoardCase): String = if (c.hasCnr) c.cnr else "Not yet. This case was added by hand."

/** A cost to show: 0 means the server has none set (it refuses with cost_unset), so no figure is shown. */
internal fun shownCost(cost: Int?): Int? = cost?.takeIf { it > 0 }

/** The daily check line under the switch, as the web case page words it. */
internal fun dailyCheckLine(on: Boolean, hasCnr: Boolean, cost: Int?): String {
    val price = shownCost(cost)?.let { " for ${creditsLabel(it)}" }.orEmpty()
    return when {
        !hasCnr -> "Checked every morning$price, once the case has a CNR. eCourts has no record of it yet."
        on -> "Checked every morning$price."
        else -> "Not checked. Its date stays as it was."
    }
}

/** A board case's source as a label: the server's keys (cnr, import, hand, sheet). */
internal fun sourceLabel(s: String): String =
    mapOf("cnr" to "CNR", "import" to "Import", "hand" to "By Hand", "sheet" to "Sheet")[s] ?: s.replaceFirstChar { it.uppercase() }

/** "1 Credit" / "5 Credits", for a button label. */
internal fun creditsTitle(n: Int): String = creditsLabel(n).replaceFirstChar { it.uppercase() }.replace(" credit", " Credit")

/** A Date History row's label as the server words it; a date that was replaced, rejected or never adopted is marked "Replaced", as on the web. */
internal fun historyLabel(r: DateHistoryRow): String =
    r.label.ifBlank { r.source.replaceFirstChar { it.uppercase() } } + if (r.superseded) " · Replaced" else ""

/** Share Summary (REQ-038 038.6): the server's text to read first, then Copy or the share sheet. No link, no note. */
@Composable
private fun ShareSummaryDialog(text: String, onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Share Summary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What You Will Share", style = MaterialTheme.typography.labelMedium, color = Lex.colors.mutedForeground)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Lex.colors.muted).padding(12.dp))
                }
                Text(SHARE_NOTE, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
            }
        },
        confirmButton = {
            TextButton({
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Case Summary", text))
                Toast.makeText(context, "Copied.", Toast.LENGTH_SHORT).show()
            }) { Text("Copy The Text") }
        },
        dismissButton = {
            Row {
                TextButton(onClose) { Text("Close") }
                TextButton({
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
                    context.startActivity(android.content.Intent.createChooser(send, "Share Summary"))
                }) { Text("Share") }
            }
        },
    )
}

/** The web's line under the preview. */
internal const val SHARE_NOTE = "No link and no note of yours goes with it."


/** The head's second line: "Next: Fri, 28 Nov · Civil Judge at Ludhiana", or the court alone, or nothing. */
internal fun caseHeadLine(c: BoardCase, today: java.time.LocalDate): String =
    listOfNotNull(c.nextDate?.let { BoardDates.show(it, today).takeIf { s -> s.isNotBlank() }?.let { s -> "Next: $s" } }, c.courtShown.takeIf { it.isNotBlank() }).joinToString(" · ")

