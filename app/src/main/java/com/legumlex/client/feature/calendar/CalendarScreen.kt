package com.legumlex.client.feature.calendar

import androidx.compose.foundation.verticalScroll
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.hearings.Hearing
import com.legumlex.client.feature.hearings.HearingsRepository
import com.legumlex.client.ui.components.DetailHeading
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// Cross-case calendar view. Reached from Home → "View calendar" link.
// (Replaces the standalone Hearings tab — we found the per-case Hearings tab
// inside Case Detail covers the day-to-day need, and the calendar earns its
// own destination only when you want a multi-matter overview.)

private sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Ready(val hearings: List<Hearing>) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
    /** The server refused: the contact's account doesn't include matters. */
    data object Denied : CalendarUiState
}

private class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HearingsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = CalendarUiState.Loading
        repo.list()
            .onSuccess { _state.value = CalendarUiState.Ready(it) }
            .onFailure { e ->
                if (e is HttpException && e.code() == 403) { _state.value = CalendarUiState.Denied; return@onFailure }
                _state.value = CalendarUiState.Error(
                    when (e) {
                        is HttpException -> when (e.code()) {
                            401 -> "Your session expired. Sign in again."
                            in 500..599 -> "Server error. Try again in a moment."
                            else -> "Couldn't load the calendar (HTTP ${e.code()})."
                        }
                        else -> "Couldn't reach the server. Check your connection."
                    }
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenHearing: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(app) } }
    )
    val state by vm.state.collectAsState()

    // The app shell already pads for the system bars.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            DetailHeading(
                eyebrow = "Court Schedule",
                title = "Calendar",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(Modifier.fillMaxSize()) {
                when (val s = state) {
                    is CalendarUiState.Loading -> CalendarSkeleton()
                    is CalendarUiState.Error   -> CalendarError(s.message, vm::load)
                    is CalendarUiState.Denied  -> com.legumlex.client.ui.components.PermissionEmptyState(
                        feature = "Calendar",
                        message = "Your account doesn't include court matters, so there are no hearings to show. Ask the firm if you expected to see them.",
                    )
                    is CalendarUiState.Ready   -> CalendarBody(s.hearings, onOpenHearing)
                }
            }
        }
    }
}

// --- Calendar body --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarBody(hearings: List<Hearing>, onOpenHearing: (String) -> Unit) {
    val spacing = LocalSpacing.current

    val today = remember { LocalDate.now() }
    var visibleMonth by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(YearMonth.from(today))
    }
    var selected by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf<LocalDate?>(null)
    }

    val byDate: Map<LocalDate, List<Hearing>> = remember(hearings) {
        hearings.filter { it.date != null }.groupBy { it.date!! }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = spacing.s4),
    ) {
        // Month nav
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryText,
                modifier = Modifier.weight(1f),
            )
            ChevronButton(Icons.Outlined.ChevronLeft, "Previous month") { visibleMonth = visibleMonth.minusMonths(1) }
            Spacer(Modifier.width(8.dp))
            ChevronButton(Icons.Outlined.ChevronRight, "Next month")     { visibleMonth = visibleMonth.plusMonths(1) }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("S","M","T","W","T","F","S").forEach { d ->
                Box(
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d, style = MaterialTheme.typography.labelSmall, color = TertiaryText)
                }
            }
        }

        val cells = remember(visibleMonth) { buildMonthCells(visibleMonth) }
        Column {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell        = cell,
                            isToday     = cell.date == today,
                            isSelected  = cell.date == selected,
                            hasHearings = byDate.containsKey(cell.date),
                            modifier    = Modifier.weight(1f).aspectRatio(1f),
                            onClick     = { selected = cell.date },
                        )
                    }
                }
            }
        }

        // The month's hearings, listed under the grid, so a month with dates in
        // it says what they are without tapping each day.
        val inMonth = remember(hearings, visibleMonth) {
            hearings.filter { it.date?.let { d -> YearMonth.from(d) == visibleMonth } == true }
                .sortedWith(compareBy({ it.date }, { it.time }))
        }
        Spacer(Modifier.height(spacing.s4))
        Text(
            "Hearings In ${visibleMonth.format(DateTimeFormatter.ofPattern("MMMM"))}",
            style = MaterialTheme.typography.titleSmall,
            color = PrimaryText,
        )
        Spacer(Modifier.height(spacing.s2))
        if (inMonth.isEmpty()) {
            Text("No hearings are listed for this month.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        } else {
            inMonth.forEach { h ->
                Text(
                    h.date!!.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = spacing.s2, bottom = 4.dp),
                )
                SheetHearingCard(h) { onOpenHearing(h.id) }
            }
        }
        Spacer(Modifier.height(spacing.s5))
    }

    selected?.let { day ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState       = sheetState,
            containerColor   = Surface2,
        ) {
            SheetContent(
                day      = day,
                hearings = byDate[day].orEmpty(),
                onOpen   = onOpenHearing,
            )
        }
    }
}

@Composable
private fun ChevronButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        border  = BorderStroke(1.dp, BorderColor),
        shape   = CircleShape,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = PrimaryText, modifier = Modifier.size(16.dp))
        }
    }
}

private data class MonthCell(val date: LocalDate, val inMonth: Boolean)

private fun buildMonthCells(month: YearMonth): List<MonthCell> {
    val firstOfMonth = month.atDay(1)
    val sundayOffset = (firstOfMonth.dayOfWeek.value % 7)
    val start = firstOfMonth.minusDays(sundayOffset.toLong())
    return (0 until 42).map { i ->
        val d = start.plusDays(i.toLong())
        MonthCell(date = d, inMonth = YearMonth.from(d) == month)
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    isToday: Boolean,
    isSelected: Boolean,
    hasHearings: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberColor = when {
        isSelected    -> InverseText
        !cell.inMonth -> TertiaryText
        else          -> PrimaryText
    }
    Box(modifier = modifier.padding(2.dp), contentAlignment = Alignment.Center) {
        when {
            isSelected -> Box(Modifier.size(34.dp).clip(CircleShape).background(AccentInk))
            isToday    -> Surface(
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, AccentInk),
                modifier = Modifier.size(34.dp),
            ) { }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(2.dp),
        ) {
            Surface(
                color   = Color.Transparent,
                onClick = onClick,
                shape   = CircleShape,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        cell.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = numberColor,
                    )
                }
            }
            if (hasHearings) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) InverseText else AccentInk),
                )
            }
        }
    }
}

@Composable
private fun SheetContent(day: LocalDate, hearings: List<Hearing>, onOpen: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = spacing.s4)
        .padding(bottom = spacing.s5)) {
        Text(
            day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryText,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (hearings.isEmpty()) "No hearings on this day."
            else "${hearings.size} ${if (hearings.size == 1) "hearing" else "hearings"}",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
        )
        Spacer(Modifier.height(spacing.s3))
        hearings.forEach { h ->
            SheetHearingCard(h) { onOpen(h.id) }
            Spacer(Modifier.height(spacing.s2))
        }
    }
}

@Composable
private fun SheetHearingCard(h: Hearing, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        shape   = RoundedCornerShape(12.dp),
        border  = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    h.caseTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryText,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        h.courtName,
                        h.time?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Time TBA",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(h.status.label, h.status.palette)
        }
    }
}

// --- Loading / Error -----------------------------------------------------

@Composable
private fun CalendarSkeleton() {
    val spacing = LocalSpacing.current
    Column(modifier = Modifier.fillMaxSize().padding(spacing.s4)) {
        Box(Modifier.size(width = 160.dp, height = 22.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor))
        Spacer(Modifier.height(spacing.s4))
        Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)).background(BorderColor))
    }
}

@Composable
private fun CalendarError(message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(StatusDanger.softBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = StatusDanger.solid, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.s4))
        Text("Couldn't Load The Calendar",
            style = MaterialTheme.typography.headlineSmall,
            color = PrimaryText,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s2))
        Text(message,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s5))
        LegumSecondaryButton(
            text        = "Try again",
            leadingIcon = Icons.Outlined.Refresh,
            onClick     = onRetry,
            modifier    = Modifier.widthIn(max = 280.dp),
        )
    }
}

// --- Savers --------------------------------------------------------------

private val YearMonthSaver = Saver<YearMonth, String>(
    save    = { it.toString() },
    restore = { YearMonth.parse(it) },
)

private val LocalDateSaver = Saver<LocalDate?, String>(
    save    = { it?.toString() ?: "" },
    restore = { if (it.isBlank()) null else LocalDate.parse(it) },
)
