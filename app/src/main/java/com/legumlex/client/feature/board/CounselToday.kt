package com.legumlex.client.feature.board

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SavedSearch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.feature.consultations.apiError
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.WizardDialog
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

// -----------------------------------------------------------------------------
// The Counsel home (REQ-008, app screen 1): the web's Today, built on
// board/today. The server counts and words everything; the app draws it. Colour
// is held to red (short credits, a date to settle) and the existing gold accent.
// -----------------------------------------------------------------------------

/** The gold the Consult desk already uses: print, and the bar's centre. */
internal val CounselGold = Color(0xFFF5C518)
private val Ink = Color(0xFF1D1D1B)

sealed interface TodayState {
    data object Loading : TodayState
    data class Ready(val today: CounselToday) : TodayState
    data class Error(val message: String, val offline: Boolean) : TodayState
}

class CounselTodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BoardRepository(app)
    private val _state = MutableStateFlow<TodayState>(TodayState.Loading)
    val state: StateFlow<TodayState> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load() { viewModelScope.launch { _state.value = TodayState.Loading; fetch() } }

    fun refresh() { viewModelScope.launch { _refreshing.value = true; fetch(); _refreshing.value = false } }

    private suspend fun fetch() {
        repo.today()
            .onSuccess {
                _state.value = TodayState.Ready(it)
                BoardHub.setChip(it.credits.balance, it.credits.low)
            }
            .onFailure { e ->
                val err = e.apiError()
                if (err.code == "board_off" || err.http == 404 || err.http == 403) BoardHub.hide()
                // A page already shown stays when a refresh cannot reach the server.
                if (_state.value !is TodayState.Ready) _state.value = TodayState.Error(e.boardMessage(), e.isOffline())
            }
    }
}

/**
 * Where a tooltip sits: above its button, its right edge on the button's, then kept inside the window
 * (the default provider centred it and ran off the right edge for a button at the screen's side).
 * Below the button when there is no room above.
 */
internal val OnScreenTooltip = object : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val margin = 24
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        val above = anchorBounds.top - popupContentSize.height - 12
        val y = if (above >= margin) above else anchorBounds.bottom + 12
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/** What a button does, by symbol; a long-press names it (TooltipBox), and a screen reader reads it. */
enum class ActionLook { Gold, Outline, OnDark, Ink }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAction(icon: ImageVector, name: String, onClick: () -> Unit, modifier: Modifier = Modifier, look: ActionLook = ActionLook.Outline, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(8.dp)
    val bg: Color
    val fg: Color
    val line: Color?
    when (look) {
        ActionLook.Gold -> { bg = CounselGold; fg = Ink; line = null }
        ActionLook.Outline -> { bg = Color.Transparent; fg = Lex.colors.foreground; line = Lex.colors.input }
        ActionLook.Ink -> { bg = Color.Black; fg = Color.White; line = null }
        ActionLook.OnDark -> { bg = Color.Transparent; fg = Lex.colors.primaryForeground; line = Lex.colors.primaryForeground.copy(alpha = 0.45f) }
    }
    TooltipBox(
        positionProvider = OnScreenTooltip,
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier.size(size).clip(shape).background(bg).let { if (line != null) it.border(1.dp, line, shape) else it }
                .clickable(onClick = onClick).semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun CounselCalendarScreen(onCredits: () -> Unit, onOpenCase: (Int) -> Unit) {
    val vm: CounselTodayViewModel = viewModel()
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    when (val s = state) {
        TodayState.Loading -> Box(Modifier.fillMaxSize().background(Lex.colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
        }
        is TodayState.Error -> Box(Modifier.fillMaxSize().background(Lex.colors.background).padding(16.dp), contentAlignment = Alignment.Center) {
            LexCard {
                LexEmpty(
                    title = if (s.offline) "You Are Offline" else "Today Could Not Be Opened", body = s.message,
                    icon = if (s.offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                    action = { LegumSecondaryButton("Try Again", vm::load) },
                )
            }
        }
        is TodayState.Ready -> CalendarPage(s.today, vm::refresh, onCredits, onOpenCase)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPage(t: CounselToday, onRefresh: () -> Unit, onCredits: () -> Unit, onOpenCase: (Int) -> Unit) {
    val context = LocalContext.current
    val today = BoardDates.today()
    var importing by rememberSaveable { mutableStateOf(false) }
    var byCnr by rememberSaveable { mutableStateOf(false) }
    var formatNote by rememberSaveable { mutableStateOf(false) }
    var answering by remember { mutableStateOf<Pair<NeedsEye, DateAnswer>?>(null) }
    var mismatches by remember { mutableStateOf<List<Mismatch>>(emptyList()) }
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    // The chosen day (kept when the Calendar is left and opened again), the counts on the week's dots, the day's cases.
    var selectedIso by rememberSaveable { mutableStateOf(BoardHub.chosen.value ?: BoardDates.api(today)) }
    val selected = BoardDates.parse(selectedIso) ?: today
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var dayItems by remember { mutableStateOf<List<BoardCase>?>(null) }
    var dayError by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var eyeOpen by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<BoardCase>?>(null) }
    var foundError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(selectedIso) { BoardHub.choose(selectedIso) }
    androidx.compose.runtime.LaunchedEffect(weekStart(selected), tick) { if (!t.empty) repo.days(weekStart(selected), 7).onSuccess { counts = it } }
    androidx.compose.runtime.LaunchedEffect(selectedIso, tick) {
        if (t.empty) return@LaunchedEffect
        dayItems = null
        repo.day(selectedIso).onSuccess { dayItems = it.items; dayError = null }.onFailure { dayError = it.boardMessage() }
    }
    androidx.compose.runtime.LaunchedEffect(q, tick) {
        if (t.empty || q.isBlank()) { found = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        repo.cases(q.trim()).onSuccess { found = it.second; foundError = null }.onFailure { foundError = it.boardMessage() }
    }
    val more = { tick++ }

    if (t.empty) {
        LazyColumn(Modifier.fillMaxSize().background(Lex.colors.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { DayHeading("WELCOME", "Three steps to your first cause list") }
            item { StepOne(onCnr = { byCnr = true }, onImport = { importing = true }) }
            item { StepTwo(onSpecimen = { formatNote = true }) }
            item { StepThree(t.credits) }
        }
    } else Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            t.short?.let { sh -> ShortBanner(sh, onCredits) }
            MonthsRow(selected) { picking = true }
            WeekRow(
                selected, today, counts,
                onPick = { selectedIso = BoardDates.api(it) },
                onSwipe = { selectedIso = BoardDates.api(shiftWeek(selected, it)) },
            )
        }
        Spacer(Modifier.height(14.dp))
        // The day, on a black sheet whose top-right corner sweeps up into the calendar. Print is the bar's.
        LazyColumn(
            // The sheet ends above the floating bar, so the page colour shows around the bar as in the design.
            Modifier.weight(1f).fillMaxWidth().padding(bottom = (com.legumlex.client.ui.navigation.LocalCounselClearance.current - 24.dp).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(SheetBlack),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sheetHeading(selected, today), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color.White)
                    Text(
                        daySubline(counts[selectedIso] ?: dayItems?.size ?: 0, selected == today, BoardDates.checkedLabel(t.checkedAt, today)),
                        fontSize = 12.sp, color = Color(0xFFA3A19A),
                    )
                }
            }
            if (q.isNotBlank()) {
                val all = found
                when {
                    foundError != null && all == null -> item { Text(foundError.orEmpty(), color = Color(0xFFD6D4CF), fontSize = 13.sp) }
                    all == null -> item { Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Color(0xFFA3A19A)) } }
                    all.isEmpty() -> item { Text("No case matches.", color = Color(0xFFA3A19A), fontSize = 14.sp) }
                    else -> items(all, key = { "found-${it.id}" }) { c -> SearchHit(c, today) { onOpenCase(c.id) } }
                }
            } else {
                val items = dayItems
                when {
                    dayError != null && items == null -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dayError.orEmpty(), color = Color(0xFFD6D4CF), fontSize = 13.sp)
                            LegumSecondaryButton("Try Again", { dayError = null; more() })
                        }
                    }
                    items == null -> item { Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Color(0xFFA3A19A)) } }
                    items.isEmpty() -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = Color(0xFF6F6D67), modifier = Modifier.size(32.dp))
                            Text("Nothing Is Listed On This Day", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFD6D4CF))
                            Text("Cases with a hearing on this date appear here.", fontSize = 12.5.sp, color = Color(0xFFA3A19A), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    else -> item {
                        // Each case draws its own stretch of the rail and its node, so the line runs unbroken from the first node to the last.
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items.forEachIndexed { i, c -> TimelineCase(c, { onOpenCase(c.id) }, { n, a -> answering = n to a }, today, first = i == 0, last = i == items.lastIndex) }
                        }
                    }
                }
            }
            // Search and Add stay at the foot.
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF3A3936), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFFA3A19A), modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = q, onValueChange = { q = it }, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Search any case" },
                            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search any case", color = Color(0xFFA3A19A), fontSize = 14.sp); inner() } },
                        )
                    }
                    Box {
                        IconAction(Icons.Outlined.Add, "Add a case", { addMenu = true }, look = ActionLook.OnDark, size = 48.dp)
                        androidx.compose.material3.DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Add By CNR") }, onClick = { addMenu = false; byCnr = true })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Import From eCourts") }, onClick = { addMenu = false; importing = true })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Export All Cases") }, onClick = {
                                addMenu = false
                                scope.launch {
                                    repo.export().onSuccess { shareText(context, it, "myCases.txt") }
                                        .onFailure { Toast.makeText(context, if (it.isOffline()) "The export needs a connection." else it.boardMessage(), Toast.LENGTH_LONG).show() }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        val ps = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    ps.selectedDateMillis?.let { selectedIso = BoardDates.api(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Show", color = Lex.colors.foreground) }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel", color = Lex.colors.foreground) } },
        ) { androidx.compose.material3.DatePicker(state = ps) }
    }

    answering?.let { (c, a) ->
        DateAnswerDialog(c, a, today, onClose = { answering = null }, onDone = {
            answering = null
            Toast.makeText(context, if (a == DateAnswer.EcourtsRight) "eCourts' date is kept." else "Your date is kept.", Toast.LENGTH_SHORT).show()
            tick++; onRefresh()
        })
    }
    if (mismatches.isNotEmpty()) MismatchDialog(mismatches, today, onDone = { mismatches = emptyList(); tick++; onRefresh() })
    if (importing) ImportWizard(onClose = { importing = false }, onDone = {
        importing = false; tick++
        Toast.makeText(context, "${it.added} added, ${it.updated} updated", Toast.LENGTH_LONG).show()
        if (it.mismatches.isNotEmpty()) mismatches = it.mismatches else onRefresh()
    })
    if (byCnr) CnrWizard(onClose = { byCnr = false }, onBuy = { byCnr = false; onCredits() }, onAdded = { _, bal ->
        byCnr = false; tick++
        Toast.makeText(context, "Case added. Balance: ${creditsText(bal)} credits.", Toast.LENGTH_LONG).show()
        onRefresh()
    })
    if (formatNote) AlertDialog(
        onDismissRequest = { formatNote = false },
        title = { Text("Set Your Format On The Web") },
        text = { Text("For now, please set your cause-list format on the Legum Lex website. It will come to the app soon.") },
        confirmButton = { TextButton({ formatNote = false }) { Text("Understood", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

/** A search hit on the black sheet: case number and parties, the court and judge, and the next date. */
@Composable
private fun SearchHit(c: BoardCase, today: LocalDate, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(c.heading, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFA3A19A))
        Text(partiesText(c, Color(0xFF8A877F)), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        if (c.courtLine.isNotBlank()) Text(c.courtLine, fontSize = 12.5.sp, color = Color(0xFFA3A19A))
        Text(c.nextDate?.let { "Next: ${BoardDates.show(it, today)}" } ?: "No Next Date", fontSize = 12.sp, color = Color(0xFFD6D4CF))
    }
}

// ---- the page's parts -----------------------------------------------------------

@Composable
private fun DayHeading(big: String, line: String) {
    Column {
        Text(
            big, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp,
            letterSpacing = 0.5.sp, color = Lex.colors.foreground,
        )
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
    }
}

@Composable
private fun DarkCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lex.colors.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

@Composable
internal fun Tile(value: String, label: String, modifier: Modifier, info: String? = null) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 26.sp, color = Lex.colors.foreground)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Lex.colors.secondaryForeground)
        }
        // In a box of its own: the corner alignment is the box's, whatever the tip does inside.
        info?.let { Box(Modifier.align(Alignment.TopEnd)) { InfoTip(it, hit = 32.dp) } }
    }
}

@Composable
private fun ShortBanner(s: CheckShort, onCredits: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val red = Lex.colors.danger
    Row(
        Modifier.fillMaxWidth().clip(shape).background(red.softBg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Credits ran short this morning", style = MaterialTheme.typography.titleSmall, color = red.softText)
            Text(
                "${s.checked} of ${s.of} ${plural(s.of, "case was", "cases were")} checked.",
                style = MaterialTheme.typography.bodySmall, color = red.softText,
            )
        }
        InfoTip("${s.unchecked} ${plural(s.unchecked, "case was", "cases were")} not checked. Add credits and the rest are checked tomorrow.", label = "Why credits ran short")
        TextButton(onCredits) { Text("Add Credits", color = red.softText, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun NeedsEyeCard(items: List<NeedsEye>, today: LocalDate, onKeep: (NeedsEye) -> Unit, onAccept: (NeedsEye) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Needs Your Eye", Modifier.weight(1f), color = Lex.colors.secondaryForeground)
            Text(items.size.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Lex.colors.danger.softText)
        }
        items.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString { if (c.heading.isNotBlank()) append(c.heading + "  ·  "); append(partiesText("", "", c.title, c.cnr, Color(0xFF8A877F))) },
                        style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground,
                    )
                    Text(eyeLine(c, today), style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                }
                IconAction(Icons.Outlined.Check, "eCourts is right: accept its date", { onAccept(c) }, look = ActionLook.Ink)
                IconAction(Icons.Outlined.EventAvailable, "Keep my date", { onKeep(c) }, look = ActionLook.Outline)
            }
        }
    }
}

internal fun eyeLine(c: NeedsEye, today: LocalDate): String {
    val ec = BoardDates.show(c.ecourtsDate, today)
    val was = BoardDates.show(c.earlierDate, today)
    return when {
        ec.isNotBlank() && was.isNotBlank() -> "eCourts shows $ec; your diary had $was"
        ec.isNotBlank() -> "eCourts shows $ec"
        else -> "eCourts and your diary differ"
    }
}

// ---- the empty state: three steps -------------------------------------------------

@Composable
private fun StepOne(onCnr: () -> Unit, onImport: () -> Unit) {
    val onDark = Lex.colors.primaryForeground
    DarkCard {
        Eyebrow("Step 1 · Bring Your Cases", color = onDark.copy(alpha = 0.8f))
        Text(
            "Import the case list you already keep in the eCourts app, or add a case by its CNR.",
            style = MaterialTheme.typography.bodyLarge, color = onDark,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            IconAction(Icons.Outlined.SavedSearch, "Add a case by its CNR", onCnr, look = ActionLook.OnDark, size = 48.dp)
            IconAction(Icons.Outlined.FileUpload, "Import cases from the eCourts app", onImport, look = ActionLook.Gold, size = 48.dp)
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, body: String, action: (@Composable RowScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).border(1.dp, Lex.colors.ring, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Lex.colors.foreground)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
            action?.let { Row(Modifier.padding(top = 6.dp), content = it) }
        }
    }
}

@Composable
private fun StepTwo(onSpecimen: () -> Unit) = StepCard(
    2, "Set your cause-list format",
    "Upload a sample of the cause list you use. Choose its columns, portrait or landscape, and paper size.",
) { IconAction(Icons.Outlined.UploadFile, "Upload a specimen of your cause list", onSpecimen, look = ActionLook.Outline) }

@Composable
private fun StepThree(c: TodayCredits) = StepCard(
    3, "We check eCourts every morning",
    "Your cause list is ready by 7 AM. Each case checked costs ${c.cost} ${plural(c.cost, "credit", "credits")}; your ${creditsText(c.balance)} credits start you off.",
)

// ---- Correct Date (a wizard) and eCourts Is Right (a confirm) -------------------------

/** The two answers to a date eCourts disagreed about. */
enum class DateAnswer { EcourtsRight, KeepMine }

/** Settles one date: eCourts is right (its date is taken) or Keep my date (ours stays); the server's own refusal is shown. */
@Composable
fun DateAnswerDialog(c: NeedsEye, answer: DateAnswer, today: LocalDate, onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ec = BoardDates.show(c.ecourtsDate, today).takeIf { it.isNotBlank() }
    val mine = BoardDates.show(c.earlierDate, today).takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (answer == DateAnswer.EcourtsRight) "eCourts Is Right?" else "Keep Your Date?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (answer == DateAnswer.EcourtsRight) "${c.heading} takes the date eCourts shows${ec?.let { ", $it" }.orEmpty()}, and stops needing a look."
                    else "${c.heading} keeps your date${mine?.let { ", $it" }.orEmpty()}. eCourts' answer is not asked about again.",
                )
                error?.let { Text(it, color = Lex.colors.danger.softText, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    (if (answer == DateAnswer.EcourtsRight) repo.dateAccept(c.id) else repo.dateKeep(c.id))
                        .onSuccess { onDone() }.onFailure { error = it.boardMessage() }
                    busy = false
                }
            }) { Text(if (busy) "Saving…" else if (answer == DateAnswer.EcourtsRight) "Accept Its Date" else "Keep My Date", color = Lex.colors.foreground) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

/**
 * After an import the imported cases were checked on eCourts, and where a date differs the counsel is asked which is right:
 * eCourts (its date is taken) or the file's (kept). One row each; answered rows leave, and the dialog closes when none are left.
 */
@Composable
fun MismatchDialog(items: List<Mismatch>, today: LocalDate, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var left by remember { mutableStateOf(items) }
    var busy by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(left.isEmpty()) { if (left.isEmpty()) onDone() }
    fun answer(m: Mismatch, a: DateAnswer) {
        busy = m.id; error = null
        scope.launch {
            (if (a == DateAnswer.EcourtsRight) repo.dateAccept(m.id) else repo.dateKeep(m.id))
                .onSuccess { left = left.filter { it.id != m.id } }.onFailure { error = it.boardMessage() }
            busy = null
        }
    }
    if (left.isNotEmpty()) AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Which Date Is Right?") },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                left.forEach { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(androidx.compose.ui.text.buildAnnotatedString { if (m.heading.isNotBlank()) append(m.heading + "  ·  "); append(partiesText("", "", m.title, m.cnr, Color(0xFF8A877F))) }, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                        Text(
                            "eCourts shows ${BoardDates.show(m.ecourtsDate, today).ifBlank { "a different date" }}; your file had ${BoardDates.show(m.yourDate, today).ifBlank { "none" }}",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconAction(Icons.Outlined.Check, "eCourts is right: accept its date", { if (busy == null) answer(m, DateAnswer.EcourtsRight) }, look = ActionLook.Ink)
                            IconAction(Icons.Outlined.EventAvailable, "Keep my date", { if (busy == null) answer(m, DateAnswer.KeepMine) }, look = ActionLook.Outline)
                            if (busy == m.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
                        }
                    }
                }
                error?.let { Text(it, color = Lex.colors.danger.softText, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onDone) { Text("Decide Later", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

