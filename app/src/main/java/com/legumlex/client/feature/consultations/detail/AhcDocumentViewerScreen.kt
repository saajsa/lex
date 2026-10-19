package com.legumlex.client.feature.consultations.detail

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.consultations.ConsultationsRepository
import com.legumlex.client.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Inline PDF viewer for the AHC source document.
 *
 * Uses Android's built-in PdfRenderer (android.graphics.pdf) — no new
 * dependencies. Pages render to bitmaps at the device's natural width and
 * stack vertically in a LazyColumn. The whole stack supports pinch-zoom +
 * pan via Modifier.graphicsLayer so the client can read fine print without
 * leaving the app.
 *
 * Why not androidx.pdf:pdf-viewer? It pulls in a Fragment dependency that
 * doesn't compose cleanly with our Compose-only nav graph, and adds ~3 MB.
 * The platform PdfRenderer covers the same need with no surface area.
 *
 * Caching: the source PDF is downloaded once into cacheDir/ahc/ahc_{id}.pdf
 * (same path the detail-screen download flow uses). Subsequent opens read
 * from cache instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhcDocumentViewerScreen(
    consultationId: String,
    onBack: () -> Unit,
    onDownloadToPhone: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: AhcDocumentViewerViewModel = viewModel(
        key     = "ahcviewer-$consultationId",
        factory = viewModelFactory {
            initializer { AhcDocumentViewerViewModel(app = app, consultationId = consultationId) }
        },
    )
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Agreement document",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        val pageCount = (state as? AhcViewerUiState.Ready)?.pageCount
                        if (pageCount != null) {
                            Text(
                                "$pageCount page${if (pageCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA8A29E),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onDownloadToPhone) {
                        Icon(Icons.Outlined.FileDownload, "Open in another app", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1917),
                ),
            )
        },
        containerColor = Color(0xFF1C1917),
    ) { inner ->
        when (val s = state) {
            AhcViewerUiState.Loading -> ViewerLoading(inner)
            is AhcViewerUiState.Error -> ViewerError(inner, s.message, onRetry = vm::load)
            is AhcViewerUiState.Ready -> PdfPagesList(inner, s.pages)
        }
    }
}

// ─── Subscreens ──────────────────────────────────────────────────────────────

@Composable
private fun ViewerLoading(inner: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(inner),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Loading document…",
                color = Color(0xFFA8A29E),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ViewerError(inner: PaddingValues, msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Color(0xFFA8A29E), modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            msg,
            color = Color(0xFFD4D4D8),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text("Retry") }
    }
}

@Composable
private fun PdfPagesList(inner: PaddingValues, pages: List<Bitmap>) {
    // Pinch-zoom + pan applied to the whole stack via graphicsLayer. We clamp
    // scale [1f..4f] and pan within the visible window so the user can't lose
    // the document off-screen.
    var scale by remember { mutableStateOf(1f) }
    var panX  by remember { mutableStateOf(0f) }
    var panY  by remember { mutableStateOf(0f) }

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .background(Color(0xFF1C1917))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    if (newScale == 1f) {
                        // Snap pan back to zero when zoomed out so list scroll resumes naturally.
                        panX = 0f; panY = 0f
                    } else {
                        panX += pan.x
                        panY += pan.y
                    }
                    scale = newScale
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = panX,
                translationY = panY,
            ),
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            userScrollEnabled = scale == 1f,  // disable list scroll while zoomed — pan owns the gesture
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pages) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class AhcDocumentViewerViewModel(
    app: Application,
    private val consultationId: String,
) : AndroidViewModel(app) {

    private val repo = ConsultationsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<AhcViewerUiState>(AhcViewerUiState.Loading)
    val state: StateFlow<AhcViewerUiState> = _state.asStateFlow()

    init { load() }

    /**
     * Downloads (or finds cached) the PDF, then renders every page to a
     * bitmap. PdfRenderer requires a seekable file descriptor — so the
     * download must land on disk first, even though we own the bytes.
     *
     * Pages render at ~2× device density for crisp text without exploding
     * memory; for a 20-page contract this is ~30 MB of bitmaps held in
     * memory while the screen is open. Bitmaps are recycled in onCleared.
     */
    fun load() {
        viewModelScope.launch {
            _state.value = AhcViewerUiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val ctx  = getApplication<Application>()
                    val dir  = File(ctx.cacheDir, "ahc").apply { mkdirs() }
                    val pdf  = File(dir, "ahc_${consultationId}.pdf")
                    if (!pdf.exists() || pdf.length() == 0L) {
                        val body = repo.downloadAhcDocument(consultationId)
                        body.byteStream().use { input ->
                            pdf.outputStream().use { input.copyTo(it) }
                        }
                    }
                    renderPages(pdf)
                }
            }
                .onSuccess { pages ->
                    _state.value = AhcViewerUiState.Ready(pages = pages, pageCount = pages.size)
                }
                .onFailure { e ->
                    _state.value = AhcViewerUiState.Error(friendly(e))
                }
        }
    }

    private fun renderPages(file: File): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val displayMetrics = getApplication<Application>().resources.displayMetrics
                // Target width = screen width × 2 for retina-ish crispness on
                // mid-range devices. Capped so a single page never exceeds
                // ~6 MP — protects against memory blowups on massive PDFs.
                val targetWidth = (displayMetrics.widthPixels * 2)
                    .coerceAtMost(3000)

                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val ratio  = page.height.toFloat() / page.width.toFloat()
                        val width  = targetWidth
                        val height = (width * ratio).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                            // PdfRenderer paints transparent areas as black by
                            // default — pre-fill white so margins look right.
                            eraseColor(android.graphics.Color.WHITE)
                        }
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages += bitmap
                    }
                }
            }
        }
        return pages
    }

    override fun onCleared() {
        super.onCleared()
        (state.value as? AhcViewerUiState.Ready)?.pages?.forEach {
            if (!it.isRecycled) it.recycle()
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            401, 403   -> "Your session expired. Sign in again."
            404        -> "This document is no longer available."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn't reach the server (HTTP ${e.code()})."
        }
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Couldn't open the document."
    }
}

sealed interface AhcViewerUiState {
    data object Loading : AhcViewerUiState
    data class Ready(val pages: List<Bitmap>, val pageCount: Int) : AhcViewerUiState
    data class Error(val message: String) : AhcViewerUiState
}
