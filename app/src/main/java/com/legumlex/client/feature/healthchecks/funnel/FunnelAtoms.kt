package com.legumlex.client.feature.healthchecks.funnel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.Inter

// ─────────────────────────────────────────────────────────────────────────────
// Primary / secondary funnel action button (replicates CLMBtn from the design)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FunnelPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        color = if (enabled) PrimaryText else PrimaryText.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = InverseText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = InverseText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
fun FunnelSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Text field (with eyebrow label)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AhcField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    minHeight: Dp = if (multiline) 90.dp else 52.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val hasValue = value.isNotBlank()
    Column(modifier = modifier) {
        EyebrowText(label)
        Spacer(Modifier.height(6.dp))
        Surface(
            color = Surface1,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (hasValue) BorderStrong else BorderColor),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = !multiline,
                cursorBrush = SolidColor(PrimaryText),
                textStyle = TextStyle(
                    color = PrimaryText,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = TertiaryText,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contract type tile (3×2 grid item)
// ─────────────────────────────────────────────────────────────────────────────

fun contractTypeIcon(t: AhcContractType): ImageVector = when (t) {
    AhcContractType.SAAS       -> Icons.Outlined.Cloud
    AhcContractType.LEASE      -> Icons.Outlined.HomeWork
    AhcContractType.EMPLOYMENT -> Icons.Outlined.Work
    AhcContractType.NDA        -> Icons.Outlined.Lock
    AhcContractType.CONSULTING -> Icons.Outlined.Handshake
    AhcContractType.OTHER      -> Icons.Outlined.MoreHoriz
}

@Composable
fun ContractTypeTile(
    type: AhcContractType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) PrimaryText else Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) PrimaryText else BorderColor),
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(7.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Surface1)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(10.dp))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    contractTypeIcon(type),
                    contentDescription = null,
                    tint = if (selected) InverseText else PrimaryText,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    type.label,
                    color = if (selected) InverseText else PrimaryText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Upload affordances
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DropTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, BorderStrong),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(140.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Surface1),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tap to pick a file",
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "PDF, DOCX, or scan — up to 25 MB",
                color = SecondaryText,
                fontSize = 12.5.sp,
            )
        }
    }
}

/** Server-stored file row (post-upload). */
@Composable
fun ServerFileRow(
    doc: AhcDocument,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) = FileRow(
    name = doc.name,
    subtitle = humanSize(doc.sizeBytes),
    isError = false,
    isUploading = false,
    onRemove = onRemove,
    modifier = modifier,
)

/** Local-only pending row (uploading / failed / offline). */
@Composable
fun PendingFileRow(
    file: PendingFileUpload,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (file.state) {
        PendingUploadState.UPLOADING -> "Uploading… ${file.progress}%"
        PendingUploadState.FAILED    -> "Couldn't upload — tap to retry"
        PendingUploadState.OFFLINE   -> "PENDING UPLOAD"
    }
    FileRow(
        name = file.displayName,
        subtitle = subtitle,
        isError = file.state == PendingUploadState.FAILED,
        isUploading = file.state == PendingUploadState.UPLOADING,
        progressPct = file.progress,
        onRemove = onRemove,
        modifier = modifier,
    )
}

@Composable
private fun FileRow(
    name: String,
    subtitle: String,
    isError: Boolean,
    isUploading: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    progressPct: Int = 0,
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isError) StatusDanger.softBg else BorderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isError) StatusDanger.softBg else Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            isError -> Icons.Outlined.ErrorOutline
                            name.endsWith(".pdf", true) -> Icons.Outlined.PictureAsPdf
                            else -> Icons.Outlined.Description
                        },
                        contentDescription = null,
                        tint = if (isError) StatusDanger.solid else SecondaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        color = PrimaryText,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        subtitle,
                        color = if (isError) StatusDanger.softText else SecondaryText,
                        fontFamily = Inter,
                        fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isError) 10.sp else 10.5.sp,
                        letterSpacing = if (isError) 1.0.sp else 0.2.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = SecondaryText, modifier = Modifier.size(17.dp))
                }
            }
            if (isUploading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (progressPct.coerceIn(0, 100)) / 100f },
                    color = PrimaryText,
                    trackColor = Surface2,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
fun AddAnotherDocumentRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Add Another Document",
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Save & exit confirm sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveAndExitSheet(
    onSaveExit: () -> Unit,
    onContinueEditing: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onContinueEditing,
        containerColor = Surface1,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp)) {
            Text(
                "Save your progress?",
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.25).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You can pick up where you left off from Agreement Checks → Drafts. Your uploads are already saved.",
                color = SecondaryText,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(20.dp))
            FunnelPrimaryButton(label = "Save & exit", onClick = onSaveExit)
            Spacer(Modifier.height(10.dp))
            FunnelSecondaryButton(label = "Continue Editing", onClick = onContinueEditing)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024        -> String.format("%d KB", bytes / 1024)
    else                 -> "$bytes B"
}
