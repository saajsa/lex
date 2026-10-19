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

private data class SevColors(val stripe: Color, val tileBg: Color, val tileTint: Color)

@androidx.compose.runtime.Composable private fun colorsFor(s: NotifSeverity): SevColors = when (s) {
    NotifSeverity.CRITICAL -> SevColors(LlColor.Claret, LlColor.ClaretSoft, LlColor.Claret)
    NotifSeverity.WARNING  -> SevColors(LlColor.AmberMid, LlColor.AmberSoft, LlColor.Amber)
    NotifSeverity.INFO     -> SevColors(LlColor.SlateMid, LlColor.SlateSoft, LlColor.Slate)
    NotifSeverity.POSITIVE -> SevColors(LlColor.VerdantMid, LlColor.VerdantSoft, LlColor.Verdant)
}

private fun iconFor(t: NotifType): ImageVector = when (t) {
    NotifType.HEARING     -> Icons.Outlined.Gavel
    NotifType.INVOICE     -> Icons.AutoMirrored.Outlined.ReceiptLong
    NotifType.PAYMENT     -> Icons.Outlined.Payments
    NotifType.AHC         -> Icons.Outlined.VerifiedUser
    NotifType.DOCREQUEST  -> Icons.Outlined.UploadFile
    NotifType.SIGNATURE   -> Icons.Outlined.Edit
    NotifType.APPOINTMENT -> Icons.Outlined.Event
    NotifType.TASK        -> Icons.Outlined.TaskAlt
    NotifType.PROJECT     -> Icons.Outlined.FolderOpen
    NotifType.MESSAGE     -> Icons.AutoMirrored.Outlined.Chat
    NotifType.DOCUMENT    -> Icons.Outlined.Description
    NotifType.CHAMBER     -> Icons.Outlined.MeetingRoom
    NotifType.GENERAL     -> Icons.Outlined.Notifications
    NotifType.ATTENTION   -> Icons.Outlined.Info
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val vm: NotificationsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    val unread = (state as? NotificationsUiState.Ready)?.unreadCount ?: 0

    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Header(unread = unread, onBack = onBack, onMarkAll = vm::markAllRead)
        SubBar(filter = filter, unread = unread, onFilter = vm::setFilter)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = state) {
                is NotificationsUiState.Loading -> SkeletonList()
                is NotificationsUiState.Error   -> ErrorBlock(s.message, onRetry = vm::load)
                is NotificationsUiState.Ready ->
                    if (s.bands.isEmpty()) EmptyState()
                    else Feed(s, onOpen = { vm.markRead(it.id); onOpen(it) }, onDismiss = { vm.dismiss(it.id) })
            }
        }
    }
}

/* ----------------------------------------------------------------- header */

@Composable
private fun Header(unread: Int, onBack: () -> Unit, onMarkAll: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(LlColor.Paper)

            .height(52.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = LlColor.InkMuted, modifier = Modifier.size(21.dp))
        }
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LegumMark(size = 17.dp, fillColor = AccentInk)
            Text("Notifications", style = com.legumlex.client.ahc.theme.LlText.appBarTitle.copy(fontSize = 17.sp))
        }
        val enabled = unread > 0
        Text(
            text = "Mark all read",
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = if (enabled) LlColor.Claret else LlColor.InkFaint,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .then(if (enabled) Modifier.clickable(onClick = onMarkAll) else Modifier)
                .padding(6.dp),
        )
    }
    Divider()
}

@Composable
private fun SubBar(filter: NotifFilter, unread: Int, onFilter: (NotifFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LlColor.Paper)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(LlColor.Paper1)
                .border(1.dp, LlColor.Border, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SegButton("All", null, filter == NotifFilter.ALL) { onFilter(NotifFilter.ALL) }
            SegButton("Unread", unread.takeIf { it > 0 }, filter == NotifFilter.UNREAD) { onFilter(NotifFilter.UNREAD) }
        }
        if (unread > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LlColor.Claret))
                Text("$unread unread", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(14.dp))
                Text("No unread", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
}

@Composable
private fun SegButton(label: String, pill: Int?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) com.legumlex.client.ui.theme.Lex.colors.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = if (on) LlColor.Ink else LlColor.InkMuted,
        )
        if (pill != null) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(LlColor.Claret).padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("$pill", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = Color.White)
            }
        }
    }
}

/* ------------------------------------------------------------------- feed */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(state: NotificationsUiState.Ready, onOpen: (AppNotification) -> Unit, onDismiss: (AppNotification) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        state.bands.forEach { band ->
            item(key = "band-${band.title}") { BandHeader(band) }
            items(band.items, key = { it.id }) { n ->
                // Waiting on the client: it goes when the step is done, not by a swipe.
                if (n.type == NotifType.ATTENTION) { NotifRow(n, onClick = { onOpen(n) }); return@items }
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) { onDismiss(n); true } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = { SwipeBackground() },
                ) {
                    NotifRow(n, onClick = { onOpen(n) })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun BandHeader(band: NotifBand) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 9.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(band.title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
        Text(
            buildString {
                if (band.newCount > 0) append("${band.newCount} new · ")
                append("${band.items.size} total")
            },
            fontFamily = Inter, fontSize = 10.5.sp,
            color = if (band.newCount > 0) LlColor.ClaretMid else LlColor.InkFaint,
        )
    }
}

@Composable
private fun NotifRow(n: AppNotification, onClick: () -> Unit) {
    val c = colorsFor(n.severity)
    // Waiting on the client is never "read": it stays live until the step is done.
    val read = n.isRead && n.type != NotifType.ATTENTION
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (read) LlColor.Paper else com.legumlex.client.ui.theme.Lex.colors.card)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 3px severity stripe
            Box(
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(c.stripe.copy(alpha = if (read) 0.4f else 1f)),
            )
            Spacer(Modifier.width(12.dp))
            // tile
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.tileBg)
                    .alpha(if (read) 0.7f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(n.type), null, tint = c.tileTint, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            // body — an announcement from the firm is read here in full; it has nowhere to open.
            val notice = n.link.isBlank() && n.type == NotifType.GENERAL
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        n.title,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = if (read) LlColor.InkMuted else LlColor.Ink,
                        maxLines = if (notice) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!notice) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n.cta.uppercase(),
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp,
                            color = if (read) LlColor.InkFaint else LlColor.Claret,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Outlined.ChevronRight, null,
                            tint = if (read) LlColor.InkFaint else LlColor.Claret,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (n.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        n.subtitle,
                        fontFamily = Inter,
                        fontSize = 11.5.sp,
                        color = if (read) LlColor.InkFaint else LlColor.InkMuted,
                        maxLines = if (notice) 8 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // meta — unread dot + time
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!read) Box(Modifier.size(8.dp).clip(CircleShape).background(LlColor.Claret))
                Text(n.timeLabel, fontFamily = Inter, fontSize = 10.5.sp, color = if (read) LlColor.InkFaint else LlColor.InkMuted)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun SwipeBackground() {
    Row(
        Modifier.fillMaxSize().background(LlColor.Claret),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            Modifier.padding(end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text("Dismiss", fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
        }
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
            Text("You're all caught up", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
            Spacer(Modifier.height(7.dp))
            Text(
                "No new alerts about your cases, invoices, hearings or documents. Anything that needs you will arrive here.",
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
                Text("Couldn't load notifications", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
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

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
}
