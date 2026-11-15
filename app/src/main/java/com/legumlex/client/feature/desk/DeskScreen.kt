package com.legumlex.client.feature.desk

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.legumlex.client.ui.theme.Lex
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================================================================
 *  The Legum Lex desk (Consult) on a phone: the website's "Lex Intelligence"
 *  drawer (cases/views/public/site/partials/desk_panel.php around
 *  partials/reception.php), in its own colours whatever the phone's theme,
 *  as the drawer keeps them whatever the site's.
 *
 *  Before the first question: the drawer's intro (what has happened, where to
 *  start, the three steps), the composer, and its services (have an agreement
 *  read, track a notice). After: the conversation, and below it whatever the
 *  desk is asking for.
 * ========================================================================== */

/**
 * The desk's colours, from the app's palette (owner, 2026-10-20: the desk
 * obeys the app theme). The names stay as the desk's layout uses them; the
 * former navy and gold are gone — the accent is the theme's ink.
 */
private object D {
    val Page: Color   @Composable get() = Lex.colors.background
    val Ink: Color    @Composable get() = Lex.colors.foreground
    val Me: Color     @Composable get() = Lex.colors.muted
    val MeInk: Color  @Composable get() = Lex.colors.foreground
    val Line: Color   @Composable get() = Lex.colors.border
    val Line2: Color  @Composable get() = Lex.colors.input
    val Raise: Color  @Composable get() = Lex.colors.card
    val Mark: Color   @Composable get() = Lex.colors.secondary
    val Gold: Color   @Composable get() = Lex.colors.primary
    val OnGold: Color @Composable get() = Lex.colors.primaryForeground
    val Live: Color   @Composable get() = Lex.colors.success.solid
    val Warn: Color   @Composable get() = Lex.colors.danger.solid
    val Muted: Color  @Composable get() = Lex.colors.mutedForeground
    val Quiet: Color  @Composable get() = Lex.colors.faint
    /** Text colour for [role] on [surface], from the palette in force (DeskInk.kt). */
    @Composable fun ink(surface: DeskSurface, role: DeskRole = DeskRole.Body): Color = deskInk(Lex.colors, surface, role)
}

private val Mono = FontFamily.Monospace

/** The language's fixed labels (REQ-017 017.31): buttons, placeholders and screen-reader texts come from it, English when it has no such key. */
internal val LocalDeskBook = androidx.compose.runtime.compositionLocalOf { LineBook.English }

@Composable
private fun dt(key: String, english: String): String = LocalDeskBook.current.fragment(key) ?: english

/** Whether the desk's boxes take the focus on arrival: yes on the full screen, no in the Sign In chat panel. */
internal val LocalDeskAutoFocus = androidx.compose.runtime.compositionLocalOf { true }

@Composable
private fun body(size: Float = 14.5f, color: Color = D.Ink, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = Inter, fontSize = size.sp, lineHeight = (size * 1.55f).sp, color = color, fontWeight = weight,
        // Room above and below every line, none trimmed: Gurmukhi and Devanagari marks hang well below the baseline and rise above the line.
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None),
    )

private val EXAMPLES = listOf("A cheque I got bounced…", "My tenant owes me rent…", "I got a legal notice…",
    "I want to set up a firm…", "My boss has not paid me…", "Someone took my land…")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeskScreen(
    onClose: () -> Unit,
    onOpenMatters: () -> Unit,
    onOpenConsultation: (Int) -> Unit,
    onAgreement: () -> Unit,
    /** The portal route's button: My Agreements. */
    onAgreements: () -> Unit = {},
    /** Opening an account (LeadHome's Open An Account, Fix And Resubmit). */
    account: Boolean = false,
    onOpenForm: () -> Unit = {},
    /** The counsel registration screen (chip href app:lead_counsel), and the app opening as the client once the desk opened the Counsel workspace. */
    onOpenCounselForm: () -> Unit = {},
    onOpenedAsClient: () -> Unit = {},
    onHome: () -> Unit = onClose,
    onSignIn: () -> Unit = onClose,
) {
    // From the Sign In chat panel the conversation arrives with it (DeskHandover); otherwise a fresh desk.
    // Closing the desk keeps the conversation for this app session (as the web keeps its page state when the desk is closed and opened again): the view model lives with the
    // activity, under a key that changes with the signed-in person and after a finished enquiry. An account entry is fresh each time.
    val deskContext = androidx.compose.ui.platform.LocalContext.current
    val vm: DeskViewModel = remember { DeskHandover.take() } ?: if (account) viewModel() else {
        val key = remember { DeskKeep.key(deskContext) }
        viewModel(viewModelStoreOwner = deskActivity(deskContext), key = key)
    }
    val ui by vm.ui.collectAsState()
    var tracking by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The fee is paid on the website: look again on coming back.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.resumed()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                DeskEvent.OpenMatters -> onOpenMatters()
                is DeskEvent.OpenConsultation -> onOpenConsultation(e.id)
                is DeskEvent.OpenUrl -> runCatching {
                    // A fee's invoice is paid inside the app; any other link opens in the browser.
                    if (e.url.contains("/invoice/") || e.url.contains("/lead_pay/")) com.legumlex.client.feature.finance.PayActivity.open(context, e.url)
                    else context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.url)))
                }
                DeskEvent.OpenForm -> onOpenForm()
                DeskEvent.OpenCounselForm -> onOpenCounselForm()
                DeskEvent.OpenedAsClient -> onOpenedAsClient()
                DeskEvent.Home -> onHome()
                DeskEvent.SignIn -> onSignIn()
                DeskEvent.OpenAgreementCheck -> onAgreement()
                DeskEvent.OpenAgreements -> onAgreements()
            }
        }
    }

    // A guest signs in with Google in the conversation, as on the website: the
    // sign-in screen's own Google request, and its view model's exchange.
    val login: com.legumlex.client.feature.auth.LoginViewModel = viewModel()
    val loginState by login.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(loginState) {
        when (val st = loginState) {
            is com.legumlex.client.feature.auth.LoginState.Success -> vm.signedIn(st.result.email, st.result.name)
            is com.legumlex.client.feature.auth.LoginState.Error -> vm.signInProblem(st.message)
            else -> Unit
        }
    }
    val google: () -> Unit = {
        login.clearError()
        scope.launch {
            val id = kotlin.runCatching { com.legumlex.client.data.api.ApiClient.get(context).googleConfig()["client_id"] as? String }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: com.legumlex.client.BuildConfig.GOOGLE_WEB_CLIENT_ID
            com.legumlex.client.feature.auth.requestGoogleIdToken(
                context = context, activity = with(com.legumlex.client.feature.auth.LoginHost) { context.host() }, serverClientId = id,
                onToken = login::onIdTokenReceived, onError = login::onCredentialError, onCancel = login::onCancelled,
            )
        }
    }

    // Debug builds only, and only with the test contact configured: the same sign-in the sign-in screen's debug chip makes. Its Success is the same LoginState.Success a Google
    // sign-in ends in, so the desk goes on through the same vm.signedIn() (resume included). Never in a release build.
    val testContact: (() -> Unit)? = if (deskTestContactOn(com.legumlex.client.BuildConfig.DEBUG, com.legumlex.client.BuildConfig.DEV_LOGIN_EMAIL, com.legumlex.client.BuildConfig.DEV_LOGIN_PASSWORD))
        ({ login.clearError(); login.onEmailLogin(com.legumlex.client.BuildConfig.DEV_LOGIN_EMAIL, com.legumlex.client.BuildConfig.DEV_LOGIN_PASSWORD) }) else null

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    // A document for the account: chosen, or scanned (the Aadhaar's two sides as one PDF).
    val acctPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.acctUpload(uri) }
    val acctScan = com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher { uri -> if (uri != null) vm.acctUpload(uri) }
    val mimes = remember(ui.terms.documentTypes) { mimesFor(ui.terms.documentTypes) }
    val attach: (() -> Unit)? = if (ui.terms.takesFiles) ({ pick.launch(mimes) }) else null

    // The question's draft lives here, so a "Change" can hand it back; a
    // starter or a "Change" puts the cursor at its end and the focus on it.
    var draft by remember { mutableStateOf(TextFieldValue(vm.draftText, TextRange(vm.draftText.length))) }
    // The unsent question is kept with the conversation (the web keeps it as lex_desk_draft until it is sent).
    LaunchedEffect(draft.text) { vm.draftText = draft.text }
    // The message has gone: the box empties, as every composer does on a successful send (the web clears its box too). A refused send never counts, so its text stays.
    LaunchedEffect(ui.draftClearTick) { if (ui.draftClearTick > 0) draft = TextFieldValue("") }
    val compose = remember { FocusRequester() }
    var focusTick by remember { mutableIntStateOf(0) }
    val put: (String) -> Unit = { draft = TextFieldValue(it, TextRange(it.length)); focusTick++ }
    LaunchedEffect(ui.prefillTick) { if (ui.prefillTick > 0) put(ui.prefill) }
    LaunchedEffect(focusTick) { if (focusTick > 0) { delay(80); runCatching { compose.requestFocus() } } }

    val typing = WindowInsets.isImeVisible
    var services by remember { mutableStateOf(false) }
    // Opening or closing the curtain must not hand focus to the question box,
    // or the keyboard springs up under the client's thumb.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val showServices: (Boolean) -> Unit = { focusManager.clearFocus(force = true); services = it }
    androidx.activity.compose.BackHandler(enabled = services) { showServices(false) }
    // The keyboard put away is the box let go: it returns to rest.
    var hadKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(typing) {
        if (typing) hadKeyboard = true else if (hadKeyboard) { hadKeyboard = false; focusManager.clearFocus() }
    }
    Column(Modifier.fillMaxSize().background(D.Page).imePadding()) {
        Head(onClose)
        if (!ui.opened) {
            // The services hang from the header like a curtain: pulled down,
            // they push the desk down beneath them.
            Curtain(
                open = services && !typing,
                firm = ui.terms.firm,
                onToggle = { showServices(it) },
                // The Agreement Check needs a sign-in: a guest is asked for it here.
                onReview = { showServices(false); if (vm.guest) vm.guestSignIn() else onAgreement() },
                onTrack = { showServices(false); tracking = true },
            )
            // The intro at the top, the middle left empty, the composer at the
            // foot, as a chat is laid out.
            // The three steps sit at the foot, just above the message box.
            Column(Modifier.weight(1f)) {
                // The intro scrolls above the steps; where more lies below, the edge fades (no row is cut off without a cue).
                val introScroll = rememberScrollState()
                Box(Modifier.weight(1f)) {
                    Column(Modifier.verticalScroll(introScroll)) {
                        DeskIntro(vm, ui)
                        SuggestedTopics(onTopic = put)
                        Spacer(Modifier.height(28.dp))
                    }
                    if (introScroll.canScrollForward) Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, D.Page))),
                    )
                }
                if (!typing) {
                    Process()
                    Spacer(Modifier.height(12.dp))
                }
            }
            DeskOpening(vm, ui, draft, { draft = it }, compose, attach, onFocus = { if (it && services) services = false })
            if (ui.termsLoaded) Disclaimer(ui.terms, compact = true, direct = ui.footerDirect, fee = ui.directFee, hidden = ui.footerUnread)
        } else {
            DeskThread(
                vm, ui, draft, { draft = it }, compose, attach,
                signingIn = loginState is com.legumlex.client.feature.auth.LoginState.Loading, google = google,
                acctScan = { acctScan() }, acctPick = { acctPick.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                fullDesk = null, modifier = Modifier.weight(1f), testContact = testContact,
            )
        }
    }

    if (tracking) TrackSheet(onDismiss = { tracking = false })
}

private fun mimesFor(types: List<String>): Array<String> = types.mapNotNull {
    when (it) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> null
    }
}.distinct().ifEmpty { listOf("*/*") }.toTypedArray()

// ---- The drawer's head and foot ---------------------------------------------

@Composable
internal fun Head(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(D.Gold), contentAlignment = Alignment.Center) {
            Text(">_", style = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = D.OnGold))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Lex Intelligence", style = body(16.5f, weight = FontWeight.SemiBold).copy(lineHeight = 20.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(D.Live))
                Spacer(Modifier.width(6.dp))
                Text("counsel desk · taking enquiries", style = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, color = D.Muted))
            }
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Close the desk", tint = D.Muted, modifier = Modifier.size(22.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
}

@Composable
private fun Foot(firm: String) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
    Text("Legum Lex is not a law firm. What you write here goes to the advocates of " + firm.ifBlank { "the firm" } + if (firm.endsWith(".")) "" else ".",
        style = body(11.5f, D.Ink.copy(alpha = 0.58f)), modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun Kicker(s: String, modifier: Modifier = Modifier) =
    Text("// $s", style = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = D.Muted), modifier = modifier)

// ---- Before the first question --------------------------------------------------

@Composable
private fun Intro(guest: Boolean = false, compact: Boolean = false) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = if (compact) 12.dp else 22.dp)) {
        Text(if (guest) LEX_INTRO_HEADING else "What has happened?", style = body(if (compact) 19f else 25f, weight = FontWeight.SemiBold).copy(lineHeight = if (compact) 24.sp else 30.sp, letterSpacing = (-0.4).sp))
        Spacer(Modifier.height(8.dp))
        Text(if (guest) LEX_INTRO_BODY
             else "In your own words: what happened, who is involved, and what you would like counsel to do.", style = body(14.5f, D.Ink.copy(alpha = 0.7f)))
    }
}

/** The drawer's three steps, and the status line that walks them. */
@Composable
private fun Process() {
    val steps = listOf(
        Triple(Icons.Outlined.Hub, "Analysed by AI", "> analysing your enquiry"),
        Triple(Icons.Outlined.HowToReg, "Reviewed by an advocate", "> routing to an advocate for review"),
        Triple(Icons.Outlined.Timer, "Reply aimed in 30 minutes", "> we aim to reply within 30 minutes once an advocate accepts it"),
    )
    var at by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_600); at = (at + 1) % steps.size } }
    // At 70% of its first size, width and fonts included (owner, 2026-10-20).
    Column(Modifier.fillMaxWidth().padding(horizontal = 56.dp)) {
        Box(Modifier.fillMaxWidth()) {
            val rail = D.Line2; val fill = D.Gold
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val y = size.height / 2
                drawLine(rail, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                drawLine(fill, Offset(0f, y), Offset(size.width * (at * 2 + 1) / 6f, y), 1.5.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                steps.forEachIndexed { i, (icon, _, _) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(D.Page)
                                .border(1.dp, if (i == at) D.Gold else D.Line2, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = if (i == at) D.Gold else D.Muted, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, (_, label, _) ->
                Text(label, style = body(9.1f, if (i == at) D.Ink else D.Muted, FontWeight.SemiBold).copy(lineHeight = 12.sp),
                    textAlign = TextAlign.Center, minLines = 2, maxLines = 2, modifier = Modifier.weight(1f).padding(horizontal = 3.dp))   // equal label heights: the three steps share one baseline
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(steps[at].third, style = TextStyle(fontFamily = Mono, fontSize = 8.75.sp, color = D.Gold), textAlign = TextAlign.Center, minLines = 2, maxLines = 2, modifier = Modifier.fillMaxWidth())   // a fixed two-line caption: the layout does not jump as the line changes
    }
}

@Composable
private fun Disclaimer(t: DeskTerms, compact: Boolean = false, direct: Boolean = false, fee: String = "", hidden: Boolean = false) {
    // While a message is unread nothing is offered, so no fee and no 30-minute line (web, F-017).
    if (hidden) return
    Text(
        buildAnnotatedString {
            // The web bolds the lead sentence (reception.php .rcp-disclaim b): web-first parity.
            val book = LocalDeskBook.current
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = D.Ink)) { append(disclaimerLead(t, direct, fee, book)) }
            append(disclaimerTail(direct, book))
        },
        style = body(if (compact) 11.5f else 13f, D.Muted).copy(lineHeight = if (compact) 16.sp else 19.sp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (compact) 8.dp else 16.dp),
    )
}

/**
 * The services, hung from the header's rule like a curtain: a curved tab to
 * pull down (or tap), the panel dropping open and pushing the desk down under
 * it; pull up, tap the tab or go back to draw it again.
 */
@Composable
private fun Curtain(open: Boolean, firm: String, onToggle: (Boolean) -> Unit, onReview: () -> Unit, onTrack: () -> Unit) {
    var pulled by remember { mutableStateOf(0f) }
    val drag = Modifier.pointerInput(open) {
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onVerticalDrag = { _, dy ->
                pulled += dy
                if (!open && pulled > 24.dp.toPx()) { pulled = 0f; onToggle(true) }
                if (open && pulled < -24.dp.toPx()) { pulled = 0f; onToggle(false) }
            },
        )
    }
    Column(Modifier.fillMaxWidth().then(drag)) {
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.expandVertically(tween(280), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.shrinkVertically(tween(240), shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth().background(D.Raise)) {
                Kicker("services", Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp))
                Service(Icons.Outlined.Description, "Review", "An advocate reads your agreement for risk, before you sign it.", onReview)
                Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(D.Line))
                Service(Icons.Outlined.LocalShipping, "Track", "Follow a legal notice sent for you, from its postal reference.", onTrack)
                Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
                Text("Legum Lex is not a law firm. What you write here goes to the advocates of " + firm.ifBlank { "the firm" } + if (firm.endsWith(".")) "" else ".",
                    style = body(11.5f, D.Ink.copy(alpha = 0.58f)), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        // The curtain's hem: a soft swag hanging from the rule, the tab in it.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(196.dp).height(34.dp)
                    .clip(CurtainHem)
                    .background(D.Raise)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onToggle(!open) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Services", style = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (open) D.Gold else D.Muted))
                    Spacer(Modifier.width(4.dp))
                    Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                        tint = if (open) D.Gold else D.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A curtain's swag: flat along the rule, curving down to a rounded hem and back. */
private val CurtainHem = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(0f, 0f)
    cubicTo(w * 0.16f, 0f, w * 0.18f, h, w * 0.34f, h)
    lineTo(w * 0.66f, h)
    cubicTo(w * 0.82f, h, w * 0.84f, 0f, w, 0f)
    close()
}

/** One service: a single-word name and two lines on what it does. */
@Composable
private fun Service(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = D.Gold, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = body(15.5f, weight = FontWeight.SemiBold).copy(lineHeight = 20.sp))
            Spacer(Modifier.height(2.dp))
            Text(sub, style = body(12.5f, D.Muted).copy(lineHeight = 17.sp), minLines = 2, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = D.Muted, modifier = Modifier.size(20.dp))
    }
}

// ---- The conversation ---------------------------------------------------------------

@Composable
private fun Conversation(ui: DeskUi, onChange: (Step) -> Unit, onAcct: (AcctChip) -> Unit, modifier: Modifier, onPick: (String) -> Unit = {}) {
    val list = rememberLazyListState()
    // Each desk line's words come in one after another, once.
    val revealed = remember { mutableStateMapOf<Long, Boolean>() }
    val cards = (ui.dock as? Dock.Cards)?.block
    LaunchedEffect(ui.lines.size, ui.typing, cards != null) {
        val n = ui.lines.size + (if (ui.typing) 1 else 0) + (if (cards != null) 1 else 0)
        if (n > 0) {
            list.animateScrollToItem(n - 1)
            // The last line may be taller than the room: go on to the very end, so its foot (and a Gurmukhi line's lower marks) are in view.
            list.animateScrollBy(1_000_000f)
        }
    }
    // The room the conversation has changes when a dock appears or grows (the waiting card, its hand-over, the keyboard): the end stays in view, so the last
    // line is never left under, or cut by, what sits below it. The dock is a sibling below the list, not drawn over it, so no padding is needed.
    val lineCount by androidx.compose.runtime.rememberUpdatedState(ui.lines.size)
    LaunchedEffect(list) {
        androidx.compose.runtime.snapshotFlow { list.layoutInfo.viewportSize.height }.collect {
            if (lineCount > 0) { list.scrollToItem(lineCount - 1); list.animateScrollBy(1_000_000f) }
        }
    }
    // A line that is still being revealed (word by word) or a card that grows after the last scroll keeps the end in view, until the visitor scrolls up on purpose.
    var stuck by remember { mutableStateOf(true) }
    val follow = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y > 0f) stuck = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(list) {
        androidx.compose.runtime.snapshotFlow { list.canScrollForward }.collect { more ->
            if (!more) stuck = true else if (stuck && lineCount > 0) list.scrollBy(1_000_000f)
        }
    }
    LazyColumn(modifier.fillMaxWidth().nestedScroll(follow), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = DESK_SIDE_MARGIN.dp, end = DESK_SIDE_MARGIN.dp, top = 16.dp, bottom = DESK_BOTTOM_ROOM.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(ui.lines.size, key = { ui.lines[it].id }) { i ->
            val line = ui.lines[i]
            when (line.who) {
                Who.ME -> MeLine(line, !ui.locked && line.step != null) { line.step?.let(onChange) }
                // The desk's mark heads each run of what it says, not every line.
                Who.DESK -> DeskLine(line, revealed, mark = i == 0 || ui.lines[i - 1].who != Who.DESK, onAcct = onAcct)
                Who.NOTE -> NoteLine(line)
            }
        }
        if (ui.typing) item(key = "typing") { Typing() }
        // The cards are part of the conversation, as on the web (dockChoice appends them to the log): tall content scrolls with it, and only the box and Not Now sit in the dock.
        if (cards != null) item(key = "cards") { CardsList(cards, onPick) }
    }
}

@Composable
private fun DeskMark() {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(D.Mark), contentAlignment = Alignment.Center) {
        Text(">_", style = TextStyle(fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = D.Gold))
    }
}

@Composable
private fun DeskLine(line: Line, revealed: SnapshotStateMap<Long, Boolean>, mark: Boolean, onAcct: (AcctChip) -> Unit = {}) {
    // The words, one after another (the page's rcp-w): 26ms apart.
    val total = line.paras.sumOf { (it as? Para.Text)?.s?.split(' ')?.size ?: 0 }
    var shown by remember(line.id) { mutableIntStateOf(if (revealed[line.id] == true) total else 0) }
    LaunchedEffect(line.id) {
        while (shown < total) { delay(26); shown++ }
        revealed[line.id] = true
    }
    Row(Modifier.fillMaxWidth()) {
        if (mark) DeskMark() else Spacer(Modifier.width(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var before = 0
            line.paras.forEach { p ->
                when (p) {
                    is Para.Text -> {
                        val words = p.s.split(' ').size
                        val upto = (shown - before).coerceIn(0, words)
                        before += words
                        Words(p.s, upto, p.flag)
                    }
                    is Para.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.items.forEach { Row { Text("–  ", style = body(color = D.Gold)); Text(it, style = body()) } }
                    }
                    is Para.Facts -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        p.rows.forEach { (k, v) ->
                            Column {
                                Text(k.uppercase(), style = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = D.Muted))
                                Text(v, style = body(14f))
                            }
                        }
                    }
                    is Para.Stages -> Stages(p)
                    is Para.Sent -> SendToAdvocate(p.caption, p.time)
                    Para.Seal -> Seal()
                    is Para.Summary -> AcctSummary(p, onAcct)
                    is Para.Divider -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(D.Line2))
                        Text(p.s, style = body(12f, D.Muted), modifier = Modifier.padding(horizontal = 10.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(D.Line2))
                    }
                }
            }
            // Translated lines carry the footer (REQ-017 017.26): very small, Secondary ink, the English first and then the local line.
            line.footer?.let { DeskFooterText(it) }
        }
    }
}

/**
 * A translated line carries only a small tag (the footer's own label, 9 sp, Secondary ink) with an information icon; tapping it opens the full disclaimer
 * (the English, then the local line) in a small dialog. The disclaimer is never shown inline.
 */
/** The strip the tag takes in the line (dp); its tappable row is 48 dp and overflows it evenly. */
private const val TAG_STRIP_HEIGHT = 20

@Composable
private fun DeskFooterText(f: DeskFooter, surface: DeskSurface = DeskSurface.Page) {
    val ink = D.ink(surface, DeskRole.Secondary)
    var open by remember { mutableStateOf(false) }
    // The tag and icon keep their small size and take the same room in the line (a 20 dp strip); the tappable row is 48 dp, overflowing the strip evenly
    // above and below (wrapContentSize unbounded) so nothing in the line is pushed around.
    Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(TAG_STRIP_HEIGHT.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.wrapContentSize(Alignment.CenterStart, unbounded = true).clip(RoundedCornerShape(6.dp)).clickable { open = true }
                .minimumInteractiveComponentSize().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(footerTag(f), style = TextStyle(fontSize = 9.sp, lineHeight = 12.sp, color = ink))
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Outlined.Info, contentDescription = f.infoLabel.ifBlank { dt("ui_translation_info", "About This Translation") }, tint = ink, modifier = Modifier.size(11.dp))
        }
    }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(f.infoLabel.ifBlank { footerTag(f) }, style = body(15f, D.Ink, FontWeight.SemiBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.en.isNotBlank()) Text(f.en, style = body(13.5f, D.Ink))
                if (f.local.isNotBlank()) Text(f.local, style = body(13.5f, D.Ink))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text(dt("ui_close", "Close"), style = body(14f, D.Ink, FontWeight.SemiBold)) } },
        containerColor = D.Raise,
    )
}

/** A paragraph with **bold**, showing its first [upto] words. */
@Composable
private fun Words(s: String, upto: Int, flag: Boolean, ink: Color = D.ink(DeskSurface.Page)) {
    val words = s.split(' ')
    val text = buildAnnotatedString {
        var bold = false
        words.forEachIndexed { i, w ->
            var word = w
            val open = word.startsWith("**")
            if (open) { bold = true; word = word.removePrefix("**") }
            val close = word.contains("**")
            word = word.replace("**", "")
            // Bold is the same ink, heavier: the surface's own colour (white here was invisible on the light page).
            val style = if (bold) SpanStyle(fontWeight = FontWeight.SemiBold) else SpanStyle()
            withStyle(if (i < upto) style else style.copy(color = Color.Transparent)) { append(word) }
            if (close) bold = false
            if (i < words.size - 1) append(' ')
        }
    }
    if (flag) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(D.Gold))
            Spacer(Modifier.width(10.dp))
            Text(text, style = body(color = ink))
        }
    } else Text(text, style = body(color = ink))
}

@Composable
private fun MeLine(line: Line, canChange: Boolean, onChange: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier.padding(start = 48.dp).clip(RoundedCornerShape(14.dp)).background(D.Me).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val s = (line.paras.firstOrNull() as? Para.Text)?.s.orEmpty()
            Text(if (line.step == Step.QUESTION && s.length > 420) s.take(420).trimEnd() + "…" else s, style = body(color = D.MeInk))
            line.files.forEach { f ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AttachFile, null, tint = D.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(f, style = body(12.5f, D.MeInk.copy(alpha = 0.8f)))
                }
            }
        }
        if (canChange) {
            // The secondary ink on the page (4.5:1 or better), not a faded one.
            Text(dt("ui_change", "Change"), style = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, color = D.ink(DeskSurface.Page, DeskRole.Secondary)),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChange).padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun NoteLine(line: Line) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (line.warn) D.Warn.copy(alpha = 0.1f) else D.Mark)
            .border(1.dp, if (line.warn) D.Warn.copy(alpha = 0.35f) else D.Line2, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = if (line.warn) D.Warn else D.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            line.paras.forEach { if (it is Para.Text) Words(it.s, Int.MAX_VALUE, false, D.ink(if (line.warn) DeskSurface.Warn else DeskSurface.Note)) }
            // A translated note or refusal carries the same footer or tag as Lex's lines, in the ink of the card it sits on.
            line.footer?.let { DeskFooterText(it, if (line.warn) DeskSurface.Warn else DeskSurface.Note) }
        }
    }
}

@Composable
private fun Typing() {
    val tr = rememberInfiniteTransition(label = "typing")
    val p by tr.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "typing-p")
    Row(verticalAlignment = Alignment.CenterVertically) {
        DeskMark()
        Spacer(Modifier.width(12.dp))
        repeat(3) { i ->
            Box(Modifier.padding(end = 5.dp).size(6.dp).alpha(if (p.toInt() == i) 1f else 0.35f).clip(CircleShape).background(D.Ink))
        }
    }
}

private val STAGES = listOf("Securing your enquiry", "Sending it to the chamber", "Letting the advocates know")

@Composable
private fun Stages(p: Para.Stages) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (p.done) p.doneTitle ?: "Delivered." else p.title ?: "Taking in your enquiry…", style = body(weight = FontWeight.SemiBold, color = D.ink(DeskSurface.Page, DeskRole.Strong)))
        val steps = p.steps.ifEmpty { STAGES }
        steps.forEachIndexed { i, running ->
            val done = p.done || i < p.active
            // A ticked step reads in its completed form when the server sent one; the running form while it runs.
            val s = if (done && p.doneSteps.size == steps.size) p.doneSteps[i] else running
            val active = !p.done && i == p.active && !p.held
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(if (done) D.Gold else Color.Transparent)
                        .border(1.5.dp, if (done || active) D.Gold else D.Line2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Outlined.Check, null, tint = D.OnGold, modifier = Modifier.size(12.dp))
                    else if (active) Pulse()
                }
                Spacer(Modifier.width(10.dp))
                Text(s, style = body(13.5f, if (done || active) D.Ink else D.Muted))
            }
        }
    }
}

@Composable
private fun Pulse() {
    val tr = rememberInfiniteTransition(label = "pulse")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse-a")
    Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(D.Gold))
}

/** The seal: a ring drawn round, then the tick. */
@Composable
private fun Seal() {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val tr = rememberInfiniteTransition(label = "seal")
    val k by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart), label = "seal-k")
    val gold = D.Gold
    Canvas(Modifier.size(44.dp)) {
        val r = size.minDimension / 2 - 2.dp.toPx()
        drawCircle(gold, r, style = Stroke(2.dp.toPx()))
        drawCircle(gold.copy(alpha = (1f - k) * 0.35f), r + k * 6.dp.toPx(), style = Stroke(1.dp.toPx()))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.51f); lineTo(size.width * 0.44f, size.height * 0.64f); lineTo(size.width * 0.69f, size.height * 0.37f)
        }
        drawPath(path, gold, style = Stroke(2.5.dp.toPx()))
    }
}

// ---- The dock ------------------------------------------------------------------------------

@Composable
private fun ErrorLine(s: String) {
    if (s.isNotEmpty()) Text(s, style = body(12.5f, D.Warn), modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun WaitLine(label: String) {
    val tr = rememberInfiniteTransition(label = "cursor")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "cursor-a")
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("> " + label.ifBlank { "" }, style = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, color = D.Gold))
        Box(Modifier.padding(start = 3.dp).size(width = 7.dp, height = 14.dp).alpha(a).background(D.Gold))
    }
}

/** The composer: what has happened, with a paperclip where files are taken. */
@Composable
private fun QuestionDock(value: TextFieldValue, onValue: (TextFieldValue) -> Unit, focus: FocusRequester, ui: DeskUi, attach: (() -> Unit)?, onRemove: (Int) -> Unit,
                         onFocus: (Boolean) -> Unit = {}, onSend: () -> Unit) {
    // Answers to focus: the box lifts, its rule brightens and it opens up for
    // writing; at rest it sits quieter and lower.
    var focused by remember { mutableStateOf(false) }
    val rule by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0.45f, tween(180), label = "rule")
    val ruleW by androidx.compose.animation.core.animateDpAsState(if (focused) 2.dp else 1.dp, tween(180), label = "ruleW")
    val room by androidx.compose.animation.core.animateDpAsState(if (focused) 96.dp else 44.dp, tween(220), label = "room")
    val text = value.text
    val enquiryDesc = dt("sr_enquiry", "Your enquiry")
    // The examples type themselves into the empty box, letter by letter, and
    // erase for the next; they stop the moment the client writes.
    // Focused, it rests on a whole example and leaves the cursor to the field.
    var shown by remember { mutableStateOf("") }
    var ex by remember { mutableIntStateOf(0) }
    LaunchedEffect(text.isEmpty(), focused) {
        if (text.isNotEmpty()) return@LaunchedEffect
        if (focused) { shown = EXAMPLES[ex]; return@LaunchedEffect }
        while (true) {
            val line = EXAMPLES[ex]
            for (i in 1..line.length) { shown = line.take(i); delay(if (line[i - 1] == ' ') 90 else 55) }
            delay(1_500)
            for (i in line.length downTo 0) { shown = line.take(i); delay(22) }
            delay(350)
            ex = (ex + 1) % EXAMPLES.size
        }
    }
    Column {
        FileChips(ui.files, onRemove)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (focused) D.Mark else D.Raise)
                .border(ruleW, D.Gold.copy(alpha = rule), RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = room)) {
                if (text.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    // A translated conversation shows its own static prompt; the typing examples are English.
                    val describe = LocalDeskBook.current.fragment("ph_describe")
                    Text(describe ?: shown, style = body(15.5f, D.Ink.copy(alpha = if (focused) 0.32f else 0.42f)))
                    if (!focused && describe == null) Box(Modifier.padding(start = 1.dp).width(2.dp).height(18.dp).background(D.Gold.copy(alpha = 0.7f)))
                }
                BasicTextField(
                    value, { if (it.text.length <= ui.terms.maxChars) onValue(it) },
                    textStyle = body(15.5f, D.MeInk), cursorBrush = SolidColor(D.Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp).focusRequester(focus).semantics { contentDescription = enquiryDesc }
                        .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; onFocus(it.isFocused) } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attach != null) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, D.Line2, RoundedCornerShape(10.dp)).clickable(onClick = attach),
                        contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, dt("ui_attach_aria", "Attach files"), tint = D.Ink, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.weight(1f))
                if (text.length > 4000) Text("${text.length} / ${ui.terms.maxChars}", style = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = D.Muted), modifier = Modifier.padding(end = 10.dp))
                SendButton(dt("ui_send", "Send"), text.isNotBlank(), onClick = onSend)
            }
        }
        ErrorLine(ui.error)
    }
}

/**
 * The one Send button of every composer (owner, 2026-11-15, F-030): the word and the icon, gold with the dark ink, and grey and not pressable until there is
 * something to send.
 */
@Composable
internal fun SendButton(label: String, enabled: Boolean, height: Dp = 40.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val ink = if (enabled) D.OnGold else D.Muted
    Row(
        Modifier.height(height).clip(shape).background(if (enabled) D.Gold else D.Line2).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = body(14f, ink, FontWeight.SemiBold))
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.Send, null, tint = ink, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun FileChips(files: List<DeskFile>, onRemove: (Int) -> Unit) {
    if (files.isEmpty()) return
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.Mark).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, null, tint = D.Gold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(f.name + " · " + DeskViewModel.mb(f.size), style = body(13f), maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { onRemove(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, dt("ui_remove", "Remove"), tint = D.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Compose(placeholder: String, keyboard: KeyboardOptions, prefix: String? = null, initial: String = "", description: String? = null, sendLabel: String? = null,
                    valid: ((String) -> Boolean)? = null, format: (String) -> String = { it },
                    /** What sits in the card's bottom row, left of Send (the guest's sign-in link), and the counter beside Send. */
                    tools: (@Composable () -> Unit)? = null, count: ((String) -> String)? = null, countWarn: ((String) -> Boolean)? = null,
                    /** Something other than the text is ready to send (the kept ticks of a question with options). */
                    sendReady: Boolean = false,
                    onSend: (String) -> Unit) {
    // The field keeps its own cursor: a formatter that adds a space (a phone number) must not leave the cursor behind, or the next digits land out of order.
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val v = field.text
    val focus = remember { FocusRequester() }
    // The full desk puts the cursor in the box; the Sign In chat panel does not (the keyboard opens only when the box is touched).
    val auto = LocalDeskAutoFocus.current
    LaunchedEffect(Unit) { if (auto) runCatching { focus.requestFocus() } }
    // One composer in every text step (owner, 2026-11-15, F-030): the field above, and the one Send button at the bottom right, as the first box has it.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(D.Raise).border(1.5.dp, D.Gold.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) { Text(prefix, style = body(15.5f, D.Muted)); Spacer(Modifier.width(8.dp)) }
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (v.isEmpty()) Text(placeholder, style = body(15.5f, D.Ink.copy(alpha = 0.42f)))
                BasicTextField(field, { field = reformat(it, format) }, textStyle = body(15.5f, D.MeInk), cursorBrush = SolidColor(D.Gold), keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).focusRequester(focus).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            tools?.invoke()
            Spacer(Modifier.weight(1f))
            if (count != null) Text(count(v), style = body(12f, if (countWarn?.invoke(v) == true) D.Warn else D.Muted), modifier = Modifier.padding(end = 10.dp))
            if (valid != null && valid(v)) Icon(Icons.Outlined.Check, null, tint = D.Live, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
            SendButton(sendLabel ?: dt("ui_send", "Send"), v.isNotBlank() || sendReady) { onSend(v) }
        }
    }
}

/**
 * A hearing or a deadline, in the client's own words (up to DEADLINE_MAX), and the date if they have one. The words are kept as written and the desk never
 * reads or changes them. Either is enough; the date is chosen, never typed.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDock(error: String, onGive: (text: String, date: String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }          // YYYY-MM-DD
    var picking by remember { mutableStateOf(false) }
    val dtFlagWords = dt("sr_flag", "Your words about the hearing or deadline")
    val dtDate = dt("sr_date", "Date, if you know it")
    val dtChoose = dt("ui_choose", "Choose")
    val dtCancel = dt("ui_cancel", "Cancel")
    Column {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(D.Raise).border(1.5.dp, D.Gold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                if (text.isEmpty()) Text(dt("ph_flag", "For example: the notice says reply by Friday"), style = body(15f, D.Ink.copy(alpha = 0.42f)))
                BasicTextField(text, { text = it.take(DEADLINE_MAX) }, textStyle = body(15.5f, D.MeInk), cursorBrush = SolidColor(D.Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).semantics { contentDescription = dtFlagWords })
            }
            Text("${text.length} / $DEADLINE_MAX", style = body(11.5f, D.Muted), modifier = Modifier.align(Alignment.End))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).border(1.5.dp, D.Line2, RoundedCornerShape(50)).clickable { picking = true }.semantics { contentDescription = dtDate }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (date.isEmpty()) dt("ui_pick_date", "Pick A Date") else deadlineShown("", date), style = body(14f, D.Ink, FontWeight.SemiBold))
            }
            if (date.isNotEmpty()) Text(dt("ui_clear", "Clear"), style = body(13f, D.Ink.copy(alpha = 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                modifier = Modifier.clickable { date = "" }.padding(12.dp))
            Spacer(Modifier.weight(1f))
            // Either the words or the date is enough.
            SendButton(dt("ui_send", "Send"), text.isNotBlank() || date.isNotEmpty()) { onGive(text, date) }
        }
        ErrorLine(error)
    }
    if (picking) {
        val st = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (date.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: java.time.LocalDate.now())
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    st.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(dtChoose) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text(dtCancel) } },
        ) { androidx.compose.material3.DatePicker(state = st) }
    }
}

/** The WhatsApp code (REQ-018): six digits, Confirm; Send Code Again unlocks after resend_in, Send By SMS Instead after sms_in (when offered); Change Number any time. */
@Composable
private fun CodeDock(info: PhoneCodeUi, error: String, onConfirm: (String) -> Unit, onAgain: () -> Unit, onSms: () -> Unit, onChange: () -> Unit) {
    val elapsed by androidx.compose.runtime.produceState(initialValue = 0, info.sentAtMs) {
        while (true) { value = ((System.currentTimeMillis() - info.sentAtMs) / 1000L).toInt(); delay(1_000) }
    }
    val againLeft = unlockLeft(info.resendIn, elapsed)
    val smsLeft = unlockLeft(info.smsIn, elapsed)
    Column {
        Compose(dt("ph_code", "6-digit code"), KeyboardOptions(keyboardType = KeyboardType.NumberPassword), description = dt("sr_code", "Code from WhatsApp"),
            format = { it.filter { c -> c.isDigit() }.take(6) }, sendLabel = dt("ui_confirm", "Confirm"), onSend = onConfirm)
        ErrorLine(error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val again = dt("ui_resend", "Send Code Again")
            Text(if (againLeft > 0) "$again ($againLeft)" else again, style = body(13f, D.Ink.copy(alpha = if (againLeft > 0) 0.35f else 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = againLeft == 0, onClick = onAgain).padding(horizontal = 4.dp, vertical = 8.dp))
            if (info.canSms) Text(dt("ui_sms_instead", "Send By SMS Instead"), style = body(13f, D.Ink.copy(alpha = if (smsLeft > 0) 0.35f else 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = smsLeft == 0, onClick = onSms).padding(horizontal = 4.dp, vertical = 8.dp))
            Quiet(dt("ui_change_number", "Change Number"), onChange)
        }
    }
}

/** A guest's message: up to the server's guest_chars, with the sign-in beside it. */
@Composable
private fun GuestDock(error: String, limit: Int, onSend: (String) -> Unit, onSignIn: () -> Unit) {
    Column {
        // One composer (F-030): the sign-in link and the counter sit in the card's bottom row, left of Send, as the web's guest box has them.
        Compose(dt("ph_ask", "Ask a short question…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), description = dt("sr_message", "Your message"),
            format = { it.take(limit) },
            tools = { QuietInRow(dt("ui_signin_google", "Sign in with Google"), onSignIn) },
            count = { v -> "${v.length} / $limit" }, countWarn = { v -> v.length > limit - 20 }, onSend = onSend)
        ErrorLine(error)
    }
}

/** The Google sign-in, in the conversation. */
@Composable
private fun GoogleDock(busy: Boolean, error: String, onGoogle: () -> Unit, onTestContact: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(D.Raise).border(1.dp, D.Line2, RoundedCornerShape(14.dp))
                .clickable(enabled = !busy, onClick = onGoogle).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.legumlex.client.R.drawable.ic_google_g), null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (busy) dt("wait_signing", "Signing You In…") else dt("ui_continue_google", "Continue with Google"), style = body(15f, D.Ink, FontWeight.Medium))
        }
        // Debug builds only (the web's dev Test visitor): the sign-in screen's test contact, continuing this same conversation as a Google sign-in does.
        if (onTestContact != null) Quiet("Test Contact", onTestContact)
        ErrorLine(error)
    }
}

/** The quiet link inside a composer's bottom row (no extra top gap). */
@Composable
private fun QuietInRow(label: String, onClick: () -> Unit) {
    Text(label, style = body(13f, D.Ink.copy(alpha = 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
private fun Quiet(label: String, onClick: () -> Unit) {
    Text(label, style = body(13f, D.Ink.copy(alpha = 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp))
}

/** One of Lex's follow-up questions: the answer box, and the quiet Skip The Questions under it. */
@Composable
private fun GatherDock(error: String, onSend: (String) -> Unit, onSendNow: () -> Unit) {
    Column {
        Compose(dt("ph_answer", "Your answer…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false), description = dt("sr_answer", "Your answer"), onSend = onSend)
        ErrorLine(error)
        Quiet(dt("ui_send_now", GATHER_SEND_NOW), onSendNow)   // a quiet text button under the box, as the web's, not a second main action
    }
}

@Composable
private fun ClarifyDock(error: String, onSend: (String) -> Unit, onAsIs: () -> Unit) {
    Column {
        Compose(dt("ph_more", "A little more about it…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false), description = dt("sr_answer", "Your answer"), onSend = onSend)
        ErrorLine(error)
        Quiet(dt("ui_send_as_is", "Send it as it is"), onAsIs)
    }
}

/** One button of a group: its words, whether it is the main one, an optional second line (a fee), and what it does. */
internal data class ChipSpec(val label: String, val main: Boolean, val sub: String = "", val go: () -> Unit = {})

/** How a group of buttons is laid out. */
internal enum class ChipLayout { Single, Row, Stack, Flow }

/**
 * The one rule for a group of buttons (owner, 2026-11-15): a pair or a trio sit side by side in one row, equal width, equal height, the main one filled and the others
 * outlined; a long trio stacks as equal-width full rows, never ragged; four or more (a list of countries) flow and wrap. A single button keeps its own width.
 */
internal fun chipLayout(labels: List<String>, availableDp: Float = 324f): ChipLayout = when {
    labels.size <= 1 -> ChipLayout.Single
    // A pair or a trio sits in a row only when every label fits in at most two lines inside its share of the width; otherwise the group stacks. No label is ever cut off.
    labels.size <= 3 -> if (labels.all { chipLines(it, labels.size, availableDp) <= 2 }) ChipLayout.Row else ChipLayout.Stack
    else -> ChipLayout.Flow
}

/** About how wide a semibold 14 sp character is in dp (generous, so the estimate stacks a little early rather than clip). */
internal const val CHIP_CHAR_DP = 8.2f

/** The lines a label needs in one of [count] equal buttons across [availableDp] (a greedy wrap by words, the padding and the gaps taken off). */
internal fun chipLines(label: String, count: Int, availableDp: Float): Int {
    val room = (availableDp - (count - 1) * CHIP_GAP) / count - 28f          // 14 dp of padding each side
    val perLine = (room / CHIP_CHAR_DP).toInt().coerceAtLeast(1)
    var lines = 1; var used = 0
    for (word in label.trim().split(Regex("\\s+"))) {
        var w = word.length
        if (used == 0) { used = minOf(w, perLine); w -= used; while (w > 0) { lines++; used = minOf(w, perLine); w -= used } }
        else if (used + 1 + w <= perLine) used += 1 + w
        else { lines++; used = 0; used = minOf(w, perLine); w -= used; while (w > 0) { lines++; used = minOf(w, perLine); w -= used } }
    }
    return lines
}

@Composable
private fun Chips(list: List<Triple<String, Boolean, () -> Unit>>, topGap: androidx.compose.ui.unit.Dp = 0.dp) =
    ChipGroup(list.map { (l, m, g) -> ChipSpec(l, m, "", g) }, topGap)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(list: List<ChipSpec>, topGap: androidx.compose.ui.unit.Dp = 0.dp) = androidx.compose.foundation.layout.BoxWithConstraints {
    val width = maxWidth.value
    @Composable
    fun One(c: ChipSpec, modifier: Modifier) {
        Column(
            modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (c.main) D.Gold else Color.Transparent)
                .border(1.5.dp, if (c.main) D.Gold else D.Line2, RoundedCornerShape(22.dp)).clickable(onClick = c.go).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(c.label, style = body(14f, if (c.main) D.OnGold else D.Ink, FontWeight.SemiBold), textAlign = TextAlign.Center)
            if (c.sub.isNotBlank()) Text(c.sub, style = body(12f, (if (c.main) D.OnGold else D.Muted)), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
    when (chipLayout(list.map { it.label }, width)) {
        ChipLayout.Single -> Box(Modifier.padding(top = topGap)) { list.forEach { One(it, Modifier) } }
        ChipLayout.Row -> Row(Modifier.padding(top = topGap).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
            list.forEach { One(it, Modifier.weight(1f).fillMaxHeight()) }
        }
        ChipLayout.Stack -> Column(Modifier.padding(top = topGap).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
            list.forEach { One(it, Modifier.fillMaxWidth()) }
        }
        ChipLayout.Flow -> FlowRow(Modifier.padding(top = topGap), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp), verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
            list.forEach { One(it, Modifier) }
        }
    }
}

@Composable
private fun DocsDock(ui: DeskUi, attach: (() -> Unit)?, onRemove: (Int) -> Unit, onGo: () -> Unit) {
    Column {
        FileChips(ui.files, onRemove)
        val n = ui.files.size
        Chips(listOfNotNull(
            attach?.let { Triple(dt("ui_attach_docs", "Attach Documents"), false, it) },
            Triple(if (n > 1) LocalDeskBook.current.fragment("ui_continue_files", mapOf("n" to n.toString())) ?: "Continue with $n Files" else if (n == 1) dt("ui_continue_file", "Continue with 1 File") else dt("ui_carry_without", "Carry On Without"), true, onGo),
        ))
        ErrorLine(ui.error)
    }
}

@Composable
private fun EntityDock(hint: String, onPick: (String) -> Unit) {
    Column {
        Chips(listOf("business" to dt("ui_business", "Business"), "individual" to dt("ui_individual", "Individual"), "nri" to dt("ui_nri", "NRI")).map { (v, l) -> Triple(l, hint == v) { onPick(v) } })
        Text("If the advocates take the matter on, they will ask for the exact legal form then, not now.", style = body(12f, D.Muted), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

@Composable
private fun CountryDock(ui: DeskUi, onPick: (String) -> Unit, onOther: (String) -> Unit) {
    var other by remember { mutableStateOf(false) }
    Column {
        Chips(ui.terms.countries.map { c -> Triple("${c.name}  ${c.dial}", false) { onPick(c.code) } } + Triple(dt("ui_somewhere_else", "Somewhere else"), other) { other = true })
        if (other) {
            Spacer(Modifier.height(10.dp))
            Compose(dt("ph_country", "Dubai, Singapore, Germany…"), KeyboardOptions(capitalization = KeyboardCapitalization.Words), description = dt("sr_country", "Country or city"), onSend = onOther)
        }
        ErrorLine(ui.error)
    }
}

@Composable
private fun PhoneDock(nri: Boolean, dial: String?, required: Boolean, error: String, problem: (String) -> String, onSend: (String) -> Unit, onNone: () -> Unit) {
    Column {
        Compose(
            placeholder = if (nri) "+1 416 555 0123" else "98765 43210",
            description = if (nri) dt("sr_phone_cc", "Phone number with country code") else dt("sr_mobile", "Mobile number"),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
            prefix = if (nri) null else "+91",
            initial = if (nri && dial != null) "$dial " else "",
            valid = { problem(it).isEmpty() },
            // Shown as it will be dialled: five and five, a leading 91 or 0 dropped.
            format = { raw -> formatPhoneInput(raw, nri) },
            onSend = onSend,
        )
        ErrorLine(error)
        if (!required) Quiet(dt("ui_no_number", "I would rather not give a number"), onNone)
    }
}

// ---- Opening an account (lexon Enrolment) ---------------------------------------------

/** The firm's note on a registration it sent back, pinned above the dock. */
@Composable
private fun AcctNote(note: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 6.dp).height(IntrinsicSize.Min)
        .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)).background(D.Mark)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(D.Gold))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("The Firm’s Note", style = body(12f, D.Ink, FontWeight.SemiBold))
            Text(note, style = body(13.5f, D.Ink.copy(alpha = 0.85f)))
        }
    }
}

/** The summary, in the desk's line: each group, with Change, Replace and Add while it is the question. */
@Composable
private fun AcctSummary(p: Para.Summary, onAcct: (AcctChip) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        p.groups.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title.uppercase(), style = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = D.Muted), modifier = Modifier.weight(1f))
                    if (g.change && p.live) AcctLink("Change", "Change ${g.title}") { onAcct(AcctChip("edit:" + g.id, "Change: " + g.title)) }
                }
                g.rows.forEach { r ->
                    Column {
                        Text(r.label, style = body(12f, D.Muted))
                        Text(r.value, style = body(14f))
                        if (r.from.isNotBlank()) Text(r.from, style = body(11.5f, D.Muted))
                    }
                }
                g.documents.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.label, style = body(12f, D.Muted))
                            Text(d.value + if (d.status.isNotBlank()) "  ·  " + d.status else "", style = body(14f))
                        }
                        if (p.live) AcctLink(d.action.label, d.action.label + " " + d.label) { onAcct(d.action.copy(label = d.action.label + ": " + d.label)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcctLink(label: String, description: String, onClick: () -> Unit) {
    Text(label, style = body(13f, D.Gold, FontWeight.SemiBold).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp))
}

/** What answers the server's turn: a document, a card, the code, the final tick, chips; More beneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcctDockView(
    t: AcctTurn, onChip: (AcctChip) -> Unit, onScan: () -> Unit, onPick: () -> Unit,
    onFields: (Map<String, String>, Map<String, String>, String) -> Unit, onCode: (String) -> Unit, onSubmit: () -> Unit,
) {
    var more by remember(t) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        t.progress?.let { (at, of) ->
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(D.Line)) {
                Box(Modifier.fillMaxWidth(at.toFloat() / of).height(2.dp).background(D.Gold))
            }
        }
        var ownButton = false
        when (val d = t.dock) {
            is AcctDock.Upload -> {
                ownButton = true
                if (d.why.isNotBlank()) Text(d.why, style = body(13f, D.Muted))
                Chips(listOf(Triple("Scan With Camera", true, onScan), Triple("Choose A File", false, onPick)))
            }
            is AcctDock.Card -> { ownButton = true; AcctCard(d, onFields, onChip) }
            is AcctDock.Code -> { ownButton = true; AcctCode(d, onCode) }
            is AcctDock.Final -> { ownButton = true; AcctFinal(d, t.error, onSubmit) }
            else -> Unit
        }
        if (t.chips.isNotEmpty()) AcctChips(t.chips, main = !ownButton, onChip)
        if (t.more.isNotEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Quiet("More") { more = !more } }
            if (more) AcctChips(t.more, main = false, onChip)
        }
    }
}

/** Chips, the first leading; one with a wait counts down to being pressed (Send A New Code In 0:42). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcctChips(chips: List<AcctChip>, main: Boolean, onChip: (AcctChip) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEachIndexed { i, c ->
            var left by remember(c) { mutableIntStateOf(c.wait) }
            LaunchedEffect(c) { while (left > 0) { delay(1_000); left-- } }
            val lead = main && i == 0
            val label = if (left > 0) "%s In %d:%02d".format(c.label, left / 60, left % 60) else c.label
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(if (lead) D.Gold else Color.Transparent)
                    .border(1.5.dp, if (lead) D.Gold else D.Line2, RoundedCornerShape(50))
                    .clickable(enabled = left == 0) { onChip(c) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = body(14f, if (lead) D.OnGold else if (left > 0) D.Muted else D.Ink, FontWeight.SemiBold)) }
        }
    }
}

/** A card of fields: labels above, errors under, locked readings shaded. */
@Composable
private fun AcctCard(d: AcctDock.Card, onFields: (Map<String, String>, Map<String, String>, String) -> Unit, onChip: (AcctChip) -> Unit) {
    val values = remember(d) { mutableStateMapOf<String, String>().apply { d.fields.forEach { put(it.name, it.value) } } }
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        d.fields.forEach { f ->
            Column {
                Text(f.label, style = body(13f, D.Ink.copy(alpha = 0.85f), FontWeight.Medium), modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                if (f.select != null) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.Raise).border(1.dp, if (f.error.isNotBlank()) D.Warn else D.Line2, RoundedCornerShape(12.dp))
                            .clickable { open = true }.padding(14.dp)) {
                            Text(f.select.firstOrNull { it.first == values[f.name] }?.second ?: "Choose…", style = body(15f, if (values[f.name].isNullOrBlank()) D.Muted else D.Ink))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            f.select.forEach { (v, l) -> androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { values[f.name] = v; open = false }) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (f.locked) D.Mark else D.Raise)
                        .border(1.dp, if (f.error.isNotBlank()) D.Warn else D.Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (f.prefix.isNotBlank()) { Text(f.prefix, style = body(15f, D.Muted)); Spacer(Modifier.width(8.dp)) }
                        BasicTextField(values[f.name].orEmpty(), { if (!f.locked) values[f.name] = it }, readOnly = f.locked,
                            textStyle = body(15f, D.MeInk), cursorBrush = SolidColor(D.Gold), singleLine = !f.multiline,
                            keyboardOptions = KeyboardOptions(keyboardType = when { f.phone && f.prefix.isBlank() -> KeyboardType.Phone; f.numeric -> KeyboardType.Number; else -> KeyboardType.Text },
                                capitalization = if (f.numeric || f.phone) KeyboardCapitalization.None else KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth().heightIn(min = if (f.multiline) 56.dp else 0.dp))
                    }
                }
                if (f.error.isNotBlank()) Text(f.error, style = body(12.5f, D.Warn), modifier = Modifier.padding(top = 4.dp, start = 2.dp))
                else if (f.hint.isNotBlank()) Text(f.hint, style = body(12f, D.Muted), modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            }
        }
        if (d.under.isNotBlank()) Text(d.under, style = body(12.5f, D.Muted))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            d.cancel?.let { c -> Chips(listOf(Triple(c, false) { onChip(AcctChip("cancel", c)) })) }
            Chips(listOf(Triple(d.button, true) {
                val editable = d.fields.filterNot { it.locked }
                onFields(editable.associate { it.name to values[it.name].orEmpty().trim() },
                    editable.associate { it.name to it.label } + editable.filter { it.select != null }.associate { it.name to it.label },
                    d.button)
            }))
        }
    }
}

/** The code: six digits, sent on their own when complete; errors under it. */
@Composable
private fun AcctCode(d: AcctDock.Code, onCode: (String) -> Unit) {
    var v by remember(d) { mutableStateOf("") }
    Column {
        Text("Code", style = body(13f, D.Ink.copy(alpha = 0.85f), FontWeight.Medium), modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(D.Raise).border(1.dp, if (d.error.isNotBlank()) D.Warn else D.Line2, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicTextField(v, { s -> v = s.filter { it.isDigit() }.take(6); if (v.length == 6) onCode(v) },
                    textStyle = body(17f, D.MeInk).copy(letterSpacing = 4.sp), cursorBrush = SolidColor(D.Gold), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            }
            Chips(listOf(Triple("Confirm", v.length == 6) { if (v.length == 6) onCode(v) }))
        }
        if (d.error.isNotBlank()) Text(d.error, style = body(12.5f, D.Warn), modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}

/** The final tick: the consent, then Open My Account (or Send Again). */
@Composable
private fun AcctFinal(d: AcctDock.Final, error: String, onSubmit: () -> Unit) {
    var ticked by remember(d) { mutableStateOf(false) }
    var asked by remember(d) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, D.Line2, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.clickable { ticked = !ticked }, verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Checkbox(checked = ticked, onCheckedChange = { ticked = it },
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = D.Gold, checkmarkColor = D.OnGold, uncheckedColor = D.Line2))
            Text(d.consent, style = body(13.5f), modifier = Modifier.padding(top = 12.dp))
        }
        if (asked && !ticked) Text("Please tick the box to confirm.", style = body(12.5f, D.Warn))
        else if (error.isNotBlank()) Text(error, style = body(12.5f, D.Warn))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Chips(listOf(Triple(d.button, true) { if (ticked) onSubmit() else asked = true }))
        }
    }
}

/** A document going up and being read: the lines at 20 and 60 seconds, then Stop Waiting. */
@Composable
private fun AcctReadingDock(d: Dock.AcctReading, onStop: () -> Unit) {
    var now by remember(d) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(d) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val s = (now - d.since) / 1_000
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WaitLine(when {
            s >= 60 -> "This copy is taking a little longer. You can wait, or upload a clearer one."
            s >= 20 -> "Still reading your document…"
            else -> "Reading your ${d.label}…"
        })
        if (s >= 60) Chips(listOf(Triple("Stop Waiting", false, onStop)))
    }
}

/** The intro, and (where the desk takes them) the Onboard Now / Register As Counsel chips. [compact] is the Sign In panel's smaller type. */
@Composable
internal fun DeskIntro(vm: DeskViewModel, ui: DeskUi, compact: Boolean = false) {
    Intro(vm.guest, compact)
    // Opening an account, for a lead the desk does that for.
    // A guest sees the counsel chip by google_config's flag (the terms need a sign-in); a lead by the desk's own.
    val counselChip = if (vm.guest) ui.guestCounselDesk else ui.terms.counselDesk
    if (((ui.terms.accountDesk && !vm.guest) || counselChip)) Box(Modifier.padding(start = 18.dp, top = if (compact) 10.dp else 18.dp)) {
        Chips(listOfNotNull(
            Triple("Onboard Now", false) { vm.startAccount() }.takeIf { ui.terms.accountDesk && !vm.guest },
            Triple("Register As Counsel", false) { vm.startCounsel() }.takeIf { counselChip },
        ))
    }
}

/** The box that asks the first question: a guest's short one, a signed-in client's composer, or the notice that the desk is closed. */
@Composable
internal fun DeskOpening(
    vm: DeskViewModel, ui: DeskUi, draft: TextFieldValue, onDraft: (TextFieldValue) -> Unit, compose: FocusRequester,
    attach: (() -> Unit)?, onFocus: (Boolean) -> Unit = {},
) {
    Box(Modifier.padding(horizontal = DESK_SIDE_MARGIN.dp)) {
        if (ui.termsLoaded && !ui.terms.isActive) {
            Text("The desk is not taking new enquiries just now. Please write to your advocate on one of your matters, or try again later.",
                style = body(14f, D.Muted), modifier = Modifier.padding(vertical = 12.dp))
        } else if (vm.guest) {
            GuestDock(ui.error, ui.guestChars, vm::submitGuest, vm::guestSignIn)
        } else {
            QuestionDock(draft, onDraft, compose, ui, attach, vm::removeFile, onFocus = onFocus) { vm.submitQuestion(draft.text) }
        }
    }
}

/** Whether the conversation has reached a step that belongs to the full desk: the Google sign-in, an upload, the account-opening steps. */
internal fun DeskUi.needsFullDesk(): Boolean = acct || dock is Dock.SignIn || dock is Dock.Acct || dock is Dock.AcctReading || dock == Dock.Docs

/**
 * The conversation once it has begun: the messages, and below them whatever the desk is asking for, with its footer. The full desk screen
 * and the Sign In chat panel both draw this. With [fullDesk] set (the panel), a step that needs the full desk shows its button in place of
 * the dock; the full screen passes null and draws every dock.
 */
@Composable
internal fun DeskThread(
    vm: DeskViewModel, ui: DeskUi, draft: TextFieldValue, onDraft: (TextFieldValue) -> Unit, compose: FocusRequester, attach: (() -> Unit)?,
    signingIn: Boolean, google: () -> Unit, acctScan: () -> Unit, acctPick: () -> Unit, fullDesk: (() -> Unit)?, modifier: Modifier = Modifier, testContact: (() -> Unit)? = null,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDeskBook provides ui.labels) {
    // The dock never takes more than 60% of the room the desk has (the keyboard included): a tall dock scrolls inside itself, so its box and Send stay in reach above the keyboard.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
    val dockMax = maxHeight * 0.6f
    Column(Modifier.fillMaxSize()) {
        Conversation(ui, vm::change, vm::acctChip, Modifier.weight(1f), onPick = vm::pickCard)
        if (fullDesk == null && ui.acct && ui.acctNote.isNotBlank()) AcctNote(ui.acctNote)
        // The tick list keeps its question, its button and its links in reach: only the rows scroll (inside OptionsDock), so this dock does not scroll as a whole.
        val tickList = (ui.dock as? Dock.Options)?.writing == false
        Box(Modifier.heightIn(max = dockMax).then(if (tickList) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(horizontal = DESK_SIDE_MARGIN.dp, vertical = 8.dp)) {
            if (fullDesk != null && ui.needsFullDesk()) {
                Column(Modifier.fillMaxWidth()) {
                    Chips(listOf(Triple("Continue In The Full Desk", true, fullDesk)))
                }
            } else when (val d = ui.dock) {
                Dock.Question -> QuestionDock(draft, onDraft, compose, ui, attach, vm::removeFile) { vm.submitQuestion(draft.text) }
                Dock.Clarify -> ClarifyDock(ui.error, vm::submitClarify, vm::sendAsItIs)
                Dock.Gather -> GatherDock(ui.error, vm::submitGather, vm::sendNow)
                Dock.Docs -> DocsDock(ui, attach, vm::removeFile, vm::docsGo)
                is Dock.Advocate -> AdvocateWaiting(d.state, { vm.act("advocate_open") })
                is Dock.Actions -> Column { ChipGroup(d.list.map { a -> ChipSpec(a.label, a.main, a.sub) { vm.act(a.id) } }, topGap = CHIP_GROUP_TOP.dp); ErrorLine(ui.error) }
                is Dock.Options -> OptionsDock(d, ui.error, vm::togglePick, vm::sendPicks, vm::writeOwn, vm::backToChoices, vm::sendNow, vm::submitGather)
                is Dock.Cards -> CardsDock(ui.error, vm::cardText, vm::cardsNotNow)
                is Dock.Route -> RouteDock(d.route, ui.error, { vm.act("route_ahc") }, vm::submitQuestion)
                Dock.Matter -> MatterDock(ui.error, vm::matterGo, vm::matterSkip)
                is Dock.Entity -> EntityDock(d.hint, vm::chooseEntity)
                Dock.Country -> CountryDock(ui, vm::chooseCountry, vm::otherCountry)
                Dock.Phone -> PhoneDock(vm.nri, vm.dial, ui.terms.phoneRequired || (ui.terms.phoneVerify.required && !ui.terms.phoneVerify.verified), ui.error, vm::phoneProblem, vm::givePhone, vm::noPhone)
                // One thing at a time: an error replaces the pending line (the dock never shows both), and buttons are a dock of their own.
                is Dock.Waiting -> if (ui.error.isNotEmpty()) ErrorLine(ui.error) else WaitLine(d.label)
                is Dock.Code -> CodeDock(d.info, ui.error, vm::submitCode, vm::codeAgain, vm::codeSms, vm::changeNumber)
                Dock.Guest -> GuestDock(ui.error, ui.guestChars, vm::submitGuest, vm::guestSignIn)
                Dock.Deadline -> DeadlineDock(ui.error, vm::giveDeadline)
                Dock.SignIn -> GoogleDock(signingIn, ui.error, google, testContact)
                is Dock.Acct -> AcctDockView(d.turn, vm::acctChip, acctScan, acctPick, vm::acctFields, vm::acctCode, vm::acctSubmit)
                is Dock.AcctReading -> AcctReadingDock(d, vm::acctStop)
            }
        }
        if (ui.acct) {
            // A counsel registration has its own footer (what to have at hand), the client's is the account footer.
            val foot = if (ui.counsel && ui.terms.counselFoot.isNotBlank()) ui.terms.counselFoot else ui.terms.accountFoot
            if (foot.isNotBlank()) Text(foot, style = body(12f, D.Muted), modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp))
        } else if (ui.termsLoaded) Disclaimer(ui.terms, compact = true, direct = ui.footerDirect, fee = ui.directFee, hidden = ui.footerUnread)
    }
    }
    }
}

/**
 * The guest desk's view model, handed from the Sign In chat panel to the full desk screen so the conversation carries over (the same
 * state, not a copy). The panel gives it just before navigating; the full screen takes it once.
 */
/** Which kept conversation the desk opens (see DeskScreen): a new key after a finished enquiry or another person signing in. */
internal object DeskKeep {
    @Volatile private var generation = 0
    fun renew() { generation++ }
    fun key(context: android.content.Context): String {
        val token = kotlinx.coroutines.runBlocking { com.legumlex.client.data.auth.TokenStore(context.applicationContext).read() }.orEmpty()
        return "desk-$generation-" + (if (token.isBlank()) "guest" else token.hashCode().toString())
    }
}

internal object DeskHandover {
    @Volatile private var held: DeskViewModel? = null
    fun give(vm: DeskViewModel) { held = vm }
    fun take(): DeskViewModel? = held.also { held = null }
}

/**
 * Lex Intelligence in the bottom half of Sign In (REQ-013 013.16): the same guest desk, drawn with the desk's own pieces (DeskIntro,
 * DeskOpening, DeskThread) over the same view model; no second chat. A step that needs the full desk (Google sign-in, an upload, opening
 * an account) hands over to it: [onFullDesk] runs once when the conversation reaches such a step, and the panel then offers its button.
 */
@Composable
fun LexChatPanel(
    onClose: () -> Unit, onFullDesk: () -> Unit, modifier: Modifier = Modifier,
    /** Opened by a petal: send its message as the first one, or go to the Google sign-in step (no message spent). [onStarted] says it has been done. */
    start: com.legumlex.client.feature.auth.PanelStart? = null, onStarted: () -> Unit = {},
) {
    val vm: DeskViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val compose = remember { FocusRequester() }
    val hand: () -> Unit = { DeskHandover.give(vm); onFullDesk() }
    LaunchedEffect(start) {
        when (start) {
            is com.legumlex.client.feature.auth.PanelStart.Message -> if (!ui.opened || ui.dock == Dock.Guest) vm.submitGuest(start.text)
            com.legumlex.client.feature.auth.PanelStart.SignIn -> vm.guestSignIn()
            null -> return@LaunchedEffect
        }
        onStarted()
    }
    // Only a change into such a step hands over by itself; coming back to the panel does not send the client on again.
    val needs = ui.needsFullDesk()
    var was by remember { mutableStateOf(needs) }
    LaunchedEffect(needs) { if (needs && !was) hand(); was = needs }
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    androidx.compose.runtime.CompositionLocalProvider(LocalDeskAutoFocus provides false) {
    Column(modifier.fillMaxWidth().clip(shape).background(D.Page).border(1.dp, D.Line, shape)) {
        Head(onClose)
        if (!ui.opened) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { DeskIntro(vm, ui, compact = true) }
            DeskOpening(vm, ui, draft, { draft = it }, compose, attach = null)
            Spacer(Modifier.height(10.dp))
        } else {
            DeskThread(
                vm, ui, draft, { draft = it }, compose, attach = null, signingIn = false, google = hand, acctScan = {}, acctPick = {},
                fullDesk = hand, modifier = Modifier.weight(1f),
            )
        }
    }
    }
}

/**
 * Debug builds only (reached from Sign In's Debug chip): a conversation with every element the desk draws, in the palette in force, so each can be
 * looked at in light and dark. Nothing here is sent anywhere.
 */
@Composable
fun DeskSampleScreen(onClose: () -> Unit) {
    val lines = remember {
        var n = 0L
        fun line(who: Who, vararg p: Para, warn: Boolean = false, files: List<String> = emptyList()) = Line(++n, who, p.toList(), warn = warn, files = files)
        listOf(
            line(Who.DESK, Para.Text(LEX_INTRO)),
            line(Who.ME, Para.Text("My tenant has not paid rent for three months and now says the flat was never let to him.")),
            line(Who.DESK, Para.Text("Thank you. **Bold words** sit in the same ink as the rest.")),
            line(Who.DESK, Para.Text("**This sounds time-critical: the limitation period may be close.** It will be marked urgent, so that the advocates see it first.", flag = true)),
            line(Who.DESK, Para.Text("What the advocates will look at:"), Para.Bullets(listOf("The letting, if any, in writing", "Rent received and when", "Any notice already sent or received"))),
            line(Who.DESK, Para.Facts(listOf("Matter" to "Recovery of rent", "Forum" to "Civil Judge, Chandigarh", "Fee" to "₹1,500 + GST"))),
            line(Who.DESK, Para.Text("An unbroken word: Supercalifragilisticexpialidocious_Supercalifragilisticexpialidocious_Supercalifragilistic and a link as plain text https://www.legumlex.com/consult/track/0123456789abcdef0123456789abcdef.")),
            line(Who.NOTE, Para.Text("That did not go through. Please check your connection and try again."), warn = true),
            line(Who.NOTE, Para.Text("The payment has not yet been confirmed. If you have just paid, please wait a minute and try again.")),
            line(Who.DESK, Para.Stages(active = 2, done = false)),
            line(Who.DESK, Para.Text("I have told an advocate you would like to talk directly."), Para.Sent("Sent to an advocate", "19:44")),
            line(Who.DESK, Para.Divider("Your registration is with the firm.")),
            line(Who.ME, Para.Text("Here are the papers."), files = listOf("rent-receipts-2026.pdf", "legal-notice.pdf")),
            // The desk's fixed lines in the visitor's script (the playbook's own examples), to see the glyphs: Hindi, then Punjabi.
            line(Who.ME, Para.Text(SAMPLE_HINDI_VISITOR)),
            line(Who.DESK, Para.Text(SAMPLE_HINDI_DESK)),
            line(Who.ME, Para.Text(SAMPLE_PUNJABI_VISITOR)),
            line(Who.DESK, Para.Text(SAMPLE_PUNJABI_DESK)),
        )
    }
    val ui = remember(lines) { DeskUi(lines = lines, opened = true, locked = true) }
    // One state at a time, as the real desk has: the conversation with a dock of buttons, an error, or a pending line, or the Review page.
    var state by remember { mutableStateOf(0) }
    var replay by remember { mutableStateOf(0) }   // the sending animation plays once; this plays it again (debug)
    val names = listOf("Buttons", "Error", "Pending", "Sending", "Waiting", "Handed Over")
    Column(Modifier.fillMaxSize().background(D.Page)) {
        Head(onClose)
            Conversation(ui, {}, {}, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = DESK_SIDE_MARGIN.dp, vertical = 8.dp)) {
                when (state) {
                    0 -> Chips(listOf(Triple("Open My Account", true, {}), Triple("No, I Have A Question", false, {}), Triple("Not Now", false, {})), topGap = CHIP_GROUP_TOP.dp)
                    1 -> ErrorLine("This is an error line. It replaces the pending line.")
                    2 -> WaitLine("reading your document")
                    3 -> androidx.compose.runtime.key(replay) { SendToAdvocate("Sent to an advocate", "19:44") }
                    4 -> AdvocateWaiting(AdvocateWait.Waiting("Waiting for the advocate", "Your final report has been sent. The advocate, or their office, will contact you to fix a time. You can leave this open."), {})
                    else -> AdvocateWaiting(AdvocateWait.HandedOver("Advocate Asha Rao has your enquiry now", "They will write to you here and by email.", "Open Consultation"), {})
                }
            }
        // Which state to look at (debug only).
        Box(Modifier.padding(horizontal = DESK_SIDE_MARGIN.dp, vertical = 8.dp)) {
            Chips(names.mapIndexed { i, n -> Triple("Show $n", i == state) { state = i; replay++ } })
        }
    }
}

/**
 * "Message sent to an advocate": a message glyph travels along a short hairline to an advocate glyph, which gets a small tick badge. About one second,
 * once; compact (the track is at most 40% of the width, left-aligned), flat, in the palette in force, Mylex radii (8). With animations off in the system
 * settings it is the final state at once. [caption] and [time] come from the caller (the server's lines); nothing is worded here. It sits inside Lex's
 * message block, under the informed line.
 */
@Composable
internal fun SendToAdvocate(caption: String, time: String? = null, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val on = remember { animationsOn(runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)) }
    // Played once: a line scrolled away and back, or a recomposition, shows the end and does not play again.
    var played by rememberSaveable { mutableStateOf(!on) }
    val t = remember { androidx.compose.animation.core.Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) { t.animateTo(1f, tween(SEND_ANIM_MS, easing = LinearEasing)); played = true }
    }
    val p = sendProgress(t.value)
    val done = sendDone(p)
    val ink = D.ink(DeskSurface.Page, DeskRole.Secondary)
    val hair = D.Line2
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(SEND_TRACK_FRACTION), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, hair, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = ink, modifier = Modifier.size(16.dp))
            }
            // The hairline, with the travelling dot on it.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(1.dp).background(hair))
                Box(Modifier.align(Alignment.CenterStart).width(maxWidth * p).height(1.dp).background(D.Gold))
                if (!done) Box(Modifier.align(Alignment.CenterStart).offset(x = (maxWidth - 8.dp) * p).size(8.dp).clip(RoundedCornerShape(4.dp)).background(D.Gold))
            }
            // The advocate stays; the tick is a small badge on it once the message has arrived.
            Box(Modifier.size(32.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, if (done) D.Gold else hair, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Gavel, null, tint = if (done) D.ink(DeskSurface.Page) else ink, modifier = Modifier.size(16.dp)) }
                if (done) Box(
                    Modifier.align(Alignment.TopEnd).size(14.dp).clip(RoundedCornerShape(7.dp)).background(D.Gold),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, null, tint = D.OnGold, modifier = Modifier.size(10.dp)) }
            }
        }
        Text(listOfNotNull(caption.takeIf { it.isNotBlank() }, time?.takeIf { it.isNotBlank() }).joinToString(" · "), style = body(12f, ink))
    }
}

/**
 * The quiet waiting state after the final report is sent: a card with a slow pulse and the waiting line; once the advocate has the enquiry, their line and
 * a button to open the consultation. Text is the caller's (the server's), so nothing here is invented.
 */
@Composable
internal fun AdvocateWaiting(state: AdvocateWait, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val (title, line) = when (state) { is AdvocateWait.Waiting -> state.title to state.line; is AdvocateWait.HandedOver -> state.title to state.line }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D.Raise).border(1.dp, D.Line2, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is AdvocateWait.Waiting) { Pulse(); Spacer(Modifier.width(10.dp)) }
            else { Icon(Icons.Outlined.Check, null, tint = D.Gold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
            // With no title (waiting) the card is the pulse and its small line (the enquiry's reference).
            if (title.isNotBlank()) Text(title, style = body(13.5f, D.ink(DeskSurface.Raised, DeskRole.Strong), FontWeight.SemiBold))
            else if (line.isNotBlank()) Text(line, style = body(12f, D.ink(DeskSurface.Raised, DeskRole.Secondary)))
        }
        if (title.isNotBlank() && line.isNotBlank()) Text(line, style = body(12f, D.ink(DeskSurface.Raised, DeskRole.Secondary)))
        if (state is AdvocateWait.HandedOver) Chips(listOf(Triple(state.button, true, onOpen)), topGap = 4.dp)
    }
}

// The desk playbook's own examples in the visitor's script (cases/config/desk_playbook.php), for the debug sample.
private const val SAMPLE_HINDI_VISITOR = "क्या मैं अपने मकान मालिक के खिलाफ केस जीत जाऊँगा?"
private const val SAMPLE_HINDI_DESK = "केस का नतीजा क्या होगा, यह मैं नहीं बता सकता, और अंदाज़ा लगाना ठीक नहीं होगा। वकील आपके कागज़ देखकर सही जवाब दे सकते हैं। आप Google से साइन इन करें, फिर एक वकील आपकी बात पढ़कर आमतौर पर 30 मिनट में जवाब देंगे।"
private const val SAMPLE_PUNJABI_VISITOR = "ਕੀ ਮੈਂ ਆਪਣੇ ਮਕਾਨ ਮਾਲਕ ਖ਼ਿਲਾਫ਼ ਕੇਸ ਜਿੱਤ ਜਾਵਾਂਗਾ?"
private const val SAMPLE_PUNJABI_DESK = "ਕੇਸ ਦਾ ਨਤੀਜਾ ਕੀ ਹੋਵੇਗਾ, ਇਹ ਮੈਂ ਨਹੀਂ ਦੱਸ ਸਕਦਾ, ਅਤੇ ਅੰਦਾਜ਼ਾ ਲਾਉਣਾ ਠੀਕ ਨਹੀਂ ਹੋਵੇਗਾ। ਵਕੀਲ ਤੁਹਾਡੇ ਕਾਗਜ਼ ਦੇਖ ਕੇ ਠੀਕ ਜਵਾਬ ਦੇ ਸਕਦੇ ਹਨ। ਤੁਸੀਂ Google ਨਾਲ ਸਾਈਨ ਇਨ ਕਰੋ, ਫਿਰ ਇੱਕ ਵਕੀਲ ਤੁਹਾਡੀ ਗੱਲ ਪੜ੍ਹ ਕੇ ਆਮ ਤੌਰ 'ਤੇ 30 ਮਿੰਟ ਵਿੱਚ ਜਵਾਬ ਦੇਵੇਗਾ।"

/** The activity that holds the kept conversation (the context may be wrapped). */
internal fun deskActivity(context: android.content.Context): androidx.activity.ComponentActivity {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper) { if (c is androidx.activity.ComponentActivity) return c; c = c.baseContext }
    error("The desk needs an activity")
}

/**
 * The web's Suggested Topics (reception.php): a heading with See all, three one-tap topics (six with See all). A tap puts the topic's starter in the question box and
 * focuses it; nothing is sent.
 */
@Composable
internal fun SuggestedTopics(onTopic: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = DESK_SIDE_MARGIN.dp).padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dt("ui_topics", "Suggested Topics"), style = body(15f, D.Ink, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Quiet(if (all) dt("ui_show_fewer", "Show Fewer") else dt("ui_see_all", "See All")) { all = !all }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
            DESK_TOPICS.take(if (all) DESK_TOPICS.size else DESK_TOPICS_SHOWN).forEach { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp))
                        .clickable { onTopic(t.starter) }.padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(t.title, style = body(14.5f, D.Ink, FontWeight.SemiBold))
                    Text(t.covers, style = body(12.5f, D.Muted))
                }
            }
        }
    }
}

/**
 * A typed change, formatted: when the formatter changes the text (it inserts a space, drops a leading 91), the cursor goes to the end, which is where a number being typed is;
 * when it leaves the text as typed, the cursor stays where the keyboard put it. Plain Kotlin, so typing digit by digit can be tested off-device.
 */
internal fun reformat(typed: TextFieldValue, format: (String) -> String): TextFieldValue {
    val f = format(typed.text)
    return if (f == typed.text) typed else TextFieldValue(f, TextRange(f.length))
}

/** The desk's debug-only "Test Contact": a debug build with the test contact configured, never a release build. */
internal fun deskTestContactOn(debug: Boolean, email: String, password: String): Boolean = debug && email.isNotBlank() && password.isNotBlank()

/** The consultation-first choice (reception.php dockChoice): one card each, the consultation first and filled, the written enquiry outlined. Drawn in the conversation. */
@Composable
private fun CardsList(block: ChoiceBlock, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.cards.forEach { c ->
            val main = c.key == "consultation"
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (main) D.Gold else D.Line2, RoundedCornerShape(12.dp))
                    .background(if (main) D.Raise else Color.Transparent).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title, style = body(15f, D.Ink, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text(c.price, style = body(15f, D.Ink, FontWeight.SemiBold))
                }
                if (c.intro.isNotBlank()) Text(c.intro, style = body(12.5f, D.Muted), modifier = Modifier.padding(top = 4.dp))
                if (c.includes.isNotBlank()) Text(c.includes, style = body(13f, D.Ink.copy(alpha = 0.85f)), modifier = Modifier.padding(top = 6.dp))
                if (c.timing.isNotBlank()) Text(c.timing, style = body(12.5f, D.Muted), modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (main) D.Gold else Color.Transparent)
                        .border(1.5.dp, if (main) D.Gold else D.Line2, RoundedCornerShape(22.dp)).clickable { onPick(c.key) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.button, style = body(14f, if (main) D.OnGold else D.Ink, FontWeight.SemiBold), textAlign = TextAlign.Center) }
            }
        }
    }
}

/** Under the cards: a box for a question (a choice typed in words picks the card) and a quiet Not Now. Short, so it always stays above the keyboard. */
@Composable
private fun CardsDock(error: String, onText: (String) -> Unit, onNotNow: () -> Unit) {
    Column {
        Compose(dt("ph_ask_question", CARD_ASK_PLACEHOLDER_EN), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), description = dt("sr_answer", "Your answer"), onSend = onText)
        ErrorLine(error)
        Quiet(dt("ui_not_now", "Not Now"), onNotNow)
    }
}

/** After the consultation is chosen: one optional line about the matter, and a quiet Skip (reception.php insistMatter). */
@Composable
private fun MatterDock(error: String, onGo: (String) -> Unit, onSkip: () -> Unit) {
    Column {
        Compose(dt("ph_answer", "Your answer…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), description = dt("sr_answer", "Your answer"), onSend = onGo)
        ErrorLine(error)
        Quiet(dt("ui_skip", "Skip"), onSkip)
    }
}

/** The Agreement Health Check is on offer: its button above the message box, which stays open for questions about the service (reception.php routeBar). */
@Composable
private fun RouteDock(route: RouteInfo, error: String, onOpen: () -> Unit, onSend: (String) -> Unit) {
    Column {
        ChipGroup(listOf(ChipSpec(route.button.ifBlank { dt("ui_open_ahc", "Open Agreement Health Check") }, true, "", onOpen)), topGap = 0.dp)
        Spacer(Modifier.height(8.dp))
        Compose(dt("ph_ahc_ask", "Ask about the Agreement Health Check…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), description = dt("sr_answer", "Your answer"), onSend = onSend)
        ErrorLine(error)
    }
}

/**
 * A question with kinds of help to tick (F-029, reception.php dockGather): the question is the heading, "Choose one or more.", tick rows with "I'm not sure" last,
 * one full-width button that reads "Choose One Or More" until a tick and then "Send N Choices", and the quiet links Write My Own Instead and Skip The Questions.
 * Write My Own Instead opens the ordinary box for the same question with the ticks kept. Only the rows scroll; the question, the button and the links stay in reach.
 */
@Composable
private fun OptionsDock(
    d: Dock.Options, error: String, onToggle: (Int) -> Unit, onSend: () -> Unit, onWrite: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, onText: (String) -> Unit,
) {
    val book = LocalDeskBook.current
    if (d.writing) {
        Column {
            if (d.picks.isNotEmpty()) Text(
                book.fragment("opt_with_choices", mapOf("list" to d.picks.sorted().mapNotNull { d.options.getOrNull(it) }.joinToString("; "))) ?: OPT_WITH_CHOICES_EN.replace("{list}", d.picks.sorted().mapNotNull { d.options.getOrNull(it) }.joinToString("; ")),
                style = body(12.5f, D.Muted), modifier = Modifier.padding(bottom = 6.dp),
            )
            Compose(dt("ph_answer", "Your answer…"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false), description = dt("sr_answer", "Your answer"), sendReady = d.picks.isNotEmpty(), onSend = onText)
            ErrorLine(error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Quiet(dt("ui_back_choices", "Back To The Choices"), onBack)
                Text("·", style = body(13f, D.Muted), modifier = Modifier.padding(horizontal = 4.dp))
                Quiet(dt("ui_skip_questions", GATHER_SEND_NOW), onSkip)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(d.question, style = body(15f, D.Ink, FontWeight.SemiBold))
        Text(dt("opt_choose", OPT_CHOOSE_EN), style = body(12.5f, D.Muted), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f, fill = false)) {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.options.forEachIndexed { i, o ->
                    val on = i in d.picks
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, if (on) D.Gold else D.Line2, RoundedCornerShape(8.dp))
                            .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle(i) }).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(if (on) D.Gold else Color.Transparent).border(1.5.dp, if (on) D.Gold else D.Line2, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Outlined.Check, null, tint = D.OnGold, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(o, style = body(14f, D.Ink))
                    }
                }
            }
            // The fade at the foot shows only while more rows follow.
            if (scroll.canScrollForward) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, D.Page))))
        }
        Spacer(Modifier.height(10.dp))
        val ready = d.picks.isNotEmpty()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (ready) D.Gold else D.Line2).clickable(enabled = ready, onClick = onSend).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(sendChoicesLabel(d.picks.size, book), style = body(14f, if (ready) D.OnGold else D.Muted, FontWeight.SemiBold), textAlign = TextAlign.Center) }
        ErrorLine(error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Quiet(dt("ui_write_own", "Write My Own Instead"), onWrite)
            Text("·", style = body(13f, D.Muted), modifier = Modifier.padding(horizontal = 4.dp))
            Quiet(dt("ui_skip_questions", GATHER_SEND_NOW), onSkip)
        }
    }
}
