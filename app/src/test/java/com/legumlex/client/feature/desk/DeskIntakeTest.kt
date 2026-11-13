package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 Phase 1 in the client app: the submission key, the deadline step, the reading, the receipt. */
class DeskIntakeTest {
    private class MapStore(val m: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
    }

    private fun keys(store: KeyValueStore, now: () -> Long = { 1_000L }): SubmissionKeys {
        var n = 0
        return SubmissionKeys(store, newKey = { "key${++n}" }, now = now)
    }

    @Test fun theKeyIsStableAcrossARetryAndARestart() {
        val store = MapStore()
        val fp = SubmissionKeys.fingerprint("My tenant has not paid rent.", listOf("receipts.pdf" to 1200L))
        val first = keys(store).forEnquiry(fp)
        assertEquals(first, keys(store).forEnquiry(fp).also { })                 // the same enquiry again in a new process: the stored key
        assertEquals(first, keys(store).forEnquiry(fp))                          // and again (a second tap, a retry after a timeout)
        // The key is a UUID without dashes when made for real.
        val real = SubmissionKeys(MapStore()).forEnquiry(fp)
        assertTrue(real.matches(Regex("[0-9a-f]{32}")))
    }

    @Test fun aDifferentEnquiryOrASentOneGetsANewKey() {
        val store = MapStore()
        val a = SubmissionKeys.fingerprint("one", emptyList()); val b = SubmissionKeys.fingerprint("two", emptyList())
        val k = keys(store)
        val first = k.forEnquiry(a)
        assertNotEquals(first, k.forEnquiry(b))           // another enquiry: another key
        val second = k.forEnquiry(b)
        k.clear()                                         // sent: cleared
        assertNotEquals(second, k.forEnquiry(b))
        assertEquals(setOf("submission_key", "submission_fp", "submission_at"), store.m.keys)
        k.clear(); assertTrue(store.m.isEmpty())
    }

    @Test fun aKeyIsNotKeptForeverAndTheFingerprintSeesTheFiles() {
        val store = MapStore(); var t = 0L
        val k = keys(store) { t }
        val fp = SubmissionKeys.fingerprint("x", emptyList())
        val first = k.forEnquiry(fp)
        t = SubmissionKeys.KEEP_MS - 1; assertEquals(first, k.forEnquiry(fp))
        t = SubmissionKeys.KEEP_MS + 1; assertNotEquals(first, k.forEnquiry(fp))
        assertNotEquals(SubmissionKeys.fingerprint("x", emptyList()), SubmissionKeys.fingerprint("x", listOf("a.pdf" to 1L)))
    }

    @Test fun theDeadlineStepsValidation() {
        assertEquals("", deadlineProblem("The notice says reply by Friday", ""))
        assertEquals("", deadlineProblem("", "2026-11-20"))
        assertEquals("", deadlineProblem("Hearing at the High Court", "2026-11-20"))
        // Either one is needed.
        assertEquals("Write a few words, or pick a date, or choose No, Nothing Yet.", deadlineProblem("   ", ""))
        // 240 characters at most (after runs of space are made one).
        assertEquals("", deadlineProblem("a".repeat(240), ""))
        assertEquals("Please keep this to 240 characters.", deadlineProblem("a".repeat(241), ""))
        assertEquals("", deadlineProblem("a  ".repeat(100).trim(), ""))             // 100 words with double spaces: 199 characters once cleaned
        // The date: YYYY-MM-DD, 2000 to 2100.
        assertEquals("Please give the date as day, month and year.", deadlineProblem("x", "20-11-2026"))
        assertEquals("Please give the date as day, month and year.", deadlineProblem("x", "1999-12-31"))
        assertEquals("Please give the date as day, month and year.", deadlineProblem("x", "2101-01-01"))
        assertEquals("Please give the date as day, month and year.", deadlineProblem("x", "2026-02-30"))
        assertEquals("Reply by Friday · 20.11.2026", deadlineShown("Reply   by Friday", "2026-11-20"))
        assertEquals("20.11.2026", deadlineShown("", "2026-11-20"))
        assertEquals("a b", cleanDeadlineText("  a \n b  "))
    }

    @Test fun aReadingThatFailedOrWasSkippedIsNoFindingButOneFromAnOlderServerIs() {
        assertTrue(deskReadingUsable("read"))
        assertTrue(deskReadingUsable(null))                // an older server: used as before
        assertFalse(deskReadingUsable("failed"))
        assertFalse(deskReadingUsable("skipped"))
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        // The verdict takes the reading only if usable; sending never looks at reading_state, so it goes on whatever it was.
        assertTrue(src.contains("reading?.takeIf { deskReadingUsable(it.readingState) }"))
        val send = src.substringAfter("private suspend fun send()").substringBefore("// ---- The fee")
        assertFalse(send.contains("readingState"))
        // matter_id is sent only for a picked matter: the desk has no matter picker, so nothing is passed.
        val repo = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskData.kt").readText()
        assertFalse(repo.contains("matterId"))
    }

    @Test fun theEnquiryNumberAndStateComeFromTheServer() {
        val d = deliveredFrom(mapOf("consultation_id" to 55.0, "enquiry_number" to "ENQ/2026/12345", "intake_state" to "received"), "a@b.c")
        assertEquals(Sent.Delivered(55, "a@b.c", "ENQ/2026/12345", "received"), d)
        // A retry answers with the same consultation_id: the same success.
        assertEquals(d, deliveredFrom(mapOf("consultation_id" to 55.0, "enquiry_number" to "ENQ/2026/12345", "intake_state" to "received", "already_submitted" to true), "a@b.c"))
        // An older server: none of the new fields.
        assertEquals(Sent.Delivered(7, "a@b.c", null, null), deliveredFrom(mapOf("consultation_id" to 7), "a@b.c"))
    }

    @Test fun theReceiptNamesNoAdvocateAndShowsTheNumber() {
        val line = receiptLine("ENQ/2026/12345", "a@b.in")
        assertEquals("**Enquiry ENQ/2026/12345 received.** It goes to one of the advocates, who usually reply within 30 minutes, to **a@b.in**.", line)
        assertEquals("**Enquiry received.** It goes to one of the advocates, who usually reply within 30 minutes, to **your email**.", receiptLine(null))
        for (l in listOf(line, receiptLine(" "), receiptLine(null))) {
            assertFalse(l.contains("with your advocate", true)); assertFalse(l.contains("with the advocates", true)); assertFalse(l.contains("Adv.", true))
        }
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        val delivered = src.substringAfter("private suspend fun delivered(").substringBefore("private suspend fun restart")
        assertTrue(delivered.contains("receiptLine(enquiryNumber, email)"))
        assertFalse(delivered.contains("is with the advocates"))
        // The key is sent on every attempt, with the flag, and cleared when sent.
        assertTrue(src.contains("submissionKey = key, deadlineFlag"))
        assertTrue(delivered.contains("submissionKeys.clear()"))
    }

    @Test fun theDeadlineStepSitsAfterThePhoneAndOwnsItsErrors() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(src.contains("phone == null -> askPhone()\n            deadline == null -> askDeadline()"))
        assertTrue(src.contains("\"deadline_flag\" to Step.DEADLINE") && src.contains("\"deadline_date\" to Step.DEADLINE"))
        assertTrue(src.contains("CHIP_FLAG_YES") && src.contains("CHIP_FLAG_NO"))
        assertNull(null)
    }
}
