package com.legumlex.client.feature.notifications

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.legumlex.client.ui.components.DayEmpty
import com.legumlex.client.ui.components.DayHead
import com.legumlex.client.ui.components.DayStrip
import com.legumlex.client.ui.components.TimelineItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.AccentInk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val vm: NotificationsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val unread = (state as? NotificationsUiState.Ready)?.unreadCount ?: 0

    Column(Modifier.fillMaxSize().background(com.legumlex.client.ui.theme.Lex.colors.background)) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is NotificationsUiState.Loading -> Column { TopBar(unread, onBack, vm::markAllRead); SkeletonList() }
                is NotificationsUiState.Error   -> Column { TopBar(unread, onBack, vm::markAllRead); ErrorBlock(s.message, onRetry = vm::load) }
                is NotificationsUiState.Ready   -> DayFeed(
                    items = s.bands.flatMap { it.items },
                    unread = unread,
                    onBack = onBack,
                    onMarkAll = vm::markAllRead,
                    onOpen = { vm.markRead(it.id); onOpen(it) },
                )
            }
        }
    }
}

/* ------------------------------------------------------- the day timeline */

private val Clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)

@Composable
private fun TopBar(unread: Int, onBack: () -> Unit, onMarkAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = com.legumlex.client.ui.theme.Lex.colors.mutedForeground, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.weight(1f))
        val enabled = unread > 0
        Text(
            "Mark All Read",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (enabled) com.legumlex.client.ui.theme.Lex.colors.foreground else com.legumlex.client.ui.theme.Lex.colors.faint,
            modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onMarkAll) else Modifier).padding(10.dp),
        )
    }
}

private val ShortDate = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)
private val SinceDate = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

/** How far back the strip runs; anything older is under "Earlier". */
private const val SpanDays = 30L

/** The strip's choice: a day, or everything before it (Earlier). */
private const val EARLIER = Long.MIN_VALUE

/**
 * The feed (customers_api notifications): matters waiting on the client, and
 * the firm's announcements. What waits is not an event of a day; it is pinned
 * above the days, as the portal drawer leads with it, until the step is done.
 * The announcements hang on the days they were published; one is read where
 * it stands (it opens nowhere), so tapping it shows it in full.
 */
@Composable
private fun DayFeed(
    items: List<AppNotification>,
    unread: Int,
    onBack: () -> Unit,
    onMarkAll: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    val first = today.minusDays(SpanDays - 1)
    val waiting = items.filter { it.type == NotifType.ATTENTION }
    val news = items.filterNot { it.type == NotifType.ATTENTION }
    // Undated is today's; anything before the span is Earlier's.
    val keyOf: (AppNotification) -> Long = { n ->
        val d = n.createdAt?.toLocalDate() ?: today
        if (d.isBefore(first)) EARLIER else minOf(d, today).toEpochDay()
    }
    val byKey = remember(news) { news.groupBy(keyOf) }
    val days = remember(first) { generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList() }
    val unreadKeys = news.filter { !it.isRead }.map(keyOf).toSet()
    // Open on the latest day with something unread; else the latest with anything; else today.
    val opening = (unreadKeys.filter { it != EARLIER }.maxOrNull()
        ?: unreadKeys.firstOrNull()
        ?: byKey.keys.filter { it != EARLIER }.maxOrNull()
        ?: byKey.keys.firstOrNull()
        ?: today.toEpochDay())
    var key by rememberSaveable { mutableStateOf(opening) }
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    val earlier = key == EARLIER
    val selected = if (earlier) null else java.time.LocalDate.ofEpochDay(key)
    val list = (byKey[key] ?: emptyList()).sortedByDescending { it.createdAt }
    val featured = list.firstOrNull { !it.isRead }
    val tap: (AppNotification) -> Unit = { n ->
        // An announcement is read here: the tap shows it whole (and marks it read).
        if (n.link.isBlank() && n.type == NotifType.GENERAL) open = if (n.id in open) open - n.id else open + n.id
        onOpen(n)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item("top") { TopBar(unread, onBack, onMarkAll) }
        if (waiting.isNotEmpty()) {
            item("waiting-head") {
                Text("Requires Your Attention", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = com.legumlex.client.ui.theme.Lex.colors.danger.solid,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp))
            }
            itemsIndexed(waiting, key = { _, n -> "w" + n.id }) { i, n ->
                TimelineItem(
                    title = n.title,
                    // Since when it has waited (its last activity), not an event's date.
                    time = n.createdAt?.let { "Since " + SinceDate.format(it) } ?: "",
                    body = n.subtitle,
                    live = true,
                    featured = true,
                    first = i == 0,
                    last = i == waiting.lastIndex,
                    onClick = { tap(n) },
                    footer = if (n.link.isNotBlank()) ({ FeatureCta(n.cta) }) else null,
                )
            }
            item("waiting-gap") { Spacer(Modifier.height(12.dp)) }
        }
        item("head") {
            if (earlier) DayHead(first, Modifier.padding(horizontal = 20.dp), title = "Earlier", dateLine = "Before " + com.legumlex.client.ui.components.longDate(first))
            else DayHead(selected!!, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            DayStrip(
                days = days,
                selected = selected,
                marked = byKey.keys.filter { it != EARLIER }.map { java.time.LocalDate.ofEpochDay(it) }.toSet(),
                unread = unreadKeys.filter { it != EARLIER }.map { java.time.LocalDate.ofEpochDay(it) }.toSet(),
                onSelect = { key = it.toEpochDay() },
                before = if (byKey.containsKey(EARLIER)) "Earlier" else null,
                edge = if (earlier) -1 else 0,
                edgeUnread = EARLIER in unreadKeys,
                onEdge = { key = EARLIER },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (items.isEmpty()) item("none") { EmptyState() }
        else if (list.isEmpty()) item("empty") { DayEmpty(if (news.isEmpty()) "The firm has published no announcements." else "Nothing was published on this day.") }
        itemsIndexed(list, key = { _, n -> n.id }) { i, n ->
            TimelineItem(
                title = n.title,
                time = n.createdAt?.let { if (earlier) ShortDate.format(it) else Clock.format(it) } ?: n.timeLabel,
                // Opened, an announcement shows its whole text.
                body = if (n.id in open && n.body.isNotBlank()) n.body else n.subtitle,
                live = !n.isRead,
                featured = n === featured,
                first = i == 0,
                last = i == list.lastIndex,
                onClick = { tap(n) },
                expanded = n.id in open,
                footer = if (n === featured && n.link.isNotBlank()) ({ FeatureCta(n.cta) }) else null,
            )
        }
        item("end") { Spacer(Modifier.height(24.dp)) }
    }
}

/** The dark card's action, as the reference's check button: a light chip. */
@Composable
private fun FeatureCta(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Black)
        Icon(Icons.Outlined.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(16.dp))
    }
}

/* --------------------------------------------------------- empty / states */

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(com.legumlex.client.ui.theme.Lex.colors.card)
                .border(1.dp, LlColor.Border, RoundedCornerShape(12.dp))
                .padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Nothing Requires Your Attention", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
            Spacer(Modifier.height(7.dp))
            Text(
                "Announcements from the firm, and anything waiting on you, arrive here.",
                fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(com.legumlex.client.ui.theme.Lex.colors.card)
                .border(1.dp, LlColor.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.AmberSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.WifiOff, null, tint = LlColor.Amber, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Notifications Unavailable", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
                Spacer(Modifier.height(2.dp))
                Text(message, fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.InkMuted)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, LlColor.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = LlColor.Ink, modifier = Modifier.size(15.dp))
                Text("Retry", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        repeat(2) { band ->
            Box(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 9.dp)) {
                ShimmerBar(width = 80.dp, height = 14.dp)
            }
            repeat(if (band == 0) 3 else 1) {
                Row(
                    Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 1.dp).background(com.legumlex.client.ui.theme.Lex.colors.card).padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(15.dp))
                    ShimmerBar(width = 40.dp, height = 40.dp, radius = 11.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        ShimmerBar(width = 200.dp, height = 12.dp)
                        Spacer(Modifier.height(7.dp))
                        ShimmerBar(width = 130.dp, height = 10.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    ShimmerBar(width = 26.dp, height = 11.dp)
                }
            }
        }
    }
}

@Composable
private fun ShimmerBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}
