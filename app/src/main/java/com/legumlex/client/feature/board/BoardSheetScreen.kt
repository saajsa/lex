package com.legumlex.client.feature.board

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.legumlex.client.ui.theme.Zinc300
import com.legumlex.client.ui.theme.Zinc400
import com.legumlex.client.ui.theme.Zinc950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hands a sheet to another app (WhatsApp, a printer, mail) as a file, through the app's FileProvider. */
fun shareSheet(context: Context, file: File, label: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Daily Board Sheet: $label")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share Sheet").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private sealed interface SheetState {
    data object Loading : SheetState
    data class Ready(val file: File, val pages: List<Bitmap>) : SheetState
    data class Error(val message: String) : SheetState
}

/** A day's cause list ("own": the counsel's own format; else a junior's old sheet): downloaded to the cache, drawn page by page, and shareable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSheetScreen(date: String, junior: String, label: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    var state by remember { mutableStateOf<SheetState>(SheetState.Loading) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(date, junior, tick) {
        state = SheetState.Loading
        repo.sheet(date, junior.takeIf { it.isNotBlank() && it != "own" })
            .onSuccess { f ->
                runCatching { withContext(Dispatchers.IO) { renderPdf(context, f) } }
                    .onSuccess { state = if (it.isEmpty()) SheetState.Error("This sheet has no pages.") else SheetState.Ready(f, it) }
                    .onFailure { state = SheetState.Error("The sheet could not be shown. You can try again.") }
            }
            .onFailure { state = SheetState.Error(if (it.isOffline()) "The sheet is made on the server, so it needs a connection. Please try again online." else it.boardMessage()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                        Text(BoardDates.parse(date)?.let { BoardDates.long(it) } ?: date, style = MaterialTheme.typography.labelSmall, color = Zinc400)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    (state as? SheetState.Ready)?.let { r ->
                        IconButton(onClick = { shareSheet(context, r.file, label) }) { Icon(Icons.Outlined.Share, "Share Sheet", tint = Color.White) }
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Zinc950),
            )
        },
        containerColor = Zinc950,
    ) { inner ->
        when (val s = state) {
            SheetState.Loading -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Making The Sheet…", color = Zinc400, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is SheetState.Error -> Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Zinc400, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(s.message, color = Zinc300, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { tick++ }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Try Again") }
            }
            is SheetState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(inner).background(Zinc950),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp),
            ) {
                items(s.pages) { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = "Sheet page", contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White),
                    )
                }
            }
        }
    }
}

/** The platform PdfRenderer, as the app's other viewers use it: every page to a white bitmap. */
private fun renderPdf(context: Context, file: File): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val target = (context.resources.displayMetrics.widthPixels * 2).coerceAtMost(3000)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val h = (target * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(target, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages += bmp
                }
            }
        }
    }
    return pages
}
