package com.legumlex.client.feature.digital_office

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.legumlex.client.feature.consultations.friendlyMessage

// -----------------------------------------------------------------------------
// Immersive pre-call lobby for a Digital Office video session ("Client Video
// Lobby.html" — Plot Design). A focused full-screen takeover on the dark "ink"
// surface; the app header + footer are hidden (Route.VideoLobby is a focused
// flow). The call itself runs in Google Meet, opened externally — this surface
// only holds the client up to the handoff. No in-call UI lives here.
// -----------------------------------------------------------------------------

private object LobbyC {
    val Deep    = Color(0xFF171411)
    val Raised  = Color(0xFF221D19)
    val Line    = Color(0xFF332B25)
    val Paper   = Color(0xFFF5F1EA)
    val Muted   = Color(0xFFB4A99D)
    val Faint   = Color(0xFF7C7066)
    val Slate   = Color(0xFF34598A)
    val Verdant = Color(0xFF22864A)
    val Amber   = Color(0xFFB45309)
    val Claret  = Color(0xFFBE2E4A)
}

private enum class Tone(val accent: Color) { SLATE(LobbyC.Slate), VERDANT(LobbyC.Verdant), CLARET(LobbyC.Claret) }

@Composable
fun VideoLobbyScreen(
    consultationId: String,
    onBack: () -> Unit,
    /** The consultation this session belongs to; defaults to plain back. */
    onOpenConsultation: (() -> Unit)? = null,
    /** The Digital Office, to send a fresh request after a lapse or decline. */
    onOpenChamber: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: VideoLobbyViewModel = viewModel(
        key     = "videolobby-$consultationId",
        factory = viewModelFactory { initializer { VideoLobbyViewModel(app, consultationId) } },
    )
    val ui by vm.state.collectAsState()

    // Poll only while the lobby is on screen.
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
    val toConsultation = onOpenConsultation ?: onBack

    // Cosmetic camera mirror — local self-view only (the call & real device
    // permissions belong to Google Meet). Gated behind the runtime CAMERA grant.
    var camGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        camGranted = granted
        if (granted) vm.setCam(true)
    }
    val onToggleCam: () -> Unit = {
        when {
            ui.camOn      -> vm.setCam(false)
            camGranted    -> vm.setCam(true)
            else          -> permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handoff beat: once "Opening Google Meet…" shows, wait a moment then launch
    // the external Meet app, and clear the flag so returning shows the lobby.
    LaunchedEffect(ui.handingOff) {
        if (ui.handingOff) {
            val link = ui.session?.meetLink
            delay(900)
            if (!link.isNullOrBlank()) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            }
            vm.clearHandoff()
        }
    }

    Scaffold(
        containerColor = LobbyC.Deep,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(LobbyC.Deep)
                .padding(inner)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                LobbyTopBar(consultationId, enabled = !ui.handingOff, onBack = onBack)

                val frame = ui.frame()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (frame) {
                        LobbyFrame.Loading -> LoadingBody()
                        is LobbyFrame.Failed -> FailedBody(frame.message, onRetry = vm::retry, onBack = onBack)
                        else -> LobbyBody(ui, frame, vm, camGranted, onToggleCam, onBack, toConsultation, onOpenChamber)
                    }
                }
            }
        }
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun LobbyTopBar(consultationId: String, enabled: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(38.dp).clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, null,
                tint = if (enabled) LobbyC.Paper else LobbyC.Faint, modifier = Modifier.size(22.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Videocam, null, tint = LobbyC.Faint, modifier = Modifier.size(13.dp))
            Text("VIDEO SESSION", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.15.em, color = LobbyC.Faint)
            Box(Modifier.size(4.dp).clip(CircleShape).background(LobbyC.Faint))
            Text("#$consultationId", fontFamily = Inter, fontSize = 10.sp, letterSpacing = 0.1.em, color = LobbyC.Faint)
        }
    }
}

// ── Body (state machine) ─────────────────────────────────────────────────────

@Composable
private fun LobbyBody(
    ui: LobbyUi,
    frame: LobbyFrame,
    vm: VideoLobbyViewModel,
    camGranted: Boolean,
    onToggleCam: () -> Unit,
    onBack: () -> Unit,
    onOpenConsultation: () -> Unit,
    onOpenChamber: (() -> Unit)?,
) {
    val adv = ui.session?.advocate
    val advName = adv?.name?.takeIf { it.isNotBlank() }
        ?: ui.waitroom?.advocateName?.takeIf { it.isNotBlank() } ?: "Your advocate"
    val advDesig = adv?.designation?.takeIf { it.isNotBlank() }
        ?: ui.waitroom?.advocateDesignation?.takeIf { it.isNotBlank() }
    val advAvatar = adv?.avatarUrl ?: ui.waitroom?.advocateAvatarUrl

    Column(Modifier.fillMaxSize()) {
        // ── centre ──
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (frame) {
                is LobbyFrame.CouldntConnect -> {
                    HeroIcon(Icons.Outlined.TimerOff, Tone.CLARET, badge = Icons.Outlined.PriorityHigh)
                    Spacer(Modifier.height(26.dp))
                    AdvName(frame.title)
                    Spacer(Modifier.height(6.dp)); Desig(advName)
                    Spacer(Modifier.height(22.dp))
                    PresenceChip(frame.chip, Tone.CLARET, live = false)
                    frame.reason?.let {
                        Spacer(Modifier.height(20.dp))
                        Reassure(it)
                    }
                }
                LobbyFrame.Ended -> {
                    HeroIcon(Icons.Outlined.EventAvailable, Tone.SLATE, badge = Icons.Outlined.Check)
                    Spacer(Modifier.height(26.dp))
                    AdvName("Session Ended")
                    Spacer(Modifier.height(10.dp))
                    Reassure("This video session has ended. Anything else you need is on your consultation.")
                }
                else -> {
                    val tone = when (frame) {
                        is LobbyFrame.YourTurn -> Tone.VERDANT
                        else -> Tone.SLATE
                    }
                    val badge = when (frame) {
                        is LobbyFrame.YourTurn -> if (ui.handingOff) null else Icons.Outlined.Check
                        LobbyFrame.BeingApproved -> Icons.Outlined.Description
                        else -> Icons.Outlined.Schedule
                    }
                    HeroAvatar(advName, advAvatar, tone, badge, pulse = !ui.handingOff)
                    Spacer(Modifier.height(26.dp))
                    AdvName(advName)
                    advDesig?.let { Spacer(Modifier.height(6.dp)); Desig(it) }
                    Spacer(Modifier.height(22.dp))

                    when {
                        ui.handingOff -> PresenceChip("Connecting you to your call", tone, live = false, spinner = true)
                        frame is LobbyFrame.InLine -> PresenceChip("With Another Client Right Now", Tone.SLATE)
                        frame is LobbyFrame.YourTurn -> PresenceChip("Ready For You Now", Tone.VERDANT)
                        frame is LobbyFrame.BeingApproved -> PresenceChip(
                            if (ui.session?.status == DoSessionStatus.APPROVED_AWAITING_PAYMENT) "Accepted · Fee Due" else "Awaiting Approval",
                            Tone.SLATE,
                        )
                        else -> Unit
                    }

                    if (!ui.handingOff && frame is LobbyFrame.InLine) {
                        Spacer(Modifier.height(24.dp))
                        QueuePosition(frame.position)
                        if (frame.position > 0) {
                            Spacer(Modifier.height(16.dp))
                            QueueStepper(frame.position)
                        }
                    }

                    if (!ui.handingOff && frame is LobbyFrame.YourTurn && frame.secondsLeft != null) {
                        Spacer(Modifier.height(22.dp))
                        Countdown(frame.secondsLeft)
                    }

                    if (!ui.handingOff && frame is LobbyFrame.YourTurn && ui.camOn && camGranted) {
                        Spacer(Modifier.height(20.dp))
                        CameraMirror(Modifier.fillMaxWidth().height(186.dp))
                    }

                    if (ui.handingOff) {
                        Spacer(Modifier.height(26.dp))
                        Reassure("You’re leaving Legum Lex. Google Meet will open in a moment and ask for camera & mic access.")
                    } else if (frame is LobbyFrame.BeingApproved) {
                        Spacer(Modifier.height(26.dp))
                        Reassure(
                            if (ui.session?.status == DoSessionStatus.APPROVED_AWAITING_PAYMENT)
                                "$advName has accepted. Pay the fee's invoice on the firm's website; the call opens here once it is paid."
                            else
                                "$advName is reading your matter before accepting the call" +
                                (ui.session?.approvalWindowMinutes?.let { " (they have $it minutes to respond)" } ?: "") +
                                ". This screen updates by itself."
                        )
                    }
                }
            }
        }

        // ── action zone ──
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp)) {
            ui.message?.let {
                Text(it, fontFamily = Inter, fontSize = 12.sp, color = LobbyC.Claret, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            }
            when {
                ui.handingOff -> {
                    JoinButton(label = "Opening Google Meet…", busy = true, enabled = false) {}
                    Spacer(Modifier.height(14.dp)); ReassureStrip(handing = true)
                }
                frame is LobbyFrame.YourTurn -> {
                    val ready = !ui.session?.meetLink.isNullOrBlank()
                    CameraToggle(on = ui.camOn, onClick = onToggleCam)
                    Spacer(Modifier.height(12.dp))
                    JoinButton(
                        label = if (ready) "Join Google Meet" else "Preparing Your Room…",
                        enabled = ready,
                    ) { vm.startHandoff() }
                    if (ui.camOn) {
                        Spacer(Modifier.height(12.dp))
                        Reassure("Meet will ask for camera & mic again — this preview doesn’t record or transmit anything.")
                    } else {
                        Spacer(Modifier.height(14.dp)); ReassureStrip()
                    }
                }
                frame is LobbyFrame.InLine -> {
                    Reassure("Sit tight — we’ll bring you in automatically the moment your advocate is free. No need to refresh.")
                    Spacer(Modifier.height(16.dp)); ReassureStrip()
                    LeaveText("Leave The Line", busy = ui.actionInFlight) { vm.leaveLine(onBack) }
                }
                frame is LobbyFrame.BeingApproved -> {
                    ui.session?.invoicePayUrl?.takeIf { ui.session?.status == DoSessionStatus.APPROVED_AWAITING_PAYMENT }?.let { url ->
                        val ctx = LocalContext.current
                        SolidButton("Open The Invoice", Icons.Outlined.OpenInNew, busy = false) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    ReassureStrip()
                    LeaveText("Cancel This Request", busy = ui.actionInFlight) { vm.cancel(onBack) }
                }
                frame is LobbyFrame.CouldntConnect -> {
                    // A lapsed or declined session is over; a new request is
                    // made from the Digital Office, never by re-queueing here.
                    if (onOpenChamber != null) {
                        SolidButton("Go To The Digital Office", Icons.Outlined.Replay, busy = false, onClick = onOpenChamber)
                        Spacer(Modifier.height(11.dp))
                    }
                    GhostButton("Back To The Consultation", Icons.AutoMirrored.Outlined.ArrowForward, onClick = onOpenConsultation)
                }
                frame is LobbyFrame.Ended -> {
                    GhostButton("Back To The Consultation", Icons.AutoMirrored.Outlined.ArrowForward, onClick = onOpenConsultation)
                }
                else -> Unit
            }
        }
    }
}

// ── Hero (avatar with pulse + badge) ─────────────────────────────────────────

@Composable
private fun HeroAvatar(name: String, avatarUrl: String?, tone: Tone, badge: ImageVector?, pulse: Boolean) {
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (pulse) PulseRings(tone.accent)
        Box(
            Modifier
                .size(118.dp).clip(CircleShape)
                .background(Color(0xFF1E3A5F))
                .border(1.dp, Color(0x14F5F1EA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(name), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, color = Color(0xFFDFE7F2))
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.matchParentSize().clip(CircleShape))
            }
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = (-14).dp)
                    .size(34.dp).clip(CircleShape).background(LobbyC.Raised).border(2.5.dp, LobbyC.Deep, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(badge, null, tint = tone.accent, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun HeroIcon(icon: ImageVector, tone: Tone, badge: ImageVector?) {
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(118.dp).clip(CircleShape)
                .background(Color(0xFF221D19))
                .border(1.dp, LobbyC.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tone.accent, modifier = Modifier.size(46.dp)) }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = (-14).dp)
                    .size(34.dp).clip(CircleShape).background(LobbyC.Raised).border(2.5.dp, LobbyC.Deep, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(badge, null, tint = tone.accent, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun PulseRings(color: Color) {
    PulseRing(color, 0)
    PulseRing(color, 900)
}

@Composable
private fun PulseRing(color: Color, delayMs: Int) {
    val t = rememberInfiniteTransition(label = "halo")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, delayMillis = delayMs), RepeatMode.Restart),
        label = "ring",
    )
    Box(
        Modifier.size(118.dp).scale(1f + 0.42f * p).clip(CircleShape)
            .border(1.5.dp, color.copy(alpha = (0.55f * (1f - p)).coerceIn(0f, 0.55f)), CircleShape),
    )
}

// ── Text bits ────────────────────────────────────────────────────────────────

@Composable private fun AdvName(text: String) =
    Text(text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 27.sp,
        letterSpacing = (-0.02).em, lineHeight = 30.sp, color = LobbyC.Paper, textAlign = TextAlign.Center)

@Composable private fun Desig(text: String) =
    Text(text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp,
        letterSpacing = 0.02.em, color = LobbyC.Muted, textAlign = TextAlign.Center)

@Composable
private fun PresenceChip(text: String, tone: Tone, live: Boolean = true, spinner: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).border(1.dp, LobbyC.Line, CircleShape)
            .background(Color(0x08F5F1EA)).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            spinner -> CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = LobbyC.Paper)
            live -> LiveDot(tone.accent)
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(tone.accent))
        }
        Text(text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = LobbyC.Paper)
    }
}

@Composable
private fun LiveDot(color: Color) {
    val t = rememberInfiniteTransition(label = "live")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "p")
    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(8.dp).scale(0.6f + 0.8f * p).clip(CircleShape).border(1.dp, color.copy(alpha = (0.7f * (1f - p))), CircleShape))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun QueuePosition(position: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            Text("$position", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 60.sp,
                letterSpacing = (-0.03).em, color = LobbyC.Paper)
            Text(ordinal(position), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp,
                color = LobbyC.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        Text("IN LINE", fontFamily = Inter, fontSize = 10.5.sp, letterSpacing = 0.16.em, color = LobbyC.Faint)
    }
}

@Composable
private fun QueueStepper(position: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        val total = (position + 2).coerceAtMost(6)
        for (i in 1..total) {
            val you = i == position
            Box(
                Modifier.size(if (you) 11.dp else 9.dp).clip(CircleShape)
                    .background(if (you) LobbyC.Slate else if (i < position) LobbyC.Faint else LobbyC.Line),
            )
        }
    }
}

@Composable
private fun Countdown(secondsLeft: Int) {
    var left by remember(secondsLeft) { mutableStateOf(secondsLeft) }
    LaunchedEffect(secondsLeft) {
        left = secondsLeft
        while (left > 0) { delay(1000); left -= 1 }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("%d:%02d".format(left / 60, left % 60), fontFamily = Inter, fontWeight = FontWeight.Medium,
            fontSize = 46.sp, letterSpacing = 0.02.em, color = LobbyC.Paper)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.HourglassTop, null, tint = LobbyC.Amber, modifier = Modifier.size(15.dp))
            Text("Joining Window Closes Soon", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = LobbyC.Amber)
        }
    }
}

@Composable
private fun Reassure(text: String) {
    Text(text, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp, color = LobbyC.Muted,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
}

@Composable
private fun ReassureStrip(handing: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        StripItem(Icons.Outlined.OpenInNew, if (handing) "Handing Off To Meet" else "Opens In Google Meet")
        Box(Modifier.padding(horizontal = 18.dp).size(3.dp).clip(CircleShape).background(LobbyC.Line))
        StripItem(Icons.Outlined.Lock, "Private And Secure")
    }
}

@Composable
private fun StripItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, tint = LobbyC.Faint, modifier = Modifier.size(15.dp))
        Text(text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = LobbyC.Faint)
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────

@Composable
private fun JoinButton(label: String, busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (busy || !enabled) LobbyC.Raised else LobbyC.Verdant)
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(vertical = 19.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp, color = LobbyC.Paper)
        else Icon(Icons.Outlined.Videocam, null, tint = if (enabled) Color.White else LobbyC.Muted, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp,
            color = if (busy || !enabled) LobbyC.Muted else Color.White)
    }
}

@Composable
private fun SolidButton(label: String, icon: ImageVector, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LobbyC.Paper)
            .clickable(enabled = !busy, onClick = onClick).padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = LobbyC.Deep)
        else Icon(icon, null, tint = LobbyC.Deep, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LobbyC.Deep)
    }
}

@Composable
private fun GhostButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, LobbyC.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LobbyC.Paper, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LobbyC.Paper)
    }
}

@Composable
private fun LeaveText(label: String, busy: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick).padding(top = 16.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.02.em, color = LobbyC.Faint)
    }
}

// ── Camera mirror (cosmetic self-view) ───────────────────────────────────────

@Composable
private fun CameraToggle(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().wrapContentWidth().clip(CircleShape)
            .border(1.dp, LobbyC.Line, CircleShape).background(Color(0x08F5F1EA))
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (on) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff, null,
            tint = if (on) LobbyC.Verdant else LobbyC.Muted, modifier = Modifier.size(17.dp),
        )
        Text(if (on) "Camera Preview On" else "Preview Your Camera",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LobbyC.Paper)
    }
}

@Composable
private fun CameraMirror(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, LobbyC.Line, RoundedCornerShape(12.dp)).background(LobbyC.Raised)) {
        // SelfView's camera: bound while shown, let go when the toggle turns it off.
        CameraSelf(Modifier.matchParentSize())
        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0xA8120F0D)).padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = LobbyC.Muted, modifier = Modifier.size(13.dp))
            Text("PREVIEW · MIRROR ONLY", fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 0.08.em, color = LobbyC.Muted)
        }
    }
}

// ── Failed to load ───────────────────────────────────────────────────────────

@Composable
private fun FailedBody(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        HeroIcon(Icons.Outlined.CloudOff, Tone.CLARET, badge = null)
        Spacer(Modifier.height(26.dp))
        AdvName("The Session Could Not Be Loaded")
        Spacer(Modifier.height(12.dp))
        Reassure(message)
        Spacer(Modifier.height(26.dp))
        SolidButton("Try Again", Icons.Outlined.Refresh, busy = false, onClick = onRetry)
        Spacer(Modifier.height(11.dp))
        GhostButton("Back", Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack)
    }
}

// ── Loading skeleton ─────────────────────────────────────────────────────────

@Composable
private fun LoadingBody() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Sk(118.dp, 118.dp, CircleShape)
        Spacer(Modifier.height(26.dp)); Sk(168.dp, 21.dp)
        Spacer(Modifier.height(11.dp)); Sk(124.dp, 12.dp)
        Spacer(Modifier.height(26.dp)); Sk(210.dp, 38.dp, RoundedCornerShape(50))
    }
}

@Composable
private fun Sk(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(w, h).clip(shape).background(Color(0xFFF5F1EA).copy(alpha = 0.07f * a)))
}

// ── helpers ──────────────────────────────────────────────────────────────────

private fun initialsOf(name: String): String =
    name.removePrefix("Adv.").trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

// =============================================================================
// State + ViewModel
// =============================================================================

private sealed interface LobbyFrame {
    data object Loading : LobbyFrame
    data class InLine(val position: Int, val etaMinutes: Int) : LobbyFrame
    data class YourTurn(val secondsLeft: Int?) : LobbyFrame
    data object BeingApproved : LobbyFrame
    data class CouldntConnect(val reason: String?, val title: String = "The Session Did Not Go Ahead", val chip: String = "Session Closed") : LobbyFrame
    data object Ended : LobbyFrame
    data class Failed(val message: String) : LobbyFrame
}

data class LobbyUi(
    val loading: Boolean = true,
    val session: DoSession? = null,
    val waitroom: DoWaitroomRow? = null,
    val handingOff: Boolean = false,
    val actionInFlight: Boolean = false,
    val camOn: Boolean = false,
    /** The session could not be loaded at all (shown with Try Again). */
    val error: String? = null,
    /** The outcome of the last action, when it failed. */
    val message: String? = null,
)

private fun LobbyUi.frame(): LobbyFrame {
    if (loading && session == null && waitroom == null) return LobbyFrame.Loading
    if (session == null && waitroom == null && error != null) return LobbyFrame.Failed(error)
    // Waitroom (if the client is queued for this advocate) takes precedence for
    // the waiting experience.
    waitroom?.let { wr ->
        when (wr.status) {
            DoWaitroomStatus.WAITING  -> return LobbyFrame.InLine(wr.position, wr.etaMinutes)
            DoWaitroomStatus.PROMPTED, DoWaitroomStatus.PROMOTED -> return LobbyFrame.YourTurn(wr.promptSecondsLeft)
            DoWaitroomStatus.EXPIRED  -> return LobbyFrame.CouldntConnect(
                "Your turn came and the joining window closed before the request was sent.",
                title = "The Window Lapsed", chip = "Joining Window Closed",
            )
            else -> Unit
        }
    }
    val s = session ?: return LobbyFrame.Loading
    return when (s.status) {
        DoSessionStatus.CONFIRMED, DoSessionStatus.IN_PROGRESS -> LobbyFrame.YourTurn(null)
        DoSessionStatus.AWAITING_APPROVAL,
        DoSessionStatus.AWAITING_INTAKE,
        DoSessionStatus.PENDING_PAYMENT,
        DoSessionStatus.APPROVED_AWAITING_PAYMENT -> LobbyFrame.BeingApproved
        DoSessionStatus.DECLINED_BY_ADVOCATE,
        DoSessionStatus.ADVOCATE_OFFLINE,
        DoSessionStatus.ADVOCATE_BUSY,
        DoSessionStatus.MEET_FAILED,
        DoSessionStatus.PAYMENT_FAILED,
        DoSessionStatus.EXPIRED,
        DoSessionStatus.CANCELLED -> LobbyFrame.CouldntConnect(
            s.failureReason ?: when (s.status) {
                DoSessionStatus.DECLINED_BY_ADVOCATE -> "Your advocate could not take this request."
                DoSessionStatus.EXPIRED -> if (s.approvedAt != null) "The fee was not paid in time, so the request lapsed. Nothing was charged for a session."
                                           else "Your advocate did not respond in time."
                DoSessionStatus.CANCELLED -> "This request was cancelled."
                else -> "The session could not be connected."
            },
            title = when (s.status) {
                DoSessionStatus.DECLINED_BY_ADVOCATE -> "Request Declined"
                DoSessionStatus.EXPIRED -> "Request Lapsed"
                DoSessionStatus.CANCELLED -> "Request Cancelled"
                else -> "The Session Did Not Go Ahead"
            },
        )
        DoSessionStatus.COMPLETED -> LobbyFrame.Ended
        DoSessionStatus.UNKNOWN -> LobbyFrame.BeingApproved
    }
}

class VideoLobbyViewModel(
    app: Application,
    private val consultationId: String,
) : AndroidViewModel(app) {

    private val repo = DoRepository(ApiClient.get(app))
    private val _state = MutableStateFlow(LobbyUi())
    val state: StateFlow<LobbyUi> = _state.asStateFlow()

    private var pollJob: Job? = null
    private val pollIntervalMs = 5_000L

    init {
        refresh(initial = true)
    }

    fun retry() {
        _state.value = _state.value.copy(error = null)
        refresh(initial = true)
        startPoll()
    }

    fun onScreenStarted() = startPoll()
    fun onScreenStopped() { pollJob?.cancel(); pollJob = null }

    /** Nothing changes after these: stop polling. */
    private fun LobbyUi.isTerminal(): Boolean = when (frame()) {
        is LobbyFrame.Ended, is LobbyFrame.CouldntConnect -> true
        else -> false
    }

    private fun startPoll() {
        if (pollJob?.isActive == true || _state.value.isTerminal()) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                if (!_state.value.handingOff) refresh(initial = false).join()
                if (_state.value.isTerminal()) break
            }
        }
    }

    private fun refresh(initial: Boolean) = viewModelScope.launch {
        if (initial) _state.value = _state.value.copy(loading = true)
        val result = repo.sessionForConsultation(consultationId)
        val session = result.getOrNull() ?: _state.value.session
        val advId = session?.advocate?.staffId
        // Match the live waitroom row for this consultation's advocate, if any.
        val waitroom = repo.listWaitroom().getOrNull()
            ?.firstOrNull { advId != null && it.advocateStaffId == advId }
        _state.value = _state.value.copy(
            loading = false, session = session, waitroom = waitroom,
            error = result.exceptionOrNull()?.friendlyMessage()
                ?: if (session == null && waitroom == null) "No video session was found for this consultation." else null,
        )
    }

    fun startHandoff() {
        if (_state.value.session?.meetLink.isNullOrBlank()) return
        _state.value = _state.value.copy(handingOff = true)
    }

    fun clearHandoff() { _state.value = _state.value.copy(handingOff = false) }

    fun setCam(on: Boolean) { _state.value = _state.value.copy(camOn = on) }

    fun leaveLine(onDone: () -> Unit) {
        val wid = _state.value.waitroom?.waitroomId ?: return onDone()
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = true, message = null)
            repo.leaveWaitroom(wid)
                .onSuccess { _state.value = _state.value.copy(actionInFlight = false); onDone() }
                .onFailure { e -> _state.value = _state.value.copy(actionInFlight = false, message = e.friendlyMessage("The queue could not be left. Please try again.")) }
        }
    }

    fun cancel(onDone: () -> Unit) {
        val sid = _state.value.session?.id ?: return onDone()
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = true, message = null)
            repo.cancelSession(sid)
                .onSuccess { _state.value = _state.value.copy(actionInFlight = false); onDone() }
                .onFailure { e -> _state.value = _state.value.copy(actionInFlight = false, message = e.friendlyMessage("The request could not be cancelled. Please try again.")) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel(); pollJob = null
    }
}
