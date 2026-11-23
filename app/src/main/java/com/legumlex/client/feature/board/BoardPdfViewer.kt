package com.legumlex.client.feature.board

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WidthFull
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Zinc300
import com.legumlex.client.ui.theme.Zinc400
import com.legumlex.client.ui.theme.Zinc950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

// REQ-038, owner 23 Nov 2026 ("order viewer"): orders read in a proper viewer: pinch and double-tap zoom, pages, a page indicator,
// fit width or page, night reading, thumbnails, and auto-hiding bars. Built on the platform PdfRenderer; no new dependency.

/** The viewer's zoom and page maths, kept apart from Compose so they can be tested. */
object PdfMaths {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f
    const val TAP_ZOOM = 2.5f
    /** The largest page bitmap: 8 million pixels (32 MB at 4 bytes), and never a side over 4096 (the canvas limit). */
    const val MAX_PAGE_PIXELS = 8_000_000
    const val MAX_SIDE = 4096

    fun clamp(z: Float): Float = z.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** A double tap: back to 1× when zoomed, else to 2.5×. */
    fun doubleTap(current: Float): Float = if (current > MIN_ZOOM + 0.01f) MIN_ZOOM else TAP_ZOOM

    /** The zoom a button step gives: ×1.5 in, ÷1.5 out, within 1× to 5×. */
    fun step(current: Float, zoomIn: Boolean): Float = clamp(if (zoomIn) current * 1.5f else current / 1.5f)

    /** "250%". */
    fun percent(z: Float): String = "${(z * 100).roundToInt()}%"

    /**
     * Zooming by [k] about a focus point keeps that point still. Vertically, the page item under the focus has its top at
     * [itemTop]: after the zoom its top must sit at the returned position (negative: above the viewport's top).
     */
    fun anchoredTop(focusY: Float, itemTop: Float, k: Float): Float = focusY - (focusY - itemTop) * k

    /** Horizontally, the new scroll position that keeps the focus x still. */
    fun anchoredScrollX(scrollX: Float, focusX: Float, k: Float): Float = ((scrollX + focusX) * k - focusX).coerceAtLeast(0f)

    /** A page's width at 1×: the viewport's width (fit width), or as wide as lets the whole page fit the viewport (fit page). */
    fun baseWidth(fitPage: Boolean, viewW: Float, viewH: Float, pageW: Int, pageH: Int): Float =
        if (!fitPage || pageW <= 0 || pageH <= 0) viewW else minOf(viewW, viewH * pageW / pageH)

    /** The rendering zoom step for a settled zoom: whole steps (1, 2, 3, 4, 5), so a small pinch does not re-render. */
    fun bucket(z: Float): Int = ceil(clamp(z) - 0.05f).toInt().coerceIn(1, MAX_ZOOM.toInt())

    /** The bitmap width for a page shown [shownWidthPx] wide, capped by [MAX_SIDE] and [MAX_PAGE_PIXELS] for its aspect (h / w). */
    fun renderWidth(shownWidthPx: Float, aspect: Float): Int {
        var w = shownWidthPx.roundToInt().coerceIn(1, MAX_SIDE)
        val h = w * aspect
        if (h > MAX_SIDE) w = (MAX_SIDE / aspect).toInt()
        if (w.toLong() * (w * aspect).toLong() > MAX_PAGE_PIXELS) w = kotlin.math.sqrt(MAX_PAGE_PIXELS / aspect).toInt()
        return w.coerceAtLeast(1)
    }

    /** "2 / 7" for the page at [index] (0-based). */
    fun pageLabel(index: Int, count: Int): String = "${(index + 1).coerceIn(1, count.coerceAtLeast(1))} / $count"

    /** The page a thumbnail or the indicator asks for, kept in range. */
    fun jumpTarget(page: Int, count: Int): Int = page.coerceIn(0, (count - 1).coerceAtLeast(0))
}

/** Night reading: the page's colours inverted (white paper reads black, ink reads white). */
private val InvertFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)))

private sealed interface ViewerState {
    data object Loading : ViewerState
    data class Ready(val file: File, val sizes: List<Pair<Int, Int>>) : ViewerState
    data class Error(val message: String) : ViewerState
}

/**
 * A kept order, or the case's combined PDF ([orderId] 0). The top bar (back, the order's title on two lines, the case number) and a
 * floating toolbar (zoom out, %, zoom in, fit, invert, Share, Download, Print) hide after 3 s of reading and come back on a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderViewerScreen(caseId: Int, orderId: Int, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ViewerState>(ViewerState.Loading) }
    var tick by remember { mutableStateOf(0) }
    var leftOut by remember { mutableStateOf(0) }
    var bannerOpen by remember { mutableStateOf(true) }
    var caseNumber by remember { mutableStateOf("") }

    LaunchedEffect(caseId, orderId, tick) {
        state = ViewerState.Loading
        (if (orderId > 0) repo.orderFile(caseId, orderId) else repo.combined(caseId).map { (f, ids) -> leftOut = ids.size; f })
            .onSuccess { f ->
                runCatching { withContext(Dispatchers.IO) { pdfPageSizes(f) } }
                    .onSuccess { state = if (it.isEmpty()) ViewerState.Error("This order has no pages.") else ViewerState.Ready(f, it) }
                    .onFailure { state = ViewerState.Error("The order could not be shown. You can try again.") }
            }
            .onFailure { state = ViewerState.Error(if (it.isOffline()) "This order is not on this device yet. Please try again online." else it.boardMessage()) }
    }
    // The case number for the subtitle, from the case kept on the device when offline.
    LaunchedEffect(caseId) { repo.caseOrSaved(caseId).onSuccess { caseNumber = it.first.heading } }

    // Keep the screen on while reading.
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    var bars by remember { mutableStateOf(true) }
    var touched by remember { mutableStateOf(0) }
    LaunchedEffect(bars, touched) { if (bars) { delay(3000); bars = false } }

    var zoom by remember { mutableStateOf(1f) }
    var settled by remember { mutableStateOf(1f) }
    LaunchedEffect(zoom) { delay(250); settled = zoom }
    var fitPage by remember { mutableStateOf(false) }
    var invert by remember { mutableStateOf(false) }
    var thumbs by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var barHeight by remember { mutableStateOf(0) }
    val list = rememberLazyListState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current

    /** Zoom to [target] about [focus] (viewport pixels), keeping that point still. */
    fun zoomTo(target: Float, focus: Offset) {
        val next = PdfMaths.clamp(target)
        val k = next / zoom
        if (k == 1f) return
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { focus.y >= it.offset && focus.y < it.offset + it.size }
            ?: list.layoutInfo.visibleItemsInfo.firstOrNull()
        val top = item?.let { PdfMaths.anchoredTop(focus.y, it.offset.toFloat(), k) }
        val x = PdfMaths.anchoredScrollX(hScroll.value.toFloat(), focus.x, k)
        zoom = next
        scope.launch {
            awaitFrame()   // the new widths are laid out first
            if (item != null && top != null) {
                if (top <= 0f) list.scrollToItem(item.index, (-top).roundToInt()) else { list.scrollToItem(item.index); list.scrollBy(-top) }
            }
            hScroll.scrollTo(x.roundToInt())
        }
    }

    Box(Modifier.fillMaxSize().background(Zinc950)) {
        when (val s = state) {
            ViewerState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp) }
            is ViewerState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Zinc400, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(s.message, color = Zinc300, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { tick++ }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Try Again") }
            }
            is ViewerState.Ready -> {
                val vw = viewport.width.toFloat(); val vh = viewport.height.toFloat()
                val (pw0, ph0) = s.sizes.first()
                val base = if (vw > 0) PdfMaths.baseWidth(fitPage, vw, vh, pw0, ph0) else 0f
                val pageW = base * zoom
                val containerW = maxOf(vw, pageW)
                Box(
                    Modifier.fillMaxSize().onSizeChanged { viewport = it }
                        // Two fingers pinch (read before the list, so it does not scroll meanwhile); one finger scrolls as usual.
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val z = event.calculateZoom()
                                        if (z != 1f) zoomTo(zoom * z, event.calculateCentroid(useCurrent = true))
                                        event.changes.forEach { it.consume() }
                                        touched++
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { bars = !bars; touched++ },
                                onDoubleTap = { p -> zoomTo(PdfMaths.doubleTap(zoom), p) },
                            )
                        },
                ) {
                    if (vw > 0) Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                        LazyColumn(
                            Modifier.requiredWidth(with(density) { containerW.toDp() }).fillMaxHeight(), state = list,
                            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = with(density) { barHeight.toDp() } + 8.dp, bottom = 120.dp),
                        ) {
                            // The combined PDF's note: what it left out, above page 1, dismissible.
                            if (bannerOpen) leftOutNote(leftOut)?.let { note ->
                                item(key = "note") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Row(Modifier.width(with(density) { vw.toDp() } - 32.dp).clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2A2A2A)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(note, color = Zinc300, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            IconButton({ bannerOpen = false }) { Icon(Icons.Outlined.Close, "Dismiss", tint = Zinc400) }
                                        }
                                    }
                                }
                            }
                            items(s.sizes.size, key = { it }) { i ->
                                val (pw, ph) = s.sizes[i]
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PdfPage(s.file, i, pw, ph, with(density) { pageW.toDp() }, pageW, PdfMaths.bucket(settled) / zoom.coerceAtLeast(1f), invert)
                                }
                            }
                        }
                    }
                }
                // The page indicator: shown while scrolling (and with the bars); a tap opens the thumbnails.
                val noteItems = if (bannerOpen && leftOutNote(leftOut) != null) 1 else 0
                val scrolling = list.isScrollInProgress
                val shownIndicator by animateFloatAsState(if (scrolling || bars) 1f else 0f, label = "indicator")
                if (shownIndicator > 0f) Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f * shownIndicator)).clickable { thumbs = true }
                        .semantics { contentDescription = "Page ${PdfMaths.pageLabel(list.firstVisibleItemIndex - noteItems, s.sizes.size)}. Show all pages." }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text(PdfMaths.pageLabel(list.firstVisibleItemIndex - noteItems, s.sizes.size), color = Color.White.copy(alpha = shownIndicator), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

                if (thumbs) ThumbnailSheet(s.file, s.sizes, onClose = { thumbs = false }) { page ->
                    thumbs = false
                    scope.launch { list.scrollToItem(PdfMaths.jumpTarget(page, s.sizes.size) + noteItems) }
                }
            }
        }

        // The top bar: back, the title on two lines, the case number.
        AnimatedVisibility(bars || state !is ViewerState.Ready, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().onSizeChanged { barHeight = it.height }.background(Color.Black.copy(alpha = 0.72f)).statusBarsPadding().padding(end = 12.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (caseNumber.isNotBlank()) Text(caseNumber, color = Zinc400, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        val s = state
        // The floating toolbar.
        if (s is ViewerState.Ready) AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1F1F1F)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                Tool(Icons.Outlined.ZoomOut, "Zoom Out") { touched++; zoomTo(PdfMaths.step(zoom, false), center) }
                Text(PdfMaths.percent(zoom), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                Tool(Icons.Outlined.ZoomIn, "Zoom In") { touched++; zoomTo(PdfMaths.step(zoom, true), center) }
                Tool(if (fitPage) Icons.Outlined.WidthFull else Icons.Outlined.FitScreen, if (fitPage) "Fit Width" else "Fit Page") { touched++; fitPage = !fitPage; zoom = 1f }
                Tool(Icons.Outlined.InvertColors, if (invert) "Normal Colours" else "Night Reading", on = invert) { touched++; invert = !invert }
                Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 20.dp).background(Color.White.copy(alpha = 0.2f)))
                Tool(Icons.Outlined.Share, "Share") { shareSheet(context, s.file, title) }
                Tool(Icons.Outlined.Download, "Download") {
                    if (saveToDownloads(context, s.file, downloadName(title))) Toast.makeText(context, "Saved to Downloads.", Toast.LENGTH_SHORT).show()
                    else shareSheet(context, s.file, title)
                }
                Tool(Icons.Outlined.Print, "Print") { printSheet(context, s.file, title, jobName = title) }
            }
        }
    }
}

/** A toolbar button: white on the dark pill, its name on long-press and for screen readers; [on] marks a toggle that is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tool(icon: ImageVector, name: String, on: Boolean = false, onClick: () -> Unit) {
    TooltipBox(positionProvider = OnScreenTooltip, tooltip = { PlainTooltip { Text(name) } }, state = rememberTooltipState()) {
        IconButton(onClick, Modifier.size(40.dp).semantics { contentDescription = name }) {
            Icon(icon, null, tint = if (on) Color(0xFFB8D8BF) else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * One page, shown [shownW] wide (its height from its aspect). Its bitmap is drawn at the settled zoom step (sharp text when zoomed),
 * capped in memory; until then the last bitmap stays, scaled, or a placeholder of the right shape.
 */
@Composable
private fun PdfPage(file: File, index: Int, pw: Int, ph: Int, shownW: androidx.compose.ui.unit.Dp, shownWpx: Float, renderScale: Float, invert: Boolean) {
    val aspect = ph.toFloat() / pw.coerceAtLeast(1)
    val target = PdfMaths.renderWidth(shownWpx * renderScale, aspect)
    var bmp by remember(file, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, index, target) {
        // Only a materially different size is drawn again.
        if (bmp?.let { kotlin.math.abs(it.width - target) < 64 } == true) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { renderPdfPageAt(file, index, target) } }.onSuccess { bmp = it }
    }
    val b = bmp
    val m = Modifier.width(shownW).aspectRatio(pw.toFloat() / ph.coerceAtLeast(1)).clip(RoundedCornerShape(2.dp))
    if (b != null) Image(b.asImageBitmap(), "Page ${index + 1}", m.background(Color.White), contentScale = ContentScale.FillBounds,
        colorFilter = if (invert) InvertFilter else null)
    else Box(m.background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

/** Every page as a small thumbnail in a grid; a tap jumps to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailSheet(file: File, sizes: List<Pair<Int, Int>>, onClose: () -> Unit, onPick: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF1F1F1F)) {
        Text("Pages", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().height(420.dp), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sizes.size) { i ->
                val (pw, ph) = sizes[i]
                var b by remember(i) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(i) { b = runCatching { withContext(Dispatchers.IO) { renderPdfPageAt(file, i, 240) } }.getOrNull() }
                Column(Modifier.clickable { onPick(i) }.semantics { contentDescription = "Page ${i + 1}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val m = Modifier.fillMaxWidth().aspectRatio(pw.toFloat() / ph.coerceAtLeast(1)).clip(RoundedCornerShape(4.dp))
                    b?.let { Image(it.asImageBitmap(), null, m.background(Color.White), contentScale = ContentScale.FillBounds) }
                        ?: Box(m.background(Color.White.copy(alpha = 0.08f)))
                    Text("${i + 1}", color = Zinc400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
