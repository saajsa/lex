package com.legumlex.client.feature.auth

import com.legumlex.client.feature.desk.KeyValueStore
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013: the first-time hint beside the Lex button. */
class OrbHintTest {
    private class MapStore(val m: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
    }

    @Test fun aFirstTimeUserSeesIt() {
        val h = OrbHint(MapStore())
        assertTrue(h.visit())
        assertTrue(h.shouldShow())           // a rotation does not count a visit
        assertTrue(h.shouldShow())
    }

    @Test fun aTouchDismissesItForGoodAndItSurvivesARestart() {
        val store = MapStore()
        val h = OrbHint(store)
        assertTrue(h.visit())
        h.dismiss()                          // a tap or a long press on the button, or a tap on the hint
        assertFalse(h.shouldShow())
        assertFalse(OrbHint(store).visit())  // a new process, a new visit: still gone
        assertFalse(OrbHint(store).shouldShow())
    }

    @Test fun itIsGoneAfterThreeVisitsWithoutATouch() {
        val store = MapStore()
        assertTrue(OrbHint(store).visit()); assertTrue(OrbHint(store).visit()); assertTrue(OrbHint(store).visit())
        assertEquals(3, HINT_MAX_VISITS)
        assertFalse(OrbHint(store).visit())          // the fourth
        assertFalse(OrbHint(store).shouldShow())
    }

    @Test fun resetBringsItBack() {
        val store = MapStore(); val h = OrbHint(store)
        repeat(4) { h.visit() }; h.dismiss()
        assertFalse(h.shouldShow())
        h.reset()
        assertTrue(h.visit())
    }

    @Test fun theSecondLineOnlyWhenThereIsAnArc() {
        assertEquals(listOf("How can I help you?", "Press and hold for more"), hintLines(true))
        assertEquals(listOf("How can I help you?"), hintLines(false))
    }

    @Test fun theTypewriterWritesLineByLine() {
        val lines = hintLines(true)
        assertEquals(listOf("", ""), hintTyped(lines, 0))
        assertEquals(listOf("How", ""), hintTyped(lines, 3))
        assertEquals(listOf("How can I help you?", ""), hintTyped(lines, HINT_FIRST.length))
        assertEquals(listOf("How can I help you?", "Press"), hintTyped(lines, HINT_FIRST.length + 5))
        assertEquals(lines, hintTyped(lines, lines.sumOf { it.length }))       // reduced motion: all at once
        assertEquals(lines, hintTyped(lines, 999))
    }

    @Test fun itIsWiredAsAskedInTheScreen() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile }?.readText() ?: return
        // Dismissed on the button's first touch and on a tap of the hint; hidden while the arc or the chat is open.
        assertTrue(src.contains("onFirstTouch = { hint.dismiss(); hintOn = false }"))
        assertTrue(src.contains("onTap = { hint.dismiss(); hintOn = false }"))
        assertTrue(src.contains("if (hintOn && !chat && !arcOpen) OrbHintBubble("))
        // Above the button: in the band over the orb's box (Alignment.BottomCenter in a box ORB_GAP high), before the orb, not beside it.
        val zone = src.substringAfter("The first-time hint sits in the band above the button")
        assertTrue(zone.indexOf("Modifier.fillMaxWidth().height(ORB_GAP.dp), contentAlignment = Alignment.BottomCenter") in 0..260)
        assertTrue(zone.indexOf("OrbHintBubble(") < zone.indexOf("LexOrb(enabled"))
        assertFalse(src.contains("Modifier.align(Alignment.CenterEnd),\n                        )"))
        assertFalse(src.contains("widthIn(max = 118.dp)"))
        // Both lines for TalkBack, politely; reduced motion shows everything at once; the accent pairing (no fixed colours).
        val bubble = src.substringAfter("private fun OrbHintBubble(")
        assertTrue(bubble.contains("LiveRegionMode.Polite") && bubble.contains("lines.joinToString(\". \")"))
        assertTrue(bubble.contains("if (reduced) total else 0") && bubble.contains("DeskSurface.Accent"))
        assertTrue(src.contains("ResetHint(\"Reset Lex Hint\")"))
    }

    @Test fun theSecondLineKeepsTheInkRuleAtReducedStrength() {
        assertTrue(HINT_SECOND_ALPHA >= 0.8f)
        for ((name, c) in listOf("light" to com.legumlex.client.ui.theme.LightLexColors, "dark" to com.legumlex.client.ui.theme.DarkLexColors)) {
            val fill = com.legumlex.client.feature.desk.deskFill(c, com.legumlex.client.feature.desk.DeskSurface.Accent)
            val ink = com.legumlex.client.feature.desk.deskInk(c, com.legumlex.client.feature.desk.DeskSurface.Accent, com.legumlex.client.feature.desk.DeskRole.Body)
            val second = ink.copy(alpha = HINT_SECOND_ALPHA).compositeOver(fill)
            assertTrue("$name first line", com.legumlex.client.feature.desk.contrastRatio(ink, fill) >= 4.5)
            assertTrue("$name second line ${com.legumlex.client.feature.desk.contrastRatio(second, fill)}", com.legumlex.client.feature.desk.contrastRatio(second, fill) >= 4.5)
        }
        // The band over the button is deep enough for the bubble (two lines and a pointer) and clear of the badge.
        assertTrue(ORB_GAP > 8f + 15f + 14f + 16f + HINT_POINTER + HINT_LIFT)
    }
}
