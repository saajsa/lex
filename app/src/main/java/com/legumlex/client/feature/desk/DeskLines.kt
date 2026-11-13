package com.legumlex.client.feature.desk

/**
 * The desk's fixed lines in Hindi, Punjabi and Hinglish (REQ-017 017.26).
 *
 * The server says which language a reading is in (`lang`: en, hi, pa, hx). Once it is not English, every fixed line the app says itself comes from
 * `desk/lines?lang=` (key to text), with the tokens {n}, {first}, {email}, {fee}, {num} and the like filled here. Under each translated line the
 * footer is shown, the English first and then the local line. English stays exactly as it is, with no footer; lines the model wrote carry none.
 * Buttons and chips stay English. When /lines cannot be had, the desk says its English lines and nothing else changes.
 */

/** The languages the desk translates into. */
val DESK_LANGS = setOf("hi", "pa", "hx")

/** The language a reading names: hi, pa or hx; anything else (or nothing) is English. */
fun deskLang(raw: String?): String = raw?.trim()?.lowercase()?.takeIf { it in DESK_LANGS } ?: "en"

/** Small print under a translated line: the English, then the local line. */
data class DeskFooter(val lang: String, val en: String, val local: String)

/** {lang, en, local} from the server; null when it is not all there. */
fun parseDeskFooter(raw: Any?): DeskFooter? {
    val m = raw as? Map<*, *> ?: return null
    val en = (m["en"] as? String).orEmpty().trim()
    val local = (m["local"] as? String).orEmpty().trim()
    if (en.isEmpty() && local.isEmpty()) return null
    return DeskFooter(deskLang(m["lang"] as? String), en, local)
}

/** What a server answer says about its own language: `lang`, and whether this line was a fixed, translated one (with its footer). */
data class DeskTongue(val lang: String = "en", val translated: Boolean = false, val footer: DeskFooter? = null) {
    /** The footer for a line of this answer: only a translated fixed line has one. */
    val lineFooter: DeskFooter? get() = if (translated) footer else null
}

fun parseTongue(d: Map<String, Any?>?): DeskTongue {
    if (d == null) return DeskTongue()
    val lang = deskLang(d["lang"] as? String)
    if (lang == "en") return DeskTongue()
    return DeskTongue(lang, d["translated"] == true, parseDeskFooter(d["footer"]))
}

/** The page's own <b> becomes the app's **bold**; any other tag is dropped. */
fun htmlToMarkup(s: String): String =
    s.replace(Regex("</?b>", RegexOption.IGNORE_CASE), "**").replace(Regex("<[^>]+>"), "")

/** A line with its {tokens} filled; a token with no value is left empty. */
fun fillLine(raw: String, vars: Map<String, String>, capitalise: Boolean = true): String =
    htmlToMarkup(Regex("\\{(\\w+)\\}").replace(raw) { m ->
        // {advo} (the plural or oblique form) falls back to {adv}'s value when the caller has one; an unknown token is empty, never shown raw.
        val name = m.groupValues[1]
        val v = (vars[name] ?: if (name == "advo") vars["adv"] else null).orEmpty()
        if (opensSentence(raw.substring(0, m.range.first))) v.replaceFirstChar { it.uppercase() } else v
    }).replace(Regex("[ \\t]{2,}"), " ").trim().let { if (capitalise) capitaliseSentences(it) else it }

/** Words whose full stop does not end a sentence ("Rs. 3 lakh", "u/s. 138", "Dr. Rao", "e.g. a notice"): what follows them keeps its case. */
private val ABBREVIATIONS = setOf("rs", "no", "nos", "u/s", "sec", "secs", "art", "dr", "mr", "mrs", "ms", "smt", "shri", "ld", "hon", "vs", "v", "etc",
    "e.g", "i.e", "viz", "approx", "st", "mt", "co", "ltd", "pvt", "inc", "hc", "sc", "ors", "anr", "w.p", "crl", "cr", "civ")

/**
 * Lex's own lines (never the visitor's words) begin with a capital: the first letter of the message, and the first letter after a sentence-ending
 * . ? ! or danda followed by a space (or after a line break). No other word is touched. The server's text can open with a lower-case word of its own
 * ("vakeel aapse kis number par..."). A full stop after an abbreviation (Rs., u/s., Dr., e.g.) does not end a sentence; Devanagari and Gurmukhi have no case.
 */
fun capitaliseSentences(s: String): String {
    val out = StringBuilder(s.length)
    var cap = true          // the next letter opens a sentence
    var stopped = false     // a sentence-ending mark was just seen: a space after it opens the next one
    var word = StringBuilder()
    for (c in s) {
        when {
            c == '\n' -> { cap = true; stopped = false; word = StringBuilder(); out.append(c) }
            c.isWhitespace() -> { if (stopped) cap = true; stopped = false; word = StringBuilder(); out.append(c) }
            c == '*' -> out.append(c)
            c == '.' || c == '!' || c == '?' || c == '\u0964' -> {
                stopped = c != '.' || word.toString().lowercase() !in ABBREVIATIONS
                cap = false
                word.append(c); out.append(c)
            }
            else -> {
                stopped = false
                if (cap && c.isLowerCase()) out.append(c.uppercaseChar()) else out.append(c)
                cap = false
                word.append(c)
            }
        }
    }
    return out.toString()
}

/** Whether text placed after [before] begins a sentence: at the start of a line (past any bold mark), or after a full stop, mark or danda and a space. */
fun opensSentence(before: String): Boolean {
    val b = before.replace(Regex("(<b>|\\*\\*)", RegexOption.IGNORE_CASE), "")
    return b.isBlank() || Regex("[.!?\u0964]\\s+$").containsMatchIn(b)
}

/** The one word that tags a later translated line: the footer's own label (its local line up to the first stop or danda). */
fun footerTag(f: DeskFooter): String =
    f.local.substringBefore('\u0964').substringBefore('.').trim().ifBlank { f.local.trim() }

/** The full footer is shown once per conversation, under the first translated line; every later translated line gets only the tag. */
class FooterOnce {
    private var shown = false
    /** (footer, tagOnly): the footer as given for the first, then tagOnly = true; null footer stays null and does not use up the first. */
    fun next(f: DeskFooter?): Pair<DeskFooter?, Boolean> {
        if (f == null) return null to false
        if (!shown) { shown = true; return f to false }
        return f to true
    }
    fun reset() { shown = false }
}

/** One language's fixed lines, as desk/lines gives them. */
class LineBook(val lang: String, private val lines: Map<String, String>, val footer: DeskFooter?, val pool: List<String> = emptyList()) {
    /** True when this book speaks a language other than English and has lines to say. */
    val active: Boolean get() = lang != "en" && lines.isNotEmpty()

    fun has(key: String) = active && !lines[key].isNullOrBlank()

    /** The line [key], filled; null when the book has none (the English is then said). */
    fun line(key: String, vars: Map<String, String> = emptyMap()): String? =
        if (!active) null else lines[key]?.takeIf { it.isNotBlank() }?.let { fillLine(it, vars) }

    /** A fragment that goes inside another line ("ek business", "3 matters"): filled but not capitalised, so it never opens a sentence of its own. */
    fun fragment(key: String, vars: Map<String, String> = emptyMap()): String? =
        if (!active) null else lines[key]?.takeIf { it.isNotBlank() }?.let { fillLine(it, vars, capitalise = false) }

    companion object {
        val English = LineBook("en", emptyMap(), null)
    }
}

/** desk/lines: {lang, lines: {key: text}, pool: [..], footer}; null when it is not a usable book (the English is then kept). */
fun parseLineBook(d: Map<String, Any?>?): LineBook? {
    if (d == null) return null
    val lang = deskLang(d["lang"] as? String)
    if (lang == "en") return null
    val lines = (d["lines"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
    if (lines.isEmpty()) return null
    val pool = (d["pool"] as? List<*>).orEmpty().mapNotNull { it as? String }.filter { it.isNotBlank() }
    return LineBook(lang, lines, parseDeskFooter(d["footer"]), pool)
}

/** The count in {n} lines: "1 matter" or "3 matters", by the book's own words. */
fun matterCount(book: LineBook, n: Int): String? = book.fragment(if (n == 1) "matter_one" else "matter_many", mapOf("n" to n.toString()))

/**
 * The book to use once an answer names [lang]: English when it says English, the cached [cached] book when there is one, else what [fetch] brings;
 * when /lines cannot be had (null), the English lines are kept and nothing else changes.
 */
suspend fun languageSwitch(lang: String, cached: LineBook?, fetch: suspend () -> LineBook?): LineBook {
    if (lang == "en") return LineBook.English
    return cached ?: fetch() ?: LineBook.English
}
