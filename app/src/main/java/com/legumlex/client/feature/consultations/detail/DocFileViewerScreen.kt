package com.legumlex.client.feature.consultations.detail

import com.legumlex.client.ui.theme.Zinc300
import com.legumlex.client.ui.theme.Zinc400
import com.legumlex.client.ui.theme.Zinc950
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Inline viewer for a document the client uploaded to fulfil a doc-request row.
 * Streams the file from the doc-request file endpoint and renders it in-app —
 * PDFs page-by-page via the platform PdfRenderer, images decoded directly — so
 * the client never has to leave the app or hand off to a third-party viewer.
 *
 * Mirrors [AhcDocumentViewerScreen]; the only differences are the source
 * (doc-request id, not the AHC document) and image support (uploads can be
 * camera JPEGs, not just PDFs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocFileViewerScreen(
    requestId: String,
    fileName: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: DocFileViewerViewModel = viewModel(
        key     = "docfile-$requestId",
        factory = viewModelFactory {
            initializer { DocFileViewerViewModel(app = app, requestId = requestId) }
        },
    )
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            fileName?.takeIf { it.isNotBlank() } ?: "Document",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                        )
                        val pageCount = (state as? DocViewerUiState.Ready)?.pages?.size
                        if (pageCount != null && pageCount > 1) {
                            Text(
                                "$pageCount pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = Zinc400,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Zinc950),
            )
        },
        containerColor = Zinc950,
    ) { inner ->
        when (val s = state) {
            DocViewerUiState.Loading -> ViewerLoadingD(inner)
            is DocViewerUiState.Error -> ViewerErrorD(inner, s.message, onRetry = vm::load)
            is DocViewerUiState.Ready -> PagesListD(inner, s.pages)
        }
    }
}

@Composable
private fun ViewerLoadingD(inner: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text("Loading document…", color = Zinc400, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ViewerErrorD(inner: PaddingValues, msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Zinc400, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(msg, color = Zinc300, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text("Retry")
        }
    }
}

@Composable
private fun PagesListD(inner: PaddingValues, pages: List<Bitmap>) {
    var scale by remember { mutableStateOf(1f) }
    var panX  by remember { mutableStateOf(0f) }
    var panY  by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .background(Zinc950)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    if (newScale == 1f) { panX = 0f; panY = 0f } else { panX += pan.x; panY += pan.y }
                    scale = newScale
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX, translationY = panY),
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            userScrollEnabled = scale == 1f,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pages) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White),
                )
            }
        }
    }
}

class DocFileViewerViewModel(
    app: Application,
    private val requestId: String,
) : AndroidViewModel(app) {

    private val repo = ConsultationsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<DocViewerUiState>(DocViewerUiState.Loading)
    val state: StateFlow<DocViewerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = DocViewerUiState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val dir = File(ctx.cacheDir, "docreq").apply { mkdirs() }
                    val file = File(dir, "dr_$requestId.bin")
                    if (!file.exists() || file.length() == 0L) {
                        repo.downloadDocRequestFile(requestId).byteStream().use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        }
                    }
                    if (isPdf(file)) renderPdf(file) else listOfNotNull(decodeImage(file))
                }
            }
                .onSuccess { pages ->
                    if (pages.isEmpty()) _state.value = DocViewerUiState.Error("This file can’t be previewed here.")
                    else _state.value = DocViewerUiState.Ready(pages)
                }
                .onFailure { e -> _state.value = DocViewerUiState.Error(friendly(e)) }
        }
    }

    /** PDFs start with the "%PDF" magic; anything else we treat as an image. */
    private fun isPdf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 && String(header) == "%PDF"
        }
    }.getOrDefault(false)

    private fun decodeImage(file: File): Bitmap? = runCatching {
        // Down-sample huge camera photos so a single page never blows memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val target = (getApplication<Application>().resources.displayMetrics.widthPixels * 2).coerceAtMost(3000)
        var sample = 1
        while (bounds.outWidth / sample > target) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    private fun renderPdf(file: File): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val dm = getApplication<Application>().resources.displayMetrics
                val targetWidth = (dm.widthPixels * 2).coerceAtMost(3000)
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val ratio  = page.height.toFloat() / page.width.toFloat()
                        val width  = targetWidth
                        val height = (width * ratio).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
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
        (state.value as? DocViewerUiState.Ready)?.pages?.forEach { if (!it.isRecycled) it.recycle() }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            401, 403   -> "Your session expired. Sign in again."
            404        -> "This document is no longer available."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn’t reach the server (HTTP ${e.code()})."
        }
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Couldn’t open the document."
    }
}

sealed interface DocViewerUiState {
    data object Loading : DocViewerUiState
    data class Ready(val pages: List<Bitmap>) : DocViewerUiState
    data class Error(val message: String) : DocViewerUiState
}
