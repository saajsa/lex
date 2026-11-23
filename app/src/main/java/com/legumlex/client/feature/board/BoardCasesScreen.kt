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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
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
import com.legumlex.client.ui.components.SegmentedToggle
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

/**
 * One case (owner, 23 Nov 2026): the head with its next date and symbols, a row of action icons (ⓘ details, share, update, the daily
 * check eye, folder, remove), then the hearings with their orders filling the page. The details live in a sheet behind ⓘ.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BoardCaseScreen(caseId: Int, onBack: () -> Unit, onCredits: () -> Unit, onOpenOrder: (Int, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf<BoardCase?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
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
    var details by remember { mutableStateOf(false) }
    var legend by remember { mutableStateOf(false) }
    val getter = rememberOrderGetter()
    val today = BoardDates.today()

    // 038.7: offline (or a server error) shows the copy kept on this device, with when it was saved; a 403 or 404 never does.
    var savedAt by remember { mutableStateOf<Long?>(null) }
    fun load() { scope.launch { repo.caseOrSaved(caseId).onSuccess { (k, at) -> c = k; savedAt = at; error = null }.onFailure { error = it.boardMessage() } } }
    LaunchedEffect(caseId) {
        load()
        repo.credits().onSuccess { cost = shownCost(it.costAdd); checkCost = shownCost(it.costCheck) }
    }
    val live = savedAt == null
    val readLabel = if (busy) "Reading The Court Record…" else "Update From Court Now" + (cost?.let { " (${creditsTitle(it)})" } ?: "")
    val onRead: (() -> Unit)? = if (live && !busy) ({ refusal = null; confirmRead = true }) else null
    val clearance = com.legumlex.client.ui.navigation.LocalCounselClearance.current
    val shownRefusal = refusal ?: getter.refusal
    val shownCode = if (refusal != null) refusalCode else getter.refusalCode

    Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
        // The pinned head: the case, its parties, its next date with its symbols, its filing, and the action icons.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PageHeader(eyebrow = "Case", title = c?.heading ?: "Case")
            c?.let { x ->
                Text(partiesText(x, Lex.colors.mutedForeground), style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                CaseDateLine(x, today)
                CaseFilingLine(x)
                savedAt?.let { at -> NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.Outlined.Info, "Details", { details = true }, size = 40.dp)
                    IconAction(Icons.Outlined.Share, "Share Summary", {
                        if (!busy) { refusal = null; scope.launch { repo.shareText(x).onSuccess { sharing = it }.onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() } } }
                    }, size = 40.dp)
                    if (x.hasCnr) IconAction(Icons.Outlined.Refresh, readLabel, { onRead?.invoke() }, size = 40.dp)
                    IconAction(if (x.morningCheck) Icons.Filled.Visibility else Icons.Outlined.Visibility,
                        (if (x.morningCheck) "Daily Check On. " else "Daily Check Off. ") + dailyCheckLine(x.morningCheck, x.hasCnr, checkCost),
                        {
                            if (!busy && live) {
                                busy = true; refusal = null; refusalCode = null
                                scope.launch {
                                    // The action's reply has no Date History: the case page is read again so the history stays.
                                    repo.caseEdit(x.id, mapOf("morning_check" to if (x.morningCheck) "0" else "1")).onSuccess { c = it.copy(history = x.history, record = x.record); load() }
                                        .onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() }
                                    busy = false
                                }
                            }
                        }, size = 40.dp, look = if (x.morningCheck) ActionLook.Ink else ActionLook.Outline)
                    IconAction(Icons.Outlined.Folder, "File This Case", { if (live) filing = true }, size = 40.dp)
                    IconAction(Icons.Outlined.DeleteOutline, "Remove From Your Diary", { if (live && !busy) confirmRemove = true }, size = 40.dp)
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().androidScroll().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = clearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val x = c
            if (x == null) {
                if (error != null) LexCard { LexEmpty("This Case Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { load() }) }) }
                else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) }
            } else {
                shownRefusal?.let {
                    NoteLine(it, danger = true)
                    if (shownCode == "insufficient_credits") LegumSecondaryButton("Buy Credits", onCredits)
                }
                if (busy) NoteLine("Working…")
                if (x.needsEye && x.ecourtsDate != null && live) {
                    NeedsEyeCard(
                        listOf(NeedsEye(x.id, x.cnr, x.caseNo, x.title, x.ecourtsDate, x.nextDate)), today,
                        onKeep = { answering = it to DateAnswer.KeepMine }, onAccept = { answering = it to DateAnswer.EcourtsRight },
                    )
                }
                if (getter.fetching) NoteLine("Getting the order from eCourts. This can take a minute…")
                CaseRecordSection(x.record, x.hasCnr, readLabel, onRead, enabled = live && !getter.fetching,
                    onOpen = { o -> onOpenOrder(o.id, orderLabel(o)) }, onGet = { o -> refusal = null; getter.ask(caseId, o) },
                    onCombined = { onOpenOrder(0, "All Kept Orders") }, onLegend = { legend = true })
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    OrderGetterHost(getter, onOpened = { _, oid, title -> onOpenOrder(oid, title) }, onChanged = { load() })
    if (legend) LegendSheet { legend = false }
    c?.let { x ->
        if (details) androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { details = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Lex.colors.background,
        ) {
            Column(Modifier.fillMaxWidth().androidScroll().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Details", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                    IconAction(Icons.Outlined.HelpOutline, "What The Symbols Mean", { details = false; legend = true }, size = 36.dp)
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
                    KeyValueRow("Daily eCourts Check", dailyCheckLine(x.morningCheck, x.hasCnr, checkCost))
                    x.check?.let { KeyValueRow("Last Check", it.label + (BoardDates.parse(it.forDate)?.let { d -> " for ${BoardDates.short(d, today)}" } ?: "")) }
                    if (x.note.isNotBlank()) KeyValueRow("Note", x.note)
                    KeyValueRow("Source", sourceLabel(x.source))
                }
                FolderAndTagsCard(x) { if (live) { details = false; filing = true } }
                // A hand case: its CNR to add, and its date the counsel's to set (eCourts has no record of it yet).
                if (!x.hasCnr) {
                    LegumPrimaryButton("Add The CNR (Free)", { details = false; addingCnr = true }, enabled = !busy && live)
                    LegumSecondaryButton("Set Next Date", { details = false; settingDate = true }, enabled = !busy && live)
                }
                DateHistoryCard(x.history, today)
            }
        }
    }
    sharing?.let { text -> ShareSummaryDialog(text) { sharing = null } }
    c?.let { x -> if (filing) FileCaseWizard(x, onClose = { filing = false }, onSaved = { filing = false; c = it.copy(history = x.history, record = x.record) }) }

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
                            c = nc.copy(history = x.history, record = x.record); load()
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

/** "Next: Fri, 28 Nov" with the date's symbol and, for an eCourts case, the "e" mark; or "No Next Date". */
@Composable
internal fun CaseDateLine(c: BoardCase, today: java.time.LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(nextLine(c, today), style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground)
        dateMark(c, today)?.let { SymbolIcon(it) }
        if (c.hasCnr) SymbolIcon(Sym.Ecourts)
    }
}

/** "Next: Fri, 28 Nov", or "No Next Date". */
internal fun nextLine(c: BoardCase, today: java.time.LocalDate): String =
    c.nextDate?.let { BoardDates.show(it, today).takeIf { s -> s.isNotBlank() } }?.let { "Next: $it" } ?: "No Next Date"

/** The folder glyph with its name, then "#tag" text; nothing when the case is unfiled. */
@Composable
internal fun CaseFilingLine(c: BoardCase) {
    if (c.folder == null && c.tags.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        c.folder?.let { SymbolIcon(Sym.Folder, 14.dp); Text(it.name, style = MaterialTheme.typography.labelSmall, color = Lex.colors.secondaryForeground) }
        if (c.tags.isNotEmpty()) Text((if (c.folder != null) "  " else "") + tagsLine(c), style = MaterialTheme.typography.labelSmall, color = Lex.colors.secondaryForeground, maxLines = 1)
    }
}

@Composable
private fun Modifier.androidScroll(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

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


/** The Cases status segments (owner, 23 Nov 2026: Listed Today and Tomorrow left the app; the web keeps them). */
internal val STATUS_SEGMENTS = listOf("ongoing" to "Ongoing", "eye" to "Needs Your Eye", "undated" to "Undated", "disposed" to "Disposed")

/** While there is search text every case is searched (no status segment), so a disposed case is never missed; court, folder and tag still apply. */
internal fun asked(q: CaseQuery): CaseQuery = if (q.q.isNotBlank()) q.copy(filter = "all") else q

internal const val SEARCH_ALL_NOTE = "Searching all cases."

/** Cases opens on Ongoing (every case not disposed); there is no "All" segment: a disposed case is under Disposed. */
internal val CASES_START = CaseQuery(filter = "ongoing")

/**
 * Every case in the diary (owner, 23 Nov 2026): the header with ⓘ (symbols), the folder manager and + (Add By Hand); search with a
 * filter icon (Court, Folder, Tag in a sheet, the count on the icon); one compact status control; cards that expand in place.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BoardCasesListScreen(onOpenCase: (Int) -> Unit, onOpenOrder: (Int, Int, String) -> Unit = { _, _, _ -> }, onCredits: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    // Kept across opening a case and coming back: the search, segment, filters, page, open cards and the scroll.
    val m: CasesListModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var query by m::query
    var offset by m::offset
    var page by m::page
    var filters by m::filters
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var byHand by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var legend by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    var listSavedAt by m::listSavedAt
    var open by m::open
    val records = m.records
    val getter = rememberOrderGetter()
    val scope = rememberCoroutineScope()
    if (byHand) HandCaseWizard(onClose = { byHand = false }, onAdded = { byHand = false; tick++; onOpenCase(it.id) })
    if (managing) FoldersAndTagsDialog(onClose = { managing = false }, onChanged = { tick++ })
    if (legend) LegendSheet { legend = false }
    val today = BoardDates.today()
    LaunchedEffect(tick) { repo.filters().onSuccess { filters = it } }
    LaunchedEffect(query, offset, tick) {
        if (query.q.isNotBlank()) kotlinx.coroutines.delay(300)
        repo.casePageOrSaved(asked(query), offset).onSuccess { (pg, at) -> page = pg; listSavedAt = at; error = null }.onFailure { error = it.boardMessage() }
    }
    // A new filter or search starts again at the first page.
    val change = { nq: CaseQuery -> query = nq; offset = 0 }
    fun loadRecord(id: Int) { scope.launch { repo.orders(id).onSuccess { records[id] = it } } }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background), state = m.listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(eyebrow = "Your Diary", title = "Cases", actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconAction(Icons.Outlined.HelpOutline, "What The Symbols Mean", { legend = true }, size = 40.dp)
                    IconAction(Icons.Outlined.Folder, "Folders And Tags", { managing = true }, size = 40.dp)
                    IconAction(Icons.Outlined.Add, "Add By Hand", { byHand = true }, size = 40.dp)
                }
            })
        }
        item {
            com.legumlex.client.ui.components.LegumTextField(query.q, { change(query.copy(q = it)) }, "Search Cases", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Search,
                trailing = {
                    val n = activeFilters(query)
                    Box {
                        androidx.compose.material3.IconButton({ filtering = true }, Modifier.semantics { contentDescription = if (n > 0) "Filters, $n on" else "Filters" }) {
                            Icon(Icons.Outlined.FilterList, null, tint = if (n > 0) Lex.colors.foreground else Lex.colors.mutedForeground)
                        }
                        if (n > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Lex.colors.foreground), contentAlignment = Alignment.Center) {
                            Text("$n", fontSize = 10.sp, color = Lex.colors.background)
                        }
                    }
                })
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                SegmentedToggle(STATUS_SEGMENTS.map { (k, l) -> k to (l + filters?.counts?.get(k)?.let { " ${creditsText(it)}" }.orEmpty()) },
                    query.filter.takeIf { f -> STATUS_SEGMENTS.any { it.first == f } } ?: "ongoing", { change(query.copy(filter = it)) })
            }
        }
        if (query.q.isNotBlank()) item { NoteLine(SEARCH_ALL_NOTE) }
        listSavedAt?.let { at -> item { NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") } }
        (getter.refusal)?.let { r -> item { NoteLine(r, danger = true); if (getter.refusalCode == "insufficient_credits") LegumSecondaryButton("Buy Credits", onCredits) } }
        if (getter.fetching) item { NoteLine("Getting the order from eCourts. This can take a minute…") }
        val p = page
        when {
            error != null && p == null -> item { LexCard { LexEmpty("The Cases Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { error = null; tick++ }) }) } }
            p == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) } }
            p.items.isEmpty() -> item { Text(if (query == CASES_START) "No cases yet." else "No case matches.", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground) }
            else -> {
                items(p.items, key = { it.id }) { c ->
                    val expanded = c.id in open
                    CaseCard(c, today, expanded, records[c.id], enabled = listSavedAt == null && !getter.fetching,
                        onToggle = { open = if (expanded) open - c.id else open + c.id; if (!expanded && c.id !in records) loadRecord(c.id) },
                        onOpenCase = { onOpenCase(c.id) },
                        onOpen = { o -> onOpenOrder(c.id, o.id, orderLabel(o)) }, onGet = { o -> getter.ask(c.id, o) })
                }
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
    OrderGetterHost(getter, onOpened = onOpenOrder, onChanged = { id -> loadRecord(id) })
    if (filtering) CaseFilterSheet(filters, query, change) { filtering = false }
}

/**
 * One case on Cases: collapsed, its number with its symbols, the parties, the court, "Next: 4 Dec" with the date's symbol, and its
 * filing; the chevron opens it in place to the latest hearing, that hearing's orders as symbols, and Open Case. The body opens the case.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CaseCard(
    c: BoardCase, today: java.time.LocalDate, expanded: Boolean, record: CaseRecord?, enabled: Boolean,
    onToggle: () -> Unit, onOpenCase: () -> Unit, onOpen: (BoardOrder) -> Unit, onGet: (BoardOrder) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenCase).padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.heading, style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground, maxLines = 1)
                    if (c.hasCnr) SymbolIcon(Sym.Ecourts)
                    SymbolIcon(if (c.morningCheck) Sym.CheckOn else Sym.CheckOff, 14.dp)
                }
                Text(partiesText(c, Lex.colors.mutedForeground), style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                if (c.courtShown.isNotBlank()) Text(c.courtShown, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, maxLines = 1)
                CaseDateLineSmall(c, today)
                CaseFilingLine(c)
            }
            androidx.compose.material3.IconButton(onToggle, Modifier.semantics { contentDescription = if (expanded) "Show Less" else "Show More" }) {
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Lex.colors.secondaryForeground)
            }
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.HorizontalDivider(color = Lex.colors.border)
                when {
                    record == null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
                    record.listings.isEmpty() -> Text(recordEmpty("hearings", c.hasCnr, record.loaded), style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                    else -> {
                        val h = record.listings.first()
                        Text(BoardDates.parse(h.date)?.let { BoardDates.long(it) } ?: h.date, style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground)
                        hearingLine(h).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground) }
                        if (h.orders.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            h.orders.forEachIndexed { i, o -> OrderSymbol(o, enabled, onOpen, onGet, index = orderIndex(h.orders, i)) }
                        }
                    }
                }
                LegumSecondaryButton("Open Case", onOpenCase)
            }
        }
    }
}

/** The card's date line: "Next: 4 Dec" (or "No Next Date") and the date's symbol. */
@Composable
private fun CaseDateLineSmall(c: BoardCase, today: java.time.LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(nextLine(c, today), style = MaterialTheme.typography.labelMedium, color = Lex.colors.foreground)
        dateMark(c, today)?.let { SymbolIcon(it, 14.dp) }
    }
}

/** Court, Folder and Tag in a sheet (the filter icon), the server's options with their counts; Clear sets all three back. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CaseFilterSheet(f: BoardFilters?, q: CaseQuery, onChange: (CaseQuery) -> Unit, onClose: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Lex.colors.card) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                if (activeFilters(q) > 0) TextButton({ onChange(q.copy(court = "", folder = "", tag = 0)) }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            val fs = f ?: BoardFilters(emptyMap(), emptyList(), emptyList(), emptyList())
            com.legumlex.client.ui.components.WizardDropdown("Court", listOf("" to "Every Court") + courtOptions(fs).map { it.value to "${it.name} (${creditsText(it.cases ?: 0)})" }, q.court) { onChange(q.copy(court = it)) }
            com.legumlex.client.ui.components.WizardDropdown("Folder", listOf("" to "Every Folder") + folderOptions(fs).map { it.value to (it.name + (it.cases?.let { n -> " (${creditsText(n)})" } ?: "")) }, q.folder) { onChange(q.copy(folder = it)) }
            com.legumlex.client.ui.components.WizardDropdown("Tag", listOf("0" to "Every Tag") + tagOptions(fs).map { it.value to "${it.name} (${creditsText(it.cases ?: 0)})" }, q.tag.toString()) { onChange(q.copy(tag = it.toIntOrNull() ?: 0)) }
            LegumPrimaryButton("Show Cases", onClose)
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




/** The Cases list's state, held by the screen's back-stack entry so it survives opening a case and coming back. */
class CasesListModel : androidx.lifecycle.ViewModel() {
    var query by mutableStateOf(CASES_START)
    var offset by mutableStateOf(0)
    var page by mutableStateOf<CasePage?>(null)
    var filters by mutableStateOf<BoardFilters?>(null)
    var listSavedAt by mutableStateOf<Long?>(null)
    var open by mutableStateOf(setOf<Int>())
    val records = androidx.compose.runtime.snapshots.SnapshotStateMap<Int, CaseRecord>()
    val listState = androidx.compose.foundation.lazy.LazyListState()
}
