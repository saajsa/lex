package com.legumlex.client.feature.digital_office

import android.app.Application
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.legumlex.client.ui.theme.Lex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.Inter

/**
 * The Digital Office request — a three-step wizard (matter, brief, review)
 * that sends the brief for the advocate's approval. Acceptance raises the
 * fee's invoice (paid on the website); the session then starts and the
 * client lands on the consultation. A request already in flight resumes
 * into Waiting; an unregistered client is sent to the website to register.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoIntakeScreen(
    advocateStaffId: Int?,
    onBack: () -> Unit,
    onSubmitted: (consultationId: String) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: DoIntakeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DoIntakeViewModel(app = app, initialAdvocateStaffId = advocateStaffId)
            }
        },
    )
    // Shared activity-scoped waitroom VM so a join here is immediately visible
    // to the home banner when we pop back.
    val waitroomVm = sharedDoWaitroomViewModel()
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }

    // Poll the waiting request only while this screen is on screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.onScreenStarted()
                androidx.lifecycle.Lifecycle.Event.ON_STOP  -> vm.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is DoIntakeEvent.Failed     -> snack.showSnackbar(ev.message)
                is DoIntakeEvent.Submitted  -> {
                    // Best case: server returned the consultation id → navigate.
                    // Server's contract: consultation creation is best-effort
                    // after the Meet link succeeds, so consultation_id may be
                    // null on rare races. We fall back to popping back so the
                    // client lands on Home with the session live.
                    ev.consultationId?.let(onSubmitted) ?: onBack()
                }
                is DoIntakeEvent.OpenUrl -> runCatching {
                    // The fee is an invoice, paid on its page on the website.
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ev.url)))
                }
                is DoIntakeEvent.OfferJoinQueue -> {
                    // Cabin is occupied — surface a snackbar with a Join action.
                    // On accept we route through the shared waitroom VM (which
                    // does an optimistic merge so the home banner appears
                    // immediately) and pop back to home.
                    val result = snack.showSnackbar(
                        message        = "${ev.advocateName} is in another session. Join their queue?",
                        actionLabel    = "Join Queue",
                        duration       = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        waitroomVm.join(ev.advocateStaffId)
                        onBack()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
        // The app-wide header already supplies the brand bar, back arrow, and
        // status-bar inset — this screen no longer draws its own top bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        when (val s = state) {
            is DoIntakeUiState.Loading   -> CenteredSpinner(inner)
            is DoIntakeUiState.Error     -> ErrorBlock(inner, s.message, onRetry = vm::retry)
            is DoIntakeUiState.Ready     -> ReadyContent(
                inner   = inner,
                state   = s,
                onSubmit = vm::submit,
            )
            // Approval-gate flow states (server migration 106) — see VM doc.
            is DoIntakeUiState.Waiting   -> WaitingContent(
                inner    = inner,
                state    = s,
                onCancel = vm::cancelWhileWaiting,
                onOpenInvoice = vm::openInvoice,
            )
            is DoIntakeUiState.Declined  -> DeclinedContent(inner, s, onBack = onBack)
            is DoIntakeUiState.Cancelled -> CancelledContent(inner, s, onBack = onBack)
            is DoIntakeUiState.NeedsRegistration -> RegistrationContent(
                inner      = inner,
                state      = s,
                onRegister = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.registerUrl?.takeIf { it.startsWith("http") } ?: portalOfficeUrl())))
                    }
                },
                onBack     = onBack,
            )
        }
    }
}

// ─── Approval-gate state screens ──────────────────────────────────────────

@Composable
private fun WaitingContent(
    inner: PaddingValues,
    state: DoIntakeUiState.Waiting,
    onCancel: () -> Unit,
    onOpenInvoice: () -> Unit,
) {
    // Accepted: the fee's invoice is the next step (paid on the website).
    val paying = state.payUrl != null || state.openingInvoice
    val fee = formatFee(state.sessionFee, state.currency)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .background(LexPaper),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // Ringed portrait — three concentric pulsing rings around the
            // advocate's initial. Verdant ring colour to read as "active &
            // benign", not danger.
            WaitingRings(
                initial    = state.advocate.firstName.firstOrNull()?.toString()?.uppercase() ?: "A",
                avatarUrl  = state.advocate.avatarUrl,
                ringColor  = if (paying) LexAmber else LexVerdant,
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (paying) LexAmber else LexVerdant),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (paying) "ACCEPTED · FEE DUE" else "AWAITING APPROVAL",
                    fontFamily = Inter,
                    fontSize   = 10.sp,
                    letterSpacing = 1.5.sp,
                    color      = if (paying) LexAmber else LexVerdant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text       = if (paying) "Pay The Fee To Begin" else "Awaiting Approval",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 26.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp,
                color      = LexInk,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text       = if (paying)
                    "${state.advocate.name} has accepted. The fee of $fee is an invoice from the firm, " +
                    "open in your browser. Pay it within three minutes and your consultation starts here."
                else
                    "${state.advocate.name} is reviewing your brief" +
                    (state.approvalWindowMinutes?.let { " and has $it minute${if (it == 1) "" else "s"} to respond" } ?: "") +
                    ". This screen updates the moment they do.",
                fontFamily = Inter,
                fontSize   = 13.sp,
                lineHeight = 20.sp,
                color      = LexInkMuted,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.widthIn(max = 280.dp),
            )

            if (state.briefSummary != null && !paying) {
                Spacer(Modifier.height(22.dp))
                Surface(
                    color    = LexCard,
                    border   = BorderStroke(1.dp, LexBorder),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text       = "YOUR BRIEF",
                            fontFamily = Inter,
                            fontSize   = 9.sp,
                            letterSpacing = 1.4.sp,
                            color      = LexInkFaint,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text       = state.briefSummary,
                            fontFamily = Inter,
                            fontSize   = 12.5.sp,
                            lineHeight = 19.sp,
                            color      = LexInk,
                        )
                    }
                }
            }
        }

        // Bottom action — Cancel (text-only outline, never primary)
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            if (paying) {
                Button(
                    onClick  = onOpenInvoice,
                    enabled  = state.payUrl != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = LexInk, contentColor = LexPaper),
                ) {
                    Text(
                        text       = if (state.payUrl == null) "Opening the Invoice…" else "Open the Invoice · $fee",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick  = onCancel,
                enabled  = !state.cancelling,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                border   = BorderStroke(1.dp, LexBorderStrong),
            ) {
                Text(
                    text       = if (state.cancelling) "Cancelling…" else "Cancel This Request",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.5.sp,
                    color      = LexInkMuted,
                )
            }
        }
    }
}

@Composable
private fun WaitingRings(initial: String, avatarUrl: String?, ringColor: Color) {
    val transition = rememberInfiniteTransition(label = "intake-rings")
    val phase1 = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring-phase-1",
    )
    val phase2 = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing, delayMillis = 1000), RepeatMode.Restart),
        label = "ring-phase-2",
    )
    val phase3 = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing, delayMillis = 2000), RepeatMode.Restart),
        label = "ring-phase-3",
    )

    Box(
        modifier = Modifier.size(148.dp),
        contentAlignment = Alignment.Center,
    ) {
        Ring(progress = phase1.value, color = ringColor)
        Ring(progress = phase2.value, color = ringColor)
        Ring(progress = phase3.value, color = ringColor)

        // Portrait at the centre
        Box(
            modifier = Modifier
                .size(84.dp)
                .border(3.dp, LexCard, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF1E3A5F)),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text       = initial,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 30.sp,
                    color      = Color.White,
                )
            }
        }
    }
}

@Composable
private fun Ring(progress: Float, color: Color) {
    // progress 0..1 maps to size 84dp..148dp and opacity 0.5..0
    val size  = 84.dp + (64.dp * progress)
    val alpha = (0.5f * (1f - progress)).coerceAtLeast(0f)
    Box(
        modifier = Modifier
            .size(size)
            .border(1.5.dp, color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun DeclinedContent(
    inner: PaddingValues,
    state: DoIntakeUiState.Declined,
    onBack: () -> Unit,
) {
    TerminalStateBlock(
        inner       = inner,
        eyebrowText = if (state.lapsedAfterAccept) "REQUEST LAPSED" else "ADVOCATE COULDN'T TAKE THIS",
        eyebrowColor= LexClaret,
        headline    = if (state.lapsedAfterAccept) "The Fee Was Not Paid In Time" else "${state.advocate.name} Is Unavailable",
        body        = state.reason,
        onBack      = onBack,
    )
}

@Composable
private fun RegistrationContent(
    inner: PaddingValues,
    state: DoIntakeUiState.NeedsRegistration,
    onRegister: () -> Unit,
    onBack: () -> Unit,
) {
    val office = state.officeName.ifBlank { "the Digital Office" }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .background(LexPaper),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("REGISTRATION NEEDED", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 1.5.sp, color = LexClaret)
            Spacer(Modifier.height(12.dp))
            Text(
                text       = "Register For $office",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 25.sp,
                lineHeight = 29.sp,
                color      = LexInk,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text       = state.message ?: ("Sessions with the chamber's advocates are open to registered clients. " +
                             "Registration is completed once, on the firm's website; come back here afterwards to send a request."),
                fontFamily = Inter,
                fontSize   = 13.sp,
                lineHeight = 20.sp,
                color      = LexInkMuted,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.widthIn(max = 300.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Button(
                onClick  = onRegister,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = LexClaret, contentColor = LexOnInk),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text("Register On The Website", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, LexBorderStrong),
            ) {
                Text("Back", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LexInkMuted)
            }
        }
    }
}

@Composable
private fun CancelledContent(
    inner: PaddingValues,
    state: DoIntakeUiState.Cancelled,
    onBack: () -> Unit,
) {
    TerminalStateBlock(
        inner       = inner,
        eyebrowText = "REQUEST CANCELLED",
        eyebrowColor= LexInkMuted,
        headline    = "Request Cancelled",
        body        = "${state.advocateName} has been notified. You can start a new request anytime.",
        onBack      = onBack,
    )
}

@Composable
private fun TerminalStateBlock(
    inner: PaddingValues,
    eyebrowText: String,
    eyebrowColor: Color,
    headline: String,
    body: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .background(LexPaper),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text       = eyebrowText,
                fontFamily = Inter,
                fontSize   = 10.sp,
                letterSpacing = 1.5.sp,
                color      = eyebrowColor,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text       = headline,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 25.sp,
                lineHeight = 29.sp,
                letterSpacing = (-0.5).sp,
                color      = LexInk,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text       = body,
                fontFamily = Inter,
                fontSize   = 13.sp,
                lineHeight = 20.sp,
                color      = LexInkMuted,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.widthIn(max = 300.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LexClaret)
                    .clickable(onClick = onBack)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Back To Chamber",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    letterSpacing = 0.5.sp,
                    color      = LexOnInk,
                )
            }
        }
    }
}

// The theme's own colours (light and dark), named as this screen used them.
// The urgency tiles keep fixed status colours with white text.
private val LexClaret: Color      @Composable get() = Lex.colors.primary
private val LexVerdant            = Color(0xFF1A5C3A)
private val LexAmber              = Color(0xFF92400E)
private val LexUrgent             = Color(0xFF7D1930)
private val LexInk: Color         @Composable get() = Lex.colors.foreground
private val LexInkMuted: Color    @Composable get() = Lex.colors.mutedForeground
private val LexInkFaint: Color    @Composable get() = Lex.colors.faint
private val LexBorder: Color      @Composable get() = Lex.colors.border
private val LexBorderStrong: Color @Composable get() = Lex.colors.input
private val LexPaper: Color       @Composable get() = Lex.colors.background
private val LexSurface1: Color    @Composable get() = Lex.colors.muted
private val LexCard: Color        @Composable get() = Lex.colors.card
private val LexOnInk: Color       @Composable get() = Lex.colors.primaryForeground

private data class IntakeArea(val key: String, val label: String, val icon: ImageVector)
private val INTAKE_AREAS = listOf(
    IntakeArea("property", "Property", Icons.Outlined.HomeWork),
    IntakeArea("tenancy",  "Tenancy",  Icons.Outlined.Key),
    IntakeArea("contract", "Contract", Icons.Outlined.Description),
    IntakeArea("family",   "Family",   Icons.Outlined.FamilyRestroom),
    IntakeArea("civil",    "Civil",    Icons.Outlined.Gavel),
    IntakeArea("other",    "Other",    Icons.Outlined.MoreHoriz),
)

private data class IntakeUrgency(val key: String, val label: String, val color: Color)
private val INTAKE_URGENCIES = listOf(
    IntakeUrgency("low",  "Low",        LexVerdant),
    IntakeUrgency("med",  "This Week",  LexAmber),
    IntakeUrgency("high", "Urgent",     LexUrgent),
)

@Composable
private fun ReadyContent(
    inner: PaddingValues,
    state: DoIntakeUiState.Ready,
    onSubmit: (String) -> Unit,
) {
    // A three-step wizard: the matter, the brief, then a review that says
    // plainly what happens next (the advocate approves; any fee follows).
    var step     by rememberSaveable { mutableStateOf(0) }
    var area     by rememberSaveable { mutableStateOf<String?>(null) }
    var urgency  by rememberSaveable { mutableStateOf<String?>(null) }
    var brief    by rememberSaveable { mutableStateOf("") }
    var consent  by rememberSaveable { mutableStateOf(false) }
    val maxBriefChars = 600

    val briefLen   = brief.trim().length
    val charLeft   = (maxBriefChars - brief.length).coerceAtLeast(0)
    val stepTitles = listOf("Your Matter", "Your Brief", "Review And Send")
    val canAdvance = when (step) {
        0    -> area != null && urgency != null
        1    -> briefLen >= 20
        else -> consent && !state.submitting
    }
    val fee = if (state.sessionFee > 0) formatFee(state.sessionFee, state.currency) else null
    val advocateName = state.advocate.name

    androidx.activity.compose.BackHandler(enabled = step > 0 && !state.submitting) { step -= 1 }

    Column(modifier = Modifier.fillMaxSize().padding(inner).imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "STEP ${step + 1} OF 3 · ${stepTitles[step].uppercase()}",
                fontFamily = Inter,
                fontSize   = 10.sp,
                letterSpacing = 1.5.sp,
                color      = LexInkFaint,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) LexClaret else LexBorder),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when (step) {
                0 -> {
                    Text(
                        text       = "What Is Your Matter About?",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 24.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color      = LexInk,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = "$advocateName reads your request before accepting it, so " +
                                     "the call starts with your matter already understood.",
                        fontFamily = Inter,
                        fontSize   = 12.5.sp,
                        lineHeight = 19.sp,
                        color      = LexInkMuted,
                    )
                    Spacer(Modifier.height(22.dp))
                    IntakeFieldLabel(
                        label = "Area Of Law",
                        help  = "Select the closest match; you may clarify during the session.",
                    )
                    AreaChipGrid(selected = area, onSelect = { area = it })
                    Spacer(Modifier.height(22.dp))
                    IntakeFieldLabel(label = "How Urgent Is It?")
                    UrgencySegmentedControl(selected = urgency, onSelect = { urgency = it })
                }
                1 -> {
                    Text(
                        text       = "Describe Your Situation",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 24.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color      = LexInk,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = "A few lines is plenty — what happened, and what you'd like to achieve.",
                        fontFamily = Inter,
                        fontSize   = 12.5.sp,
                        lineHeight = 19.sp,
                        color      = LexInkMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    IntakeTextarea(
                        value         = brief,
                        onValueChange = { brief = it.take(maxBriefChars) },
                        enabled       = !state.submitting,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text       = "${brief.length} / $maxBriefChars",
                            fontFamily = Inter,
                            fontSize   = 10.sp,
                            color      = if (charLeft == 0) LexClaret else LexInkFaint,
                        )
                    }
                    if (briefLen in 1..19) {
                        Text(
                            text       = "${20 - briefLen} more character${if (20 - briefLen == 1) "" else "s"} so your advocate gets enough context.",
                            fontFamily = Inter,
                            fontSize   = 11.sp,
                            color      = LexAmber,
                            modifier   = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                else -> {
                    Text(
                        text       = "Review Your Request",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 24.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp,
                        color      = LexInk,
                    )
                    Spacer(Modifier.height(14.dp))
                    ReviewRow("Advocate", advocateName) { step = 0 }
                    ReviewRow("Area Of Law", INTAKE_AREAS.firstOrNull { it.key == area }?.label ?: "—") { step = 0 }
                    ReviewRow("Urgency", INTAKE_URGENCIES.firstOrNull { it.key == urgency }?.label ?: "—") { step = 0 }
                    ReviewRow("Brief", brief.trim()) { step = 1 }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text       = buildString {
                            append("Your request goes to $advocateName for approval. ")
                            if (fee != null) append("Once they accept, the session fee of $fee is raised as an invoice that you pay on the firm's website; the call begins after payment. ")
                            else append("Once they accept, the call begins. ")
                            append("Nothing is charged if they decline.")
                        },
                        fontFamily = Inter,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                        color      = LexInkMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    ConsentCheckbox(checked = consent, onToggle = { consent = !consent })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        IntakeActionBar(
            submitting = state.submitting,
            enabled    = canAdvance,
            label      = if (step < 2) "Continue" else "Send For Approval",
            onBack     = if (step > 0 && !state.submitting) ({ step -= 1 }) else null,
            onClick    = {
                if (step < 2) step += 1
                else {
                    val areaLabel    = INTAKE_AREAS.firstOrNull { it.key == area }?.label
                    val urgencyLabel = INTAKE_URGENCIES.firstOrNull { it.key == urgency }?.label
                    onSubmit(packIntake(areaLabel, urgencyLabel, brief))
                }
            },
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp),
    ) {
        Text(label, fontFamily = Inter, fontSize = 11.sp, color = LexInkFaint)
        Spacer(Modifier.height(2.dp))
        Text(value, fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp, color = LexInk)
    }
    HorizontalDivider(color = LexBorder, thickness = 1.dp)
}

/** Money as Indian grouping (12,34,567), without trailing paise when whole. */
private fun formatFee(amount: Double, currency: String): String =
    com.legumlex.client.feature.finance.formatMoney(amount, if (currency == "INR") "₹" else "$currency ").removeSuffix(".00")

private fun packIntake(area: String?, urgency: String?, brief: String): String {
    val tags = listOfNotNull(area?.let { "Area: $it" }, urgency?.let { "Urgency: $it" })
    return if (tags.isEmpty()) brief.trim()
    else tags.joinToString(" · ") + "\n\n" + brief.trim()
}

@Composable
private fun IntakeFieldLabel(label: String, help: String? = null) {
    Text(
        text       = label,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 13.sp,
        color      = LexInk,
        modifier   = Modifier.padding(bottom = 4.dp),
    )
    if (help != null) {
        Text(
            text       = help,
            fontFamily = Inter,
            fontSize   = 11.5.sp,
            lineHeight = 16.sp,
            color      = LexInkMuted,
            modifier   = Modifier.padding(bottom = 10.dp),
        )
    } else {
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AreaChipGrid(selected: String?, onSelect: (String) -> Unit) {
    // 3-column grid via Rows. Simpler than FlowRow which requires Compose 1.5+.
    val rows = INTAKE_AREAS.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { area ->
                    AreaChip(
                        label    = area.label,
                        icon     = area.icon,
                        selected = selected == area.key,
                        onClick  = { onSelect(area.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad with empty weight cells if last row is partial
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AreaChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg     = if (selected) LexInk else LexCard
    val fg     = if (selected) LexOnInk else LexInkMuted
    val border = if (selected) LexInk else LexBorder
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text       = label,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            color      = fg,
            maxLines   = 1,
        )
    }
}

@Composable
private fun UrgencySegmentedControl(selected: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        INTAKE_URGENCIES.forEach { u ->
            val isSel = selected == u.key
            val bg     = if (isSel) u.color else LexCard
            val fg     = if (isSel) Color.White else LexInkMuted
            val border = if (isSel) u.color else LexBorder
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
                    .clickable { onSelect(u.key) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = u.label,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.sp,
                    color      = fg,
                )
            }
        }
    }
}

@Composable
private fun IntakeTextarea(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = {
            Text(
                "e.g. My landlord changed the locks on our shop and is refusing access " +
                "despite a valid lease until 2027. I want to know how to regain possession quickly.",
                color = LexInkFaint,
                fontFamily = Inter,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        },
        minLines      = 5,
        maxLines      = 8,
        modifier      = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction      = ImeAction.Default,
        ),
        enabled       = enabled,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = LexInk,
            unfocusedBorderColor = LexBorder,
            disabledBorderColor  = LexBorder,
            cursorColor          = LexClaret,
            focusedContainerColor   = LexCard,
            unfocusedContainerColor = LexCard,
            disabledContainerColor  = LexCard,
        ),
    )
}

@Composable
private fun ConsentCheckbox(checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LexSurface1)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) LexVerdant else com.legumlex.client.ui.theme.Lex.colors.card)
                .border(
                    BorderStroke(1.5.dp, if (checked) LexVerdant else LexBorderStrong),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = buildString {
                append("I understand this is a preliminary consultation and that an advocate–client ")
                append("relationship begins only once expressly agreed. ")
            },
            fontFamily = Inter,
            fontSize   = 11.5.sp,
            lineHeight = 16.sp,
            color      = LexInkMuted,
            modifier   = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IntakeActionBar(
    submitting: Boolean,
    enabled: Boolean,
    label: String,
    onBack: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(LexPaper)) {
        HorizontalDivider(color = LexBorder, thickness = 1.dp)
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    shape   = RoundedCornerShape(12.dp),
                    border  = BorderStroke(1.dp, LexBorderStrong),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
                ) {
                    Text("Back", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LexInkMuted)
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (enabled && !submitting) LexClaret else LexInkFaint)
                    .clickable(enabled = enabled && !submitting, onClick = onClick)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        color       = LexOnInk,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text       = "Sending…",
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = LexOnInk,
                    )
                } else {
                    Text(
                        text       = label,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = LexOnInk,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = LexOnInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner(inner: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ErrorBlock(inner: PaddingValues, message: String, onRetry: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier         = Modifier.size(56.dp).clip(CircleShape).background(StatusDanger.softBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = StatusDanger.solid, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(spacing.s4))
        Text(
            "The Session Could Not Start",
            style = MaterialTheme.typography.headlineSmall,
            color = PrimaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.s2))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.s5))
        LegumSecondaryButton(
            text        = "Try Again",
            leadingIcon = Icons.Outlined.Refresh,
            onClick     = onRetry,
            modifier    = Modifier.widthIn(max = 280.dp),
        )
    }
}
