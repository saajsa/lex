package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 Phase 2: the brief, its Review step, and what is sent. */
class DeskBriefTest {
    private val brief = DeskBrief(
        "My tenant stopped paying rent in June.",
        listOf(BriefPerson("Ramesh Kumar", "tenant"), BriefPerson("Sunita")),
        "I want the rent recovered.",
        listOf(BriefDate("June", "when rent stopped")),
    )
    private fun fields(text: String = "The words, exactly.", brief: DeskBrief? = null, token: String = "") =
        enquiryFields(text, "individual", "9876543210", null, "", "reading", null, "key", "", "", brief, token).toMap()

    @Test fun noBriefCallWhileTheTextIsUnchanged() {
        val cache = BriefCache()
        val r = BriefResult("read", brief, "tok1")
        assertNull(cache.get("one"))
        cache.put("one", r)
        assertEquals(r, cache.get("one"))          // back and forward: the same draft, no new call
        assertEquals(r, cache.get("one"))
        assertNull(cache.get("one "))              // a changed text is asked again
        assertNull(cache.get("two"))
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        val make = src.substringAfter("private suspend fun makeBrief()").substringBefore("/** Send with the brief")
        assertTrue(make.contains("briefCache.get(whole)") && make.contains("if (r == null)") && make.contains("repo.brief(whole)"))
        // It is made from the whole enquiry as it will be sent.
        assertTrue(src.contains("val whole = fullText()") && src.contains("fullText(), entity.orEmpty()"))
    }

    @Test fun anEditedAndAnUnchangedBriefBothSendTheDesksToken() {
        val edited = brief.copy(wants = "I want the rent and the keys back.")
        for (b in listOf(brief, edited)) {
            val f = fields(brief = b, token = "tok1")
            assertEquals("tok1", f["brief_token"])
            assertEquals(b.toJson(), f["brief"])
        }
        assertEquals(false, fields(brief = brief, token = "tok1")["brief"] == fields(brief = edited, token = "tok1")["brief"])
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        // Both go through sendBrief, which takes the token from the cached draft, never from the edit.
        assertTrue(src.contains("fun sendBrief(brief: DeskBrief)") && src.contains("briefToken = r.token"))
        assertTrue(src.contains("brief = briefToSend, briefToken = briefToken"))
    }

    @Test fun theNoBriefPathSendsNeitherField() {
        val none = fields()
        assertFalse(none.containsKey("brief")); assertFalse(none.containsKey("brief_token"))
        // Nor a brief with no token (it would be unverifiable).
        val noToken = fields(brief = brief, token = "")
        assertFalse(noToken.containsKey("brief")); assertFalse(noToken.containsKey("brief_token"))
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        val own = src.substringAfter("fun sendOwnWords()").substringBefore("// ---- A hearing")
        assertTrue(own.contains("briefToSend = null; briefToken = \"\""))
    }

    @Test fun reviewIsHiddenOnAnyStateButRead() {
        assertTrue(BriefResult("read", brief, "tok").reviewable)
        assertFalse(BriefResult("failed", brief, "tok").reviewable)
        assertFalse(BriefResult("skipped", null, "").reviewable)
        assertFalse(BriefResult("read", null, "tok").reviewable)      // no brief
        assertFalse(BriefResult("read", brief, "").reviewable)        // no token
        assertFalse(BriefResult("something else", brief, "tok").reviewable)
        // A network error or a timeout reads as no brief.
        assertFalse(parseBriefResult(null).reviewable)
        assertEquals(NO_BRIEF, parseBriefResult(null))
        assertFalse(NO_BRIEF.reviewable)
        val read = parseBriefResult(mapOf("state" to "read", "brief_token" to "tok", "brief" to mapOf("what_happened" to "x", "who" to listOf(mapOf("name" to "A", "role" to "r")),
            "wants" to "w", "dates" to listOf(mapOf("as_written" to "June", "about" to "rent")))))
        assertTrue(read.reviewable)
        assertEquals(DeskBrief("x", listOf(BriefPerson("A", "r")), "w", listOf(BriefDate("June", "rent"))), read.brief)
        assertFalse(parseBriefResult(mapOf("state" to "failed", "reason" to "model", "brief" to null, "brief_token" to "")).reviewable)
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(src.contains("if (!r.reviewable) { briefDecided = true"))
        // 14 seconds, and a lead's desk (which has no brief endpoint) is not asked.
        val data = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskData.kt").readText()
        assertTrue(data.contains("withTimeoutOrNull(14_000)"))
        // A lead's desk asks lead/desk/brief, and gets the same Review screen (nothing in the VM or the screen depends on who is asking).
        assertTrue(data.contains("(if (lead) api.leadDeskBrief(text) else api.deskBrief(text))"))
        assertFalse(data.contains("if (lead) return NO_BRIEF"))
        val api = java.io.File("src/main/java/com/legumlex/client/data/api/ApiService.kt").readText()
        assertTrue(api.contains("@POST(\"lead/desk/brief\")") && api.contains("@POST(\"desk/brief\")"))
    }

    @Test fun sendMyOwnWordsIsOnBothScreens() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskScreen.kt").takeIf { it.isFile }?.readText() ?: return
        val dock = src.substringAfter("private fun ReviewDock(").substringBefore("private fun BriefRow(")
        // Added outside the if (!editing) choice, so the review and the edit screen both have it.
        val idx = dock.indexOf("add(Triple(\"Send My Own Words Instead\", false, onOwn))")
        assertTrue(idx > dock.indexOf("if (!editing) add(Triple(\"Edit\""))
        assertTrue(dock.contains("add(Triple(\"Back\", false) { editing = false })"))
        assertTrue(dock.contains("Send To The Firm") && dock.contains("Your Own Words") && dock.contains("They always read your own words as well."))
        for (label in listOf("What Happened", "Who Is Involved", "What You Want", "Dates, As You Wrote Them")) assertTrue(label, dock.contains(label))
        // The desk line and the spinner, as worded.
        val vm = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").readText()
        assertTrue(vm.contains("Here is what I will tell the advocates, in your own words. Please check it, change anything that is wrong, and send it."))
        assertTrue(vm.contains("Getting what you wrote ready for the advocates…"))
    }

    @Test fun theOriginalTextGoesByteIdentical() {
        val tricky = "My tenant — Ramesh — owes ₹45,000.\n\nHe said: \"I will pay\" on 15 March.\tNo reply.  Two  spaces and a trailing space. "
        for (b in listOf<DeskBrief?>(null, brief)) {
            val f = enquiryFields(tricky, "individual", "9", null, "", "", null, "", "", "", b, if (b != null) "tok" else "").toMap()
            assertEquals(tricky, f["enquiry_text"])
            assertEquals(tricky.toByteArray().toList(), f["enquiry_text"]!!.toByteArray().toList())
        }
        // Never the brief.
        assertFalse(fields(brief = brief, token = "tok")["enquiry_text"]!!.contains("Ramesh"))
    }

    @Test fun theEditFieldsAndTheirLimits() {
        val ok = parseBriefEdit("What  happened", "Ramesh Kumar, tenant\nSunita\n\n", "Wants", "June, rent stopped\n15 March, 2026")
        assertEquals(BriefEdit.Ok(DeskBrief("What happened", listOf(BriefPerson("Ramesh Kumar", "tenant"), BriefPerson("Sunita", "")), "Wants",
            listOf(BriefDate("June", "rent stopped"), BriefDate("15 March", "2026")))), ok)
        assertTrue(parseBriefEdit("a".repeat(401), "", "", "") is BriefEdit.Problem)
        assertTrue(parseBriefEdit("a".repeat(400), "", "", "") is BriefEdit.Ok)
        assertTrue(parseBriefEdit("", "", "b".repeat(201), "") is BriefEdit.Problem)
        assertTrue(parseBriefEdit("x", (1..6).joinToString("\n") { "P$it" }, "", "") is BriefEdit.Problem)      // at most 5 people
        assertTrue(parseBriefEdit("x", (1..5).joinToString("\n") { "P$it" }, "", "") is BriefEdit.Ok)
        assertTrue(parseBriefEdit("x", "", "", (1..5).joinToString("\n") { "d$it" }) is BriefEdit.Problem)      // at most 4 dates
        assertTrue(parseBriefEdit("x", "", "", (1..4).joinToString("\n") { "d$it" }) is BriefEdit.Ok)
        assertTrue(parseBriefEdit(" ", "\n", " ", "\n") is BriefEdit.Problem)                                 // nothing left
        // A line reads back as it was shown.
        assertEquals("Ramesh Kumar, tenant", BriefPerson("Ramesh Kumar", "tenant").line()); assertEquals("Sunita", BriefPerson("Sunita").line())
        assertEquals("June, rent stopped", BriefDate("June", "rent stopped").line())
    }

    @Test fun theBriefJsonIsWhatTheServerTakes() {
        val j = DeskBrief("Say \"hi\"\nnew line \\ back", listOf(BriefPerson("A", "")), "", listOf(BriefDate("15 March", "x"))).toJson()
        assertEquals("{\"what_happened\":\"Say \\\"hi\\\"\\nnew line \\\\ back\",\"who\":[{\"name\":\"A\",\"role\":\"\"}],\"wants\":\"\",\"dates\":[{\"as_written\":\"15 March\",\"about\":\"x\"}]}", j)
        assertEquals("{\"what_happened\":\"\",\"who\":[],\"wants\":\"\",\"dates\":[]}", DeskBrief("", emptyList(), "", emptyList()).toJson())
    }

    @Test fun aClearEnquiryGetsItsOneQuestionOnlyForTheAskReasons() {
        assertEquals(setOf("time_limit_unknown", "forum_unknown", "missing_facts"), ASK_REASONS)
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(src.contains("v == \"clear\" && r != null && r.askReason in ASK_REASONS && r.followUp.isNotBlank()"))
        // The re-read after the answer only updates the area and the token: it never sets vetted, so it never asks again.
        val clarify = src.substringAfter("fun submitClarify(").substringBefore("fun sendAsItIs")
        assertFalse(clarify.contains("vetted ="))
        assertTrue(src.contains("fun sendAsItIs()"))
        assertTrue(java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskData.kt").readText().contains("askReason = (d[\"ask_reason\"] as? String)"))
    }
}
