package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 UI pass: margins, button spacing, one pending-or-error line, the foot of the conversation. */
class DeskLayoutTest {
    private val screen: String? = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskScreen.kt").takeIf { it.isFile }?.readText()

    @Test fun theRulesAreTheAskedNumbers() {
        assertEquals(16f, DESK_SIDE_MARGIN, 0f); assertEquals(8f, CHIP_GAP, 0f); assertEquals(12f, CHIP_GROUP_TOP, 0f)
    }

    @Test fun lexAndTheVisitorShareTheSameSideMargin() {
        val s = screen ?: return
        // The conversation list pads both sides by the margin: Lex's lines and the visitor's bubbles (which only keep 48 dp clear on the left) end 16 dp from the edge.
        assertTrue(s.contains("PaddingValues(start = DESK_SIDE_MARGIN.dp, end = DESK_SIDE_MARGIN.dp, top = 16.dp, bottom = DESK_BOTTOM_ROOM.dp)"))
        val me = s.substringAfter("private fun MeLine(").substringBefore("private fun NoteLine(")
        assertTrue(me.contains("Modifier.padding(start = 48.dp)") && !me.contains("padding(end"))
        // Their docks and the opening box use it too, so the buttons and the composer line up with the text.
        assertTrue(s.contains("Box(Modifier.padding(horizontal = DESK_SIDE_MARGIN.dp, vertical = 8.dp)) {\n            if (fullDesk != null"))
        assertTrue(s.substringAfter("internal fun DeskOpening(").take(300).contains("padding(horizontal = DESK_SIDE_MARGIN.dp)"))
    }

    @Test fun buttonsAreEightApartWrapAndSitTwelveBelowWhatIsAbove() {
        val s = screen ?: return
        val chips = s.substringAfter("private fun Chips(").substringBefore("@Composable\nprivate fun DocsDock")
        assertTrue(chips.contains("FlowRow(Modifier.padding(top = topGap)") && chips.contains("spacedBy(CHIP_GAP.dp)"))
        assertEquals(2, Regex("spacedBy\\(CHIP_GAP.dp\\)").findAll(chips).count())      // across and down
        // The actions dock and the sample ask for the 12 dp above the group.
        assertTrue(s.contains("vm.act(it.id) } }, topGap = CHIP_GROUP_TOP.dp)"))
    }

    @Test fun anErrorReplacesThePendingLineAndButtonsNeverShowWithIt() {
        val s = screen ?: return
        // One dock at a time in the real state: Waiting shows the error if there is one, else the pending line; Actions is its own dock.
        assertTrue(s.contains("is Dock.Waiting -> if (ui.error.isNotEmpty()) ErrorLine(ui.error) else WaitLine(d.label)"))
        val vm = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").readText()
        assertTrue(vm.contains("private fun dock(d: Dock) = _ui.update { it.copy(dock = d, error = \"\") }"))      // a new dock clears the old error
        // The sample shows one state at a time.
        val sample = s.substringAfter("fun DeskSampleScreen(")
        assertTrue(sample.contains("when (state) {") && sample.contains("1 -> ErrorLine(") && sample.contains("else -> WaitLine("))
    }

    @Test fun nothingInTheDeskIsPinnedToAFontSize() {
        // Font scale 1.3: the texts are sp, wrap, and the chips have a minimum height, so they grow.
        val s = screen ?: return
        assertTrue(s.substringAfter("private fun Chips(").substringBefore("@Composable\nprivate fun DocsDock").contains("heightIn(min = 44.dp)"))
    }

    @Test fun theVisitorsBubbleIsRoundOnEveryCorner() {
        val s = screen ?: return
        val me = s.substringAfter("private fun MeLine(").substringBefore("private fun NoteLine(")
        // All four corners the same (no square right side), and the clip is the bubble's own.
        assertTrue(me.contains("clip(RoundedCornerShape(14.dp)).background(D.Me)"))
        assertFalse(me.contains("RoundedCornerShape(14.dp, 14.dp"))
    }

    @Test fun theLastLineIsScrolledWhollyIntoViewWithRoomUnder() {
        val s = screen ?: return
        assertEquals(24f, DESK_BOTTOM_ROOM, 0f)
        val conv = s.substringAfter("private fun Conversation(").substringBefore("items(ui.lines.size")
        assertTrue(conv.contains("list.animateScrollToItem(n - 1)") && conv.contains("list.animateScrollBy(1_000_000f)"))
        assertTrue(conv.contains("bottom = DESK_BOTTOM_ROOM.dp"))
    }

    @Test fun indicMarksGetTheirRoomAboveAndBelowEveryLine() {
        val s = screen ?: return
        val body = s.substringAfter("private fun body(").substringBefore("private val EXAMPLES")
        assertTrue(body.contains("lineHeight = (size * 1.55f).sp"))
        assertTrue(body.contains("includeFontPadding = true") && body.contains("Trim.None"))
    }

    @Test fun theSecondaryInkPassesTheInkRuleInBothThemes() {
        val s = screen ?: return
        for ((name, c) in listOf("light" to com.legumlex.client.ui.theme.LightLexColors, "dark" to com.legumlex.client.ui.theme.DarkLexColors)) {
            for (surface in listOf(DeskSurface.Page, DeskSurface.Raised, DeskSurface.Bubble, DeskSurface.Note)) {
                val ratio = contrastRatio(deskInk(c, surface, DeskRole.Secondary), deskFill(c, surface))
                assertTrue("$name $surface secondary $ratio", ratio >= 4.5)
            }
        }
        // The muted ink on the raised card is what fell short.
        assertTrue(contrastRatio(deskInk(com.legumlex.client.ui.theme.LightLexColors, DeskSurface.Raised, DeskRole.Muted), deskFill(com.legumlex.client.ui.theme.LightLexColors, DeskSurface.Raised)) < 4.5)
    }

    @Test fun theReviewStepIsGone() {
        // REQ-017 017.20 (owner): no review from the client. No Review page, no Edit, no "Send My Own Words Instead", no brief call.
        val dir = java.io.File("src/main/java/com/legumlex/client")
        if (!dir.isDirectory) return
        val desk = java.io.File(dir, "feature/desk").listFiles { f -> f.name.endsWith(".kt") }.orEmpty().joinToString("\n") { it.readText() }
        for (gone in listOf("ReviewDock", "ReviewPage", "Dock.Review", "Send My Own Words Instead", "Send To The Firm", "makeBrief", "DeskBrief", "BriefCache", "REVIEW_LINE")) assertFalse(gone, desk.contains(gone))
        val api = java.io.File(dir, "data/api/ApiService.kt").readText()
        assertFalse(api.contains("desk/brief") || api.contains("deskBrief") || api.contains("leadDeskBrief"))
        // The enquiry carries no brief or token.
        val f = enquiryFields("text", "individual", "9", null, "", "reading", null, "key", "", "").toMap()
        assertFalse(f.containsKey("brief") || f.containsKey("brief_token"))
        assertEquals("text", f["enquiry_text"])
        // What stays from the earlier rounds: margins, bubbles, the foot of the list, the Indic text style, the one-moment line.
        val s = screen!!
        assertTrue(s.contains("DESK_SIDE_MARGIN") && s.contains("clip(RoundedCornerShape(14.dp)).background(D.Me)") && s.contains("list.animateScrollBy(1_000_000f)") && s.contains("Trim.None"))
    }
}
