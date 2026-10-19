package com.legumlex.client.feature.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.R
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.Inter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val vm: NoticesViewModel = viewModel()
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val grouped by vm.grouped.collectAsState()
    val refreshing by vm.isRefreshing.collectAsState()
    val allFlat by vm.allFlat.collectAsState()

    var showSort by remember { mutableStateOf(false) }

    // The app-wide header (logo · back · notifications) sits above via the Scaffold.
    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {

        // Content heading — title + New (the app header has no per-screen actions).
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Legal notices", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = LlColor.Ink, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Ink).clickable(onClick = onNew).padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_ms_add), null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
            }
        }

        FilterBar(filter, counts(allFlat), vm::setFilter)
        SortBar(sort, state) { showSort = true }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when (val s = state) {
                is NoticesUiState.Loading -> Skeleton()
                is NoticesUiState.Error -> ErrorBlock(s.message, vm::load)
                is NoticesUiState.Ready ->
                    if (s.sections.all { it.roots.isEmpty() }) Empty(onNew)
                    else LazyColumn(Modifier.fillMaxSize()) {
                        s.sections.forEach { section ->
                            section.band?.let { band ->
                                item(key = "band-${band.name}") { GBand(band, section.roots) }
                            }
                            section.roots.forEach { root ->
                                item(key = root.id) {
                                    Column {
                                        NoticeRow(root) { onOpen(root.id) }
                                        root.children.forEach { child ->
                                            NoticeRow(child, nested = true) { onOpen(child.id) }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = sort, grouped = grouped,
            onPick = { vm.setSort(it); showSort = false },
            onToggleGroup = { vm.setGrouped(it) },
            onDismiss = { showSort = false },
        )
    }
}

/* --------------------------------------------------------------- filters */

private data class FilterCounts(val all: Int, val outbound: Int, val inbound: Int, val active: Int, val closed: Int) {
    fun of(f: NoticeFilter) = when (f) {
        NoticeFilter.ALL -> all; NoticeFilter.OUTBOUND -> outbound; NoticeFilter.INBOUND -> inbound
        NoticeFilter.ACTIVE -> active; NoticeFilter.CLOSED -> closed
    }
}

private fun counts(flat: List<NoticeSummary>): FilterCounts = FilterCounts(
    all = flat.size,
    outbound = flat.count { it.direction == NoticeDirection.OUTBOUND },
    inbound = flat.count { it.direction == NoticeDirection.INBOUND },
    active = flat.count { !it.isClosed },
    closed = flat.count { it.isClosed },
)

@Composable
private fun FilterBar(active: NoticeFilter, counts: FilterCounts, onPick: (NoticeFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(LlColor.Paper).horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NoticeFilter.values().forEach { f ->
            val on = f == active
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) LlColor.Ink else LlColor.Paper1)
                    .border(1.dp, if (on) LlColor.Ink else LlColor.Border, RoundedCornerShape(8.dp))
                    .clickable { onPick(f) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (on) LlColor.Paper else LlColor.InkMuted)
                Spacer(Modifier.width(4.dp))
                Text("${counts.of(f)}", fontFamily = Inter, fontSize = 9.sp, color = (if (on) LlColor.Paper else LlColor.InkMuted).copy(alpha = 0.7f))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
}

/* ----------------------------------------------------------------- sort */

@Composable
private fun SortBar(sort: NoticeSort, state: NoticesUiState, onSort: () -> Unit) {
    val ready = state as? NoticesUiState.Ready
    Row(
        Modifier.fillMaxWidth().background(LlColor.Paper).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp))
                .clickable(onClick = onSort).padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_ms_swap_vert), null, tint = LlColor.InkMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("SORT", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.4.sp, color = LlColor.InkFaint)
            Spacer(Modifier.width(6.dp))
            Text(sort.short(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = LlColor.Ink)
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_ms_expand_more), null, tint = LlColor.InkMuted, modifier = Modifier.size(16.dp))
        }
        if (ready != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_ms_layers), null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("${ready.total} notices · ${ready.threadCount} threads", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
}

private fun NoticeSort.short(): String = when (this) {
    NoticeSort.DEADLINE -> "Deadline"; NoticeSort.RECENT -> "Recent"
    NoticeSort.AMOUNT -> "Amount"; NoticeSort.STATUS -> "Status"
}

private fun NoticeSort.icon(): Int = when (this) {
    NoticeSort.DEADLINE -> R.drawable.ic_ms_schedule
    NoticeSort.RECENT -> R.drawable.ic_ms_history
    NoticeSort.AMOUNT -> R.drawable.ic_ms_payments
    NoticeSort.STATUS -> R.drawable.ic_ms_label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: NoticeSort, grouped: Boolean,
    onPick: (NoticeSort) -> Unit, onToggleGroup: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = LlColor.Paper) {
        Text("SORT BY", fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 1.4.sp, color = LlColor.InkFaint, modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp))
        NoticeSort.values().forEach { opt ->
            val on = opt == current
            Row(
                Modifier.fillMaxWidth().clickable { onPick(opt) }.padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(opt.icon()), null, tint = if (on) LlColor.Slate else LlColor.InkMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(opt.label, fontFamily = Inter, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, color = if (on) LlColor.Slate else LlColor.Ink)
                    opt.sub?.let { Text(it, fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint) }
                }
                if (on) Icon(painterResource(R.drawable.ic_ms_check), null, tint = LlColor.Slate, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        // group-by-priority toggle
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Group by priority", fontFamily = Inter, fontSize = 13.sp, color = LlColor.Ink)
                Text("Needs you → In progress → Closed", fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
            }
            Toggle(grouped) { onToggleGroup(!grouped) }
        }
    }
}

@Composable
private fun Toggle(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(width = 42.dp, height = 24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) LlColor.Slate else LlColor.BorderStrong).clickable(onClick = onToggle),
    ) {
        Box(
            Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 2.dp)
                .size(20.dp).clip(CircleShape).background(Color.White),
        )
    }
}

/* ----------------------------------------------------------- group band */

@Composable
private fun GBand(band: NoticeBand, roots: List<NoticeSummary>) {
    val alert = band == NoticeBand.NEEDS_YOU
    val (icon, tint) = when (band) {
        NoticeBand.NEEDS_YOU -> R.drawable.ic_ms_priority_high to LlColor.ClaretMid
        NoticeBand.IN_PROGRESS -> R.drawable.ic_ms_hourglass_top to LlColor.InkMuted
        NoticeBand.CLOSED -> R.drawable.ic_ms_lock to LlColor.InkMuted
    }
    val total = roots.sumOf { 1 + it.children.size }
    val countText = when (band) {
        NoticeBand.NEEDS_YOU -> "$total awaiting you"
        NoticeBand.IN_PROGRESS -> "$total active"
        NoticeBand.CLOSED -> "$total"
    }
    Row(
        Modifier.fillMaxWidth().background(LlColor.Paper1).padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(16.dp))
            Text(band.label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LlColor.Ink)
        }
        Text(countText, fontFamily = Inter, fontSize = 10.sp, color = if (alert) LlColor.ClaretMid else LlColor.InkFaint)
    }
}

/* ------------------------------------------------------------ rich rows */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeRow(n: NoticeSummary, nested: Boolean = false, onClick: () -> Unit) {
    val c = dirColors(n.direction)
    val stripe = if (n.isClosed) LlColor.BorderStrong else c.mid
    val tileBg = if (n.isClosed) LlColor.Paper2 else c.soft
    val tileFg = if (n.isClosed) LlColor.InkMuted else c.solid
    Box(Modifier.fillMaxWidth().background(if (nested) LlColor.Paper else Color.White).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (nested) 14.dp else 0.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (nested) {
                Box(Modifier.width(24.dp).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
                    Icon(painterResource(R.drawable.ic_ms_subdirectory_arrow_right), null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.width(3.dp).height(if (nested) 38.dp else 44.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(stripe))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(if (nested) 38.dp else 44.dp).clip(RoundedCornerShape(8.dp)).background(tileBg), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (n.direction == NoticeDirection.OUTBOUND) R.drawable.ic_ms_outgoing_mail else R.drawable.ic_ms_mark_email_unread), null, tint = tileFg, modifier = Modifier.size(if (nested) 19.dp else 21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // ltop — dirbadge + (wrapping) title
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DirBadge(n.direction)
                    Text(
                        (if (n.direction == NoticeDirection.OUTBOUND) "To " else "From ") + n.counterparty,
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = if (nested) 12.5.sp else 13.5.sp,
                        color = if (n.isClosed) LlColor.InkMuted else LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                // lfields — ref · amount · reply count. FlowRow so a narrow
                // screen wraps the chips onto a second line instead of crushing
                // the last one into vertical one-character-per-line text.
                FlowRow(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    n.referenceNumber?.let { Text(it, fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkMuted, maxLines = 1, softWrap = false) }
                    money(n.claimAmount)?.let { Text(it, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = LlColor.Ink, maxLines = 1, softWrap = false) }
                    if (n.children.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_ms_reply), null, tint = LlColor.InkFaint, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("${n.children.size} repl" + if (n.children.size == 1) "y" else "ies", fontFamily = Inter, fontSize = 9.5.sp, color = LlColor.InkFaint, maxLines = 1, softWrap = false)
                        }
                    }
                }
                // lfacets — countdown + your-action tag. Hide the response
                // countdown (incl. the "overdue" state) once a response has been
                // recorded — a replied/closed/expired notice is no longer "due".
                val responded = n.children.isNotEmpty() ||
                    n.status in listOf("Response Received", "No Response", "Expired")
                val showCd = !n.isClosed && !responded && n.responseDueDate != null
                if (showCd || n.needsApproval) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (showCd) CountdownPill(n.responseDueDate!!)
                        if (n.needsApproval) StateTag(if (n.direction == NoticeDirection.INBOUND) "Approve reply" else "Approve draft")
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(n)
                Text(relTime(n.createdAt), fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun DirBadge(d: NoticeDirection) {
    val c = dirColors(d)
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(c.soft).padding(start = 6.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (d == NoticeDirection.OUTBOUND) R.drawable.ic_ms_north_east else R.drawable.ic_ms_south_west), null, tint = c.solid, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (d == NoticeDirection.OUTBOUND) "OUT" else "IN", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.5.sp, color = c.solid)
    }
}

@Composable
private fun StateTag(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(LlColor.AmberSoft).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.4.sp, color = LlColor.Amber)
    }
}

@Composable
private fun StatusChip(n: NoticeSummary) {
    val (bg, fg) = when {
        n.isClosed -> LlColor.Paper2 to LlColor.InkMuted
        n.needsApproval -> LlColor.AmberSoft to LlColor.Amber
        n.direction == NoticeDirection.OUTBOUND -> LlColor.SlateSoft to LlColor.SlateMid
        else -> LlColor.AmberSoft to LlColor.Amber
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(chipLabel(n), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun CountdownPill(due: LocalDate) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), due)
    val soon = days <= 10
    val bg = if (soon) LlColor.ClaretSoft else LlColor.AmberSoft
    val fg = if (soon) LlColor.ClaretMid else LlColor.Amber
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_ms_schedule), null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (days < 0) "overdue" else "${days}d to respond", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = fg)
    }
}

private fun chipLabel(n: NoticeSummary): String = when {
    n.needsApproval -> "YOUR OK"
    n.isClosed && n.status == "Response Received" -> "REPLIED"
    n.isClosed -> "CLOSED"
    n.direction == NoticeDirection.INBOUND && n.children.isNotEmpty() -> "REPLIED"
    n.status in listOf("Dispatched", "Sent") -> "SENT"
    n.status in listOf("Served", "Delivered") -> "SERVED"
    n.status == "Received" -> "RECEIVED"
    n.status == "Draft" -> if (n.direction == NoticeDirection.INBOUND) "REVIEWING" else "DRAFTING"
    n.status == "Response Received" -> "REPLIED"
    n.status == "No Response" -> "NO REPLY"
    n.status == "Expired" -> "EXPIRED"
    else -> n.status.uppercase()
}

private fun relTime(dt: LocalDateTime?): String {
    if (dt == null) return ""
    val mins = Duration.between(dt, LocalDateTime.now()).toMinutes().coerceAtLeast(0)
    return when {
        mins < 1 -> "now"; mins < 60 -> "${mins}m"; mins < 1440 -> "${mins / 60}h"
        mins < 10080 -> "${mins / 1440}d"; else -> "${mins / 10080} wk"
    }
}

/* ---------------------------------------------------------------- states */

@Composable
private fun Empty(onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)).padding(vertical = 38.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_ms_gavel), null, tint = LlColor.InkFaint, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text("No notices yet", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LlColor.Ink)
            Spacer(Modifier.height(7.dp))
            Text("Send a legal notice, or upload one you've received and we'll help you respond.", fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted, modifier = Modifier.fillMaxWidth(0.85f))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Ink).clickable(onClick = onNew).padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_ms_add), null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Start a notice", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.AmberSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Inventory2, null, tint = LlColor.Amber, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Couldn't load notices", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
                Text(message, fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.InkMuted)
            }
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = LlColor.Ink, modifier = Modifier.size(15.dp))
                Text("Retry", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
            }
        }
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        repeat(5) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.width(190.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.width(120.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(LlColor.Paper2))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        }
    }
}
