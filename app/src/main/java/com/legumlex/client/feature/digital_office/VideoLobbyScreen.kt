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
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: VideoLobbyViewModel = viewModel(
        key     = "videolobby-$consultationId",
        factory = viewModelFactory { initializer { VideoLobbyViewModel(app, consultationId) } },
    )
    val ui by vm.state.collectAsState()

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
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF241E19),
                        0.52f to LobbyC.Deep,
                        1.0f to Color(0xFF120F0D),
                        radius = 1600f,
                    )
                )
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
                        else -> LobbyBody(ui, frame, vm, camGranted, onToggleCam, onBack)
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
                    AdvName("The window lapsed")
                    advName.let { Spacer(Modifier.height(6.dp)); Desig("$it stepped away") }
                    Spacer(Modifier.height(22.dp))
                    PresenceChip("Joining window closed", Tone.CLARET, live = false)
                    frame.reason?.let {
                        Spacer(Modifier.height(20.dp))
                        Reassure(it)
                    }
                }
                LobbyFrame.Ended -> {
                    HeroIcon(Icons.Outlined.EventAvailable, Tone.SLATE, badge = Icons.Outlined.Check)
                    Spacer(Modifier.height(26.dp))
                    AdvName("Session ended")
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
                        frame is LobbyFrame.InLine -> PresenceChip("With another client right now", Tone.SLATE)
                        frame is LobbyFrame.YourTurn -> PresenceChip("Ready for you now", Tone.VERDANT)
                        frame is LobbyFrame.BeingApproved -> PresenceChip("Reviewing your brief", Tone.SLATE)
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
                        Reassure("$advName is reading your matter before the call. You’ll be brought in automatically once it’s confirmed.")
                    }
                }
            }
        }

        // ── action zone ──
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp)) {
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
                        label = if (ready) "Join Google Meet" else "Preparing your room…",
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
                    LeaveText("Leave the line", busy = ui.actionInFlight) { vm.leaveLine(onBack) }
                }
                frame is LobbyFrame.BeingApproved -> {
                    ReassureStrip()
                    LeaveText("Cancel this session", busy = ui.actionInFlight) { vm.cancel(onBack) }
                }
                frame is LobbyFrame.CouldntConnect -> {
                    SolidButton("Rejoin the line", Icons.Outlined.Replay, busy = ui.actionInFlight) { vm.rejoin() }
                    Spacer(Modifier.height(11.dp))
                    GhostButton("Back to consultation", Icons.AutoMirrored.Outlined.ArrowForward, onClick = onBack)
                }
                frame is LobbyFrame.Ended -> {
                    GhostButton("Back to consultation", Icons.AutoMirrored.Outlined.ArrowForward, onClick = onBack)
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
                .background(Brush.linearGradient(listOf(Color(0xFF2B496E), Color(0xFF1E3A5F), Color(0xFF15293F))))
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
                .background(Brush.linearGradient(listOf(Color(0xFF2A2420), Color(0xFF1F1A16))))
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
            Text("Joining window closes soon", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = LobbyC.Amber)
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
        StripItem(Icons.Outlined.OpenInNew, if (handing) "Handing off to Meet" else "Opens in Google Meet")
        Box(Modifier.padding(horizontal = 18.dp).size(3.dp).clip(CircleShape).background(LobbyC.Line))
        StripItem(Icons.Outlined.Lock, "Private & secure")
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
        Text(if (on) "Camera preview on" else "Preview your camera",
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
    data class CouldntConnect(val reason: String?) : LobbyFrame
    data object Ended : LobbyFrame
}

data class LobbyUi(
    val loading: Boolean = true,
    val session: DoSession? = null,
    val waitroom: DoWaitroomRow? = null,
    val handingOff: Boolean = false,
    val actionInFlight: Boolean = false,
    val camOn: Boolean = false,
)

private fun LobbyUi.frame(): LobbyFrame {
    if (loading && session == null && waitroom == null) return LobbyFrame.Loading
    // Waitroom (if the client is queued for this advocate) takes precedence for
    // the waiting experience.
    waitroom?.let { wr ->
        when (wr.status) {
            DoWaitroomStatus.WAITING  -> return LobbyFrame.InLine(wr.position, wr.etaMinutes)
            DoWaitroomStatus.PROMPTED, DoWaitroomStatus.PROMOTED -> return LobbyFrame.YourTurn(wr.promptSecondsLeft)
            DoWaitroomStatus.EXPIRED  -> return LobbyFrame.CouldntConnect("Your joining window closed before you joined.")
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
        DoSessionStatus.CANCELLED -> LobbyFrame.CouldntConnect(s.failureReason)
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
        startPoll()
    }

    private fun startPoll() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                if (!_state.value.handingOff) refresh(initial = false)
            }
        }
    }

    private fun refresh(initial: Boolean) = viewModelScope.launch {
        if (initial) _state.value = _state.value.copy(loading = true)
        val session = repo.sessionForConsultation(consultationId).getOrNull()
        val advId = session?.advocate?.staffId
        // Match the live waitroom row for this consultation's advocate, if any.
        val waitroom = repo.listWaitroom().getOrNull()
            ?.firstOrNull { advId != null && it.advocateStaffId == advId }
        _state.value = _state.value.copy(loading = false, session = session, waitroom = waitroom)
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
            _state.value = _state.value.copy(actionInFlight = true)
            repo.leaveWaitroom(wid)
            _state.value = _state.value.copy(actionInFlight = false)
            onDone()
        }
    }

    fun cancel(onDone: () -> Unit) {
        val sid = _state.value.session?.id ?: return onDone()
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = true)
            repo.cancelSession(sid)
            _state.value = _state.value.copy(actionInFlight = false)
            onDone()
        }
    }

    fun rejoin() {
        val advId = _state.value.session?.advocate?.staffId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = true)
            repo.joinWaitroom(advId)
            refresh(initial = false)
            _state.value = _state.value.copy(actionInFlight = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel(); pollJob = null
    }
}
