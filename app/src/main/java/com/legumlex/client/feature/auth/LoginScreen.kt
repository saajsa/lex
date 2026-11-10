package com.legumlex.client.feature.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.grain

// Sign In (REQ-013): one card (the title, a Google pill, the mobile number with the round arrow that
// sends the code, and a quiet link to email and password), then a black "New Here" card that opens
// Join. Email and the code step open in place; back returns to the card. Debug builds carry one
// small chip with the test sign-ins.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    /** Lex Intelligence before sign-in: the desk, as a guest. */
    onAskLexIntelligence: () -> Unit = {},
    /** Discover: Join, the screen of Onboard Now and Register As Counsel. */
    onJoin: () -> Unit = {},
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
    // Whether newcomers may open an account (google_config's sign_up), as the web's sign-in page asks.
    var signUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        signUp = runCatching { ApiClient.get(context).googleConfig()["sign_up"] == true }.getOrDefault(false)
    }

    LaunchedEffect(state) {
        if (state is LoginState.Success) onLoginSuccess()
    }
    val onCodeStep = mobile.ref != null
    BackHandler(enabled = view != LoginView.Choose || onCodeStep) { if (onCodeStep) vm.changeNumber() else vm.show(LoginView.Choose) }

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

    Box(Modifier.fillMaxSize().background(Lex.colors.background).grain()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.s4)
                .padding(top = spacing.s3, bottom = spacing.s4),
        ) {
            // Our monogram on the left, Lex Intelligence (small) on the right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LegumMark(size = 34.dp, fillColor = PrimaryText)
                Spacer(Modifier.weight(1f))
                LexOrb(enabled = !isLoading, onOpen = onAskLexIntelligence, compact = true)
            }
            Spacer(Modifier.height(spacing.s5))

            // Inline error per handoff UX rule 5 ("confirmation, not interruption").
            (state as? LoginState.Error)?.let { err ->
                Banner(message = err.message, palette = StatusDanger, onDismiss = vm::clearError)
                Spacer(Modifier.height(spacing.s4))
            }

            // The card.
            val cardShape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(cardShape).background(Lex.colors.card).border(1.dp, BorderColor, cardShape).padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (onCodeStep) "Enter The Code" else "Sign In",
                        style = MaterialTheme.typography.headlineMedium, color = PrimaryText, modifier = Modifier.weight(1f),
                    )
                    if (!onCodeStep) GooglePill(busy = isLoading, onClick = signInWithGoogle)
                }
                Spacer(Modifier.height(spacing.s5))
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
                            LoginView.Choose -> {
                                NumberRow(vm = vm, mobile = mobile, busy = isLoading)
                                Spacer(Modifier.height(spacing.s3))
                                LinkButton(
                                    text = "Use Email And Password", enabled = !isLoading,
                                    onClick = { vm.show(LoginView.Email) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                )
                            }
                            LoginView.Mobile -> CodeStep(vm = vm, mobile = mobile, busy = isLoading)
                            LoginView.Email -> {
                                EmailView(vm = vm, busy = isLoading)
                                Spacer(Modifier.height(spacing.s2))
                                LinkButton(
                                    text = "Back", enabled = !isLoading,
                                    onClick = { vm.show(LoginView.Choose) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                )
                            }
                        }
                    }
                }
            }

            // New Here: a black card, only when the server allows sign-up; Discover opens Join.
            if (discoverShown(signUp)) {
                Spacer(Modifier.height(spacing.s3))
                NewHereCard(enabled = !isLoading, onDiscover = onJoin)
            }

            // Debug builds only: one small chip with the test sign-ins; nothing in a release build.
            val debug = debugSignIns(BuildConfig.DEBUG, BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_ADV_PASSWORD, BuildConfig.DEV_ADV_EMAIL, BuildConfig.DEV_ADV_EMPTY_EMAIL)
            if (debug.isNotEmpty()) {
                Spacer(Modifier.height(spacing.s3))
                DebugChip(
                    items = debug, enabled = !isLoading,
                    onPick = { d ->
                        when (d) {
                            DebugSignIn.TestContact -> vm.onEmailLogin(BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_LOGIN_PASSWORD)
                            DebugSignIn.CounselWithCases -> vm.onEmailLogin(BuildConfig.DEV_ADV_EMAIL, BuildConfig.DEV_ADV_PASSWORD)
                            DebugSignIn.CounselEmpty -> vm.onEmailLogin(BuildConfig.DEV_ADV_EMPTY_EMAIL, BuildConfig.DEV_ADV_PASSWORD)
                        }
                    },
                )
            }
        }
    }
}

/** Discover (the black card) is offered only when the server lets newcomers open an account: google_config's sign_up. */
internal fun discoverShown(signUp: Boolean): Boolean = signUp

/** The debug-build test sign-ins (REQ-013): the test contact, and the two counsel accounts. */
enum class DebugSignIn(val label: String) {
    TestContact("Test Contact"), CounselWithCases("Counsel · With Cases"), CounselEmpty("Counsel · Empty"),
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

/** The small Google pill on the title line. */
@Composable
private fun GooglePill(busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, BorderColor, shape)
            .clickable(enabled = !busy, onClickLabel = "Continue with Google", onClick = onClick)
            .semantics { contentDescription = "Continue with Google" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryText)
        else Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(16.dp))
        Text("Google", style = MaterialTheme.typography.labelMedium, color = PrimaryText)
    }
}

/** "New Here": black, whatever the theme; the line says who it is for, and Discover sits at the foot on the right. */
@Composable
private fun NewHereCard(enabled: Boolean, onDiscover: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(NewHereBlack)
            // On the dark page the black card needs its edge: a hairline in the outline colour, dark theme only.
            .then(if (Lex.colors.isDark) Modifier.border(1.dp, Lex.colors.input, shape) else Modifier)
            .clickable(enabled = enabled, onClickLabel = "Discover", onClick = onDiscover)
            .padding(20.dp),
    ) {
        Text("New Here", style = MaterialTheme.typography.headlineMedium, color = NewHereText)
        Text("Clients and advocates", style = MaterialTheme.typography.titleMedium, color = NewHereMuted)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", style = MaterialTheme.typography.labelLarge, color = NewHereText)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = NewHereText, modifier = Modifier.size(18.dp))
        }
    }
}

internal val NewHereBlack = androidx.compose.ui.graphics.Color(0xFF151513)
internal val NewHereText = androidx.compose.ui.graphics.Color(0xFFF4F3EF)
internal val NewHereMuted = androidx.compose.ui.graphics.Color(0xFFA3A19A)

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
            Icon(Icons.Outlined.Phone, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
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
    Text("The number you verified on your profile. The code comes on WhatsApp, or by SMS.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    LinkButton(
        text = if (abroad) "An Indian Number" else "Number Outside India?", enabled = !busy, flush = true,
        onClick = { if (abroad) { abroad = false; vm.onNumberChange("") } else { abroad = true; vm.onNumberChange("+") } },
    )
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

/**
 * The compact Lex Intelligence prompt (the orb): the yellow disc with its blinking cursor and an "AI" badge. Touched,
 * a ring runs once round it, then [onOpen]. [compact] is the small one beside the monogram, without its name.
 */
@Composable
private fun LexOrb(enabled: Boolean, onOpen: () -> Unit, compact: Boolean = false) {
    val outer = if (compact) 44.dp else 96.dp
    val inner = if (compact) 30.dp else 64.dp
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
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
    val track = PrimaryText.copy(alpha = 0.15f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(outer)) {
        Box(
            Modifier.fillMaxSize()
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(enabled = enabled && !running, onClickLabel = "Lex Intelligence") {
                    running = true
                    scope.launch {
                        sweep.snapTo(0f)
                        sweep.animateTo(360f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        onOpen()
                        running = false
                    }
                }
                .semantics { contentDescription = "Lex Intelligence" },
            contentAlignment = Alignment.Center,
        ) {
            // The ring, round the button.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = (if (compact) 2.dp else 3.dp).toPx()
                drawCircle(track, radius = size.minDimension / 2 - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                if (sweep.value > 0f) drawArc(LexYellow, -90f, sweep.value, false,
                    topLeft = androidx.compose.ui.geometry.Offset(w, w),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Box(
                Modifier.size(inner).graphicsLayer { val k = if (running) 1f else breath; scaleX = k; scaleY = k }
                    .clip(androidx.compose.foundation.shape.CircleShape).background(LexYellow),
                contentAlignment = Alignment.Center,
            ) {
                val mono = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = if (compact) 11.sp else 21.sp, color = androidx.compose.ui.graphics.Color.Black)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(">", style = mono)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.padding(bottom = if (compact) 3.dp else 7.dp).size(width = if (compact) 6.dp else 11.dp, height = if (compact) 2.dp else 3.dp)
                        .graphicsLayer { alpha = blink }.background(androidx.compose.ui.graphics.Color.Black))
                }
            }
        }
            // The badge, outside the round touch area so it is not clipped.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = if (compact) 4.dp else (-6).dp, y = if (compact) (-2).dp else 8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(NewHereBlack)
                    .padding(horizontal = if (compact) 3.dp else 5.dp, vertical = if (compact) 0.dp else 1.dp),
            ) {
                Text("AI", color = androidx.compose.ui.graphics.Color.White, fontSize = if (compact) 6.sp else 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.4.sp)
            }
        }
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            Text("Lex Intelligence", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        }
    }
}

/** The counsel desk's yellow (the bottom bar's Consult). */
private val LexYellow = androidx.compose.ui.graphics.Color(0xFFF5C518)

@Composable
private fun ColumnScope.EmailView(vm: LoginViewModel, busy: Boolean) {
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

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
