package com.legumlex.client.feature.finance

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.SegmentedToggle
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.components.StatCard
import com.legumlex.client.ui.components.PageHeader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import com.legumlex.client.ahc.theme.Inter
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

/**
 * Billing › Invoices, as Mylex's /mylex/invoices lays it out: four figures,
 * the outstanding balance on a dark card with what is paid against what is
 * billed, and the invoices with an All | Outstanding | Paid toggle.
 */
@Composable
private fun Ready(state: InvoicesUiState.Ready, onOpenInvoice: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(InvFilter.ALL) }
    val all = state.invoices
    val sym = state.currencySymbol
    val shown = when (filter) {
        InvFilter.ALL         -> all
        InvFilter.OUTSTANDING -> all.filter { it.status in OUTSTANDING_STATES }
        InvFilter.PAID        -> all.filter { it.status == InvoiceStatus.PAID }
    }
    val billable = all.filter { it.status != InvoiceStatus.DRAFT && it.status != InvoiceStatus.CANCELLED }
    // A paid invoice owes nothing, whatever its left-to-pay field says.
    fun Invoice.owed() = if (status == InvoiceStatus.PAID) 0.0 else leftToPay
    val billed = billable.sumOf { it.total }
    val paidTotal = billable.sumOf { it.total - it.owed() }
    val overdue = all.filter { it.status == InvoiceStatus.OVERDUE }
    // The list carries no payment dates, so this is what has been paid on this
    // year's invoices — said as such, not as money received this year.
    val year = java.time.LocalDate.now().year
    val paidThisYear = billable.filter { it.date?.year == year }.sumOf { it.total - it.owed() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") { PageHeader(eyebrow = "Billing", title = "Invoices") }
        item("figures") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The balance itself is the card below; here, how many invoices it is.
                    StatCard("Unpaid Invoices", "${state.outstandingCount}",
                        "Invoices with money still owed on them", Modifier.weight(1f).fillMaxHeight())
                    StatCard("Overdue", formatMoney(overdue.sumOf { it.leftToPay }, sym), "Past the date they were due to be settled",
                        Modifier.weight(1f).fillMaxHeight(),
                        valueColor = if (overdue.isNotEmpty()) Lex.colors.danger.solid else Lex.colors.foreground,
                        chip = null)
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Paid On $year Invoices", formatMoney(paidThisYear, sym), "Paid against invoices issued since 1 January", Modifier.weight(1f).fillMaxHeight())
                    StatCard("Billed In Total", formatMoney(billed, sym), "Every invoice raised on your account", Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        item("balance") { BalanceCard(state.outstandingTotal, paidTotal, billed, sym, overdue.isEmpty(), state.outstandingByIssuer) }
        item("statement") {
            StatementLink {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(siteUrl("mylex/statement"))))
                }.onFailure { android.widget.Toast.makeText(context, "No browser here can open the statement.", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
        item("list") {
            LexCard(title = "${all.size} ${if (all.size == 1) "Invoice" else "Invoices"}") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SegmentedToggle(
                        options = listOf(InvFilter.ALL to "All", InvFilter.OUTSTANDING to "Outstanding", InvFilter.PAID to "Paid"),
                        selected = filter, onSelect = { filter = it },
                    )
                }
                HorizontalDivider(color = Lex.colors.border)
                if (shown.isEmpty()) EmptyState(filter)
                shown.forEachIndexed { i, inv ->
                    if (i > 0) HorizontalDivider(color = Lex.colors.border)
                    InvoiceRow(inv) { onOpenInvoice(inv.id) }
                }
            }
        }
    }
}

/** The dark balance card: what is owed, and a bar of what is paid against what is billed. */
@Composable
private fun BalanceCard(outstanding: Double, paid: Double, billed: Double, sym: String, nothingOverdue: Boolean, byIssuer: List<Pair<String, Double>>) {
    androidx.compose.runtime.CompositionLocalProvider(com.legumlex.client.ui.theme.LocalLexColors provides com.legumlex.client.ui.theme.DarkLexColors) {
        LexCard(container = androidx.compose.ui.graphics.Color(0xFF0C0C0E), borderColor = Lex.colors.input) {
            Column(Modifier.padding(16.dp)) {
                Eyebrow("Outstanding Balance")
                Spacer(Modifier.height(6.dp))
                Text(formatMoney(outstanding, sym), style = MaterialTheme.typography.headlineMedium, color = Lex.colors.foreground)
                // Each business bills and is paid on its own, so say what each is owed.
                if (byIssuer.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    byIssuer.forEach { (name, owed) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(name, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatMoney(owed, sym), style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lex.colors.secondary)) {
                    Box(Modifier.fillMaxWidth(if (billed > 0) (paid / billed).toFloat().coerceIn(0f, 1f) else 0f).height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Lex.colors.foreground))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("${formatMoney(paid, sym)} Paid To Date", style = MaterialTheme.typography.bodySmall,
                        color = Lex.colors.mutedForeground, modifier = Modifier.weight(1f))
                    Text("${formatMoney(billed, sym)} Billed", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
                if (outstanding <= 0.0 && nothingOverdue) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Lex.colors.muted).padding(12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("All Settled — Nothing Due", style = MaterialTheme.typography.labelLarge, color = Lex.colors.secondaryForeground)
                    }
                }
            }
        }
    }
}

/** The web's statement of account, opened in the browser (it asks for the portal sign-in). */
@Composable
private fun StatementLink(onOpen: () -> Unit) {
    LexCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Statement Of Account", style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                Text("Every invoice and payment on one statement, on the website. Sign in there with your portal account.",
                    style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(16.dp))
        }
    }
}

// --- severity ---------------------------------------------------------------

/** [mid] fills stripes and icons; [text] is the readable shade for words. */
private data class LSev(val mid: Color, val soft: Color, val text: Color)

@androidx.compose.runtime.Composable private fun lsev(s: InvoiceStatus): LSev = when (s) {
    InvoiceStatus.OVERDUE -> LSev(LlColor.ClaretMid, LlColor.ClaretSoft, LlColor.Claret)
    InvoiceStatus.UNPAID  -> LSev(LlColor.AmberMid, LlColor.AmberSoft, LlColor.Amber)
    InvoiceStatus.PARTIAL -> LSev(LlColor.SlateMid, LlColor.SlateSoft, LlColor.Slate)
    InvoiceStatus.PAID    -> LSev(LlColor.VerdantMid, LlColor.VerdantSoft, LlColor.Verdant)
    else                  -> LSev(LlColor.InkMuted, LlColor.Paper2, LlColor.InkMuted)
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
            if (d >= 1) "$d ${if (d == 1) "day" else "days"} overdue" else "overdue"
        }
        InvoiceStatus.UNPAID -> inv.dueDate?.let {
            val d = ChronoUnit.DAYS.between(today, it).toInt()
            when {
                d == 0 -> "due today"; d == 1 -> "due tomorrow"
                d > 0  -> "due in $d days"; else -> "overdue"
            }
        }
        InvoiceStatus.PARTIAL -> "${formatMoney(inv.leftToPay, inv.currencySymbol)} left"
        InvoiceStatus.PAID    -> null   // the Paid chip says it
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
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(sev.soft),
            contentAlignment = Alignment.Center,
        ) { Icon(rowIcon(inv.status), null, tint = sev.mid, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(vertical = 13.dp)) {
            Text(inv.number, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = LlColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            inv.issuerName.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = LlColor.InkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            inv.projectName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                inv.date?.let {
                    Text("Issued ${it.format(listDayFmt)}", fontFamily = Inter,
                        fontSize = 10.5.sp, color = LlColor.InkFaint)
                }
                rowHint(inv)?.let { h ->
                    if (inv.date != null) Text(" · ", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
                    Text(h, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = sev.text)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
            Text(
                formatMoney(amount, inv.currencySymbol),
                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (paid) LlColor.InkFaint else LlColor.Ink,
                textDecoration = null,   // a paid amount is not void: no strikethrough
            )
            Spacer(Modifier.height(7.dp))
            StatusBadge(inv.status.label, inv.status.palette)
        }
    }
}

// --- empty / loading / error -------------------------------------------------

@Composable
private fun EmptyState(filter: InvFilter) {
    val (title, body) = when (filter) {
        InvFilter.PAID        -> "No Paid Invoices" to "Paid invoices will appear here once settled."
        InvFilter.OUTSTANDING -> "Nothing Outstanding" to "Every invoice is settled. New invoices from the chamber will appear here the moment they're issued."
        InvFilter.ALL         -> "No Invoices Yet" to "When your advocate raises an invoice on your account, it'll appear here."
    }
    Column(
        Modifier.fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)
            .padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Verified, null, tint = LlColor.Verdant, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        Text(body, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp,
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
            Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Can't Load Your Invoices", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Surface(
            onClick = onRetry, color = LlColor.Ink, contentColor = LlColor.Paper,
            shape = RoundedCornerShape(LlDimen.ButtonRadius),
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try Again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Paper)
            }
        }
    }
}
