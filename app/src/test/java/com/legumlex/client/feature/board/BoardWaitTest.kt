package com.legumlex.client.feature.board

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's wait for the board's answer is bounded: still asking, could not be reached, or known; and an unreachable server never keeps the shell waiting. */
class BoardWaitTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @After fun reset() = BoardHub.clear()

    @Test fun stillAskingIsNotTheSameAsCouldNotBeReached() {
        assertEquals(BoardWait.Asking, boardWait(BoardAccess.Unknown, unreachable = false))
        assertEquals(BoardWait.Unreachable, boardWait(BoardAccess.Unknown, unreachable = true))
        assertEquals(BoardWait.Known, boardWait(BoardAccess.No, unreachable = true))      // an answer wins over an old failure
        assertEquals(BoardWait.Known, boardWait(BoardAccess.No, unreachable = false))
        assertEquals(10_000L, BOARD_WAIT_MS)
    }

    @Test fun aFailedAttemptIsRecordedOnlyWhileNothingIsKnownAndAnyAnswerClearsIt() {
        BoardHub.clear()
        assertFalse(BoardHub.unreachable.value)
        BoardHub.markUnreachable()
        assertTrue(BoardHub.unreachable.value)
        assertTrue(BoardHub.access.value is BoardAccess.Unknown)          // distinct from, and not a replacement for, Unknown
        BoardHub.hide()                                                    // the server answered (refused): known
        assertFalse(BoardHub.unreachable.value)
        BoardHub.markUnreachable()                                         // too late: something is known
        assertFalse(BoardHub.unreachable.value)
        BoardHub.clear()
        BoardHub.markUnreachable(); BoardHub.clear()
        assertFalse(BoardHub.unreachable.value)                            // sign-out clears it
    }

    @Test fun aNetworkFailureDecidesNothingButIsRecorded() {
        assertEquals(Verdict.Keep, decideAccess(false, listOf(Answer.Failed)))
        assertEquals(Verdict.Keep, decideAccess(true, listOf(Answer.Failed)))
        val hub = src("feature/board/BoardHub.kt") ?: return
        assertTrue(hub.contains("Verdict.Keep -> if (answers.last() == Answer.Failed) markUnreachable()"))
    }

    @Test fun theAppAsksAgainOnResumeAfterARefusalOrAFailureButNotWhileAsking() {
        assertTrue(reaskOnResume(BoardAccess.No, false))
        assertTrue(reaskOnResume(BoardAccess.Unknown, true))
        assertFalse(reaskOnResume(BoardAccess.Unknown, false))
    }

    @Test fun homeShowsItsErrorWithTryAgainAndTheShellStopsWaiting() {
        val nav = src("ui/navigation/NavGraph.kt") ?: return
        assertTrue(nav.contains("boardWait(access, unreachable) == com.legumlex.client.feature.board.BoardWait.Unreachable"))
        assertTrue(nav.contains("com.legumlex.client.feature.home.HomeError(") && nav.contains("BoardHub.retry(boardCtx)"))
        val main = src("MainActivity.kt")!!
        assertTrue(main.contains("kotlinx.coroutines.delay(com.legumlex.client.feature.board.BOARD_WAIT_MS)") && main.contains("BoardHub.markUnreachable()"))
        assertTrue(main.contains("val waiting = !lead && !boardUnreachable && "))                       // the portal chrome and its bottom bar show
        assertTrue(main.contains("reaskOnResume(boardAccess, boardUnreachable)"))
        assertTrue(src("feature/home/HomeScreen.kt")!!.contains("internal fun HomeError("))
    }
}
