package com.legumlex.client.feature.counsel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.BuildConfig
import com.legumlex.client.R
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.feature.auth.requestGoogleIdToken
import com.legumlex.client.feature.auth.requireActivity
import com.legumlex.client.feature.board.Cormorant
import com.legumlex.client.feature.board.rememberReducedMotion
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.Display
import kotlinx.coroutines.launch

private val Room = Color(0xFF151513)
private val Gold = Color(0xFFF5C518)
private val Chalk = Color(0xFFF4F3EF)
private val Dim = Color(0xFFA3A19A)
private val Faint = Color(0xFF8A877F)
private val Ease = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * Counsel registration, the door (REQ-010, canvas 10c): its own dark screen, apart from the client sign-up. Google first, then
 * the server's answer to "who is this?" (new, client, counsel, pending, rejected, staff), each with its own words; a new or
 * rejected advocate goes on to the form. A person who is already a client is simply signed in, with a note.
 */
@Composable
fun CounselRegisterScreen(
    onSignIn: () -> Unit,
    onForm: () -> Unit,
    onSignedInAsClient: () -> Unit,
    /** An existing client whose account can take the Counsel workspace: straight into Add Counsel Workspace. */
    onAddCounsel: () -> Unit = onSignedInAsClient,
) {
    val context = LocalContext.current
    val activity = context.requireActivity()
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository(ApiClient.get(context), TokenStore(context.applicationContext), context.cacheDir) }
    val repo = remember { CounselRepository(context) }
    val still = rememberReducedMotion()
    // A lead already signed in (from their own app) goes straight to the triage: no second Google sign-in, and nothing is forgotten on the way back.
    val storedLead = remember { com.legumlex.client.data.auth.SessionRole.isLead.also { CounselFlow.keepSession = it } }

    // door | checking | outcome | client
    var stage by rememberSaveable { mutableStateOf("door") }
    var triage by remember { mutableStateOf<CounselTriage?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var addCounsel by remember { mutableStateOf(false) }

    suspend fun leave() { if (!storedLead) auth.forget(); stage = "door"; triage = null }
    // Anywhere past the door the Google sign-in is put away again, so nobody is left half signed in.
    BackHandler(enabled = stage != "door" && !busy) { scope.launch { leave() } }

    fun startRegistration() {
        busy = true; error = ""
        scope.launch {
            repo.begin().onSuccess { busy = false; onForm() }.onFailure { busy = false; error = it.message.orEmpty() }
        }
    }

    /** Who is this lead to the firm? New: the form; the rest have words to read first. */
    suspend fun triageLead() {
        repo.triage().onSuccess { t ->
            triage = t; busy = false
            if (t.outcome == "new") startRegistration() else stage = "outcome"
        }.onFailure { busy = false; error = it.message.orEmpty(); if (!storedLead) auth.forget() }
    }

    fun google() {
        error = ""; busy = true
        if (storedLead) { scope.launch { triageLead() }; return }
        scope.launch {
            val serverClientId = runCatching { ApiClient.get(context).googleConfig()["client_id"] as? String }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GOOGLE_WEB_CLIENT_ID
            requestGoogleIdToken(
                context = context, activity = activity, serverClientId = serverClientId,
                onToken = { token ->
                    scope.launch {
                        auth.signInWithGoogle(token)
                            .onSuccess { r ->
                                if (!r.lead) {
                                    // An existing client: if the Counsel workspace can be added to the account, that is where Continue goes.
                                    // The Google client answer carries no `next` (it is on GET lead/counsel and the begin 409): here the list decides.
                                    val next: String? = null
                                    val ws = com.legumlex.client.feature.workspace.WorkspaceRepository(context).state().getOrNull()?.workspaces
                                    addCounsel = com.legumlex.client.feature.workspace.doorClientNext(ws, next) == com.legumlex.client.feature.workspace.DoorNext.AddCounsel
                                    busy = false; stage = "client"
                                } else triageLead()
                            }
                            .onFailure { busy = false; error = it.message.orEmpty() }
                    }
                },
                onError = { busy = false; error = it },
                onCancel = { busy = false },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Room)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp)) {
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegumMark(26.dp, fillColor = Chalk)
                Text("COUNSEL", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.2.sp, color = Gold)
            }
            when (stage) {
                "door" -> Door(still)
                "outcome" -> Outcome(triage, busy, error, onStart = ::startRegistration, onBack = { scope.launch { leave() } })
                "client" -> ClientNote(addCounsel, onContinue = if (addCounsel) onAddCounsel else onSignedInAsClient)
                else -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold, strokeWidth = 2.dp) }
            }
            Spacer(Modifier.height(24.dp))
            if (stage == "door") {
                Spacer(Modifier.height(32.dp))
                error.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFE8735A), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Chalk).clickable(enabled = !busy, onClick = ::google)
                        .semantics { contentDescription = if (storedLead) "Continue" else "Register with Google" },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Room)
                    else if (!storedLead) Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(18.dp))
                    if (!storedLead) Spacer(Modifier.width(10.dp))
                    Text(if (storedLead) "Continue" else "Register with Google", color = Room, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
                if (!storedLead) Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 34.dp), horizontalArrangement = Arrangement.Center) {
                    Text("Already registered? ", color = Faint, fontSize = 13.sp)
                    Text("Sign in", color = Chalk, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSignIn))
                }
                // Debug builds only: walk the wizard with a lead session already stored on the phone, without Google.
                DebugLeadShortcut(enabled = !busy) { busy = true; error = ""; scope.launch { triageLead() } }
            }
        }
    }
}

@Composable
private fun Door(still: Boolean) {
    Column {
        Spacer(Modifier.height(34.dp))
        Text("Your practice,", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 38.sp, color = Chalk)
        Text("in plain sight.", fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 38.sp, color = Chalk)
        Text("A workspace for advocates: your diary, cause lists and eCourts dates.", fontSize = 14.sp, lineHeight = 21.sp, color = Dim, modifier = Modifier.padding(top = 12.dp).width(290.dp))
        Column(Modifier.padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Pop(320, still) { Need("01", "Bar Council certificate or ID card", "Read as it uploads") }
            Pop(440, still) { Need("02", "PAN card", "One account per advocate") }
            Pop(560, still) { Need("03", "Your mobile", "A code to confirm it") }
        }
    }
}

@Composable
private fun Need(n: String, head: String, sub: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Text(n, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Gold, modifier = Modifier.width(20.dp))
        Column {
            Text(head, fontSize = 15.sp, color = Chalk)
            Text(sub, fontSize = 12.sp, color = Faint, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** An item that rises a little as it fades in, after its delay. */
@Composable
private fun Pop(delayMs: Int, still: Boolean, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) { if (!still) { kotlinx.coroutines.delay(delayMs.toLong()); p.animateTo(1f, tween(500, easing = Ease)) } }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 6.dp.toPx() }) { content() }
}

/** The server's answer for someone who cannot start: new is handled before, so these are client, counsel, pending, rejected, staff. */
@Composable
private fun Outcome(t: CounselTriage?, busy: Boolean, error: String, onStart: () -> Unit, onBack: () -> Unit) {
    val o = t?.outcome.orEmpty()
    val label = when (o) {
        "rejected" -> "NEEDS CHANGES"
        "pending" -> "WITH THE FIRM"
        "staff" -> "NOT AVAILABLE HERE"
        "client", "counsel" -> "SIGN IN"
        else -> "REGISTRATION"
    }
    Column(Modifier.padding(top = 44.dp)) {
        Text(label, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.2.sp, color = Gold)
        Text(t?.message.orEmpty(), fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 31.sp, color = Chalk, modifier = Modifier.padding(top = 12.dp))
        if (o == "rejected" && !t?.note.isNullOrBlank()) {
            Text("The firm checked your registration and asks you to correct it:", fontSize = 13.sp, color = Dim, modifier = Modifier.padding(top = 18.dp))
            Text(t?.note.orEmpty(), fontSize = 15.sp, lineHeight = 22.sp, color = Chalk, modifier = Modifier.padding(top = 6.dp))
        }
        error.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFE8735A), fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) }
        Spacer(Modifier.height(30.dp))
        if (o == "rejected") DoorButton(if (busy) "Opening…" else "Fix And Resubmit", !busy, primary = true, onClick = onStart)
        DoorButton(if (o == "rejected") "Back" else "Back To Sign In", !busy, primary = o != "rejected", onClick = onBack)
    }
}

/** An existing client signed in with Google: no second registration; the way to add Counsel is in Profile. */
@Composable
private fun ClientNote(addCounsel: Boolean, onContinue: () -> Unit) {
    Column(Modifier.padding(top = 44.dp)) {
        Text("SIGNED IN", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.2.sp, color = Gold)
        Text("You already have an account here.", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 31.sp, color = Chalk, modifier = Modifier.padding(top = 12.dp))
        Text(if (addCounsel) "You are signed in with it. Continue to add the Counsel workspace to this account: your certificate, your details, and you are done." else "You are signed in with it. To add a Counsel workspace to this account, open Profile and choose Add Counsel Workspace.", fontSize = 14.sp, lineHeight = 21.sp, color = Dim, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(30.dp))
        DoorButton("Continue", true, primary = true, onClick = onContinue)
    }
}

@Composable
private fun DoorButton(text: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).height(52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (primary) Chalk else Color(0xFF2A2927)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) Room else Chalk, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}
