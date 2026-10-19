package com.legumlex.client.feature.consultations.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.feature.consultations.Consultation
import com.legumlex.client.feature.consultations.ConsultationKind
import com.legumlex.client.feature.consultations.ConsultationLinkage
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.TertiaryText

// Screen 06 of the Agreement Check Workflow design: "Close consultation"
// confirmation sheet. Triggered from a consultation's 3-dot "Close" action.
// Copy adapts to the consultation's kind/state — we never show the same
// generic warning regardless of context (the server enforces invariants
// anyway, but front-loading them in copy avoids round-tripping for a 409).

/**
 * Bottom sheet that confirms a client-driven cancel.
 *
 * @param consultation       The row being closed.
 * @param linkage            Optional pre-fetched parent/child linkage. When a
 *                           linked child is still open the sheet shows that
 *                           in the body copy and disables the close action —
 *                           same invariant the server enforces with a 409.
 * @param onConfirm          Called with the optional reason string the user
 *                           typed. Caller posts to /cancel and dismisses.
 * @param onDismiss          Caller dismisses without writing.
 * @param submitting         True while the network call is in flight; the
 *                           confirm button shows a spinner and both actions
 *                           are blocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseConsultationSheet(
    consultation: Consultation,
    linkage: ConsultationLinkage? = null,
    onConfirm: (reason: String?) -> Unit,
    onDismiss: () -> Unit,
    submitting: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by remember { mutableStateOf("") }

    val copy = sheetCopyFor(consultation, linkage)

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
        sheetState       = sheetState,
        containerColor   = Surface1,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 26.dp, top = 4.dp)) {

            // ── Title ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text       = copy.title,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 19.sp,
                    letterSpacing = (-0.19).sp,
                    color      = PrimaryText,
                    modifier   = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, enabled = !submitting) {
                    Icon(Icons.Outlined.Close, "Close", tint = TertiaryText)
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── Body copy ────────────────────────────────────────────────
            Text(
                text  = copy.body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize   = 13.5.sp,
                    lineHeight = 20.sp,
                ),
                color = SecondaryText,
            )

            // ── Blocking message — invariant the server would reject ─────
            if (copy.blockingMessage != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusDanger.softBg)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text  = copy.blockingMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize   = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = StatusDanger.softText,
                    )
                }
            }

            // ── Reason field (skipped when blocked) ──────────────────────
            if (copy.blockingMessage == null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text       = "REASON (OPTIONAL)",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 10.sp,
                    letterSpacing = 1.2.sp,
                    color      = TertiaryText,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    placeholder   = { Text("Anything you'd like your advocate to know?") },
                    singleLine    = false,
                    minLines      = 2,
                    maxLines      = 4,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    enabled       = !submitting,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentInk,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor    = AccentInk,
                        cursorColor          = AccentInk,
                    ),
                )
                Text(
                    text       = "${reason.length} / 500",
                    fontFamily = Inter,
                    fontSize   = 10.sp,
                    color      = TertiaryText,
                    modifier   = Modifier.align(Alignment.End).padding(top = 5.dp),
                )
            }

            // ── Actions ──────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .clickable(enabled = !submitting, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "CANCEL",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 12.sp,
                        letterSpacing = 0.6.sp,
                        color      = PrimaryText,
                    )
                }
                val canConfirm = copy.blockingMessage == null && !submitting
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canConfirm) AccentInk else AccentInk.copy(alpha = 0.35f))
                        .clickable(enabled = canConfirm) {
                            onConfirm(reason.trim().ifBlank { null })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier   = Modifier.size(18.dp),
                            color      = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text       = copy.confirmLabel,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 12.sp,
                            letterSpacing = 0.6.sp,
                            color      = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ── Copy resolution per kind / state ───────────────────────────────────────

private data class SheetCopy(
    val title: String,
    val body: String,
    val confirmLabel: String,
    /** When non-null, the server would reject the close with a 409. Show the
     *  reason inline and disable the confirm button. */
    val blockingMessage: String? = null,
)

private fun sheetCopyFor(consultation: Consultation, linkage: ConsultationLinkage?): SheetCopy {
    // Litigation phase is the bluntest block — the consultation has graduated
    // and can't be cancelled from the app.
    if (consultation.phase.equals("litigation", ignoreCase = true)) {
        return SheetCopy(
            title           = "Can't cancel this",
            body            = "This consultation has been converted to a case. Contact your advocate if you'd like to discuss it further.",
            confirmLabel    = "CLOSE",
            blockingMessage = "Once on the case register, a consultation can't be cancelled from the app.",
        )
    }

    // AHC with an open linked child blocks too — mirrors the server's 409.
    val openChild = linkage?.linked?.takeIf { !it.isClosed }
    if (consultation.kind == ConsultationKind.AHC && openChild != null) {
        return SheetCopy(
            title           = "Close the linked consultation first",
            body            = "This Agreement Check is open because the consultation it was escalated to is still in progress.",
            confirmLabel    = "CLOSE",
            blockingMessage = "Open the linked consultation and close that one first — this one will close along with it.",
        )
    }

    // Kind-specific copy for the happy path.
    return when (consultation.kind) {
        ConsultationKind.AHC -> SheetCopy(
            title        = "Close this Agreement Check?",
            body         = "You'll keep access to anything that's already been released — the verdict, findings and the original document.",
            confirmLabel = "CLOSE",
        )
        ConsultationKind.LEGAL_NOTICE -> SheetCopy(
            title        = "Close this notice request?",
            body         = "Your advocate will be notified. If a notice has already been drafted, it will stay in your vault.",
            confirmLabel = "CLOSE",
        )
        ConsultationKind.GENERAL, ConsultationKind.LEGAL_ENQUIRY -> SheetCopy(
            title        = "Close this consultation?",
            body         = "Your advocate will be notified you've marked this complete. The conversation stays in your history.",
            confirmLabel = "CLOSE",
        )
        ConsultationKind.DOC_REQUEST -> SheetCopy(
            title        = "Close this request?",
            body         = "Your advocate will be notified. Any documents you've already uploaded stay attached.",
            confirmLabel = "CLOSE",
        )
        ConsultationKind.DIGITAL_OFFICE -> SheetCopy(
            title        = "Close this session?",
            body         = "Your advocate will be notified. The session record stays in your history.",
            confirmLabel = "CLOSE",
        )
    }
}
