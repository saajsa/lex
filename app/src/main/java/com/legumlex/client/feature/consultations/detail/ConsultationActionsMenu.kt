package com.legumlex.client.feature.consultations.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.legumlex.client.feature.consultations.ConsultationDetailUiState
import com.legumlex.client.feature.consultations.ConsultationDetailViewModel
import com.legumlex.client.feature.consultations.ConsultationKind
import com.legumlex.client.feature.consultations.canClose
import com.legumlex.client.feature.consultations.canRescheduleSlot
import com.legumlex.client.feature.consultations.canWithdraw
import com.legumlex.client.feature.consultations.components.CloseConsultationSheet
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.TertiaryText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Kebab menu rendered inside each detail variant's TopAppBar action slot.
 * Surfaces the three client-agency actions the server permits:
 *
 *   • Withdraw request           — only while pending + self-initiated
 *   • Update preferred slot      — only while not yet scheduled
 *   • View Your Agreement        — Agreement Check, once a document is on file
 *
 * The menu hides entries that don't apply to this consultation so the user
 * never sees a disabled-for-no-reason action.
 */
@Composable
fun ConsultationActionsMenu(vm: ConsultationDetailViewModel) {
    val state by vm.state.collectAsState()
    val ready = state as? ConsultationDetailUiState.Ready ?: return
    val c = ready.consultation

    val withdrawable    = c.canWithdraw()
    val rescheduleable  = c.canRescheduleSlot()
    // The client's own agreement (the document the Check read), when one is on file.
    val hasAhc          = c.kind == ConsultationKind.AHC &&
        (ready.ahcData?.agreementSizeBytes != null || ready.attachments.isNotEmpty())
    // Close = terminal cancel via the new lifecycle endpoint. Mutually
    // exclusive with withdraw in the menu — withdraw only shows while the
    // request is still pending+self-initiated; close covers every other
    // non-terminal state. They never appear together.
    val closable        = c.canClose() && !withdrawable

    if (!withdrawable && !rescheduleable && !hasAhc && !closable) return

    var menuOpen          by remember { mutableStateOf(false) }
    var confirmWithdraw   by remember { mutableStateOf(false) }
    var rescheduleOpen    by remember { mutableStateOf(false) }
    var closeOpen         by remember { mutableStateOf(false) }

    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "Actions", tint = TertiaryText)
    }
    DropdownMenu(
        expanded         = menuOpen,
        onDismissRequest = { menuOpen = false },
    ) {
        if (hasAhc) {
            DropdownMenuItem(
                text = { Text("View Your Agreement") },
                leadingIcon = {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = PrimaryText)
                },
                enabled = !ready.ahcDocumentLoading,
                onClick = {
                    menuOpen = false
                    vm.downloadAhcDocument()
                },
            )
        }
        if (rescheduleable) {
            DropdownMenuItem(
                text = { Text("Update Preferred Slot") },
                leadingIcon = {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = PrimaryText)
                },
                onClick = {
                    menuOpen = false
                    rescheduleOpen = true
                },
            )
        }
        if (withdrawable) {
            if (hasAhc || rescheduleable) HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text("Withdraw Request", color = StatusDanger.solid)
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Cancel, null, tint = StatusDanger.solid)
                },
                enabled = !ready.actionInFlight,
                onClick = {
                    menuOpen = false
                    confirmWithdraw = true
                },
            )
        }
        if (closable) {
            if (hasAhc || rescheduleable) HorizontalDivider()
            DropdownMenuItem(
                text        = { Text("Close Consultation") },
                leadingIcon = { Icon(Icons.Outlined.CheckCircle, null, tint = PrimaryText) },
                enabled     = !ready.actionInFlight,
                onClick     = {
                    menuOpen = false
                    // The sheet waits for the linkage (below), so its "linked
                    // consultation still open" block is never missed.
                    vm.loadLinkage()
                    closeOpen = true
                },
            )
        }
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Withdraw This Request?") },
            text = {
                Text(
                    "Your firm won't act on this request and it will disappear " +
                    "from your list. You can submit a new one any time."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWithdraw = false
                        vm.withdraw()
                    },
                ) { Text("Withdraw", color = StatusDanger.solid) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) { Text("Keep") }
            },
        )
    }

    if (rescheduleOpen) {
        ReschedulePreferredSheet(
            initialDate = c.preferredDate,
            initialTime = c.preferredTime,
            onDismiss   = { rescheduleOpen = false },
            onConfirm   = { date, time ->
                rescheduleOpen = false
                vm.updatePreferredSlot(
                    preferredDate = date?.toString(),
                    preferredTime = time?.toString(),
                )
            },
        )
    }

    if (closeOpen && !ready.linkageLoaded) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { androidx.compose.material3.CircularProgressIndicator(color = PrimaryText) }
    }
    if (closeOpen && ready.linkageLoaded) {
        CloseConsultationSheet(
            consultation = c,
            linkage      = ready.linkage,
            submitting   = ready.actionInFlight,
            onConfirm    = { reason ->
                closeOpen = false
                vm.cancel(reason)
            },
            onDismiss    = { closeOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReschedulePreferredSheet(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalTime?) -> Unit,
) {
    // Local picker state — we don't push to the VM until "Save".
    // The Material date picker works in UTC midnights: convert with UTC both
    // ways, or the chosen day shifts by one west of Greenwich.
    val zone = ZoneId.of("UTC")
    val today = LocalDate.now()
    val initEpochMillis = (initialDate?.takeIf { !it.isBefore(today) } ?: today)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initEpochMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            // No past dates: a preferred slot is in the future.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate().isBefore(today)
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    val timePickerState = rememberTimePickerState(
        initialHour   = initialTime?.hour   ?: 10,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour      = false,
    )

    var page by remember { mutableStateOf(0) } // 0 = date, 1 = time

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            if (page == 0) {
                TextButton(onClick = { page = 1 }) { Text("Next") }
            } else {
                TextButton(onClick = {
                    val pickedMillis = datePickerState.selectedDateMillis
                    val pickedDate   = pickedMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                    val pickedTime   = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onConfirm(pickedDate, pickedTime)
                }) { Text("Save") }
            }
        },
        dismissButton = {
            if (page == 1) TextButton(onClick = { page = 0 }) { Text("Back") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = {
            Text(if (page == 0) "Preferred Date" else "Preferred Time",
                fontWeight = FontWeight.SemiBold, color = PrimaryText)
        },
        text = {
            Column {
                if (page == 0) DatePicker(state = datePickerState, showModeToggle = false)
                else TimePicker(state = timePickerState)
            }
        },
    )
}
