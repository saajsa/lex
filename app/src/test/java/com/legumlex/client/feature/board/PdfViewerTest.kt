package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-038 order viewer (owner 23 Nov): the zoom maths and the page-index logic. */
class PdfViewerTest {
    @Test fun zoomIsClampedOneToFive() {
        assertEquals(1f, PdfMaths.clamp(0.4f))
        assertEquals(5f, PdfMaths.clamp(9f))
        assertEquals(2.2f, PdfMaths.clamp(2.2f))
    }

    @Test fun doubleTapTogglesOneAndTwoAndAHalf() {
        assertEquals(2.5f, PdfMaths.doubleTap(1f))
        assertEquals(1f, PdfMaths.doubleTap(2.5f))
        assertEquals(1f, PdfMaths.doubleTap(4f))
    }

    @Test fun buttonStepsStayInRange() {
        assertEquals(1.5f, PdfMaths.step(1f, true))
        assertEquals(1f, PdfMaths.step(1.2f, false))
        assertEquals(5f, PdfMaths.step(4f, true))
        assertEquals("250%", PdfMaths.percent(2.5f))
        assertEquals("100%", PdfMaths.percent(1f))
    }

    @Test fun theFocusPointStaysStill() {
        // A page top at 100 px, focus at 400: doubling moves the top to 400 - 300 * 2 = -200, so the focus row is still under 400.
        assertEquals(-200f, PdfMaths.anchoredTop(400f, 100f, 2f))
        // Scrolled 0, focus x 300, doubling: content point 300 becomes 600, so scroll to 300.
        assertEquals(300f, PdfMaths.anchoredScrollX(0f, 300f, 2f))
        assertEquals(0f, PdfMaths.anchoredScrollX(0f, 300f, 0.5f))
    }

    @Test fun fitWidthAndFitPage() {
        assertEquals(1080f, PdfMaths.baseWidth(false, 1080f, 2000f, 595, 842))
        // A4 on a 1080 x 1200 viewport: the page height decides, 1200 * 595 / 842.
        assertEquals(1200f * 595 / 842, PdfMaths.baseWidth(true, 1080f, 1200f, 595, 842), 0.01f)
        // A tall viewport: width still decides.
        assertEquals(1080f, PdfMaths.baseWidth(true, 1080f, 2400f, 595, 842))
    }

    @Test fun renderingStepsAndTheMemoryCap() {
        assertEquals(1, PdfMaths.bucket(1f))
        assertEquals(2, PdfMaths.bucket(1.6f))
        assertEquals(3, PdfMaths.bucket(2.5f))
        assertEquals(5, PdfMaths.bucket(5f))
        val a4 = 842f / 595f
        assertEquals(1080, PdfMaths.renderWidth(1080f, a4))
        val w = PdfMaths.renderWidth(5400f, a4)
        assertTrue(w <= PdfMaths.MAX_SIDE)
        assertTrue((w * w * a4).toLong() <= PdfMaths.MAX_PAGE_PIXELS)
        assertTrue((w * a4) <= PdfMaths.MAX_SIDE)
        // A very tall strip: the height cap decides.
        assertTrue(PdfMaths.renderWidth(1080f, 10f) * 10 <= PdfMaths.MAX_SIDE)
    }

    @Test fun pageIndicatorAndJumps() {
        assertEquals("1 / 7", PdfMaths.pageLabel(0, 7))
        assertEquals("2 / 7", PdfMaths.pageLabel(1, 7))
        assertEquals("7 / 7", PdfMaths.pageLabel(12, 7))
        assertEquals(0, PdfMaths.jumpTarget(-3, 7))
        assertEquals(6, PdfMaths.jumpTarget(9, 7))
        assertEquals(3, PdfMaths.jumpTarget(3, 7))
        assertEquals(0, PdfMaths.jumpTarget(2, 0))
    }
}
