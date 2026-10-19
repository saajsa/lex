package com.legumlex.client.feature.projects

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.api.uriToFilePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// -----------------------------------------------------------------------------
// "Client Project Files.html" — every document on a project, pushed from the
// Files drill-row on Project Detail. Banded by relationship (Shared by your
// chamber / Uploaded by you), type-tinted rows, a gated upload dropzone, and
// tap-to-open via the system viewer.
// -----------------------------------------------------------------------------

private val ClaretMidFix = Color(0xFFBE2E4A)
private val ClaretSoftFix = Color(0xFFFDF2F4)
private val ClaretBorderFix = Color(0xFFF4D9DF)

sealed interface FilesUiState {
    data object Loading : FilesUiState
    data class Ready(val projectName: String, val projectRef: String, val canUpload: Boolean, val files: List<ProjectFile>) : FilesUiState
    data class Error(val message: String) : FilesUiState
}

class ProjectFilesViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<FilesUiState>(FilesUiState.Loading)
    val state: StateFlow<FilesUiState> = _state.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    init { load() }

    fun load() { viewModelScope.launch { _state.value = FilesUiState.Loading; fetch(false) } }
    fun refresh() { if (_isRefreshing.value) return; viewModelScope.launch { _isRefreshing.value = true; fetch(true); _isRefreshing.value = false } }

    private suspend fun fetch(preserve: Boolean) {
        try {
            coroutineScope {
                val detail = async { repo.detail(projectId).getOrThrow() }
                val files = async { repo.files(projectId).getOrNull().orEmpty() }
                val d = detail.await()
                _state.value = FilesUiState.Ready(d.summary.name, "PRJ-${d.summary.id}", d.settings.uploadFiles, files.await())
            }
        } catch (e: Throwable) {
            if (!(preserve && _state.value is FilesUiState.Ready)) {
                _state.value = FilesUiState.Error(
                    when (e) {
                        is HttpException -> when (e.code()) { 401, 403 -> "Your session expired. Sign in again."; 404 -> "This project is no longer available."; in 500..599 -> "Server error. Try again in a moment."; else -> "Couldn't load files (HTTP ${e.code()})." }
                        else -> "Couldn't reach the server. Check your connection."
                    },
                )
            }
        }
    }

    fun uploadFile(part: MultipartBody.Part) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            repo.uploadFile(projectId, part)
                .onSuccess { added ->
                    _state.update { cur -> if (cur is FilesUiState.Ready) cur.copy(files = listOf(added) + cur.files) else cur }
                    _toast.value = "File uploaded"
                }
                .onFailure { _toast.value = "Upload failed. Try again." }
            _uploading.value = false
        }
    }

    fun openFile(file: ProjectFile, onReady: (File, String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = repo.downloadFile(file.id)
                    val name = (file.originalFileName ?: file.fileName).ifBlank { "document" }
                    val out = File(getApplication<Application>().cacheDir, "projfile_${file.id}_$name")
                    body.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
                    out to (file.fileType ?: "application/octet-stream")
                }
            }.onSuccess { (f, mime) -> onReady(f, mime) }.onFailure { onError() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFilesScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val scope = rememberCoroutineScope()
    val vm: ProjectFilesViewModel = viewModel(
        key = "files-$projectId",
        factory = viewModelFactory { initializer { ProjectFilesViewModel(app, projectId) } },
    )
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val toast by vm.toast.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { val part = uriToFilePart(context, it); if (part != null) vm.uploadFile(part) else Toast.makeText(context, "Couldn't read that file.", Toast.LENGTH_LONG).show() } }
    }
    val openFile = { f: ProjectFile -> vm.openFile(f, onReady = { file, mime -> openProjFile(context, file, mime) }, onError = { Toast.makeText(context, "Couldn't open the file.", Toast.LENGTH_LONG).show() }) }

    Scaffold(containerColor = LlColor.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                val ready = state as? FilesUiState.Ready
                FilesSubBar(ready?.files?.size, ready?.let { it.projectRef + " · " + it.projectName }, ready?.canUpload == true, onBack, onUpload = { filePicker.launch("*/*") })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val s = state) {
                        is FilesUiState.Loading -> FilesSkeleton()
                        is FilesUiState.Error -> FilesError(s.message, vm::load)
                        is FilesUiState.Ready -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                            FilesReady(s, uploading, onUpload = { filePicker.launch("*/*") }, onOpen = openFile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesReady(s: FilesUiState.Ready, uploading: Boolean, onUpload: () -> Unit, onOpen: (ProjectFile) -> Unit) {
    val today = remember { LocalDate.now() }
    val chamber = s.files.filterNot { it.fromClient }
    val mine = s.files.filter { it.fromClient }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (s.canUpload) item("upz") { UploadDropzone(uploading, onUpload) }
        if (s.files.isEmpty()) {
            item("empty") { FilesEmpty() }
        } else {
            if (chamber.isNotEmpty()) {
                item("h1") { Slabel("Shared by your chamber", chamber.size) }
                item("c1") { FileCard(chamber, today, onOpen) }
            }
            if (mine.isNotEmpty()) {
                item("h2") { Slabel("Uploaded by you", mine.size) }
                item("c2") { FileCard(mine, today, onOpen) }
            }
        }
    }
}

@Composable
private fun UploadDropzone(uploading: Boolean, onUpload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(13.dp)).background(LlColor.Paper1)
            .border(LlDimen.HairlineWidth, LlColor.BorderStrong, RoundedCornerShape(13.dp)).clickable(enabled = !uploading, onClick = onUpload).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LlColor.InkMuted)
            else Icon(Icons.Outlined.UploadFile, null, tint = LlColor.InkMuted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(if (uploading) "Uploading…" else "Upload a file", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
            Text(if (uploading) "Please wait" else "PDF, image or document", fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun FileCard(files: List<ProjectFile>, today: LocalDate, onOpen: (ProjectFile) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)) {
        files.forEachIndexed { i, f ->
            val kind = fileKind(f)
            val recent = !f.fromClient && f.dateAdded?.toLocalDate()?.let { ChronoUnit.DAYS.between(it, today) <= 3 } == true
            Row(Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width = 38.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(kind.bg).border(LlDimen.HairlineWidth, kind.border, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(kind.icon, null, tint = kind.fg, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(f.originalFileName ?: f.fileName, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 17.sp, color = LlColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (recent) Text("New", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = ClaretMidFix)
                        if (recent) Text("·", fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.BorderStrong)
                        Text(listOfNotNull(f.authorName ?: if (f.fromClient) "You" else null, f.dateAdded?.toLocalDate()?.format(dayFmt)).joinToString("  ·  "), fontFamily = SourceSans, fontSize = 10.sp, color = LlColor.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(kind.bg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(kind.badge, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.08.em, color = kind.fg)
                }
                Icon(Icons.Outlined.OpenInNew, null, tint = LlColor.InkFaint, modifier = Modifier.size(18.dp))
            }
            if (i < files.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        }
    }
}

private data class Kind(val bg: Color, val fg: Color, val border: Color, val icon: ImageVector, val badge: String)

private fun fileKind(f: ProjectFile): Kind {
    val s = ((f.fileType ?: "") + " " + (f.originalFileName ?: f.fileName)).lowercase()
    return when {
        s.contains("pdf") -> Kind(ClaretSoftFix, ClaretMidFix, ClaretBorderFix, Icons.Outlined.Description, "PDF")
        Regex("image|jpg|jpeg|png|gif|webp|svg|heic").containsMatchIn(s) -> Kind(LlColor.SlateSoft, LlColor.SlateMid, Color(0xFFD6E3F3), Icons.Outlined.Image, "IMG")
        Regex("zip|rar|7z|tar|gz").containsMatchIn(s) -> Kind(LlColor.Paper2, LlColor.InkMuted, LlColor.BorderStrong, Icons.Outlined.FolderZip, "ZIP")
        Regex("doc|word|rtf|txt|odt|pages").containsMatchIn(s) -> Kind(LlColor.AmberSoft, LlColor.Amber, Color(0xFFF3E3C4), Icons.Outlined.Article, "DOC")
        else -> Kind(LlColor.Paper2, LlColor.InkMuted, LlColor.BorderStrong, Icons.Outlined.Description, "FILE")
    }
}

// --- shared bits ----------------------------------------------------------

@Composable
private fun FilesSubBar(count: Int?, contextLine: String?, canUpload: Boolean, onBack: () -> Unit, onUpload: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LlColor.Paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Files", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.015).em, color = LlColor.Ink)
            count?.let {
                Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(LlColor.Paper2).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text("$it", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = LlColor.InkMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            if (canUpload) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(LlColor.Ink).clickable(onClick = onUpload).padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Upload, null, tint = LlColor.Paper, modifier = Modifier.size(16.dp))
                    Text("Upload", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = LlColor.Paper)
                }
            }
        }
        contextLine?.let { Text(it, fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.04.em, color = LlColor.InkFaint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
    }
}

@Composable
private fun Slabel(name: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name.uppercase(), fontFamily = SourceSans, fontSize = 10.sp, letterSpacing = 0.15.em, color = LlColor.InkFaint)
        Box(Modifier.heightIn(min = 16.dp).widthIn(min = 16.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = LlColor.InkMuted)
        }
        Box(Modifier.weight(1f).height(1.dp).background(LlColor.Paper2))
    }
}

@Composable
private fun FilesEmpty() {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 42.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.SlateSoft).border(LlDimen.HairlineWidth, Color(0xFFD6E3F3), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FolderOpen, null, tint = LlColor.Slate, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("No files yet", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text("Documents your chamber shares — drafts, certificates, opinions — will appear here, alongside anything you upload.", fontFamily = SourceSans, fontSize = 12.5.sp, lineHeight = 20.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 282.dp))
    }
}

@Composable
private fun FilesSkeleton() {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.BorderStrong, RoundedCornerShape(13.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkF(Modifier.size(38.dp), 38.dp, 11.dp); Column(Modifier.weight(1f)) { SkF(Modifier.fillMaxWidth(0.46f), 13.dp); Spacer(Modifier.height(7.dp)); SkF(Modifier.fillMaxWidth(0.66f), 10.dp) }
        }
        Spacer(Modifier.height(18.dp))
        SkF(Modifier.padding(horizontal = 16.dp).width(140.dp), 11.dp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(LlShape.Card).background(LlColor.Surface).border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card)) {
            repeat(3) { i ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkF(Modifier.size(width = 38.dp, height = 46.dp), 46.dp, 8.dp); Column(Modifier.weight(1f)) { SkF(Modifier.fillMaxWidth(0.7f), 13.dp); Spacer(Modifier.height(8.dp)); SkF(Modifier.fillMaxWidth(0.5f), 10.dp) }
                }
            }
        }
    }
}

@Composable
private fun SkF(modifier: Modifier, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(radius)).background(LlColor.Paper2))
}

@Composable
private fun FilesError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LlColor.Paper).padding(horizontal = 30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(LlColor.Paper1).border(LlDimen.HairlineWidth, LlColor.Border, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, null, tint = LlColor.InkMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Couldn't load files", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em, color = LlColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(message, fontFamily = SourceSans, fontSize = 13.sp, lineHeight = 21.sp, color = LlColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Ink).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Refresh, null, tint = LlColor.Paper, modifier = Modifier.size(18.dp))
            Text("TRY AGAIN", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.05.em, color = LlColor.Paper)
        }
    }
}

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun openProjFile(context: android.content.Context, file: java.io.File, mime: String?) {
    val authority = "${context.packageName}.fileprovider"
    val uri = try { FileProvider.getUriForFile(context, authority, file) } catch (e: Exception) { Toast.makeText(context, "Couldn't open file: ${e.message}", Toast.LENGTH_LONG).show(); return }
    val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime ?: "application/octet-stream"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try { context.startActivity(Intent.createChooser(intent, "Open document").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    catch (e: Exception) { Toast.makeText(context, "No app installed to open this file type.", Toast.LENGTH_LONG).show() }
}
