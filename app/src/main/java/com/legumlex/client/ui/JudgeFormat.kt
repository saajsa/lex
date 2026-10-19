package com.legumlex.client.ui

/**
 * Renders a judge's name with the correct Indian courtroom salutation, derived
 * from the court's level:
 *   - Supreme Court / High Court judges → "Hon'ble Justice <name>"
 *   - All other (subordinate) courts    → "Ld. <name>"
 *
 * Returns null for a blank judge. Names that already carry an honorific/title
 * (Hon'ble, Justice, CJI, Ld., …) are left untouched so we never double-prefix.
 */
fun formatJudge(judge: String?, courtName: String?): String? {
    val name = judge?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val lower = name.lowercase()

    val alreadyTitled = listOf("hon'ble", "honble", "justice", "cji", "ld.", "ld ")
        .any { lower.startsWith(it) } || lower.contains("justice")
    if (alreadyTitled) return name

    val court = courtName?.lowercase().orEmpty()
    val superior = court.contains("supreme court") || court.contains("high court")
    return if (superior) "Hon'ble Justice $name" else "Ld. $name"
}
