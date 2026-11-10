package com.legumlex.client.feature.auth

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A legal maxim and its English meaning. */
data class Maxim(val latin: String, val meaning: String)

/**
 * The maxims shown at the foot of Sign In (REQ-013, 013.3): one a day, chosen by the day of the year, so it is stable all day and the
 * same for everyone. The list is fixed.
 */
object SignInMaxims {
    val all: List<Maxim> = listOf(
        Maxim("Audi alteram partem.", "Hear the other side before deciding."),
        Maxim("Nemo judex in causa sua.", "No one may judge their own cause."),
        Maxim("Ubi jus ibi remedium.", "Every right has a remedy."),
        Maxim("Actus curiae neminem gravabit.", "An act of the court harms no one."),
        Maxim("Interest reipublicae ut sit finis litium.", "Litigation must come to an end."),
        Maxim("Vigilantibus non dormientibus jura subveniunt.", "The law assists the vigilant, not the sleeping."),
        Maxim("Nemo debet bis vexari.", "No one is troubled twice for one cause."),
        Maxim("Res judicata.", "A matter already decided is final."),
        Maxim("Lis pendens.", "A pending case affects dealings in the property."),
        Maxim("Stare decisis.", "Courts follow earlier decisions."),
        Maxim("Lex non cogit ad impossibilia.", "The law does not demand the impossible."),
        Maxim("De minimis non curat lex.", "The law does not concern itself with trifles."),
        Maxim("Res ipsa loquitur.", "The facts speak for themselves."),
        Maxim("Omnia praesumuntur rite esse acta.", "Official acts are presumed properly done."),
        Maxim("Ei incumbit probatio qui dicit, non qui negat.", "Proof lies on the one who asserts."),
        Maxim("Caveat emptor.", "Let the buyer beware."),
        Maxim("Pacta sunt servanda.", "Agreements must be kept."),
        Maxim("Consensus ad idem.", "Both parties agree on the same thing."),
        Maxim("Contra proferentem.", "Unclear wording is read against its drafter."),
        Maxim("Nemo dat quod non habet.", "No one can give what they lack."),
        Maxim("Ex turpi causa non oritur actio.", "No legal claim can rest on illegality."),
        Maxim("Qui facit per alium facit per se.", "Acting through an agent is acting oneself."),
        Maxim("Volenti non fit injuria.", "One who consents to a risk cannot complain."),
        Maxim("Actus non facit reum nisi mens sit rea.", "No crime without a guilty mind."),
        Maxim("Ejusdem generis.", "General words follow the specific words before them."),
        Maxim("Noscitur a sociis.", "A word is known by its company."),
        Maxim("Expressio unius est exclusio alterius.", "Naming one thing excludes others."),
        Maxim("Generalia specialibus non derogant.", "General law does not override special law."),
        Maxim("Ut res magis valeat quam pereat.", "Better that a thing take effect than fail."),
        Maxim("Fraus omnia vitiat.", "Fraud spoils whatever it touches."),
        Maxim("Delegatus non potest delegare.", "A delegate cannot delegate the power received."),
    )

    /** The maxim for [date] (its day of the year, counted round the list). */
    fun forDate(date: LocalDate): Maxim = all[(date.dayOfYear - 1) % all.size]
}

/** "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st". */
fun ordinalDay(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** The date block's three words: the weekday short ("Tue"), the ordinal day ("10th") and the month ("November"). */
data class DateWords(val weekday: String, val day: String, val month: String)

fun dateWords(date: LocalDate): DateWords = DateWords(
    weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
    day = ordinalDay(date.dayOfMonth),
    month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
)
