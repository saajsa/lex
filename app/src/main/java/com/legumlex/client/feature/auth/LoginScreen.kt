package com.legumlex.client.feature.auth

import android.content.Context
import com.legumlex.client.feature.desk.DeskSurface
import com.legumlex.client.feature.desk.DeskRole
import com.legumlex.client.feature.desk.deskFill
import com.legumlex.client.feature.desk.deskInk
import androidx.compose.ui.semantics.liveRegion
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.legumlex.client.BuildConfig
import com.legumlex.client.R
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumTextField
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.StatusPalette
import com.legumlex.client.ui.theme.StatusSettled
import com.legumlex.client.ui.theme.TertiaryText
import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.graphics.Color
import com.legumlex.client.ui.components.easedDipIn
import com.legumlex.client.ui.components.easedDipOut
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.grain

// Sign In (REQ-013): one card (the title and the mobile number with the round arrow that sends the code),
// with Email and Google as round buttons on its bottom edge, and Lex Intelligence (the orb, with a typing
// bubble) beneath it; newcomers reach onboarding through Lex Intelligence. Email and the code step open in
// place; back returns to the card. Debug builds carry one small chip with the test sign-ins.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    /** The full guest desk: where the chat panel hands over a step it does not do itself (Google sign-in, an upload, opening an account). */
    onAskLexIntelligence: () -> Unit = {},
    /** The petals' own screens, where the desk does not take the wish: the existing Onboard Now screen, and the counsel registration screen. */
    onOnboard: () -> Unit = {},
    onRegisterCounsel: () -> Unit = {},
    /** Debug builds only: the desk's sample conversation. */
    onDeskSample: () -> Unit = {},
) {
    val spacing  = LocalSpacing.current
    val context  = LocalContext.current
    val activity = context.requireActivity()
    val scope    = rememberCoroutineScope()
    val vm: LoginViewModel = viewModel()
    val state by vm.state.collectAsState()
    val view by vm.view.collectAsState()
    val mobile by vm.mobile.collectAsState()

    val isLoading = state is LoginState.Loading

    LaunchedEffect(state) {
        if (state is LoginState.Success) onLoginSuccess()
    }
    val onCodeStep = mobile.ref != null
    BackHandler(enabled = view != LoginView.Choose || onCodeStep) { if (onCodeStep) vm.changeNumber() else vm.show(LoginView.Choose) }
    // Lex Intelligence's chat takes over the bottom half; Back closes it first (the last handler registered answers first).
    var chat by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = chat) { chat = false }
    val still = com.legumlex.client.feature.board.rememberReducedMotion()
    val pageScroll = rememberScrollState()
    // google_config's switches decide which petals show and where each goes (read once, as the page opens).
    var switches by remember { mutableStateOf(DeskSwitches()) }
    LaunchedEffect(Unit) { switches = deskSwitches(runCatching { ApiClient.get(context).googleConfig() }.getOrNull()) }
    var start by remember { mutableStateOf<PanelStart?>(null) }
    var arcOpen by remember { mutableStateOf(false) }
    // The first-time hint: shown until the button has been touched once, or the screen has been seen three times without a touch.
    val hint = remember { OrbHint(prefsStore(context, "lex_orb_hint")) }
    var counted by rememberSaveable { mutableStateOf(false) }
    var hintOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hintOn = if (!counted) { counted = true; hint.visit() } else hint.shouldShow() }
    var belowPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The texture is dimmed while the arc is open (the petals stay clean) and goes with the orb while the chat is open.
    val texAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (chat) 0f else if (arcOpen) 0.25f else 1f, androidx.compose.animation.core.tween(if (still) 0 else 260), label = "texture")
    val onPetal: (Petal) -> Unit = { p ->
        when (val r = petalRoute(p, switches)) {
            is PetalRoute.Chat -> { start = PanelStart.Message(r.message); chat = true }
            PetalRoute.GoogleSignIn -> { start = PanelStart.SignIn; chat = true }
            PetalRoute.OnboardScreen -> onOnboard()
            PetalRoute.CounselScreen -> onRegisterCounsel()
            null -> Unit
        }
    }

    // Google: a client's way in, and a newcomer's (as a lead, to Lex Intelligence).
    val signInWithGoogle: () -> Unit = {
        vm.clearError()
        scope.launch {
            val serverClientId = runCatching {
                ApiClient.get(context).googleConfig()["client_id"] as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GOOGLE_WEB_CLIENT_ID
            requestGoogleIdToken(
                context        = context,
                activity       = activity,
                serverClientId = serverClientId,
                onToken        = vm::onIdTokenReceived,
                onError        = vm::onCredentialError,
                onCancel       = vm::onCancelled,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Lex.colors.background).grain()) {
        // The screen's height less the bars and the page's own padding: the content is at least this tall, so the date block
        // takes the room left at the foot without moving the cards; a short screen scrolls instead.
        val bars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val screenH = maxHeight
        // Where the card's squares end (screen coordinates, as measured now) and how far the page is scrolled: the chat starts 12 dp below them.
        var squaresBottom by remember { mutableFloatStateOf(0f) }
        val panelH = with(density) { chatPanelHeight(screenH.value, if (squaresBottom > 0f) (squaresBottom + pageScroll.value).toDp().value else null).dp }
        val minContent = (maxHeight - bars - spacing.s3 - spacing.s4 - 8.dp).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(pageScroll)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.s4)
                .padding(top = spacing.s3, bottom = spacing.s4),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = minContent)) {
            // Our monogram (the orb floats at the bottom right).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LegumMark(size = 34.dp, fillColor = PrimaryText)
            }
            Spacer(Modifier.height(spacing.s5))

            // Inline error per handoff UX rule 5 ("confirmation, not interruption").
            (state as? LoginState.Error)?.let { err ->
                Banner(message = err.message, palette = StatusDanger, onDismiss = vm::clearError)
                Spacer(Modifier.height(spacing.s4))
            }

            // The card, with Email and Google on its bottom edge (all hidden during the code step; no envelope in the Email view).
            SignInCard(
                onBottom = { squaresBottom = it },
                actions = edgeActions(codeStep = onCodeStep, emailView = view == LoginView.Email), busy = isLoading,
                onAction = { a -> runSignInAction(a, { vm.show(LoginView.Email) }, signInWithGoogle) },
            ) {
                // The number view takes the Email view's laid-out height (the owner's height), so the card does not change size when switching;
                // the code step keeps its own.
                EqualHeight(
                    enabled = !onCodeStep,
                    reference = {
                        Column(Modifier.fillMaxWidth()) {
                            CardTitle("Sign In")
                            EmailView(vm = vm, busy = isLoading, onBack = {})
                        }
                    },
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        CardTitle(if (onCodeStep) "Enter The Code" else "Sign In")
                        AnimatedContent(
                            targetState = if (onCodeStep) LoginView.Mobile else view,
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 12 })
                                    .togetherWith(fadeOut(tween(120)))
                            },
                            label = "login-view",
                        ) { v ->
                            Column(Modifier.fillMaxWidth()) {
                                when (v) {
                                    LoginView.Choose -> NumberRow(vm = vm, mobile = mobile, busy = isLoading)
                                    LoginView.Mobile -> CodeStep(vm = vm, mobile = mobile, busy = isLoading)
                                    LoginView.Email -> EmailView(vm = vm, busy = isLoading, onBack = { vm.show(LoginView.Choose) })
                                }
                            }
                        }
                    }
                }
            }

            // Debug builds only: one small chip with the test sign-ins; nothing in a release build.
            val debug = debugSignIns(BuildConfig.DEBUG, BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_ADV_PASSWORD, BuildConfig.DEV_ADV_EMAIL, BuildConfig.DEV_ADV_EMPTY_EMAIL)
            // In debug builds the chip also leads to the desk's sample conversation, even with no test sign-ins configured.
            val chipItems = if (BuildConfig.DEBUG) debug + DebugSignIn.DeskSample + DebugSignIn.ResetHint else debug
            if (chipItems.isNotEmpty()) {
                Spacer(Modifier.height(spacing.s3))
                DebugChip(
                    items = chipItems, enabled = !isLoading,
                    onPick = { d ->
                        when (d) {
                            DebugSignIn.TestContact -> vm.onEmailLogin(BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_LOGIN_PASSWORD)
                            DebugSignIn.CounselWithCases -> vm.onEmailLogin(BuildConfig.DEV_ADV_EMAIL, BuildConfig.DEV_ADV_PASSWORD)
                            DebugSignIn.CounselEmpty -> vm.onEmailLogin(BuildConfig.DEV_ADV_EMPTY_EMAIL, BuildConfig.DEV_ADV_PASSWORD)
                            DebugSignIn.DeskSample -> onDeskSample()
                            DebugSignIn.ResetHint -> { hint.reset(); hintOn = hint.visit() }
                        }
                    },
                )
            }

            // Today and a legal maxim at the foot: type only, no action. When the chat opens the date slides down and out; the room below the
            // card is left for the chat (it is drawn over the screen's bottom half, below), so the card can scroll above it.
            // Lex Intelligence, the orb, centred below the Debug chip (ORB_GAP under it, so its arc of petals clears the card's squares and the chip);
            // it goes while the chat is open and its place stays. The date and maxim stay at the foot.
            // The AI texture fills the space round the orb (below the chip and the squares, above the date), behind the orb, its ripple and the arc.
            Box(Modifier.fillMaxWidth()) {
                AiTexture(Modifier.matchParentSize(), belowDp = with(density) { belowPx.toDp().value }, show = texAlpha, reduced = still)
                Column {
                    // The first-time hint sits in the band above the button, centred on it, with a pointer down to it; not while the arc is open.
                    Box(Modifier.fillMaxWidth().height(ORB_GAP.dp), contentAlignment = Alignment.BottomCenter) {
                        if (hintOn && !chat && !arcOpen) OrbHintBubble(
                            hintLines(visiblePetals(switches).isNotEmpty()), reduced = still,
                            onTap = { hint.dismiss(); hintOn = false },
                            modifier = Modifier.padding(bottom = HINT_LIFT.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(ORB_BOX.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !chat, enter = if (still) EnterTransition.None else fadeIn(tween(220)), exit = if (still) ExitTransition.None else fadeOut(tween(120)),
                        ) {
                            LexOrb(enabled = !isLoading, onOpen = { chat = true }, compact = true, scale = 1.7f, petals = visiblePetals(switches), onPetal = onPetal, ripple = true, onArc = { arcOpen = it }, onFirstTouch = { hint.dismiss(); hintOn = false })
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f).onSizeChanged { belowPx = it.height })
            AnimatedVisibility(
                visible = !chat, enter = EnterTransition.None,
                exit = if (still) ExitTransition.None else slideOutVertically(tween(CHAT_SLIDE_MS)) { it / 2 } + fadeOut(tween(CHAT_SLIDE_MS / 2)),
            ) {
                Column {
                    Spacer(Modifier.height(FootGeometry.GAP_ABOVE.dp))
                    DateAndMaxim(Modifier.padding(start = 20.dp, end = 20.dp))
                }
            }
            if (chat) Spacer(Modifier.height(panelH))
            }
        }
        // Lex Intelligence's chat: anchored to the bottom half of the screen, header at its top, messages scrolling inside, the input at its
        // foot. It takes the keyboard's inset itself (imePadding), so with the keyboard up it sits directly above it and its list shrinks.
        AnimatedVisibility(
            visible = chat,
            enter = if (still) EnterTransition.None else slideInVertically(tween(CHAT_SLIDE_MS)) { it } + fadeIn(tween(CHAT_SLIDE_MS)),
            exit = if (still) ExitTransition.None else slideOutVertically(tween(CHAT_SLIDE_MS)) { it } + fadeOut(tween(CHAT_SLIDE_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            com.legumlex.client.feature.desk.LexChatPanel(
                onClose = { chat = false }, onFullDesk = onAskLexIntelligence, start = start, onStarted = { start = null },
                modifier = run {
                    val keyboard = WindowInsets.ime.getBottom(density) > 0
                    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    // At rest the panel is exactly from below the squares to the screen's bottom; with the keyboard up it may take everything above it.
                    (if (keyboard) Modifier.statusBarsPadding() else Modifier).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .heightIn(max = if (keyboard) screenH else (panelH - nav).coerceAtLeast(0.dp))
                }
                    .padding(horizontal = spacing.s4 / 2),
            )
        }
    }
}

/**
 * The chat panel's height at rest: from 12 dp below the card's squares (their bottom is [squaresBottomDp] down the page; null before it is
 * measured, when half the screen stands in) to the screen's bottom, never under 320 dp: a screen too short for that scrolls the card instead.
 */
internal fun chatPanelHeight(screenDp: Float, squaresBottomDp: Float?): Float =
    (if (squaresBottomDp == null) screenDp * 0.5f else screenDp - (squaresBottomDp + 12f)).coerceAtLeast(320f)

internal const val CHAT_SLIDE_MS = 300

/**
 * Today in India (never the phone's zone) as the reference's date: the weekday, the ordinal day large beneath it and the month quieter
 * beside it; then a short rule and the day's maxim with its meaning. It moves on at midnight IST. Plain text for a screen reader.
 */
@Composable
private fun DateAndMaxim(modifier: Modifier = Modifier) {
    var today by remember { mutableStateOf(com.legumlex.client.feature.board.BoardDates.today()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(com.legumlex.client.feature.board.BoardDates.millisToNextDay(java.time.Instant.now()))
            today = com.legumlex.client.feature.board.BoardDates.today()
        }
    }
    val w = dateWords(today)
    val maxim = SignInMaxims.forDate(today)
    // The foot block at its smaller size (013.18): the room it frees goes to the orb and its arc. Same content: IST date, the day's maxim.
    Column(modifier.fillMaxWidth()) {
        Text(w.weekday, fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 22.sp, lineHeight = FootGeometry.WEEKDAY_LINE.sp, color = PrimaryText)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(w.day, fontFamily = com.legumlex.client.ui.theme.Display, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, fontSize = 26.sp, lineHeight = FootGeometry.DAY_LINE.sp, color = SecondaryText)
            Spacer(Modifier.weight(1f))
            Text(w.month, style = MaterialTheme.typography.titleSmall, color = SecondaryText, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(36.dp).height(1.dp).background(Lex.colors.input))
        Spacer(Modifier.height(6.dp))
        Text(maxim.latin, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = FootGeometry.MAXIM_LINE.sp), color = PrimaryText)
        Spacer(Modifier.height(2.dp))
        Text(maxim.meaning, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = FootGeometry.MEANING_LINE.sp), color = SecondaryText)
    }
}

/** The debug-build test sign-ins (REQ-013): the test contact, and the two counsel accounts. */
enum class DebugSignIn(val label: String) {
    TestContact("Test Contact"), CounselWithCases("Counsel · With Cases"), CounselEmpty("Counsel · Empty"),
    /** Debug builds: a screen with every element the desk draws, for looking at both themes. */
    DeskSample("Desk Message Samples"),
    /** Debug builds: show the first-time Lex hint again. */
    ResetHint("Reset Lex Hint"),
}

/**
 * Which test sign-ins the Debug chip offers. None in a release build, whatever is configured; in a debug build each needs
 * its address (and the counsel ones their password), all from local.properties, never from source.
 */
internal fun debugSignIns(debug: Boolean, devEmail: String, advPassword: String, advEmail: String, advEmptyEmail: String): List<DebugSignIn> {
    if (!debug) return emptyList()
    return buildList {
        if (devEmail.isNotBlank()) add(DebugSignIn.TestContact)
        if (advPassword.isNotBlank() && advEmail.isNotBlank()) add(DebugSignIn.CounselWithCases)
        if (advPassword.isNotBlank() && advEmptyEmail.isNotBlank()) add(DebugSignIn.CounselEmpty)
    }
}

/** The three other ways in, as the icon row's buttons. */
enum class SignInAction(val label: String) {
    Email("Sign In With Email"), Google("Sign In With Google"),
}

/** What each button does: email opens the email form in place, Google runs the Google flow, the orb opens Lex Intelligence. */
internal fun runSignInAction(action: SignInAction, onEmail: () -> Unit, onGoogle: () -> Unit) = when (action) {
    SignInAction.Email -> onEmail()
    SignInAction.Google -> onGoogle()
}

/** The hint under the mobile field. */
const val MOBILE_HINT = "Code comes on WhatsApp/SMS."

// ---- The edge buttons: raised rounded squares in a dip of the card's bottom edge, as the Counsel bar's Cause List button sits in its dip ----

private val DipButton = DipGeometry.BUTTON.dp
private val DipButtonGap = DipGeometry.GAP.dp
private val DipDepth = DipGeometry.DEPTH.dp           // scaled from the bar's NotchDepth with the buttons (48/56)
private val DipOverhang = DipGeometry.OVERHANG.dp     // how far a button hangs below the card
private val DipShoulder = DipGeometry.SHOULDER.dp     // each eased S
private val DipEnd = DipGeometry.END.dp               // from the card's right edge to the dip's end
private val DipTuck = DipGeometry.TUCK.dp             // how far the end buttons reach into the shoulders
private val DeskYellow = androidx.compose.ui.graphics.Color(0xFFF5C518)
private val BarBlack = androidx.compose.ui.graphics.Color(0xFF111110)   // the counsel bar's colour
private val OffWhite = androidx.compose.ui.graphics.Color(0xFFF4F3EF)
internal val OrbInk = androidx.compose.ui.graphics.Color(0xFF151513)

/**
 * The buttons on the card's bottom edge, left to right: Email, Google. None in the code step; only Google in the Email view
 * (Google keeps the right-hand place and Lex Intelligence the one beside it).
 */
internal fun edgeActions(codeStep: Boolean, emailView: Boolean): List<SignInAction> = when {
    codeStep -> emptyList()
    emailView -> listOf(SignInAction.Google)
    else -> listOf(SignInAction.Email, SignInAction.Google)
}

/** The dip's sizes in dp: the print button's proportions (56 dp, 34 deep, 26 below) scaled to 48 dp buttons. */
internal object DipGeometry {
    const val BUTTON = 48f
    const val GAP = 20f
    const val DEPTH = 29f
    const val OVERHANG = 22f
    const val SHOULDER = 38f
    const val END = 14f
    const val TUCK = 12f
    const val RADIUS = 15f
}

/** The width in dp of the row of [n] buttons. */
internal fun dipRowWidth(n: Int): Float = if (n <= 0) 0f else n * DipGeometry.BUTTON + (n - 1) * DipGeometry.GAP

/** The flat run of the dip for [n] buttons: the row less what the end buttons reach into the shoulders. */
internal fun dipFlatRun(n: Int): Float = if (n <= 0) 0f else (dipRowWidth(n) - 2 * DipGeometry.TUCK).coerceAtLeast(0f)

/**
 * The card: rounded (12 dp), and, when it has buttons, with one smooth dip eased UP into its bottom edge near the right, wide enough for them:
 * the bar's own eased S at each end (shared helper) and a flat run across the middle. The outline follows the dip.
 */
private class DippedCardShape(val buttons: Int) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline = with(density) {
        val w = size.width; val h = size.height
        val r = 12.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
            lineTo(w, h - r)
            arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            if (buttons > 0) {
                val sw = DipShoulder.toPx(); val d = DipDepth.toPx()
                val xr = w - DipEnd.toPx()
                val flat = dipFlatRun(buttons).dp.toPx()
                // leftward along the bottom edge: in, across, out
                lineTo(xr, h)
                easedDipIn(xr, h, -sw, -d)
                lineTo(xr - sw - flat, h - d)
                easedDipOut(xr - sw - flat, h - d, -sw, d)
            }
            lineTo(r, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/**
 * The Sign In card. With [actions] it has a dip in its bottom edge and a row of raised rounded squares (56 dp, radius 18, elevation 6 dp, like
 * the Cause List button) sitting in it, 30 dp inside the dip and 26 dp below the card. The content keeps clear of the dip.
 */
@Composable
private fun SignInCard(onBottom: (Float) -> Unit, actions: List<SignInAction>, busy: Boolean, onAction: (SignInAction) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val n = actions.size
    val shape = remember(n) { DippedCardShape(n) }
    val dark = Lex.colors.isDark
    // The box is as tall as the card plus what the buttons hang below it, so they are inside its bounds (and tappable).
    Box(Modifier.fillMaxWidth().onGloballyPositioned { onBottom(it.boundsInRoot().bottom) }) {
        Column(
            Modifier.fillMaxWidth()
                .padding(bottom = if (n > 0) DipOverhang else 0.dp)
                .background(Lex.colors.card, shape).border(1.dp, BorderColor, shape)
                // The bottom leaves room for the dip.
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = if (n > 0) 40.dp else 8.dp),
            content = content,
        )
        if (n > 0) Row(
            // The row's right edge is where the dip's flat run ends, plus the tuck.
            Modifier.align(Alignment.BottomEnd).padding(end = DipEnd + DipShoulder - DipTuck),
            horizontalArrangement = Arrangement.spacedBy(DipButtonGap),
        ) {
            actions.forEach { a ->
                when (a) {
                    SignInAction.Email -> DipButton(a.label, BarBlack, Color(0xFF2A2A28), !busy, if (dark) Lex.colors.input else null, { onAction(a) }) {
                        Icon(Icons.Outlined.Mail, contentDescription = null, tint = OffWhite, modifier = Modifier.size(22.dp))
                    }
                    SignInAction.Google -> DipButton(a.label, OffWhite, Color(0xFFDAD9D3), !busy, null, { onAction(a) }) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = BarBlack)
                        else Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** A raised rounded square, as the Cause List button: solid, 56 dp, radius 18, 6 dp elevation; a little darker while pressed. */
@Composable
private fun DipButton(
    label: String, color: Color, pressedColor: Color, enabled: Boolean, hairline: Color?, onClick: () -> Unit, glyph: @Composable BoxScope.() -> Unit,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = source, shape = RoundedCornerShape(DipGeometry.RADIUS.dp),
        color = if (pressed) pressedColor else color, shadowElevation = 6.dp,
        border = hairline?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.size(DipButton).semantics { contentDescription = label },
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = glyph) }
}

/**
 * The AI texture: faint dots joined by hairlines, densest near the orb (the layout is AiNetwork's, built once per size and cached), drawn in one canvas
 * behind the orb. [belowDp] is how far the empty space runs below this box, down to the date. Ink only, flat. A few dots twinkle slowly, driven by one
 * infinite transition read only when drawing (no recomposition, no per-frame allocation); still with [reduced] motion. [show] scales it all (0 hides it).
 */
@Composable
private fun AiTexture(modifier: Modifier, belowDp: Float, show: Float, reduced: Boolean) {
    val ink = PrimaryText
    val seconds = if (reduced) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "net").animateFloat(
        0f, NET_MASTER_S.toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(NET_MASTER_S * 1000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "net-t",
    )
    val bleed = 16f
    Box(modifier.drawWithCache {
        val d = density
        val wDp = size.width / d + 2 * bleed
        val hDp = ORB_GAP + ORB_BOX + belowDp
        val net = buildNetwork(wDp, hDp, wDp / 2f, ORB_GAP + ORB_BOX / 2f)
        // Lines in five alpha steps, each one path: five draws a frame, however many lines.
        val steps = 5
        val paths = Array(steps) { androidx.compose.ui.graphics.Path() }
        val any = BooleanArray(steps)
        for (e in 0 until net.edges) {
            val s = ((net.edgeAlpha[e] / 0.10f) * steps).toInt().coerceIn(0, steps - 1)
            paths[s].moveTo((net.ex[4 * e] - bleed) * d, net.ex[4 * e + 1] * d); paths[s].lineTo((net.ex[4 * e + 2] - bleed) * d, net.ex[4 * e + 3] * d); any[s] = true
        }
        onDrawBehind {
            val a = show
            if (a <= 0.004f) return@onDrawBehind
            val t = seconds?.value ?: 0f
            for (s in 0 until steps) if (any[s]) drawPath(paths[s], ink.copy(alpha = (s + 0.5f) / steps * 0.10f * a), style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx()))
            for (i in 0 until net.nodes) {
                val m = twinkle(t, net.twPeriod[i], net.twPhase[i], reduced)
                drawCircle(ink.copy(alpha = (net.nodeAlpha[i] * m * a).coerceAtMost(0.3f)), net.nodeSize[i] * d / 2f, androidx.compose.ui.geometry.Offset((net.nx[i] - bleed) * d, net.ny[i] * d))
            }
        }
    })
}

/** How the arc is left: a petal chosen (it pulses, the others fold, then the hand-off), or dismissed (all fold back). */
private sealed interface ArcExit {
    data class Pick(val index: Int) : ArcExit
    data object Dismiss : ArcExit
}

/**
 * The compact Lex Intelligence prompt (the orb): the yellow disc with its blinking cursor and an "AI" badge. Touched,
 * a ring runs once round it, then [onOpen]. [compact] is the small one without its name; [scale] enlarges the compact one.
 * Held down, a ring fills round it, then (a light tick) an arc of [petals] fans out above it: slide onto one and lift to choose it, or
 * lift and tap one; a tap outside, or Back, folds it away. [onPetal] gets the choice once the arc has folded.
 * Motion: a press scales the orb down a little and springs back; the ring breathes slowly at rest. None of it with reduced motion.
 */
@Composable
private fun LexOrb(
    enabled: Boolean, onOpen: () -> Unit, compact: Boolean = false, scale: Float = 1f,
    petals: List<Petal> = emptyList(), onPetal: (Petal) -> Unit = {}, ripple: Boolean = false, onArc: (Boolean) -> Unit = {}, onFirstTouch: () -> Unit = {},
) {
    val k = if (compact) scale else 1f
    val outer = if (compact) 44.dp * k else 96.dp
    val inner = if (compact) 30.dp * k else 64.dp
    val reduced = com.legumlex.client.feature.board.rememberReducedMotion()
    val motion = petalMotion(reduced)
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    val hold = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var exit by remember { mutableStateOf<ArcExit?>(null) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) motion.pressScale else 1f,
        if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "orb-press",
    )
    // Returning to this screen: the ring starts empty again.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && !running) scope.launch { sweep.snapTo(0f) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "lex-pulse")
    val breath by pulse.animateFloat(1f, 1.06f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "lex-breath")
    // The cursor's blink is read only when drawing (no recomposition each half second).
    val blink by pulse.animateFloat(1f, 0f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.keyframes { durationMillis = 1060; 1f at 0; 1f at 529; 0f at 530; 0f at 1059 }),
        label = "lex-blink")
    // The ring's slow breathing at rest (about 3 s), inviting a touch.
    val ringBreath by pulse.animateFloat(0.14f, 0.34f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "lex-ring")
    val track = PrimaryText.copy(alpha = if (motion.idle) ringBreath else 0.15f)
    // The idle ripple: three hairline rings that grow from the orb to the arc's reach and fade, 2.8 s a cycle, staggered. Quiet, ink only. It rests while
    // the orb is pressed or held and while the arc is open (the chat hides the orb, and with it the ripple). With reduced motion: one still, faint ring.
    val rippleOn by androidx.compose.animation.core.animateFloatAsState(
        if (ripple && !pressed && !menu) 1f else 0f, androidx.compose.animation.core.tween(if (reduced) 0 else 240), label = "ripple-on")
    val rippleT by pulse.animateFloat(0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(RIPPLE_CYCLE_MS, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ripple-t")
    val rippleInk = PrimaryText
    val tap: () -> Unit = {
        if (enabled && !running) {
            running = true
            scope.launch {
                sweep.snapTo(0f)
                sweep.animateTo(360f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                onOpen()
                running = false
            }
        }
    }
    LaunchedEffect(menu) { onArc(menu) }
    val tapNow by rememberUpdatedState(tap)
    val touchNow by rememberUpdatedState(onFirstTouch)
    val pickNow by rememberUpdatedState(onPetal)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(outer)) {
        if (ripple && rippleOn > 0f) {
            // Drawn behind the orb and larger than its box (requiredSize: not clipped by it), but no further than the arc's reach, which stays clear of the card.
            androidx.compose.foundation.Canvas(Modifier.align(Alignment.Center).requiredSize((2 * PETAL_OUTER).dp)) {
                val c = center; val r0 = outer.toPx() / 2f; val reach = PETAL_OUTER.dp.toPx()
                if (!motion.idle) {
                    drawCircle(rippleInk.copy(alpha = RIPPLE_STILL_ALPHA * rippleOn), r0 + (reach - r0) * 0.45f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(RIPPLE_STROKE.dp.toPx()))
                } else for (n in 0 until RIPPLE_RINGS) {
                    val p = (rippleT + n.toFloat() / RIPPLE_RINGS) % 1f
                    val e = 1f - (1f - p) * (1f - p)
                    drawCircle(rippleInk.copy(alpha = rippleAlpha(p) * rippleOn), r0 + (reach - r0) * e, c, style = androidx.compose.ui.graphics.drawscope.Stroke(RIPPLE_STROKE.dp.toPx()))
                }
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .pointerInput(enabled, petals, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabled || menu) return@awaitEachGesture
                        pressed = true
                        touchNow()
                        val lp = viewConfiguration.longPressTimeoutMillis
                        // The ring fills while the finger holds, so it is seen that the arc is about to open.
                        val fill = if (petals.isNotEmpty() && !reduced) scope.launch {
                            hold.snapTo(0f); hold.animateTo(1f, androidx.compose.animation.core.tween(lp.toInt(), easing = androidx.compose.animation.core.LinearEasing))
                        } else null
                        try {
                            var cancelled = false
                            val up = withTimeoutOrNull(lp) { waitForUpOrCancellation().also { if (it == null) cancelled = true } }
                            if (up != null) { fill?.cancel(); scope.launch { hold.snapTo(0f) }; up.consume(); tapNow(); return@awaitEachGesture }
                            if (cancelled || petals.isEmpty()) { fill?.cancel(); scope.launch { hold.snapTo(0f) }; return@awaitEachGesture }
                            // Held: a light tick, and the arc fans out. Sliding onto a petal lights it (a tick for each); lifting on it chooses it,
                            // lifting elsewhere leaves the arc open.
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            menu = true; exit = null
                            scope.launch { hold.animateTo(0f, androidx.compose.animation.core.tween(if (reduced) 0 else 220)) }
                            val cx = size.width / 2f; val cy = size.height / 2f
                            while (true) {
                                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                val h = petalAt((ch.position.x - cx) / density, (ch.position.y - cy) / density, petals.size)
                                if (h != hover) { hover = h; if (h != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                ch.consume()
                                if (!ch.pressed) {
                                    val pick = hover
                                    if (pick != null && exit == null) exit = ArcExit.Pick(pick)
                                    break
                                }
                            }
                        } finally { pressed = false }
                    }
                }
                .semantics {
                    contentDescription = "Lex Intelligence"
                    onClick("Lex Intelligence") { tapNow(); true }
                    if (petals.isNotEmpty()) onLongClick("Open The Menu") { menu = true; exit = null; true }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The ring, round the button: breathing at rest, filling while held, running once when tapped.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = (if (compact) 2.dp * k else 3.dp).toPx()
                drawCircle(track, radius = size.minDimension / 2 - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                if (hold.value > 0f) drawArc(OrbInk, -90f, hold.value * 360f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(w, w),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                if (sweep.value > 0f) drawArc(DeskYellow, -90f, sweep.value, false,
                    topLeft = androidx.compose.ui.geometry.Offset(w, w),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Box(
                Modifier.size(inner).graphicsLayer { val s = if (running || !motion.idle) 1f else breath; scaleX = s; scaleY = s }
                    .clip(androidx.compose.foundation.shape.CircleShape).background(DeskYellow),
                contentAlignment = Alignment.Center,
            ) {
                val mono = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = if (compact) (11f * k).sp else 21.sp, color = androidx.compose.ui.graphics.Color.Black)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(">", style = mono)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.padding(bottom = if (compact) 3.dp * k else 7.dp).size(width = if (compact) 6.dp * k else 11.dp, height = if (compact) 2.dp * k else 3.dp)
                        .graphicsLayer { alpha = if (menu || !motion.idle) 1f else blink }.background(androidx.compose.ui.graphics.Color.Black))
                }
            }
        }
            // The badge, outside the round touch area so it is not clipped.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = if (compact) 4.dp * k else (-6).dp, y = if (compact) (-2).dp * k else 8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(OrbInk)
                    .padding(horizontal = if (compact) 3.dp * k else 5.dp, vertical = if (compact) 0.dp else 1.dp),
            ) {
                Text("AI", color = androidx.compose.ui.graphics.Color.White, fontSize = if (compact) (6f * k).sp else 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.4.sp)
            }
            if (menu) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val halfW = with(density) { (PETAL_OUTER + PETAL_MARGIN).dp.roundToPx() }
                val below = with(density) { PETAL_BELOW.dp.roundToPx() }
                Popup(
                    popupPositionProvider = remember(halfW, below) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize) =
                                androidx.compose.ui.unit.IntOffset(
                                    (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                                    anchorBounds.center.y - (popupContentSize.height - below),
                                )
                        }
                    },
                    onDismissRequest = { if (exit == null) exit = ArcExit.Dismiss },
                    properties = PopupProperties(focusable = true),
                ) {
                    PetalArc(
                        petals, hover, exit, motion,
                        onTap = { i -> if (exit == null) exit = if (i != null) ArcExit.Pick(i) else ArcExit.Dismiss },
                        onFolded = { e ->
                            menu = false; exit = null; hover = null
                            if (e is ArcExit.Pick) pickNow(petals[e.index])
                        },
                    )
                }
            }
        }
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            Text("Lex Intelligence", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        }
    }
}

/**
 * The arc of petals: rounded segments of a ring above the button (its centre is the bottom middle of the window), each with an icon and its
 * label. Flat: a card fill, a hairline border and a quiet shadow; the one under the finger lifts and turns black.
 * Opening fans them out from the button, 40 ms apart, each scaling and fading in along its own angle with a little overshoot; choosing pulses
 * the chosen one while the others fold back in; dismissing folds them all back in reverse order. [onFolded] says it is done.
 */
@Composable
private fun PetalArc(petals: List<Petal>, hover: Int?, exit: ArcExit?, motion: PetalMotion, onTap: (Int?) -> Unit, onFolded: (ArcExit) -> Unit) {
    val n = petals.size
    val prog = remember(n) { List(n) { androidx.compose.animation.core.Animatable(if (motion.openStaggerMs == 0 && motion.foldMs == 0) 1f else 0f) } }
    val lift = remember(n) { List(n) { androidx.compose.animation.core.Animatable(1f) } }
    LaunchedEffect(n) {
        petals.indices.forEach { i ->
            launch {
                delay((i * motion.openStaggerMs).toLong())
                if (motion.foldMs == 0) prog[i].snapTo(1f)
                else prog[i].animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
            }
        }
    }
    LaunchedEffect(exit) {
        val e = exit ?: return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            for (i in 0 until n) {
                if (e is ArcExit.Pick && e.index == i) {
                    // The chosen one pulses once.
                    launch {
                        if (motion.pulseMs > 0) {
                            lift[i].animateTo(1.12f, androidx.compose.animation.core.tween(motion.pulseMs))
                            lift[i].animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
                        }
                    }
                } else launch {
                    delay(((n - 1 - i) * motion.foldStaggerMs).toLong())
                    if (motion.foldMs == 0) prog[i].snapTo(0f)
                    else prog[i].animateTo(0f, androidx.compose.animation.core.tween(motion.foldMs, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                }
            }
        }
        onFolded(e)
    }
    val fill = Lex.colors.card
    val edge = BorderColor
    val half = PETAL_OUTER + PETAL_MARGIN
    // The labels are measured as drawn: each is sized (shrunk if need be) so it keeps its room to every edge of its petal.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val fontScale = dens.fontScale
    // One style for measuring and for drawing (the theme's own text style, merged once), and the text is DRAWN from the very layout that was
    // measured (drawText), so it cannot come out cut off or wider than the petal was fitted for.
    val base = androidx.compose.material3.LocalTextStyle.current
    val labelStyle = { sizeDp: Float ->
        base.merge(androidx.compose.ui.text.TextStyle(
            fontSize = (sizeDp / fontScale).sp, lineHeight = (sizeDp * LABEL_LINE_RATIO / fontScale).sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        ))
    }
    val blocks = remember(petals, fontScale, base) {
        val measure = { text: String, sizeDp: Float -> measurer.measure(text, labelStyle(sizeDp), softWrap = false, maxLines = 1).size.width / dens.density }
        petals.mapIndexed { i, p ->
            val fit = fitLabel(i, petals.size, p.label, fontScale, measure)
            val layout = measurer.measure(fit.lines.joinToString("\n"), labelStyle(fit.sizeDp), softWrap = false)
            val w = maxOf(ICON, layout.size.width / dens.density)
            val h = ICON + ICON_GAP + layout.size.height / dens.density
            Triple(fit.copy(width = w, height = h), layout, w)
        }
    }
    Box(
        Modifier.size(width = (2 * half).dp, height = (half + PETAL_BELOW).dp)
            .pointerInput(n) { detectTapGestures { o -> onTap(petalAt(o.x / density - half, o.y / density - half, n)) } },
    ) {
        for (i in 0 until n) {
            val p = petals[i]
            val mid = petalMid(i, n)
            val a = Math.toRadians(mid.toDouble())
            val rMid = (PETAL_INNER + PETAL_OUTER) / 2f
            val px = half + rMid * Math.cos(a).toFloat(); val py = half + rMid * Math.sin(a).toFloat()
            val hovered = hover == i
            val hoverScale by androidx.compose.animation.core.animateFloatAsState(
                if (hovered) 1.05f else 1f,
                if (motion.foldMs == 0) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
                label = "petal-hover",
            )
            val body by androidx.compose.animation.animateColorAsState(if (hovered) BarBlack else fill, androidx.compose.animation.core.tween(if (motion.foldMs == 0) 0 else 120), label = "petal-fill")
            val ink by androidx.compose.animation.animateColorAsState(if (hovered) OffWhite else PrimaryText, androidx.compose.animation.core.tween(if (motion.foldMs == 0) 0 else 120), label = "petal-ink")
            // Outer layer: out of the button along the petal's own angle (the button is the origin) and fading in; inner: the lift, about the petal's own middle.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val v = prog[i].value
                    scaleX = v; scaleY = v; alpha = (v * 2f).coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, half.dp.toPx() / size.height)
                },
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val s = hoverScale * lift[i].value
                        scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(px.dp.toPx() / size.width, py.dp.toPx() / size.height)
                    },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val cx = half.dp.toPx(); val cy = half.dp.toPx()
                        val r = PETAL_CORNER.dp.toPx(); val hair = 1.dp.toPx()
                        // The rounded segment: the shape inset by the corner radius, then thickened by it with round joins, so the four corners are round
                        // and the gap between neighbours is the same all along.
                        val rIn = PETAL_INNER.dp.toPx() + r; val rOut = PETAL_OUTER.dp.toPx() - r
                        val hIn = petalHalfSpan(rIn / density); val hOut = petalHalfSpan(rOut / density)
                        val path = androidx.compose.ui.graphics.Path().apply {
                            arcTo(androidx.compose.ui.geometry.Rect(cx - rOut, cy - rOut, cx + rOut, cy + rOut), mid - hOut, 2 * hOut, true)
                            arcTo(androidx.compose.ui.geometry.Rect(cx - rIn, cy - rIn, cx + rIn, cy + rIn), mid + hIn, -2 * hIn, false)
                            close()
                        }
                        val join = androidx.compose.ui.graphics.StrokeJoin.Round
                        // A quiet shadow under the arc: two faint, flat copies a little lower.
                        translate(0f, 4.dp.toPx()) {
                            drawPath(path, Color.Black.copy(alpha = 0.05f), style = androidx.compose.ui.graphics.drawscope.Stroke(2 * r + 8.dp.toPx(), join = join))
                        }
                        translate(0f, 2.dp.toPx()) {
                            drawPath(path, Color.Black.copy(alpha = 0.08f), style = androidx.compose.ui.graphics.drawscope.Stroke(2 * r + 3.dp.toPx(), join = join))
                        }
                        drawPath(path, edge, style = androidx.compose.ui.graphics.drawscope.Stroke(2 * r + 2 * hair, join = join))
                        drawPath(path, body, style = androidx.compose.ui.graphics.drawscope.Stroke(2 * r, join = join))
                        drawPath(path, body)
                    }
                    val (lx, ly) = petalCentroid(i, n)
                    val (blk, layout, _) = blocks[i]
                    Column(
                        Modifier.offset((half + lx - blk.width / 2f).dp, (half + ly - blk.height / 2f).dp).size(blk.width.dp, blk.height.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top,
                    ) {
                        val icon = when (p) { Petal.OpenAccount -> Icons.Outlined.HowToReg; Petal.RegisterCounsel -> Icons.Outlined.Gavel; Petal.DiscussMatter -> Icons.Outlined.Forum }
                        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(ICON.dp))
                        Spacer(Modifier.height(ICON_GAP.dp))
                        // Drawn from the measured layout itself: shrunk until every edge has its room, never clipped.
                        Spacer(Modifier.size(blk.width.dp, (layout.size.height / dens.density).dp).drawBehind {
                            drawText(layout, color = ink, topLeft = androidx.compose.ui.geometry.Offset((size.width - layout.size.width) / 2f, 0f))
                        })
                    }
                }
            }
        }
    }
}

/** The one Debug chip: opens a menu of the test sign-ins. Debug builds only (the caller passes nothing otherwise). */
@Composable
private fun DebugChip(items: List<DebugSignIn>, enabled: Boolean, onPick: (DebugSignIn) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val shape = RoundedCornerShape(8.dp)
        Text(
            "Debug", style = MaterialTheme.typography.labelSmall, color = SecondaryText,
            modifier = Modifier.clip(shape).border(1.dp, BorderColor, shape).clickable(enabled = enabled) { open = true }.padding(horizontal = 10.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { d -> DropdownMenuItem(text = { Text(d.label) }, onClick = { open = false; onPick(d) }) }
        }
    }
}

/**
 * The mobile number, with its country code and a phone icon, and the round arrow that sends the code. An Indian number
 * is ten digits after +91; a number outside India is typed whole, with its own code.
 */
@Composable
private fun NumberRow(vm: LoginViewModel, mobile: MobileState, busy: Boolean) {
    val spacing = LocalSpacing.current
    var abroad by rememberSaveable { mutableStateOf(mobile.number.startsWith("+") && !mobile.number.startsWith("+91")) }
    val local = localTenDigits(mobile.number)
    val ready = numberReady(abroad, mobile.number)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.weight(1f).height(52.dp).clip(shape).background(Lex.colors.background).border(1.dp, BorderColor, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_whatsapp_mark), contentDescription = null, tint = SecondaryText, modifier = Modifier.size(22.dp))
            if (!abroad) Text("+91", style = MaterialTheme.typography.titleMedium, color = SecondaryText)
            BasicTextField(
                value = if (abroad) mobile.number else local,
                onValueChange = { vm.onNumberChange(if (abroad) it else it.filter(Char::isDigit).take(10)) },
                enabled = !busy, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (ready && !busy) vm.onSendCode() }),
                textStyle = MaterialTheme.typography.titleMedium.copy(color = PrimaryText),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(PrimaryText),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Mobile number" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if ((if (abroad) mobile.number else local).isEmpty()) Text(if (abroad) "+44 7700 900123" else "Mobile number", style = MaterialTheme.typography.titleMedium, color = TertiaryText)
                        inner()
                    }
                },
            )
        }
        // The round arrow: sends the code.
        RoundArrow(label = "Send code", enabled = ready && !busy, busy = busy) { vm.onSendCode() }
    }
    Spacer(Modifier.height(spacing.s2))
    Text(MOBILE_HINT, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    LinkButton(
        text = if (abroad) "An Indian Number" else "Number Outside India?", enabled = !busy, flush = true,
        onClick = { if (abroad) { abroad = false; vm.onNumberChange("") } else { abroad = true; vm.onNumberChange("+") } },
    )
}

/** The card's title line. */
@Composable
private fun ColumnScope.CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = PrimaryText, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(LocalSpacing.current.s5))
}

/** A small outlined pill: a left arrow and "Back", in the card's text colour with a hairline, as the other pills. */
@Composable
private fun BackPill(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, BorderColor, shape)
            .clickable(enabled = enabled, onClickLabel = "Back", onClick = onClick)
            .semantics { contentDescription = "Back" }
            .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(18.dp))
        Text("Back", style = MaterialTheme.typography.labelLarge, color = PrimaryText)
    }
}

/**
 * Lays out [content], at least as tall as [reference] would be (the reference is composed and measured but never shown). With [enabled] false
 * it is just [content]. Used so the number view's card is as tall as the Email view's.
 */
@Composable
private fun EqualHeight(enabled: Boolean, reference: @Composable () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { c ->
        val loose = c.copy(minHeight = 0)
        val refH = if (enabled) subcompose("reference", reference).maxOfOrNull { it.measure(loose).height } ?: 0 else 0
        val body = subcompose("body", content).map { it.measure(loose) }
        val w = maxOf(c.minWidth, body.maxOfOrNull { it.width } ?: 0)
        val h = maxOf(body.maxOfOrNull { it.height } ?: 0, refH)
        layout(w, h) { body.forEach { it.placeRelative(0, 0) } }
    }
}

/** The round arrow both ways in share: the number row sends the code, the email form signs in. */
@Composable
private fun RoundArrow(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (enabled) AccentInk else Lex.colors.muted)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = TertiaryText)
        else Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = if (enabled) InverseText else TertiaryText, modifier = Modifier.size(22.dp))
    }
}

/** The ten local digits of an Indian number as the view model holds it (with +91, 91 or 0 in front dropped). */
internal fun localTenDigits(number: String): String = number.filter(Char::isDigit).let {
    when {
        it.length == 12 && it.startsWith("91") -> it.drop(2)
        it.length == 11 && it.startsWith("0")  -> it.drop(1)
        else -> it
    }
}.take(10)

/** Whether the number can be sent: ten digits for India, eight or more with a country code abroad. */
internal fun numberReady(abroad: Boolean, number: String): Boolean =
    if (abroad) number.count(Char::isDigit) >= 8 else number.length == 10


@Composable
private fun ColumnScope.EmailView(vm: LoginViewModel, busy: Boolean, onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val fpCtx = androidx.compose.ui.platform.LocalContext.current

    LegumTextField(
        value = email,
        onValueChange = { email = it },
        label = "Email",
        leadingIcon = Icons.Outlined.Mail,
        keyboardType = KeyboardType.Email,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(spacing.s3))

    LegumTextField(
        value = password,
        onValueChange = { password = it },
        label = "Password",
        leadingIcon = Icons.Outlined.Lock,
        isPassword = !showPassword,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        onImeAction = { if (!busy && email.isNotBlank() && password.isNotEmpty()) vm.onEmailLogin(email.trim(), password) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = TertiaryText,
                    )
                }
                // The portal's own reset: a link to the address on file.
                val shape = RoundedCornerShape(50)
                Text(
                    "I Forgot", style = MaterialTheme.typography.labelMedium, color = PrimaryText,
                    modifier = Modifier.padding(end = 6.dp).clip(shape).border(1.dp, BorderColor, shape)
                        .clickable(enabled = !busy) { openSite(fpCtx, "authentication/forgot_password") }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        },
    )

    Spacer(Modifier.height(spacing.s4))

    // One tidy row: Back on the left, the round sign-in arrow on the right.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackPill(enabled = !busy, onClick = onBack)
        Spacer(Modifier.weight(1f))
        RoundArrow(label = "Sign in", enabled = email.isNotBlank() && password.isNotBlank() && !busy, busy = busy) { vm.onEmailLogin(email.trim(), password) }
    }
}

// The code step of the mobile sign-in: the 6-digit code sent on WhatsApp. After 30 seconds the
// server may offer the same code by SMS; after a minute a new code can be asked for.
@Composable
private fun ColumnScope.CodeStep(vm: LoginViewModel, mobile: MobileState, busy: Boolean) {
    val spacing = LocalSpacing.current
    var code by remember(mobile.ref) { mutableStateOf("") }

    Text(mobile.sentNote, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    Spacer(Modifier.height(spacing.s3))
    mobile.info?.let {
        Banner(message = it, palette = StatusSettled)
        Spacer(Modifier.height(spacing.s3))
    }
    DigitBoxes(
        value = code,
        length = 6,
        onValueChange = {
            code = it
            vm.clearError()
            if (code.length == 6 && !busy) vm.onCode(code)
        },
        enabled = !busy,
        keyboardType = KeyboardType.NumberPassword,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        LinkButton(text = "Change Number", enabled = !busy, onClick = vm::changeNumber)
    }
    Spacer(Modifier.height(spacing.s2))
    LegumPrimaryButton(
        text    = if (busy) "Signing in…" else "Sign In",
        onClick = { vm.onCode(code) },
        enabled = code.length == 6 && !busy,
    )
    Spacer(Modifier.height(spacing.s3))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mobile.canSms) {
            LinkButton(text = "Send by SMS Instead", enabled = !busy, onClick = vm::onSendBySms)
            Spacer(Modifier.width(spacing.s4))
        }
        LinkButton(
            text    = if (mobile.resendIn > 0)
                "Send a New Code in %d:%02d".format(mobile.resendIn / 60, mobile.resendIn % 60)
            else "Send a New Code",
            enabled = mobile.resendIn == 0 && !busy,
            onClick = vm::onSendCode,
        )
    }
}

/**
 * Square boxes, one digit each, over one hidden field: typing, deleting and
 * pasting work as in a field, and the box next to be filled is outlined.
 */
@Composable
private fun DigitBoxes(
    value: String,
    length: Int,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    groupAfter: Int = 0,
    digitStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        modifier = modifier.focusRequester(focus),
        decorationBox = { inner ->
            Box {
                // The real field sits under the boxes, invisible, to take input.
                Box(Modifier.size(1.dp)) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until length) {
                        if (groupAfter > 0 && i == groupAfter) Spacer(Modifier.width(6.dp))
                        val ch = value.getOrNull(i)?.toString() ?: ""
                        val next = i == value.length && enabled
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(com.legumlex.client.ui.theme.Lex.colors.card)
                                .border(
                                    if (next) 1.5.dp else 1.dp,
                                    if (next) PrimaryText else BorderColor,
                                    MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, style = digitStyle, color = PrimaryText)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LinkButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** True: the text starts at the left edge of its column (no button inset), to line up with the text above. */
    flush: Boolean = false,
) {
    TextButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        contentPadding = if (flush) PaddingValues(horizontal = 0.dp, vertical = 8.dp) else ButtonDefaults.TextButtonContentPadding,
        colors   = ButtonDefaults.textButtonColors(contentColor = AccentInk, disabledContentColor = TertiaryText),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Banner(message: String, palette: StatusPalette, onDismiss: (() -> Unit)? = null) {
    Surface(
        color = palette.softBg,
        contentColor = palette.softText,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.softText,
                modifier = Modifier.weight(1f),
            )
            onDismiss?.let {
                TextButton(
                    onClick = it,
                    colors  = ButtonDefaults.textButtonColors(contentColor = palette.softText),
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// --- Credential Manager / Google ID token retrieval ---

internal suspend fun requestGoogleIdToken(
    context:        Context,
    activity:       ComponentActivity,
    serverClientId: String,
    onToken:        (String) -> Unit,
    onError:        (String) -> Unit,
    onCancel:       () -> Unit,
) {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(activity, request)
        val cred = result.credential
        if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                onToken(googleCred.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                onError("Google sign-in could not be completed. Please try again.")
            }
        } else {
            onError("Unexpected credential type. Please try again.")
        }
    } catch (_: GetCredentialCancellationException) {
        onCancel()
    } catch (_: NoCredentialException) {
        onError("Google sign-in is not available on this device. Please sign in with your mobile or email.")
    } catch (e: GetCredentialException) {
        onError(e.message ?: "Google sign-in failed.")
    }
}

/** The activity behind a context, for the Google request elsewhere (the desk). */
internal object LoginHost { fun Context.host(): ComponentActivity = requireActivity() }

internal fun Context.requireActivity(): ComponentActivity {
    var ctx: Context = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("LoginScreen must be hosted in a ComponentActivity")
}


/** A page of the Legum Lex site, beside the API, in the browser. */
internal fun openSite(context: android.content.Context, path: String) {
    val site = com.legumlex.client.BuildConfig.API_BASE_URL.substringBefore("customers_api/")
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(site + path)))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No browser is installed.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun prefsStore(context: Context, name: String) = object : com.legumlex.client.feature.desk.KeyValueStore {
    private val p = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String) = p.getString(key, null)
    override fun put(key: String, value: String) { p.edit().putString(key, value).apply() }
    override fun remove(key: String) { p.edit().remove(key).apply() }
}

/**
 * The first-time hint: a small tip above the Lex button, centred on it, with a pointer down to it, that types its lines out (all at once with reduced
 * motion). Sized to its full text from the start, so it does not grow as it types and each line fits on one line (it wraps only if the screen is truly
 * too narrow). The accent fill with the accent's own foreground (white on black, black on light), Mylex's 8 dp radius; the second line a size smaller at
 * [HINT_SECOND_ALPHA] of the same ink. A tap dismisses it for good. TalkBack gets both lines, announced politely.
 */
@Composable
private fun OrbHintBubble(lines: List<String>, reduced: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val total = lines.sumOf { it.length }
    var typed by remember(lines, reduced) { mutableIntStateOf(if (reduced) total else 0) }
    LaunchedEffect(lines, reduced) {
        if (reduced) return@LaunchedEffect
        delay(700)
        var n = 0
        while (n < total) { delay(if (n == lines[0].length) 420 else 42); n++; typed = n }
    }
    val c = Lex.colors
    val fill = deskFill(c, DeskSurface.Accent)
    val ink = deskInk(c, DeskSurface.Accent, DeskRole.Body)
    val shown = hintTyped(lines, typed)
    val said = lines.joinToString(". ")
    @Composable fun block(texts: List<String>, visible: Boolean) = Column(Modifier.clearAndSetSemantics { }) {
        texts.forEachIndexed { i, t ->
            val second = i > 0
            Text(t.ifEmpty { " " }, color = if (!visible) Color.Transparent else if (second) ink.copy(alpha = HINT_SECOND_ALPHA) else ink,
                fontSize = if (second) 11.sp else 12.sp, lineHeight = if (second) 14.sp else 15.sp,
                fontWeight = if (second) androidx.compose.ui.text.font.FontWeight.Normal else androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
    Column(
        modifier.clickable(onClick = onTap).semantics(mergeDescendants = true) { contentDescription = said; liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(fill).padding(horizontal = 12.dp, vertical = 8.dp)) {
            block(lines, visible = false)          // the whole text, unseen, fixes the size from the first moment
            block(shown.mapIndexed { i, t -> if (t.isEmpty() && i < lines.size) "" else t }, visible = true)
        }
        // The pointer down to the button.
        androidx.compose.foundation.Canvas(Modifier.size(width = 12.dp, height = HINT_POINTER.dp)) {
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }
            drawPath(p, fill)
        }
    }
}
