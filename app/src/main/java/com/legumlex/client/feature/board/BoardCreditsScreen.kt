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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.abs
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
import com.legumlex.client.feature.consultations.apiError
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
    val store = remember { PendingOrderStore(context.cacheDir) }
    // The order sent to the pay page and not settled yet: kept on the device, so it outlives the process.
    var pending by remember { mutableStateOf(store.get()) }
    var outcome by remember { mutableStateOf<PayOutcome?>(null) }
    var settling by remember { mutableStateOf(false) }
    // The server said the pay page ended without a payment, but the order stays payable: it is kept and asked about again.
    var notCompleted by remember { mutableStateOf(false) }
    // The pages read after the first stay while the same history is read again (a resume); a new first row or a different length starts over.
    val historyKey = historyKey(data?.history)
    var extra by remember(historyKey) { mutableStateOf<List<LedgerRow>>(emptyList()) }
    var historyTotal by remember(historyKey) { mutableStateOf<Int?>(null) }
    var historyDone by remember(historyKey) { mutableStateOf(false) }
    var loadingMore by remember(historyKey) { mutableStateOf(false) }
    var resumes by remember { mutableStateOf(0) }

    LaunchedEffect(tick, resumes) {
        repo.credits().onSuccess {
            data = it; error = null
            // The header's chip follows the balance: a balance that went up is no longer low.
            BoardHub.applyBalance(it.balance)
        }
            .onFailure { error = it.boardMessage() }
    }
    // Asks the server once where the pending order stands. Called on every resume (which includes opening the screen after the
    // app was closed) and by Check Again; there is no loop and no give-up: a pending order is asked again the next time.
    suspend fun checkPending() {
        val p = pending ?: return
        settling = true
        val res = repo.order(p.order)
        val r = res.getOrNull()
        settling = false
        if (r == null && orderGone(res.exceptionOrNull()?.apiError()?.http)) {
            outcome = PayOutcome(false, "That payment could not be found. Nothing was charged."); store.clear(); pending = null; notCompleted = false
            return
        }
        when (orderVerdict(r?.status)) {
            OrderVerdict.Paid -> {
                outcome = PayOutcome(true, "${creditsLabel(r?.credits ?: 0)} added.")
                store.clear(); pending = null; notCompleted = false; tick++
            }
            OrderVerdict.Failed -> { outcome = PayOutcome(false, EXPIRED_NOTE); store.clear(); pending = null; notCompleted = false }
            OrderVerdict.NotCompleted -> {
                if (failedOrderGivenUp(p.startedAt, System.currentTimeMillis())) { outcome = PayOutcome(false, FAILED_NOTE); store.clear(); pending = null; notCompleted = false }
                else notCompleted = true
            }
            OrderVerdict.Flagged -> { outcome = PayOutcome(false, FLAGGED_NOTE); store.clear(); pending = null; notCompleted = false; tick++ }
            OrderVerdict.Pending -> Unit
        }
    }
    LaunchedEffect(resumes) { checkPending() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        // The first resume is the screen opening, which loads for itself: only later resumes load again.
        var opened = false
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { if (opened) resumes++ else opened = true } }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    fun buy(p: CreditPack) {
        if (pending != null) return
        buying = p.key; refusal = null; outcome = null
        scope.launch {
            repo.checkout(p.key)
                .onSuccess {
                    // The order is kept only once the pay page has really opened (no browser, or no address, keeps nothing).
                    val opened = it.payUrl.isNotBlank() && runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it.payUrl)) }.isSuccess
                    if (opened) PendingOrder(it.order, p.key, System.currentTimeMillis()).also { o -> store.put(o); pending = o; notCompleted = false }
                    else refusal = NO_BROWSER_NOTE
                }
                .onFailure { refusal = it.boardMessage() }
            buying = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(eyebrow = "Counsel", title = "Credits") }
        outcome?.let { o -> item { OutcomeBanner(o) { outcome = null } } }
        if (pending != null && !settling) item {
            PendingBanner(if (notCompleted) FAILED_NOTE else PENDING_NOTE, { scope.launch { checkPending() } }) { store.clear(); pending = null; notCompleted = false }
        }
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
                                "Free: the board and the cause list. Credits pay for reading a case from the court: Add By CNR costs ${creditsLabel(d.costAdd)}, Update From Court Now costs ${creditsLabel(d.costAdd)}, and the morning check costs ${creditsLabel(d.costCheck)} a case. Credits last ${d.creditMonths} months.",
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
                        Text(
                            nextLapseLine(d.nextLapse), style = MaterialTheme.typography.bodySmall,
                            color = if (d.nextLapse != null) Lex.colors.danger.softText else Lex.colors.mutedForeground,
                        )
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
            else item { PackCarousel(d.packs, d, buying, pending != null, ::buy) }
            item { Eyebrow("History", modifier = Modifier.padding(start = 4.dp)) }
            if (d.history.isEmpty()) item { LexCard { LexEmpty("No Movements Yet", "Your credits and what they were used for appear here.") } }
            else {
                val rows = d.history + extra
                val hasMore = !historyDone && (historyTotal?.let { rows.size < it } ?: (d.history.size >= 60))
                itemsIndexed(rows, key = { i, h -> "h$i-${h.whenAt}-${h.label}" }) { i, h ->
                    val shape = RoundedCornerShape(12.dp)
                    // The next page is read when the last row on show is reached.
                    if (i == rows.lastIndex && hasMore && !loadingMore) LaunchedEffect(rows.size) {
                        loadingMore = true
                        repo.creditsHistory(50, rows.size)
                            .onSuccess { pg -> historyTotal = pg.total; if (pg.items.isEmpty()) historyDone = true else extra = extra + pg.items }
                            .onFailure { historyDone = true }
                        loadingMore = false
                    }
                    Column(Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(h.label.ifBlank { h.kind }, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                                Text(BoardDates.parse(h.whenAt)?.let { BoardDates.long(it) } ?: h.whenAt.take(10), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                            }
                            StatusChip((if (h.delta > 0) "+" else "") + creditsText(h.delta), if (h.delta > 0) Lex.colors.success else Lex.colors.neutral)
                        }
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
        Modifier.width(240.dp).clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(p.label, Modifier.weight(1f), color = Lex.colors.secondaryForeground)
            // The bonus, as the server states it (none when 0).
            if (p.bonusPct > 0) StatusChip("${p.bonusPct}% bonus", Lex.colors.success)
        }
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

/** "Expiring Next: 10 credits, Trial, 11 Dec 2026", the web's line; "Nothing is about to lapse" when there is no such lot. */
internal fun nextLapseLine(n: NextLapse?): String {
    n ?: return "Nothing is about to lapse"
    val kind = mapOf("trial" to "Trial", "purchase" to "Pack", "refund" to "Refund")[n.kind] ?: "Credits"
    val on = BoardDates.parse(n.expiresAt)?.let { BoardDates.long(it) } ?: n.expiresAt
    return "Expiring Next: ${creditsLabel(n.credits)}, $kind, $on"
}

/** What identifies the first page of the history: its newest row and its length. Equal keys mean the same history was read again. */
internal fun historyKey(rows: List<LedgerRow>?): Pair<Int?, Int> = rows?.firstOrNull()?.id to (rows?.size ?: 0)

/** An order is still waiting: careful wording (it may yet turn paid) and a way to ask again. */
@Composable
private fun PendingBanner(note: String, onCheck: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lex.colors.card).border(1.dp, Lex.colors.border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(note, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
        Column {
            TextButton(onCheck) { Text("Check Again", color = Lex.colors.foreground) }
            TextButton(onDismiss) { Text("Dismiss", color = Lex.colors.mutedForeground) }
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

/**
 * The packs as a carousel that snaps: one card is in focus at the centre (full size, full opacity), the others stand
 * back (smaller, dimmed). It opens on the middle pack. Dots show the place; only the card in focus can be bought.
 */
@Composable
private fun PackCarousel(packs: List<CreditPack>, d: CreditsData, buying: String?, pending: Boolean, onBuy: (CreditPack) -> Unit) {
    val cardWidth = 240.dp
    val pager = rememberPagerState(initialPage = packs.size / 2) { packs.size }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The carousel runs edge to edge (the page's 16 dp margin is given back), so the side cards show whole.
        BoxWithConstraints(Modifier.fillMaxWidth().layout { m, c ->
            val bleed = 16.dp.roundToPx()
            val w = c.maxWidth + bleed * 2
            val p = m.measure(c.copy(minWidth = w, maxWidth = w))
            layout(c.maxWidth, p.height) { p.place(-bleed, 0) }
        }) {
            val side = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager, pageSize = androidx.compose.foundation.pager.PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = side), pageSpacing = 12.dp,
            ) { page ->
                val p = packs[page]
                val away = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                val focused = page == pager.currentPage
                Box(Modifier.graphicsLayer { val k = 1f - 0.1f * away; scaleX = k; scaleY = k; alpha = 1f - 0.45f * away }) {
                    PackCard(
                        p, p.lasts,
                        busy = buying != null || pending, buying = buying == p.key,
                    ) { if (focused) onBuy(p) else scope.launch { pager.animateScrollToPage(page) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            packs.forEachIndexed { i, _ ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) Lex.colors.foreground else Lex.colors.mutedForeground.copy(alpha = 0.4f)))
            }
        }
    }
}
