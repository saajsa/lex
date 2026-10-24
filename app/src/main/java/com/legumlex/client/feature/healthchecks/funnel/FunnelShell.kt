package com.legumlex.client.feature.healthchecks.funnel

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.components.WizardFrame

/** The funnel's steps as the wizard names them (AhcStep order; short, so six fit). */
private val FunnelSteps = listOf("Upload", "Reading", "Confirm", "Pay", "Review", "Result")

/**
 * The Agreement Check funnel in the app's wizard (owner, 2026-10-24): the
 * steps named across the top, the step's body, and Back / Next.
 *
 * The step is the server's (current_step), so a step cannot be undone here:
 * close, Back and the system back all ask Save And Exit ([onSaveAndExit]).
 * Next is the step's own work ([onNext], labelled [nextLabel]); a step with
 * nothing to press, or its own buttons, hides the pair ([showActions]).
 */
@Composable
fun FunnelScreen(
    step: AhcStep,
    onSaveAndExit: () -> Unit,
    ready: Boolean = false,
    saving: Boolean = false,
    nextLabel: String = "Next",
    savingLabel: String = "Saving…",
    onNext: () -> Unit = {},
    showActions: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    WizardFrame(
        title = "Agreement Check",
        steps = FunnelSteps,
        step = step.ordinal1based - 1,
        ready = ready,
        saving = saving,
        saveLabel = nextLabel,
        savingLabel = savingLabel,
        nextLabel = nextLabel,
        error = null,
        onClose = onSaveAndExit,
        onStep = { onSaveAndExit() },
        onSave = onNext,
        onNext = onNext,
        showActions = showActions,
        body = content,
    )
}
