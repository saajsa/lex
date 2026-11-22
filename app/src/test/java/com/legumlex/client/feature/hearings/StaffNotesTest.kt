package com.legumlex.client.feature.hearings

import com.legumlex.client.feature.cases.CaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** REQ-039 B5: staff notes never reach the client; the recorded outcome shows instead. */
class StaffNotesTest {
    @Test fun outcomeLine() {
        assertEquals("Outcome: Heard", outcomeLine("Heard"))
        assertEquals("Outcome: No Appearance", outcomeLine(" No Appearance "))
        assertNull(outcomeLine(null)); assertNull(outcomeLine(" "))
    }

    @Test fun disposedCasesFallUnderDisposed() {
        assertEquals(CaseStatus.CLOSED, CaseStatus.from("disposed"))
        assertEquals(CaseStatus.ONGOING, CaseStatus.from("active"))
    }

    @Test fun noParserReadsTheNotes() {
        val root = listOf("src/main/java/com/legumlex/client/feature", "app/src/main/java/com/legumlex/client/feature").map(::File).first { it.exists() }
        for (f in listOf("hearings/HearingsRepository.kt", "cases/CasesRepository.kt")) {
            val s = File(root, f).readText()
            assertFalse(f, s.contains("\"adjournment_reason\""))
        }
        assertFalse(File(root, "hearings/HearingsRepository.kt").readText().contains("\"description\""))
    }
}
