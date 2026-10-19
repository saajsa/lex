package com.legumlex.client.feature.consultations.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legumlex.client.feature.consultations.ConsultationAttachment
import com.legumlex.client.feature.consultations.ConsultationDetailUiState
import com.legumlex.client.feature.consultations.ConsultationDetailViewModel
import com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*

/**
 * Shared attachments section embedded into each kind-specific consultation
 * detail screen. Renders the list (staff-shared + own uploads, newest first),
 * a busy state while loading, an Upload / Scan affordance, and a per-row
 * spinner while a download is in flight.
 *
 * The screen owns the picker / scanner launchers; the ViewModel owns the
 * upload + open actions. Failures surface through the parent's snackbar
 * host via the existing ReplyEvent.Failed channel.
 */
@Composable
fun AttachmentsCard(
    attachments: List<ConsultationAttachment>,
    loading: Boolean,
    uploading: Boolean,
    downloadingIds: Set<String>,
    deletingIds: Set<String> = emptySet(),
    onUpload: (Uri) -> Unit,
    onOpen: (ConsultationAttachment) -> Unit,
    onDelete: (ConsultationAttachment) -> Unit = {},
    /**
     * Terminal-state guard. When true, the Scan/Upload actions and the
     * empty-state "drop files here" CTA are hidden — on a closed consultation
     * the client can't add anything, so surfacing those affordances is
     * misleading. Callers pass true once `consultation.closedAt != null`.
     */
    readOnly: Boolean = false,
) {
    val spacing = LocalSpacing.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) onUpload(uri) }
    val launchScanner = rememberDocumentScannerLauncher { pdfUri: Uri? ->
        if (pdfUri != null) onUpload(pdfUri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EyebrowText(
                text  = if (attachments.isEmpty()) "Attachments" else "Attachments (${attachments.size})",
                color = TertiaryText,
            )
            if (uploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = StatusOngoing.solid,
                    )
                    Text(
                        "Uploading…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TertiaryText,
                    )
                }
            }
        }

        Card(
            colors  = CardDefaults.cardColors(containerColor = Surface1),
            shape   = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                when {
                    loading && attachments.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(Surface1),
                        )
                    }
                    attachments.isEmpty() -> EmptyAttachmentsRow(spacing, readOnly = readOnly)
                    else -> {
                        attachments.forEachIndexed { idx, att ->
                            AttachmentRow(
                                att = att,
                                downloading = att.id in downloadingIds,
                                deleting    = att.id in deletingIds,
                                onClick     = { onOpen(att) },
                                onLongClick = if (att.fromClient) ({ onDelete(att) }) else null,
                                spacing = spacing,
                            )
                            if (idx < attachments.lastIndex) {
                                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Compose actions strip — hidden on closed consultations where
                // the client can't add anything new.
                if (!readOnly) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.s3, vertical = spacing.s2),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AttachmentAction(
                            label = "Scan",
                            icon  = Icons.Outlined.PhotoCamera,
                            enabled = !uploading,
                            onClick = { launchScanner() },
                        )
                        AttachmentAction(
                            label = "Upload",
                            icon  = Icons.Outlined.UploadFile,
                            enabled = !uploading,
                            onClick = { picker.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AttachmentRow(
    att: ConsultationAttachment,
    downloading: Boolean,
    deleting: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    spacing: Spacing,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled       = !downloading && !deleting,
                onClick       = onClick,
                onLongClick   = onLongClick,
            )
            .padding(spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (att.fromClient) StatusOngoing.softBg else Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = iconFor(att.fileType),
                contentDescription = null,
                tint               = if (att.fromClient) StatusOngoing.solid else TertiaryText,
                modifier           = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = att.displayName,
                style      = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color      = PrimaryText,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(att.uploadedBy)
                if (!att.tag.isNullOrBlank()) {
                    append(" · "); append(att.tag)
                }
            }
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TertiaryText,
            )
        }
        when {
            deleting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = TertiaryText,
            )
            downloading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = StatusOngoing.solid,
            )
            else -> Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = TertiaryText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyAttachmentsRow(spacing: Spacing, readOnly: Boolean = false) {
    Column(
        modifier              = Modifier.padding(spacing.s5).fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier         = Modifier.size(40.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AttachFile,
                null,
                tint = TertiaryText,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(spacing.s2))
        Text(
            text = if (readOnly) "No supporting documents" else "No attachments yet",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (readOnly) "Nothing was attached to this consultation."
                   else "Scan or upload documents to share with your advocate.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AttachmentAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = StatusOngoing.solid,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = StatusOngoing.solid,
        )
    }
}

/**
 * Convenience overload — pulls everything from the ViewModel. Detail screens
 * call this so wiring (busy flags, upload, open) stays in one place. Silently
 * renders nothing if the VM hasn't reached Ready yet (parent screen owns the
 * loading state).
 */
@Composable
fun AttachmentsCardForVM(vm: ConsultationDetailViewModel) {
    val state by vm.state.collectAsState()
    val ready = state as? ConsultationDetailUiState.Ready ?: return

    var confirmDelete by remember { mutableStateOf<ConsultationAttachment?>(null) }

    AttachmentsCard(
        attachments    = ready.attachments,
        loading        = ready.attachmentsLoading,
        uploading      = ready.attachmentUploading,
        downloadingIds = ready.downloadingAttachmentIds,
        deletingIds    = ready.deletingAttachmentIds,
        onUpload       = vm::uploadAttachment,
        onOpen         = vm::openAttachment,
        onDelete       = { att -> confirmDelete = att },
        // Closed consultations are terminal — hide Scan/Upload + soften
        // the empty-state copy so we don't invite uploads that the
        // backend would also reject.
        readOnly       = ready.consultation.closedAt != null,
    )

    val target = confirmDelete
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title  = { Text("Remove file?") },
            text   = {
                Text("“${target.displayName}” will be deleted from this consultation. " +
                     "You can re-upload it any time.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = null
                    vm.deleteAttachment(target)
                }) {
                    Text(
                        "Remove",
                        color = com.legumlex.client.ui.theme.StatusDanger.solid,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    Text("Keep")
                }
            },
        )
    }
}

/** Coarse MIME → icon mapping. Falls back to a generic doc icon. */
private fun iconFor(mime: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    mime.startsWith("image/")             -> Icons.Outlined.Image
    mime == "application/pdf"             -> Icons.Outlined.PictureAsPdf
    mime.startsWith("audio/")             -> Icons.Outlined.Mic
    mime.startsWith("video/")             -> Icons.Outlined.Videocam
    else                                  -> Icons.Outlined.InsertDriveFile
}
