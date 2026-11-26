package com.legumlex.client.feature.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** REQ-055: the demo build (target, version), the banner, Send Feedback's request and the error reports, on the recorded contract. */
class DemoBuildTest {
    @Test fun theReleaseTargetsLiveUnlessToldDemo() {
        val f = java.io.File("build.gradle.kts").takeIf { it.isFile } ?: return
        val g = f.readText()
        assertTrue(g.contains("versionCode = 9")); assertTrue(g.contains("versionName = \"0.5.0\""))
        assertTrue(g.contains("findProperty(\"lexTarget\")"))
        assertTrue(g.contains("?: \"live\""))
        assertTrue(g.contains("\"live\" -> \"https://www.legumlex.com/customers_api/v1/\""))
        assertTrue(g.contains("\"demo\" -> \"https://demo.legumlex.com/customers_api/v1/\""))
        assertTrue(g.contains("else -> throw GradleException("))
    }

    @Test fun theBannerSaysTheServersLineOrTheFallback() {
        assertNull(demoBanner(false, "Demo."))
        assertEquals("Demo. Nothing here is real.", demoBanner(true, null))
        assertEquals("Demo. Nothing here is real.", demoBanner(true, "  "))
        assertEquals("A demo for testers.", demoBanner(true, " A demo for testers. "))
        DemoSession.set(true, null); assertTrue(DemoSession.isDemo)
        DemoSession.set(false, "x"); assertFalse(DemoSession.isDemo)
    }

    @Test fun feedbackIsSentInTheContractsShape() {
        assertFalse(feedbackReady("   ")); assertTrue(feedbackReady(" ok ")); assertFalse(feedbackReady("x".repeat(4001))); assertTrue(feedbackReady("x".repeat(4000)))
        val f = feedbackFields("  It froze.  ", "consultation_detail", "Android 15", "Nothing A065")
        assertEquals(listOf("message", "screen", "app_version", "version_code", "os", "device"), f.map { it.first })
        assertEquals("It froze.", f[0].second)
        assertEquals("0.5.0", f.toMap()["app_version"]); assertEquals("9", f.toMap()["version_code"])
        assertEquals(1600 to 800, shotSize(3200, 1600)); assertEquals(900 to 1600, shotSize(1080, 1920))
        assertEquals(800 to 600, shotSize(800, 600))   // never enlarged
    }

    @Test fun aReportCarriesTheContractsFieldsCapped() {
        val e = IllegalStateException("x".repeat(900))
        val r = buildReport(e, "main", "desk", "2026-11-27T10:15:02Z", "id-1")
        assertEquals(setOf("id", "at", "app_version", "version_code", "os", "device", "screen", "thread", "exception", "message", "stack"), r.keys)
        assertEquals("java.lang.IllegalStateException", r["exception"])
        assertEquals(500, (r["message"] as String).length)
        assertTrue((r["stack"] as String).length <= 16 * 1024)
        assertEquals(9, r["version_code"]); assertEquals("desk", r["screen"]); assertEquals("main", r["thread"])
    }

    @Test fun reportsAreKeptTenAndAWeek() {
        val dir = Files.createTempDirectory("errs").toFile()
        val now = System.currentTimeMillis()
        repeat(12) { i ->
            save(dir, buildReport(RuntimeException("e$i"), "main", "", "t", "id-$i"))
            java.io.File(dir, "id-$i.json").setLastModified(now - (12 - i) * 1000L)
        }
        assertEquals(10, dir.listFiles()!!.size)   // the oldest two went at once
        java.io.File(dir, "id-11.json").setLastModified(now - 8L * 24 * 60 * 60 * 1000)   // a week and a day old
        val p = pending(dir, now)
        assertEquals(9, p.size); assertFalse(java.io.File(dir, "id-11.json").exists())
        assertEquals("id-10", p.first().nameWithoutExtension)   // newest first
        val back = readReport(p.first())!!
        assertEquals(9, back["version_code"]); assertEquals("e10", back["message"])
    }
}

/** REQ-055: the demo's sign-in screen is email and password only. */
class DemoSignInTest {
    @Test fun theDemoHostOrBuildOpensOnEmailOnly() {
        val demoHost = com.legumlex.client.feature.auth.deskSwitches(mapOf("client_id" to "x", "demo" to true))
        assertTrue(demoHost.demo)
        assertFalse(com.legumlex.client.feature.auth.deskSwitches(mapOf("client_id" to "x")).demo)
        assertTrue(com.legumlex.client.feature.auth.demoSignIn(demoHost, "live"))
        assertTrue(com.legumlex.client.feature.auth.demoSignIn(com.legumlex.client.feature.auth.DeskSwitches(), "demo"))
        assertFalse(com.legumlex.client.feature.auth.demoSignIn(com.legumlex.client.feature.auth.DeskSwitches(), "live"))
        assertFalse(com.legumlex.client.feature.auth.demoSignIn(com.legumlex.client.feature.auth.DeskSwitches(), "debug"))
        // No Email or Google buttons on the demo's card, in either view.
        assertTrue(com.legumlex.client.feature.auth.edgeActions(codeStep = false, emailView = true, demo = true).isEmpty())
        assertTrue(com.legumlex.client.feature.auth.edgeActions(codeStep = false, emailView = false, demo = true).isEmpty())
        assertEquals(2, com.legumlex.client.feature.auth.edgeActions(codeStep = false, emailView = false).size)
    }

    @Test fun theScreenHidesTheMobileFormGoogleAndTheOrb() {
        val f = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile } ?: return
        val s = f.readText()
        assertTrue(s.contains("LaunchedEffect(demo) { if (demo) vm.show(LoginView.Email) }"))
        assertTrue(s.contains("onBack = if (demo) null else"))
        assertTrue(s.contains("if (!demo) Box(Modifier.fillMaxWidth()) {\n                AiTexture("))   // the orb, its hint and texture
        assertTrue(s.contains("BackHandler(enabled = !demo &&"))
    }
}

/** REQ-055: the demo's profile says why codes cannot be sent, in the server's words. */
class DemoMobileNoteTest {
    @Test fun theServersNoteReplacesTheAppsLine() {
        val demo = com.legumlex.client.feature.profile.parseMobile(mapOf("available" to false, "unavailable_note" to " Codes are not sent in the demo. "))
        assertEquals("Codes are not sent in the demo.", com.legumlex.client.feature.profile.mobileUnavailableLine(demo))
        val live = com.legumlex.client.feature.profile.parseMobile(mapOf("available" to false))
        assertEquals("Verification codes cannot be sent at present. Please try again later.", com.legumlex.client.feature.profile.mobileUnavailableLine(live))
        assertNull(com.legumlex.client.feature.profile.parseMobile(mapOf("unavailable_note" to "  ")).unavailableNote)
    }
}
