package com.legumlex.client.feature.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LegumTextField
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.TertiaryText
import kotlinx.coroutines.launch

// Screen 03 — handoff §Login.
// Email/password is the primary form (works for every client, including those
// without Gmail). "Continue with Google" sits below the OR divider as a
// quicker alternative — taking the slot the handoff originally gave biometric.
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegister: () -> Unit,
) {
    val spacing  = LocalSpacing.current
    val context  = LocalContext.current
    val activity = context.requireActivity()
    val scope    = rememberCoroutineScope()
    val vm: LoginViewModel = viewModel()
    val state by vm.state.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val isLoading = state is LoginState.Loading
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !isLoading

    LaunchedEffect(state) {
        if (state is LoginState.Success) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s4)
            .padding(top = spacing.s6, bottom = spacing.s5),
    ) {
        LegumMark(
            size = 36.dp,
            fillColor = AccentInk,
        )

        Spacer(Modifier.height(spacing.s8))

        Text("Welcome back.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.s2))
        Text(
            "Sign in to see what's happening with your matters.",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )

        // Debug-only quick-fill so we don't keep typing creds during dev.
        // BuildConfig.DEBUG is wiped in release builds, so this button + the
        // hardcoded string both get stripped from the production APK.
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(spacing.s4))
            TextButton(
                onClick = {
                    email = "saaj.asingha@legumlex.com"
                    password = "test1234"
                },
                colors  = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
                modifier = Modifier.align(androidx.compose.ui.Alignment.Start),
            ) {
                Text("DEV · LOGIN AS ADMIN",
                    style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(spacing.s4))

        LegumTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = Icons.Outlined.Mail,
            keyboardType = KeyboardType.Email,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(spacing.s3))

        LegumTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            leadingIcon = Icons.Outlined.Lock,
            isPassword = !showPassword,
            enabled = !isLoading,
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { /* Forgot flow — not in v1 scope. */ },
                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
                enabled = !isLoading,
            ) {
                Text("Forgot password?", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(spacing.s3))

        LegumPrimaryButton(
            text    = if (isLoading) "Signing in…" else "Sign In",
            onClick = { vm.onEmailLogin(email.trim(), password.trim()) },
            enabled = canSubmit,
        )

        // Inline error per handoff UX rule 5 ("confirmation, not interruption").
        (state as? LoginState.Error)?.let { err ->
            Spacer(Modifier.height(spacing.s3))
            ErrorBanner(message = err.message, onDismiss = vm::clearError)
        }

        Spacer(Modifier.height(spacing.s5))

        OrDivider()

        Spacer(Modifier.height(spacing.s5))

        LegumSecondaryButton(
            text        = "Continue with Google",
            leadingIcon = Icons.AutoMirrored.Outlined.Login,
            enabled     = !isLoading,
            onClick     = {
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
        )

        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(spacing.s6))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "New client?",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
            )
            TextButton(
                onClick = onRegister,
                colors  = ButtonDefaults.textButtonColors(contentColor = AccentInk),
            ) {
                Text("Register", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OrDivider() {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
        Text(
            "OR",
            style = MaterialTheme.typography.labelSmall,
            color = TertiaryText,
            modifier = Modifier.padding(horizontal = spacing.s3),
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = StatusDanger.softBg,
        contentColor = StatusDanger.softText,
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
                color = StatusDanger.softText,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = StatusDanger.softText),
            ) {
                Text("Dismiss", style = MaterialTheme.typography.labelLarge)
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
                onError("Couldn't read Google credential: ${e.message}")
            }
        } else {
            onError("Unexpected credential type. Please try again.")
        }
    } catch (_: GetCredentialCancellationException) {
        onCancel()
    } catch (_: NoCredentialException) {
        onError("No Google accounts on this device. Sign in with email above, or add a Google account in Settings.")
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
