package com.legumlex.client

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

    // App-wide unread badge — refresh the shared count when the route changes
    // (covers launch, and re-sync after the Notifications screen edits it).
    val context = androidx.compose.ui.platform.LocalContext.current
    val unreadCount by com.legumlex.client.feature.notifications.NotificationHub.unread.collectAsState()
    LaunchedEffect(currentRoute) {
        com.legumlex.client.feature.notifications.NotificationHub.refresh(context)
    }
    // The signed-in contact, for the header's initials: fetched once a
    // signed-in screen is reached, and again after a fresh sign-in.
    val profile by com.legumlex.client.feature.profile.ProfileHub.profile.collectAsState()
    LaunchedEffect(currentRoute, profile) {
        if (profile == null && showAppFooter(currentRoute)) {
            com.legumlex.client.feature.profile.ProfileHub.refresh(context)
        }
    }

    Scaffold(
        topBar = {
            if (showAppHeader(currentRoute)) {
                AppHeader(
                    showBack = showHeaderBack(currentRoute, canPop),
                    onBack = { navController.popBackStack() },
                    onNotifications = { navController.navigate(Route.Notifications.path) },
                    unreadCount = unreadCount,
                    initials = profile?.initials,
                    avatarUrl = profile?.avatarUrl,
                    onProfile = { navController.navigate(Route.Profile.path) { launchSingleTop = true } },
                )
            }
        },
        bottomBar = { if (showAppFooter(currentRoute)) LegumBottomBar(navController) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            LegumNavGraph(navController)
        }
    }
}
