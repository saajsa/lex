package com.legumlex.client.feature.finance

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.brand.LegumMark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Invoice Detail — the "Client Invoice Statement" Lexcore design: a dignified
// letterhead sheet whose emotional core is the Balance due hero. Block order:
// letterhead → status pill → matter → balance hero → action stack (with the
// editable partial-amount field + Pay) → amount summary → expandable breakdown
// → footer. Severity (Overdue/Unpaid/Partial/Paid) drives a single accent
// colour across pill, hero band and summary.

private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

// --- severity -----------------------------------------------------------------

private data class Sev(val accent: Color, val mid: Color, val soft: Color, val border: Color)

@androidx.compose.runtime.Composable private fun sevFor(s: InvoiceStatus): Sev = when (s) {
    InvoiceStatus.OVERDUE   -> Sev(LlColor.Claret, LlColor.ClaretMid, LlColor.ClaretSoft, LlColor.ClaretBorder)
    InvoiceStatus.UNPAID    -> Sev(LlColor.Amber, LlColor.AmberMid, LlColor.AmberSoft, LlColor.AmberBorder)
    InvoiceStatus.PARTIAL   -> Sev(LlColor.Slate, LlColor.SlateMid, LlColor.SlateSoft, LlColor.SlateBorder)
    InvoiceStatus.PAID      -> Sev(LlColor.Verdant, LlColor.VerdantMid, LlColor.VerdantSoft, LlColor.VerdantBorder)
    else                    -> Sev(LlColor.InkMuted, LlColor.InkMuted, LlColor.Paper2, LlColor.Border)
}

private fun statusIcon(s: InvoiceStatus): ImageVector = when (s) {
    InvoiceStatus.OVERDUE   -> Icons.Outlined.ErrorOutline
    InvoiceStatus.UNPAID    -> Icons.Outlined.Schedule
    InvoiceStatus.PARTIAL   -> Icons.Outlined.PieChart
    InvoiceStatus.PAID      -> Icons.Outlined.CheckCircle
    else                    -> Icons.Outlined.Description
}

/** Human status line for the pill — "Overdue · 5 days", "Due in 4 days", … */
private fun statusLabel(inv: Invoice): String {
    val today = LocalDate.now()
    return when (inv.status) {
        InvoiceStatus.PAID      -> "Paid in full"
        InvoiceStatus.PARTIAL   -> "Partially paid"
        InvoiceStatus.CANCELLED -> "Cancelled"
        InvoiceStatus.DRAFT     -> "Draft"
        InvoiceStatus.OVERDUE   -> {
            val d = inv.dueDate?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 0
            "Overdue · $d ${if (d == 1) "day" else "days"}"
        }
        InvoiceStatus.UNPAID    -> {
            val due = inv.dueDate
            if (due != null) {
                val d = ChronoUnit.DAYS.between(today, due).toInt()
                when {
                    d == 0 -> "Due today"
                    d == 1 -> "Due tomorrow"
                    d > 0  -> "Due in $d days"
                    else   -> "Overdue"
                }
            } else "Unpaid"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(invoiceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: InvoiceDetailViewModel = viewModel(
        key     = "invoice-$invoiceId",
        factory = viewModelFactory {
            initializer { InvoiceDetailViewModel(app = app, invoiceId = invoiceId) }
        },
    )
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    val razorpayResults = com.legumlex.client.feature.payment.LocalRazorpayResults.current

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is InvoicePaymentEvent.Failed         -> snack.showSnackbar(ev.message)
                is InvoicePaymentEvent.Info           -> snack.showSnackbar(ev.message)
                is InvoicePaymentEvent.LaunchCheckout -> {
                    (context as? android.app.Activity)?.let {
                        com.legumlex.client.feature.payment.openRazorpayCheckout(it, ev.options)
                    }
                }
            }
        }
    }
    LaunchedEffect(vm) {
        razorpayResults.collect { vm.handlePaymentResult(it) }
    }

    Scaffold(
        snackbarHost        = { SnackbarHost(snack) },
        containerColor      = LlColor.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is InvoiceDetailUiState.Loading -> Skeleton()
                is InvoiceDetailUiState.Error   -> ErrorView(s.message, vm::load)
                is InvoiceDetailUiState.Ready   -> Sheet(
                    state          = s,
                    onPay          = vm::startPayment,
                    onAmountChange = vm::onAmountChange,
                )
            }
        }
    }
}

// =============================================================================
// Sheet — the letterhead statement card
// =============================================================================

@Composable
private fun Sheet(
    state: InvoiceDetailUiState.Ready,
    onPay: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val detail = state.detail
    val inv = detail.invoice
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Surface(
            color  = LlColor.Surface,
            shape  = LlShape.Card,
            border = BorderStroke(LlDimen.HairlineWidth, LlColor.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Letterhead(inv)
                StatusPill(inv)
                if (!inv.projectName.isNullOrBlank()) MatterBlock(inv)
                BalanceHero(detail)
                ActionStack(state, onPay, onAmountChange)
                AmountSummary(detail)
                ExpandableBreakdown(detail)
                FirmFooter()
            }
        }
    }
}

// 1 · Letterhead -------------------------------------------------------------

@Composable
private fun Letterhead(inv: Invoice) {
    val overdue = inv.status == InvoiceStatus.OVERDUE
    Column {
        // claret hairline cap
        Box(Modifier.fillMaxWidth().height(LlDimen.HeroStripeWidth).background(LlColor.Claret))
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Ink),
                        contentAlignment = Alignment.Center,
                    ) { LegumMark(size = 22.dp, fillColor = LlColor.Paper) }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("Legum Lex", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, letterSpacing = (-0.01).em, color = LlColor.Ink)
                        Text("ADVOCATES", fontFamily = Inter, fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp, letterSpacing = 0.16.em, color = LlColor.InkFaint)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("TAX INVOICE", fontFamily = Inter, fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp, letterSpacing = 0.14.em, color = LlColor.Claret)
                    Spacer(Modifier.height(4.dp))
                    Text(inv.number, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = LlColor.Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.BorderStrong))
            Spacer(Modifier.height(12.dp))
            Row {
                DateCol("ISSUED", inv.date?.format(dayFmt) ?: "—", false)
                Spacer(Modifier.width(28.dp))
                DateCol("DUE", inv.dueDate?.format(dayFmt) ?: "—", overdue)
            }
        }
    }
}

@Composable
private fun DateCol(label: String, value: String, over: Boolean) {
    Column {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = Inter, fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp, color = if (over) LlColor.ClaretMid else LlColor.Ink)
    }
}

// 2 · Status pill ------------------------------------------------------------

@Composable
private fun StatusPill(inv: Invoice) {
    val sev = sevFor(inv.status)
    Row(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(sev.soft)
                .border(LlDimen.HairlineWidth, sev.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(statusIcon(inv.status), null, tint = sev.mid, modifier = Modifier.size(17.dp))
            Text(statusLabel(inv), fontFamily = Inter, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = sev.mid)
        }
    }
}

// 3 · Matter block -----------------------------------------------------------

@Composable
private fun MatterBlock(inv: Invoice) {
    Column(
        Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.SlateSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.FolderOpen, null, tint = LlColor.SlateMid, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("FOR MATTER", fontFamily = Inter, fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
                Spacer(Modifier.height(3.dp))
                Text(inv.projectName ?: "Matter", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = LlColor.Ink, maxLines = 1)
            }
        }
    }
}

// 4 · Balance hero -----------------------------------------------------------

@Composable
private fun BalanceHero(detail: InvoiceDetail) {
    val inv = detail.invoice
    val sym = inv.currencySymbol
    val sev = sevFor(inv.status)
    val settled = inv.status == InvoiceStatus.PAID || inv.leftToPay <= 0.0
    val paid = (inv.total - inv.leftToPay).coerceAtLeast(0.0)

    Column(
        Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(LlColor.Surface, if (settled) LlColor.VerdantSoft else sev.soft)))
            .border(LlDimen.HairlineWidth, if (settled) LlColor.VerdantBorder else sev.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BALANCE DUE", fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 0.14.em, color = LlColor.InkMuted)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(sym, fontFamily = Inter, fontWeight = FontWeight.Medium,
                fontSize = 22.sp, color = if (settled) LlColor.VerdantMid else LlColor.InkMuted,
                modifier = Modifier.padding(top = 6.dp, end = 3.dp))
            Text(
                formatAmountOnly(if (settled) 0.0 else inv.leftToPay),
                fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 42.sp, letterSpacing = (-0.03).em, lineHeight = 44.sp,
                color = if (settled) LlColor.Verdant else LlColor.Ink,
            )
        }
        if (settled) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Verified, null, tint = LlColor.Verdant, modifier = Modifier.size(15.dp))
                val on = detail.payments.mapNotNull { it.date }.maxOrNull()
                Text("Paid in full" + (on?.let { " · ${it.format(dayFmt)}" } ?: ""),
                    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = LlColor.Verdant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Border))
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroSub("Invoice total", formatMoney(inv.total, sym), LlColor.Ink, Modifier.weight(1f))
            Box(Modifier.width(LlDimen.HairlineWidth).height(30.dp).background(LlColor.Border))
            HeroSub("Amount paid", formatMoney(paid, sym), LlColor.VerdantMid, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroSub(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp, letterSpacing = 0.08.em, color = LlColor.InkFaint)
        Spacer(Modifier.height(5.dp))
        Text(value, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = valueColor)
    }
}

// 5 · Action stack (amount field + Pay) --------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionStack(
    state: InvoiceDetailUiState.Ready,
    onPay: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val inv = state.detail.invoice
    val sym = inv.currencySymbol
    val payable = inv.status in setOf(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL, InvoiceStatus.OVERDUE) &&
        inv.leftToPay > 0.0

    Column(Modifier.padding(horizontal = 18.dp).padding(top = 18.dp)) {
        if (payable) {
            // Editable partial-amount field — prefilled with the full balance.
            val showErr = state.payAmount.isNotBlank() && !state.amountValid
            Text("AMOUNT TO PAY", fontFamily = Inter, fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = state.payAmount,
                onValueChange = onAmountChange,
                singleLine = true,
                isError = showErr,
                enabled = !state.paying,
                leadingIcon = {
                    Text(sym, fontFamily = Inter, fontWeight = FontWeight.Medium,
                        fontSize = 16.sp, color = LlColor.InkMuted)
                },
                textStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, color = LlColor.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = LlColor.Claret,
                    unfocusedBorderColor = LlColor.Border,
                    errorBorderColor     = LlColor.ClaretMid,
                    cursorColor          = LlColor.Claret,
                    focusedContainerColor   = LlColor.Surface,
                    unfocusedContainerColor = LlColor.Surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (showErr) (state.amountError ?: "") else "Min ${formatMoney(1.0, sym)} · balance ${formatMoney(inv.leftToPay, sym)}",
                fontFamily = Inter, fontSize = 10.5.sp,
                color = if (showErr) LlColor.ClaretMid else LlColor.InkFaint,
            )
            Spacer(Modifier.height(12.dp))

            // Pay button — claret, amount baked in; "partially" when < balance.
            val amount = state.enteredAmount ?: inv.leftToPay
            val enabled = !state.paying && state.amountValid
            Surface(
                onClick = onPay,
                enabled = enabled,
                color = if (enabled) LlColor.Claret else LlColor.BorderStrong,
                contentColor = LlColor.Paper,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.paying) {
                    Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LlColor.Paper)
                        Spacer(Modifier.width(10.dp))
                        Text("Opening secure checkout…", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = LlColor.Paper)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).padding(start = 16.dp, top = 15.dp, bottom = 15.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isPartial) "Pay partially" else "Pay now",
                                fontFamily = Inter, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, letterSpacing = 0.03.em, color = LlColor.Paper)
                        }
                        Box(Modifier.width(LlDimen.HairlineWidth).height(22.dp).background(LlColor.Paper.copy(alpha = 0.22f)))
                        Text(formatMoney(amount, sym), fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = LlColor.Paper,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically, ) {
                Icon(Icons.Outlined.Verified, null, tint = LlColor.InkFaint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Secured by Razorpay · in-app checkout", fontFamily = Inter,
                    fontSize = 9.5.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint)
            }
            Spacer(Modifier.height(11.dp))
            GhostRow(context, inv, settled = false)
        } else {
            // Paid / Draft / Cancelled — no Pay CTA.
            GhostRow(context, inv, settled = inv.status == InvoiceStatus.PAID)
        }
    }
}

@Composable
private fun GhostRow(context: android.content.Context, inv: Invoice, settled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (settled) {
            GhostAction(Icons.Outlined.ReceiptLong, "Receipt", { openPaymentPage(context, inv) }, Modifier.weight(1f))
            GhostAction(Icons.AutoMirrored.Outlined.OpenInNew, "In browser", { openPaymentPage(context, inv) }, Modifier.weight(1f))
        } else {
            GhostAction(Icons.AutoMirrored.Outlined.OpenInNew, "In browser", { openPaymentPage(context, inv) }, Modifier.weight(1f))
            GhostAction(Icons.Outlined.Share, "Share link", { sharePaymentLink(context, inv) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GhostAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = LlColor.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(LlDimen.HairlineWidth, LlColor.Border),
        modifier = modifier,
    ) {
        Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LlColor.Ink, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Ink)
        }
    }
}

// 6 · Amount summary ---------------------------------------------------------

@Composable
private fun AmountSummary(detail: InvoiceDetail) {
    val sym = detail.invoice.currencySymbol
    val paid = (detail.invoice.total - detail.invoice.leftToPay).coerceAtLeast(0.0)
    val partlyPaid = paid > 0.0
    val settled = detail.invoice.status == InvoiceStatus.PAID || detail.invoice.leftToPay <= 0.0

    Column(
        Modifier.padding(horizontal = 18.dp).padding(top = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxWidth().background(LlColor.Paper1).padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text("AMOUNT SUMMARY", fontFamily = Inter, fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp, letterSpacing = 0.1.em, color = LlColor.InkMuted)
        }
        SumRow("Subtotal", formatMoney(detail.subtotal, sym))
        if (detail.tax != 0.0)        SumRow("Tax", formatMoney(detail.tax, sym))
        if (detail.discount != 0.0)   SumRow("Discount", "− " + formatMoney(detail.discount, sym), valueColor = LlColor.Verdant)
        if (detail.adjustment != 0.0) SumRow("Adjustment", formatMoney(detail.adjustment, sym))
        SumRow("Total", formatMoney(detail.invoice.total, sym), total = true)
        if (partlyPaid) SumRow("Amount paid", "− " + formatMoney(paid, sym), valueColor = LlColor.Verdant)
        BalanceRow(formatMoney(detail.invoice.leftToPay.coerceAtLeast(0.0), sym), settled)
    }
}

@Composable
private fun SumRow(label: String, value: String, total: Boolean = false, valueColor: Color = LlColor.Ink) {
    Box(Modifier.fillMaxWidth().background(if (total) LlColor.Paper else Color.Transparent)) {
        Column {
            Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (total) {
                    Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LlColor.Ink)
                    Text(value, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LlColor.Ink)
                } else {
                    Text(label, fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted)
                    Text(value, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = valueColor)
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(value: String, settled: Boolean) {
    val bg = if (settled) LlColor.VerdantSoft else LlColor.ClaretSoft
    val fg = if (settled) LlColor.Verdant else LlColor.ClaretMid
    Box(Modifier.fillMaxWidth().background(bg)) {
        Column {
            Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BALANCE DUE", fontFamily = Inter, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 0.06.em, color = fg)
                Text(value, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = fg)
            }
        }
    }
}

// 7 · Expandable breakdown (behaviour preserved, restyled) -------------------

@Composable
private fun ExpandableBreakdown(detail: InvoiceDetail) {
    if (detail.items.isEmpty() && detail.payments.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")

    Column(Modifier.padding(horizontal = 18.dp).padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(LlDimen.ButtonRadius)).clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ReceiptLong, null, tint = LlColor.InkFaint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Breakdown", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = LlColor.Ink, modifier = Modifier.weight(1f))
            Text("${detail.items.size} items", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkFaint)
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Outlined.ExpandMore, null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp).rotate(rot))
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 10.dp)) {
                if (detail.items.isNotEmpty()) {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))) {
                        detail.items.forEachIndexed { i, item ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
                            BreakdownItem(item, detail.invoice.currencySymbol)
                        }
                    }
                }
                if (detail.payments.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("PAYMENTS", fontFamily = Inter, fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp, letterSpacing = 0.1.em, color = LlColor.InkFaint)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp))) {
                        detail.payments.forEachIndexed { i, p ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
                            PaymentItem(p, detail.invoice.currencySymbol)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownItem(item: InvoiceItem, symbol: String) {
    Column(Modifier.padding(13.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(item.description.ifBlank { "—" }, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = LlColor.Ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(formatMoney(item.amount, symbol), fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = LlColor.Ink)
        }
        item.longDescription?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = Inter, fontSize = 11.5.sp, lineHeight = 17.sp, color = LlColor.InkMuted, maxLines = 3)
        }
        val qtyStr = if (item.qty == item.qty.toLong().toDouble()) item.qty.toLong().toString() else item.qty.toString()
        val unitStr = item.unit?.let { " $it" } ?: ""
        Spacer(Modifier.height(5.dp))
        Text("$qtyStr$unitStr × ${formatMoney(item.rate, symbol)}", fontFamily = Inter,
            fontSize = 10.5.sp, color = LlColor.InkFaint)
    }
}

@Composable
private fun PaymentItem(p: Payment, symbol: String) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.VerdantSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CheckCircle, null, tint = LlColor.Verdant, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(p.date?.format(dayFmt) ?: "—", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = LlColor.Ink)
            val sub = listOfNotNull(p.method, p.transactionId).joinToString(" · ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sub, fontFamily = Inter, fontSize = 10.sp, color = LlColor.InkFaint, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("+ " + formatMoney(p.amount, symbol), fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = LlColor.Verdant)
    }
}

// 8 · Footer -----------------------------------------------------------------

@Composable
private fun FirmFooter() {
    val context = LocalContext.current
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
    Column(
        Modifier.fillMaxWidth().background(LlColor.Paper1).padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Legum Lex, Advocates", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        Text(
            "Office No. 10 & 11, Casablanca CHS, Jesal Park,\nBhayander (East), Mumbai 401105",
            fontFamily = Inter, fontSize = 10.5.sp, lineHeight = 15.sp,
            color = LlColor.InkMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "+91 98194 64324",
            fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = LlColor.Ink,
            modifier = Modifier.clickable {
                try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919819464324"))) } catch (_: Exception) {}
            },
        )
    }
}

// --- open actions -----------------------------------------------------------

private fun openPaymentPage(context: android.content.Context, inv: Invoice) {
    val url = paymentUrlFor(inv)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try { context.startActivity(intent) } catch (e: Exception) {
        Toast.makeText(context, "No browser installed.", Toast.LENGTH_LONG).show()
    }
}

private fun sharePaymentLink(context: android.content.Context, inv: Invoice) {
    val url = paymentUrlFor(inv)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Invoice ${inv.number}")
        putExtra(Intent.EXTRA_TEXT, "Invoice ${inv.number}\n${formatMoney(inv.total, inv.currencySymbol)}\n$url")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share invoice").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share.", Toast.LENGTH_LONG).show()
    }
}

// --- helpers ----------------------------------------------------------------

// Amount without the currency symbol (rendered separately in the hero).
private fun formatAmountOnly(amount: Double): String {
    val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    return nf.format(amount)
}

// --- Loading / Error --------------------------------------------------------

@Composable
private fun Skeleton() {
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Surface(
            color = LlColor.Surface, shape = LlShape.Card,
            border = BorderStroke(LlDimen.HairlineWidth, LlColor.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(LlDimen.HeroStripeWidth).background(LlColor.Border))
                Column(Modifier.padding(18.dp)) {
                    SkBox(110.dp, 16.dp)
                    Spacer(Modifier.height(22.dp))
                    SkBox(120.dp, 30.dp, radius = 10.dp)
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper2))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper2))
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper2))
                }
            }
        }
    }
}

@Composable
private fun SkBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.ErrorOutline, null, tint = LlColor.InkMuted, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Couldn't open this invoice", fontFamily = Inter, fontWeight = FontWeight.SemiBold,
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
                Text("Try again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = LlColor.Paper)
            }
        }
    }
}
