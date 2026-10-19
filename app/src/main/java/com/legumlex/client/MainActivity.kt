package com.legumlex.client

import com.legumlex.client.ui.theme.grain
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.legumlex.client.ui.navigation.AppHeader
import com.legumlex.client.ui.navigation.Route
import com.legumlex.client.ui.navigation.LegumBottomBar
import com.legumlex.client.ui.navigation.LegumNavGraph
import com.legumlex.client.ui.navigation.showAppFooter
import com.legumlex.client.ui.navigation.showAppHeader
import com.legumlex.client.ui.navigation.showHeaderBack
import com.legumlex.client.ui.theme.LegumLexTheme
import kotlinx.coroutines.launch

/**
 * The host Activity. The app takes no payments: fees and invoices are paid on
 * the website, on the invoice's own page.
 */
class MainActivity : ComponentActivity() {

    // singleTask activity — onNewIntent fires for notification taps while
    // the app is alive. We hoist the latest intent into Compose state so the
    // NavController can re-process its deepLink when it arrives.
    private var latestIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        latestIntent = intent
        setContent {
            LegumLexTheme {
                AppShell(latestIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }

}

@Composable
private fun AppShell(deepLinkIntent: Intent?) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val canPop = navController.previousBackStackEntry != null

    // Re-evaluate deep links whenever the activity hands us a new intent
    // (notification taps after launch). The NavController matches the
    // intent's data URI against composables' registered deepLinks.
    var handledKey by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(deepLinkIntent) {
        val k = System.identityHashCode(deepLinkIntent)
        if (deepLinkIntent != null && k != handledKey && deepLinkIntent.data != null) {
            navController.handleDeepLink(deepLinkIntent)
            handledKey = k
        }
    }

    // The server ended the sign-in (expired, or revoked): forget it here and
    // return to sign-in, unless already in the sign-in flow.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        val auth = com.legumlex.client.data.auth.AuthRepository(
            com.legumlex.client.data.api.ApiClient.get(appContext),
            com.legumlex.client.data.auth.TokenStore(appContext),
            appContext.cacheDir,
        )
        com.legumlex.client.data.auth.SessionEvents.ended.collect {
            auth.forget()
            val here = navController.currentDestination?.route
            if (here !in setOf(Route.Splash.path, Route.Welcome.path, Route.Login.path, Route.Register.path)) {
                navController.navigate(Route.Login.path) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // App-wide unread badge: read on a signed-in screen at most once a minute
    // as the route changes, and on every return to the app. The Notifications
    // screen keeps the hub current itself as things are read.
    val context = androidx.compose.ui.platform.LocalContext.current
    val unreadCount by com.legumlex.client.feature.notifications.NotificationHub.unread.collectAsState()
    val actionBundle by com.legumlex.client.feature.home.ActionHub.bundle.collectAsState()
    var lastUnreadAt by remember { mutableStateOf(0L) }
    var resumes by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumes) { lastUnreadAt = 0L }
    LaunchedEffect(currentRoute, resumes) {
        val now = System.currentTimeMillis()
        if (showAppFooter(currentRoute) && now - lastUnreadAt >= 60_000L) {
            lastUnreadAt = now
            com.legumlex.client.feature.notifications.NotificationHub.refresh(context)
            com.legumlex.client.feature.home.ActionHub.refresh(context)
        }
    }
    // The signed-in contact, for the header's initials: fetched once a
    // signed-in screen is reached, and again after a fresh sign-in.
    val profile by com.legumlex.client.feature.profile.ProfileHub.profile.collectAsState()
    LaunchedEffect(currentRoute, profile) {
        if (profile == null && showAppFooter(currentRoute)) {
            com.legumlex.client.feature.profile.ProfileHub.refresh(context)
        }
    }

    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        topBar = {
            if (showAppHeader(currentRoute)) {
                AppHeader(
                    showBack = showHeaderBack(currentRoute, canPop),
                    // The system back: a wizard steps back a page before it
                    // leaves, as it does for the back gesture.
                    onBack = { backDispatcher?.onBackPressed() ?: navController.popBackStack() },
                    onNotifications = { navController.navigate(Route.Notifications.path) },
                    unreadCount = unreadCount,
                    onActions = { navController.navigate(Route.ActionQueue.path) { launchSingleTop = true } },
                    actionCount = actionBundle?.total ?: 0,
                    actionCritical = (actionBundle?.critical ?: 0) > 0,
                    initials = profile?.initials,
                    avatarUrl = profile?.avatarUrl,
                    onProfile = { navController.navigate(Route.Profile.path) { launchSingleTop = true } },
                )
            }
        },
        bottomBar = { if (showAppFooter(currentRoute)) LegumBottomBar(navController) },
    ) { innerPadding ->
        // The paper grain is the page's alone: the header and bottom bar are
        // solid chrome, so the three read as frame and paper.
        Box(Modifier.padding(innerPadding).grain()) {
            LegumNavGraph(navController)
        }
    }
}
