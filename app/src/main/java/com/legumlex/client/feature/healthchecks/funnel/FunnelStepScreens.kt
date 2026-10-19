package com.legumlex.client.feature.healthchecks.funnel

import com.legumlex.client.ahc.theme.LlColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.components.RiskBadge
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.Inter

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 · Upload  (document-first entry point)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StepUploadScreen(
    state: AhcFunnelViewModel.State,
    vm: AhcFunnelViewModel,
    onBack: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    val session = state.session
    val docs = session?.documents.orEmpty()
    val pending = state.pending
    val canAdd = (docs.size + pending.size) < 5

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.pickFile(it) } }

    FunnelScreen(
        step = AhcStep.UPLOAD,
        onBack = onBack,
        onSaveAndExit = onSaveAndExit,
        action = {
            FunnelPrimaryButton(
                label = "Analyse",
                onClick = vm::advanceFromUpload,
                enabled = docs.isNotEmpty() && pending.none { it.state == PendingUploadState.UPLOADING } && !state.saving,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            FunnelStepHeader(
                step = AhcStep.UPLOAD,
                question = "Upload the agreement",
                sub = "We'll read it and pre-fill the details. PDF, DOCX, JPG or PNG, up to 25 MB.",
            )

            if (docs.isEmpty() && pending.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    DropTile(onClick = { picker.launch("*/*") })
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    docs.forEach { d ->
                        ServerFileRow(doc = d, onRemove = { vm.removeServerFile(d.id) })
                    }
                    pending.forEach { p ->
                        PendingFileRow(file = p, onRemove = { vm.removePending(p.localId) })
                    }
                    if (canAdd) {
                        Spacer(Modifier.height(4.dp))
                        AddAnotherDocumentRow(onClick = { picker.launch("*/*") })
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 · Extracting  (Gemini runs; client polls /status)
// ─────────────────────────────────────────────────────────────────────────────

private val EXTRACT_LABELS = listOf(
    "Reading text…",
    "Identifying parties…",
    "Detecting contract type…",
    "Scoring risk…",
)

@Composable
fun StepExtractingScreen(
    state: AhcFunnelViewModel.State,
    vm: AhcFunnelViewModel,
    onBack: () -> Unit,
    onSaveAndExit: () -> Unit,
    onRunInBackground: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.pollExtracting() }

    FunnelScreen(
        step = AhcStep.EXTRACTING,
        onBack = onBack,
        onSaveAndExit = onSaveAndExit,
        action = null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                PulsingRing(delayMillis = 0)
                PulsingRing(delayMillis = 800)
                PulsingRing(delayMillis = 1600)
                Box(
                    modifier = Modifier
                        .size(64.dp).clip(CircleShape).background(Surface2)
                        .border(1.dp, BorderColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Reading your agreement",
                color = PrimaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "We're identifying the parties, the type, and a one-line summary so you can confirm in a tap.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(20.dp))
            Surface(color = Surface2, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, BorderColor)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "${state.processingSubStep} / ${EXTRACT_LABELS.size} ·",
                        color = SecondaryText, fontFamily = Inter, fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        EXTRACT_LABELS[(state.processingSubStep - 1).coerceIn(0, EXTRACT_LABELS.lastIndex)],
                        color = PrimaryText, fontFamily = Inter, fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Run in background",
                color = SecondaryText,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRunInBackground)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PulsingRing(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1.0f, targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = delayMillis),
            repeatMode = RepeatMode.Restart,
        ), label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.30f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = delayMillis),
            repeatMode = RepeatMode.Restart,
        ), label = "alpha",
    )
    Box(
        modifier = Modifier.size(96.dp).scale(scale).alpha(alpha)
            .border(1.5.dp, PrimaryText, CircleShape),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 · Confirm  (pre-filled from initial reading; user confirms or corrects)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StepConfirmScreen(
    state: AhcFunnelViewModel.State,
    vm: AhcFunnelViewModel,
    onBack: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    val extraction = state.session?.extraction
    val draft = state.confirmDraft

    FunnelScreen(
        step = AhcStep.CONFIRM,
        onBack = onBack,
        onSaveAndExit = onSaveAndExit,
        action = {
            FunnelPrimaryButton(
                label = "Confirm & continue",
                onClick = vm::submitConfirm,
                enabled = draft.isComplete && !state.saving,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            val question = if (extraction?.ok == true) "Looks right?" else "Fill in the details"
            val sub = when {
                extraction == null -> null
                extraction.ok && extraction.isLowConfidence ->
                    "Our initial reading found these — double-check before continuing."
                extraction.ok -> "Pre-filled from an initial reading of your document. Edit anything that's off."
                else -> "We couldn't process your document automatically. Please fill in the details."
            }
            FunnelStepHeader(step = AhcStep.CONFIRM, question = question, sub = sub)

            // Preliminary analysis card (only when extraction succeeded)
            if (extraction?.ok == true && extraction.summary.isNotBlank()) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PreliminaryAnalysisCard(extraction)
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Contract type — 3×2 grid, pre-selected from extraction
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EyebrowText("Contract type")
                        if (extraction?.ok == true && extraction.contractType != null
                            && extraction.contractType == draft.contractType) {
                            Spacer(Modifier.width(8.dp))
                            PreFilledPill()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AhcContractType.values().toList().chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                ContractTypeTile(
                                    type = t,
                                    selected = draft.contractType == t,
                                    onClick = { vm.setConfirmType(t) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EyebrowText("Parties")
                        if (extraction?.ok == true && extraction.parties.isNotBlank()
                            && extraction.parties == draft.parties) {
                            Spacer(Modifier.width(8.dp))
                            PreFilledPill()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AhcField(
                        label = "",
                        value = draft.parties,
                        onValueChange = vm::setConfirmParties,
                        placeholder = "e.g. Acme Pvt Ltd ↔ your company",
                    )
                }

                AhcField(
                    label = "Anything specific you're worried about? (optional)",
                    value = draft.concern,
                    onValueChange = vm::setConfirmConcern,
                    placeholder = "Describe any specific concerns…",
                    multiline = true,
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PreliminaryAnalysisCard(extraction: AhcExtraction) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "PRELIMINARY ANALYSIS",
                    color = SecondaryText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.weight(1f))
                extraction.confidence?.let { c ->
                    Text(
                        "${(c * 100).toInt()}% confidence",
                        color = SecondaryText,
                        fontFamily = Inter,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                extraction.summary,
                color = PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            extraction.riskHint?.let { hint ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Initial risk hint",
                        color = SecondaryText,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    RiskBadge(level = hint)
                }
            }
        }
    }
}

@Composable
private fun PreFilledPill() {
    Surface(color = Surface2, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, BorderColor)) {
        Text(
            "PRE-FILLED",
            color = SecondaryText,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.0.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 · Payment — the site's fee (tblfunnel_modules), as an invoice paid
// on the website
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StepPaymentScreen(
    state: AhcFunnelViewModel.State,
    vm: AhcFunnelViewModel,
    onBack: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    val price = state.session?.pricePaise ?: 0
    // No fee: nothing to ask; the document goes to the advocate at once.
    LaunchedEffect(price) { if (price <= 0) vm.startPayment() }

    FunnelScreen(
        step = AhcStep.PAYMENT,
        onBack = onBack,
        onSaveAndExit = onSaveAndExit,
        action = if (price > 0) {
            {
                Column {
                    FunnelPrimaryButton(
                        label = when {
                            state.paying      -> "Opening the Invoice…"
                            state.invoiceOpen -> "Open the Invoice Again"
                            else              -> "Pay ${com.legumlex.client.feature.finance.formatMoney(price / 100.0, "₹").removeSuffix(".00")} on the Website"
                        },
                        onClick = { vm.startPayment() },
                        enabled = !state.paying,
                    )
                    if (state.invoiceOpen) {
                        Spacer(Modifier.height(8.dp))
                        FunnelSecondaryButton(label = "I Have Paid", onClick = { vm.checkPaid(asked = true) })
                    }
                }
            }
        } else null,
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
            FunnelStepHeader(step = AhcStep.PAYMENT, question = if (price > 0) "The Agreement Check Fee" else "Sending to Your Advocate")
            Spacer(Modifier.height(16.dp))
            if (price > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp), color = Lex.colors.card,
                    border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        EyebrowText("Agreement Check")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            com.legumlex.client.feature.finance.formatMoney(price / 100.0, "₹").removeSuffix(".00"),
                            color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "The fee covers an advocate's review of your agreement and the written report. It is an invoice from the firm, paid on the website; your document goes to the advocate once the payment is confirmed.",
                            color = SecondaryText, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "The invoice opens in your browser and is paid through Razorpay. You can save and pay later; your agreement stays here.",
                    color = TertiaryText, style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("There is no fee for this check. Your agreement is going to the advocate.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.payError?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (price <= 0) {
                    Spacer(Modifier.height(10.dp))
                    FunnelSecondaryButton(label = "Try Again", onClick = { vm.startPayment() })
                }
            }
        }
    }
}

// Step 5 (Pending review) has no funnel screen: on reaching pending_review the
// funnel hands off to the live consultation detail (DetailAHC) — see
// AhcFunnelScreen. The old static StepPendingReviewScreen was removed with that
// change.

// ─────────────────────────────────────────────────────────────────────────────
// Edge state · Session expired (410)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EdgeExpiredScreen(
    onStartNew: () -> Unit,
    onBackToHealthChecks: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Surface1).padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "This draft has been archived",
            color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Health check drafts expire after 30 days of inactivity. Start a fresh submission — it only takes a minute.",
            color = SecondaryText, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(28.dp))
        FunnelPrimaryButton(label = "Start a new agreement check", onClick = onStartNew)
        Spacer(Modifier.height(10.dp))
        FunnelSecondaryButton(label = "Back to Agreement Checks", onClick = onBackToHealthChecks)
    }
}
