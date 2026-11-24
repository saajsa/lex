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
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
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
    val fetchOrder = orderFetcher(getter, onOpened = { _, oid, title -> onOpenOrder(oid, title) }, onChanged = { load() })
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
        // The pinned head (owner, 23 Nov 2026: fewer buttons): the case and a ⋮ menu; the parties; the watch status as one line;
        // the next date with share and update beside it as plain icons; its filing.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The case's status as a thin edge beside its number, as on its Cases tile. Drawn, not measured: no intrinsic height
            // (a SubcomposeLayout below would refuse it).
            val headEdge = c?.let { edgeColor(rowEdge(it, today)) }
            Box(Modifier.drawBehind { headEdge?.let { col -> drawRoundRect(col, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx())) } }
                .padding(start = if (headEdge != null) 14.dp else 0.dp)) {
            // Owner, 24 Nov 2026: no "Case" eyebrow; the case number, then the case title, in the heading face; the ⋮ on the first line.
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).padding(top = 4.dp)) {
                    c?.let { CaseHeading(it) } ?: Text("…", fontFamily = com.legumlex.client.ui.theme.Display, fontSize = 30.sp, color = Lex.colors.foreground)
                }
                c?.let { x -> OverflowMenu(listOfNotNull(
                    "Details" to { details = true },
                    "File This Case" to { if (live) filing = true },
                    if (combinedOffered(x.record.orders)) "All Kept Orders As One PDF" to { onOpenOrder(0, "All Kept Orders") } else null,
                    "Remove From Your Diary" to { if (live && !busy) confirmRemove = true },
                    "What The Symbols Mean" to { legend = true },
                )) }
            }
            }
            c?.let { x ->
                Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WatchRow(x.morningCheck, enabled = !busy && live) {
                        busy = true; refusal = null; refusalCode = null
                        scope.launch {
                            // The action's reply has no Date History: the case page is read again so the history stays.
                            repo.caseEdit(x.id, mapOf("morning_check" to if (x.morningCheck) "0" else "1")).onSuccess { c = it.copy(history = x.history, record = x.record); load() }
                                .onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() }
                            busy = false
                        }
                    }
                    CaseFilingLine(x)
                    savedAt?.let { at -> NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") }
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
                // Owner, 24 Nov 2026: the next date element is gone (the hearings list carries it): its share and refresh sit on the
                // Hearings header, its eCourts mark and red rules on the top hearing row.
                CaseRecordSection(x.record, x.hasCnr, readLabel, onRead, enabled = live && !getter.fetching,
                    nextDate = x.nextDate, dot = rowEdge(x, today).takeIf { it == Edge.Red || it == Edge.Green }?.let { edgeColor(it) },
                    mark = dateMark(x, today)?.takeIf { it != Sym.Undated }, moved = movedToday(x, today),
                    actions = {
                        PlainIcon(Icons.Outlined.Share, "Share Summary") {
                            if (!busy) { refusal = null; scope.launch { repo.shareText(x).onSuccess { sharing = it }.onFailure { refusal = it.boardMessage(); refusalCode = it.boardCode() } } }
                        }
                        if (x.hasCnr) PlainIcon(Icons.Outlined.Refresh, readLabel) { onRead?.invoke() }
                    },
                    onOpen = { o -> onOpenOrder(o.id, orderLabel(o)) }, onGet = { o -> refusal = null; fetchOrder(caseId, o) }, fetchingId = getter.fetchingId)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (legend) LegendSheet { legend = false }
    c?.let { x ->
        if (details) androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { details = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Lex.colors.background,
        ) {
            Column(Modifier.fillMaxWidth().androidScroll().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Details", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                LexCard {
                    KeyValueRow("CNR", cnrLine(x), mono = x.hasCnr)
                    if (x.caseNo.isNotBlank()) KeyValueRow("Case Number", x.caseNo)
                    if (x.caseType.isNotBlank()) KeyValueRow("Case Type", x.caseType)
                    if (x.petitioner.isNotBlank()) KeyValueRow("Petitioner", x.petitioner)
                    if (x.respondent.isNotBlank()) KeyValueRow("Respondent", x.respondent)
                    if (x.courtShown.isNotBlank()) KeyValueRow("Court", x.courtShown)
                    if (x.judgeShown.isNotBlank()) KeyValueRow("Judge", x.judgeShown)
                    if (x.stage.isNotBlank()) KeyValueRow("Stage", x.stage)
                    BoardDates.parse(x.nextDate)?.let { d -> if (d.isBefore(today)) KeyValueRow("Next Date", "None yet. Last listed ${BoardDates.long(d)}.") else KeyValueRow("Next Date", BoardDates.long(d)) }
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

/** Watch Daily (owner, 23 Nov 2026: "I was in favour of the toggle"): a real switch, green when on, its line muted; no cost shown. */
@Composable
internal fun WatchRow(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(WATCH_TITLE, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
            Text(WATCH_LINE, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        }
        LexToggle(on, enabled, WATCH_TITLE, onToggle)
    }
}

internal const val WATCH_TITLE = "Watch Daily"
internal const val WATCH_LINE = "Checked with eCourts every morning"

/** A plain, borderless icon button with its name on long-press and for screen readers. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PlainIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, onClick: () -> Unit) {
    androidx.compose.material3.TooltipBox(positionProvider = OnScreenTooltip, tooltip = { PlainTooltip { Text(name) } }, state = androidx.compose.material3.rememberTooltipState()) {
        androidx.compose.material3.IconButton(onClick, Modifier.semantics { contentDescription = name }) { Icon(icon, null, tint = Lex.colors.secondaryForeground) }
    }
}

/** A ⋮ menu of named actions. */
@Composable
internal fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton({ open = true }, Modifier.semantics { contentDescription = "More" }) { Icon(Icons.Outlined.MoreVert, null, tint = Lex.colors.foreground) }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = Lex.colors.card) {
            items.forEach { (label, act) -> androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { open = false; act() }) }
        }
    }
}

/** "Next: 28 Nov"; a date gone by is not next: "Listed 8 Nov · no new date yet" (owner, 24 Nov 2026); or "No Next Date". */
internal fun nextLine(c: BoardCase, today: java.time.LocalDate): String {
    val d = BoardDates.parse(c.nextDate) ?: return "No Next Date"
    return if (d.isBefore(today)) listedLine(d, today) else "Next: " + BoardDates.short(d, today)
}

const val NO_NEW_DATE = "no new date yet"

/**
 * The one wording for a next date that has passed with no new one recorded, wherever the counsel workspace shows a next date
 * (owner, 24 Nov 2026: "Next" or "Upcoming" for a past date is wrong): "Listed 8 Nov · Appearance · no new date yet". Shown red.
 */
internal fun listedLine(d: java.time.LocalDate, today: java.time.LocalDate, purpose: String = ""): String =
    listOf("Listed " + BoardDates.short(d, today), purpose.trim(), NO_NEW_DATE).filter { it.isNotEmpty() }.joinToString(" · ")

/** The case's next date when it has passed (no new date recorded yet); null when it is today or later, or unknown. */
internal fun passedNext(nextDate: String?, today: java.time.LocalDate): java.time.LocalDate? = BoardDates.parse(nextDate)?.takeIf { it.isBefore(today) }

/** The folder glyph with its name, then "#tag" text; nothing when the case is unfiled. */
@Composable
internal fun CaseFilingLine(c: BoardCase) {
    if (c.folder == null && c.tags.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        c.folder?.let { SymbolIcon(Sym.Folder, 14.dp); Text(it.name, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground) }
        if (c.tags.isNotEmpty()) Text((if (c.folder != null) "  " else "") + tagsLine(c), style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, maxLines = 1)
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

/** The short labels the segments show under their counts; the long name stays in each one's content description. */
internal val SEGMENT_SHORT = mapOf("ongoing" to "Ongoing", "eye" to "Needs Eye", "undated" to "Undated", "disposed" to "Disposed")

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
fun BoardCasesListScreen(onOpenCase: (Int) -> Unit) {
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
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background), state = m.listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
    ) {
        // Owner, 23 Nov 2026: one +, and the rest in ⋮.
        item {
            PageHeader(eyebrow = "Your Diary", title = "Cases", actions = {
                PlainIcon(Icons.Outlined.Add, "Add By Hand") { byHand = true }
                OverflowMenu(listOf("Folders And Tags" to { managing = true }, "What The Symbols Mean" to { legend = true }))
            })
        }
        item {
            com.legumlex.client.ui.components.LegumTextField(query.q, { change(query.copy(q = it)) }, "Search Cases", Modifier.fillMaxWidth().padding(top = 10.dp), leadingIcon = Icons.Outlined.Search,
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
            StatusSegments(filters?.counts.orEmpty(), query.filter.takeIf { f -> STATUS_SEGMENTS.any { it.first == f } } ?: "ongoing") { change(query.copy(filter = it)) }
        }
        if (query.q.isNotBlank()) item { NoteLine(SEARCH_ALL_NOTE) }
        listSavedAt?.let { at -> item { NoteLine(BoardDates.savedLabel(at, today) + ". Connect to refresh.") } }
        val p = page
        when {
            error != null && p == null -> item { LexCard { LexEmpty("The Cases Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { error = null; tick++ }) }) } }
            p == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) } }
            p.items.isEmpty() -> item { Text(if (query == CASES_START) "No cases yet." else "No case matches.", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground) }
            else -> {
                items(p.items, key = { it.id }) { c ->
                    val ex = c.id in open
                    CaseRow(c, today, { onOpenCase(c.id) }, expanded = ex, onToggle = { open = if (ex) open - c.id else open + c.id })
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
    if (filtering) CaseFilterSheet(filters, query, change) { filtering = false }
}

/**
 * One case as a list row (owner, 23 Nov 2026: minimal, list-like, colour as the symbol): the number bold with the next date and its mark
 * on the right; the parties; the court small and muted with the folder and #tags. A watched case is tinted green with a green start
 * edge; Needs Your Eye or a recent move gives a red edge. A hairline under it; the whole row opens the case.
 */
@Composable
internal fun CaseRow(c: BoardCase, today: java.time.LocalDate, onClick: () -> Unit, expanded: Boolean = false, onToggle: (() -> Unit)? = null) {
    // Owner, 23 Nov 2026: plain card tiles, radius 8, 8dp apart; the status in the 4dp left edge alone (red, black, green, grey).
    // Owner, 24 Nov 2026 ("symmetry and geometry"): the hearing row's own geometry. The case number in the date block's column and rule
    // (type, number, year), the badge, title and court with the hearing text's insets, and the next date in a mirrored block on the right.
    // No toggle icon: with a toggle the whole card opens and closes (Open Case goes to the case); without one (Days Ahead) a tap opens it.
    // Lines and the edge are drawn behind, so the tile needs no intrinsic measurement.
    val edge = edgeColor(rowEdge(c, today))
    val rule = Lex.colors.border
    val no = caseNoBlock(c.caseNo, c.heading)
    val next = BoardDates.parse(c.nextDate)
    val red = next != null && (next.isBefore(today) || movedToday(c, today))
    val nextInk = if (red) Lex.colors.danger.solid else Lex.colors.foreground
    Column(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Lex.colors.card).drawBehind { drawRect(edge, size = Size(4.dp.toPx(), size.height)) }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle ?: onClick)
            .semantics {
                contentDescription = rowSpeech(c, today)
                if (onToggle != null) stateDescription = if (expanded) "Open" else "Closed"
            },
    ) {
        Row(
            Modifier.fillMaxWidth().drawBehind {
                val l = (CARD_INSET + TL_RULE_X).dp.toPx(); val r = size.width - (CARD_INSET + TL_RULE_X).dp.toPx(); val w = 1.dp.toPx()
                drawLine(rule, androidx.compose.ui.geometry.Offset(l, 0f), androidx.compose.ui.geometry.Offset(l, size.height), w)
                drawLine(rule, androidx.compose.ui.geometry.Offset(r, 0f), androidx.compose.ui.geometry.Offset(r, size.height), w)
            }.padding(horizontal = CARD_INSET.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Left: the case number as the hearing date block is set ("CS" small, "777" large, "2021" small), centred both ways.
            Column(Modifier.width(TL_DATE_COL.dp).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                if (no.type.isNotEmpty()) Text(no.type.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Lex.colors.secondaryForeground,
                    letterSpacing = 1.sp, maxLines = 1, softWrap = false)
                Text(no.number, style = MaterialTheme.typography.headlineSmall.copy(fontSize = caseNoFontSp(no.number.length).sp), fontWeight = FontWeight.Bold,
                    color = Lex.colors.foreground, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (no.year.isNotEmpty()) Text(no.year, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, maxLines = 1)
            }
            Spacer(Modifier.width(TL_TEXT_GAP.dp))
            // Middle: the hearing text's insets; the badge, the title, the court.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lastHeardBadge(c.lastDate, today)?.let { b ->
                    Text(b, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Lex.colors.secondaryForeground, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Lex.colors.muted).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(partiesText(c, Lex.colors.mutedForeground), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Lex.colors.foreground)
                courtLine(c).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(TL_TEXT_GAP.dp))
            // Right: the next date, mirrored ("12" over "DEC"), its mark under it; red when moved or passed; none: a dash, muted.
            Column(Modifier.width(TL_DATE_COL.dp).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                if (next == null) Text("—", style = MaterialTheme.typography.headlineSmall, color = Lex.colors.mutedForeground)
                else {
                    Text(next.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = nextInk)
                    Text(next.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase(), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = if (red) nextInk else Lex.colors.secondaryForeground)
                    if (next.year != today.year) Text("${next.year}", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    dateMark(c, today)?.let { Spacer(Modifier.height(4.dp)); SymbolIcon(it, 14.dp) }
                }
            }
        }
        if (expanded) {
            androidx.compose.material3.HorizontalDivider(color = rule)
            Column(Modifier.fillMaxWidth().padding(horizontal = CARD_INSET.dp, vertical = 12.dp)) {
                // Owner, 24 Nov 2026 ("Next date + purpose"): the next listing, its purpose and the days to go; red when moved or within 2 days.
                val line = openNextLine(c, today)
                Text(line?.first ?: "No next date yet", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line != null) FontWeight.SemiBold else null,
                    color = when { line == null -> Lex.colors.mutedForeground; line.second -> Lex.colors.danger.solid; else -> Lex.colors.foreground },
                    modifier = Modifier.padding(bottom = 10.dp))
                LegumPrimaryButton("Open Case", onClick)
            }
        }
    }
}

/** The card's inner side inset: the hearings card's own (its LexCard rows sit 12dp in), after the 4dp status edge. */
internal const val CARD_INSET = 12

/** A case number set like a date block: "CS/777/2021" gives type CS, number 777, year 2021; another form is the number whole; none, the CNR. */
internal data class CaseNoBlock(val type: String, val number: String, val year: String)

internal fun caseNoBlock(caseNo: String, fallback: String): CaseNoBlock {
    val m = Regex("""^\s*(.+?)\s*/\s*(\w+)\s*/\s*(\d{4})\s*$""").find(caseNo)
    return if (m != null) CaseNoBlock(m.groupValues[1], m.groupValues[2], m.groupValues[3]) else CaseNoBlock("", caseNo.trim().ifBlank { fallback }, "")
}

/** The number's size in the 56dp block: the hearing day's 24sp for up to 3 characters, then smaller so it never wraps. */
internal fun caseNoFontSp(len: Int): Int = when {
    len <= 3 -> 24
    len == 4 -> 20
    len == 5 -> 17
    len == 6 -> 14
    len <= 8 -> 12
    else -> 9
}

/** "today", "tomorrow", "in 9 days" (a date to come; a date gone by is read by [openNextLine] as listed, with no new date yet). */
internal fun daysToGo(d: java.time.LocalDate, today: java.time.LocalDate): String = when (val n = java.time.temporal.ChronoUnit.DAYS.between(today, d)) {
    0L -> "today"
    1L -> "tomorrow"
    else -> "in $n days"
}

/** Red when the date was just moved (the list's Moved mark, date_moved_on) or is within 2 days. */
internal fun nextIsUrgent(c: BoardCase, d: java.time.LocalDate, today: java.time.LocalDate): Boolean =
    movedToday(c, today) || java.time.temporal.ChronoUnit.DAYS.between(today, d) <= 2

/**
 * The open card's line and whether it is red; null when no next date is known. A date to come: "Next: 3 Dec · Arguments · in 9 days". A date gone by is
 * not "next": "Listed 8 Nov · Appearance · no new date yet", always red (the counsel must find the new date).
 */
internal fun openNextLine(c: BoardCase, today: java.time.LocalDate): Pair<String, Boolean>? {
    val d = BoardDates.parse(c.nextDate) ?: return null
    val when_ = BoardDates.short(d, today)
    return if (d.isBefore(today)) listedLine(d, today, c.stage) to true
    else listOf("Next: $when_", c.stage.trim(), daysToGo(d, today)).filter { it.isNotEmpty() }.joinToString(" · ") to nextIsUrgent(c, d, today)
}

/** The badge above the title: "Last heard 12 Nov" (the year added when it is not this year's); none without a last date. */
internal fun lastHeardBadge(lastDate: String?, today: java.time.LocalDate): String? =
    BoardDates.show(lastDate, today).takeIf { it.isNotBlank() }?.let { "Last heard $it" }

/** The row's third line: the court, then the folder and the #tags. */
internal fun courtLine(c: BoardCase): String = listOf(c.courtShown, c.folder?.name.orEmpty(), tagsLine(c)).filter { it.isNotBlank() }.joinToString(" · ")

/** What a screen reader says for a row: the case, its next date and mark, and whether it is watched. */
internal fun rowSpeech(c: BoardCase, today: java.time.LocalDate): String =
    listOfNotNull(c.heading, nextLine(c, today), dateMark(c, today)?.label, if (c.morningCheck) "Watched daily" else null).joinToString(". ")

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
    val listState = androidx.compose.foundation.lazy.LazyListState()
}

/**
 * The four status segments, sharing the full width with no scroll (owner, 23 Nov 2026): each its count, bold and larger, over a short
 * label; the chosen one filled ink with white text. Read aloud with the long name and the count.
 */
@Composable
internal fun StatusSegments(counts: Map<String, Int>, selected: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min).clip(shape).border(1.dp, Lex.colors.input, shape)) {
        STATUS_SEGMENTS.forEachIndexed { i, (key, long) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Lex.colors.input))
            val on = key == selected
            val n = counts[key] ?: 0
            Column(
                Modifier.weight(1f).background(if (on) Lex.colors.foreground else Lex.colors.card).clickable { onSelect(key) }
                    .semantics { contentDescription = "$long, ${creditsText(n)}" + if (on) ", selected" else "" }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(creditsText(n), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (on) androidx.compose.ui.graphics.Color.White else Lex.colors.foreground)
                Text(SEGMENT_SHORT[key] ?: long, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = if (on) androidx.compose.ui.graphics.Color.White else Lex.colors.secondaryForeground)
            }
        }
    }
}
