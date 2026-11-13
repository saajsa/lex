package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.20: Lex's follow-up questions before an enquiry is sent. */
class DeskGatherTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theAnswerIsAppendedInTheAgreedFormat() {
        assertEquals("\n\nLex asked: When did the notice arrive?\nThey answered: On 3 March.", gatherLabel("When did the notice arrive?", "On 3 March."))
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        // Every answer, after the text and the reading's own answer, in the order asked.
        assertTrue(s.contains("gatherLog.joinToString(\"\") { gatherLabel(it.first, it.second) }"))
    }

    @Test fun atMostThreeQuestionsInAllTheReadingsOwnCountingAsTheFirst() {
        assertEquals(3, GATHER_MAX)
        assertTrue(mayAskMore(false, false, 0)); assertTrue(mayAskMore(true, false, 1)); assertTrue(mayAskMore(true, false, 2))
        assertFalse(mayAskMore(true, false, 3)); assertFalse(mayAskMore(false, false, 3))
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        // The count sent to the desk and tested is the reading's own (1 if it was answered) plus Lex's own.
        assertTrue(s.contains("private val readingAsked get() = if (!more.isNullOrEmpty()) 1 else 0"))
        assertTrue(s.contains("val askedSoFar = readingAsked + gatherAsked") && s.contains("repo.gather(fullText(), askedSoFar, langField(convLang), docsField(read?.documents.orEmpty()))"))
    }

    @Test fun aSkippedFollowUpMeansNoMoreQuestions() {
        assertFalse(mayAskMore(false, true, 0))           // "send it as it is" on the reading's question
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(s.contains("private val readingSkipped get() = more == \"\""))
        assertTrue(s.contains("mayAskMore(readingAsked > 0, readingSkipped, askedSoFar)"))
    }

    @Test fun sendNowEndsTheQuestionsAtOnce() {
        assertEquals("Send Now", GATHER_SEND_NOW)
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        val now = s.substringAfter("fun sendNow()").substringBefore("// ---- Documents")
        assertFalse(now.contains("answer(Step."))            // no visitor bubble, so no Change link
        assertTrue(now.contains("finishGather()"))
        val screen = src("feature/desk/DeskScreen.kt")!!
        assertTrue(screen.contains("Dock.Gather -> GatherDock(ui.error, vm::submitGather, vm::sendNow)") && screen.contains("Chips(listOf(Triple(GATHER_SEND_NOW, false, onSendNow))"))
    }

    @Test fun aFailureNeverBlocksSendingAndTheTextIsReadOnceMore() {
        // Nothing, an error, a timeout (null), "done" or an empty question all mean: no more.
        assertNull(parseGather(null)); assertNull(parseGather(mapOf("success" to false)))
        assertFalse(parseGather(mapOf("success" to true, "question" to "", "topic" to "", "done" to true))!!.more)
        assertFalse(parseGather(mapOf("question" to "A question?", "done" to true))!!.more)
        assertFalse(parseGather(mapOf("question" to "  ", "done" to false))!!.more)
        assertEquals(GatherResult("When did it arrive?", "dates", false), parseGather(mapOf("success" to true, "question" to " When did it arrive? ", "topic" to "dates", "done" to false)))
        assertTrue(parseGather(mapOf("question" to "When did it arrive?", "done" to false))!!.more)
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(s.contains("if (r == null || !r.more) return finishGather()"))
        // After answers, one more reading of the whole text; the papers step waits for it.
        val fin = s.substringAfter("private suspend fun finishGather()").substringBefore("fun submitGather(")
        assertTrue(fin.contains("if (gatherLog.isNotEmpty())") && fin.contains("repo.check(fullText(), langField(convLang))") && fin.contains("next()"))
        assertTrue(s.contains("reread?.await()"))
    }

    @Test fun leadsAskLeadDeskGatherAndTheWaitIsTwentySeconds() {
        val data = src("feature/desk/DeskData.kt") ?: return
        assertTrue(data.contains("(if (lead) api.leadDeskGather(text, asked, lang, docs, conversation) else api.deskGather(text, asked, lang, docs, conversation))"))
        assertTrue(data.contains("suspend fun gather(text: String, asked: Int, lang: String? = null, docs: String? = null): GatherResult? = withTimeoutOrNull(DESK_READ_WAIT_MS)"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("@POST(\"desk/gather\")") && api.contains("@POST(\"lead/desk/gather\")") && api.contains("@Field(\"asked\") asked: Int"))
        assertTrue(Regex("@Headers\\(\"X-Read-Timeout: 22\"\\)\\s+@POST\\(\"desk/gather\"\\)").containsMatchIn(api))
        // "One moment…" after about 3 s.
        assertTrue(src("feature/desk/DeskViewModel.kt")!!.contains("withSlowNote(ONE_MOMENT) { repo.gather(fullText(), askedSoFar, langField(convLang), docsField(read?.documents.orEmpty())) }"))
    }

    @Test fun theQuestionsComeAfterTheReadingsOwnFollowUpAndBeforeThePapers() {
        val steps = Step.values().toList()
        assertTrue(steps.indexOf(Step.CLARIFY) < steps.indexOf(Step.GATHER) && steps.indexOf(Step.GATHER) < steps.indexOf(Step.DOCS))
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(s.contains("vetted == \"clarify\" && more == null -> askClarify()\n            !gatherDone -> gather()\n            docs == null -> askDocs()"))
        // Changing an answer starts the questions over; the desk restarting clears them.
        assertTrue(s.contains("Step.GATHER -> { gatherLog = emptyList(); gatherPending = null; gatherAsked = 0; gatherDone = false; gatherRan = false }"))
        // Asked in Lex's own bubble and voice (a desk line), with the box and a Send Now chip as the dock.
        assertTrue(s.contains("say(Para.Text(r.question), prompt = Step.GATHER, footer = r.tongue.lineFooter)") && s.contains("dock(Dock.Gather)"))
    }

    @Test fun theDeadlineStepIsSkippedOnlyWhenGatheringRan() {
        assertTrue(parseGather(mapOf("question" to "", "done" to true, "ran" to true))!!.ran)
        assertFalse(parseGather(mapOf("question" to "", "done" to true, "ran" to false))!!.ran)
        assertFalse(parseGather(mapOf("question" to "", "done" to true))!!.ran)            // an older server: the step stays
        assertTrue(skipDeadlineStep(true)); assertFalse(skipDeadlineStep(false))
        val s = src("feature/desk/DeskViewModel.kt") ?: return
        // Any call that ran, in the whole journey; failed, timed out or never ran: kept.
        assertTrue(s.contains("if (r?.ran == true) gatherRan = true"))
        assertTrue(s.substringAfter("private suspend fun askDeadline()").take(300).contains("if (skipDeadlineStep(gatherRan)) { deadline = \"\" to \"\"; return next() }"))
        // Reset when the questions start over and when the desk restarts.
        assertTrue(s.contains("gatherDone = false; gatherRan = false }") && s.contains("gatherDone = false; gatherRan = false; sessionHash = null"))
    }
}
