package com.legumlex.client.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFitTest {
    @Test fun a_title_that_fits_keeps_its_size() {
        assertEquals(28f, fitTitleSize(28f, 150f, 167f, 20f), 0f)
        assertEquals(28f, fitTitleSize(28f, 167f, 167f, 20f), 0f)
    }

    @Test fun a_title_a_little_too_wide_shrinks_in_proportion() {
        // LITIGATION at 28 sp measuring 176 against 167 available.
        val s = fitTitleSize(28f, 176f, 167f, 20f)
        assertTrue(s < 28f)
        assertTrue("shrunk size ${s} must fit: ${176f * s / 28f} <= 167", 176f * s / 28f <= 167f)
    }

    @Test fun it_never_goes_below_the_minimum() {
        assertEquals(20f, fitTitleSize(28f, 400f, 100f, 20f), 0f)
    }

    @Test fun nothing_measured_changes_nothing() {
        assertEquals(28f, fitTitleSize(28f, 0f, 167f, 20f), 0f)
        assertEquals(28f, fitTitleSize(28f, 176f, 0f, 20f), 0f)
    }

    @Test fun repeated_layouts_settle() {
        var s = 28f
        repeat(4) { s = minOf(s, fitTitleSize(s, 176f * s / 28f, 167f, 20f)) }
        assertTrue(176f * s / 28f <= 167f)
        assertTrue(s >= 20f)
    }
}
