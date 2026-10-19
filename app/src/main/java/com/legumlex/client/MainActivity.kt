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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.legumlex.client.feature.payment.LocalRazorpayResults
import com.legumlex.client.feature.payment.RazorpayResult
import com.legumlex.client.ui.navigation.AppHeader
import com.legumlex.client.ui.navigation.Route
import com.legumlex.client.ui.navigation.LegumBottomBar
import com.legumlex.client.ui.navigation.LegumNavGraph
import com.legumlex.client.ui.navigation.showAppFooter
import com.legumlex.client.ui.navigation.showAppHeader
import com.legumlex.client.ui.navigation.showHeaderBack
import com.legumlex.client.ui.theme.LegumLexTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The host Activity is also the Razorpay payment-result receiver. Razorpay's
 * Checkout SDK discovers the listener interface via reflection on the calling
 * Activity, so the implementation must live here. The result is forwarded onto
 * a hot SharedFlow which any composable can collect via [LocalRazorpayResults].
 */
class MainActivity :
    ComponentActivity(),
    PaymentResultWithDataListener {

    // singleTask activity — onNewIntent fires for notification taps while
    // the app is alive. We hoist the latest intent into Compose state so the
    // NavController can re-process its deepLink when it arrives.
    private var latestIntent by mutableStateOf<Intent?>(null)

    private val _razorpay = MutableSharedFlow<RazorpayResult>(extraBufferCapacity = 1)
    val razorpayResults: SharedFlow<RazorpayResult> = _razorpay.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        latestIntent = intent
        setContent {
            LegumLexTheme {
                CompositionLocalProvider(LocalRazorpayResults provides razorpayResults) {
                    AppShell(latestIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }

    // ── Razorpay PaymentResultWithDataListener ────────────────────────────────

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        // The SDK guarantees these on a captured payment, but be defensive —
        // a malformed callback should surface as a failure rather than crash.
        val paymentId = razorpayPaymentId
        val orderId   = paymentData?.orderId
        val signature = paymentData?.signature
        lifecycleScope.launch {
            if (paymentId == null || orderId == null || signature == null) {
                _razorpay.emit(
                    RazorpayResult.Failed(
                        code = -1,
                        message = "Payment confirmation was incomplete.",
                    )
                )
            } else {
                _razorpay.emit(
                    RazorpayResult.Success(
                        paymentId = paymentId,
                        orderId   = orderId,
                        signature = signature,
                    )
                )
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        // code 2 = payment cancelled by the user; everything else is a genuine
        // failure (network / TLS / invalid options / unknown).
        lifecycleScope.launch {
            _razorpay.emit(
                RazorpayResult.Failed(
                    code    = code,
                    message = if (code == 2) "Payment cancelled."
                              else "Payment couldn't be completed. Please try again.",
                )
            )
        }
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

    Scaffold(
        topBar = {
            if (showAppHeader(currentRoute)) {
                AppHeader(
                    showBack = showHeaderBack(currentRoute, canPop),
                    onBack = { navController.popBackStack() },
                    onNotifications = { navController.navigate(Route.Notifications.path) },
                    unreadCount = unreadCount,
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
