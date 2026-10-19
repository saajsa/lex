package com.legumlex.client.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import android.net.Uri
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.legumlex.client.feature.digital_office.DoWaitroomBanner
import com.legumlex.client.feature.digital_office.DoWaitroomRow
import com.legumlex.client.feature.digital_office.DoWaitroomSheet
import com.legumlex.client.feature.digital_office.sharedDoWaitroomViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.R
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.StatusPalette
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora
import kotlin.math.PI
import kotlin.math.sin
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

// Implementation of: Claude Design "Client Home" (Lexcore aesthetic).
// Blocks, descending obligation: Greeting → Action queue → Next event →
// Balance → Chamber. Severity stripes live on the leading edge so the queue
// scans by colour before reading a word.

// Lexcore (warm-paper) design language — the home screen now speaks the same
// claret / Source Serif 4 / IBM Plex / warm-paper system as the AHC screens.
// These same-named aliases repoint every existing call site at the Lexcore
// tokens without rewiring the layout. Font split follows the design: SERIF for
// headings, SANS for UI labels/buttons/chips, MONO for numerics/eyebrows.
private val PrimaryText   = LlColor.Ink        // --ink
private val SecondaryText = LlColor.InkMuted   // --ink-m
private val TertiaryText  = LlColor.InkFaint   // --ink-f
private val BorderColor   = LlColor.Border     // --b
private val BorderSubtle  = LlColor.Paper2     // --p2 (hairline inside cards)
private val Surface1      = LlColor.Surface    // card white on paper
private val Surface2      = LlColor.Paper1     // --p1 inset fill
private val AccentInk     = LlColor.Ink        // ink button fill
private val StatusDanger  = StatusPalette(LlColor.Claret,     LlColor.ClaretSoft,  LlColor.ClaretMid)
private val StatusPending = StatusPalette(LlColor.AmberMid,   LlColor.AmberSoft,   LlColor.Amber)
private val StatusOngoing = StatusPalette(LlColor.SlateMid,   LlColor.SlateSoft,   LlColor.SlateMid)
private val StatusSettled = StatusPalette(LlColor.VerdantMid, LlColor.VerdantSoft, LlColor.Verdant)

// Solid surfaces that carry meaning (event top-strips, chamber portrait, CTAs).
private val SlateSolid   = LlColor.Slate       // "hearing" strip + portrait plinth
private val VerdantSolid = LlColor.Verdant     // "appointment" strip + free-now
private val VerdantSoft   = LlColor.VerdantSoft
private val Claret        = LlColor.Claret     // chamber hero primary CTA
private val ClaretDeep    = Color(0xFF5E1124)  // pressed-state for Claret
private val InkMuted      = LlColor.InkMuted   // chamber hero body

// ── Entry point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit = {},
    onOpenInvoice: (String) -> Unit = {},
    onOpenContract: (String) -> Unit = {},
    onOpenConsultation: (String) -> Unit = {},
    onOpenCase: (String) -> Unit = {},
    onOpenNotice: (String) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    onOpenProject: (String) -> Unit = {},
    onAskNew: () -> Unit = {},
    onWalkInDigitalOffice: () -> Unit = {},
    /**
     * Called when the client taps the chamber hero portrait or eyebrow row —
     * opens the Digital Office landing/profile screen for that advocate.
     * advocateStaffId is null if the chamber roster is empty.
     */
    onOpenChamberLanding: (advocateStaffId: Int?) -> Unit = {},
    /**
     * Called when the waitroom sheet's "Pay & start intake" action fires.
     * Routes to DoIntake with the specific advocate id from the prompted row
     * so the existing booking flow can pick up the (already-prompted) client.
     */
    onStartDoSessionWith: (Int) -> Unit = {},
    /**
     * Start (or resume) the Agreement Health Checkup funnel. The AHC is a
     * specialised consultation type — the CTA lives in the greeting icon row
     * next to the calendar; completed AHCs surface in the regular
     * Consultations list as `agreement_review` rows.
     */
    onStartAgreementHealthCheckup: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    /**
     * Start a "Legal Notice" client request. Currently routes to
     * NewConsultation pre-tagged `legal_notice` so the advocate can pick up
     * the request and draft a notice in the cases module. A dedicated
     * lexon-side Notice funnel can replace this destination later.
     */
    onStartLegalNotice: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    // Waitroom (cabin queue) — banner + action sheet. The VM polls every 15s
    // and the home banner sits in the sticky slot above the scrollable area.
    val waitroomVm = sharedDoWaitroomViewModel()
    val waitroomRows by waitroomVm.rows.collectAsState()
    var openWaitroomRow by remember { mutableStateOf<DoWaitroomRow?>(null) }
    val waitroomEvent by waitroomVm.event.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(waitroomEvent) {
        waitroomEvent?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            waitroomVm.clearEvent()
        }
    }
    LaunchedEffect(Unit) {
        waitroomVm.bookDirectly.collect { advocateId ->
            openWaitroomRow = null
            onStartDoSessionWith(advocateId)
        }
    }

    // Live chamber polling — the "Open/Closed" badge and online roster
    // reflect real-time presence (advocates signing in, going busy, leaving)
    // every 30s while the home screen is in composition. Auto-cancels on
    // dispose, so backgrounding or navigating away stops the network.
    androidx.compose.runtime.DisposableEffect(vm) {
        vm.startChamberPolling()
        onDispose { vm.stopChamberPolling() }
    }

    when (val s = state) {
        is HomeUiState.Loading -> HomeSkeleton()
        is HomeUiState.Error   -> HomeError(message = s.message, onRetry = vm::load)
        is HomeUiState.Ready   -> PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = vm::refresh,
            modifier     = Modifier.fillMaxSize(),
        ) {
            HomeContent(
                data                = s.data,
                onOpenCalendar      = onOpenCalendar,
                onOpenInvoice       = onOpenInvoice,
                onOpenContract      = onOpenContract,
                onOpenConsultation  = onOpenConsultation,
                onOpenCase          = onOpenCase,
                onOpenNotice        = onOpenNotice,
                onOpenTask          = onOpenTask,
                onOpenProject       = onOpenProject,
                onAskNew            = onAskNew,
                onWalkInDigitalOffice = onWalkInDigitalOffice,
                onOpenChamberLanding  = onOpenChamberLanding,
                onStartAgreementHealthCheckup = onStartAgreementHealthCheckup,
                onOpenNotifications = onOpenNotifications,
                onStartLegalNotice  = onStartLegalNotice,
                onDismissAction     = vm::dismissAction,
                waitroomRows        = waitroomRows,
                onTapWaitroomRow    = { openWaitroomRow = it },
            )
        }
    }

    openWaitroomRow?.let { row ->
        DoWaitroomSheet(
            row = row,
            onStartSession = { advId ->
                openWaitroomRow = null
                onStartDoSessionWith(advId)
            },
            onLeave = { wid ->
                waitroomVm.leave(wid)
                openWaitroomRow = null
            },
            onDismiss = { openWaitroomRow = null },
        )
    }
}

// ── Ready state ─────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    data: HomeData,
    onOpenCalendar: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onAskNew: () -> Unit,
    onWalkInDigitalOffice: () -> Unit,
    onOpenChamberLanding: (Int?) -> Unit,
    onStartAgreementHealthCheckup: () -> Unit,
    onOpenNotifications: () -> Unit,
    onStartLegalNotice: () -> Unit,
    onDismissAction: (ActionItem) -> Unit,
    waitroomRows: List<DoWaitroomRow>,
    onTapWaitroomRow: (DoWaitroomRow) -> Unit,
) {
    val ev = data.nextEvent
    // The live banner is for *this client's* live session — drive it ONLY
    // from the next-event appointment row (`isLive`/`isJoinable` are
    // server-stamped per-client). Chamber-busy state means "some advocate
    // is with someone" and only informs queue ETAs on the Chamber block —
    // it must NOT trigger the banner, or we end up showing "Adv X joined
    // chamber" for a session the client isn't in (and a stale flag with
    // the Chamber closed contradicts itself on the same screen).
    val isLive = ev is NextEvent.Appointment && (ev.isLive || ev.isJoinable)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LlColor.Paper),
    ) {
        DoWaitroomBanner(rows = waitroomRows, onTap = onTapWaitroomRow)

        if (isLive) {
            LiveSessionBanner(
                data = data,
                onOpenConsultation = onOpenConsultation,
                onOpenDigitalOffice = onWalkInDigitalOffice,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            GreetingRow(
                firstName       = data.firstName,
                onCalendarClick = onOpenCalendar,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 0. Service highlights — entry to the two core funnels.
                // Ongoing instances of either funnel still surface in the
                // Action Queue below as tracked items.
                ServiceHighlightsBlock(
                    onStartAgreementCheck = onStartAgreementHealthCheckup,
                    onStartLegalNotice    = onStartLegalNotice,
                )

                // 1. Action queue
                ActionQueueBlock(
                    bundle             = data.actionItems,
                    onOpenInvoice      = onOpenInvoice,
                    onOpenContract     = onOpenContract,
                    onOpenConsultation = onOpenConsultation,
                    onOpenCase         = onOpenCase,
                    onOpenNotice       = onOpenNotice,
                    onOpenTask         = onOpenTask,
                    onOpenProject      = onOpenProject,
                    onOpenAhcFunnel    = onStartAgreementHealthCheckup,
                    onAskNew           = onAskNew,
                    onDismiss          = onDismissAction,
                )

                // 2. Next event
                NextEventBlock(
                    event = ev,
                    onOpenCalendar = onOpenCalendar,
                    onOpenConsultation = onOpenConsultation,
                    onOpenCase = onOpenCase,
                )

                // 3. Chamber
                data.chamber?.let { ch ->
                    if (ch.isOpen) ChamberOpenBlock(ch, onWalkIn = onWalkInDigitalOffice, onOpenLanding = onOpenChamberLanding)
                    else ChamberClosedBlock(ch, onOpenLanding = onOpenChamberLanding)
                }
            }
        }
    }
}

// ── 0. Greeting + icon row ──────────────────────────────────────────────────

@Composable
private fun GreetingRow(
    firstName: String,
    onCalendarClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LlColor.Paper)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = todayDateLabel(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily    = SourceSans,
                    fontWeight    = FontWeight.Medium,
                    fontSize      = 10.5.sp,
                    letterSpacing = 1.5.sp,
                ),
                color = TertiaryText,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text       = "${greetingForNow()}, ${firstName.ifBlank { "there" }}",
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 22.sp,
                letterSpacing = (-0.44).sp,
                lineHeight = 26.sp,
                color      = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconSquare(
            icon        = Icons.Outlined.CalendarMonth,
            contentDesc = "Calendar",
            onClick     = onCalendarClick,
        )
    }
}

// 42×42 rounded square; ink fill for AHC, white-with-border for the rest.
@Composable
private fun IconSquare(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(if (filled) AccentInk else Surface1)
            .then(if (filled) Modifier else Modifier.border(1.dp, BorderColor, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDesc,
            tint = if (filled) Color.White else PrimaryText,
            modifier = Modifier.size(21.dp),
        )
    }
}

// ── 0.5. Service highlights ─────────────────────────────────────────────────

// Two side-by-side CTA cards for the practice's two core funnels. The
// Agreement Check card kicks off the AHC submission funnel; the Legal Notice
// card opens NewConsultation with a `legal_notice` preset so the advocate
// receives it as a notice-drafting request. Each ongoing instance keeps
// showing up in the Action Queue below as a tracked item, so this block is
// strictly an entry point, not a status board.
@Composable
private fun ServiceHighlightsBlock(
    onStartAgreementCheck: () -> Unit,
    onStartLegalNotice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ServiceCard(
            modifier = Modifier.weight(1f),
            kind       = ServiceKind.AGREEMENT,
            iconRes    = R.drawable.ic_contract,
            iconBg     = LlColor.ClaretSoft,
            iconTint   = LlColor.Claret,
            accent     = LlColor.Claret,
            title      = "Agreement Check",
            desc       = "Upload a contract — get an advocate-reviewed risk report.",
            ctaLabel   = "Start checkup",
            onClick    = onStartAgreementCheck,
        )
        ServiceCard(
            modifier = Modifier.weight(1f),
            kind       = ServiceKind.NOTICE,
            iconRes    = R.drawable.ic_outgoing_mail,
            iconBg     = LlColor.SlateSoft,
            iconTint   = LlColor.SlateMid,
            accent     = LlColor.SlateMid,
            title      = "Legal Notice",
            desc       = "Draft and serve a legal notice, end to end.",
            ctaLabel   = "Send notice",
            onClick    = onStartLegalNotice,
        )
    }
}

private enum class ServiceKind { AGREEMENT, NOTICE }

@Composable
private fun ServiceCard(
    modifier: Modifier = Modifier,
    kind: ServiceKind,
    iconRes: Int,
    iconBg: Color,
    iconTint: Color,
    accent: Color,
    title: String,
    desc: String,
    ctaLabel: String,
    onClick: () -> Unit,
) {
    // One looping clock drives every micro-animation, mirroring the design's
    // CSS keyframes: doc scan (3.6s), paper-plane dispatch (4.2s), icon ping
    // (3.6s), icon bob (3.6s), CTA arrow nudge (1.9s) and CTA sheen (3.8s).
    val tr = rememberInfiniteTransition(label = "svc")
    val scanT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    val flyT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "fly")
    val pingT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "ping")
    val bobT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val nudgeT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "nudge")
    val sheenT by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), label = "sheen")

    // .fx-scan — sweeps down the doc, fading in then out.
    val scanY = when {
        scanT < 0.48f -> scanT / 0.48f * 25f
        scanT < 0.60f -> 25f
        else          -> 25f * (1f - (scanT - 0.60f) / 0.40f)
    }
    val scanA = when {
        scanT < 0.14f -> scanT / 0.14f * 0.95f
        scanT < 0.48f -> 0.95f
        scanT < 0.60f -> (1f - (scanT - 0.48f) / 0.12f) * 0.95f
        else          -> 0f
    }
    // .fx-ping — ring scales up and dissolves.
    val pingScale = if (pingT < 0.55f) 1f + (pingT / 0.55f) * 0.5f else 1.5f
    val pingA = if (pingT < 0.55f) 0.5f * (1f - pingT / 0.55f) else 0f
    // .fx-plane — dispatch arc, fading in then out.
    val flyA = when {
        flyT < 0.14f -> flyT / 0.14f * 0.24f
        flyT < 0.68f -> 0.24f
        else         -> (1f - (flyT - 0.68f) / 0.32f) * 0.24f
    }
    val bobY = (-2f * sin(bobT.toDouble() * PI)).toFloat()          // single dip per loop
    val nudgeX = (3f * sin(nudgeT.toDouble() * PI)).toFloat()
    val sheenFrac = if (sheenT < 0.30f) -0.40f + (sheenT / 0.30f) * 1.70f else 1.30f

    Surface(
        color  = LlColor.Surface,                          // .svc-card · #fff
        border = BorderStroke(1.dp, LlColor.Border),
        shape  = RoundedCornerShape(14.dp),                // clips overflowing FX
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box {
            // ---- animated FX layer (behind content) ----
            Box(Modifier.matchParentSize()) {
                if (kind == ServiceKind.AGREEMENT) {
                    // contract lines + scan beam, top-right
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 17.dp, end = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        DocBar(42.dp, accent); DocBar(30.dp, accent); DocBar(39.dp, accent); DocBar(25.dp, accent)
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 11.dp)
                            .offset(y = scanY.dp)
                            .graphicsLayer { alpha = scanA }
                            .size(width = 48.dp, height = 3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LlColor.ClaretMid),
                    )
                } else {
                    // paper-plane dispatch arc, bottom-left
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = null,
                        tint = LlColor.SlateMid,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 34.dp, start = 2.dp)
                            .offset(x = (flyT * 150f).dp, y = (flyT * -44f).dp)
                            .graphicsLayer { rotationZ = -8f; alpha = flyA }
                            .size(23.dp),
                    )
                }
                // ping ring over where the icon sits (top-left)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 15.dp, start = 15.dp)
                        .graphicsLayer { scaleX = pingScale; scaleY = pingScale; alpha = pingA }
                        .size(38.dp)
                        .border(1.5.dp, accent, RoundedCornerShape(11.dp)),
                )
            }

            // ---- content (above FX) ----
            Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 14.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp).offset(y = bobY.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text       = title,
                    fontFamily = Lora,                  // .svc-title · serif
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.5.sp,
                    letterSpacing = (-0.15).sp,
                    color      = LlColor.Ink,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text  = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 11.5.sp, lineHeight = 16.5.sp),
                    color = LlColor.InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                // .svc-cta — a tinted pill with a sweeping sheen + nudging arrow.
                BoxWithConstraints(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LlColor.Paper1)
                        .border(1.dp, LlColor.Border, RoundedCornerShape(8.dp)),
                ) {
                    val wPx = with(LocalDensity.current) { maxWidth.toPx() }
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = ctaLabel.uppercase(),
                            fontFamily = SourceSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 10.5.sp,
                            letterSpacing = 0.63.sp,
                            color      = accent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            null, tint = accent,
                            modifier = Modifier.size(15.dp).offset(x = nudgeX.dp),
                        )
                    }
                    // .svc-cta .sheen — translucent band sweeping left→right.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { translationX = sheenFrac * wPx }
                            .background(
                                Brush.horizontalGradient(
                                    0.30f to Color.Transparent,
                                    0.50f to Color.White.copy(alpha = 0.55f),
                                    0.70f to Color.Transparent,
                                ),
                            ),
                    )
                }
            }
        }
    }
}

// .fx-doc bar — a faint claret contract line.
@Composable
private fun DocBar(width: androidx.compose.ui.unit.Dp, accent: Color) {
    Box(
        Modifier
            .size(width = width, height = 3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(accent.copy(alpha = 0.15f)),
    )
}

// ── 1. Action queue ─────────────────────────────────────────────────────────

@Composable
private fun ActionQueueBlock(
    bundle: ActionItemsBundle,
    onOpenInvoice: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAhcFunnel: () -> Unit,
    onAskNew: () -> Unit,
    onDismiss: (ActionItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        // Section header — title left, counts pill right
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "Action queue",
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 17.sp,
                letterSpacing = (-0.26).sp,
                color      = PrimaryText,
                modifier   = Modifier.weight(1f),
            )
            if (bundle.total == 0) {
                CountsClear()
            } else {
                CountsPill(bundle)
            }
        }

        if (bundle.total == 0) {
            CaughtUpCard(onAskNew = onAskNew)
        } else {
            Surface(
                color  = Surface1,
                border = BorderStroke(1.dp, BorderColor),
                shape  = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    bundle.items.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                        }
                        SwipeableActionRow(
                            itemKey = item.dismissKey(),
                            item = item,
                            onDismiss = { onDismiss(item) },
                            onClick = {
                                when (item.kind) {
                                    ActionKind.AhcDraftInProgress -> { onOpenAhcFunnel(); return@SwipeableActionRow }
                                    ActionKind.AhcReviewPending,
                                    ActionKind.AhcReportReady     -> {
                                        onOpenConsultation(item.deepLink.id); return@SwipeableActionRow
                                    }
                                    else -> Unit
                                }
                                when (item.deepLink.type) {
                                    DeepLinkType.Invoice      -> onOpenInvoice(item.deepLink.id)
                                    DeepLinkType.Contract     -> onOpenContract(item.deepLink.id)
                                    DeepLinkType.Consultation -> onOpenConsultation(item.deepLink.id)
                                    DeepLinkType.Case         -> onOpenCase(item.deepLink.id)
                                    DeepLinkType.Notice       -> onOpenNotice(item.deepLink.id)
                                    DeepLinkType.Task         -> onOpenTask(item.deepLink.id)
                                    DeepLinkType.Project      -> onOpenProject(item.deepLink.id)
                                    DeepLinkType.AhcFunnel    -> onOpenAhcFunnel()
                                    DeepLinkType.Unknown      -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountsPill(bundle: ActionItemsBundle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (bundle.critical > 0) CountDot(StatusDanger.solid, bundle.critical)
        if (bundle.warning > 0)  CountDot(StatusPending.solid, bundle.warning)
        if (bundle.info > 0)     CountDot(StatusOngoing.solid, bundle.info)
    }
}

@Composable
private fun CountDot(color: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            text       = count.toString(),
            fontFamily = SourceSans,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            color      = SecondaryText,
        )
    }
}

@Composable
private fun CountsClear() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StatusSettled.softBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Task, null, tint = StatusSettled.softText, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text       = "Clear",
            fontFamily = SourceSans,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 10.5.sp,
            color      = StatusSettled.softText,
        )
    }
}

// Single action row — severity stripe on the leading edge + tinted icon +
// title/sub + due chip + chevron. Whole row is the tap target.
@Composable
private fun ActionRow(item: ActionItem, onClick: () -> Unit) {
    val sev = item.severity
    val stripe = when (sev) {
        ActionSeverity.Critical -> StatusDanger.solid
        ActionSeverity.Warning  -> StatusPending.solid
        ActionSeverity.Info     -> StatusOngoing.solid
    }
    val iconBg = when (sev) {
        ActionSeverity.Critical -> StatusDanger.softBg
        ActionSeverity.Warning  -> StatusPending.softBg
        ActionSeverity.Info     -> StatusOngoing.softBg
    }
    val iconTint = when (sev) {
        ActionSeverity.Critical -> StatusDanger.softText
        ActionSeverity.Warning  -> StatusPending.softText
        ActionSeverity.Info     -> StatusOngoing.softText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Surface1)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(stripe),
        )
        Spacer(Modifier.width(11.dp))
        // Tinted icon tile
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(item.kind), null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                text       = item.title,
                fontFamily = SourceSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.5.sp,
                letterSpacing = (-0.07).sp,
                color      = PrimaryText,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 11.5.sp),
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        ActionMetaChip(item)
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Outlined.ChevronRight, null,
            tint = TertiaryText,
            modifier = Modifier.size(18.dp).padding(end = 0.dp),
        )
        Spacer(Modifier.width(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableActionRow(
    itemKey: String,
    item: ActionItem,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    // key() ties the dismiss state to this item's identity so, once the list
    // re-emits without the swiped row, the state resets cleanly for whatever
    // row reuses the slot.
    key(itemKey) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) { onDismiss(); true } else false
            },
            positionalThreshold = { total -> total * 0.45f },
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = { DismissBackground(dismissState.dismissDirection) },
        ) {
            ActionRow(item = item, onClick = onClick)
        }
    }
}

// Claret reveal behind a swiped row — either edge, "Dismiss" with a check.
@Composable
private fun DismissBackground(direction: SwipeToDismissBoxValue) {
    val atStart = direction == SwipeToDismissBoxValue.StartToEnd
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(StatusDanger.softBg)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (atStart) Arrangement.Start else Arrangement.End,
    ) {
        Icon(Icons.Outlined.Check, null, tint = StatusDanger.softText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text          = "Dismiss",
            fontFamily    = SourceSans,
            fontWeight    = FontWeight.Bold,
            fontSize      = 11.sp,
            letterSpacing = 0.5.sp,
            color         = StatusDanger.softText,
        )
    }
}

@Composable
private fun ActionMetaChip(item: ActionItem) {
    val today = LocalDate.now()
    val (label, palette) = when (item.kind) {
        ActionKind.InvoiceOverdue -> "Overdue" to StatusDanger
        ActionKind.AhcReportReady -> "New"     to StatusOngoing
        else -> {
            val due = item.dueAt
            if (due != null) {
                val daysOut = (due.toEpochDay() - today.toEpochDay()).toInt()
                val palette = when (item.severity) {
                    ActionSeverity.Critical -> StatusDanger
                    ActionSeverity.Warning  -> StatusPending
                    ActionSeverity.Info     -> StatusOngoing
                }
                when {
                    daysOut < 0  -> "Overdue" to StatusDanger
                    daysOut == 0 -> "Today" to palette
                    daysOut <= 7 -> "$daysOut days" to palette
                    else         -> due.format(DateTimeFormatter.ofPattern("d MMM")) to palette
                }
            } else {
                null to StatusOngoing
            }
        }
    }
    if (label == null) return

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(palette.softBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = label,
            fontFamily = SourceSans,
            fontWeight = FontWeight.Bold,
            fontSize   = 10.sp,
            letterSpacing = 0.4.sp,
            color      = palette.softText,
        )
    }
}

private fun kindIcon(kind: ActionKind): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    ActionKind.InvoiceUnpaid, ActionKind.InvoiceOverdue -> Icons.AutoMirrored.Outlined.ReceiptLong
    ActionKind.ContractUnsigned                         -> Icons.Outlined.Edit
    ActionKind.DocRequestPending                        -> Icons.Outlined.UploadFile
    ActionKind.ConsultationAwaitingReply                -> Icons.Outlined.Forum
    ActionKind.InboundNoticeResponseDue                 -> Icons.Outlined.Gavel
    ActionKind.AppointmentPaymentRequired               -> Icons.Outlined.Payments
    ActionKind.TaskAwaitingFeedback                     -> Icons.Outlined.Task
    ActionKind.ProjectMilestoneApproaching              -> Icons.Outlined.Flag
    ActionKind.AhcDraftInProgress                       -> Icons.Outlined.Description
    ActionKind.AhcReviewPending                         -> Icons.Outlined.HourglassTop
    ActionKind.AhcReportReady                           -> Icons.Outlined.VerifiedUser
    ActionKind.Unknown                                  -> Icons.Outlined.ChevronRight
}

// Empty queue card. Kept dignified — no confetti, no streak.
@Composable
private fun CaughtUpCard(onAskNew: () -> Unit) {
    Surface(
        color  = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape  = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StatusSettled.softBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Task, null, tint = StatusSettled.softText, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(
                text       = "You're all caught up",
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 18.sp,
                letterSpacing = (-0.18).sp,
                color      = PrimaryText,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "No invoices, signatures or requests need you right now. We'll surface anything new here.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 12.5.sp, lineHeight = 18.sp),
                color = SecondaryText,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 260.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentInk)
                    .clickable(onClick = onAskNew)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Ask your advocate",
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 11.5.sp,
                    letterSpacing = 0.46.sp,
                    color      = Color.White,
                )
            }
        }
    }
}

// ── 2. Next event ───────────────────────────────────────────────────────────

@Composable
private fun NextEventBlock(
    event: NextEvent?,
    onOpenCalendar: () -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenCase: (String) -> Unit,
) {
    SectionHeader(
        title = "Next event",
        actionLabel = "Calendar",
        onAction = onOpenCalendar,
    )
    if (event == null) {
        NextEventEmptyCard()
        return
    }
    val ctx = LocalContext.current
    val isHearing = event is NextEvent.Hearing

    Surface(
        color  = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape  = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            // Top strip — slate for hearing, verdant for appointment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isHearing) SlateSolid else VerdantSolid)
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isHearing) Icons.Outlined.AccountBalance else Icons.Outlined.Videocam,
                        null, tint = Color.White, modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = if (isHearing) "HEARING" else "APPOINTMENT",
                        fontFamily = SourceSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 11.sp,
                        letterSpacing = 1.1.sp,
                        color      = Color.White,
                    )
                }
                CountdownChip(daysOut = (event.date.toEpochDay() - LocalDate.now().toEpochDay()).toInt(), event = event)
            }
            // Body
            Column(modifier = Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text       = formatEventDate(event.date),
                        fontFamily = Lora,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 21.sp,
                        letterSpacing = (-0.32).sp,
                        color      = PrimaryText,
                    )
                    if (event.time != null) {
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text       = event.time!!.format(DateTimeFormatter.ofPattern("h:mm a")),
                            fontFamily = SourceSans,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 14.sp,
                            color      = SecondaryText,
                            modifier   = Modifier.padding(bottom = 1.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = "IST",
                            fontFamily = SourceSans,
                            fontSize   = 10.sp,
                            letterSpacing = 0.8.sp,
                            color      = TertiaryText,
                            modifier   = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text       = eventTitle(event),
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.5.sp,
                    color      = PrimaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (event is NextEvent.Appointment) Icons.Outlined.Videocam else Icons.Outlined.LocationOn,
                        null, tint = TertiaryText, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text  = eventLocation(event),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 12.sp),
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Action buttons
                when (event) {
                    is NextEvent.Hearing -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        EventActionButton(
                            label = "View case",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCase(event.caseId.toString()) },
                        )
                        EventActionButton(
                            label = "Directions",
                            primary = false,
                            icon = Icons.Outlined.Directions,
                            onClick = { launchCalendarInsert(ctx, event, withAlarm = false) },
                        )
                    }
                    is NextEvent.Appointment -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        EventActionButton(
                            label = "Join call",
                            verdant = true,
                            icon = Icons.Outlined.Videocam,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val link = event.meetLink
                                if (!link.isNullOrBlank()) {
                                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                                    catch (_: ActivityNotFoundException) {
                                        Toast.makeText(ctx, "No app can open the Meet link.", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (event.consultationId != null) {
                                    onOpenConsultation(event.consultationId.toString())
                                }
                            },
                        )
                        EventActionButton(
                            label = "Reschedule",
                            primary = false,
                            onClick = {
                                event.consultationId?.let { onOpenConsultation(it.toString()) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(daysOut: Int, event: NextEvent) {
    val now = LocalDateTime.now()
    val eventDt = event.date.atTime(event.time ?: LocalTime.of(9, 0))
    val label = when {
        daysOut < 0 -> "past"
        daysOut == 0 -> {
            val mins = Duration.between(now, eventDt).toMinutes()
            when {
                mins in 1..59 -> "in $mins min"
                mins <= 0     -> "now"
                else          -> "today"
            }
        }
        daysOut == 1 -> "tomorrow"
        else         -> "in $daysOut days"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text       = label,
            fontFamily = SourceSans,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            color      = Color.White,
        )
    }
}

@Composable
private fun EventActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    verdant: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val bg = when {
        verdant -> VerdantSolid
        primary -> AccentInk
        else    -> Surface1
    }
    val fg = if (primary || verdant) Color.White else PrimaryText
    val borderMod = if (!primary && !verdant) Modifier.border(1.dp, BorderColor, RoundedCornerShape(9.dp)) else Modifier
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .then(borderMod)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text       = label.uppercase(),
            fontFamily = SourceSans,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 11.sp,
            letterSpacing = 0.55.sp,
            color      = fg,
        )
    }
}

@Composable
private fun NextEventEmptyCard() {
    Surface(
        color    = Surface2,
        border   = BorderStroke(1.dp, BorderSubtle),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                text       = "No hearings scheduled.",
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp,
                letterSpacing = (-0.15).sp,
                color      = PrimaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Your advocate will set one when needed.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SourceSans, fontSize = 12.5.sp),
                color = SecondaryText,
            )
        }
    }
}

private fun eventTitle(event: NextEvent): String = when (event) {
    is NextEvent.Hearing     -> com.legumlex.client.ui.caseTitlePlain(event.caseTitle)
    is NextEvent.Appointment -> event.serviceName ?: event.consultationTag ?: "Video consultation"
}

private fun eventLocation(event: NextEvent): String = when (event) {
    is NextEvent.Hearing     -> event.courtName ?: "Court · location TBA"
    is NextEvent.Appointment -> "Video call · Digital Chamber"
}

private fun formatEventDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date.toEpochDay() - today.toEpochDay()) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

// Removed: a dedicated home balance/outstanding card ("BalanceBlock"). Pending
// and overdue invoices are already surfaced as Action Queue items, so a separate
// home balance section was redundant. Individual invoices open via the queue.

// ── 4. Chamber (digital office) ─────────────────────────────────────────────
//
// Client-first hero card per the "Client Chamber.html" Lexcore redesign:
// one advocate at a time as a calm welcome, not a list of doors. The card
// surfaces the *primary* advocate (the first row from the chamber roster) —
// for multi-advocate clients a "+N more advocates" link sits below the CTA
// so they can still reach the full list via the dedicated Chamber tab.

@Composable
private fun ChamberOpenBlock(chamber: ChamberState, onWalkIn: () -> Unit, onOpenLanding: (Int?) -> Unit) {
    val hero = chamber.advocates.firstOrNull { !it.isBusy } ?: chamber.advocates.firstOrNull()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ChamberSectionHeading(label = "Talk to your advocate", meta = "DIGITAL OFFICE")
        Spacer(Modifier.height(10.dp))
        ChamberHeroCard(
            onOpenLanding   = { onOpenLanding(hero?.staffId) },
            statusPillText  = "Open now",
            statusPillColor = VerdantSolid,
            statusPillLive  = true,
            advocate        = hero,
            welcome         = "We're here when you need us.",
            availability    = if (hero?.isBusy == true) {
                "${hero.name} is in another consult · ~${hero.etaMinutes.coerceAtLeast(2)} min"
            } else "Available now · talk in ~2 min",
            availColor      = if (hero?.isBusy == true) InkMuted else VerdantSolid,
            reassure        = if (chamber.sessionFee > 0)
                "₹${"%,d".format(chamber.sessionFee.toLong())} for an unhurried ${chamber.sessionCapMinutes} minutes — private and on the record."
            else "Your first consultation is free and completely private. Walk in whenever you're ready — no appointment, no card.",
            primaryLabel    = if (chamber.sessionFee > 0) "Walk in now" else "Walk in now · it's free",
            primaryIsClaret = true,
            primaryLeading  = null,
            primaryTrailing = Icons.AutoMirrored.Outlined.ArrowForward,
            onPrimary       = onWalkIn,
            extraAdvocates  = (chamber.advocates.size - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ChamberClosedBlock(chamber: ChamberState, onOpenLanding: (Int?) -> Unit) {
    val hero = chamber.advocates.firstOrNull()
    val opensHint = chamber.hours.ifBlank { "9:00 AM IST" }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ChamberSectionHeading(label = "Talk to your advocate", meta = "DIGITAL OFFICE")
        Spacer(Modifier.height(10.dp))
        ChamberHeroCard(
            onOpenLanding   = { onOpenLanding(hero?.staffId) },
            statusPillText  = "Closed now",
            statusPillColor = TertiaryText,
            statusPillLive  = false,
            advocate        = hero,
            welcome         = "We're back $opensHint.",
            availability    = "Opens $opensHint",
            availColor      = InkMuted,
            availIcon       = Icons.Outlined.Bedtime,
            reassure        = "Walk-in only — drop by during chamber hours.",
            primaryLabel    = "Notify me when we open",
            primaryIsClaret = false,
            primaryLeading  = Icons.Outlined.NotificationsActive,
            primaryTrailing = null,
            onPrimary       = {},
            extraAdvocates  = 0,
            closed          = true,
        )
    }
}

@Composable
private fun ChamberSectionHeading(label: String, meta: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = label,
            fontFamily = Lora,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 18.sp,
            letterSpacing = (-0.27).sp,
            color      = PrimaryText,
            modifier   = Modifier.weight(1f),
        )
        Text(
            text       = meta,
            fontFamily = SourceSans,
            fontSize   = 10.5.sp,
            letterSpacing = 0.5.sp,
            color      = TertiaryText,
        )
    }
}

@Composable
private fun ChamberHeroCard(
    onOpenLanding: () -> Unit,
    statusPillText: String,
    statusPillColor: Color,
    statusPillLive: Boolean,
    advocate: ChamberAdvocate?,
    welcome: String,
    availability: String,
    availColor: Color,
    availIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    reassure: String,
    primaryLabel: String,
    primaryIsClaret: Boolean,
    primaryLeading: androidx.compose.ui.graphics.vector.ImageVector?,
    primaryTrailing: androidx.compose.ui.graphics.vector.ImageVector?,
    onPrimary: () -> Unit,
    extraAdvocates: Int,
    closed: Boolean = false,
) {
    Surface(
        color    = Surface1,
        border   = BorderStroke(1.dp, BorderColor),
        shape    = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            // Top eyebrow row — "DIGITAL OFFICE" + status pill. Tap opens
            // the advocate's Digital Office landing page.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLanding)
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "DIGITAL OFFICE",
                    fontFamily = SourceSans,
                    fontSize   = 10.sp,
                    letterSpacing = 1.4.sp,
                    color      = TertiaryText,
                    modifier   = Modifier.weight(1f),
                )
                ChamberStatusPill(text = statusPillText, color = statusPillColor, live = statusPillLive)
            }
            Spacer(Modifier.height(12.dp))

            // Hero row — portrait + welcome + name + availability. The
            // portrait itself opens the landing page so clients can read the
            // "what you'll get" promises before deciding to walk in.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clickable(onClick = onOpenLanding)) {
                    ChamberHeroPortrait(advocate = advocate, closed = closed)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = welcome,
                        fontFamily = Lora,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 20.sp,
                        letterSpacing = (-0.4).sp,
                        lineHeight = 23.sp,
                        color      = PrimaryText,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (advocate != null) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = PrimaryText)) {
                                    append("Adv. ${advocate.name}")
                                }
                                append(" · your advocate")
                            },
                            fontFamily = SourceSans,
                            fontSize   = 12.sp,
                            color      = InkMuted,
                            lineHeight = 16.sp,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (availIcon != null) {
                            Icon(availIcon, null, tint = availColor, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(7.dp))
                        } else {
                            ChamberLivePip(color = availColor, animate = !closed && statusPillLive)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            text       = availability,
                            fontFamily = SourceSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 11.sp,
                            color      = availColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text       = reassure,
                fontFamily = SourceSans,
                fontSize   = 12.5.sp,
                lineHeight = 19.sp,
                color      = InkMuted,
                modifier   = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(14.dp))

            // Primary CTA
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (primaryIsClaret) Claret else AccentInk)
                    .clickable(enabled = !closed || !primaryIsClaret, onClick = onPrimary)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (primaryLeading != null) {
                    Icon(primaryLeading, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text       = primaryLabel.uppercase(),
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    letterSpacing = 0.5.sp,
                    color      = Color.White,
                )
                if (primaryTrailing != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(primaryTrailing, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (extraAdvocates > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text       = "+ $extraAdvocates more on your panel — tap Chamber",
                    fontFamily = SourceSans,
                    fontSize   = 11.sp,
                    color      = TertiaryText,
                    modifier   = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChamberHeroPortrait(advocate: ChamberAdvocate?, closed: Boolean) {
    val initials = run {
        if (advocate == null) return@run "?"
        val f = advocate.firstName.firstOrNull()?.toString().orEmpty()
        val l = advocate.lastName.firstOrNull()?.toString().orEmpty()
        (f + l).uppercase().ifBlank { "AD" }
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 106.dp)
            .border(3.dp, Color.White, shape)
            .clip(shape)
            .background(if (closed) Color(0xFF34598A) else SlateSolid),
        contentAlignment = Alignment.Center,
    ) {
        if (!advocate?.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = advocate?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Text(
                text       = initials,
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 30.sp,
                color      = Color.White,
            )
        }
    }
}

@Composable
private fun ChamberStatusPill(text: String, color: Color, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            ChamberLivePip(color = color, animate = true)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TertiaryText),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text       = text.uppercase(),
            fontFamily = SourceSans,
            fontWeight = FontWeight.Bold,
            fontSize   = 10.5.sp,
            letterSpacing = 0.65.sp,
            color      = color,
        )
    }
}

@Composable
private fun ChamberLivePip(color: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "chamber-pip")
    val scale = if (animate) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue  = 2.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "chamber-pip-scale",
        ).value
    } else 1f
    val alpha = if (animate) {
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue  = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "chamber-pip-alpha",
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

// ── Section header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 0.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = title,
            fontFamily = Lora,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 17.sp,
            letterSpacing = (-0.26).sp,
            color      = PrimaryText,
            modifier   = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier.clickable(onClick = onAction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = actionLabel,
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 11.5.sp,
                    letterSpacing = 0.46.sp,
                    color      = SecondaryText,
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = SecondaryText, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ── Live session banner ─────────────────────────────────────────────────────

@Composable
private fun LiveSessionBanner(
    data: HomeData,
    onOpenConsultation: (String) -> Unit,
    onOpenDigitalOffice: () -> Unit = {},
) {
    val context     = LocalContext.current
    val ev          = data.nextEvent as? NextEvent.Appointment
    val chamberOpen = data.chamber?.isOpen == true

    // Advocate name resolution: when the chamber is open, the busy roster
    // entry is a reliable signal — that's the advocate the client is with.
    // When the chamber is CLOSED, the roster may be stale or empty and an
    // `isBusy` flag there could refer to a different session entirely, so
    // we don't trust it — fall back to the consultation tag or a generic.
    val advName = if (chamberOpen) {
        val busy = data.chamber?.advocates?.firstOrNull { it.isBusy }
        busy?.name?.removePrefix("Adv. ")
            ?: ev?.consultationTag?.takeIf { it.isNotBlank() }
            ?: "Your advocate"
    } else {
        ev?.consultationTag?.takeIf { it.isNotBlank() }
            ?: "Your advocate"
    }

    // Copy must NOT say "joined chamber" when the chamber is closed —
    // a pre-scheduled video appointment legitimately runs outside chamber
    // hours, and the closed Chamber block right below the banner would
    // contradict that wording on the same screen.
    val tailCopy = when {
        ev?.isLive == true && chamberOpen      -> "has joined chamber"
        ev?.isLive == true && !chamberOpen     -> "is on the call"
        ev?.isJoinable == true && chamberOpen  -> "is on standby"
        ev?.isJoinable == true && !chamberOpen -> "is ready to start"
        else                                   -> "is ready"
    }

    Column(modifier = Modifier.fillMaxWidth().background(AccentInk)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(StatusSettled.solid.copy(alpha = 0.18f)))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusSettled.solid))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text  = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily    = SourceSans,
                fontWeight    = FontWeight.Bold,
                fontSize      = 10.sp,
                letterSpacing = 1.4.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.width(12.dp))
        Row(modifier = Modifier.weight(1f)) {
            // "Adv. " prefix only when we resolved a real personal name from
            // the roster — the generic fallback "Your advocate" reads better
            // without the honorific.
            val headerName = if (advName == "Your advocate") advName else "Adv. $advName"
            Text(
                text  = headerName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SourceSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.5.sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text  = " $tailCopy",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SourceSans, fontSize = 13.5.sp),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .clickable {
                    val link = ev?.meetLink
                    when {
                        !link.isNullOrBlank() -> {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No app can open the Meet link.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        ev?.consultationId != null -> {
                            onOpenConsultation(ev.consultationId.toString())
                        }
                        else -> {
                            Toast.makeText(context,
                                "Your session isn't ready yet — opening Digital Office.",
                                Toast.LENGTH_SHORT).show()
                            onOpenDigitalOffice()
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = "Join now",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily    = SourceSans,
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 12.sp,
                    letterSpacing = 0.48.sp,
                ),
                color = PrimaryText,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PrimaryText, modifier = Modifier.size(14.dp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

// ── Skeleton / Error ────────────────────────────────────────────────────────

@Composable
private fun HomeSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LlColor.Paper),
    ) {
        // Greeting
        Row(
            modifier = Modifier.fillMaxWidth().background(LlColor.Paper).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBlock(width = 80.dp, height = 11.dp)
                Spacer(Modifier.height(7.dp))
                ShimmerBlock(width = 200.dp, height = 22.dp)
            }
            repeat(3) { i ->
                ShimmerBlock(width = 42.dp, height = 42.dp, shape = RoundedCornerShape(11.dp))
                if (i < 2) Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        // Service highlights — two parallel placeholders
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                Surface(
                    color = Surface1,
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        ShimmerBlock(width = 38.dp, height = 38.dp, shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(12.dp))
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.8f), height = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 10.dp)
                        Spacer(Modifier.height(4.dp))
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 10.dp)
                        Spacer(Modifier.height(14.dp))
                        ShimmerBlock(width = 80.dp, height = 11.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Queue
        SkSectionCard {
            repeat(4) { i ->
                if (i > 0) HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBlock(width = 38.dp, height = 38.dp, shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 12.dp)
                        Spacer(Modifier.height(7.dp))
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 10.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    ShimmerBlock(width = 46.dp, height = 18.dp, shape = RoundedCornerShape(7.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Event
        SkSectionCard {
            Box(modifier = Modifier.fillMaxWidth().height(38.dp).background(BorderSubtle))
            Column(modifier = Modifier.padding(15.dp)) {
                ShimmerBlock(width = 140.dp, height = 20.dp)
                Spacer(Modifier.height(11.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(0.85f), height = 12.dp)
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(0.6f), height = 11.dp)
                Spacer(Modifier.height(14.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 40.dp, shape = RoundedCornerShape(9.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        // Balance
        SkSectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ShimmerBlock(width = 80.dp, height = 10.dp)
                Spacer(Modifier.height(10.dp))
                ShimmerBlock(width = 160.dp, height = 28.dp)
                Spacer(Modifier.height(11.dp))
                ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 1.dp)
                Spacer(Modifier.height(11.dp))
                Row {
                    ShimmerBlock(modifier = Modifier.weight(1f), height = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    ShimmerBlock(width = 70.dp, height = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun SkSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { Column(content = content) }
}

@Composable
private fun ShimmerBlock(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp = 14.dp,
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(6.dp),
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(BorderColor),
    )
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LlColor.Paper)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(Surface2).border(1.dp, BorderColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, null, tint = SecondaryText, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("We can't reach Legum Lex right now", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Lora, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = PrimaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SourceSans), color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        LegumSecondaryButton(text = "Try again", leadingIcon = Icons.Outlined.Refresh, onClick = onRetry, modifier = Modifier.widthIn(max = 280.dp))
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Good evening"
    }
}

private fun todayDateLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM"))

// Opens the system "new calendar event" sheet (Google Calendar etc.),
// pre-filled from a NextEvent. Used by Hearing → "Directions" (no alarm).
private fun launchCalendarInsert(context: Context, event: NextEvent, withAlarm: Boolean) {
    val zone  = ZoneId.systemDefault()
    val start = event.date.atTime(event.time ?: LocalTime.of(9, 0))
    val beginMillis = start.atZone(zone).toInstant().toEpochMilli()
    val endMillis = when (event) {
        is NextEvent.Appointment -> {
            val end = event.endTime?.let { event.date.atTime(it) }
                ?: event.durationMinutes?.let { start.plusMinutes(it.toLong()) }
                ?: start.plusMinutes(45)
            end.atZone(zone).toInstant().toEpochMilli()
        }
        is NextEvent.Hearing -> start.plusHours(1).atZone(zone).toInstant().toEpochMilli()
    }
    val title = when (event) {
        is NextEvent.Hearing     -> "Hearing · ${event.caseTitle}"
        is NextEvent.Appointment -> event.serviceName ?: event.consultationTag ?: "Legal consultation"
    }
    val location = when (event) {
        is NextEvent.Hearing     -> event.courtName ?: "Court"
        is NextEvent.Appointment -> event.meetLink ?: "Google Meet · Digital Chamber"
    }
    val description = when (event) {
        is NextEvent.Hearing     -> event.purpose ?: "Hearing for ${event.caseNumber ?: event.caseTitle}"
        is NextEvent.Appointment -> event.meetLink ?: "Digital Chamber session"
    }
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        if (withAlarm) putExtra(CalendarContract.Events.HAS_ALARM, 1)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No calendar app found on this device.", Toast.LENGTH_SHORT).show()
    }
}
