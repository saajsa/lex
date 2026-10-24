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

// Screen 03 — sign-in, as on the web door: two choices, Google and Mobile,
// with email + password a link below them. Each opens in place (the views
// cross-fade and rise); back returns to the choices.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    /** Lex Intelligence before sign-in: the desk, as a guest. */
    onAskLexIntelligence: () -> Unit = {},
    /** New here? Onboard Now: Lex Intelligence, opening an account (owner, 2026-10-24). */
    onOpenAccount: () -> Unit = {},
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
    BackHandler(enabled = view != LoginView.Choose) { vm.show(LoginView.Choose) }

    // A white panel with the mark on a black tile, under the status bar,
    // and a black sheet laid over its foot with the ways in (the dark
    // palette, in either theme). The shell sets the bar icons to match.
    // With the keyboard up the panel gives way, so the fields and the button
    // stay in view.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val ime = WindowInsets.isImeVisible
    val heroHeight by androidx.compose.animation.core.animateDpAsState(if (ime) 150.dp else HeroHeight, label = "hero")
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        Hero(heroHeight)
        com.legumlex.client.ui.theme.LexDarkRegion {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = heroHeight - 28.dp)
                .clip(SheetShape)
                .background(androidx.compose.ui.graphics.Color.Black)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.s4)
                .padding(top = spacing.s6, bottom = spacing.s2),
        ) {
        // Not "Welcome back": many arrive here for the first time.
        Text(
            "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.s2))
        Text(
            when (view) {
                LoginView.Choose -> "Clients see their matters here."
                LoginView.Mobile -> "Sign in with a code sent to your mobile."
                LoginView.Email  -> "Sign in with your email and password."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(spacing.s6))

        // Inline error per handoff UX rule 5 ("confirmation, not interruption").
        (state as? LoginState.Error)?.let { err ->
            Banner(message = err.message, palette = StatusDanger, onDismiss = vm::clearError)
            Spacer(Modifier.height(spacing.s4))
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
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 12 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "login-view",
        ) { v ->
            Column(Modifier.fillMaxWidth()) {
                when (v) {
                    LoginView.Choose -> {
                    ChooseView(
                        busy     = isLoading,
                        onGoogle = signInWithGoogle,
                        onMobile = { vm.show(LoginView.Mobile) },
                        onEmail  = { vm.show(LoginView.Email) },
                        onDev    = { vm.onEmailLogin(BuildConfig.DEV_LOGIN_EMAIL, BuildConfig.DEV_LOGIN_PASSWORD) },
                    )
                    if (signUp) {
                        // A newcomer's own way in (owner, 2026-10-24): set apart
                        // from signing in, as a button of its own.
                        Spacer(Modifier.height(spacing.s5))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(BorderColor))
                            Text("New To The Firm?", style = MaterialTheme.typography.labelMedium, color = SecondaryText,
                                modifier = Modifier.padding(horizontal = 12.dp))
                            Box(Modifier.weight(1f).height(1.dp).background(BorderColor))
                        }
                        Spacer(Modifier.height(spacing.s4))
                        OutlinedButton(
                            onClick  = onOpenAccount,
                            enabled  = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(50),
                            border   = BorderStroke(1.dp, PrimaryText),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                        ) {
                            Icon(Icons.Outlined.HowToReg, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Onboard Now", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(spacing.s2))
                        Text("Your PAN, Aadhaar and mobile number. The firm approves every new account.",
                            style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    }
                    LoginView.Mobile -> MobileView(vm = vm, mobile = mobile, busy = isLoading)
                    LoginView.Email  -> EmailView(vm = vm, busy = isLoading)
                }
                if (v != LoginView.Choose) {
                    Spacer(Modifier.height(spacing.s4))
                    LinkButton(
                        text     = "Other Ways to Sign In",
                        enabled  = !isLoading,
                        onClick  = { vm.show(LoginView.Choose) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        // Lex Intelligence, in the space beneath (owner, 2026-10-20): the bar's
        // Consult, its cursor blinking; a touch draws a ring once round it, and
        // the desk opens when the ring closes.
        Box(Modifier.weight(1f, fill = true).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (view == LoginView.Choose) LexOrb(enabled = !isLoading, onOpen = onAskLexIntelligence)
        }
        Spacer(Modifier.height(spacing.s6))

        }
        }
    }
}

private val HeroHeight = 300.dp

/**
 * The sign-in sheet, edge to edge: square at the top left, and at the top
 * right a large quarter circle that starts well in from the edge.
 */
private val SheetShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = with(density) { 84.dp.toPx() }
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(w - r, 0f)
            // A true quarter circle, as the reference draws it: the earlier
            // long-handled cubic bulged square in its middle and looked odd.
            arcTo(androidx.compose.ui.geometry.Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, size.height)
            lineTo(0f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** The logo panel: flat white, and the black mark on it, nothing else. */
@Composable
private fun Hero(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(androidx.compose.ui.graphics.Color.White),
    ) {
        // Centred in what shows: below the status bar, above the sheet's lip.
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 28.dp), contentAlignment = Alignment.Center) {
            LegumMark(size = 56.dp, fillColor = androidx.compose.ui.graphics.Color.Black)
        }
    }
}

// The two choices, each a full-width button with its mark, and email +
// password as a link beneath them.
@Composable
private fun ColumnScope.ChooseView(
    busy: Boolean,
    onGoogle: () -> Unit,
    onMobile: () -> Unit,
    onEmail: () -> Unit,
    onDev: () -> Unit,
) {
    val spacing = LocalSpacing.current
    // The two ways in, as their marks alone: Google and a mobile code.
    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MarkButton("Continue with Google", !busy, onGoogle) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PrimaryText)
            else Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(26.dp))
        }
        MarkButton("Continue with Mobile", !busy, onMobile) {
            Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(26.dp))
        }
    }
    Spacer(Modifier.height(spacing.s5))
    LinkButton(
        text     = "Sign In with Email and Password",
        enabled  = !busy,
        onClick  = onEmail,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    // Debug builds only: one tap signs in as the dev test contact, whose
    // email and password come from local.properties (DEV_LOGIN_EMAIL/_PASSWORD),
    // never from source. Release builds carry neither, and show nothing.
    if (BuildConfig.DEBUG && BuildConfig.DEV_LOGIN_EMAIL.isNotBlank()) {
        Spacer(Modifier.height(spacing.s2))
        OutlinedButton(
            onClick  = onDev,
            enabled  = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape    = MaterialTheme.shapes.small,
            border   = BorderStroke(1.dp, BorderColor),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
        ) {
            Text("Debug · Sign In as Test Contact", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The counsel desk's yellow (the bottom bar's Consult). */
private val LexYellow = androidx.compose.ui.graphics.Color(0xFFF5C518)

/**
 * Lex Intelligence: the yellow prompt with its blinking cursor and an "AI"
 * badge. Touched, a ring runs once round it, then [onOpen]; back here later,
 * the ring is gone and it can be touched again.
 */
@Composable
private fun LexOrb(enabled: Boolean, onOpen: () -> Unit) {
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(530); on = !on } }
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp)) {
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
                val w = 3.dp.toPx()
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), radius = size.minDimension / 2 - w,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                if (sweep.value > 0f) drawArc(LexYellow, -90f, sweep.value, false,
                    topLeft = androidx.compose.ui.geometry.Offset(w, w),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Box(
                Modifier.size(64.dp).graphicsLayer { val k = if (running) 1f else breath; scaleX = k; scaleY = k }
                    .clip(androidx.compose.foundation.shape.CircleShape).background(LexYellow),
                contentAlignment = Alignment.Center,
            ) {
                val mono = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 21.sp, color = androidx.compose.ui.graphics.Color.Black)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(">", style = mono)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.padding(bottom = 7.dp).size(width = 11.dp, height = 3.dp)
                        .background(if (on) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
            // The badge, outside the round touch area so it is not clipped.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.White)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("AI", color = androidx.compose.ui.graphics.Color.Black, fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.4.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Lex Intelligence", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
    }
}

/** A round button carrying only a mark; its name is read out, not shown. */
@Composable
private fun MarkButton(label: String, enabled: Boolean, onClick: () -> Unit, mark: @Composable () -> Unit) {
    Box(
        Modifier.size(68.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, BorderColor, androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { mark() }
}

@Composable
private fun ChoiceButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        border   = BorderStroke(1.dp, BorderColor),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColumnScope.EmailView(vm: LoginViewModel, busy: Boolean) {
    val spacing = LocalSpacing.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

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
        trailing = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = TertiaryText,
                )
            }
        },
    )

    Spacer(Modifier.height(spacing.s2))

    val fpCtx = androidx.compose.ui.platform.LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            // The portal's own reset: a link to the address on file.
            onClick = { openSite(fpCtx, "authentication/forgot_password") },
            colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
            enabled = !busy,
        ) {
            Text("Forgot Password?", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(Modifier.height(spacing.s3))

    LegumPrimaryButton(
        text    = if (busy) "Signing in…" else "Sign In",
        onClick = { vm.onEmailLogin(email.trim(), password) },
        enabled = email.isNotBlank() && password.isNotBlank() && !busy,
    )
}

// Sign-in by mobile: the number, then the 6-digit code sent on WhatsApp.
// After 30 seconds the server may offer the same code by SMS; after a minute
// a new code can be asked for.
@Composable
private fun ColumnScope.MobileView(vm: LoginViewModel, mobile: MobileState, busy: Boolean) {
    val spacing = LocalSpacing.current
    val onCodeStep = mobile.ref != null
    var code by remember(mobile.ref) { mutableStateOf("") }

    Text(
        if (onCodeStep) "STEP 2 OF 2 · THE CODE" else "STEP 1 OF 2 · YOUR NUMBER",
        style = MaterialTheme.typography.labelSmall,
        color = TertiaryText,
    )
    Spacer(Modifier.height(spacing.s3))

    if (!onCodeStep) {
        // An Indian mobile goes in ten boxes after +91; a number outside
        // India (the door takes those too) in a field with its own code.
        var abroad by rememberSaveable { mutableStateOf(mobile.number.startsWith("+") && !mobile.number.startsWith("+91")) }
        if (!abroad) {
            val local = mobile.number.filter(Char::isDigit).let {
                when {
                    it.length == 12 && it.startsWith("91") -> it.drop(2)
                    it.length == 11 && it.startsWith("0")  -> it.drop(1)
                    else -> it
                }
            }.take(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+91", style = MaterialTheme.typography.titleMedium, color = SecondaryText)
                Spacer(Modifier.width(10.dp))
                DigitBoxes(
                    value = local,
                    length = 10,
                    onValueChange = vm::onNumberChange,
                    enabled = !busy,
                    keyboardType = KeyboardType.Phone,
                    groupAfter = 5,
                    digitStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(spacing.s2))
            Text("The number you verified on your profile. The code comes on WhatsApp, or by SMS.",
                style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            LinkButton(text = "Number Outside India?", enabled = !busy,
                onClick = { abroad = true; vm.onNumberChange("+") })
        } else {
            LegumTextField(
                value = mobile.number,
                onValueChange = vm::onNumberChange,
                label = "Mobile Number, With Country Code",
                leadingIcon = Icons.Outlined.Smartphone,
                keyboardType = KeyboardType.Phone,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                supportingText = "The number you verified on your profile, as +44 7700 900123. The code comes on WhatsApp, or by SMS.",
            )
            LinkButton(text = "An Indian Number", enabled = !busy,
                onClick = { abroad = false; vm.onNumberChange("") })
        }
        Spacer(Modifier.height(spacing.s3))
        LegumPrimaryButton(
            text    = if (busy) "Sending…" else "Send Code",
            onClick = vm::onSendCode,
            enabled = (if (abroad) mobile.number.count(Char::isDigit) >= 8 else mobile.number.length == 10) && !busy,
        )
        return
    }

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
) {
    TextButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
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
