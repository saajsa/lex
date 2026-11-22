package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-038 phone dry run: the short credits note, the lapse line's colour rule, the source label. */
class DryRunFixesTest {
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun creditsNoteIsShort() {
        assertEquals("Board and cause list free. Court reads cost credits. Paid credits never expire.", CREDITS_NOTE)
        assertFalse(CREDITS_NOTE.contains("—"))
    }

    @Test fun lapseRedOnlyWithin30Days() {
        assertTrue(lapseIsNear(NextLapse(10, "2026-12-22", "trial"), today))
        assertTrue(lapseIsNear(NextLapse(10, "2026-11-22 10:00:00", "trial"), today))
        assertFalse(lapseIsNear(NextLapse(10, "2026-12-23", "trial"), today))
        assertFalse(lapseIsNear(null, today))
        assertFalse(lapseIsNear(NextLapse(10, "", "trial"), today))
    }

    @Test fun sourceLabels() {
        assertEquals("CNR", sourceLabel("cnr"))
        assertEquals("Import", sourceLabel("import"))
        assertEquals("By Hand", sourceLabel("hand"))
        assertEquals("Sheet", sourceLabel("sheet"))
        assertEquals("Other", sourceLabel("other"))
    }
}
