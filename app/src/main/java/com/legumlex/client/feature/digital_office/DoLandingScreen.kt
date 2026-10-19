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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.legumlex.client.ui.theme.Lex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.ChamberState
import com.legumlex.client.ui.theme.*
import com.legumlex.client.ahc.theme.Inter

// Lexcore palette tokens that the global theme doesn't carry one-to-one. Kept
// file-private so the rest of the app keeps using the BizLink palette unless
// it opts into Lexcore. Identical hex to the HomeScreen chamber hero tokens
// — when those move into ui/theme/, both files should import from there.
private val LexSlate     = Color(0xFF1E3A5F)
private val LexSlateDeep = Color(0xFF15293F)
private val LexSlateMid  = Color(0xFFCDD8E8)
private val LexSlateFog  = Color(0xFF9FB0C6)
private val LexClaret    = Color(0xFF7D1930)
private val LexClaretSoft: Color @Composable get() = Lex.colors.danger.softBg
private val LexVerdant   = Color(0xFF1A5C3A)
private val LexInkMuted: Color @Composable get() = Lex.colors.mutedForeground
private val LexInkFaint: Color @Composable get() = Lex.colors.faint
private val LexBorder: Color @Composable get() = Lex.colors.border
private val LexPaper: Color @Composable get() = Lex.colors.background
private val LexSurface1: Color @Composable get() = Lex.colors.muted

/**
 * Digital Office landing screen — the calm, client-first advocate page.
 *
 * Implements the "Profile" view from Claude Design "Client Chamber.html":
 *   slate hero (portrait + status) → "what you'll get" → "what happens when
 *   you walk in" → "about your advocate" → bottom-pinned Walk in CTA.
 *
 * Notes on the design vs. our data:
 *  - "Your matter" card (intended to overlap the hero) is deferred — needs a
 *    new home endpoint surfacing the client's active consultation per
 *    advocate. The hero card on Home will gain the same surface in phase
 *    2.5; both should read the same field.
 *  - "About your advocate" omits bio/years/rating because the chamber
 *    payload doesn't carry them today. When ChamberAdvocate grows those
 *    fields, drop them into AboutCard without restructuring.
 *  - Weekly schedule is a single "hours" string — design shows a Mon–Sun
 *    table. Backend doesn't expose per-day open hours yet, so we render
 *    the single window with a "Mon–Sat" qualifier and a "Closed Sun" row
 *    inferred from the same string. Replace when ChamberState gains a
 *    weekly object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoLandingScreen(
    advocateStaffId: Int?,
    onBack: () -> Unit,
    onWalkIn: (advocateStaffId: Int?) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val vm: DoLandingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DoLandingViewModel(app = app, initialAdvocateStaffId = advocateStaffId) }
        },
    )
    val state by vm.state.collectAsState()
    // Shared with the home banner, so a join here shows there at once.
    val waitroomVm = sharedDoWaitroomViewModel()
    LaunchedEffect(waitroomVm) {
        // The cabin turned out to be free: go straight to the request.
        waitroomVm.bookDirectly.collect { staffId -> onWalkIn(staffId) }
    }

    Scaffold(
        containerColor = LexPaper,
        bottomBar = {
            val ready = state as? DoLandingUiState.Ready
            if (ready != null) {
                WalkInActionBar(
                    chamber  = ready.chamber,
                    advocate = ready.advocate,
                    onWalkIn = { onWalkIn(ready.advocate?.staffId) },
                    onJoinQueue = {
                        ready.advocate?.staffId?.let { waitroomVm.join(it) }
                        onBack()
                    },
                    onRegister = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(portalOfficeUrl())))
                        }
                    },
                )
            }
        },
    ) { inner ->
        when (val s = state) {
            DoLandingUiState.Loading       -> LoadingShell(inner)
            is DoLandingUiState.Error      -> ErrorShell(inner, s.message, onRetry = { vm.load() })
            is DoLandingUiState.Ready      -> ReadyContent(
                inner    = inner,
                chamber  = s.chamber,
                advocate = s.advocate,
                onBack   = onBack,
            )
        }
    }
}

// ── States ──────────────────────────────────────────────────────────────────

@Composable
private fun LoadingShell(inner: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(inner),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(color = LexClaret) }
}

@Composable
private fun ErrorShell(inner: PaddingValues, msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = LexInkMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text(msg, color = LexInkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, colors = ButtonDefaults.outlinedButtonColors(contentColor = LexClaret)) {
            Text("Try Again")
        }
    }
}

@Composable
private fun ReadyContent(
    inner: PaddingValues,
    chamber: ChamberState,
    advocate: ChamberAdvocate?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .verticalScroll(rememberScrollState()),
    ) {
        SlateHero(chamber = chamber, advocate = advocate, onBack = onBack)
        BenefitsBlock()
        WalkInStepsBlock()
        AboutAdvocateBlock(advocate = advocate, chamber = chamber)
        WhenYouCanWalkInBlock(chamber = chamber)
        Spacer(Modifier.height(26.dp))
    }
}

// ── Slate hero ──────────────────────────────────────────────────────────────

@Composable
private fun SlateHero(chamber: ChamberState, advocate: ChamberAdvocate?, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LexSlate)
            .padding(bottom = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top bar with back + share
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = LexPaper)
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(2.dp))

            // Round portrait
            HeroPortrait(advocate)

            Spacer(Modifier.height(12.dp))
            Text(
                text       = "YOUR ADVOCATE",
                fontFamily = Inter,
                fontSize   = 10.sp,
                letterSpacing = 1.6.sp,
                color      = LexSlateFog,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text       = advocate?.name ?: "Your Advocate",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 25.sp,
                letterSpacing = (-0.5).sp,
                color      = LexPaper,
                textAlign  = TextAlign.Center,
            )
            advocate?.designation?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(5.dp))
                Text(
                    text       = it,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.5.sp,
                    color      = LexSlateMid,
                    textAlign  = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(13.dp))
            HeroStatus(open = chamber.isOpen, busy = advocate?.isBusy == true, advocate = advocate)
            Spacer(Modifier.height(7.dp))
            Text(
                text       = if (chamber.isOpen) "The advocate reads what you write, then answers your knock"
                             else "Opens ${chamber.hours.ifBlank { "9:00 AM" }}",
                fontFamily = Inter,
                fontSize   = 10.5.sp,
                color      = LexSlateFog,
            )
        }
    }
}

@Composable
private fun HeroPortrait(advocate: ChamberAdvocate?) {
    val initials = run {
        if (advocate == null) return@run "?"
        val f = advocate.firstName.firstOrNull()?.toString().orEmpty()
        val l = advocate.lastName.firstOrNull()?.toString().orEmpty()
        (f + l).uppercase().ifBlank { "AD" }
    }
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .size(120.dp)
            .border(4.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clip(CircleShape)
            .background(LexSlateDeep),
        contentAlignment = Alignment.Center,
    ) {
        if (!advocate?.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = advocate?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text       = initials,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 40.sp,
                color      = LexPaper,
            )
        }
    }
}

@Composable
private fun HeroStatus(open: Boolean, busy: Boolean, advocate: ChamberAdvocate?) {
    val color = when {
        !open    -> LexSlateFog
        busy     -> Color(0xFFE0A012)
        else     -> Color(0xFF4ADE80)
    }
    val label = when {
        !open    -> "Closed Now"
        busy     -> "In Another Consultation · ~${(advocate?.etaMinutes ?: 2).coerceAtLeast(2)} Min"
        else     -> "Open Now"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LivePip(color = color, animate = open && !busy)
        Spacer(Modifier.width(8.dp))
        Text(
            text       = label.uppercase(),
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize   = 11.sp,
            letterSpacing = 0.77.sp,
            color      = Color.White,
        )
    }
}

@Composable
private fun LivePip(color: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "do-landing-pip")
    val scale = if (animate) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue  = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "do-pip-scale",
        ).value
    } else 1f
    val alpha = if (animate) {
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue  = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "do-pip-alpha",
        ).value
    } else 0f
    Box(contentAlignment = Alignment.Center) {
        if (animate) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

// ── Blocks ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text       = text,
        fontFamily = Inter,
        fontSize   = 10.sp,
        letterSpacing = 1.5.sp,
        color      = LexInkFaint,
        modifier   = Modifier.padding(start = 2.dp, bottom = 11.dp),
    )
}

@Composable
private fun BenefitsBlock() {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
        SectionEyebrow("WHAT YOU'LL GET")
        Surface(
            color  = Lex.colors.card,
            border = BorderStroke(1.dp, LexBorder),
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                BenefitRow(
                    icon  = Icons.Outlined.Videocam,
                    title = "A Consultation On Google Meet",
                    sub   = "One-to-one with your advocate, in the Meet app on your phone",
                    first = true,
                )
                BenefitRow(
                    icon  = Icons.Outlined.Schedule,
                    title = "Read Before The Call",
                    sub   = "The advocate reads what you wrote before deciding to take it",
                )
                BenefitRow(
                    icon  = Icons.Outlined.Savings,
                    title = "Nothing Charged Until They Agree",
                    sub   = "The fee is invoiced only once the advocate has agreed, and paid on the firm's website",
                )
                BenefitRow(
                    icon  = Icons.Outlined.Summarize,
                    title = "On Your File",
                    sub   = "The consultation stays in Consultations, with the advocate's replies",
                )
                BenefitRow(
                    icon  = Icons.Outlined.Lock,
                    title = "Private And Confidential",
                    sub   = "Only you and your advocate are in the call",
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, sub: String, first: Boolean = false) {
    if (!first) HorizontalDivider(color = LexSurface1, thickness = 1.dp)
    Row(
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = LexVerdant, modifier = Modifier.size(21.dp).padding(top = 1.dp))
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text       = title,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                color      = PrimaryText,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text       = sub,
                fontFamily = Inter,
                fontSize   = 11.5.sp,
                lineHeight = 16.sp,
                color      = LexInkMuted,
            )
        }
    }
}

@Composable
private fun WalkInStepsBlock() {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 0.dp)) {
        SectionEyebrow("WHAT HAPPENS WHEN YOU WALK IN")
        Surface(
            color  = Lex.colors.card,
            border = BorderStroke(1.dp, LexBorder),
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                // The four steps of the Legum Lex chamber page's How It Works.
                StepRow(n = "1", title = "Say What Has Happened", sub = "In your own words; the advocate reads it first", trailing = Icons.Outlined.EditNote, first = true)
                StepRow(n = "2", title = "The Advocate Agrees",   sub = "They read it before deciding to take the call",   trailing = Icons.Outlined.HourglassTop)
                StepRow(n = "3", title = "Face To Face",          sub = "The consultation opens in Google Meet",           trailing = Icons.Outlined.Videocam)
            }
        }
    }
}

@Composable
private fun StepRow(n: String, title: String, sub: String, trailing: ImageVector, first: Boolean = false) {
    if (!first) HorizontalDivider(color = LexSurface1, thickness = 1.dp)
    Row(
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LexClaretSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = n,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                color      = LexClaret,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                color      = PrimaryText,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text       = sub,
                fontFamily = Inter,
                fontSize   = 11.5.sp,
                lineHeight = 16.sp,
                color      = LexInkMuted,
            )
        }
        Icon(trailing, null, tint = LexInkFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AboutAdvocateBlock(advocate: ChamberAdvocate?, chamber: ChamberState) {
    if (advocate == null) return
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
        SectionEyebrow("ABOUT YOUR ADVOCATE")
        Surface(
            color  = Lex.colors.card,
            border = BorderStroke(1.dp, LexBorder),
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text       = buildAboutLine(advocate, chamber),
                    fontFamily = Inter,
                    fontSize   = 13.sp,
                    lineHeight = 21.sp,
                    color      = LexInkMuted,
                )
                if (!advocate.barCouncilId.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LexSurface1)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    ) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = LexInkMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text       = "Bar Council ID · ${advocate.barCouncilId}",
                            fontFamily = Inter,
                            fontSize   = 10.5.sp,
                            color      = LexInkMuted,
                        )
                    }
                }
            }
        }
    }
}

private fun buildAboutLine(advocate: ChamberAdvocate, chamber: ChamberState): String {
    val parts = mutableListOf<String>()
    advocate.designation?.takeIf { it.isNotBlank() }?.let { parts += it }
    chamber.location.takeIf { it.isNotBlank() }?.let { parts += "Practising in $it" }
    if (parts.isEmpty()) return "${advocate.name} sits in the Digital Office during chamber hours."
    return parts.joinToString(" · ") + "."
}

@Composable
private fun WhenYouCanWalkInBlock(chamber: ChamberState) {
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        SectionEyebrow("WHEN YOU CAN WALK IN")
        Surface(
            color  = Lex.colors.card,
            border = BorderStroke(1.dp, LexBorder),
            shape  = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp)) {
                val h = chamber.sitting
                fun t(m: Int) = java.time.LocalTime.of(m / 60, m % 60).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val days = if (h.days.size > 1) "${names[h.days.first()]} – ${names[h.days.last()]}" else names[h.days.first()]
                val off = (1..7).filter { it !in h.days }.joinToString(", ") { names[it] }
                ScheduleRow(day = days, hours = "${t(h.from)} – ${t(h.to)}", today = chamber.isOpen, last = off.isEmpty())
                if (off.isNotEmpty()) ScheduleRow(day = off, hours = "Closed", closed = true, last = true)
            }
        }
    }
}

@Composable
private fun ScheduleRow(day: String, hours: String, today: Boolean = false, closed: Boolean = false, last: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = day,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.5.sp,
            color      = if (today) LexClaret else PrimaryText,
            modifier   = Modifier.weight(1f),
        )
        Text(
            text       = hours,
            fontFamily = Inter,
            fontSize   = 11.5.sp,
            color      = when {
                today  -> LexClaret
                closed -> LexInkFaint
                else   -> LexInkMuted
            },
        )
    }
    if (!last) HorizontalDivider(color = LexSurface1, thickness = 1.dp)
}

// ── Bottom action bar ───────────────────────────────────────────────────────

@Composable
private fun WalkInActionBar(
    chamber: ChamberState,
    advocate: ChamberAdvocate?,
    onWalkIn: () -> Unit,
    onJoinQueue: () -> Unit,
    onRegister: () -> Unit,
) {
    val open    = chamber.isOpen
    val busy    = advocate?.isBusy == true
    // This client's own fee (client_session_fee), as the server reckons it.
    val fee     = chamber.clientSessionFee
    val hint    = when {
        !chamber.hasAccess -> "Sessions are open to registered clients · register once on the website"
        !open    -> "Walk-in closed — back at ${chamber.hours.ifBlank { "chamber hours" }}"
        busy     -> "${advocate?.name ?: "Your advocate"} is in another consultation · join the queue"
        fee <= 0 -> "Open now"
        else     -> "Open now · ${com.legumlex.client.feature.finance.formatMoney(fee, if (chamber.currency == "INR") "₹" else chamber.currency + " ").removeSuffix(".00")} after the advocate accepts"
    }
    // Closed: no button at all (there is nothing to do until the chamber opens).
    val cta: Pair<String, () -> Unit>? = when {
        !chamber.hasAccess -> "Register On The Website" to onRegister
        !open    -> null
        busy     -> "Join The Queue" to onJoinQueue
        else     -> "Walk In Now" to onWalkIn
    }

    Column(modifier = Modifier.fillMaxWidth().background(LexPaper)) {
        HorizontalDivider(color = LexBorder, thickness = 1.dp)
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = LexInkFaint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text       = hint,
                fontFamily = Inter,
                fontSize   = 11.5.sp,
                color      = LexInkMuted,
                textAlign  = TextAlign.Center,
            )
        }
        if (cta != null) {
        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LexClaret)
                .clickable(onClick = cta.second)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = cta.first,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                letterSpacing = 0.5.sp,
                color      = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        }
        }
    }
}
