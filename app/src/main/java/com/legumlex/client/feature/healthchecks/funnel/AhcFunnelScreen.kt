package com.legumlex.client.feature.healthchecks.funnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.Surface1
import kotlinx.coroutines.flow.collectLatest

/**
 * Top-level container for the AHC funnel. Hydrates state from the server on
 * entry (handles fresh-start vs resume), dispatches to the matching step
 * screen, owns the Save-and-Exit sheet.
 *
 * - `onResultReady` fires when the server reports current_step == result;
 *   the host (NavGraph) should navigate to the consultation detail.
 * - `onExit` fires on Save & exit, Done — close, and Run in background;
 *   the host pops back to wherever the funnel was launched from.
 */
@Composable
fun AhcFunnelScreen(
    onExit: () -> Unit,
    onResultReady: (consultationId: String?) -> Unit,
) {
    val vm: AhcFunnelViewModel = viewModel()
    val state by vm.state.collectAsState()
    var showSaveExit by rememberSaveable { mutableStateOf(false) }

    // Bridge ViewModel events
    LaunchedEffect(Unit) {
        vm.events.collectLatest { event ->
            when (event) {
                AhcFunnelViewModel.Event.NavigateToResult ->
                    onResultReady(state.session?.consultationId)
                AhcFunnelViewModel.Event.NavigateToHome -> onExit()
                is AhcFunnelViewModel.Event.Toast -> { /* handled by host snackbar if needed */ }
            }
        }
    }

    // Start a session once when the host launched us at the empty state. A
    // failed start shows its error with Try Again; it is not retried forever.
    var autoStarted by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state.loading, state.session) {
        if (!autoStarted && !state.loading && state.session == null && !state.expired) {
            autoStarted = true
            vm.startNew()
        }
    }

    when {
        state.expired -> {
            EdgeExpiredScreen(
                onStartNew = { vm.startNew() },
                onBackToHealthChecks = onExit,
            )
        }
        !state.loading && state.session == null && state.error != null -> {
            com.legumlex.client.ui.components.LexEmpty(
                "The Agreement Check Could Not Start",
                state.error ?: "Please check your connection and try again.",
                modifier = Modifier.fillMaxSize().background(Surface1),
                action = { androidx.compose.material3.TextButton(onClick = { vm.startNew() }) { androidx.compose.material3.Text("Try Again", color = PrimaryText) } },
            )
        }
        state.loading || state.session == null -> {
            Box(modifier = Modifier.fillMaxSize().background(Surface1), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryText, strokeWidth = 2.dp())
            }
        }
        else -> {
            val sess = state.session!!
            val onSaveAndExit: () -> Unit = { showSaveExit = true }
            when (sess.currentStep) {
                AhcStep.UPLOAD -> StepUploadScreen(state, vm, onBack = onExit, onSaveAndExit = onSaveAndExit)
                AhcStep.EXTRACTING -> StepExtractingScreen(
                    state, vm,
                    onBack = onExit,
                    onSaveAndExit = onSaveAndExit,
                    onRunInBackground = onExit,
                )
                AhcStep.CONFIRM -> StepConfirmScreen(state, vm, onBack = onExit, onSaveAndExit = onSaveAndExit)
                AhcStep.PAYMENT -> StepPaymentScreen(state, vm, onBack = onExit, onSaveAndExit = onSaveAndExit)
                // Once the agreement is submitted for advocate review, hand off
                // to the live consultation detail (DetailAHC) with its real-time
                // 30-minute countdown + "What happens next / What you submitted",
                // rather than a separate static "pending review" funnel screen.
                AhcStep.PENDING_REVIEW,
                AhcStep.RESULT -> {
                    LaunchedEffect(sess.consultationId) {
                        onResultReady(sess.consultationId)
                    }
                }
            }
        }
    }

    if (showSaveExit) {
        SaveAndExitSheet(
            onSaveExit = {
                showSaveExit = false
                onExit()
            },
            onContinueEditing = { showSaveExit = false },
        )
    }
}

// Tiny shim so the inline `2.dp()` reads cleanly without importing Dp here.
private fun Int.dp() = androidx.compose.ui.unit.Dp(toFloat())
