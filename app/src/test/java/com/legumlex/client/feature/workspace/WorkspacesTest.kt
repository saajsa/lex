package com.legumlex.client.feature.workspace

import com.legumlex.client.feature.board.BoardAccess
import com.legumlex.client.feature.board.counselChromeFor
import com.legumlex.client.feature.board.workspaceUnknownFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesTest {
    private val both = mapOf(
        "choose" to true, "counsel_only" to false,
        "items" to listOf(
            mapOf("kind" to "counsel", "label" to "Counsel", "status" to "active", "enter" to true, "note" to "", "can_add" to false, "can_remove" to true),
            mapOf("kind" to "portal", "label" to "Client Portal", "status" to "active", "enter" to true, "note" to "", "can_add" to false, "can_remove" to false),
        ),
    )

    @Test fun the_servers_list_is_read_as_it_comes() {
        val w = parseWorkspaceList(both)
        assertTrue(w.choose); assertFalse(w.counselOnly)
        assertEquals("Counsel", w.counsel?.label)
        assertTrue(w.counsel!!.canRemove)
        assertEquals("Client Portal", w.portal?.label)
        assertFalse(w.portal!!.canRemove)
    }

    @Test fun a_missing_answer_holds_nothing_and_asks_nothing() {
        val w = parseWorkspaceList(null)
        assertTrue(w.items.isEmpty()); assertFalse(w.choose)
        assertNull(w.counsel)
    }

    @Test fun a_pending_counsel_cannot_be_entered() {
        val w = parseWorkspaceList(mapOf("choose" to false, "items" to listOf(
            mapOf("kind" to "counsel", "label" to "Counsel", "status" to "pending", "enter" to false, "note" to "With the firm"))))
        assertFalse(w.counsel!!.enter)
        assertEquals("With the firm", w.counsel!!.note)
    }

    @Test fun the_chosen_portal_never_wears_the_counsel_chrome() {
        assertFalse(counselChromeFor(BoardAccess.Unknown, Mode.Portal))
    }

    @Test fun the_chosen_counsel_wears_it_before_the_server_answers() {
        assertTrue(counselChromeFor(BoardAccess.Unknown, Mode.Counsel))
        assertFalse(counselChromeFor(BoardAccess.No, Mode.Counsel))
    }

    @Test fun nothing_chosen_leaves_the_server_to_decide_and_stays_neutral_until_it_does() {
        assertFalse(counselChromeFor(BoardAccess.Unknown, Mode.Unknown))
        assertTrue(workspaceUnknownFor(BoardAccess.Unknown, Mode.Unknown))
        assertFalse(workspaceUnknownFor(BoardAccess.Unknown, Mode.Counsel))
        assertFalse(workspaceUnknownFor(BoardAccess.Unknown, Mode.Portal))
    }

    @Test fun the_greeting_follows_the_hour() {
        assertEquals("GOOD MORNING", greeting(8)); assertEquals("GOOD AFTERNOON", greeting(14)); assertEquals("GOOD EVENING", greeting(21))
    }

    private fun ws(counselEnter: Boolean?, note: String = "") = Workspaces(
        listOfNotNull(
            counselEnter?.let { WorkspaceItem("counsel", "Counsel", if (it) "active" else "removed", it, note, false, it) },
            WorkspaceItem("portal", "Client Portal", "active", true, "", false, false),
        ), choose = counselEnter == true, counselOnly = false)

    @Test fun a_counsel_workspace_that_goes_moves_a_counsel_to_the_portal_with_a_note() {
        val c = workspaceChange(ws(true), ws(false), Mode.Counsel) as WorkspaceChange.CounselGone
        assertTrue(c.moveToPortal); assertEquals(GONE_NOTE, c.note)
    }

    @Test fun the_servers_note_is_used_when_it_gives_one() {
        val c = workspaceChange(ws(true), ws(false, "Suspended by the firm."), Mode.Counsel) as WorkspaceChange.CounselGone
        assertEquals("Suspended by the firm.", c.note)
    }

    @Test fun a_person_in_the_portal_is_only_told_when_counsel_goes() {
        val c = workspaceChange(ws(true), ws(null), Mode.Portal) as WorkspaceChange.CounselGone
        assertFalse(c.moveToPortal)
    }

    @Test fun a_counsel_workspace_that_appears_is_a_note_and_nothing_swaps() {
        val c = workspaceChange(ws(null), ws(true), Mode.Portal)
        assertEquals(WorkspaceChange.CounselAdded(ADDED_NOTE), c)
    }

    @Test fun no_change_and_no_earlier_list_say_nothing() {
        assertEquals(WorkspaceChange.None, workspaceChange(ws(true), ws(true), Mode.Counsel))
        assertEquals(WorkspaceChange.None, workspaceChange(null, ws(true), Mode.Counsel))
    }

    @Test fun the_status_word_is_the_servers_own_capitalised() {
        assertEquals("Pending", statusWord("pending")); assertEquals("Rejected", statusWord("rejected")); assertEquals("Not Added", statusWord(""))
    }
}

class PushRouteTest {
    private fun held(counsel: Boolean, portal: Boolean) = Workspaces(listOfNotNull(
        if (counsel) WorkspaceItem("counsel", "Counsel", "active", true, "", false, true) else null,
        if (portal) WorkspaceItem("portal", "Client Portal", "active", true, "", false, false) else null), counsel && portal, counsel && !portal)

    @Test fun a_push_without_a_workspace_changes_nothing() {
        assertEquals(PushRoute.Stay, workspaceForPush(null, held(true, true), Mode.Counsel))
        assertEquals(PushRoute.Stay, workspaceForPush("", null, Mode.Unknown))
    }

    @Test fun a_push_for_a_workspace_not_held_is_dropped() {
        assertEquals(PushRoute.Drop, workspaceForPush("counsel", held(false, true), Mode.Portal))
        assertEquals(PushRoute.Drop, workspaceForPush("client", held(true, false), Mode.Counsel))
    }

    @Test fun a_push_for_where_the_app_already_is_stays() {
        assertEquals(PushRoute.Stay, workspaceForPush("counsel", held(true, true), Mode.Counsel))
        assertEquals(PushRoute.Stay, workspaceForPush("client", held(true, true), Mode.Portal))
    }

    @Test fun a_push_for_the_other_workspace_opens_it_with_a_note() {
        assertEquals(PushRoute.Open(Mode.Portal, true), workspaceForPush("client", held(true, true), Mode.Counsel))
        assertEquals(PushRoute.Open(Mode.Counsel, true), workspaceForPush("counsel", held(true, true), Mode.Portal))
    }

    @Test fun at_a_cold_start_the_push_decides_without_a_note() {
        assertEquals(PushRoute.Open(Mode.Counsel, false), workspaceForPush("counsel", held(true, true), Mode.Unknown))
        // Before the list is known nothing can be dropped: the server already drops what is not held.
        assertEquals(PushRoute.Open(Mode.Portal, false), workspaceForPush("client", null, Mode.Unknown))
    }
}

class StatsTest {
    @Test fun counsel_figures_read_in_the_designs_order_with_indian_grouping() {
        val w = parseWorkspaceList(mapOf("choose" to true, "items" to listOf(
            mapOf("kind" to "counsel", "label" to "Counsel", "enter" to true, "stats" to mapOf("listed_today" to 4, "credits" to 1097)),
            mapOf("kind" to "portal", "label" to "Client Portal", "enter" to true, "stats" to mapOf("matters" to 2, "hearings_week" to 1)))))
        assertEquals(listOf("4" to "listed today", "1,097" to "credits"), statLines(w.counsel!!))
        assertEquals(listOf("2" to "matters", "1" to "hearing this week"), statLines(w.portal!!))
    }

    @Test fun without_figures_nothing_is_shown() {
        val w = parseWorkspaceList(mapOf("choose" to true, "items" to listOf(mapOf("kind" to "counsel", "label" to "Counsel", "enter" to true))))
        assertTrue(statLines(w.counsel!!).isEmpty())
    }
}

class OpenDecisionTest {
    private fun ws(choose: Boolean, counselOnly: Boolean = false) = Workspaces(emptyList(), choose, counselOnly)

    @Test fun a_person_with_both_workspaces_is_asked_at_every_sign_in() {
        assertEquals(OpenAs.Choose, openAs(ws(choose = true)))
    }

    @Test fun counsel_only_opens_as_counsel_and_everyone_else_as_the_portal() {
        assertEquals(OpenAs.Counsel, openAs(ws(choose = false, counselOnly = true)))
        assertEquals(OpenAs.Portal, openAs(ws(choose = false)))
    }

    @Test fun the_sign_in_answer_is_enough_to_decide_without_a_second_call() {
        // The sign-in answer carries `workspaces`; open() reads it from there (no profile call, no timeout).
        val signIn = mapOf("name" to "Ravinder Kaur", "workspaces" to mapOf("choose" to true, "counsel_only" to false, "items" to emptyList<Any>()))
        assertEquals(OpenAs.Choose, openAs(parseWorkspaceList(signIn["workspaces"] as Map<String, Any?>)))
    }
}

class PushHintTest {
    @Test fun a_sign_in_or_sign_out_drops_a_push_hint() {
        WorkspaceSession.pushHint = "counsel"
        WorkspaceSession.signInData = mapOf("workspaces" to emptyMap<String, Any?>())
        WorkspaceSession.clear()
        assertNull(WorkspaceSession.pushHint)
        assertNull(WorkspaceSession.signInData)
        assertEquals(Mode.Unknown, WorkspaceSession.mode.value)
    }
}

class AfterAddCounselTest {
    private fun counsel(enter: Boolean) = WorkspaceItem("counsel", "Counsel", "pending", enter, "", false, enter)
    private val portal = WorkspaceItem("portal", "Client Portal", "active", true, "", false, false)

    @Test fun after_add_counsel_with_both_workspaces_the_decision_is_choose() {
        val fresh = Workspaces(listOf(counsel(true), portal), choose = true, counselOnly = false)
        assertEquals(OpenAs.Choose, afterAddDecision(settledAfterAdd(fresh, fresh)))
    }

    @Test fun a_stale_submit_answer_does_not_decide_when_a_fresh_list_could_be_read() {
        // Worked out inside the request that granted the workspace: still the old account (Portal only).
        val stale = Workspaces(listOf(counsel(false), portal), choose = false, counselOnly = false)
        val fresh = Workspaces(listOf(counsel(true), portal), choose = true, counselOnly = false)
        assertEquals(fresh, settledAfterAdd(stale, fresh))
        assertEquals(OpenAs.Choose, afterAddDecision(settledAfterAdd(stale, fresh)))
    }

    @Test fun when_the_second_ask_fails_the_submits_own_list_is_used() {
        val own = Workspaces(listOf(counsel(true), portal), choose = true, counselOnly = false)
        assertEquals(own, settledAfterAdd(own, null))
    }
}

class DoorNextTest {
    private fun ws(canAdd: Boolean) = Workspaces(listOf(WorkspaceItem("counsel", "Counsel", "removed", false, "", canAdd, false),
        WorkspaceItem("portal", "Client Portal", "active", true, "", false, false)), choose = false, counselOnly = false)

    @Test fun the_servers_next_decides_when_it_is_there() {
        assertEquals(DoorNext.AddCounsel, doorClientNext(null, "add_counsel"))
        assertEquals(DoorNext.AddCounsel, doorClientNext(ws(false), "add_counsel"))
        assertEquals(DoorNext.Open, doorClientNext(ws(true), "open"))
    }

    @Test fun without_it_the_existing_outcome_goes_by_can_add() {
        assertEquals(DoorNext.AddCounsel, doorClientNext(ws(true), null))
        assertEquals(DoorNext.Open, doorClientNext(ws(false), null))
        assertEquals(DoorNext.Open, doorClientNext(null, null))
        // A blank next counts as absent.
        assertEquals(DoorNext.AddCounsel, doorClientNext(ws(true), ""))
    }
}
