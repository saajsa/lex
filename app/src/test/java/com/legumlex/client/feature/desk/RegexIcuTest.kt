package com.legumlex.client.feature.desk

import org.junit.Assert.assertTrue
import org.junit.Test

/** Android compiles regexes with ICU, which rejects a bare closing brace that java.util.regex tolerates: every Regex("...") in the app must escape it. */
class RegexIcuTest {
    @Test fun noRegexInTheAppHasAnUnescapedClosingBrace() {
        val bad = mutableListOf<String>()
        val lit = Regex("Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"")
        val quant = Regex("(?<!\\\\)\\{\\d+(,\\d*)?\\}")
        java.io.File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            lit.findAll(f.readText()).forEach { m ->
                val pattern = m.groupValues[1].replace("\\\\", "\\")            // the Kotlin string, unescaped once
                val rest = quant.replace(pattern.replace(Regex("\\\\[pP]\\{\\w+\\}"), ""), "").replace("\\}", "").replace("\\\\", "")
                if (rest.contains('}')) bad += f.name + ": " + pattern
            }
        }
        assertTrue("Unescaped } in: $bad", bad.isEmpty())
    }
}
