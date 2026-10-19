package com.legumlex.client.feature.digital_office

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.home.ChamberAdvocate
import com.legumlex.client.feature.home.ChamberClosedBlock
import com.legumlex.client.feature.home.ChamberOpenBlock
import com.legumlex.client.feature.home.ChamberState
import com.legumlex.client.feature.home.parseChamber
import com.legumlex.client.ui.components.KeyValueRow
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* ============================================================================
 *  The Digital Office — its own tab, because it is the firm's own room: an
 *  advocate at their desk, a client who walks in. Ordered as Mylex's office
 *  page decides what a client sees: the door fee while it is outstanding; a
 *  session already in flight, which outranks everything; then the chamber —
 *  open with who is in, or closed with when it opens and what to do instead.
 *  The chamber card keeps the Digital Office's own design.
 * ========================================================================== */

class DigitalOfficeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val _state = MutableStateFlow<ChamberState?>(null)
    val state: StateFlow<ChamberState?> = _state.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var poll: Job? = null

    suspend fun fetch() {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            parseChamber(api.dashboardChamber()["data"] as? Map<String, Any?>)
        }.onSuccess { _state.value = it; _failed.value = false }
         .onFailure { if (_state.value == null) _failed.value = true }
    }

    fun refresh() {
        viewModelScope.launch { _refreshing.value = true; fetch(); _refreshing.value = false }
    }

    // Presence is a three-minute heartbeat; every 30 seconds keeps the roster
    // honest while the tab is on screen, and stops when it is not.
    fun startPolling() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch { while (isActive) { fetch(); delay(30_000) } }
    }

    fun stopPolling() { poll?.cancel(); poll = null }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DigitalOfficeScreen(
    onWalkIn: (advocateStaffId: Int?) -> Unit,
    onOpenLanding: (advocateStaffId: Int?) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onAskAdvocate: () -> Unit,
) {
    val vm: DigitalOfficeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val failed by vm.failed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    DisposableEffect(vm) { vm.startPolling(); onDispose { vm.stopPolling() } }

    // The cabin queue: the same banner and sheet Home shows.
    val waitroomVm = sharedDoWaitroomViewModel()
    val waitroomRows by waitroomVm.rows.collectAsState()
    var openRow by remember { mutableStateOf<DoWaitroomRow?>(null) }
    val event by waitroomVm.event.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(event) { event?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); waitroomVm.clearEvent() } }

    val s = state
    when {
        s == null && failed -> Column(
            Modifier.fillMaxSize().background(Lex.colors.background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexEmpty("The Digital Office Could Not Be Reached", "Please check your connection and try again.")
            TextButton(onClick = vm::refresh) { Text("Try Again", color = Lex.colors.foreground) }
        }
        s == null -> Box(Modifier.fillMaxSize().background(Lex.colors.background), Alignment.Center) {
            CircularProgressIndicator(color = Lex.colors.foreground, strokeWidth = 2.dp)
        }
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Lex.colors.background)) {
                DoWaitroomBanner(rows = waitroomRows, onTap = { openRow = it })
                OfficeBody(s, onWalkIn, onOpenLanding, onOpenConsultation, onAskAdvocate)
            }
        }
    }

    openRow?.let { row ->
        DoWaitroomSheet(
            row = row,
            onStartSession = { id -> openRow = null; onWalkIn(id) },
            onLeave = { wid -> waitroomVm.leave(wid); openRow = null },
            onDismiss = { openRow = null },
        )
    }
}

@Composable
private fun OfficeBody(
    c: ChamberState,
    onWalkIn: (Int?) -> Unit,
    onOpenLanding: (Int?) -> Unit,
    onOpenConsultation: (String) -> Unit,
    onAskAdvocate: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(
            eyebrow = "Digital Office",
            title = c.officeName.ifBlank { "The Chamber" },
            modifier = Modifier.padding(horizontal = 16.dp),
            actions = {
                if (c.isOpen) StatusBadge("${c.onlineCount} At Their Desk", Lex.colors.success)
                else StatusBadge("Closed Now", Lex.colors.neutral)
            },
        )

        val active = c.activeSession
        when {
            // The door fee, and the only thing while it is outstanding.
            !c.hasAccess -> Box(Modifier.padding(horizontal = 16.dp)) {
                LexCard {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = Lex.colors.foreground, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Register For The Digital Office", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The Digital Office is our chamber, open online. When an advocate is at their desk you can walk in, state your business and speak to them over Google Meet. Registering is a one-time charge that opens the door.",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Fee("₹${"%,d".format(c.onboardingFee.toLong())}", "One-Time Registration", Modifier.weight(1f))
                            Fee("₹${"%,d".format(c.clientSessionFee.toLong())}", "Per Consultation", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        LegumPrimaryButton(text = "Register And Enter", onClick = { onWalkIn(null) })
                    }
                }
            }
            // A session already in flight outranks the roster.
            active != null -> Box(Modifier.padding(horizontal = 16.dp)) {
                LexCard {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Videocam, null, tint = Lex.colors.foreground, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("You Have A Session In Progress", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                        Spacer(Modifier.height(6.dp))
                        Text("You have already walked in. Your brief, the advocate's answer and your meeting link are on the session.",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                        Spacer(Modifier.height(14.dp))
                        LegumPrimaryButton(
                            text = "Go To My Session", trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward,
                            onClick = { active.consultationId?.let { onOpenConsultation(it.toString()) } ?: onWalkIn(null) },
                        )
                    }
                }
            }
            else -> {
                // The chamber, in its own design.
                if (c.isOpen) ChamberOpenBlock(c, onWalkIn = { onWalkIn(null) }, onOpenLanding = onOpenLanding, showHeading = false)
                else ChamberClosedBlock(c, onOpenLanding = onOpenLanding, showHeading = false, onAskInstead = onAskAdvocate)

                if (c.isOpen && c.roster.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        LexCard(title = "At Their Desks") {
                            c.roster.forEachIndexed { i, a ->
                                if (i > 0) HorizontalDivider(color = Lex.colors.border)
                                AdvocateRow(a) { onOpenLanding(a.staffId) }
                            }
                        }
                    }
                }
            }
        }

        // The office's particulars, whatever its state.
        Box(Modifier.padding(horizontal = 16.dp)) {
            LexCard(title = "The Office") {
                KeyValueRow("Hours", c.hours.ifBlank { "Not Published" }, trailing = {
                    Icon(Icons.Outlined.Schedule, null, tint = Lex.colors.faint, modifier = Modifier.size(16.dp))
                })
                HorizontalDivider(color = Lex.colors.border)
                KeyValueRow("Chambers", c.location.ifBlank { "Not Published" }, trailing = {
                    Icon(Icons.Outlined.Place, null, tint = Lex.colors.faint, modifier = Modifier.size(16.dp))
                })
                HorizontalDivider(color = Lex.colors.border)
                KeyValueRow("A Session", "Up to ${c.durationMinutes} minutes, on Google Meet")
                HorizontalDivider(color = Lex.colors.border)
                KeyValueRow("Fee", if (c.clientSessionFee > 0) "₹${"%,d".format(c.clientSessionFee.toLong())} per consultation"
                    else "No charge for your account")
            }
        }
        Text(
            "A consultation is charged only when an advocate accepts your request. Nothing is taken while they are still reading it, and nothing is taken if they decline.",
            style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun Fee(amount: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Lex.colors.muted).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(amount, style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
    }
}

/** An advocate at their desk: photo (or initials), name, designation, and whether they are free. */
@Composable
private fun AdvocateRow(a: ChamberAdvocate, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Lex.colors.muted).border(1.dp, Lex.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (a.avatarUrl != null) {
                AsyncImage(model = a.avatarUrl, contentDescription = a.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape))
            } else {
                Text((a.firstName.take(1) + a.lastName.take(1)).uppercase(), style = MaterialTheme.typography.labelLarge,
                    color = Lex.colors.foreground)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
            a.designation?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (a.isBusy) {
            StatusBadge(if (a.queueCount > 0) "${a.queueCount} Waiting" else "In A Consult", Lex.colors.warning)
        } else {
            StatusBadge("Available", Lex.colors.success)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Lex.colors.faint, modifier = Modifier.size(20.dp))
    }
}
