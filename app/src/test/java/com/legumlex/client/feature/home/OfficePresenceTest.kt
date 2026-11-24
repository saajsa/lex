package com.legumlex.client.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-048: the Digital Office button's state from the home answer, and its reduced-motion form. */
class OfficePresenceTest {
    private fun chamber(online: Int) = ChamberState(isOpen = online > 0, onlineCount = online, sessionFee = 0.0, currency = "INR", hours = "", location = "",
        sessionCapMinutes = 0, advocates = emptyList())

    @Test fun onlineNoneUnknown() {
        assertEquals(OfficePresence.Live, officePresence(chamber(1)))
        assertEquals(OfficePresence.Live, officePresence(chamber(3)))
        assertEquals(OfficePresence.Dead, officePresence(chamber(0)))
        assertEquals(OfficePresence.Unknown, officePresence(null))
    }

    @Test fun liveIsGreenAndPulsesUnlessMotionIsReduced() {
        val moving = officeLook(OfficePresence.Live, reducedMotion = false)
        assertTrue(moving.green && moving.pulse && moving.opens)
        val still = officeLook(OfficePresence.Live, reducedMotion = true)
        assertTrue(still.green && !still.pulse && still.opens)   // still filled green, without the ring
    }

    @Test fun deadIsMutedAndExplainsInsteadOfOpening() {
        for (reduced in listOf(false, true)) {
            val d = officeLook(OfficePresence.Dead, reduced)
            assertFalse(d.green || d.pulse || d.opens)
        }
        assertEquals("No advocate is in the Digital Office just now.", OFFICE_DEAD_LINE)
        val u = officeLook(OfficePresence.Unknown, false)
        assertTrue(u.opens && !u.green && !u.pulse)   // not known: no claim, it opens as before
    }

    @Test fun liveIsAFilledGreenCircleWithAWhiteIcon() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/home/OfficePresence.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(src.contains("look.green -> androidx.compose.ui.graphics.Color.White") && src.contains("if (look.green) Modifier.background(green)"))
    }

    @Test fun theHeaderCarriesSignOutWhereTheOfficeWas() {
        val main = listOf("src/main/java/com/legumlex/client/MainActivity.kt").map { java.io.File(it) }.firstOrNull { it.isFile }?.readText() ?: return
        assertTrue(main.contains("onOffice = null,") && main.contains("onSignOut = if (!lead) ({ confirmSignOut = true }) else null,") && main.contains("signOutBesideBell = !lead && !counsel,"))
        val header = java.io.File("src/main/java/com/legumlex/client/ui/navigation/AppHeader.kt").readText()
        assertTrue(header.contains("if (onSignOut != null && signOutBesideBell) SignOutButton(onSignOut)") && header.contains("if (onSignOut != null && !signOutBesideBell) SignOutButton(onSignOut)"))
    }
}
