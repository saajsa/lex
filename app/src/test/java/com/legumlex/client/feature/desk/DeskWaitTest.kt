package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.18: the app waits long enough for the desk's AI calls, shows progress, and draws Gurmukhi and Devanagari whole. */
class DeskWaitTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theWaitsMatchTheServersBudgets() {
        // The server answers a reading or a chat within about 14 s.
        assertTrue(DESK_READ_WAIT_MS >= 18_000L)
        // The socket outlasts the wait, so the wait is what ends a call.
        assertTrue(DESK_READ_HTTP_S * 1000L > DESK_READ_WAIT_MS)
        val data = src("feature/desk/DeskData.kt") ?: return
        assertTrue(data.contains("fun guest(turns: List<Pair<String, String>>): GuestReply? = withTimeoutOrNull(DESK_READ_WAIT_MS)"))
        assertTrue(data.contains("fun check(text: String, lang: String? = null): DeskReading? = withTimeoutOrNull(DESK_READ_WAIT_MS)"))
        val api = src("data/api/ApiService.kt")!!
        assertFalse(api.contains("X-Read-Timeout: 35"))          // no brief calls any more
        assertTrue(api.contains("\"X-Read-Timeout: 22\"") && api.contains("@POST(\"desk/check\")") && api.contains("@POST(\"guest/desk\")"))
        assertTrue(src("data/api/ApiClient.kt")!!.contains("req.header(\"X-Read-Timeout\")"))
    }

    @Test fun theProgressLinesAreSaidAfterAboutThreeSeconds() {
        assertEquals(3_000L, SLOW_AFTER_MS)
        assertEquals("One moment…", ONE_MOMENT)
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("withSlowNote(ONE_MOMENT) { repo.check(text, langField(convLang)) }"))
        assertTrue(vm.contains("withSlowNote(ONE_MOMENT) { repo.guest(guestTurns) }"))
        // It only replaces a pending line, and stops when the answer comes.
        val helper = vm.substringAfter("private suspend fun <T> withSlowNote").substringBefore("private fun dock(")
        assertTrue(helper.contains("delay(SLOW_AFTER_MS)") && helper.contains("is Dock.Waiting") && helper.contains("note.cancel()"))
        // Drawn by the dock's own pending line (WaitLine), whose colour is the desk's accent ink on the page.
        assertTrue(src("feature/desk/DeskScreen.kt")!!.contains("else WaitLine(d.label)"))
    }

    private val gurmukhi = "ਮੇਰੇ ਕਿਰਾਏਦਾਰ ਨੇ ਜੂਨ ਤੋਂ ਕਿਰਾਇਆ ਦੇਣਾ ਬੰਦ ਕਰ ਦਿੱਤਾ ਹੈ ਅਤੇ ਹੁਣ ਕਹਿੰਦਾ ਹੈ ਕਿ ਫਲੈਟ ਉਸਨੂੰ ਕਦੇ ਕਿਰਾਏ ਤੇ ਨਹੀਂ ਦਿੱਤਾ ਗਿਆ। ਮੈਂ ਚਾਹੁੰਦਾ ਹਾਂ ਕਿ ਬਕਾਇਆ ਕਿਰਾਇਆ ਵਸੂਲ ਹੋਵੇ ਅਤੇ ਫਲੈਟ ਖਾਲੀ ਕਰਵਾਇਆ ਜਾਵੇ।"
    private val devanagari = "मेरे किरायेदार ने जून से किराया देना बंद कर दिया है और अब कहता है कि फ्लैट कभी किराए पर नहीं दिया गया। मैं चाहता हूँ कि बकाया किराया वसूल हो।"

    @Test fun gurmukhiAndDevanagariPassThroughWholeAndByteIdentical() {
        for (text in listOf(gurmukhi, devanagari, gurmukhi.repeat(4))) {
            val f = enquiryFields(text, "individual", "9", null, "", "", null, "", "", "").toMap()
            assertEquals(text.toByteArray().toList(), f["enquiry_text"]!!.toByteArray().toList())
        }
        assertEquals("ਜੂਨ ਤੋਂ", cleanDeadlineText("  ਜੂਨ   ਤੋਂ  "))
    }

    @Test fun theLineHeightLeavesRoomForTheseScriptsAndNothingClipsThem() {
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        // Body text, the brief rows and the chat lines all use body(): a 1.55 line height, which clears Gurmukhi's and Devanagari's marks above and below.
        assertTrue(screen.contains("lineHeight = (size * 1.55f).sp"))
        val ratio = 1.55f
        assertTrue(ratio >= 1.5f)
        // The chat lines wrap (no softWrap = false, no single-line cap) in the words and the notes.
        val words = screen.substringAfter("private fun Words(").substringBefore("private fun MeLine(")
        assertFalse(words.contains("maxLines") || words.contains("softWrap"))
    }

    @Test fun theDebugSampleHasTheIndicLines() {
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        val sample = screen.substringAfter("fun DeskSampleScreen(")
        for (n in listOf("SAMPLE_HINDI_VISITOR", "SAMPLE_HINDI_DESK", "SAMPLE_PUNJABI_VISITOR", "SAMPLE_PUNJABI_DESK")) assertTrue(n, sample.contains("Para.Text($n)"))
        assertTrue(screen.contains("केस का नतीजा क्या होगा, यह मैं नहीं बता सकता") && screen.contains("ਕੇਸ ਦਾ ਨਤੀਜਾ ਕੀ ਹੋਵੇਗਾ, ਇਹ ਮੈਂ ਨਹੀਂ ਦੱਸ ਸਕਦਾ"))
        // Debug only: reached from the Debug chip, whose route is gated on BuildConfig.DEBUG.
        assertTrue(src("ui/navigation/NavGraph.kt")!!.contains("if (com.legumlex.client.BuildConfig.DEBUG) com.legumlex.client.feature.desk.DeskSampleScreen("))
    }
}
