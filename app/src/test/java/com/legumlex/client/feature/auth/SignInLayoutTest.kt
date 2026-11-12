package com.legumlex.client.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013: Sign In and Join. */
class SignInLayoutTest {
    @Test fun noDebugChipInARelease() {
        // Whatever is configured, a release build offers nothing.
        assertTrue(debugSignIns(false, "a@b.c", "pw", "adv@b.c", "empty@b.c").isEmpty())
    }

    @Test fun theDebugChipOffersOnlyWhatIsConfigured() {
        assertEquals(listOf(DebugSignIn.TestContact, DebugSignIn.CounselWithCases, DebugSignIn.CounselEmpty), debugSignIns(true, "a@b.c", "pw", "adv@b.c", "empty@b.c"))
        // Counsel accounts need the password.
        assertEquals(listOf(DebugSignIn.TestContact), debugSignIns(true, "a@b.c", "", "adv@b.c", "empty@b.c"))
        assertTrue(debugSignIns(true, "", "pw", "", "").isEmpty())
    }

    @Test fun theNumberIsReadAsTheOldViewReadIt() {
        assertEquals("9876543210", localTenDigits("+91 98765 43210"))
        assertEquals("9876543210", localTenDigits("09876543210"))
        assertEquals("98765", localTenDigits("98765"))
        assertTrue(numberReady(false, "9876543210")); assertFalse(numberReady(false, "98765"))
        assertTrue(numberReady(true, "+44 7700 900123")); assertFalse(numberReady(true, "+44 77"))
    }


    @Test fun theHintIsShort() {
        assertEquals("Code comes on WhatsApp/SMS.", MOBILE_HINT)
    }

    @Test fun theTwoButtonsDoTheirOwnThing() {
        val hit = mutableListOf<String>()
        SignInAction.values().forEach { runSignInAction(it, { hit += "email" }, { hit += "google" }) }
        assertEquals(listOf("email", "google"), hit)
        assertEquals(listOf("Sign In With Email", "Sign In With Google"), SignInAction.values().map { it.label })
    }

    @Test fun noEmailLinkIsLeftInTheCard() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile }?.readText()
        // The unit-test working directory is the module; when the source cannot be found the check is skipped.
        if (src != null) assertFalse(src.contains("Use Email And Password"))
    }

    private fun source(name: String) = java.io.File("src/main/java/com/legumlex/client/$name").takeIf { it.isFile }?.readText()

    @Test fun noJoinScreenOrNewHereCardIsLeft() {
        // The unit-test working directory is the module; when a source cannot be found its check is skipped.
        source("feature/auth/LoginScreen.kt")?.let { src ->
            assertFalse(src.contains("New Here")); assertFalse(src.contains("Discover")); assertFalse(src.contains("onJoin"))
        }
        assertEquals(null, source("feature/auth/JoinScreen.kt"))
        source("ui/navigation/Routes.kt")?.let { assertFalse(it.contains("Route(\"join\")")) }
        source("ui/navigation/NavGraph.kt")?.let { assertFalse(it.contains("JoinScreen")) }
    }

    @Test fun theEdgeButtonsAreEmailGoogleLeftToRight() {
        assertEquals(listOf(SignInAction.Email, SignInAction.Google), edgeActions(codeStep = false, emailView = false))
        // In the Email view the envelope goes: only Google is left.
        assertEquals(listOf(SignInAction.Google), edgeActions(codeStep = false, emailView = true))
        // In the code step none show.
        assertTrue(edgeActions(codeStep = true, emailView = false).isEmpty())
        assertTrue(edgeActions(codeStep = true, emailView = true).isEmpty())
    }

    @Test fun theOrbIsCentredBelowTheDebugChipAndTheTopRowHasOnlyTheMonogram() {
        source("feature/auth/LoginScreen.kt")?.let { src ->
            assertTrue(src.contains("LexOrb(enabled = !isLoading, onOpen = { chat = true }, compact = true, scale = 1.7f"))
            // Between two spacers (centred in the empty space), after the Debug chip and before the date.
            val foot = src.substringAfter("DebugChip(").substringBefore("DateAndMaxim(Modifier.padding")
            assertTrue(foot.contains("contentAlignment = Alignment.Center") && foot.indexOf("LexOrb(") > foot.indexOf("Spacer(Modifier.height(ORB_GAP.dp))"))
            // While the arc is open the caret stays on (a blink off looked like a missing underscore).
            assertTrue(src.contains("alpha = if (menu || !motion.idle) 1f else blink"))
            assertFalse(src.contains("Modifier.align(Alignment.BottomEnd).navigationBarsPadding()"))
            val top = src.substringAfter("// Our monogram").substringBefore("Spacer(Modifier.height(spacing.s5))")
            assertFalse(top.contains("LexOrb"))
        }
    }

    @Test fun theChatOpensAndClosesInTheBottomHalf() {
        // Open and close: Back and the close button both clear the one flag; the orb is shown only while it is clear.
        source("feature/auth/LoginScreen.kt")?.let { src ->
            assertTrue(src.contains("BackHandler(enabled = chat) { chat = false }"))
            assertTrue(src.contains("onClose = { chat = false }"))
            assertTrue(src.contains("visible = !chat"))
        }
        // Half the screen, never under 320 dp.
        // From 12 dp under the squares to the screen's bottom, never under 320 dp.
        assertEquals(800f - (430f + 12f), chatPanelHeight(800f, 430f), 0.01f)
        assertEquals(320f, chatPanelHeight(700f, 430f), 0.01f)
        assertEquals(400f, chatPanelHeight(800f, null), 0.01f)
        assertEquals(300, CHAT_SLIDE_MS)
    }

    @Test fun theChatIsAnchoredAndTakesTheKeyboardInset() {
        source("feature/auth/LoginScreen.kt")?.let { src ->
            val panel = src.substringAfter("com.legumlex.client.feature.desk.LexChatPanel(").substringBefore("// Lex Intelligence, the orb")
            assertTrue(panel.contains("WindowInsets.ime"))
            assertTrue(src.substringBefore("com.legumlex.client.feature.desk.LexChatPanel(").takeLast(400).contains("Modifier.align(Alignment.BottomCenter)"))
            assertTrue(src.contains("if (still) EnterTransition.None"))
        }
    }

    @Test fun theChatPanelNeverTakesTheFocusItself() {
        source("feature/desk/DeskScreen.kt")?.let { src ->
            assertTrue(src.contains("LocalDeskAutoFocus provides false"))
            assertTrue(src.contains("if (auto) runCatching { focus.requestFocus() }"))
        }
    }

    @Test fun theChatPanelUsesTheDesksOwnPiecesNotASecondChat() {
        val panel = source("feature/desk/DeskScreen.kt")
        val login = source("feature/auth/LoginScreen.kt")
        if (panel != null && login != null) {
            // The panel and the full screen draw the same composables over the same view model.
            val screen = panel.substringAfter("fun DeskScreen(").substringBefore("private fun mimesFor")
            val chat = panel.substringAfter("fun LexChatPanel(")
            for (piece in listOf("DeskIntro(", "DeskOpening(", "DeskThread(")) { assertTrue(screen.contains(piece)); assertTrue(chat.contains(piece)) }
            assertTrue(chat.contains("viewModel()") && screen.contains("DeskHandover.take()"))
            assertFalse(login.contains("DeskRepository"))
        }
    }

    @Test fun noTooltipOrTypewriterIsLeft() {
        source("feature/auth/LoginScreen.kt")?.let { src ->
            assertFalse(src.contains("OrbTooltip")); assertFalse(src.contains("typewriter")); assertFalse(src.contains("How can I help you"))
        }
        assertEquals(null, source("feature/auth/OrbTypewriter.kt"))
    }
}
