package com.legumlex.client.feature.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.navigation.showAppFooter
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.flow.MutableStateFlow

// REQ-055 (owner: "Add a floating button instead."): on a demo sign-in, a small round Feedback button floats bottom-right on every
// signed-in screen, above the tab bar and above a screen's own bottom bar; it opens a sheet with Send Feedback and Feedback On Google Play.

/** The height of the screen's own bottom bar (a reply box, sticky actions), in px, so the button sits above it; 0 when there is none. */
object FeedbackFabInset { val px = MutableStateFlow(0) }

/** On a screen's own bottom bar: tells the floating Feedback button how tall it is, and forgets it when the bar goes. */
fun Modifier.liftsFeedbackFab(): Modifier = composed {
    DisposableEffect(Unit) { onDispose { FeedbackFabInset.px.value = 0 } }
    onSizeChanged { FeedbackFabInset.px.value = it.height }
}

/** Shown on a demo sign-in, on the screens with the tab bar (tabs, lists, details), never while the keyboard is open. Wizards,
 *  full-screen viewers and the desk hide the tab bar, so the button goes with it. */
internal fun feedbackFabShown(demo: Boolean, route: String?, keyboard: Boolean): Boolean = demo && !keyboard && showAppFooter(route)

/** How far above the bottom of the window it sits: the tab bar's room (or the counsel bar's zone), the screen's own bar, and a margin. */
internal fun feedbackFabBottom(barRoom: Dp, ownBar: Dp): Dp = barRoom + ownBar + 12.dp

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedbackFab(route: String?, barRoom: Dp) {
    val note by DemoSession.note.collectAsState()
    val keyboard = WindowInsets.isImeVisible
    val ownPx by FeedbackFabInset.px.collectAsState()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var wizard by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    if (!feedbackFabShown(note != null, route, keyboard) && !sheet && !wizard) return
    val ownBar = with(LocalDensity.current) { ownPx.toDp() }

    if (!sheet && !wizard && feedbackFabShown(note != null, route, keyboard)) Box(Modifier.fillMaxSize()) {
        Surface(
            onClick = { sheet = true }, shape = CircleShape, color = Lex.colors.card, contentColor = Lex.colors.foreground,
            shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Lex.colors.border),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = feedbackFabBottom(barRoom, ownBar))
                .size(48.dp).semantics { contentDescription = "Feedback" },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Feedback, null, modifier = Modifier.size(22.dp)) }
        }
    }

    if (sheet) ModalBottomSheet(
        onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Lex.colors.card,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Feedback", style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            SheetRow(Icons.Outlined.Feedback, "Send Feedback", "Write to us, with a screenshot if you like.", Icons.AutoMirrored.Outlined.KeyboardArrowRight) {
                sheet = false; wizard = true
            }
            HorizontalDivider(color = Lex.colors.border, modifier = Modifier.padding(horizontal = 20.dp))
            SheetRow(Icons.Outlined.RateReview, "Feedback On Google Play", "Opens Google Play. What you write there reaches us privately.", Icons.AutoMirrored.Outlined.OpenInNew) {
                sheet = false
                if (!openPlayListing(context)) android.widget.Toast.makeText(context, PLAY_UNAVAILABLE, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (wizard) FeedbackWizard(onClose = { wizard = false }, onSent = { msg ->
        wizard = false
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    })
}

/** One choice in the sheet: its icon on a round muted badge, the title and a line, the trailing mark. */
@Composable
private fun SheetRow(icon: ImageVector, title: String, line: String, trailing: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Lex.colors.muted), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lex.colors.foreground, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Lex.colors.foreground)
            Text(line, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        }
        Spacer(Modifier.width(4.dp))
        Icon(trailing, null, tint = Lex.colors.mutedForeground, modifier = Modifier.size(20.dp))
    }
}
