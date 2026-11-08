package com.legumlex.client.feature.board

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.legumlex.client.feature.finance.formatMoney
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a payment ended, as the server says once the app is back from the pay page. */
private data class PayOutcome(val ok: Boolean, val text: String)

/**
 * Credits: the balance and what was spent, the packs as a carousel, and the history. Buying pays first:
 * Buy asks the server for a checkout and opens its hosted pay page in a browser tab (no SDK, no invoice
 * until the payment is made); back in the app, the order is asked about and the balance refreshed.
 */
@Composable
fun BoardCreditsScreen() {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<CreditsData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var buying by remember { mutableStateOf<String?>(null) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var pendingOrder by rememberSaveable { mutableStateOf("") }
    var outcome by remember { mutableStateOf<PayOutcome?>(null) }
    var settling by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(20) }
    var resumes by remember { mutableStateOf(0) }

    LaunchedEffect(tick, resumes) {
        repo.credits().onSuccess {
            data = it; error = null
            // The header's chip follows the balance: a balance that went up is no longer low.
            val old = BoardHub.chip.value
            BoardHub.setChip(it.balance, old?.low == true && it.balance <= old.balance)
        }
            .onFailure { error = it.boardMessage() }
    }
    // Back from the pay page: ask where the order stands (the gateway may take a moment to confirm), then say so.
    LaunchedEffect(resumes) {
        val order = pendingOrder
        if (order.isBlank()) return@LaunchedEffect
        settling = true
        var last: OrderStatus? = null
        for (i in 0 until 5) {
            val r = repo.order(order).getOrNull()
            if (r != null) last = r
            if (r != null && r.status != "pending") break
            delay(2000)
        }
        settling = false
        val s = last
        if (s?.status == "paid") {
            outcome = PayOutcome(true, "${creditsText(s.credits)} credits added.")
            pendingOrder = ""
            tick++
        } else {
            // A failed order is said at once; a pending one keeps the careful wording (it can still turn paid, so the balance is read again).
            outcome = PayOutcome(false, if (s?.status == "failed") "Payment not completed. Nothing was charged. You can try again." else if (s?.status == "pending") "Payment not completed. If you did pay, your credits arrive shortly; otherwise nothing was charged." else "Payment not completed. No credits were added and nothing was charged.")
            pendingOrder = ""
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    fun buy(p: CreditPack) {
        buying = p.key; refusal = null; outcome = null
        scope.launch {
            repo.checkout(p.key)
                .onSuccess {
                    pendingOrder = it.order
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it.payUrl))
                }
                .onFailure { refusal = it.boardMessage() }
            buying = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(eyebrow = "Counsel", title = "Credits") }
        outcome?.let { o -> item { OutcomeBanner(o) { outcome = null } } }
        if (settling) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
                Text("Confirming your payment…", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
            }
        }
        val d = data
        if (d == null) item {
            if (error != null) LexCard { LexEmpty("Credits Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { error = null; tick++ }) }) }
            else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) }
        } else {
            item {
                LexCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow("Your Balance", Modifier.weight(1f))
                            // The long explanation lives behind the "i".
                            InfoTip(
                                "Free: the board and the cause list. Credits pay for reading a case from the court: Add By CNR costs ${d.costAdd} credits, Update From Court costs ${d.costAdd}, and the morning check costs ${d.costCheck}. Credits last ${d.creditMonths} months.",
                                label = "What credits pay for",
                            )
                        }
                        Text("${creditsText(d.balance)} Credits", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Lex.colors.foreground)
                        val at = BoardDates.checkedLabel(d.checked.at, BoardDates.today())
                        Text(
                            if (at == null) "eCourts not checked yet"
                            else "eCourts checked $at" + if (d.checked.cases > 0) " · ${d.checked.cases} ${if (d.checked.cases == 1) "case" else "cases"}" else "",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground,
                        )
                        d.expiring.forEach {
                            Text("${creditsText(it.credits)} credits expire on ${BoardDates.parse(it.expiresAt)?.let { x -> BoardDates.long(x) } ?: it.expiresAt}.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.danger.softText)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(creditsText(d.spent.today), "Spent Today", Modifier.weight(1f).fillMaxHeight())
                    Tile(creditsText(d.spent.month), "This Month", Modifier.weight(1f).fillMaxHeight())
                    Tile(creditsText(d.spent.all), "All Time", Modifier.weight(1f).fillMaxHeight())
                }
            }
            refusal?.let { item { NoteLine(it, danger = true) } }
            item { Eyebrow("Buy Credits", modifier = Modifier.padding(start = 4.dp)) }
            if (d.packs.isEmpty()) item { NoteLine("No packs are on sale just now.") }
            else item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
                    items(d.packs, key = { it.key }) { p ->
                        PackCard(p, packLasts(p.credits, d.checked.onCheck, d.costCheck, d.creditMonths), busy = buying != null, buying = buying == p.key) { buy(p) }
                    }
                }
            }
            item { Eyebrow("History", modifier = Modifier.padding(start = 4.dp)) }
            item {
                LexCard {
                    if (d.history.isEmpty()) LexEmpty("No Movements Yet", "Your credits and what they were used for appear here.")
                    d.history.take(shown).forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = Lex.colors.border)
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.label.ifBlank { h.kind }, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                                Text(BoardDates.parse(h.whenAt)?.let { BoardDates.long(it) } ?: h.whenAt.take(10), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                            }
                            StatusChip((if (h.delta > 0) "+" else "") + creditsText(h.delta), if (h.delta > 0) Lex.colors.success else Lex.colors.neutral)
                        }
                    }
                    if (d.history.size > shown) {
                        HorizontalDivider(color = Lex.colors.border)
                        TextButton({ shown += 20 }, Modifier.fillMaxWidth()) { Text("Show More", color = Lex.colors.foreground) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** One pack, as a product card: its name, credits, price with GST, how long it lasts, and a buy symbol. */
@Composable
private fun PackCard(p: CreditPack, lasts: String, busy: Boolean, buying: Boolean, onBuy: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(236.dp).clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Eyebrow(p.label, color = Lex.colors.secondaryForeground)
        Text(creditsText(p.credits), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = Lex.colors.foreground)
        Text("credits", style = MaterialTheme.typography.labelMedium, color = Lex.colors.mutedForeground)
        Spacer(Modifier.height(8.dp))
        Text("${formatMoney(p.price, "₹")} incl. GST", style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
        Text(lasts, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, minLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (buying) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
            else IconAction(Icons.Outlined.ShoppingCart, "Buy the ${p.label} pack", { if (!busy) onBuy() }, look = ActionLook.Gold, size = 48.dp)
        }
    }
}

@Composable
private fun OutcomeBanner(o: PayOutcome, onClose: () -> Unit) {
    val pal = if (o.ok) Lex.colors.success else Lex.colors.danger
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.softBg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (o.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = pal.softText)
        Text(o.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = pal.softText)
        TextButton(onClose) { Text("Dismiss", color = pal.softText) }
    }
}
