package com.legumlex.client.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013: Sign In and Join. */
class SignInLayoutTest {
    @Test fun discoverFollowsTheServersSignUpSwitch() {
        assertTrue(discoverShown(true))
        assertFalse(discoverShown(false))
    }

    @Test fun joinIsReachableOnlyWhenSignUpIsOn() {
        assertTrue(joinReachable(true))
        assertFalse(joinReachable(false))
        // Not answered yet: nothing is offered.
        assertFalse(joinReachable(null))
    }

    @Test fun theJoinChoicesRouteToTheirOwnAction() {
        var account = 0; var counsel = 0
        runJoin(JoinChoice.Client, { account++ }, { counsel++ })
        assertEquals(1 to 0, account to counsel)
        runJoin(JoinChoice.Advocate, { account++ }, { counsel++ })
        assertEquals(1 to 1, account to counsel)
    }

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

    @Test fun onboardNowKeepsItsLine() {
        assertEquals("Your PAN, Aadhaar and mobile number. The firm approves every new account.", ONBOARD_NOW_LINE)
    }
}
