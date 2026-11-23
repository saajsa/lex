package com.legumlex.client.feature.board

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.Zinc300
import com.legumlex.client.ui.theme.Zinc400
import com.legumlex.client.ui.theme.Zinc950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

// REQ-038 038.2 hearings and 038.3 orders on the counsel case page: copies from eCourts, fetched once at a cost, kept, opened free.

/** One order as the server states it: [state] available (not fetched; [cost] is its price), on_file, signed or encrypted (all three kept). */
data class BoardOrder(
    val id: Int, val label: String, val date: String?, val kind: String, val state: String, val cost: Int,
    val pages: Int?, val size: Int?, val fetchedAt: String?,
) {
    /** Kept on the platform: free to open. */
    val fetched: Boolean get() = state != "available"
    /** Signed or encrypted: opens on its own, never joined into the combined PDF. */
    val sealed: Boolean get() = state == "signed" || state == "encrypted"
}
/** One listing date (038.2): eCourts' purpose and judge, the next date, and the orders of that day. business, item and outcome are null for now. */
data class BoardListing(
    val date: String, val purpose: String, val business: String?, val judge: String, val item: String?, val outcome: String?,
    val nextDate: String?, val nextPurpose: String, val orders: List<BoardOrder>,
)
data class CaseRecord(val loaded: Boolean, val listings: List<BoardListing>, val orders: List<BoardOrder>)
data class OrderFetch(val charged: Int, val balance: Int, val order: BoardOrder)

internal fun parseCourtOrder(m: Map<String, Any?>) = BoardOrder(
    id = m["id"].int(), label = m["label"].str(), date = m["date"].strOrNull(), kind = m["kind"].str().ifBlank { "Order" },
    // An unknown or absent state is treated as not fetched: the server then answers the fetch as it sees fit.
    state = m["state"].str().takeIf { it in setOf("available", "on_file", "signed", "encrypted") } ?: "available",
    cost = m["cost"].int(), pages = m["pages"].intOrNull(), size = m["size"].intOrNull(), fetchedAt = m["fetched_at"].strOrNull(),
)

internal fun parseRecord(d: Map<String, Any?>) = CaseRecord(
    loaded = d["record_loaded"] == true,
    listings = d["listings"].list().map {
        BoardListing(it["date"].str(), it["purpose"].str(), it["business"].strOrNull(), it["judge"].str(), it["item"].strOrNull(), it["outcome"].strOrNull(),
            it["next_date"].strOrNull(), it["next_purpose"].str(), it["orders"].list().map(::parseCourtOrder))
    },
    orders = d["orders"].list().map(::parseCourtOrder),
)

internal fun parseOrderFetch(d: Map<String, Any?>) = OrderFetch(d["charged"].int(), d["balance"].int(), parseCourtOrder(d["order"].map()))

/** The order's name: the server's label ("Order of 12 Nov 2026"), else its kind and date the same way. */
internal fun orderLabel(o: BoardOrder): String = o.label.ifBlank {
    o.kind + (BoardDates.parse(o.date)?.let { " of " + it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)) } ?: "")
}

/** Its state: "Kept, 3 pages", "Kept, 1 page, signed: opens on its own", or "Not fetched yet" (no cost shown: owner, 23 Nov 2026). */
internal fun orderState(o: BoardOrder): String = when {
    o.fetched -> "Kept" + (o.pages?.let { ", ${creditsText(it)} ${if (it == 1) "page" else "pages"}" } ?: "") + (if (o.sealed) ", ${o.state}: opens on its own" else "")
    else -> "Not fetched yet"
}

/** The web offers the combined PDF once any order is on file (signed and encrypted copies never join it). */
internal fun combinedOffered(orders: List<BoardOrder>): Boolean = orders.any { it.state == "on_file" }

/** The ids the server left out of the combined PDF, from its X-Orders-Left-Out header ("" when none). */
internal fun leftOutIds(header: String?): List<Int> = header.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }

/** The viewer's note for what the combined PDF left out. */
internal fun leftOutNote(n: Int): String? = if (n <= 0) null else "${creditsText(n)} kept ${if (n == 1) "order is" else "orders are"} not in this PDF: ${if (n == 1) "it opens" else "they open"} on its own."

internal const val ORDERS_LINE = "Copies from eCourts. A fetched copy is kept: opening it again is free."
internal const val HEARINGS_UNLOADED = "Update From Court to load hearings."

/** Why a tab is empty, in the web's words: no CNR yet, not read yet, or eCourts lists none. [what] is "hearings" or "orders". */
internal fun recordEmpty(what: String, hasCnr: Boolean, loaded: Boolean): String = when {
    !hasCnr -> "${what.replaceFirstChar { it.uppercase() }} come from eCourts once the case has a CNR."
    !loaded -> "Update From Court to load $what."
    else -> "eCourts lists no $what for this case yet."
}

/** "Listed for Arguments. Next: Fri, 28 Nov 2026, Evidence." */
internal fun hearingLine(h: BoardListing): String {
    val listed = h.purpose.takeIf { it.isNotBlank() }?.let { "Listed for $it." }
    val next = BoardDates.parse(h.nextDate)?.let { "Next: ${BoardDates.long(it)}" + (h.nextPurpose.takeIf { p -> p.isNotBlank() }?.let { p -> ", $p" } ?: "") + "." }
    return listOfNotNull(listed, next).joinToString(" ")
}

/** An empty Hearings or Orders tab: the web's reason, and Update From Court Now right there when a read would load it. */
@Composable
private fun RecordEmptyCard(title: String, what: String, icon: androidx.compose.ui.graphics.vector.ImageVector, r: CaseRecord, hasCnr: Boolean, readLabel: String, onRead: (() -> Unit)?) {
    LexCard {
        LexEmpty(title, recordEmpty(what, hasCnr, r.loaded), icon = icon,
            action = if (offerRead(hasCnr, r.loaded)) ({ LegumSecondaryButton(readLabel, { onRead?.invoke() }, enabled = onRead != null) }) else null)
    }
}

/** A read is offered on an empty tab only when it would load something: the case has a CNR and its record is not loaded yet. */
internal fun offerRead(hasCnr: Boolean, loaded: Boolean): Boolean = hasCnr && !loaded

/** The orders not shown under any hearing (no hearing date, or a date with no listing): the rare "Other Orders". */
internal fun otherOrders(r: CaseRecord): List<BoardOrder> {
    val shown = r.listings.flatMap { l -> l.orders.map { it.id } }.toSet()
    return r.orders.filter { it.id !in shown }
}

/**
 * The case's hearings (owner, 23 Nov 2026): soft tiles newest first, 8dp apart; the date bold, the
 * purpose and next line, the judge muted, on the left 80%; the order buttons stacked in the right 20%. Other Orders last.
 * Empty: the web's reason, with Update From Court Now when a read helps. All Kept Orders As One PDF lives in the case's ⋮ menu.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CaseRecordSection(
    r: CaseRecord, hasCnr: Boolean, readLabel: String, onRead: (() -> Unit)?, enabled: Boolean,
    onOpen: (BoardOrder) -> Unit, onGet: (BoardOrder) -> Unit, fetchingId: Int? = null,
) {
    val other = otherOrders(r)
    if (r.listings.isEmpty() && other.isEmpty()) { RecordEmptyCard("No Hearings Yet", "hearings", Icons.Outlined.Event, r, hasCnr, readLabel, onRead); return }
    com.legumlex.client.ui.components.Eyebrow("Hearings")
    @Composable
    fun HearingRow(title: String, lines: List<Pair<String, Boolean>>, orders: List<BoardOrder>) {
        Box(Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Lex.colors.card).padding(12.dp)) {
            HearingSplit(orders, enabled, fetchingId, onOpen, onGet) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Lex.colors.foreground)
                lines.forEach { (t, muted) ->
                    Text(t, style = MaterialTheme.typography.bodySmall,
                        color = if (muted) Lex.colors.mutedForeground else Lex.colors.secondaryForeground)
                }
            }
        }
    }
    r.listings.forEach { h ->
        HearingRow(BoardDates.parse(h.date)?.let { BoardDates.long(it) } ?: h.date,
            listOfNotNull(hearingLine(h).takeIf { it.isNotBlank() }?.let { it to false }, h.judge.takeIf { it.isNotBlank() }?.let { it to true }), h.orders)
    }
    if (other.isNotEmpty()) HearingRow("Other Orders", emptyList(), other)
}

/**
 * Getting an order (owner, 23 Nov 2026: no cost on orders, no confirm): a tap fetches it at once; its icon shows progress, then the
 * viewer opens. The server charges as before and refunds a failure; a refusal (402 with Buy Credits, and the rest) shows its words.
 */
class OrderGetter {
    /** The order being fetched now, or null. */
    var fetchingId by mutableStateOf<Int?>(null)
    var refusal by mutableStateOf<String?>(null)
    var refusalCode by mutableStateOf<String?>(null)
    val fetching: Boolean get() = fetchingId != null
}

@Composable
fun rememberOrderGetter(): OrderGetter = remember { OrderGetter() }

/** Returns the tap handler: fetch [o] of case [caseId]; on success the balance chip moves and [onOpened] opens it; a refusal re-reads the balance (refunds). */
@Composable
fun orderFetcher(g: OrderGetter, onOpened: (caseId: Int, orderId: Int, title: String) -> Unit, onChanged: (caseId: Int) -> Unit): (Int, BoardOrder) -> Unit {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return { caseId, o ->
        if (!g.fetching) {
            g.refusal = null; g.refusalCode = null; g.fetchingId = o.id
            scope.launch {
                repo.orderFetch(caseId, o.id).onSuccess { r ->
                    BoardHub.applyBalance(r.balance)
                    onChanged(caseId)
                    onOpened(caseId, r.order.id, orderLabel(r.order))
                }.onFailure {
                    g.refusal = it.boardMessage(); g.refusalCode = it.boardCode()
                    repo.credits().onSuccess { c -> BoardHub.applyBalance(c.balance) }
                    onChanged(caseId)
                }
                g.fetchingId = null
            }
        }
    }
}

/** Saves a PDF to Downloads (Android 10 and later); false where the system has no such store, and the share sheet is used instead. */
internal fun saveToDownloads(context: Context, file: File, name: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        true
    }.getOrDefault(false)
}

/** A file name for Downloads: the order's name, safe for the file system. */
internal fun downloadName(title: String): String = title.replace(Regex("[^A-Za-z0-9 ._-]"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { "Order" } + ".pdf"

private sealed interface OrderView {
    data object Loading : OrderView
    data class Ready(val file: File, val sizes: List<Pair<Int, Int>>) : OrderView
    data class Error(val message: String) : OrderView
}

/** A kept order, or the combined PDF ([orderId] 0), drawn page by page with Print, Share and Download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderViewerScreen(caseId: Int, orderId: Int, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    var state by remember { mutableStateOf<OrderView>(OrderView.Loading) }
    var tick by remember { mutableStateOf(0) }
    var leftOut by remember { mutableStateOf(0) }
    LaunchedEffect(caseId, orderId, tick) {
        state = OrderView.Loading
        (if (orderId > 0) repo.orderFile(caseId, orderId) else repo.combined(caseId).map { (f, ids) -> leftOut = ids.size; f })
            .onSuccess { f ->
                runCatching { withContext(Dispatchers.IO) { pdfPageSizes(f) } }
                    .onSuccess { state = if (it.isEmpty()) OrderView.Error("This order has no pages.") else OrderView.Ready(f, it) }
                    .onFailure { state = OrderView.Error("The order could not be shown. You can try again.") }
            }
            .onFailure { state = OrderView.Error(if (it.isOffline()) "This order is not on this device yet. Please try again online." else it.boardMessage()) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                // The whole label ("Interim Order of 8 Nov 2026 (1)"): up to two lines, a step smaller.
                title = { Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    (state as? OrderView.Ready)?.let { r ->
                        IconButton(onClick = { printSheet(context, r.file, title, jobName = title) }) { Icon(Icons.Outlined.Print, "Print", tint = Color.White) }
                        IconButton(onClick = { shareSheet(context, r.file, title) }) { Icon(Icons.Outlined.Share, "Share", tint = Color.White) }
                        IconButton(onClick = {
                            if (saveToDownloads(context, r.file, downloadName(title))) Toast.makeText(context, "Saved to Downloads.", Toast.LENGTH_SHORT).show()
                            else shareSheet(context, r.file, title)
                        }) { Icon(Icons.Outlined.Download, "Download", tint = Color.White) }
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Zinc950),
            )
        },
        containerColor = Zinc950,
    ) { inner ->
        when (val s = state) {
            OrderView.Loading -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp) }
            is OrderView.Error -> Column(Modifier.fillMaxSize().padding(inner).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Zinc400, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(s.message, color = Zinc300, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { tick++ }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Try Again") }
            }
            is OrderView.Ready -> LazyColumn(Modifier.fillMaxSize().padding(inner), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
                leftOutNote(leftOut)?.let { n -> item { Text(n, color = Zinc300, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) } }
                items(s.sizes.size, key = { it }) { i ->
                    val (pw, ph) = s.sizes[i]
                    val bmp by androidx.compose.runtime.produceState<Bitmap?>(null, s.file, i) {
                        value = runCatching { withContext(Dispatchers.IO) { renderPdfPage(context, s.file, i) } }.getOrNull()
                    }
                    val shape = RoundedCornerShape(4.dp)
                    val b = bmp
                    if (b != null) Image(b.asImageBitmap(), "Order page ${i + 1}", Modifier.fillMaxWidth().clip(shape).background(Color.White), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.fillMaxWidth().aspectRatio(pw.toFloat() / ph.coerceAtLeast(1)).clip(shape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
