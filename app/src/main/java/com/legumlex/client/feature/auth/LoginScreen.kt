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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    BackHandler(enabled = view != LoginView.Choose) { vm.show(LoginView.Choose) }

    // A white panel with the mark on a black tile, under the status bar,
    // and a black sheet laid over its foot with the ways in (the dark
    // palette, in either theme). The shell sets the bar icons to match.
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        Hero()
        com.legumlex.client.ui.theme.LexDarkRegion {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = HeroHeight - 28.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 0.dp, topEnd = 28.dp))
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
                LoginView.Choose -> "Sign in to see what is happening with your matters."
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
                    LoginView.Choose -> ChooseView(
                        busy     = isLoading,
                        onGoogle = {
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
                        },
                        onMobile = { vm.show(LoginView.Mobile) },
                        onEmail  = { vm.show(LoginView.Email) },
                    )
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

        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(spacing.s6))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Someone new becomes a client through the Legum Lex desk on the
            // website (Consult, Google sign-in), as they do from the site.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Text(
                "New to Legum Lex?",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
            )
            TextButton(
                onClick = { openSite(ctx, "?desk=1") },
                colors  = ButtonDefaults.textButtonColors(contentColor = AccentInk),
            ) {
                Text("Consult Us", style = MaterialTheme.typography.labelLarge)
            }
        }
        }
        }
    }
}

/**
 * The white panel: a flat pattern of circles, halves and quarters in two
 * pale greys (no gradient), and the white mark on a black tile at its centre.
 */
private val HeroHeight = 300.dp

@Composable
private fun Hero() {
    val c1 = androidx.compose.ui.graphics.Color(0xFFF4F4F4)
    val c2 = androidx.compose.ui.graphics.Color(0xFFECECEC)
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val cell = 64.dp.toPx()
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            for (r in 0 until rows) for (k in 0 until cols) {
                val x = k * cell; val y = r * cell
                val tone = if ((r + k) % 2 == 0) c1 else c2
                val tl = androidx.compose.ui.geometry.Offset(x, y)
                val sz = androidx.compose.ui.geometry.Size(cell, cell)
                when ((r * 7 + k * 3) % 6) {
                    0 -> drawCircle(tone, cell / 2, androidx.compose.ui.geometry.Offset(x + cell / 2, y + cell / 2))
                    1 -> drawArc(tone, 180f, 180f, true, androidx.compose.ui.geometry.Offset(x, y + cell / 2), androidx.compose.ui.geometry.Size(cell, cell))
                    2 -> drawArc(tone, 0f, 90f, true, androidx.compose.ui.geometry.Offset(x - cell, y - cell), androidx.compose.ui.geometry.Size(cell * 2, cell * 2))
                    3 -> drawRect(tone, tl, sz)
                    4 -> drawArc(tone, 90f, 180f, true, androidx.compose.ui.geometry.Offset(x + cell / 2, y), androidx.compose.ui.geometry.Size(cell, cell))
                    else -> {}
                }
            }
        }
        Box(
            Modifier
                .statusBarsPadding()
                .size(84.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            LegumMark(size = 40.dp, fillColor = androidx.compose.ui.graphics.Color.White)
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
) {
    val spacing = LocalSpacing.current
    ChoiceButton(
        text    = if (busy) "Signing in…" else "Continue with Google",
        enabled = !busy,
        onClick = onGoogle,
        icon    = { Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
    Spacer(Modifier.height(spacing.s3))
    ChoiceButton(
        text    = "Continue with Mobile",
        enabled = !busy,
        onClick = onMobile,
        icon    = { Icon(Icons.Outlined.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
    Spacer(Modifier.height(spacing.s4))
    LinkButton(
        text     = "Sign In with Email and Password",
        enabled  = !busy,
        onClick  = onEmail,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
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

    // Debug-only quick-fill so we don't keep typing creds during dev. The
    // credentials come from local.properties (DEV_LOGIN_EMAIL/_PASSWORD), never
    // from source; without them, and in every release build, there's no button.
    if (BuildConfig.DEBUG && BuildConfig.DEV_LOGIN_EMAIL.isNotBlank()) {
        TextButton(
            onClick = {
                email = BuildConfig.DEV_LOGIN_EMAIL
                password = BuildConfig.DEV_LOGIN_PASSWORD
            },
            colors  = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
        ) {
            Text("Dev · Fill Test Sign-In",
                style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(spacing.s2))
    }

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
        LegumTextField(
            value = mobile.number,
            onValueChange = vm::onNumberChange,
            label = "Mobile Number",
            leadingIcon = Icons.Outlined.Smartphone,
            keyboardType = KeyboardType.Phone,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            supportingText = "The number you verified on your profile. The code comes on WhatsApp, or by SMS.",
        )
        Spacer(Modifier.height(spacing.s4))
        LegumPrimaryButton(
            text    = if (busy) "Sending…" else "Send Code",
            onClick = vm::onSendCode,
            enabled = mobile.number.isNotBlank() && !busy,
        )
        return
    }

    Text(mobile.sentNote, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    Spacer(Modifier.height(spacing.s3))
    mobile.info?.let {
        Banner(message = it, palette = StatusSettled)
        Spacer(Modifier.height(spacing.s3))
    }
    LegumTextField(
        value = code,
        onValueChange = {
            code = it.filter(Char::isDigit).take(6)
            vm.clearError()
            if (code.length == 6 && !busy) vm.onCode(code)
        },
        label = "6-Digit Code",
        leadingIcon = Icons.Outlined.Lock,
        keyboardType = KeyboardType.NumberPassword,
        enabled = !busy,
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

private suspend fun requestGoogleIdToken(
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

private fun Context.requireActivity(): ComponentActivity {
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
