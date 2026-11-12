package com.legumlex.client.feature.desk

import androidx.compose.ui.graphics.Color
import com.legumlex.client.ui.theme.DarkLexColors
import com.legumlex.client.ui.theme.LightLexColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.15: every desk text colour comes from the surface it sits on, in both themes. */
class DeskInkTest {
    private val themes = listOf("light" to LightLexColors, "dark" to DarkLexColors)

    @Test fun textIsReadableOnEverySurfaceInBothThemes() {
        for ((name, c) in themes) for (surface in DeskSurface.values()) {
            val fill = deskFill(c, surface)
            for (role in listOf(DeskRole.Body, DeskRole.Strong)) {
                val ratio = contrastRatio(deskInk(c, surface, role), fill)
                assertTrue("$name $surface $role contrast $ratio", ratio >= 4.5)
            }
        }
        // Muted captions (not body copy) and the error line stay legible too, on the surfaces they are used on.
        for ((name, c) in themes) {
            for (surface in listOf(DeskSurface.Page, DeskSurface.Raised, DeskSurface.Bubble, DeskSurface.Note)) {
                assertTrue("$name $surface muted", contrastRatio(deskInk(c, surface, DeskRole.Muted), deskFill(c, surface)) >= 3.0)
            }
            assertTrue("$name error on page", contrastRatio(deskInk(c, DeskSurface.Page, DeskRole.Error), deskFill(c, DeskSurface.Page)) >= 3.0)
        }
    }

    @Test fun boldAndTheHighlightedQuoteAreNeverAFixedWhite() {
        for ((name, c) in themes) {
            for (surface in listOf(DeskSurface.Page, DeskSurface.Raised, DeskSurface.Bubble, DeskSurface.Note, DeskSurface.Warn)) {
                val strong = deskInk(c, surface, DeskRole.Strong)
                // The same ink as the body, set heavier: not a colour of its own.
                assertEquals("$name $surface", deskInk(c, surface, DeskRole.Body), strong)
                assertNotEquals("$name $surface is white", Color.White, strong)
            }
        }
        // The reported fault: white bold on the light page.
        assertTrue(contrastRatio(Color.White, deskFill(LightLexColors, DeskSurface.Page)) < 4.5)
        assertTrue(contrastRatio(deskInk(LightLexColors, DeskSurface.Page, DeskRole.Strong), deskFill(LightLexColors, DeskSurface.Page)) >= 4.5)
    }

    @Test fun theColourFollowsTheSurfaceNotTheTheme() {
        for ((_, c) in themes) {
            // On the accent fill the accent's own foreground; on every neutral surface the foreground.
            assertEquals(c.primaryForeground, deskInk(c, DeskSurface.Accent, DeskRole.Strong))
            for (s in listOf(DeskSurface.Page, DeskSurface.Raised, DeskSurface.Bubble, DeskSurface.Note)) assertEquals(c.foreground, deskInk(c, s, DeskRole.Body))
        }
        // Light and dark differ, so a colour fixed for one is wrong for the other.
        assertNotEquals(deskInk(LightLexColors, DeskSurface.Page, DeskRole.Body), deskInk(DarkLexColors, DeskSurface.Page, DeskRole.Body))
    }

    @Test fun theDeskSourceHasNoFixedWhiteOrBlackText() {
        val dir = java.io.File("src/main/java/com/legumlex/client/feature/desk")
        if (!dir.isDirectory) return
        for (f in dir.listFiles { x -> x.name.endsWith(".kt") }.orEmpty()) {
            if (f.name == "DeskInk.kt") continue
            val src = f.readText()
            assertFalse("${f.name} has Color.White", Regex("""Color\.White""").containsMatchIn(src))
            assertFalse("${f.name} has Color.Black", Regex("""Color\.Black""").containsMatchIn(src))
            assertFalse("${f.name} has a literal colour", Regex("""Color\(0x""").containsMatchIn(src))
        }
        val screen = java.io.File(dir, "DeskScreen.kt").readText()
        assertTrue(screen.contains("D.ink(DeskSurface.Page, DeskRole.Strong)"))
        assertTrue(screen.contains("fun DeskSampleScreen("))
    }

    @Test fun theSampleConversationIsDebugOnly() {
        val login = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(login.contains("if (BuildConfig.DEBUG) debug + DebugSignIn.DeskSample + DebugSignIn.ResetHint else debug"))
        val nav = java.io.File("src/main/java/com/legumlex/client/ui/navigation/NavGraph.kt").readText()
        assertTrue(nav.contains("if (com.legumlex.client.BuildConfig.DEBUG) com.legumlex.client.feature.desk.DeskSampleScreen("))
    }
}
