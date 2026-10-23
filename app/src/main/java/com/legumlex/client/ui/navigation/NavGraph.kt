package com.legumlex.client.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.TokenStore
import kotlinx.coroutines.launch
import com.legumlex.client.feature.auth.LoginScreen
import com.legumlex.client.feature.auth.SplashScreen
import com.legumlex.client.feature.calendar.CalendarScreen
import com.legumlex.client.feature.cases.CaseDetailScreen
import com.legumlex.client.feature.cases.CaseDocViewerScreen
import com.legumlex.client.feature.cases.CasesScreen
import com.legumlex.client.feature.consultations.ConsultationDetailScreen
import com.legumlex.client.feature.consultations.detail.AhcDocumentViewerScreen
import com.legumlex.client.feature.consultations.detail.DocFileViewerScreen
import com.legumlex.client.feature.digital_office.VideoLobbyScreen
import com.legumlex.client.feature.consultations.ConsultationsScreen
import com.legumlex.client.feature.desk.DeskScreen
import com.legumlex.client.feature.consultations.detail.ConversationThreadScreen
import com.legumlex.client.feature.digital_office.DoIntakeScreen
import com.legumlex.client.feature.digital_office.DoLandingScreen
import com.legumlex.client.feature.documents.VaultScreen
import com.legumlex.client.feature.finance.InvoiceDetailScreen
import com.legumlex.client.feature.finance.InvoicesScreen
import com.legumlex.client.feature.healthchecks.funnel.AhcFunnelScreen
import com.legumlex.client.feature.healthchecks.funnel.OnboardingAhcHandoffScreen
import com.legumlex.client.feature.hearings.HearingDetailScreen
import com.legumlex.client.feature.home.HomeScreen
import com.legumlex.client.feature.more.MoreScreen
import com.legumlex.client.feature.projects.ProjectDetailScreen
import com.legumlex.client.feature.projects.ProjectActivityScreen
import com.legumlex.client.feature.projects.ProjectDiscussionsScreen
import com.legumlex.client.feature.projects.ProjectFilesScreen
import com.legumlex.client.feature.projects.ProjectMilestonesScreen
import com.legumlex.client.feature.projects.ProjectsScreen
import com.legumlex.client.feature.projects.TaskDetailScreen

@Composable
fun LegumNavGraph(navController: NavHostController) {
    // Where a waiting item opens (the action queue, now in Notifications).
    val navCtx = androidx.compose.ui.platform.LocalContext.current
    val actionRoutes = com.legumlex.client.feature.home.ActionRoutes(
        invoice      = { id -> navController.navigate(Route.InvoiceDetail.build(id)) },
        contract     = { _  -> navController.navigate(Route.Vault.path) },
        consultation = { id -> navController.navigate(Route.ConsultationDetail.build(id)) },
        case         = { id -> navController.navigate(Route.CaseDetail.build(id)) },
        notice       = { id -> navController.navigate(Route.NoticeDetail.build(id)) },
        task         = { id -> navController.navigate(Route.TaskDetail.build(id)) },
        project      = { id -> navController.navigate(Route.ProjectDetail.build(id)) },
        agreement    = { id -> navController.navigate(Route.RegistryAgreement.build(id)) },
        ahcFunnel    = { navController.navigate(Route.AhcFunnel.path) },
    )
    // App-wide screen transitions: a subtle horizontal slide + fade. Forward
    // navigation slides in from the right; back navigation reverses it.
    val slideDur = 300
    val fadeDur  = 220
    NavHost(
        navController    = navController,
        startDestination = Route.Splash.path,
        enterTransition = {
            slideInHorizontally(tween(slideDur)) { it / 4 } + fadeIn(tween(fadeDur))
        },
        exitTransition = {
            slideOutHorizontally(tween(slideDur)) { -it / 6 } + fadeOut(tween(fadeDur))
        },
        popEnterTransition = {
            slideInHorizontally(tween(slideDur)) { -it / 6 } + fadeIn(tween(fadeDur))
        },
        popExitTransition = {
            slideOutHorizontally(tween(slideDur)) { it / 4 } + fadeOut(tween(fadeDur))
        },
    ) {
        // --- Auth flow ---
        composable(Route.Splash.path) {
            // A stored sign-in opens the app where it was; the server's first
            // refusal of it (AppShell, SessionEvents) returns to sign-in.
            val context = LocalContext.current
            val tokens  = remember(context) { TokenStore(context.applicationContext) }
            val scope   = rememberCoroutineScope()
            SplashScreen(
                onTimeout = {
                    scope.launch {
                        val signedIn = !tokens.read().isNullOrBlank()
                        tokens.loadRole()
                        // A lead asks the firm where they stand: approved since
                        // the last launch, the app opens as the client's.
                        var lead = signedIn && com.legumlex.client.data.auth.SessionRole.isLead
                        if (lead) {
                            val auth = AuthRepository(ApiClient.get(context), tokens, context.cacheDir)
                            if (kotlinx.coroutines.withTimeoutOrNull(4_000) { auth.leadStanding() } == "client") lead = false
                        }
                        val to = when { !signedIn -> Route.Login.path; lead -> Route.LeadHome.path; else -> Route.Home.path }
                        navController.navigate(to) {
                            popUpTo(Route.Splash.path) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Route.Login.path) {
            LoginScreen(
                onLoginSuccess = {
                    val home = if (com.legumlex.client.data.auth.SessionRole.isLead) Route.LeadHome.path else Route.Home.path
                    navController.navigate(home) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
                onAskLexIntelligence = { navController.navigate(Route.NewConsultation.build()) },
            )
        }

        // --- A lead's app (owner, 2026-10-20) ---
        composable(Route.LeadHome.path) {
            com.legumlex.client.feature.lead.LeadHomeScreen(
                onAsk            = { navController.navigate(Route.NewConsultation.build()) },
                onAgreementCheck = { navController.navigate(Route.LeadAhc.path) },
                onEnquiries      = { navController.navigateTab(Route.LeadEnquiries.path) },
                onOpenAccount    = { navController.navigate(Route.LeadOnboard.path) },
                // Approved: lead/me has already handed over the client's token
                // and retired the lead's, so the client app opens at once.
                onBecameClient   = { navController.navigate(Route.Home.path) { popUpTo(0) { inclusive = true } } },
            )
        }
        composable(Route.LeadOnboard.path) {
            com.legumlex.client.feature.lead.LeadOnboardingScreen(onClose = { navController.navigateTab(Route.LeadHome.path) })
        }
        composable(Route.LeadEnquiries.path) {
            com.legumlex.client.feature.lead.LeadEnquiriesScreen(
                onOpen = { hash -> navController.navigate(Route.LeadThread.build(hash)) },
                onAsk  = { navController.navigate(Route.NewConsultation.build()) },
            )
        }
        composable(
            Route.LeadThread.path,
            arguments = listOf(navArgument(Route.LeadThread.ARG) { type = NavType.StringType }),
        ) { entry ->
            com.legumlex.client.feature.lead.LeadThreadScreen(entry.arguments?.getString(Route.LeadThread.ARG).orEmpty())
        }
        composable(Route.LeadAhc.path) {
            com.legumlex.client.feature.lead.LeadAhcScreen(
                onClose     = { navController.popBackStack() },
                onEnquiries = { navController.navigateTab(Route.LeadEnquiries.path) },
            )
        }
        composable(Route.LeadMore.path) {
            val context = LocalContext.current
            val auth    = remember(context) { AuthRepository(ApiClient.get(context), TokenStore(context.applicationContext), context.cacheDir) }
            val scope   = rememberCoroutineScope()
            com.legumlex.client.feature.lead.LeadMoreScreen(onSignOut = {
                scope.launch {
                    auth.signOut()
                    navController.navigate(Route.Login.path) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                }
            })
        }

        // --- Main shell (bottom-nav tabs) ---
        composable(Route.Home.path) {
            HomeScreen(
                onOpenLitigation   = { navController.navigate(Route.Matters.path) },
                onOpenProjects     = { navController.navigate(Route.Projects.path) },
                onOpenRegistry     = { navController.navigate(Route.Registry.path) },
                onOpenConsultations = { navController.navigateTab(Route.Consultations.path) },
                onOpenCalendar     = { navController.navigate(Route.Calendar.path) },
                onOpenInvoice      = { id -> navController.navigate(Route.InvoiceDetail.build(id)) },
                onOpenContract     = { _  -> navController.navigate(Route.Vault.path) },
                onOpenConsultation = { id -> navController.navigate(Route.ConsultationDetail.build(id)) },
                onOpenVideoLobby   = { id -> navController.navigate(Route.VideoLobby.build(id)) },
                onOpenCase         = { id -> navController.navigate(Route.CaseDetail.build(id)) },
                onOpenNotice       = { id -> navController.navigate(Route.NoticeDetail.build(id)) },
                onOpenAgreement    = { id -> navController.navigate(Route.RegistryAgreement.build(id)) },
                onOpenTask         = { id -> navController.navigate(Route.TaskDetail.build(id)) },
                onOpenProject      = { id -> navController.navigate(Route.ProjectDetail.build(id)) },
                onAskNew           = { navController.navigate(Route.NewConsultation.build()) },
                onWalkInDigitalOffice = { navController.navigate(Route.DoIntake.build()) },
                onOpenChamberLanding  = { id -> navController.navigate(Route.DoLanding.build(id)) },
                onStartDoSessionWith  = { advocateId ->
                    navController.navigate(Route.DoIntake.build(advocateId))
                },
                onStartAgreementHealthCheckup = { navController.navigate(Route.AhcFunnel.path) },
                onStartLegalNotice = { navController.navigate(Route.Notices.path) },
            )
        }
        composable(Route.Matters.path) {
            CasesScreen(
                onOpenCase = { id -> navController.navigate(Route.CaseDetail.build(id)) },
                onOpenNotices = { navController.navigate(Route.Notices.path) },
            )
        }
        composable(Route.Notices.path) {
            com.legumlex.client.feature.notices.NoticesScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Route.NoticeDetail.build(id)) },
            )
        }
        composable(
            route = Route.NoticeDetail.path,
            arguments = listOf(navArgument(Route.NoticeDetail.ARG) { type = NavType.StringType }),
        ) { entry ->
            val nid = entry.arguments?.getString(Route.NoticeDetail.ARG).orEmpty()
            com.legumlex.client.feature.notices.NoticeDetailScreen(
                noticeId = nid,
                onBack = { navController.popBackStack() },
                onMessageAdvocate = { cid -> navController.navigate(Route.ConsultationDetail.build(cid)) },
                onOpen = { id -> navController.navigate(Route.NoticeDetail.build(id)) },
                onOpenDocument = { ownerNoticeId, fileId, name ->
                    navController.navigate(Route.NoticeDocViewer.build(ownerNoticeId, fileId, name))
                },
                onOpenCase = { id -> navController.navigate(Route.CaseDetail.build(id)) },
            )
        }
        composable(
            route = Route.NoticeDocViewer.path,
            arguments = listOf(
                navArgument(Route.NoticeDocViewer.ARG_NOTICE) { type = NavType.StringType },
                navArgument(Route.NoticeDocViewer.ARG_FILE) { type = NavType.StringType },
                navArgument(Route.NoticeDocViewer.ARG_NAME) { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            com.legumlex.client.feature.notices.NoticeDocViewerScreen(
                noticeId = entry.arguments?.getString(Route.NoticeDocViewer.ARG_NOTICE).orEmpty(),
                fileId = entry.arguments?.getString(Route.NoticeDocViewer.ARG_FILE).orEmpty(),
                fileName = entry.arguments?.getString(Route.NoticeDocViewer.ARG_NAME),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Notifications.path) {
            com.legumlex.client.feature.notifications.NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { n ->
                    // A queue item opens where the queue opened it.
                    n.action?.let { com.legumlex.client.feature.home.openActionItem(navCtx, it, actionRoutes); return@NotificationsScreen }
                    // A firm announcement is read where it stands (tapping marks it read).
                    if (n.link.isBlank() && n.type == com.legumlex.client.feature.notifications.NotifType.GENERAL) return@NotificationsScreen
                    // A matter waiting on the client opens itself.
                    n.link.removePrefix("consultation/").takeIf { n.link.startsWith("consultation/") }?.let {
                        navController.navigate(Route.ConsultationDetail.build(it)); return@NotificationsScreen
                    }
                    // The feed holds only firm announcements and items waiting on
                    // the client; an item whose consultation isn't shared yet
                    // opens the consultations list.
                    if (n.type == com.legumlex.client.feature.notifications.NotifType.ATTENTION) {
                        navController.navigateTab(Route.Consultations.path)
                    }
                },
            )
        }
        composable(Route.Consultations.path) {
            ConsultationsScreen(
                onOpenConsultation = { id ->
                    navController.navigate(Route.ConsultationDetail.build(id))
                },
                onNewConsultation = {
                    navController.navigate(Route.NewConsultation.build())
                },
            )
        }

        composable(
            route = Route.NewConsultation.path,
            arguments = listOf(
                navArgument(Route.NewConsultation.ARG) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) {
            // Consult: the Legum Lex desk, as a client signed in to the portal
            // meets it (feature/desk). It replaced the three-step request form;
            // every way in to a new consultation arrives here.
            // A lead's desk ends in their enquiries, not a client's consultations.
            // Read when used: a guest signs in partway, as a lead or a client.
            val lead = { com.legumlex.client.data.auth.SessionRole.isLead }
            val deskCtx = LocalContext.current
            val tokens = remember(deskCtx) { TokenStore(deskCtx.applicationContext) }
            val scope = rememberCoroutineScope()
            // Leaving the desk: a guest who signed in in it goes to their Home
            // (the sign-in screen beneath is no longer theirs); otherwise back.
            val leave: (() -> Unit) -> Unit = { then ->
                scope.launch {
                    val signedIn = !tokens.read().isNullOrBlank()
                    if (signedIn && navController.previousBackStackEntry?.destination?.route == Route.Login.path) {
                        navController.navigate(if (lead()) Route.LeadHome.path else Route.Home.path) { popUpTo(0) { inclusive = true } }
                    } else navController.popBackStack()
                    then()
                }
            }
            DeskScreen(
                onClose = { leave {} },
                onOpenMatters = {
                    leave { navController.navigateTab(if (lead()) Route.LeadEnquiries.path else Route.Consultations.path) }
                },
                onOpenConsultation = { id ->
                    leave {
                        if (lead()) navController.navigateTab(Route.LeadEnquiries.path)
                        else navController.navigate(Route.ConsultationDetail.build(id.toString()))
                    }
                },
                // "Have an agreement read": the app's Agreement Check (a lead's
                // is the website's, under the lead's token).
                onAgreement = { navController.navigate(if (lead()) Route.LeadAhc.path else Route.AhcFunnel.path) },
            )
        }
        composable(Route.DigitalOffice.path) {
            com.legumlex.client.feature.digital_office.DigitalOfficeScreen(
                onWalkIn           = { id -> navController.navigate(Route.DoIntake.build(id)) },
                onOpenLanding      = { id -> navController.navigate(Route.DoLanding.build(id)) },
                onOpenConsultation = { id -> navController.navigate(Route.ConsultationDetail.build(id)) },
                onAskAdvocate      = { navController.navigate(Route.NewConsultation.build()) },
            )
        }
        composable(Route.Registry.path) {
            com.legumlex.client.feature.registry.RegistryScreen(
                onOpenAgreement = { id -> navController.navigate(Route.RegistryAgreement.build(id)) },
            )
        }
        composable(
            route     = Route.RegistryAgreement.path,
            arguments = listOf(navArgument(Route.RegistryAgreement.ARG) { type = NavType.StringType }),
        ) {
            com.legumlex.client.feature.registry.RegistryAgreementScreen()
        }
        composable(Route.Profile.path) {
            com.legumlex.client.feature.profile.ProfileScreen()
        }
        composable(Route.More.path) {
            val context = LocalContext.current
            val auth    = remember(context) {
                AuthRepository(ApiClient.get(context), TokenStore(context.applicationContext), context.cacheDir)
            }
            val scope   = rememberCoroutineScope()
            MoreScreen(
                onOpenVault    = { navController.navigate(Route.Vault.path) },
                onOpenInvoices = { navController.navigate(Route.Invoices.path) },
                onOpenConsultations = { navController.navigate(Route.Consultations.path) },
                onAskAdvocate  = { navController.navigate(Route.NewConsultation.build()) },
                onOpenNotices  = { navController.navigate(Route.Notices.path) },
                onOpenCalendar = { navController.navigate(Route.Calendar.path) },
                onOpenProfile  = { navController.navigate(Route.Profile.path) },
                onOpenLitigation = { navController.navigate(Route.Matters.path) },
                onOpenProjects = { navController.navigate(Route.Projects.path) },
                onOpenRegistry = { navController.navigate(Route.Registry.path) },
                onOpenNotifications = { navController.navigate(Route.Notifications.path) },
                onSignOut      = {
                    scope.launch {
                        auth.signOut()
                        navController.navigate(Route.Login.path) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        // --- Nested destinations ---
        composable(
            route     = Route.CaseDetail.path,
            arguments = listOf(navArgument(Route.CaseDetail.ARG) { type = NavType.StringType }),
        ) { entry ->
            val caseId = entry.arguments?.getString(Route.CaseDetail.ARG).orEmpty()
            CaseDetailScreen(
                caseId             = caseId,
                onBack             = { navController.popBackStack() },
                onOpenConsultation = { id -> navController.navigate(Route.ConsultationDetail.build(id)) },
                onOpenOrderDocument = { order ->
                    navController.navigate(Route.CaseDocViewer.build("order", order.id, order.documentName))
                },
                onOpenDocument     = { file ->
                    navController.navigate(Route.CaseDocViewer.build("file", file.id, file.name))
                },
                onOpenHearing      = { id -> navController.navigate(Route.HearingDetail.build(id)) },
            )
        }

        composable(
            route     = Route.HearingDetail.path,
            arguments = listOf(navArgument(Route.HearingDetail.ARG) { type = NavType.StringType }),
            // Deep-link target for hearing-reminder notifications. The arg
            // name must match Route.HearingDetail.ARG so the nav library
            // populates the same SavedStateHandle key as in-app navigation.
            deepLinks = listOf(navDeepLink {
                uriPattern = "legumlex://hearing/{${Route.HearingDetail.ARG}}"
            }),
        ) { entry ->
            val hearingId = entry.arguments?.getString(Route.HearingDetail.ARG).orEmpty()
            HearingDetailScreen(
                hearingId  = hearingId,
                onBack     = { navController.popBackStack() },
                onOpenCase = { id ->
                    navController.navigate(Route.CaseDetail.build(id)) {
                        popUpTo(Route.Matters.path)
                    }
                },
            )
        }

        composable(
            route     = Route.ConsultationDetail.path,
            arguments = listOf(navArgument(Route.ConsultationDetail.ARG) { type = NavType.StringType }),
            // Deep-link target for push notifications (consultation reply,
            // doc-request added). The URI scheme matches what the FCM
            // service builds in ClientFcmService.buildDeepLinkUri.
            deepLinks = listOf(navDeepLink {
                uriPattern = "legumlex://consultation/{${Route.ConsultationDetail.ARG}}"
            }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ConsultationDetail.ARG).orEmpty()
            ConsultationDetailScreen(
                consultationId = id,
                onBack         = { navController.popBackStack() },
                onNewConsultation = { navController.navigate(Route.NewConsultation.build()) },
                onOpenCase     = { caseId ->
                    navController.navigate(Route.CaseDetail.build(caseId)) {
                        popUpTo(Route.Matters.path)
                    }
                },
                onOpenThread   = { cid ->
                    navController.navigate(Route.ConsultationThread.build(cid))
                },
                onOpenConsultation = { otherId ->
                    // Linked / escalated / converted-to navigation. Replace
                    // the current detail in the stack instead of pushing on
                    // top, so back from the linked consultation returns the
                    // user to the list — not to a chain of stacked detail
                    // screens they have to back through one at a time.
                    navController.navigate(Route.ConsultationDetail.build(otherId)) {
                        popUpTo(Route.ConsultationDetail.path) { inclusive = true }
                    }
                },
                onOpenAhcDocument = { cid ->
                    navController.navigate(Route.AhcDocumentViewer.build(cid))
                },
                onOpenDocFile = { rid, name ->
                    navController.navigate(Route.DocFileViewer.build(rid, name))
                },
                onOpenVideoLobby = { cid ->
                    // Skip-to-lobby: replace the detail so Back returns to the list,
                    // not to a detail screen the client was bounced through.
                    navController.navigate(Route.VideoLobby.build(cid)) {
                        popUpTo(Route.ConsultationDetail.path) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route     = Route.VideoLobby.path,
            arguments = listOf(navArgument(Route.VideoLobby.ARG) { type = NavType.StringType }),
        ) { entry ->
            val cid = entry.arguments?.getString(Route.VideoLobby.ARG).orEmpty()
            VideoLobbyScreen(
                consultationId = cid,
                onBack         = { navController.popBackStack() },
                onOpenConsultation = {
                    navController.navigate(Route.ConsultationDetail.build(cid)) {
                        popUpTo(Route.VideoLobby.path) { inclusive = true }
                    }
                },
                onOpenChamber  = { navController.navigate(Route.DigitalOffice.path) },
            )
        }

        composable(
            route     = Route.CaseDocViewer.path,
            arguments = listOf(
                navArgument(Route.CaseDocViewer.ARG_KIND) { type = NavType.StringType },
                navArgument(Route.CaseDocViewer.ARG_ID) { type = NavType.StringType },
                navArgument(Route.CaseDocViewer.ARG_NAME) { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val kind = entry.arguments?.getString(Route.CaseDocViewer.ARG_KIND).orEmpty()
            val id   = entry.arguments?.getString(Route.CaseDocViewer.ARG_ID).orEmpty()
            val name = entry.arguments?.getString(Route.CaseDocViewer.ARG_NAME)
                ?.takeIf { it.isNotBlank() }  // Navigation has already decoded it
            CaseDocViewerScreen(kind = kind, id = id, fileName = name, onBack = { navController.popBackStack() })
        }

        composable(
            route     = Route.DocFileViewer.path,
            arguments = listOf(
                navArgument(Route.DocFileViewer.ARG) { type = NavType.StringType },
                navArgument(Route.DocFileViewer.ARG_NAME) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) { entry ->
            val rid  = entry.arguments?.getString(Route.DocFileViewer.ARG).orEmpty()
            val name = entry.arguments?.getString(Route.DocFileViewer.ARG_NAME)
                ?.takeIf { it.isNotBlank() }  // Navigation has already decoded it
            DocFileViewerScreen(
                requestId = rid,
                fileName  = name,
                onBack    = { navController.popBackStack() },
            )
        }

        composable(
            route     = Route.AhcDocumentViewer.path,
            arguments = listOf(navArgument(Route.AhcDocumentViewer.ARG) { type = NavType.StringType }),
        ) { entry ->
            val cid = entry.arguments?.getString(Route.AhcDocumentViewer.ARG).orEmpty()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            AhcDocumentViewerScreen(
                consultationId    = cid,
                onBack            = { navController.popBackStack() },
                // The cached document, handed to the phone: open it, save it, or share it.
                onDownloadToPhone = {
                    val f = java.io.File(ctx.cacheDir, "ahc/ahc_$cid.pdf")
                    if (f.exists()) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "Save or share the agreement"))
                    } else {
                        android.widget.Toast.makeText(ctx, "The agreement is still loading.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        composable(
            route     = Route.ConsultationThread.path,
            arguments = listOf(navArgument(Route.ConsultationThread.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ConsultationThread.ARG).orEmpty()
            ConversationThreadScreen(
                consultationId = id,
                onBack         = { navController.popBackStack() },
                onNewQuestion  = { navController.navigate(Route.NewConsultation.build()) },
            )
        }

        composable(
            route     = Route.DoIntake.path,
            arguments = listOf(navArgument(Route.DoIntake.ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            val advocateStaffId = entry.arguments?.getString(Route.DoIntake.ARG)?.toIntOrNull()
            DoIntakeScreen(
                advocateStaffId = advocateStaffId,
                onBack          = { navController.popBackStack() },
                onSubmitted     = { consultationId ->
                    navController.popBackStack()
                    navController.navigate(Route.ConsultationDetail.build(consultationId))
                },
            )
        }

        composable(
            route     = Route.DoLanding.path,
            arguments = listOf(navArgument(Route.DoLanding.ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            val advocateStaffId = entry.arguments?.getString(Route.DoLanding.ARG)?.toIntOrNull()
            DoLandingScreen(
                advocateStaffId = advocateStaffId,
                onBack          = { navController.popBackStack() },
                onWalkIn        = { id -> navController.navigate(Route.DoIntake.build(id)) },
            )
        }

        composable(Route.Calendar.path) {
            CalendarScreen(
                onBack        = { navController.popBackStack() },
                onOpenHearing = { id -> navController.navigate(Route.HearingDetail.build(id)) },
            )
        }

        composable(Route.Vault.path) {
            VaultScreen(onBack = { navController.popBackStack() })
        }

        // --- Projects & Tasks ---
        composable(Route.Projects.path) {
            ProjectsScreen(
                onOpenProject = { id -> navController.navigate(Route.ProjectDetail.build(id)) },
            )
        }
        composable(
            route     = Route.ProjectDetail.path,
            arguments = listOf(navArgument(Route.ProjectDetail.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ProjectDetail.ARG).orEmpty()
            ProjectDetailScreen(
                projectId  = id,
                onBack     = { navController.popBackStack() },
                onOpenTask = { taskId, canComment -> navController.navigate(Route.TaskDetail.build(taskId, canComment)) },
                onOpenMilestones = { pid -> navController.navigate(Route.ProjectMilestones.build(pid)) },
                onOpenFiles = { pid -> navController.navigate(Route.ProjectFiles.build(pid)) },
                onOpenDiscussions = { pid -> navController.navigate(Route.ProjectDiscussions.build(pid)) },
                onOpenActivity = { pid -> navController.navigate(Route.ProjectActivity.build(pid)) },
            )
        }
        composable(
            route     = Route.ProjectMilestones.path,
            arguments = listOf(navArgument(Route.ProjectMilestones.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ProjectMilestones.ARG).orEmpty()
            ProjectMilestonesScreen(projectId = id, onBack = { navController.popBackStack() })
        }
        composable(
            route     = Route.ProjectFiles.path,
            arguments = listOf(navArgument(Route.ProjectFiles.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ProjectFiles.ARG).orEmpty()
            ProjectFilesScreen(projectId = id, onBack = { navController.popBackStack() })
        }
        composable(
            route     = Route.ProjectDiscussions.path,
            arguments = listOf(navArgument(Route.ProjectDiscussions.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ProjectDiscussions.ARG).orEmpty()
            ProjectDiscussionsScreen(
                projectId = id,
                onBack = { navController.popBackStack() },
                onOpenThread = { did -> navController.navigate(Route.ProjectDiscussionThread.build(id, did)) },
            )
        }
        composable(
            route     = Route.ProjectDiscussionThread.path,
            arguments = listOf(
                navArgument(Route.ProjectDiscussionThread.ARG) { type = NavType.StringType },
                navArgument(Route.ProjectDiscussionThread.ARG_DISC) { type = NavType.StringType },
            ),
        ) { entry ->
            val pid = entry.arguments?.getString(Route.ProjectDiscussionThread.ARG).orEmpty()
            val did = entry.arguments?.getString(Route.ProjectDiscussionThread.ARG_DISC).orEmpty()
            ProjectDiscussionsScreen(projectId = pid, discussionId = did, onBack = { navController.popBackStack() })
        }
        composable(
            route     = Route.ProjectActivity.path,
            arguments = listOf(navArgument(Route.ProjectActivity.ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.ProjectActivity.ARG).orEmpty()
            ProjectActivityScreen(projectId = id, onBack = { navController.popBackStack() })
        }
        composable(
            route     = Route.TaskDetail.path,
            arguments = listOf(
                navArgument(Route.TaskDetail.ARG) { type = NavType.StringType },
                navArgument(Route.TaskDetail.ARG_CAN_COMMENT) { type = NavType.BoolType; defaultValue = true },
            ),
        ) { entry ->
            val id = entry.arguments?.getString(Route.TaskDetail.ARG).orEmpty()
            val canComment = entry.arguments?.getBoolean(Route.TaskDetail.ARG_CAN_COMMENT) ?: true
            TaskDetailScreen(
                taskId = id,
                canComment = canComment,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.Invoices.path) {
            InvoicesScreen(
                onBack         = { navController.popBackStack() },
                onOpenInvoice  = { id -> navController.navigate(Route.InvoiceDetail.build(id)) },
            )
        }

        composable(Route.AhcFunnel.path) {
            AhcFunnelScreen(
                onExit         = { navController.popBackStack() },
                onResultReady  = { consultationId ->
                    if (consultationId != null) {
                        navController.navigate(Route.ConsultationDetail.build(consultationId)) {
                            popUpTo(Route.Home.path)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(Route.OnboardingAhc.path) {
            OnboardingAhcHandoffScreen(
                userFirstName  = null,
                userEmail      = null,
                onStartHealthCheck = {
                    navController.navigate(Route.AhcFunnel.path) {
                        popUpTo(Route.OnboardingAhc.path) { inclusive = true }
                    }
                },
                onSkipToHome = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route     = Route.InvoiceDetail.path,
            arguments = listOf(navArgument(Route.InvoiceDetail.ARG) { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink {
                uriPattern = "legumlex://invoice/{${Route.InvoiceDetail.ARG}}"
            }),
        ) { entry ->
            val id = entry.arguments?.getString(Route.InvoiceDetail.ARG).orEmpty()
            InvoiceDetailScreen(
                invoiceId = id,
                onBack    = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Go to a bottom-bar tab as the bar itself does, so its state is kept. Home is
 * the root of the signed-in graph (Splash is popped on the way in), so tabs pop
 * back to it; popping to the start destination would be a silent no-op.
 */
internal fun NavHostController.navigateTab(path: String) {
    // Home is the root every tab pops back to (a lead's Home, for a lead's
    // tabs). Going home clears the stack to it outright: saving and restoring
    // Home's own state brought back whatever was open above it (a
    // consultation opened from the Action Queue stayed).
    val home = if (path == Route.LeadHome.path || path.startsWith(Route.LeadHome.path + "/")) Route.LeadHome.path else Route.Home.path
    if (path == home) {
        if (!popBackStack(home, inclusive = false)) navigate(home) { launchSingleTop = true }
        return
    }
    // A tab always opens at its own root. Restoring a tab's saved stack sent a
    // tap back to the very screen already open (More → a hearing → More did
    // nothing), so the tab looked dead.
    navigate(path) {
        popUpTo(home)
        launchSingleTop = true
    }
}
