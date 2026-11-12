package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-014 014.9: the desk's counsel turns, the wish rules, the forms' hrefs and the counsel closing. */
class DeskCounselTest {
    // The server's own lists (lexon Enrolment).
    private val account = listOf("\\b(open|create|start)\\b.{0,20}\\baccount\\b", "\\bbecome (a )?client\\b", "\\bsign ?up\\b", "\\bregister (me|myself|with (you|the firm))\\b")
    private val counsel = listOf(
        "\\b(register|join|sign ?up|sign (me )?up|enrol|enroll|onboard)\\b.{0,25}\\bas\\s+(an?\\s+|the\\s+)?(counsel|advocate|lawyer|attorney)\\b",
        "\\b(counsel|advocate)\\s+(workspace|account|registration|sign ?up)\\b",
    )
    private val client = listOf("\\bbecome (a )?client\\b", "\\bclient account\\b", "\\bas (a )?client\\b")

    private fun wish(text: String, counselOn: Boolean = true, signUp: Boolean = true) = deskWish(text, account, counsel, client, counselOn, signUp)

    @Test fun aClearCounselWishIsCounsels() { assertEquals(WishKind.Counsel, wish("register me as an advocate")) }
    @Test fun aClearClientWishIsClients() { assertEquals(WishKind.Client, wish("I want to open an account")) }
    @Test fun explicitClientWordsWin() { assertEquals(WishKind.Client, wish("open a client account for my advocate")) }
    @Test fun aWishThatNamesACounselIsUnsure() { assertEquals(WishKind.Unsure, wish("open an account to hire an advocate")) }
    @Test fun anythingElseIsNothing() { assertEquals(WishKind.None, wish("my landlord will not return the deposit")) }
    @Test fun bankAccountsAreNotOurs() { assertEquals(WishKind.None, wish("open a bank account")) }

    @Test fun withCounselOffACounselWishIsThePointerToTheForm() {
        assertEquals(WishKind.CounselForm, wish("register me as an advocate", counselOn = false))
        // An unsure one is a client's, as before; a client wish is unchanged.
        assertEquals(WishKind.Client, wish("open an account to hire an advocate", counselOn = false))
        assertEquals(WishKind.Client, wish("I want to open an account", counselOn = false))
    }

    @Test fun withClientSignUpShutAClientWishIsNotTakenUpButAnUnsureOneIsAsked() {
        assertEquals(WishKind.None, wish("I want to open an account", signUp = false))
        assertEquals(WishKind.Unsure, wish("open an account to hire an advocate", signUp = false))
        assertEquals(WishKind.Counsel, wish("register me as an advocate", signUp = false))
    }

    @Test fun theClarifyingAnswerIsReadLikeTheWebs() {
        assertEquals(WishKind.Client, clarifyAnswer("clarify:client"))
        assertEquals(WishKind.Counsel, clarifyAnswer("clarify:counsel"))
        assertEquals(WishKind.Client, clarifyAnswer("I am a client"))
        // "I need an advocate" is a client's; "advocate" alone is counsel's; nonsense is unclear.
        assertEquals(WishKind.Client, clarifyAnswer("I need an advocate"))
        assertEquals(WishKind.Counsel, clarifyAnswer("advocate"))
        assertNull(clarifyAnswer("hmm"))
        assertNull(clarifyAnswer(""))
    }

    @Test fun theFormsHrefsOpenTheirOwnScreens() {
        assertEquals(ChipTarget.ClientForm, chipTarget("app:lead_onboard"))
        assertEquals(ChipTarget.CounselForm, chipTarget("app:lead_counsel"))
        assertEquals(ChipTarget.Url("https://example.test/x"), chipTarget("https://example.test/x"))
    }

    private fun turn(step: String, extra: Map<String, Any?> = emptyMap()) =
        mapOf("step" to step, "say" to listOf("A line."), "chips" to listOf(mapOf("id" to "kind:counsel", "label" to "Myself, As An Advocate")), "dock" to mapOf("kind" to "none")) + extra

    @Test fun aCounselTurnCarriesItsTrackAndItsChips() {
        val t = parseTurn(turn("kind", mapOf("track" to "counsel")))
        assertEquals("counsel", t.track)
        assertEquals("kind:counsel", t.chips.single().id)
        assertEquals("Myself, As An Advocate", t.chips.single().label)
        assertEquals("", parseTurn(turn("kind")).track)
    }

    @Test fun theReviewCardShowsThePractisingStateSelect() {
        val t = parseTurn(turn("review", mapOf("track" to "counsel", "dock" to mapOf(
            "kind" to "card", "button" to "Continue",
            "fields" to listOf(
                mapOf("name" to "full_name", "label" to "Full Name", "value" to "A B"),
                mapOf("name" to "pan_number", "label" to "PAN", "value" to "ABCDE1234F", "locked" to true),
                mapOf("name" to "state", "label" to "State You Practise In", "type" to "select", "options" to listOf(mapOf("value" to "PB", "label" to "Punjab"))),
            ),
        ))))
        val card = t.dock as AcctDock.Card
        assertTrue(card.fields.first { it.name == "pan_number" }.locked)
        assertEquals(listOf("PB" to "Punjab"), card.fields.first { it.name == "state" }.select)
    }

    @Test fun theFixedTurnsOfTheDeskConfigAreDrawnAsTurns() {
        val offer = fixedTurn(mapOf("step" to "counsel_offer", "say" to listOf("Shall we?"), "chips" to listOf(mapOf("id" to "open_counsel", "label" to "Register As Counsel"), mapOf("id" to "decline", "label" to "No, I Have A Question"))))
        assertEquals(listOf("open_counsel", "decline"), offer?.chips?.map { it.id })
        val forms = fixedTurn(mapOf("step" to "forms", "say" to listOf("Here are the forms."), "chips" to listOf(
            mapOf("id" to "forms_client", "label" to "Open An Account", "href" to "app:lead_onboard"),
            mapOf("id" to "forms_counsel", "label" to "Register As Counsel", "href" to "app:lead_counsel"),
        )))
        assertEquals(listOf("app:lead_onboard", "app:lead_counsel"), forms?.chips?.map { it.href })
        assertNull(fixedTurn(null)); assertNull(fixedTurn(emptyMap()))
    }

    @Test fun theGuestAnswerCarriesTheCounselWishTheClarifyAndTheForms() {
        val counselWish = parseGuestReply(mapOf("reply" to "Sign in.", "counsel" to true, "account" to true, "sign_in" to true))!!
        assertTrue(counselWish.counsel); assertTrue(counselWish.signIn)
        // The clarifying question never sends the visitor to sign in: the text box stays.
        val clarify = parseGuestReply(mapOf("reply" to "Client or advocate?", "clarify" to true, "sign_in" to true))!!
        assertTrue(clarify.clarify); assertFalse(clarify.signIn)
        val forms = parseGuestReply(mapOf("reply" to "Use a form.", "forms" to listOf(mapOf("label" to "Open An Account", "href" to "app:lead_onboard"), mapOf("label" to "Register As Counsel", "href" to "app:lead_counsel"))))!!
        assertEquals(listOf("Open An Account" to "app:lead_onboard", "Register As Counsel" to "app:lead_counsel"), forms.forms)
        assertFalse(forms.signIn)
        // A plain answer is as before.
        assertFalse(parseGuestReply(mapOf("reply" to "Hello", "left" to 1.0))!!.counsel)
        assertNull(parseGuestReply(mapOf("reply" to "")))
    }

    @Test fun theCounselClosingCarriesTheClientToken() {
        val opened = parseSubmit(mapOf(
            "status" to "counsel_open", "role" to "client", "token" to "jwt.token.here", "client_id" to 41.0, "contact_id" to "77",
            "turn" to turn("sent", mapOf("sent" to true, "track" to "counsel")),
        ))
        assertEquals(CounselSession("jwt.token.here", 41, 77), opened.opened)
        assertTrue(opened.turn!!.sent)
        // A client's registration (awaiting_approval) opens nothing and keeps the lead's session.
        assertNull(parseSubmit(mapOf("status" to "awaiting_approval", "turn" to turn("sent", mapOf("sent" to true)))).opened)
        // A refusal's turn (no token) opens nothing.
        assertNull(parseSubmit(mapOf("turn" to turn("review"))).opened)
    }
}
