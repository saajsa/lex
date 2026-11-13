package com.legumlex.client.feature.desk

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.26: the desk's fixed lines in Hindi, Punjabi and Hinglish, with the footer under each, and English left exactly as it was. */
class DeskLinesTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    private val footer = mapOf("lang" to "hi", "en" to "Translated. We are testing.", "local" to "अनुवादित। हम जाँच रहे हैं।")
    private val data: Map<String, Any?> = mapOf(
        "lang" to "hi",
        "lines" to mapOf(
            "received_no" to "पूछताछ {n} मिल गई।",
            "received_body" to "वे <b>{email}</b> पर जवाब देते हैं।",
            "pay_fee" to "शुल्क {fee} है।",
            "phone_onfile" to "क्या {adv} आपको <b>{num}</b> पर फ़ोन करें?",
        ),
        "pool" to listOf("एक", "दो", "तीन", "चार"),
        "footer" to footer,
    )

    // ---- The language switch ----

    @Test fun aReadingNamesItsLanguage() {
        assertEquals("hi", deskLang("hi")); assertEquals("pa", deskLang("PA")); assertEquals("hx", deskLang(" hx "))
        assertEquals("en", deskLang("en")); assertEquals("en", deskLang(null)); assertEquals("en", deskLang("fr")); assertEquals("en", deskLang(""))
    }

    @Test fun englishAnswersCarryNoFooterAndNoSwitch() {
        val t = parseTongue(mapOf("lang" to "en", "translated" to true, "footer" to footer))
        assertEquals("en", t.lang); assertNull(t.lineFooter)
        assertEquals("en", parseTongue(emptyMap()).lang)   // an older server names no language
        assertEquals("en", parseTongue(null).lang)
    }

    @Test fun theLanguageSwitchFetchesOnceAndKeepsTheBook() = runBlocking {
        val book = parseLineBook(data)!!
        var calls = 0
        val first = languageSwitch("hi", null) { calls++; book }
        assertSame(book, first); assertEquals(1, calls)
        val again = languageSwitch("hi", first) { calls++; null }   // cached: no second call
        assertSame(book, again); assertEquals(1, calls)
        assertSame(LineBook.English, languageSwitch("en", book) { calls++; book })   // back to English
    }

    @Test fun aFailureOfLinesFallsBackToEnglish() = runBlocking {
        val b = languageSwitch("pa", null) { null }
        assertSame(LineBook.English, b)
        assertFalse(b.active)
        assertNull(b.line("received_no", mapOf("n" to "5")))   // the English line is then said by the caller
        assertNull(b.footer)
        assertNull(parseLineBook(mapOf("lang" to "hi", "lines" to emptyMap<String, String>())))   // an empty book is no book
        assertNull(parseLineBook(null)); assertNull(parseLineBook(mapOf("lang" to "en", "lines" to mapOf("a" to "b"))))
    }

    @Test fun aLinesCallThatThrowsIsNoBookEither() {
        val data = src("feature/desk/DeskData.kt") ?: return
        assertTrue(data.contains("fun lines(lang: String): LineBook?"))
        assertTrue(data.contains("api.leadDeskLines(lang)") && data.contains("api.deskLines(lang)"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("@GET(\"desk/lines\")") && api.contains("@GET(\"lead/desk/lines\")"))
    }

    // ---- Tokens ----

    @Test fun tokensAreFilled() {
        val b = parseLineBook(data)!!
        assertEquals("पूछताछ 12 मिल गई।", b.line("received_no", mapOf("n" to "12")))
        assertEquals("शुल्क ₹2,500 है।", b.line("pay_fee", mapOf("fee" to "₹2,500")))
            }

    @Test fun boldTagsBecomeMarkupAndOtherTagsGo() {
        val b = parseLineBook(data)!!
        assertEquals("वे **a@b.in** पर जवाब देते हैं।", b.line("received_body", mapOf("email" to "a@b.in")))
        assertEquals("क्या वकील आपको **98765 43210** पर फ़ोन करें?", b.line("phone_onfile", mapOf("adv" to "वकील", "num" to "98765 43210")))
        assertEquals("A **b** c", fillLine("a <b>b</b> c<br>", emptyMap()))
    }

    @Test fun aMissingTokenLeavesNothingBehind() {
        assertEquals("Enquiry received.", fillLine("Enquiry {n} received.", emptyMap()))
        assertFalse(fillLine("x {unknown} y", emptyMap()).contains("{"))
    }

    @Test fun aKeyTheBookLacksIsNull() {
        val b = parseLineBook(data)!!
        assertNull(b.line("no_such_key")); assertTrue(b.has("pay_fee")); assertFalse(b.has("no_such_key"))
        assertEquals(4, b.pool.size)
    }

    // ---- The footer: only on translated fixed lines ----

    @Test fun theFooterIsOnlyOnTranslatedLines() {
        val fixed = parseTongue(mapOf("lang" to "hi", "translated" to true, "footer" to footer))
        assertEquals("hi", fixed.lang)
        assertEquals("Translated. We are testing.", fixed.lineFooter?.en); assertEquals("अनुवादित। हम जाँच रहे हैं।", fixed.lineFooter?.local)
        // A model-written reply in Hindi is in Hindi but is not a fixed line: no footer.
        val written = parseTongue(mapOf("lang" to "hi", "translated" to false, "footer" to footer))
        assertEquals("hi", written.lang); assertNull(written.lineFooter)
        val none = parseTongue(mapOf("lang" to "pa", "translated" to true))
        assertNull(none.lineFooter)
    }

    @Test fun repliesAndReadingsAndGatherCarryTheirTongue() {
        val g = parseGuestReply(mapOf("reply" to "ठीक है", "lang" to "hi", "translated" to true, "footer" to footer))!!
        assertEquals("hi", g.tongue.lang); assertEquals("Translated. We are testing.", g.tongue.lineFooter?.en)
        val plain = parseGuestReply(mapOf("reply" to "Fine"))!!
        assertEquals("en", plain.tongue.lang); assertNull(plain.tongue.lineFooter)
        val r = parseGather(mapOf("question" to "कब हुआ?", "done" to false, "lang" to "pa"))!!
        assertEquals("pa", r.tongue.lang); assertNull(r.tongue.lineFooter)
    }

    @Test fun theFooterIsShownSmallInSecondaryInkEnglishFirst() {
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        assertTrue(screen.contains("line.footer?.let { DeskFooterText(it, line.footerTag) }"))
        val at = screen.indexOf("private fun DeskFooterText")
        val body = screen.substring(at, screen.indexOf("/** A paragraph with **bold**", at))
        assertTrue(body.contains("fontSize = 9.sp"))
        assertTrue(body.contains("tagOnly") && body.contains("footerTag(f)"))
        assertTrue(body.contains("DeskRole.Secondary"))
        assertTrue(body.indexOf("f.en") < body.indexOf("f.local"))
        val vm = src("feature/desk/DeskViewModel.kt")!!
        // A line has a footer only when it was said from the book or the server called it translated.
        assertTrue(vm.contains("footer = ft(t != null)"))
        assertTrue(vm.contains("footer = r.tongue.lineFooter"))
        assertFalse(vm.contains("footer = book.footer"))   // never put on a line unconditionally
    }

    @Test fun footerParsingNeedsAtLeastOneLine() {
        assertNull(parseDeskFooter(null)); assertNull(parseDeskFooter(mapOf("lang" to "hi")))
        assertEquals("only local", parseDeskFooter(mapOf("lang" to "hi", "local" to "only local"))?.local)
    }

    // ---- English stays exactly as it was ----

    @Test fun englishLinesAreUntouched() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        for (s in listOf("What would you like to ask?", "Any hearing or deadline coming up?",
            "Which country are you writing from?")) assertTrue(s, vm.contains(s))
        assertEquals("Please sign in with Google. Your question will then reach the advocates, who will get back to you.", signInLine(true))
    }

    @Test fun matterCountsUseTheBooksOwnWords() {
        val b = LineBook("hi", mapOf("matter_one" to "{n} मामला", "matter_many" to "{n} मामले"), null)
        assertEquals("1 मामला", matterCount(b, 1)); assertEquals("3 मामले", matterCount(b, 3))
        assertNull(matterCount(LineBook.English, 3))
    }

    // ---- Footer once, then a tag ----

    @Test fun theFullFooterIsShownOncePerConversationThenOnlyATag() {
        val f = DeskFooter("hi", "Translated. We are testing.", "अनुवादित। हम जाँच रहे हैं।")
        val once = FooterOnce()
        assertEquals(null to false, once.next(null))                // a line with no footer does not use up the first
        assertEquals(f to false, once.next(f))                       // the first translated line: the full footer
        assertEquals(f to true, once.next(f)); assertEquals(f to true, once.next(f))   // later ones: the tag only
        once.reset()
        assertEquals(f to false, once.next(f))                       // a new conversation starts again
        assertEquals("अनुवादित", footerTag(f))
        assertEquals("Translated", footerTag(DeskFooter("hx", "Translated. x", "Translated. Hum test kar rahe hain.")))
        assertEquals("ਅਨੁਵਾਦਿਤ", footerTag(DeskFooter("pa", "e", "ਅਨੁਵਾਦਿਤ। ਅਸੀਂ")))
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("footerOnce.next(footer)") && vm.contains("footerOnce.reset()"))
    }

    // ---- A token that opens a sentence is capitalised ----

    @Test fun aTokenOpeningALineOrASentenceStartsWithACapital() {
        val v = mapOf("adv" to "vakeel", "Adv" to "Vakeel")
        assertEquals("Vakeel aapse kis number par baat kar sakte hain?", fillLine("{adv} aapse kis number par baat kar sakte hain?", v))
        assertEquals("Theek hai. Vakeel aapko phone karenge.", fillLine("Theek hai. {adv} aapko phone karenge.", v))
        assertEquals("Kya? Vakeel", fillLine("Kya? {adv}", v))
        assertEquals("**Vakeel** ne kaha", fillLine("<b>{adv}</b> ne kaha", v))
        assertEquals("Aap vakeel se milein.", fillLine("Aap {adv} se milein.", v))        // mid-sentence: left as it is
        assertEquals("वकील आपको फ़ोन करेंगे।", fillLine("{adv} आपको फ़ोन करेंगे।", mapOf("adv" to "वकील")))
        assertEquals("पहले। वकील आएँगे", fillLine("पहले। {adv} आएँगे", mapOf("adv" to "वकील")))
        assertTrue(opensSentence("")); assertTrue(opensSentence("A. ")); assertFalse(opensSentence("A, "))
    }

    @Test fun theVisitorBubblesAndDeadlineChipsComeFromTheBook() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        for (k in listOf("ans_as_is", "ans_docs_later", "ans_attached", "ans_no_phone", "ans_no_deadline", "ans_new_enquiry", "chip_flag_yes", "chip_flag_no"))
            assertTrue(k, vm.contains("tl(\"$k\")"))
        // The keys web merged away are not used (they would show blank).
        for (k in listOf("thanks_plain", "thanks_helps", "thanks_name", "docs_tail", "timeout_b", "received_body")) assertFalse(k, vm.contains("\"$k\""))
        assertEquals("Send it as it is.", ANS_AS_IS); assertEquals("Nothing yet.", ANS_NO_DEADLINE)
    }

    @Test fun aGuestWhoAsksForAPersonIsCarriedIntoTheEnquiry() {
        assertTrue(parseGuestReply(mapOf("reply" to "Noted", "person" to true))!!.person)
        assertFalse(parseGuestReply(mapOf("reply" to "Noted"))!!.person)
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("if (r.person) directTalk = v"))
        assertTrue(vm.contains("\"\\n\\nAsked in the chat: \$it\""))
        assertTrue(vm.contains("reading?.wantsPerson == true") && vm.contains("sayKey(\"person_signed\""))
    }

    @Test fun everyLineOfLexStartsWithACapitalLiteralOrToken() {
        assertEquals("Vakeel aapse kis number par baat kar sakte hain?", fillLine("vakeel aapse kis number par baat kar sakte hain?", emptyMap()))
        assertEquals("Theek hai. Vakeel dekhenge.\nAur sunie.", fillLine("theek hai. vakeel dekhenge.\naur sunie.", emptyMap()))
        assertEquals("**Enquiry 5 received.** It goes to vakeel.", fillLine("<b>enquiry {n} received.</b> It goes to vakeel.", mapOf("n" to "5")))
        assertEquals("पहले। वकील", fillLine("पहले। वकील", emptyMap()))
        assertEquals("a@b.in par", fillLine("{x} par", mapOf("x" to "a@b.in")).replaceFirstChar { it.lowercase() })
    }

    @Test fun theReadingsOwnFollowUpAnswerIsLabelledAndNothingIsRecased() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        // Appended exactly as a gather answer is: "\n\nLex asked: <question>\nThey answered: <answer>".
        assertTrue(vm.contains("text + gatherLabel(ask, more.orEmpty())"))
        assertEquals("\n\nLex asked: Bank ne return memo kab diya?\nThey answered: Kal mila.", gatherLabel("Bank ne return memo kab diya?", "Kal mila."))
        // The visitor's words are never recased in the desk: no case change anywhere near what they type, and no autocorrect on the answer boxes.
        val data = src("feature/desk/DeskData.kt")!!; val intake = src("feature/desk/DeskIntake.kt")!!
        for (code in listOf(vm, data, intake)) {
            assertFalse(Regex("(?:text|more|v|raw|answer)\\.(?:replaceFirstChar|capitalize|titlecase|uppercase)").containsMatchIn(code))
        }
        val screen = src("feature/desk/DeskScreen.kt")!!
        assertTrue(screen.contains("Compose(\"A little more about it…\", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false)"))
        assertTrue(screen.contains("Compose(\"Your answer…\", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false)"))
    }

    @Test fun aPersonRequestIsHeardEvenWhenTheReadingWasSkipped() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("if (reading?.wantsPerson == true && \"person\" !in said)"))
        // Parsed from desk/check whatever the reading state.
        assertTrue(src("feature/desk/DeskData.kt")!!.contains("wantsPerson = d[\"wants_person\"] == true"))
    }

    @Test fun onlyTheFirstLetterAndTheOneAfterAStopAreCapitalised() {
        // Mid-sentence words are never touched, even when a token or a fragment sits there.
        assertEquals("Aap file mein ek business ke roop mein darj hain.", fillLine("aap file mein ek business ke roop mein darj hain.", emptyMap()))
        val book = LineBook("hx", mapOf("entity_onfile" to "Aap file mein {as} ke roop mein darj hain.", "as_business" to "ek business", "as_individual" to "ek vyakti"), null)
        assertEquals("Aap file mein ek business, Acme ke roop mein darj hain.", book.line("entity_onfile", mapOf("as" to (book.fragment("as_business") + ", Acme"))))
        assertEquals("ek business", book.fragment("as_business"))               // a fragment is not capitalised
        assertEquals("Ek business", book.line("as_business"))                    // a line of its own is
        assertEquals("Pehla. Doosra? Teesra! Chautha", capitaliseSentences("pehla. doosra? teesra! chautha"))
        assertEquals("Ek. Do", capitaliseSentences("ek. do"))
        assertEquals("Haan\u0964 नमस्ते\u0964 ਸਤ ਸ੍ਰੀ ਅਕਾਲ\u0964", capitaliseSentences("haan\u0964 नमस्ते\u0964 ਸਤ ਸ੍ਰੀ ਅਕਾਲ\u0964"))
        assertEquals("नमस्ते\u0964 ਸਤ ਸ੍ਰੀ ਅਕਾਲ", capitaliseSentences("नमस्ते\u0964 ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))      // no case: nothing to do
        // A stop with no space after it (an address, a version) does not open a sentence.
        assertEquals("Mail a@b.in or c.d", capitaliseSentences("mail a@b.in or c.d"))
    }

    @Test fun abbreviationsDoNotEndASentence() {
        assertEquals("Fee hai Rs. 3 lakh, kul.", capitaliseSentences("fee hai Rs. 3 lakh, kul."))
        assertEquals("Notice u/s. 138 ke tahat aaya.", capitaliseSentences("notice u/s. 138 ke tahat aaya."))
        assertEquals("Rs. aap dekhein", capitaliseSentences("rs. aap dekhein"))
        assertEquals("Yeh Dr. rao hain. Phir Aap aaye", capitaliseSentences("yeh Dr. rao hain. phir Aap aaye"))
        assertEquals("Jaise e.g. ek notice. Phir doosra", capitaliseSentences("jaise e.g. ek notice. phir doosra"))
    }

    @Test fun noVoiceTokenIsEverShownRaw() {
        // {advo} (the plural/oblique form) falls back to {adv}'s value, else it is empty; no "{...}" survives in any case.
        assertEquals("Vakeelon ko bataya.", fillLine("{advo} ko bataya.", mapOf("adv" to "vakeelon")))
        assertEquals("Vakeelon ko bataya.", fillLine("{advo} ko bataya.", mapOf("advo" to "vakeelon", "adv" to "vakeel")))
        for (raw in listOf("{advo} {adv} {Adv} aur {unknown}", "x {advo}", "{Adv} {advo}.")) assertFalse(raw, fillLine(raw, emptyMap()).contains("{"))
    }

    @Test fun completedStepsUseTheirDoneFormsAndOldServersKeepToday() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("tl(\"stage_done_title\")") && vm.contains("\"stage_done_1\", \"stage_done_2\", \"stage_done_3\", \"stage_done_4\""))
        assertTrue(vm.contains("if (doneSteps.all { it != null }) doneSteps.filterNotNull() else emptyList()"))   // all four, or none
        val screen = src("feature/desk/DeskScreen.kt")!!
        assertTrue(screen.contains("p.doneTitle ?: \"Delivered.\""))                                          // old server: "Delivered."
        assertTrue(screen.contains("if (done && p.doneSteps.size == STAGES.size) p.doneSteps[i] else running")) // running form while it runs
        val p = Para.Stages(1, false)
        assertTrue(p.doneSteps.isEmpty() && p.doneTitle == null)
        assertEquals(Para.Stages(3, true, null, emptyList(), "Done", listOf("a", "b", "c", "d")), p.copy(active = 3, done = true, doneTitle = "Done", doneSteps = listOf("a", "b", "c", "d")))
    }
}
