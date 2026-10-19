package com.legumlex.client.feature.consultations

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import com.legumlex.client.feature.digital_office.DoSessionStatus
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.feature.consultations.detail.ConsultationSpecContent
import com.legumlex.client.feature.consultations.detail.DetailAHC
import com.legumlex.client.feature.consultations.detail.DetailConverted
import com.legumlex.client.feature.consultations.detail.DetailDigital
import com.legumlex.client.feature.consultations.detail.DetailDocReq
import com.legumlex.client.feature.consultations.detail.DetailGeneral
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger

// Router: checks converted state first, then dispatches by kind.
// Per handoff: "Don't try to abstract a 'consultation detail template'."
// The spec sheet (screen #14) is hoisted here so it overlays any variant.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationDetailScreen(
    consultationId: String,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenConsultation: (String) -> Unit = {},
    onOpenAhcDocument: (String) -> Unit = {},
    onOpenDocFile: (rid: String, name: String?) -> Unit = { _, _ -> },
    onOpenVideoLobby: (consultationId: String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: ConsultationDetailViewModel = viewModel(
        key     = "cons-$consultationId",
        factory = viewModelFactory {
            initializer { ConsultationDetailViewModel(app = app, consultationId = consultationId) }
        },
    )
    val state by vm.state.collectAsState()
    var showSpec by remember { mutableStateOf(false) }
    // The AHC body's bottom "Done with this — close" button flips this; the
    // sheet renders at the screen level (below the layout switch) so it
    // stays mounted across body-content recompositions.
    var ahcCloseOpen by remember { mutableStateOf(false) }

    // Surface upload / reply failures as a transient snackbar. The detail
    // screen is the most common host for ReplyEvent.Failed (doc-request
    // upload errors land here even though the legacy ConversationThread
    // also listens for reply failures). Info events also surface here so
    // withdraw/reschedule/decline toasts land in one place.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is ReplyEvent.Failed -> snackbarHostState.showSnackbar(ev.message)
                is ReplyEvent.Info   -> snackbarHostState.showSnackbar(ev.message)
            }
        }
    }
    // Attachment opens — VM produces a cached File; we hand it to the
    // system viewer via FileProvider so it doesn't matter what app handles it.
    LaunchedEffect(vm) {
        vm.attachmentEvents.collect { ev ->
            if (ev is AttachmentEvent.Open) openAttachment(context, ev.file, ev.mime)
        }
    }
    // Lifecycle events — withdraw OR cancel both pop back; the snackbar from
    // ReplyEvent.Info is shown first so the user sees the confirmation
    // before the screen disappears.
    LaunchedEffect(vm) {
        vm.lifecycleEvents.collect { ev ->
            when (ev) {
                is DetailLifecycleEvent.Withdrew -> onBack()
                is DetailLifecycleEvent.Closed   -> onBack()
            }
        }
    }

    // File picker for AHC agreement upload — launched from the "Upload your
    // agreement" CTA inside DetailAHC when no document has been attached yet.
    val ahcFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.uploadAttachment(uri) }

    // Doc-request "I don't have this" sheet — hoisted here so it overlays
    // the DetailDocReq variant without that screen owning the state.
    var declineTarget by remember { mutableStateOf<DocRequestItem?>(null) }

    // Video sessions skip the detail when there's a live/approaching session:
    // once the session loads joinable, jump straight into the immersive lobby
    // and replace this detail in the back stack (so Back returns to the list).
    // Terminal/record states (completed, payment, intake, declined…) fall
    // through and render DetailDigital as the consultation record.
    var redirectedToLobby by remember(consultationId) { mutableStateOf(false) }

    when (val s = state) {
        is ConsultationDetailUiState.Loading -> ScaffoldedLoading(onBack)
        is ConsultationDetailUiState.Error   -> ScaffoldedError(s.message, onBack, vm::load)
        is ConsultationDetailUiState.Ready   -> {
            val c = s.consultation
            val openSpec = { showSpec = true }

            val lobbyJoinable = c.kind == ConsultationKind.DIGITAL_OFFICE && !c.isConverted() &&
                s.doSession?.status in setOf(
                    DoSessionStatus.CONFIRMED,
                    DoSessionStatus.IN_PROGRESS,
                    DoSessionStatus.AWAITING_APPROVAL,
                )
            LaunchedEffect(lobbyJoinable) {
                if (lobbyJoinable && !redirectedToLobby) {
                    redirectedToLobby = true
                    onOpenVideoLobby(c.id)
                }
            }

            when {
                c.isConverted() -> DetailConverted(
                    consultation = c,
                    onBack       = onBack,
                    onOpenCase   = onOpenCase,
                    onOpenSpec   = openSpec,
                )
                else -> {
                    val actionsMenu: @Composable () -> Unit = {
                        com.legumlex.client.feature.consultations.detail.ConsultationActionsMenu(vm)
                    }
                    when (c.kind) {
                    // Legal Notice requests render with the General layout for
                    // now — they're advocate-drafted notices that progress like
                    // any other consultation; a dedicated detail panel can
                    // replace this when the Notice surface gets specialised UI.
                    ConsultationKind.GENERAL,
                    ConsultationKind.LEGAL_NOTICE   -> DetailGeneral(
                        vm           = vm,
                        consultation = c,
                        onBack       = onBack,
                        onOpenCase   = onOpenCase,
                        onOpenThread = onOpenThread,
                        onOpenSpec   = openSpec,
                    )
                    ConsultationKind.AHC            -> DetailAHC(
                        consultation       = c,
                        ahcData            = s.ahcData,
                        ahcLoading         = s.ahcLoading,
                        hasAttachments     = s.attachments.isNotEmpty(),
                        ahcDocumentLoading = s.ahcDocumentLoading,
                        onDownloadDocument = vm::downloadAhcDocument,
                        onUploadAgreement  = { ahcFilePicker.launch(arrayOf("*/*")) },
                        onOpenInline       = { onOpenAhcDocument(c.id) },
                        onOpenLinkedConsultation = onOpenConsultation,
                        onMessageAdvocate  = { onOpenThread(c.id) },
                        onRequestClose     = { ahcCloseOpen = true },
                        attachmentsSlot    = { com.legumlex.client.feature.consultations.detail.AttachmentsCardForVM(vm) },
                        topBarActions      = actionsMenu,
                        onBack             = onBack,
                        onOpenSpec         = openSpec,
                    )
                    ConsultationKind.DOC_REQUEST    -> DetailDocReq(
                        consultation = c,
                        bundle       = s.docRequestBundle,
                        loading      = s.docRequestsLoading,
                        decliningIds = s.decliningDocRequestIds,
                        onDecline    = { item -> declineTarget = item },
                        onOpenFile   = { item ->
                            onOpenDocFile(item.id, item.fulfilledDisplayName ?: item.fulfilledFileName)
                        },
                        onRefresh    = { vm.refreshDocRequests() },
                        onMessageAdvocate = { onOpenThread(c.id) },
                        snackbarHostState = snackbarHostState,
                        onBack       = onBack,
                    )
                    // While the session resolves — or when it's joinable and the
                    // lobby redirect is about to fire — show a loader instead of
                    // flashing the detail record (skip-to-lobby).
                    ConsultationKind.DIGITAL_OFFICE -> if (s.doSessionLoading || lobbyJoinable) {
                        ScaffoldedLoading(onBack)
                    } else {
                        DetailDigital(
                            consultation = c,
                            doSession    = s.doSession,
                            loading      = s.doSessionLoading,
                            attachmentsSlot = { com.legumlex.client.feature.consultations.detail.AttachmentsCardForVM(vm) },
                            topBarActions = actionsMenu,
                            onEnterLobby = { onOpenVideoLobby(c.id) },
                            onBack       = onBack,
                            onOpenSpec   = openSpec,
                        )
                    }
                    }
                }
            }

            if (showSpec) {
                ModalBottomSheet(
                    onDismissRequest = { showSpec = false },
                    containerColor   = MaterialTheme.colorScheme.background,
                ) {
                    ConsultationSpecContent(c)
                }
            }

            val target = declineTarget
            if (target != null) {
                DeclineDocRequestDialog(
                    item     = target,
                    onSubmit = { reason ->
                        vm.declineDocRequest(target.id, reason)
                        declineTarget = null
                    },
                    onDismiss = { declineTarget = null },
                )
            }
        }
    }

    // AHC "Done with this — close" sheet — opened from the DetailAHC body's
    // bottom button via the ahcCloseOpen flag. Sits outside the layout switch
    // so it survives state-driven body recompositions. Same component the 3-dot
    // menu uses, with the same blocking-on-open-linked-child invariants.
    if (ahcCloseOpen) {
        val ready = state as? ConsultationDetailUiState.Ready
        if (ready != null) {
            com.legumlex.client.feature.consultations.components.CloseConsultationSheet(
                consultation = ready.consultation,
                linkage      = ready.linkage,
                submitting   = ready.actionInFlight,
                onConfirm    = { reason ->
                    ahcCloseOpen = false
                    vm.cancel(reason)
                },
                onDismiss    = { ahcCloseOpen = false },
            )
        }
    }
}

// "I don't have this" sheet (Plot Design 08e). Bottom sheet: what happens, a
// reason field, and a submit that stays disabled until a reason is typed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclineDocRequestDialog(
    item: DocRequestItem,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val canSubmit = reason.trim().isNotEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LlColor.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 22.dp)) {
            Text(
                "I don’t have this",
                fontFamily = Lora, fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp, letterSpacing = (-0.015).em, color = LlColor.Ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append("We’ll mark ")
                    withStyle(SpanStyle(color = LlColor.Ink, fontWeight = FontWeight.SemiBold)) {
                        append(item.documentName)
                    }
                    append(" as unavailable. Your advocate will see your reason and can waive it or suggest an alternative.")
                },
                fontFamily = SourceSans, fontSize = 12.5.sp, lineHeight = 19.sp, color = LlColor.InkMuted,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "REASON",
                fontFamily = SourceSans, fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp, letterSpacing = 0.07.em, color = LlColor.InkFaint,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(500) },
                placeholder = {
                    Text("lost, doesn’t exist, applied for…",
                        fontFamily = SourceSans, fontSize = 14.sp, color = LlColor.InkFaint)
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SourceSans, fontSize = 14.sp, color = LlColor.Ink),
                minLines = 3,
                shape = RoundedCornerShape(9.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LlColor.BorderStrong,
                    unfocusedBorderColor = LlColor.Border,
                    focusedContainerColor = LlColor.Surface,
                    unfocusedContainerColor = LlColor.Surface,
                    cursorColor = LlColor.Ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (canSubmit) LlColor.Ink else LlColor.Paper2)
                    .clickable(enabled = canSubmit) { onSubmit(reason) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = if (canSubmit) LlColor.Paper else LlColor.InkFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "MARK AS DON’T HAVE",
                    fontFamily = SourceSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, letterSpacing = 0.05.em,
                    color = if (canSubmit) LlColor.Paper else LlColor.InkFaint,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldedLoading(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldedError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = spacing.s5),
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
            Text("Couldn't load this consultation",
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
}

/** Fire the system viewer for a cached file. Same pattern as VaultScreen. */
private fun openAttachment(
    context: android.content.Context,
    file: java.io.File,
    mime: String?,
) {
    val authority = "${context.packageName}.fileprovider"
    val uri = try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open file: ${e.message}", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime ?: "application/octet-stream")
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

