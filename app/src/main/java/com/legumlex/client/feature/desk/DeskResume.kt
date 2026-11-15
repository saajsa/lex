package com.legumlex.client.feature.desk

/**
 * Lex's first turn after sign-in (REQ-029 slice 4; the web's ajax_desk_resume, via customers_api POST desk/resume): the guest conversation is read once and the
 * answer says what to do next. Shapes are cases_desk_resume's own.
 */

/** The consultation-first choice (desk/check `insist`, desk/resume `insist`, desk/close `choice`): a lead line and the cards, the consultation first. */
data class ChoiceBlock(val lead: String, val cards: List<CloseCard>)

/** A service route (REQ-026): which service, Lex's line, when the visitor said they must act, and the button. portal_docs also has the signed-in variants. */
data class RouteInfo(
    val route: String, val line: String, val whenText: String, val button: String,
    val lineIn: String = "", val buttonIn: String = "", val hrefIn: String = "",
)

/** kind: story (the guest told what happened: it becomes the enquiry), question (answer it, ask for the matter), route (a service). */
data class ResumeAnswer(
    val kind: String, val reply: String, val nonmatter: Boolean, val insist: ChoiceBlock?, val route: RouteInfo?, val lang: String,
)

@Suppress("UNCHECKED_CAST")
fun parseChoiceBlock(m: Any?): ChoiceBlock? {
    val b = m as? Map<String, Any?> ?: return null
    val cards = (b["cards"] as? List<*>).orEmpty().mapNotNull { c ->
        val x = c as? Map<*, *> ?: return@mapNotNull null
        val key = (x["key"] as? String).orEmpty().trim()
        val button = (x["button"] as? String).orEmpty().trim()
        if (key !in setOf("consultation", "written") || button.isEmpty()) null
        else CloseCard(key, button, (x["title"] as? String).orEmpty().trim(), (x["price"] as? String).orEmpty().trim(), x["secondary"] == true,
            (x["includes"] as? String).orEmpty().trim(), (x["timing"] as? String).orEmpty().trim(), (x["intro"] as? String).orEmpty().trim())
    }
    return if (cards.isEmpty()) null else ChoiceBlock((b["lead"] as? String).orEmpty().trim(), cards)
}

/** The route fields of a check, resume or guest answer; null when there is no usable route. */
fun parseRoute(d: Map<String, Any?>?): RouteInfo? {
    if (d == null) return null
    val route = (d["route"] as? String).orEmpty().trim()
    val line = (d["route_line"] as? String).orEmpty().trim()
    if (route !in setOf("ahc", "intake", "consultation", "portal_docs", "clarify") || line.isEmpty()) return null
    fun s(k: String) = (d[k] as? String).orEmpty().trim()
    return RouteInfo(route, htmlToMarkup(line), s("route_when"), s("route_button"), s("route_line_in"), s("route_button_in"), s("route_href_in"))
}

/** Null for a failed or unusable answer (the app then decides by its own plain rule). */
@Suppress("UNCHECKED_CAST")
fun parseResume(d: Map<String, Any?>?): ResumeAnswer? {
    if (d == null || d["success"] != true) return null
    val kind = (d["kind"] as? String).orEmpty()
    if (kind !in setOf("story", "question", "route")) return null
    return ResumeAnswer(
        kind, htmlToMarkup((d["reply"] as? String).orEmpty().trim()), d["nonmatter"] == true,
        parseChoiceBlock(d["insist"]), if (kind == "route") parseRoute(d) else null, deskLang(d["lang"] as? String),
    )
}

/** The guest conversation as the server reads it: [{"role":"guest|desk","text":"..."}], with the JSON escaped by hand (plain Kotlin, so it is testable off-device). */
fun turnsJson(turns: List<Pair<String, String>>): String = turns.joinToString(",", "[", "]") { (who, text) ->
    val e = buildString { for (c in text) when (c) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c) } }
    "{\"role\":\"" + (if (who == "guest") "guest" else "desk") + "\",\"text\":\"" + e + "\"}"
}
