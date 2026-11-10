package com.legumlex.client.feature.board

import com.legumlex.client.feature.workspace.Workspaces
import com.legumlex.client.feature.workspace.WorkspaceItem
import com.legumlex.client.feature.workspace.isWorkspacePaused
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-008 board audit, app batch: the import line, the banners, the CNR the scanner accepts, freshness, and the paused account. */
class BoardAuditTest {
    private fun credits(balance: Int, daysLeft: Int?, low: Boolean, label: String = "") = TodayCredits(balance, 1, 1, daysLeft, label, low)

    // ---- A6: what the import did, in the web's words
    @Test fun importLineWithTheCheck() {
        val d = ImportDone(10, 2, 12, check = ImportCheck(checked = 8, unchecked = 4))
        assertEquals("10 added, 2 updated. You now have 12 cases in your diary. 8 checked on eCourts just now, 4 not checked (your credits did not cover them).", importDoneLine(d))
    }

    @Test fun importLineWhenEverythingWasChecked() {
        val d = ImportDone(1, 0, 1, check = ImportCheck(checked = 1, unchecked = 0))
        assertEquals("1 added, 0 updated. You now have 1 case in your diary. 1 checked on eCourts just now.", importDoneLine(d))
    }

    @Test fun importLineWhenTheCheckStopped() {
        val d = ImportDone(3, 0, 3, check = ImportCheck(0, 0, "no_credits"))
        assertTrue(importDoneLine(d).endsWith("They are checked on eCourts from tomorrow morning."))
    }

    @Test fun importLineCountsTheDatesToSettle() {
        val m = Mismatch(1, "PBLD010012342026", "", "", "2026-11-10", "2026-11-08")
        assertTrue(importDoneLine(ImportDone(1, 0, 1, listOf(m), ImportCheck(1, 0))).endsWith("1 date to settle."))
        assertTrue(importDoneLine(ImportDone(2, 0, 2, listOf(m, m), ImportCheck(2, 0))).endsWith("2 dates to settle."))
    }

    @Test fun importDoneReadsTheCheckFromTheServer() {
        val done = parseImportDone(mapOf("added" to 2.0, "updated" to 0.0, "total" to 2.0, "check" to mapOf("checked" to 1.0, "unchecked" to 1.0, "stopped" to false)))
        assertEquals(1, done.check.checked); assertEquals(1, done.check.unchecked); assertNull(done.check.stopped)
        // An old server with no `check` still parses.
        assertEquals(0, parseImportDone(mapOf("added" to 1.0)).check.checked)
        assertEquals("credits", parseImportDone(mapOf("check" to mapOf("checked" to 0.0, "stopped" to "credits"))).check.stopped)
    }

    // ---- A7: the banners and the line under the month
    @Test fun lowBannerWords() {
        assertNull(lowCreditsText(credits(100, 50, low = false)))
        assertEquals("You have no credits left", lowCreditsText(credits(0, 0, low = true)))
        assertEquals("Less than a day of checks is left", lowCreditsText(credits(3, 0, low = true)))
        assertEquals("About 1 day of checks is left", lowCreditsText(credits(7, 1, low = true)))
        assertEquals("About 3 days of checks are left", lowCreditsText(credits(21, 3, low = true)))
    }

    @Test fun shortBannerWords() {
        val s = CheckShort(checked = 3, of = 7, creditsHad = 3, unchecked = 4)
        assertEquals("Only 3 of your 7 cases were checked this morning", shortTitle(s))
        assertTrue(shortInfo(s).startsWith("You had 3 credits, so we checked the 3 cases listed soonest. The other 4 keep their last known dates"))
        assertTrue(shortInfo(CheckShort(1, 7, 1, 6)).startsWith("You had 1 credit, so we checked the 1 case listed soonest."))
    }

    @Test fun trackedLineUsesTheServersDaysLabel() {
        val t = parseToday(mapOf("cases_tracked" to 7.0, "credits" to mapOf("balance" to 100.0, "days_label" to "About 14 days of checks")))
        assertEquals("7 cases tracked · About 14 days of checks", trackedLine(t))
        val one = parseToday(mapOf("cases_tracked" to 1.0, "credits" to mapOf("balance" to 0.0, "days_label" to "")))
        assertEquals("1 case tracked", trackedLine(one))
    }

    // ---- A10
    @Test fun scannerAcceptsTheServersCnrPattern() {
        // four letters, eight letters or digits, four digits
        assertEquals("DLHC0A1B2C342026", extractCnr("DLHC0A1B2C342026"))
        assertEquals("PBLD010012342026", extractCnr("PBLD010012342026"))
        // not four digits at the end
        assertNull(extractCnr("PBLD0100123420AB"))
        // a longer run of letters and digits is not a CNR
        assertNull(extractCnr("PBLD010012342026X"))
    }

    @Test fun notFlaggedIsTheOneRefusalThatCountsAsDone() {
        assertTrue(notFlagged(422, "not_flagged"))
        assertFalse(notFlagged(422, "cap_reached"))
        assertFalse(notFlagged(403, "not_flagged"))
        assertFalse(notFlagged(null, null))
    }

    @Test fun malformedJsonIsNotOffline() {
        assertTrue(java.net.UnknownHostException("x").isOffline())
        assertTrue(java.net.SocketTimeoutException("x").isOffline())
        assertFalse(com.squareup.moshi.JsonEncodingException("Use JsonReader.setLenient(true)").isOffline())
        assertFalse(com.squareup.moshi.JsonDataException("x").isOffline())
    }

    @Test fun aRefusalIsReadAsARefusalAndNothingElse() {
        assertEquals(Answer.Ok, answerOfResult(Result.success(1)))
        assertEquals(Answer.Failed, answerOfResult(Result.failure<Int>(java.io.IOException("offline"))))
        // The two-refusal rule on top of it: one refusal keeps the counsel; two hide the board.
        assertEquals(Verdict.Keep, decideAccess(true, listOf(Answer.Refused)))
        assertEquals(Verdict.No, decideAccess(true, listOf(Answer.Refused, Answer.Refused)))
    }

    // ---- A9: a new day
    @Test fun theChosenDayFollowsTodayOnRolloverButAPickedDayStays() {
        BoardHub.clear()
        BoardHub.rollover(LocalDate.of(2026, 11, 10))
        BoardHub.choose("2026-11-10")
        BoardHub.rollover(LocalDate.of(2026, 11, 11))
        assertEquals("2026-11-11", BoardHub.chosen.value)
        BoardHub.choose("2026-11-20")
        BoardHub.rollover(LocalDate.of(2026, 11, 12))
        assertEquals("2026-11-20", BoardHub.chosen.value)
        BoardHub.clear()
    }

    @Test fun theChipFollowsTheBalanceAfterASpend() {
        BoardHub.clear()
        BoardHub.setChip(10, true)
        BoardHub.applyBalance(4)
        assertEquals(BoardHub.Chip(4, true), BoardHub.chip.value)
        // Credits bought: no longer low.
        BoardHub.applyBalance(500)
        assertEquals(BoardHub.Chip(500, false), BoardHub.chip.value)
        BoardHub.clear()
    }

    @Test fun theHistoryKeepsItsPagesWhenTheSameRowsAreReadAgain() {
        val a = LedgerRow(9, "2026-11-10", "spend", "x", -1, 99, null)
        val b = LedgerRow(8, "2026-11-09", "spend", "y", -1, 100, null)
        assertEquals(historyKey(listOf(a, b)), historyKey(listOf(a, b)))
        // A new newest row starts the paging over.
        val c = LedgerRow(10, "2026-11-10", "spend", "z", -1, 98, null)
        assertFalse(historyKey(listOf(a, b)) == historyKey(listOf(c, a, b)))
        assertEquals(null to 0, historyKey(null))
    }

    // ---- A11
    private fun item(status: String) = WorkspaceItem("counsel", "Counsel", status, enter = false, note = "", canAdd = false, canRemove = false)

    @Test fun pausedMeansCounselOnlyAndSuspended() {
        assertTrue(isWorkspacePaused(Workspaces(listOf(item("suspended")), choose = false, counselOnly = true)))
        // A person who also holds the Client Portal is not paused out of the app.
        assertFalse(isWorkspacePaused(Workspaces(listOf(item("suspended")), choose = false, counselOnly = false)))
        assertFalse(isWorkspacePaused(Workspaces(listOf(item("active")), choose = false, counselOnly = true)))
        assertFalse(isWorkspacePaused(null))
    }

    // ---- A5: find, then add
    private val found = mapOf(
        "lookup_id" to "lk_1", "charged" to 4.0, "balance" to 96.0,
        "case" to mapOf("cnr" to "PBLD010012342026", "case_no" to "CS/12/2026", "case_type" to "Civil Suit", "title" to "A v. B", "court" to "Civil Judge Ludhiana", "room" to "Court No. 3", "bench" to "Ld. Smt. X", "next_date" to "2026-12-01", "stage" to ""),
    )

    @Test fun lookupIsParsedAndKeepsItsId() {
        val l = parseLookup(found)
        assertEquals("lk_1", l.lookupId); assertEquals(4, l.charged); assertEquals(96, l.balance)
        assertEquals("A v. B", l.case.title); assertEquals("2026-12-01", l.case.nextDate)
    }

    @Test fun factsAreInTheWebsOrderAndBlanksAreLeftOut() {
        val facts = cnrFacts(parseLookup(found).case, LocalDate.of(2026, 11, 10))
        assertEquals(listOf("Case Number", "Court", "Next Date"), facts.map { it.first })
        assertEquals("Civil Judge Ludhiana · Ld. Smt. X", facts[1].second)
    }

    @Test fun addLineStatesTheCreditsUsedAndTheDailyCost() {
        assertEquals("Finding this case on eCourts used 4 credits. Adding it to your diary is free; from tomorrow it is checked every morning for 1 credit.", cnrAddLine(4, 1))
        assertEquals("Finding this case on eCourts used 1 credit. Adding it to your diary is free; from tomorrow it is checked every morning for 2 credits.", cnrAddLine(1, 2))
    }

    // ---- hand cases
    @Test fun aHandCaseNeverSendsACnr() {
        val f = handFields(" CS/1/2026 ", "A", "B", "Court", "", LocalDate.of(2026, 11, 14))
        assertFalse(f.containsKey("cnr"))
        assertEquals("CS/1/2026", f["case_no"]); assertEquals("2026-11-14", f["next_date"])
    }

    @Test fun refusalsOnTheFirstStepGoBackThere() {
        assertTrue(handFirstStep("duplicate")); assertTrue(handFirstStep("case_no"))
        assertFalse(handFirstStep("next_date")); assertFalse(handFirstStep("court"))
    }

    @Test fun supersededDatesAreMarkedReplaced() {
        assertEquals("Entered By You · Replaced", historyLabel(DateHistoryRow("2026-11-11", "diary", "Entered By You", null, superseded = true)))
        assertEquals("From eCourts", historyLabel(DateHistoryRow("2026-11-11", "ecourts", "From eCourts", null)))
        val h = parseCase(mapOf("id" to 1.0, "history" to listOf(mapOf("date" to "2026-11-11", "source" to "x", "label" to "L", "superseded" to true)))).history
        assertTrue(h.single().superseded)
    }

    // ---- A6
    @Test fun importPreviewCarriesTheCostLine() {
        val p = parsePreview(mapOf("import_id" to "i1", "cost" to mapOf("per_case" to 1.0, "cases" to 40.0, "total" to 40.0, "balance" to 25.0, "covers" to 25.0, "line" to "Each case is checked on eCourts now, 1 credit a case.")))
        assertEquals("Each case is checked on eCourts now, 1 credit a case.", p.cost?.line); assertEquals(25, p.cost?.covers)
        assertNull(parsePreview(mapOf("import_id" to "i1")).cost)
    }

    @Test fun aRepeatedConfirmIsTheFirstResult() {
        val d = parseImportDone(mapOf("added" to 3.0, "updated" to 0.0, "total" to 3.0, "repeated" to true, "check" to mapOf("checked" to 3.0, "unchecked" to 0.0)))
        assertTrue(d.repeated); assertEquals(3, d.added)
        assertFalse(parseImportDone(mapOf("added" to 1.0)).repeated)
    }

    // ---- A8
    private val credits = mapOf(
        "balance" to 50.0, "costs" to mapOf("add" to 4.0, "check" to 1.0), "credit_months" to 12.0,
        "packs" to listOf(mapOf("key" to "starter", "label" to "Starter", "credits" to 500.0, "price" to 590.0, "lasts" to "About 71 days at 7 cases", "bonus_pct" to 10.0, "base" to 500.0, "gst_amount" to 90.0)),
        "next_lapse" to mapOf("credits" to 40.0, "expires_at" to "2026-12-11", "kind" to "trial"),
        "history" to listOf(mapOf("id" to 1.0, "when" to "2026-11-01 07:00:00", "kind" to "check", "label" to "raw", "delta" to -1.0, "balance_after" to 49.0)),
        "history_rows" to listOf(mapOf("kind" to "check", "day" to "2026-11-09", "when" to "2026-11-09 07:00:00", "delta" to -7.0, "balance_after" to 43.0, "what" to "Morning check, 7 cases", "cases" to 7.0)),
    )

    @Test fun packsCarryTheServersLinesAndBonus() {
        val p = parseCredits(credits).packs.single()
        assertEquals("About 71 days at 7 cases", p.lasts); assertEquals(10, p.bonusPct); assertEquals(90.0, p.gstAmount, 0.0)
    }

    @Test fun historyUsesTheGroupedRowsWhenSent() {
        val d = parseCredits(credits)
        assertEquals("Morning check, 7 cases", d.history.single().label); assertEquals(7, d.history.single().cases)
        // An older server without history_rows still reads the raw ledger.
        assertEquals("raw", parseCredits(credits - "history_rows").history.single().label)
        val page = parseLedgerPage(mapOf("grouped" to true, "total" to 1.0, "items" to listOf(credits["history_rows"].let { (it as List<*>).first() })))
        assertEquals("Morning check, 7 cases", page.items.single().label)
    }

    @Test fun nextLapseReadsLikeTheWeb() {
        assertEquals("Expiring Next: 40 credits, Trial, Tue, 11 Dec 2026".replace("Tue", "Fri"), nextLapseLine(parseCredits(credits).nextLapse))
        assertEquals("Nothing is about to lapse", nextLapseLine(parseCredits(credits - "next_lapse").nextLapse))
        assertEquals("Expiring Next: 1 credit, Pack, Sat, 12 Dec 2026", nextLapseLine(NextLapse(1, "2026-12-12", "purchase")))
    }

    // ---- A11
    @Test fun pausedIsA403WithTheCode() {
        assertTrue(pausedRefusal(403, "paused"))
        assertFalse(pausedRefusal(403, "forbidden"))
        assertFalse(pausedRefusal(404, "paused"))
        assertFalse(isPausedRefusal(Result.success(1)))
    }
}
