package com.legumlex.client.feature.documents

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val vm: VaultWizardViewModel = viewModel()
    val step by vm.step.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is DownloadEvent.Ready  -> openWithViewer(context, ev.file, ev.doc.mimeType)
                is DownloadEvent.Failed -> Toast.makeText(context, ev.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Wizard back: pop a step if there's history, else exit Vault.
    BackHandler { if (!vm.back()) onBack() }

    val title = when (step) {
        is VaultStep.Welcome         -> "Vault"
        is VaultStep.PickerLoading   -> (step as VaultStep.PickerLoading).intent.title
        is VaultStep.PickerError     -> (step as VaultStep.PickerError).intent.title
        is VaultStep.Picker          -> (step as VaultStep.Picker).intent.title
        is VaultStep.SearchInput     -> "Search documents"
        is VaultStep.ResultsLoading  -> (step as VaultStep.ResultsLoading).context.title
        is VaultStep.Results         -> (step as VaultStep.Results).context.title
        is VaultStep.ResultsError    -> (step as VaultStep.ResultsError).context.title
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The app-wide header already consumes the status-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // In-content header — the app-wide bar shows only the brand mark, so
            // the wizard names its current step here.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 6.dp),
            ) {
                EyebrowText("Documents", color = TertiaryText)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = PrimaryText, maxLines = 1)
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 6 })
                },
                label = "vault-step",
            ) { current ->
                when (current) {
                    is VaultStep.Welcome        -> WelcomeStep(onPick = vm::chooseIntent)
                    is VaultStep.PickerLoading  -> StepCenteredSpinner("Loading your ${current.intent.title.lowercase()}…")
                    is VaultStep.PickerError    -> StepError(current.message) { vm.retryPicker(current.intent) }
                    is VaultStep.Picker         -> PickerStep(current) { item -> vm.choosePickerItem(current.intent, item) }
                    is VaultStep.SearchInput    -> SearchStep(initial = current.query, onSubmit = vm::submitSearch)
                    is VaultStep.ResultsLoading -> StepCenteredSpinner("Fetching documents…")
                    is VaultStep.ResultsError   -> StepError(current.message) { vm.retryResults(current.context) }
                    is VaultStep.Results        -> ResultsStep(
                        current,
                        downloading = downloading,
                        onOpen      = vm::openDocument,
                        onPickAnother = vm::goHome,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Step 1 — Welcome (the wizard's home)
// =============================================================================

@Composable
private fun WelcomeStep(onPick: (VaultIntent) -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s3, bottom = spacing.s8),
    ) {
        Text(
            "What are you looking for?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
        )
        Spacer(Modifier.height(spacing.s2))
        Text(
            "Pick the closest match and we'll narrow it down together.",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )
        Spacer(Modifier.height(spacing.s5))

        IntentTile(
            icon  = Icons.Outlined.Folder,
            title = VaultIntent.CASE_FILE.title,
            sub   = VaultIntent.CASE_FILE.sub,
        ) { onPick(VaultIntent.CASE_FILE) }
        TileSpacer()
        IntentTile(
            icon  = Icons.Outlined.Gavel,
            title = VaultIntent.HEARING_DOC.title,
            sub   = VaultIntent.HEARING_DOC.sub,
        ) { onPick(VaultIntent.HEARING_DOC) }
        TileSpacer()
        IntentTile(
            icon  = Icons.AutoMirrored.Outlined.Send,
            title = VaultIntent.LEGAL_NOTICE_DOC.title,
            sub   = VaultIntent.LEGAL_NOTICE_DOC.sub,
        ) { onPick(VaultIntent.LEGAL_NOTICE_DOC) }
        TileSpacer()
        IntentTile(
            icon  = Icons.Outlined.Forum,
            title = VaultIntent.SESSION_DOC.title,
            sub   = VaultIntent.SESSION_DOC.sub,
        ) { onPick(VaultIntent.SESSION_DOC) }
        TileSpacer()
        IntentTile(
            icon  = Icons.Outlined.Handshake,
            title = VaultIntent.AGREEMENT_DOC.title,
            sub   = VaultIntent.AGREEMENT_DOC.sub,
        ) { onPick(VaultIntent.AGREEMENT_DOC) }
        TileSpacer()
        IntentTile(
            icon  = Icons.Outlined.Share,
            title = VaultIntent.SHARED_WITH_ME.title,
            sub   = VaultIntent.SHARED_WITH_ME.sub,
        ) { onPick(VaultIntent.SHARED_WITH_ME) }

        Spacer(Modifier.height(spacing.s6))
        HorizontalDivider(color = BorderSubtle)
        Spacer(Modifier.height(spacing.s4))

        IntentTile(
            icon  = Icons.Outlined.Search,
            title = VaultIntent.SEARCH_BY_NAME.title,
            sub   = VaultIntent.SEARCH_BY_NAME.sub,
            secondary = true,
        ) { onPick(VaultIntent.SEARCH_BY_NAME) }
    }
}

@Composable
private fun TileSpacer() = Spacer(Modifier.height(10.dp))

@Composable
private fun IntentTile(
    icon: ImageVector,
    title: String,
    sub: String,
    secondary: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        shape   = RoundedCornerShape(16.dp),
        border  = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (secondary) Surface2 else MaterialTheme.colorScheme.surface)
                    .then(
                        if (secondary) Modifier
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = PrimaryText, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
                Spacer(Modifier.height(2.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = TertiaryText, modifier = Modifier.size(20.dp))
        }
    }
}

// =============================================================================
// Step 2 — Picker
// =============================================================================

@Composable
private fun PickerStep(step: VaultStep.Picker, onPick: (PickerItem) -> Unit) {
    val spacing = LocalSpacing.current
    val (header, subhead) = when (step.intent) {
        VaultIntent.CASE_FILE   -> "Which case?"           to "Pick the matter to pull documents from."
        VaultIntent.HEARING_DOC -> "Which hearing?"        to "Pick a date to fetch its documents."
        VaultIntent.SESSION_DOC -> "Which session?"        to "Pick a consultation to fetch its files."
        else                    -> step.intent.title       to step.intent.sub
    }

    if (step.items.isEmpty()) {
        StepEmpty(header = "Nothing to pick", body = "There aren't any ${step.intent.title.lowercase()} on your account yet.")
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s4)) {
        Spacer(Modifier.height(spacing.s3))
        Text(header, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
        Spacer(Modifier.height(2.dp))
        Text(subhead, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        Spacer(Modifier.height(spacing.s4))

        LazyColumn(
            contentPadding = PaddingValues(bottom = spacing.s8),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(step.items, key = { it.id }) { item ->
                Surface(
                    onClick = { onPick(item) },
                    color   = MaterialTheme.colorScheme.surface,
                    shape   = RoundedCornerShape(14.dp),
                    border  = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText, maxLines = 2)
                            item.sub?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.ChevronRight, null, tint = TertiaryText, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// =============================================================================
// Step 2 — Search input
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchStep(initial: String, onSubmit: (String) -> Unit) {
    val spacing = LocalSpacing.current
    var query by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s4)) {
        Spacer(Modifier.height(spacing.s3))
        Text(
            "Type a name or keyword",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "We'll look across file names and tags — across every category.",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )
        Spacer(Modifier.height(spacing.s4))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            placeholder = { Text("e.g. Vermicompost, Order, PAN…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BorderColor,
                unfocusedBorderColor = BorderColor,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = PrimaryText,
            ),
        )

        Spacer(Modifier.height(spacing.s4))

        LegumPrimaryButton(
            text    = "Search",
            onClick = { onSubmit(query) },
            enabled = query.trim().isNotBlank(),
        )
    }
}

// =============================================================================
// Step 3 — Results
// =============================================================================

@Composable
private fun ResultsStep(
    step: VaultStep.Results,
    downloading: Set<String>,
    onOpen: (Document) -> Unit,
    onPickAnother: () -> Unit,
) {
    val spacing = LocalSpacing.current
    if (step.documents.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s5).padding(top = spacing.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null, tint = TertiaryText, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(spacing.s4))
            Text("No documents here yet", style = MaterialTheme.typography.headlineSmall, color = PrimaryText)
            Spacer(Modifier.height(spacing.s2))
            Text(
                "Nothing's been attached to this yet. Try a different choice or check again later.",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s5))
            LegumSecondaryButton(
                text    = "Pick something else",
                onClick = onPickAnother,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start  = spacing.s4,
            end    = spacing.s4,
            top    = spacing.s3,
            bottom = spacing.s8,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.s2),
    ) {
        item("count") {
            Text(
                "${step.documents.size} ${if (step.documents.size == 1) "document" else "documents"}",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
            )
            Spacer(Modifier.height(spacing.s2))
        }
        items(step.documents, key = { it.id }) { d ->
            DocumentCard(d, isDownloading = d.id in downloading) { onOpen(d) }
        }
    }
}

@Composable
private fun DocumentCard(doc: Document, isDownloading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface,
        shape   = RoundedCornerShape(14.dp),
        border  = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(doc.iconForType(), null, tint = PrimaryText, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        doc.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PrimaryText,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(doc.sourceLabel.takeIf { it.isNotBlank() }, doc.sourceContext)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryText,
                    )
                }
            }
            if (doc.tag != null || doc.uploadedBy != null || doc.fileExtension != null || doc.uploadedAt != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    doc.tag?.let {
                        TagPill(it); Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = buildString {
                            doc.fileExtension?.let { append(it.uppercase()) }
                            if (doc.uploadedBy != null) {
                                if (isNotEmpty()) append(" · ")
                                append("by ${doc.uploadedBy}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TertiaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    doc.uploadedAt?.let {
                        Text(
                            relativeAgo(it),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 11.sp),
                            color = TertiaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(label: String) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = SecondaryText,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// =============================================================================
// Step-level utility states (loading/error/empty)
// =============================================================================

@Composable
private fun StepCenteredSpinner(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, color = PrimaryText, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    }
}

@Composable
private fun StepError(message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(StatusDanger.softBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = StatusDanger.solid, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.s4))
        Text("Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = PrimaryText,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s2))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s5))
        LegumSecondaryButton(
            text        = "Try again",
            leadingIcon = Icons.Outlined.Refresh,
            onClick     = onRetry,
            modifier    = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun StepEmpty(header: String, body: String) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = spacing.s5).padding(top = spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null, tint = TertiaryText, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.s4))
        Text(header, style = MaterialTheme.typography.headlineSmall, color = PrimaryText)
        Spacer(Modifier.height(spacing.s2))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
    }
}

// =============================================================================
// Open with viewer
// =============================================================================

private fun openWithViewer(context: android.content.Context, file: File, mimeType: String?) {
    val authority = "${context.packageName}.fileprovider"
    val uri: Uri = try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open file: ${e.message}", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open document").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Toast.makeText(context, "No app installed to open this file type.", Toast.LENGTH_LONG).show()
    }
}

internal fun relativeAgo(ts: LocalDateTime): String {
    val now = LocalDateTime.now()
    val mins = ChronoUnit.MINUTES.between(ts, now)
    return when {
        mins < 1               -> "just now"
        mins < 60              -> "${mins}m ago"
        mins < 60 * 24         -> "${mins / 60}h ago"
        mins < 60 * 24 * 7     -> "${mins / (60 * 24)}d ago"
        mins < 60 * 24 * 30    -> "${mins / (60 * 24 * 7)}w ago"
        else                   -> ts.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
