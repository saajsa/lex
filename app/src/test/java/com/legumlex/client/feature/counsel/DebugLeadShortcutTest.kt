package com.legumlex.client.feature.counsel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source checks: the debug-only door shortcut has no path into a release build. */
class DebugLeadShortcutTest {
    private fun main(vararg parts: String) = File("src/main/java/com/legumlex/client").let { root ->
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().also { assertTrue("sources not found from ${File(".").absolutePath}", it.isNotEmpty()) }
    }

    @Test fun the_shortcut_returns_before_drawing_unless_debug() {
        val src = File("src/main/java/com/legumlex/client/feature/counsel/DebugLeadShortcut.kt").readText()
        val body = src.substringAfter("fun DebugLeadShortcut").substringAfter(") {")
        val guard = body.indexOf("if (!BuildConfig.DEBUG) return")
        assertTrue("the debug guard is missing", guard >= 0)
        // The guard comes before anything is drawn or called.
        assertTrue(guard < body.indexOf("Row(").takeIf { it >= 0 }!!)
        assertTrue(guard < body.indexOf("onUse"))
    }

    @Test fun the_shortcut_is_used_only_by_the_counsel_door() {
        val users = main().filter { it.readText().contains("DebugLeadShortcut(") && it.name != "DebugLeadShortcut.kt" }.map { it.name }
        assertEquals(listOf("CounselDoor.kt"), users)
    }

    @Test fun nothing_in_the_shortcuts_words_reaches_a_release_screen_by_another_door() {
        val others = main().filter { it.name != "DebugLeadShortcut.kt" && it.readText().contains("Use Stored Lead Session") }
        assertTrue(others.isEmpty())
    }
}
