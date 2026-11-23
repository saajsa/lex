package com.legumlex.client.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.theme.Lex

// REQ-038 038.1: counsel alerts (the morning digest, a moved date), as pushes and as the Alerts list (GET board/alerts).

data class BoardAlert(val id: Int, val kind: String, val ref: String, val title: String, val body: String, val route: String, val at: String)

internal fun parseAlerts(d: Map<String, Any?>): List<BoardAlert> = d["items"].list().map {
    BoardAlert(it["id"].int(), it["kind"].str(), it["ref"].str(), it["title"].str(), it["body"].str(), it["data"].map()["route"].str().ifBlank { "board" }, it["at"].str())
}

/** Where an alert's route leads in the app: the board's Today, or one case. */
sealed interface AlertTarget {
    data object Today : AlertTarget
    data class Case(val id: Int) : AlertTarget
}

/** "board" is Today; "board/cases/{id}" is that case; anything else is Today (the board is still the right place). */
internal fun alertTarget(route: String?): AlertTarget {
    val id = route?.trim()?.removePrefix("/")?.let { Regex("^board/cases/(\\d+)$").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    return if (id != null && id > 0) AlertTarget.Case(id) else AlertTarget.Today
}

/** The deep link a counsel push opens (matched by the NavGraph's counsel deep links). */
internal fun counselDeepLink(route: String?): String = when (val t = alertTarget(route)) {
    AlertTarget.Today -> "legumlex://counsel/today"
    is AlertTarget.Case -> "legumlex://counsel/case/${t.id}"
}

/** A counsel alert push (data workspace=counsel with an alert_id): posted under that tag so one alert never shows twice. */
internal fun isCounselAlert(data: Map<String, String>): Boolean = data["workspace"] == "counsel" && !data["alert_id"].isNullOrBlank()

/** The bell's list: newest first, each opening where it points. */
@Composable
fun BoardAlertsScreen(onOpen: (AlertTarget) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    var items by remember { mutableStateOf<List<BoardAlert>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    val today = BoardDates.today()
    LaunchedEffect(tick) { repo.alerts().onSuccess { items = it; error = null }.onFailure { error = it.boardMessage() } }

    LazyColumn(
        Modifier.fillMaxSize().background(Lex.colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = com.legumlex.client.ui.navigation.LocalCounselClearance.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(eyebrow = "Counsel", title = "Alerts") }
        val list = items
        when {
            error != null && list == null -> item { LexCard { LexEmpty("Alerts Could Not Be Opened", error.orEmpty(), icon = Icons.Outlined.ErrorOutline, action = { LegumSecondaryButton("Try Again", { tick++ }) }) } }
            list == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = Lex.colors.mutedForeground) } }
            list.isEmpty() -> item { LexCard { LexEmpty("No Alerts Yet", "The morning list and any moved date will show here.", icon = Icons.Outlined.NotificationsNone) } }
            else -> items(list, key = { it.id }) { a ->
                LexCard(onClick = { onOpen(alertTarget(a.route)) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                        if (a.body.isNotBlank()) Text(a.body, style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                        BoardDates.checkedLabel(a.at, today)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Lex.colors.mutedForeground) }
                    }
                }
            }
        }
    }
}
