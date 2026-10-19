package com.legumlex.client.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.legumlex.client.R

sealed class Route(val path: String) {
    // Auth flow (no bottom nav)
    data object Splash   : Route("splash")
    data object Welcome  : Route("welcome")
    data object Login    : Route("login")
    data object Register : Route("register")

    // Main shell (bottom nav)
    data object Home          : Route("home")
    data object Matters       : Route("matters")
    data object Consultations : Route("consultations")
    data object More          : Route("more")
    data object Registry      : Route("registry")
    data object DigitalOffice : Route("office")

    // Account
    data object Profile       : Route("profile")

    // Nested destinations.
    data object CaseDetail : Route("matters/{caseId}") {
        const val ARG = "caseId"
        fun build(caseId: String) = "matters/$caseId"
    }
    data object HearingDetail : Route("hearings/d/{hearingId}") {
        const val ARG = "hearingId"
        fun build(hearingId: String) = "hearings/d/$hearingId"
    }
    data object ConsultationDetail : Route("consultations/d/{consultationId}") {
        const val ARG = "consultationId"
        fun build(id: String) = "consultations/d/$id"
    }
    data object NewConsultation : Route("consultations/new?preset={preset}") {
        const val ARG = "preset"
        // Preset apiValue from ConsultationKind (e.g. "legal_notice"); null = no preset.
        fun build(preset: String? = null): String =
            if (preset.isNullOrBlank()) "consultations/new" else "consultations/new?preset=$preset"
    }
    data object ConsultationThread : Route("consultations/d/{consultationId}/thread") {
        const val ARG = "consultationId"
        fun build(id: String) = "consultations/d/$id/thread"
    }
    data object Calendar       : Route("calendar")
    data object Vault          : Route("vault")
    data object Invoices       : Route("invoices")
    data object Notifications  : Route("notifications")
    data object Notices        : Route("notices")
    data object NewNotice      : Route("notices/new?parent={parent}&to={to}") {
        const val ARG_PARENT = "parent"
        const val ARG_TO = "to"
        /** Fresh notice (fork) when no parent; a nested response when parent is set. */
        fun build(parentId: String? = null, recipientName: String? = null): String {
            if (parentId == null) return "notices/new"
            val to = android.net.Uri.encode(recipientName.orEmpty())
            return "notices/new?parent=$parentId&to=$to"
        }
    }
    data object NoticeDetail   : Route("notices/d/{noticeId}") {
        const val ARG = "noticeId"
        fun build(id: String) = "notices/d/$id"
    }
    data object NoticeDocViewer : Route("notices/{noticeId}/doc/{fileId}?name={name}") {
        const val ARG_NOTICE = "noticeId"
        const val ARG_FILE = "fileId"
        const val ARG_NAME = "name"
        fun build(noticeId: String, fileId: String, name: String?): String {
            val n = android.net.Uri.encode(name.orEmpty())
            return "notices/$noticeId/doc/$fileId?name=$n"
        }
    }
    data object AhcFunnel      : Route("healthchecks/funnel")
    data object OnboardingAhc  : Route("onboarding/ahc")
    data object InvoiceDetail  : Route("invoices/d/{invoiceId}") {
        const val ARG = "invoiceId"
        fun build(id: String) = "invoices/d/$id"
    }
    data object Projects       : Route("projects")
    data object RegistryAgreement : Route("registry/{agreementId}") {
        const val ARG = "agreementId"
        fun build(id: String) = "registry/$id"
    }
    data object ProjectDetail  : Route("projects/{projectId}") {
        const val ARG = "projectId"
        fun build(id: String) = "projects/$id"
    }
    data object ProjectMilestones : Route("projects/{projectId}/milestones") {
        const val ARG = "projectId"
        fun build(id: String) = "projects/$id/milestones"
    }
    data object ProjectFiles : Route("projects/{projectId}/files") {
        const val ARG = "projectId"
        fun build(id: String) = "projects/$id/files"
    }
    data object ProjectDiscussions : Route("projects/{projectId}/discussions") {
        const val ARG = "projectId"
        fun build(id: String) = "projects/$id/discussions"
    }
    data object ProjectDiscussionThread : Route("projects/{projectId}/discussions/{discussionId}") {
        const val ARG = "projectId"
        const val ARG_DISC = "discussionId"
        fun build(projectId: String, discussionId: String) = "projects/$projectId/discussions/$discussionId"
    }
    data object ProjectActivity : Route("projects/{projectId}/activity") {
        const val ARG = "projectId"
        fun build(id: String) = "projects/$id/activity"
    }
    data object TaskDetail     : Route("tasks/{taskId}?canComment={canComment}") {
        const val ARG = "taskId"
        const val ARG_CAN_COMMENT = "canComment"
        fun build(id: String, canComment: Boolean = true) = "tasks/$id?canComment=$canComment"
    }

    /**
     * Inline PDF viewer for the AHC source agreement document. Reached from
     * the DetailAHC top-bar "view document" action. Renders pages via the
     * built-in PdfRenderer — no PDF dependency added.
     */
    data object AhcDocumentViewer : Route("consultations/{consultationId}/ahc-document") {
        const val ARG = "consultationId"
        fun build(consultationId: String) = "consultations/$consultationId/ahc-document"
    }

    /**
     * Inline viewer for a document a client uploaded against a doc-request row.
     * Streams the file from the doc-request file endpoint and renders it in-app
     * (PDF via PdfRenderer, images decoded directly) — no external app handoff.
     */
    data object DocFileViewer : Route("consultations/doc-file/{rid}?name={name}") {
        const val ARG = "rid"
        const val ARG_NAME = "name"
        fun build(rid: String, name: String? = null): String =
            "consultations/doc-file/$rid?name=" +
                android.net.Uri.encode(name ?: "")
    }

    /**
     * Inline viewer for a case document. kind = "order" (id = order id, streams
     * the certified copy) or "file" (id = file id, streams a shared case file).
     */
    data object CaseDocViewer : Route("matters/doc/{kind}/{id}?name={name}") {
        const val ARG_KIND = "kind"
        const val ARG_ID = "id"
        const val ARG_NAME = "name"
        fun build(kind: String, id: String, name: String? = null): String =
            "matters/doc/$kind/$id?name=" +
                android.net.Uri.encode(name ?: "")
    }

    /**
     * Immersive pre-call lobby for a Digital Office video session. A focused
     * full-screen takeover (dark ink surface, no app chrome) that holds the
     * client between "paid & approved" and the Google Meet handoff. Reached
     * from the digital_office consultation detail.
     */
    data object VideoLobby : Route("consultations/{consultationId}/video-lobby") {
        const val ARG = "consultationId"
        fun build(consultationId: String) = "consultations/$consultationId/video-lobby"
    }

    /**
     * Digital Office walk-in intake — single screen that handles
     * start-session + submit-intake atomically. Optional advocateStaffId
     * pre-selects a specific advocate from the chamber roster; null falls
     * back to "first available counsel" (same semantics as the Hero card).
     */
    data object DoIntake : Route("digital_office/intake?advocate={advocateStaffId}") {
        const val ARG = "advocateStaffId"
        fun build(advocateStaffId: Int? = null): String =
            advocateStaffId?.let { "digital_office/intake?advocate=$it" }
                ?: "digital_office/intake?advocate="
    }

    /**
     * Digital Office landing — the calm "advocate profile" page reached
     * from the home chamber card portrait/header tap. CTA hands off to
     * DoIntake with the same advocateStaffId pre-selected.
     */
    data object DoLanding : Route("digital_office/landing?advocate={advocateStaffId}") {
        const val ARG = "advocateStaffId"
        fun build(advocateStaffId: Int? = null): String =
            advocateStaffId?.let { "digital_office/landing?advocate=$it" }
                ?: "digital_office/landing?advocate="
    }
}

// ── App-wide chrome (header + footer) visibility ─────────────────────────────
//
// Chrome is owned by the top-level Scaffold in MainActivity; these rules decide,
// per current route, whether to show the centered-logo header, a back arrow, and
// the bottom-nav footer. Routes with optional query args ("a/b?x={x}") are
// matched on their base path, so the placeholder query never breaks a lookup.
//
// Three tiers:
//   • immersive — full-screen viewers/takeovers with their own toolbars: NO app
//                 header (avoids a double bar) and NO footer.
//   • wizard    — focused multi-step forms: they sit inside the app shell and
//                 SHOW the app header, but the footer stays hidden so a stray
//                 bottom-nav tap can't abandon an in-progress form.
//   • everything else (tabs, lists, detail screens, landing pages) — header
//                 AND footer, the standard app shell.

/** Auth-flow routes — never show the nav footer. */
private val authBases = setOf(
    Route.Splash.path, Route.Welcome.path, Route.Login.path, Route.Register.path,
)

/**
 * Immersive full-screen takeovers — PDF/image document viewers, the video-call
 * lobby, and the onboarding handoff. They carry their own functional toolbars
 * (page count, download, step back), so BOTH the app header and the footer are
 * hidden here.
 */
private val immersiveBases = setOf(
    Route.AhcDocumentViewer.path,                      // consultations/{id}/ahc-document
    Route.DocFileViewer.path.substringBefore("?"),     // consultations/doc-file/{rid}
    Route.NoticeDocViewer.path.substringBefore("?"),   // inline doc viewer (own dark chrome)
    Route.CaseDocViewer.path.substringBefore("?"),     // matters/doc/{kind}/{id} (own toolbar)
    Route.VideoLobby.path,                              // consultations/{id}/video-lobby
    Route.OnboardingAhc.path,                           // onboarding handoff
    // Branded full-bleed advocate profile: dark slate hero with its own
    // back/share controls and a Walk-in CTA bar — the light app header would
    // clash with the hero, so it keeps its own chrome.
    Route.DoLanding.path.substringBefore("?"),          // digital_office/landing
    // Consult: the Legum Lex desk keeps its own dark head, as the site's drawer does.
    Route.NewConsultation.path.substringBefore("?"),   // consultations/new
)

/**
 * Focused multi-step forms. They now live inside the app shell and SHOW the
 * app-wide header for brand consistency, but the footer stays hidden so a nav
 * tap can't derail a half-finished form.
 */
private val wizardBases = setOf(
    Route.AhcFunnel.path,                               // healthchecks/funnel
    Route.Vault.path,                                   // multi-step document wizard
    Route.NewNotice.path.substringBefore("?"),         // notices/new
    Route.DoIntake.path.substringBefore("?"),          // digital_office/intake
)

/** Immersive takeovers + splash/welcome/notifications get NO centered-logo header. */
private val headerHiddenBases = setOf(
    Route.Splash.path,
    Route.Welcome.path,
    Route.Login.path,           // nothing to notify of before sign-in
    Route.Register.path,
    Route.Notifications.path,   // draws its own header (back · title · mark all read)
) + immersiveBases

/** Auth + immersive + wizards hide the footer. Everything else — tabs, lists,
 *  detail screens, the Notices hub/detail, the Digital Office landing — shows it. */
private val footerHiddenBases = authBases + immersiveBases + wizardBases

private fun routeBase(route: String?): String? = route?.substringBefore("?")

/** Centered-logo header shows everywhere except the immersive splash/welcome. */
fun showAppHeader(route: String?): Boolean {
    val b = routeBase(route) ?: return false
    return b !in headerHiddenBases
}

/** Back arrow shows on any non-tab screen that has somewhere to return to. */
fun showHeaderBack(route: String?, canPop: Boolean): Boolean {
    val b = routeBase(route) ?: return false
    return canPop && bottomTabs.none { it.route.path == b }
}

/** Footer nav shows on tabs + content screens, never on auth/funnels/modals. */
fun showAppFooter(route: String?): Boolean {
    val b = routeBase(route) ?: return false
    return b !in footerHiddenBases
}

data class BottomTab(
    val route: Route,
    val labelRes: Int,
    val iconInactive: ImageVector,
    val iconActive: ImageVector,
)

// Four tabs. The workspaces (Litigation, Projects, Registry) are entered from
// Home's tiles and from More, not repeated here; the Digital Office has a tab
// of its own because it is the firm's own room, not a list.
val bottomTabs = listOf(
    BottomTab(Route.Home,          R.string.nav_home,          Icons.Outlined.GridView,  Icons.Rounded.GridView),
    BottomTab(Route.Consultations, R.string.nav_consultations, Icons.Outlined.Forum,     Icons.Rounded.Forum),
    BottomTab(Route.DigitalOffice, R.string.nav_office,        Icons.Outlined.Videocam,  Icons.Rounded.Videocam),
    BottomTab(Route.More,          R.string.nav_more,          Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)
