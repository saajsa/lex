package com.legumlex.client.feature.home

import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.TertiaryText
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderSubtle
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.StatusPending
import com.legumlex.client.ui.theme.StatusOngoing
import com.legumlex.client.ui.theme.StatusSettled
import com.legumlex.client.ui.theme.Lex
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.EventNote
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.R
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.theme.StatusPalette
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.Inter
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
// Blocks, descending obligation: Greeting and workspaces → Action Queue →
// Next Event; the chamber has its own tab. Severity stripes live on the leading edge so the queue
// scans by colour before reading a word.

// ── Entry point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLitigation: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenRegistry: () -> Unit = {},
    onOpenConsultations: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenInvoice: (String) -> Unit = {},
    onOpenContract: (String) -> Unit = {},
    onOpenConsultation: (String) -> Unit = {},
    /** A joinable appointment's call, through the video lobby (by consultation id). */
    onOpenVideoLobby: (String) -> Unit = {},
    onOpenCase: (String) -> Unit = {},
    onOpenNotice: (String) -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    onOpenProject: (String) -> Unit = {},
    onOpenAgreement: (String) -> Unit = {},
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

    // While Home is resumed, the next event is polled (an appointment turning
    // joinable raises the live banner); on coming back to Home, the whole
    // dashboard is read again, so what was done elsewhere drops off the queue.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // A no-op while the first load is still in flight.
                    vm.onResume()
                    vm.startNextEventPolling()
                }
                Lifecycle.Event.ON_PAUSE -> vm.stopNextEventPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopNextEventPolling()
        }
    }

    // A swiped-away item can be put back for a few seconds.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onDismissAction: (ActionItem) -> Unit = { item ->
        vm.dismissAction(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message     = "Removed from your queue.",
                actionLabel = "Undo",
                duration    = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) vm.restoreAction(item)
        }
    }

    Box(Modifier.fillMaxSize()) {
    when (val s = state) {
        is HomeUiState.Loading -> HomeSkeleton()
        is HomeUiState.Error   -> HomeError(message = s.message, denied = s.denied, onRetry = vm::load)
        is HomeUiState.Ready   -> PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = vm::refresh,
            modifier     = Modifier.fillMaxSize(),
        ) {
            HomeContent(
                data                = s.data,
                onOpenLitigation    = onOpenLitigation,
                onOpenProjects      = onOpenProjects,
                onOpenRegistry      = onOpenRegistry,
                onOpenConsultations = onOpenConsultations,
                onOpenCalendar      = onOpenCalendar,
                onOpenInvoice       = onOpenInvoice,
                onOpenContract      = onOpenContract,
                onOpenConsultation  = onOpenConsultation,
                onOpenVideoLobby    = onOpenVideoLobby,
                onOpenCase          = onOpenCase,
                onOpenNotice        = onOpenNotice,
                onOpenTask          = onOpenTask,
                onOpenProject       = onOpenProject,
                onOpenAgreement = onOpenAgreement,
                onAskNew            = onAskNew,
                onWalkInDigitalOffice = onWalkInDigitalOffice,
                onOpenChamberLanding  = onOpenChamberLanding,
                onStartAgreementHealthCheckup = onStartAgreementHealthCheckup,
                onOpenNotifications = onOpenNotifications,
                onStartLegalNotice  = onStartLegalNotice,
                onDismissAction     = onDismissAction,
                waitroomRows        = waitroomRows,
                onTapWaitroomRow    = { openWaitroomRow = it },
            )
        }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
    onOpenLitigation: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenRegistry: () -> Unit,
    onOpenConsultations: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenVideoLobby: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAgreement: (String) -> Unit,
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
                onOpenVideoLobby = onOpenVideoLobby,
                onOpenDigitalOffice = onWalkInDigitalOffice,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            HomeHeader(
                dateLabel        = todayDateLabel(),
                greeting         = "${greetingForNow()}, ${data.firstName.ifBlank { "there" }}",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 0. The workspaces, and the consultations' four figures — the
                // top of Mylex's home.
                WorkspaceEntrances(
                    ws           = data.workspaces,
                    onConsult    = onAskNew,
                    onLitigation = onOpenLitigation,
                    onProjects   = onOpenProjects,
                    onRegistry   = onOpenRegistry,
                )
                ConsultationFigures(data.workspaces, onOpenConsultations)

                // The action queue lives behind the header's queue button now
                // (now in Notifications, under Requires Your Attention).

                // 2. Next event
                NextEventBlock(
                    event = ev,
                    onOpenCalendar = onOpenCalendar,
                    onOpenConsultation = onOpenConsultation,
                    onOpenCase = onOpenCase,
                    onOpenVideoLobby = onOpenVideoLobby,
                )

                // The chamber lives on its own tab now (Digital Office); Home
                // keeps only the live-session banner above, which is urgent.
            }
        }
    }
}

// ── 1. Action queue ─────────────────────────────────────────────────────────

@Composable
internal fun ActionQueueBlock(
    bundle: ActionItemsBundle,
    onOpenInvoice: (String) -> Unit,
    onOpenContract: (String) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAgreement: (String) -> Unit,
    onOpenAhcFunnel: () -> Unit,
    onAskNew: () -> Unit,
    onDismiss: (ActionItem) -> Unit,
    showHeader: Boolean = true,
) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        // Section header — title left, counts pill right
        if (showHeader) Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "Action Queue",
                fontFamily = Inter,
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
                shape  = RoundedCornerShape(12.dp),
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
                                openActionItem(ctx, item, ActionRoutes(onOpenInvoice, onOpenContract, onOpenConsultation, onOpenCase,
                                    onOpenNotice, onOpenTask, onOpenProject, onOpenAgreement, onOpenAhcFunnel))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Where an action-queue item can open. */
internal data class ActionRoutes(
    val invoice: (String) -> Unit,
    val contract: (String) -> Unit,
    val consultation: (String) -> Unit,
    val case: (String) -> Unit,
    val notice: (String) -> Unit,
    val task: (String) -> Unit,
    val project: (String) -> Unit,
    val agreement: (String) -> Unit,
    val ahcFunnel: () -> Unit,
)

/** Opens an action-queue item where it is acted on (Home's block and the queue screen alike). */
internal fun openActionItem(ctx: Context, item: ActionItem, r: ActionRoutes) {
    // Signing a contract and accepting an estimate or a proposal happen on
    // its own web page, as on the portal.
    val web = item.webUrl
    if (web != null && item.deepLink.type in WebOnlyLinks) { openInBrowser(ctx, web); return }
    when (item.kind) {
        ActionKind.AhcDraftInProgress -> { r.ahcFunnel(); return }
        ActionKind.AhcReviewPending,
        ActionKind.AhcReportReady     -> { r.consultation(item.deepLink.id); return }
        else -> Unit
    }
    when (item.deepLink.type) {
        DeepLinkType.Invoice      -> r.invoice(item.deepLink.id)
        DeepLinkType.Contract     -> r.contract(item.deepLink.id)
        DeepLinkType.Estimate,
        DeepLinkType.Proposal     -> item.webUrl?.let { openInBrowser(ctx, it) }
        DeepLinkType.Consultation -> r.consultation(item.deepLink.id)
        DeepLinkType.Case         -> r.case(item.deepLink.id)
        DeepLinkType.Notice       -> r.notice(item.deepLink.id)
        DeepLinkType.Task         -> r.task(item.deepLink.id)
        DeepLinkType.Project      -> r.project(item.deepLink.id)
        DeepLinkType.AhcFunnel    -> r.ahcFunnel()
        DeepLinkType.Agreement    -> if (item.deepLink.id.isNotBlank()) r.agreement(item.deepLink.id)
        DeepLinkType.Unknown      -> Unit
    }
}

/** Link types whose action is taken on the item's public web page. */
private val WebOnlyLinks = setOf(DeepLinkType.Contract, DeepLinkType.Estimate, DeepLinkType.Proposal)

private fun openInBrowser(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No browser is available to open this page.", Toast.LENGTH_SHORT).show()
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
            fontFamily = Inter,
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
            fontFamily = Inter,
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
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(item.kind), null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                text       = item.title,
                fontFamily = Inter,
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
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter, fontSize = 11.5.sp),
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
        // A critical item (a reply due to a notice, an overdue invoice) stays
        // until it is dealt with: it can't be swiped away.
        if (item.severity == ActionSeverity.Critical) {
            ActionRow(item = item, onClick = onClick)
        } else {
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
            fontFamily    = Inter,
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
                    daysOut == 1 -> "Tomorrow" to palette
                    daysOut <= 7 -> "$daysOut Days" to palette
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
            .clip(RoundedCornerShape(6.dp))
            .background(palette.softBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = label,
            fontFamily = Inter,
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
    ActionKind.EstimateAwaitingDecision,
    ActionKind.ProposalAwaitingDecision                 -> Icons.Outlined.Description
    ActionKind.DocRequestPending                        -> Icons.Outlined.UploadFile
    ActionKind.ConsultationAwaitingReply                -> Icons.Outlined.Forum
    ActionKind.InboundNoticeResponseDue                 -> Icons.Outlined.Gavel
    ActionKind.AppointmentPaymentRequired               -> Icons.Outlined.Payments
    ActionKind.TaskAwaitingFeedback                     -> Icons.Outlined.Task
    ActionKind.ProjectMilestoneApproaching              -> Icons.Outlined.Flag
    ActionKind.AhcDraftInProgress                       -> Icons.Outlined.Description
    ActionKind.AhcReviewPending                         -> Icons.Outlined.HourglassTop
    ActionKind.AhcReportReady                           -> Icons.Outlined.VerifiedUser
    ActionKind.RegisterObligationDue                    -> Icons.Outlined.EventNote
    ActionKind.Unknown                                  -> Icons.Outlined.ChevronRight
}

// Empty queue card. Kept dignified — no confetti, no streak.
@Composable
private fun CaughtUpCard(onAskNew: () -> Unit) {
    Surface(
        color  = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StatusSettled.softBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Task, null, tint = StatusSettled.softText, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(
                text       = "Nothing Requires Your Attention",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 18.sp,
                letterSpacing = (-0.18).sp,
                color      = PrimaryText,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "No invoices, signatures or requests currently require your attention. New items will appear here.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 18.sp),
                color = SecondaryText,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 260.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentInk)
                    .clickable(onClick = onAskNew)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Ask On The Desk",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 11.5.sp,
                    letterSpacing = 0.46.sp,
                    color      = com.legumlex.client.ui.theme.InverseText,
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
    onOpenVideoLobby: (String) -> Unit,
) {
    SectionHeader(
        title = "Next Event",
        actionLabel = "Calendar",
        onAction = onOpenCalendar,
    )
    if (event == null) {
        NextEventEmptyCard()
        return
    }
    val ctx = LocalContext.current
    val isHearing = event is NextEvent.Hearing
    // Text on the strip reads on its soft fill in light and dark alike.
    val stripPalette = if (isHearing) StatusOngoing else StatusSettled

    Surface(
        color  = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column {
            // Top strip — slate for hearing, verdant for appointment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stripPalette.softBg)
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isHearing) Icons.Outlined.AccountBalance else Icons.Outlined.Videocam,
                        null, tint = stripPalette.softText, modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = if (isHearing) "HEARING" else "APPOINTMENT",
                        fontFamily = Inter,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 11.sp,
                        letterSpacing = 1.1.sp,
                        color      = stripPalette.softText,
                    )
                }
                CountdownChip(
                    daysOut = (event.date.toEpochDay() - LocalDate.now().toEpochDay()).toInt(),
                    event   = event,
                    color   = stripPalette.softText,
                )
            }
            // Body
            Column(modifier = Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text       = formatEventDate(event.date),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 21.sp,
                        letterSpacing = (-0.32).sp,
                        color      = PrimaryText,
                    )
                    val time = event.time
                    if (time != null) {
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text       = time.format(DateTimeFormatter.ofPattern("h:mm a")),
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 14.sp,
                            color      = SecondaryText,
                            modifier   = Modifier.padding(bottom = 1.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = "IST",
                            fontFamily = Inter,
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
                    fontFamily = Inter,
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
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter, fontSize = 12.sp),
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
                            label = "View Case",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCase(event.caseId.toString()) },
                        )
                        EventActionButton(
                            label = "Add To Calendar",
                            primary = false,
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = { launchCalendarInsert(ctx, event, withAlarm = false) },
                        )
                    }
                    is NextEvent.Appointment -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        val cid = event.consultationId?.toString()
                        // The call opens through the video lobby, and only once
                        // the server says it can be joined; until then the
                        // appointment's consultation holds its details.
                        if (event.isJoinable && cid != null) {
                            EventActionButton(
                                label = "Join Call",
                                verdant = true,
                                icon = Icons.Outlined.Videocam,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenVideoLobby(cid) },
                            )
                        } else if (cid != null) {
                            EventActionButton(
                                label = "View Details",
                                primary = true,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenConsultation(cid) },
                            )
                        }
                        EventActionButton(
                            label = "Add To Calendar",
                            primary = false,
                            icon = Icons.Outlined.CalendarMonth,
                            modifier = if (cid == null) Modifier.weight(1f) else Modifier,
                            onClick = { launchCalendarInsert(ctx, event, withAlarm = false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(daysOut: Int, event: NextEvent, color: Color) {
    val now = LocalDateTime.now()
    val eventDt = event.date.atTime(event.time ?: LocalTime.of(9, 0))
    val label = when {
        daysOut < 0 -> "Past"
        daysOut == 0 -> {
            val mins = Duration.between(now, eventDt).toMinutes()
            when {
                mins in 1..59 -> "In $mins Min"
                mins <= 0     -> "Now"
                else          -> "Today"
            }
        }
        daysOut == 1 -> "Tomorrow"
        else         -> "In $daysOut Days"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text       = label,
            fontFamily = Inter,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            color      = color,
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
        verdant -> StatusSettled.softBg
        primary -> AccentInk
        else    -> Surface1
    }
    val fg = when {
        verdant -> StatusSettled.softText
        primary -> InverseText
        else    -> PrimaryText
    }
    val borderMod = if (!primary && !verdant) Modifier.border(1.dp, BorderColor, RoundedCornerShape(8.dp)) else Modifier
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
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
            fontFamily = Inter,
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
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                text       = "Nothing Scheduled",
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp,
                letterSpacing = (-0.15).sp,
                color      = PrimaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Your next hearing or appointment appears here once it is listed.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Inter, fontSize = 12.5.sp),
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
            fontFamily = Inter,
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
                    fontFamily = Inter,
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
    onOpenVideoLobby: (String) -> Unit,
    onOpenDigitalOffice: () -> Unit = {},
) {
    val context     = LocalContext.current
    val ev          = data.nextEvent as? NextEvent.Appointment
    val chamberOpen = data.chamber?.isOpen == true

    // The advocate's name only when the open chamber's roster shows who is
    // busy; a closed chamber's roster may be stale. The consultation's tag is
    // a matter label, never a name, so it is not used here.
    val advocate = if (chamberOpen) {
        data.chamber?.advocates?.firstOrNull { it.isBusy }?.name?.removePrefix("Adv. ")?.takeIf { it.isNotBlank() }
    } else null
    val headerName = advocate?.let { "Adv. $it" } ?: "Your advocate"

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

    Column(modifier = Modifier.fillMaxWidth().background(com.legumlex.client.ui.theme.DarkLexColors.background)  /* dark in both themes: its text is white */) {
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
                fontFamily    = Inter,
                fontWeight    = FontWeight.Bold,
                fontSize      = 10.sp,
                letterSpacing = 1.4.sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.width(12.dp))
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text  = headerName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.5.sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text  = " $tailCopy",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Inter, fontSize = 13.5.sp),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable {
                    // The call opens through the video lobby, as from the consultation.
                    val cid = ev?.consultationId
                    when {
                        cid != null && ev?.isJoinable == true -> onOpenVideoLobby(cid.toString())
                        cid != null                  -> onOpenConsultation(cid.toString())
                        else -> {
                            Toast.makeText(context,
                                "Your session is not ready yet. Opening the Digital Office.",
                                Toast.LENGTH_SHORT).show()
                            onOpenDigitalOffice()
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = "Join Now",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily    = Inter,
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 12.sp,
                    letterSpacing = 0.48.sp,
                ),
                color = Color(0xFF18181B),
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color(0xFF18181B), modifier = Modifier.size(14.dp))
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
        // Date and greeting
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ShimmerBlock(width = 150.dp, height = 11.dp)
            Spacer(Modifier.height(7.dp))
            ShimmerBlock(width = 220.dp, height = 24.dp)
        }
        Spacer(Modifier.height(12.dp))
        // Workspace tiles: one in view, the next one's edge showing
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.88f), height = 188.dp, shape = RoundedCornerShape(12.dp))
            ShimmerBlock(modifier = Modifier.weight(1f), height = 188.dp, shape = RoundedCornerShape(12.dp))
        }
        Spacer(Modifier.height(30.dp))
        // Consultation figures, two by two
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBlock(modifier = Modifier.weight(1f), height = 96.dp, shape = RoundedCornerShape(12.dp))
                    ShimmerBlock(modifier = Modifier.weight(1f), height = 96.dp, shape = RoundedCornerShape(12.dp))
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
                    ShimmerBlock(width = 38.dp, height = 38.dp, shape = RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 12.dp)
                        Spacer(Modifier.height(7.dp))
                        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 10.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    ShimmerBlock(width = 46.dp, height = 18.dp, shape = RoundedCornerShape(6.dp))
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
                ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 40.dp, shape = RoundedCornerShape(8.dp))
            }
        }
    }
}

@Composable
private fun SkSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Surface1,
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(12.dp),
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
private fun HomeError(message: String, denied: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LlColor.Paper)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(Surface2).border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, null, tint = SecondaryText, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (denied) "Access Not Yet Enabled" else "Service Unavailable", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = PrimaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Inter), color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        LegumSecondaryButton(text = "Try Again", leadingIcon = Icons.Outlined.Refresh, onClick = onRetry, modifier = Modifier.widthIn(max = 280.dp))
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..23 -> "Good evening"
        else      -> "Hello"
    }
}

private fun todayDateLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH))

// Opens the system "new calendar event" sheet (Google Calendar etc.),
// pre-filled from a NextEvent. Used by the event's "Add To Calendar" (no alarm).
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
