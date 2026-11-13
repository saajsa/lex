package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.30 (prepared): the send-to-advocate animation plays once for about a second, is static with animations off, and uses only theme colours. */
class DeskAdvocateTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun animationsOffMeansTheFinalStateAtOnce() {
        assertFalse(animationsOn(0f)); assertTrue(animationsOn(1f)); assertTrue(animationsOn(0.5f))
        assertEquals(1_000, SEND_ANIM_MS)
    }

    @Test fun theTickShowsOnlyAtTheEnd() {
        assertEquals(0f, sendProgress(-1f)); assertEquals(0.4f, sendProgress(0.4f)); assertEquals(1f, sendProgress(3f))
        assertFalse(sendDone(0.99f)); assertTrue(sendDone(1f))
    }

    @Test fun theComposablesAreFlatThemedAndPlayOnce() {
        val s = src("feature/desk/DeskScreen.kt") ?: return
        val a = s.substringAfter("internal fun SendToAdvocate(").substringBefore("internal fun AdvocateWaiting(")
        assertTrue(a.contains("ANIMATOR_DURATION_SCALE") && a.contains("animationsOn(") && a.contains("tween(SEND_ANIM_MS"))
        assertTrue(a.contains("rememberSaveable") && a.contains("played = true"))       // once
        assertFalse(a.contains("Color(0x") || a.contains("Color.White") || a.contains("Color.Black"))
        assertFalse(a.contains("shadow(") || a.contains("Brush."))                         // flat
        assertTrue(a.contains("RoundedCornerShape(8.dp)"))                                 // Mylex radii
        // Compact and left-aligned; the advocate glyph stays with a tick badge; the words come from the caller; it is a paragraph of Lex's message, never a dock.
        assertTrue(a.contains("fillMaxWidth(SEND_TRACK_FRACTION)") && a.contains("Icons.Outlined.Gavel") && a.contains("Alignment.TopEnd") && a.contains("caption"))
        assertFalse(Regex("Text\\(\"[A-Za-z]").containsMatchIn(a))                         // no hard-coded English in the composable
        assertTrue(s.contains("is Para.Sent -> SendToAdvocate(p.caption, p.time)"))
        assertFalse(s.contains("Dock.Sent"))   // the animation is a paragraph of Lex's message; Dock.Advocate is only the waiting card
        assertEquals(0.4f, SEND_TRACK_FRACTION)
        val w = s.substringAfter("internal fun AdvocateWaiting(").substringBefore("// The desk playbook")
        assertFalse(w.contains("Color(0x") || w.contains("Color.White") || w.contains("Color.Black"))
        assertTrue(w.contains("RoundedCornerShape(12.dp)") && w.contains("DeskRole.Secondary"))
        // The debug sample shows them, to look at in light and dark.
        assertTrue(s.contains("\"Sending\"") && s.contains("\"Waiting\"") && s.contains("\"Handed Over\""))
    }
}
