package com.legumlex.client.feature.desk

/* ============================================================================
 *  Opening an account in the desk (owner, 2026-10-23): the website's account
 *  mode (reception.php over lexon Enrolment), for a lead. The server computes
 *  the conversation (customers_api lead/onboarding/turn and answer); the app
 *  draws each turn it is given: the lines, the firm's note pinned above the
 *  dock, and the dock (chips, a document, a card of fields, a code, the
 *  summary, the final tick). Nothing here decides a step.
 * ========================================================================== */

/** A chip; [confirm] is asked first, here (Start Over, Replace My …). */
data class AcctChip(val id: String, val label: String, val href: String? = null, val wait: Int = 0, val confirm: AcctConfirm? = null)

data class AcctConfirm(val say: String, val ok: AcctChip, val cancel: String)

data class AcctField(
    val name: String, val label: String, val value: String, val locked: Boolean, val hint: String, val error: String,
    val multiline: Boolean, val select: List<Pair<String, String>>?, val prefix: String, val numeric: Boolean, val phone: Boolean,
)

data class AcctRow(val label: String, val value: String, val from: String = "")

data class AcctDoc(val type: String, val label: String, val value: String, val status: String, val action: AcctChip)

data class AcctGroup(val id: String, val title: String, val rows: List<AcctRow>, val documents: List<AcctDoc>, val change: Boolean)

/** What answers the turn. */
sealed interface AcctDock {
    data object None : AcctDock
    data class Upload(val type: String, val label: String, val why: String, val maxMb: Int) : AcctDock
    data class Confirm(val type: String, val rows: List<AcctRow>) : AcctDock
    data class Card(val fields: List<AcctField>, val button: String, val cancel: String?, val under: String) : AcctDock
    data class Code(val to: String, val error: String, val resendIn: Int, val smsIn: Int) : AcctDock
    data class Summary(val groups: List<AcctGroup>) : AcctDock
    data class Final(val consent: String, val button: String) : AcctDock
    data object Google : AcctDock
}

data class AcctTurn(
    val step: String,
    val say: List<String>,
    val dock: AcctDock,
    val chips: List<AcctChip>,
    val more: List<AcctChip>,
    val progress: Pair<Int, Int>?,
    val note: String,
    val error: String,
    val stale: Boolean,
    val sent: Boolean,
    val divider: String,
)

private fun Any?.s(): String = (this as? String).orEmpty()
private fun Any?.n(): Int = when (this) { is Number -> toInt(); is String -> toIntOrNull() ?: 0; else -> 0 }

@Suppress("UNCHECKED_CAST")
private fun chip(m: Map<String, Any?>): AcctChip = AcctChip(
    id = m["id"].s(), label = m["label"].s(), href = (m["href"] as? String)?.takeIf { it.isNotBlank() }, wait = m["wait"].n(),
    confirm = (m["confirm"] as? Map<String, Any?>)?.let { c ->
        AcctConfirm(c["say"].s(), chip(c["ok"] as? Map<String, Any?> ?: emptyMap()), c["cancel"].s())
    },
)

@Suppress("UNCHECKED_CAST")
private fun rows(v: Any?): List<AcctRow> = (v as? List<Map<String, Any?>>).orEmpty().map { AcctRow(it["label"].s(), it["value"].s(), it["from"].s()) }

/** A turn, as the server sends it (lexon Enrolment). */
@Suppress("UNCHECKED_CAST")
fun parseTurn(t: Map<String, Any?>): AcctTurn {
    val d = t["dock"] as? Map<String, Any?>
    val dock: AcctDock = when (d?.get("kind")) {
        "upload" -> AcctDock.Upload(d["type"].s(), d["label"].s(), d["why"].s(), d["max_mb"].n().takeIf { it > 0 } ?: 10)
        "confirm" -> AcctDock.Confirm(d["type"].s(), rows(d["rows"]))
        "card" -> AcctDock.Card(
            fields = (d["fields"] as? List<Map<String, Any?>>).orEmpty().map { f ->
                AcctField(
                    name = f["name"].s(), label = f["label"].s(), value = f["value"]?.toString().orEmpty(), locked = f["locked"] == true,
                    hint = f["hint"].s(), error = f["error"].s(), multiline = f["multiline"] == true,
                    select = (f["options"] as? List<Map<String, Any?>>)?.map { it["value"].s() to it["label"].s() }?.takeIf { f["type"] == "select" },
                    prefix = f["prefix"].s(), numeric = f["inputmode"] == "numeric", phone = f["name"] == "phonenumber",
                )
            },
            button = d["button"].s().ifBlank { "Continue" }, cancel = (d["cancel"] as? String)?.takeIf { it.isNotBlank() }, under = d["under"].s(),
        )
        "code" -> AcctDock.Code(d["to"].s(), d["error"].s(), d["resend_in"].n(), d["sms_in"].n())
        "summary" -> AcctDock.Summary((d["groups"] as? List<Map<String, Any?>>).orEmpty().map { g ->
            AcctGroup(g["id"].s(), g["title"].s(), rows(g["rows"]),
                (g["documents"] as? List<Map<String, Any?>>).orEmpty().map { x ->
                    AcctDoc(x["type"].s(), x["label"].s(), x["value"].s(), x["status"].s(), chip(x["action"] as? Map<String, Any?> ?: emptyMap()))
                }, g["change"] == true)
        })
        "final" -> AcctDock.Final(d["consent"].s(), d["button"].s())
        "google" -> AcctDock.Google
        else -> AcctDock.None
    }
    val p = t["progress"] as? Map<String, Any?>
    return AcctTurn(
        step = t["step"].s(),
        say = (t["say"] as? List<*>).orEmpty().mapNotNull { it as? String },
        dock = dock,
        chips = (t["chips"] as? List<Map<String, Any?>>).orEmpty().map(::chip),
        more = (t["more"] as? List<Map<String, Any?>>).orEmpty().map(::chip),
        progress = p?.let { it["at"].n() to it["of"].n() }?.takeIf { it.second > 0 },
        note = t["note"].s(),
        error = t["error"].s(),
        stale = t["stale"] == true,
        sent = t["sent"] == true,
        divider = t["divider"].s(),
    )
}

/** The phrase check (Enrolment::wish), on the visitor's own words. */
fun accountWish(patterns: List<String>, text: String): Boolean {
    val t = text.lowercase()
    patterns.forEachIndexed { i, re ->
        if (!Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t)) return@forEachIndexed
        if (i == 0 && Regex("\\b(bank|demat|savings|current|trading)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return@forEachIndexed
        return true
    }
    return false
}
