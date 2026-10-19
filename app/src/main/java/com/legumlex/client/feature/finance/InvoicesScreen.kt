package com.legumlex.client.feature.finance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Lora
import com.legumlex.client.ahc.theme.SourceSans
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Invoices ledger (Screen A of the "Client Invoice Statement" design): a dense,
// scannable list — severity on the leading edge, invoice number + matter, a
// relative date hint in the severity colour, and the amount + status chip on the
// right. Filter chips (All / Outstanding / Paid) sit under an Outstanding total.

private val listDayFmt = DateTimeFormatter.ofPattern("d MMM")

private enum class InvFilter(val label: String) { ALL("All"), OUTSTANDING("Outstanding"), PAID("Paid") }

private val OUTSTANDING_STATES = setOf(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL, InvoiceStatus.OVERDUE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(onBack: () -> Unit, onOpenInvoice: (String) -> Unit) {
    val vm: InvoicesViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = LlColor.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is InvoicesUiState.Loading -> Skeleton()
                is InvoicesUiState.Error   -> ErrorView(s.message, vm::retry)
                is InvoicesUiState.Ready   -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh    = vm::refresh,
                        modifier     = Modifier.fillMaxSize(),
                    ) { Ready(s, onOpenInvoice) }
                }
            }
        }
    }
}

@Composable
private fun Ready(state: InvoicesUiState.Ready, onOpenInvoice: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(InvFilter.ALL) }
    val all = state.invoices
    val paidCount = all.count { it.status == InvoiceStatus.PAID }
    val shown = when (filter) {
        InvFilter.ALL         -> all
        InvFilter.OUTSTANDING -> all.filter { it.status in OUTSTANDING_STATES }
        InvFilter.PAID        -> all.filter { it.status == InvoiceStatus.PAID }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
    ) {
        item("head") {
            ListHead(all.size, state.outstandingTotal, state.outstandingCount, state.currencySymbol)
            Spacer(Modifier.height(14.dp))
        }
        item("filters") {
            FilterBar(filter, { filter = it }, all.size, state.outstandingCount, paidCount)
            Spacer(Modifier.height(12.dp))
        }
        item("ledger") {
            if (shown.isEmpty()) EmptyState(filter)
            else Ledger(shown, onOpenInvoice)
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ListHead(count: Int, outstanding: Double, outstandingCount: Int, symbol: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text("Invoices", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp, letterSpacing = (-0.02).em, color = LlColor.Ink)
            Spacer(Modifier.height(3.dp))
            Text("$count ${if (count == 1) "invoice" else "invoices"}",
                fontFamily = SourceSans, fontSize = 12.sp, color = LlColor.InkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("OUTSTANDING", fontFamily = SourceSans, fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(3.dp))
            Text(
                formatMoney(outstanding, symbol),
                fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                color = if (outstandingCount == 0) LlColor.VerdantMid else LlColor.ClaretMid,
            )
        }
    }
}

@Composable
private fun FilterBar(active: InvFilter, onSelect: (InvFilter) -> Unit, all: Int, outstanding: Int, paid: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FChip(InvFilter.ALL, active, onSelect, all)
        FChip(InvFilter.OUTSTANDING, active, onSelect, outstanding)
        FChip(InvFilter.PAID, active, onSelect, paid)
    }
}

@Composable
private fun FChip(filter: InvFilter, active: InvFilter, onSelect: (InvFilter) -> Unit, count: Int) {
    val on = filter == active
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) LlColor.Ink else LlColor.Surface)
            .border(LlDimen.HairlineWidth, if (on) LlColor.Ink else LlColor.Border, RoundedCornerShape(8.dp))
            .clickable { onSelect(filter) }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(filter.label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            color = if (on) LlColor.Paper else LlColor.InkMuted)
        Text("$count", fontFamily = SourceSans, fontSize = 10.sp,
            color = if (on) LlColor.Paper.copy(alpha = 0.75f) else LlColor.InkFaint)
    }
}

@Composable
private fun Ledger(invoices: List<Invoice>, onOpenInvoice: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
    ) {
        invoices.forEachIndexed { i, inv ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
            InvoiceRow(inv) { onOpenInvoice(inv.id) }
        }
    }
}

// --- severity ---------------------------------------------------------------

private data class LSev(val mid: Color, val soft: Color)

private fun lsev(s: InvoiceStatus): LSev = when (s) {
    InvoiceStatus.OVERDUE -> LSev(LlColor.ClaretMid, LlColor.ClaretSoft)
    InvoiceStatus.UNPAID  -> LSev(LlColor.AmberMid, LlColor.AmberSoft)
    InvoiceStatus.PARTIAL -> LSev(LlColor.SlateMid, LlColor.SlateSoft)
    InvoiceStatus.PAID    -> LSev(LlColor.VerdantMid, LlColor.VerdantSoft)
    else                  -> LSev(LlColor.InkMuted, LlColor.Paper2)
}

private fun rowIcon(s: InvoiceStatus): ImageVector = when (s) {
    InvoiceStatus.OVERDUE -> Icons.Outlined.ErrorOutline
    InvoiceStatus.UNPAID  -> Icons.Outlined.Schedule
    InvoiceStatus.PARTIAL -> Icons.Outlined.PieChart
    InvoiceStatus.PAID    -> Icons.Outlined.CheckCircle
    else                  -> Icons.Outlined.Description
}

/** Relative hint shown after the issue date, in the severity colour. */
private fun rowHint(inv: Invoice): String? {
    val today = LocalDate.now()
    return when (inv.status) {
        InvoiceStatus.OVERDUE -> {
            val d = inv.dueDate?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 0
            "$d ${if (d == 1) "day" else "days"} overdue"
        }
        InvoiceStatus.UNPAID -> inv.dueDate?.let {
            val d = ChronoUnit.DAYS.between(today, it).toInt()
            when {
                d == 0 -> "due today"; d == 1 -> "due tomorrow"
                d > 0  -> "due in $d days"; else -> "overdue"
            }
        }
        InvoiceStatus.PARTIAL -> "${formatMoney(inv.leftToPay, inv.currencySymbol)} left"
        InvoiceStatus.PAID    -> "settled"
        else                  -> null
    }
}

@Composable
private fun InvoiceRow(inv: Invoice, onClick: () -> Unit) {
    val sev = lsev(inv.status)
    val paid = inv.status == InvoiceStatus.PAID
    val amount = if (inv.status == InvoiceStatus.PARTIAL) inv.leftToPay else inv.total

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // leading severity stripe
        Box(Modifier.width(3.dp).height(56.dp).background(sev.mid))
        Spacer(Modifier.width(11.dp))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(sev.soft),
            contentAlignment = Alignment.Center,
        ) { Icon(rowIcon(inv.status), null, tint = sev.mid, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(vertical = 13.dp)) {
            Text(inv.number, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            inv.projectName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = SourceSans, fontSize = 12.sp, color = LlColor.InkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                inv.date?.let {
                    Text("Issued ${it.format(listDayFmt)}", fontFamily = SourceSans,
                        fontSize = 10.5.sp, color = LlColor.InkFaint)
                }
                rowHint(inv)?.let { h ->
                    if (inv.date != null) Text(" · ", fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint)
                    Text(h, fontFamily = SourceSans, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = sev.mid)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
            Text(
                formatMoney(amount, inv.currencySymbol),
                fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (paid) LlColor.InkFaint else LlColor.Ink,
                textDecoration = if (paid) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.height(7.dp))
            StatusChip(inv.status.label, sev)
        }
    }
}

@Composable
private fun StatusChip(label: String, sev: LSev) {
    Text(
        label,
        fontFamily = SourceSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.04.em,
        color = sev.mid,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(sev.soft).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// --- empty / loading / error -------------------------------------------------

@Composable
private fun EmptyState(filter: InvFilter) {
    val (title, body) = when (filter) {
        InvFilter.PAID        -> "No paid invoices" to "Paid invoices will appear here once settled."
        InvFilter.OUTSTANDING -> "Nothing outstanding" to "Every invoice is settled. New invoices from the chamber will appear here the moment they're issued."
        InvFilter.ALL         -> "No invoices yet" to "When your advocate raises an invoice on your account, it'll appear here."
    }
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(LlColor.VerdantSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Verified, null, tint = LlColor.Verdant, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        Text(body, fontFamily = SourceSans, fontSize = 12.5.sp, lineHeight = 19.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                SkBox(120.dp, 24.dp); Spacer(Modifier.height(8.dp)); SkBox(100.dp, 11.dp)
            }
            Column(horizontalAlignment = Alignment.End) {
                SkBox(70.dp, 9.dp); Spacer(Modifier.height(7.dp)); SkBox(90.dp, 16.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SkBox(60.dp, 28.dp, 8.dp); SkBox(96.dp, 28.dp, 8.dp); SkBox(72.dp, 28.dp, 8.dp)
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
                .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card),
        ) {
            repeat(4) { i ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkBox(40.dp, 40.dp, 10.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        SkBox(80.dp, 13.dp); Spacer(Modifier.height(7.dp)); SkBox(140.dp, 10.dp)
                        Spacer(Modifier.height(7.dp)); SkBox(60.dp, 9.dp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        SkBox(60.dp, 13.dp); Spacer(Modifier.height(8.dp)); SkBox(48.dp, 17.dp, 7.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Can't load your invoices", fontFamily = Lora, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 19.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Surface(
            onClick = onRetry, color = LlColor.Ink, contentColor = LlColor.Paper,
            shape = RoundedCornerShape(LlDimen.ButtonRadius),
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Paper)
            }
        }
    }
}
