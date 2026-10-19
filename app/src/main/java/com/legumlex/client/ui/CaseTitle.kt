package com.legumlex.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

// Mirrors the Perfex cases module's lxc_case_title_html(): a cause-title written
// as "<A> {v/s | vs | vs. | versus | v | v.} <B>" is normalised to "<A> v. <B>"
// with the "v." de-emphasised. Anything that isn't a cause-title is left verbatim.
private val CAUSE_TITLE = Regex("""^(.+?)\s+(?:v/s|versus|vs\.?|v\.?)\s+(.+)$""", RegexOption.IGNORE_CASE)

/** Plain-string form: "X vs Y" → "X v. Y"; non-cause titles returned unchanged. */
fun caseTitlePlain(raw: String?): String {
    val title = raw?.trim().orEmpty()
    val m = CAUSE_TITLE.matchEntire(title) ?: return title
    val a = m.groupValues[1].trim()
    val b = m.groupValues[2].trim()
    return if (a.isNotEmpty() && b.isNotEmpty()) "$a v. $b" else title
}

/**
 * Styled form for Compose Text: renders "<A> v. <B>" with the "v." in [vsColor]
 * and italic. The surrounding parties inherit the Text's own style.
 */
fun caseTitleAnnotated(raw: String?, vsColor: Color): AnnotatedString {
    val title = raw?.trim().orEmpty()
    val m = CAUSE_TITLE.matchEntire(title)
    return buildAnnotatedString {
        if (m != null) {
            val a = m.groupValues[1].trim()
            val b = m.groupValues[2].trim()
            if (a.isNotEmpty() && b.isNotEmpty()) {
                append(a)
                append(' ')
                withStyle(SpanStyle(color = vsColor, fontStyle = FontStyle.Italic)) { append("v.") }
                append(' ')
                append(b)
                return@buildAnnotatedString
            }
        }
        append(title)
    }
}
