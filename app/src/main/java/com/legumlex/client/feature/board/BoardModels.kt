package com.legumlex.client.feature.board

// -----------------------------------------------------------------------------
// The advocate's Daily Board (REQ-008): what customers_api/v1/board/* sends,
// read from its loose maps into plain classes. Nothing is invented here:
// statuses, groupings, check answers and display text all come from the server.
// -----------------------------------------------------------------------------

internal fun Any?.str(): String = (this as? String)?.takeIf { it != "null" }.orEmpty()
internal fun Any?.strOrNull(): String? = (this as? String)?.takeIf { it.isNotBlank() && it != "null" }
internal fun Any?.int(): Int = (this as? Number)?.toInt() ?: (this as? String)?.toIntOrNull() ?: 0
internal fun Any?.intOrNull(): Int? = (this as? Number)?.toInt() ?: (this as? String)?.toIntOrNull()
internal fun Any?.dbl(): Double = (this as? Number)?.toDouble() ?: (this as? String)?.toDoubleOrNull() ?: 0.0

@Suppress("UNCHECKED_CAST")
internal fun Any?.map(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()
internal fun Any?.list(): List<Map<String, Any?>> = (this as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

/** The result entered for a case on a day. */
data class BoardEntry(
    val id: Int,
    val itemNo: String,
    val status: String,
    val nextDate: String?,
    val remarks: String,
)

/** The junior who covers a case: "case" when named on it, "court" when through its court. */
data class JuniorRef(val id: Int, val name: String, val by: String? = null)

/** The morning check's answer: listed_today, listed_tomorrow or not_listed. */
data class MorningCheck(val result: String, val forDate: String?, val at: String?) {
    /** The sentence for the answer, as the app words it. */
    val label: String
        get() = when (result) {
            "listed_today" -> "Listed Today"
            "listed_tomorrow" -> "Listed Tomorrow"
            "not_listed" -> "Not Listed"
            else -> "Checked"
        }
}

data class BoardCase(
    val id: Int,
    val cnr: String,
    val caseNo: String,
    val caseType: String,
    val title: String,
    val petitioner: String,
    val respondent: String,
    val court: String,
    val courtKey: String,
    val room: String,
    val stage: String,
    val nextDate: String?,
    val lastDate: String?,
    val note: String,
    val source: String,
    val junior: JuniorRef?,
    val caseJuniorId: Int?,
    val morningCheck: Boolean,
    val check: MorningCheck?,
    val entry: BoardEntry?,
    val itemNo: String,
    /** The server's bench text (judge and court room), shown instead of the room when present. */
    val bench: String = "",
    val judge: String = "",
    /** eCourts' own date for a case flagged Needs Your Eye; the date the counsel had is nextDate. */
    val ecourtsDate: String? = null,
    val needsEye: Boolean = false,
    val hasCnr: Boolean = true,
    /** The case's Date History, newest first (only on the single-case read). */
    val history: List<DateHistoryRow> = emptyList(),
    /** The server's "designation at place" (no room) and the judge as the bench rule addresses them ("Ld. Court" when none). */
    val courtDesig: String = "",
    val judgeLine: String = "",
) {
    /** The court line: the court, then the bench the server names (else the room). */
    val courtLine: String get() = listOf(courtShown, judgeShown).filter { it.isNotBlank() }.joinToString(" · ")

    /** The court as the server words it ("Civil Judge (Jr. Div.) at Ludhiana"), else the court name. */
    val courtShown: String get() = courtDesig.ifBlank { court }

    /** The judge line (no room), else the bench the server named, else the room. */
    val judgeShown: String get() = judgeLine.ifBlank { bench.ifBlank { room } }

    /** The name a row leads with: the case number, else the CNR. */
    val heading: String get() = caseNo.ifBlank { cnr }
}

/** One recorded date of a case: the date, where it came from (the server's `label`), and when it was recorded. */
data class DateHistoryRow(val date: String, val source: String, val label: String, val at: String?)

/** The counsel's cause-list format, in the server's own words. */
data class BoardFormat(val summary: String, val paper: String, val orientation: String, val matched: Boolean)

internal fun parseFormat(m: Map<String, Any?>): BoardFormat? =
    if (m.isEmpty()) null else BoardFormat(m["summary"].str(), m["paper"].str(), m["orientation"].str(), m["matched"] == true)

data class RoomGroup(val room: String, val items: List<BoardCase>)
data class CourtGroup(val court: String, val count: Int, val rooms: List<RoomGroup>)
data class JuniorGroup(val junior: JuniorRef?, val items: List<BoardCase>)

data class BoardDay(
    val date: String,
    val items: List<BoardCase>,
    val byCourt: List<CourtGroup>,
    val byJunior: List<JuniorGroup>,
    val total: Int,
    val withResult: Int,
    val listed: Int,
    val format: BoardFormat? = null,
)

data class BoardSummary(
    val today: String,
    val cases: Int,
    val listedToday: Int,
    val listedTomorrow: Int,
    val resultsPending: Int,
    val resultsEntered: Int,
    val juniors: Int,
    val unassignedToday: Int,
    val credits: Int,
    /** The result options, as the server lists them. */
    val statuses: List<String>,
    /** True when the server makes this account's app the Counsel workspace (board/summary `counsel`). */
    val counsel: Boolean = false,
)

data class ImportRowError(val row: Int, val message: String)
data class ImportSample(val cnr: String, val caseNo: String, val court: String, val nextDate: String?, val isNew: Boolean)
data class ImportPreview(
    val importId: String,
    val kind: String,
    val found: Int,
    val new: Int,
    val existing: Int,
    val duplicates: Int,
    val errorCount: Int,
    val errors: List<ImportRowError>,
    val sample: List<ImportSample>,
)

/** A date eCourts and the imported file disagree on, put to the counsel after an import. */
data class Mismatch(val id: Int, val cnr: String, val caseNo: String, val title: String, val ecourtsDate: String?, val yourDate: String?) {
    val heading: String get() = caseNo.ifBlank { cnr }
}

data class ImportDone(val added: Int, val updated: Int, val total: Int, val mismatches: List<Mismatch> = emptyList())

data class Junior(val id: Int, val name: String, val cases: Int, val courts: List<String>)
data class CourtRow(val courtKey: String, val court: String, val cases: Int, val junior: JuniorRef?)
data class JuniorsData(val juniors: List<Junior>, val courts: List<CourtRow>)

data class CreditPack(val key: String, val label: String, val credits: Int, val price: Double)
data class LedgerRow(val id: Int, val whenAt: String, val kind: String, val label: String, val delta: Int, val balanceAfter: Int, val expiresAt: String?)
data class ExpiringLot(val credits: Int, val expiresAt: String)
data class Purchase(val id: Int, val pack: String, val credits: Int, val status: String, val createdAt: String, val paidAt: String?, val payLink: String?)
/** Credits spent (net of refunds): today, this month, ever. */
data class SpentStats(val today: Int, val month: Int, val all: Int)

/** This morning's check: when, how many cases and credits, and how many are on the daily check. */
data class CheckedStats(val at: String?, val cases: Int, val credits: Int, val onCheck: Int)

/** A pay-first order: the hosted pay page to open, and what it is for. */
data class CheckoutOrder(val order: String, val payUrl: String, val amount: Double, val label: String, val credits: Int)

/** Where an order stands: pending, paid, expired or flagged; the credits it adds, and the balance after. */
data class OrderStatus(val order: String, val status: String, val credits: Int, val balance: Int?)

data class CreditsData(
    val balance: Int,
    val expiring: List<ExpiringLot>,
    val packs: List<CreditPack>,
    val costAdd: Int,
    val costCheck: Int,
    val history: List<LedgerRow>,
    val purchases: List<Purchase>,
    val creditMonths: Int,
    val spent: SpentStats = SpentStats(0, 0, 0),
    val checked: CheckedStats = CheckedStats(null, 0, 0, 0),
)

// ---- parsing ------------------------------------------------------------------

internal fun parseCase(m: Map<String, Any?>): BoardCase {
    val j = m["junior"].map()
    val chk = m["check"].map()
    val en = m["entry"].map()
    return BoardCase(
        id = m["id"].int(),
        cnr = m["cnr"].str(),
        caseNo = m["case_no"].str(),
        caseType = m["case_type"].str(),
        title = m["title"].str(),
        petitioner = m["petitioner"].str(),
        respondent = m["respondent"].str(),
        court = m["court"].str(),
        courtKey = m["court_key"].str(),
        room = m["room"].str(),
        stage = m["stage"].str(),
        nextDate = m["next_date"].strOrNull(),
        lastDate = m["last_date"].strOrNull(),
        note = m["note"].str(),
        source = m["source"].str(),
        junior = if (j.isEmpty()) null else JuniorRef(j["id"].int(), j["name"].str(), j["by"].strOrNull()),
        caseJuniorId = m["case_junior_id"].intOrNull(),
        morningCheck = m["morning_check"] == true,
        check = if (chk.isEmpty()) null else MorningCheck(chk["result"].str(), chk["for"].strOrNull(), chk["at"].strOrNull()),
        entry = if (en.isEmpty()) null else BoardEntry(en["id"].int(), en["item_no"].str(), en["status"].str(), en["next_date"].strOrNull(), en["remarks"].str()),
        itemNo = m["item_no"].str(),
        bench = m["bench"].str(),
        judge = m["judge"].str(),
        ecourtsDate = m["ecourts_date"].strOrNull(),
        needsEye = m["needs_eye"] == true,
        hasCnr = m["has_cnr"] != false,
        history = m["history"].list().map { DateHistoryRow(it["date"].str(), it["source"].str(), it["label"].str(), it["at"].strOrNull()) },
        courtDesig = m["court_line"].str(),
        judgeLine = m["judge_line"].str(),
    )
}

/** board/day: the server's own grouping is kept as sent (court, then room, then item; or by junior). */
internal fun parseDay(d: Map<String, Any?>): BoardDay {
    val counts = d["counts"].map()
    return BoardDay(
        date = d["date"].str(),
        items = d["items"].list().map(::parseCase),
        byCourt = d["by_court"].list().map { c ->
            CourtGroup(
                court = c["court"].str(),
                count = c["count"].int(),
                rooms = c["rooms"].list().map { r -> RoomGroup(r["room"].str(), r["items"].list().map(::parseCase)) },
            )
        },
        byJunior = d["by_junior"].list().map { g ->
            val j = g["junior"].map()
            JuniorGroup(if (j.isEmpty()) null else JuniorRef(j["id"].int(), j["name"].str()), g["items"].list().map(::parseCase))
        },
        total = counts["total"].int(),
        withResult = counts["with_result"].int(),
        listed = counts["listed"].int(),
        format = parseFormat(d["format"].map()),
    )
}

internal fun parseSummary(d: Map<String, Any?>) = BoardSummary(
    today = d["today"].str(),
    cases = d["cases"].int(),
    listedToday = d["listed_today"].int(),
    listedTomorrow = d["listed_tomorrow"].int(),
    resultsPending = d["results_pending"].int(),
    resultsEntered = d["results_entered"].int(),
    juniors = d["juniors"].int(),
    unassignedToday = d["unassigned_today"].int(),
    credits = d["credits"].int(),
    statuses = (d["statuses"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
    counsel = d["counsel"] == true,
)

internal fun parsePreview(d: Map<String, Any?>) = ImportPreview(
    importId = d["import_id"].str(),
    kind = d["kind"].str(),
    found = d["found"].int(),
    new = d["new"].int(),
    existing = d["existing"].int(),
    duplicates = d["duplicates"].int(),
    errorCount = d["error_count"].int(),
    errors = d["errors"].list().map { ImportRowError(it["row"].int(), it["message"].str()) },
    sample = d["sample"].list().map {
        ImportSample(it["cnr"].str(), it["case_no"].str(), it["court"].str(), it["next_date"].strOrNull(), it["is_new"] == true)
    },
)

internal fun parseImportDone(d: Map<String, Any?>) = ImportDone(
    d["added"].int(), d["updated"].int(), d["total"].int(),
    d["mismatches"].list().map { Mismatch(it["id"].int(), it["cnr"].str(), it["case_no"].str(), it["title"].str(), it["ecourts_date"].strOrNull(), it["your_date"].strOrNull()) },
)

internal fun parseJuniors(d: Map<String, Any?>) = JuniorsData(
    juniors = d["juniors"].list().map { j ->
        Junior(j["id"].int(), j["name"].str(), j["cases"].int(), (j["courts"] as? List<*>)?.mapNotNull { it as? String }.orEmpty())
    },
    courts = d["courts"].list().map { c ->
        val j = c["junior"].map()
        CourtRow(c["court_key"].str(), c["court"].str(), c["cases"].int(), if (j.isEmpty()) null else JuniorRef(j["id"].int(), j["name"].str()))
    },
)

internal fun parseCredits(d: Map<String, Any?>): CreditsData {
    val costs = d["costs"].map()
    return CreditsData(
        balance = d["balance"].int(),
        expiring = d["expiring"].list().map { ExpiringLot(it["credits"].int(), it["expires_at"].str()) },
        packs = d["packs"].list().map { CreditPack(it["key"].str(), it["label"].str(), it["credits"].int(), it["price"].dbl()) },
        costAdd = costs["add"].int(),
        costCheck = costs["check"].int(),
        history = d["history"].list().map {
            LedgerRow(it["id"].int(), it["when"].str(), it["kind"].str(), it["label"].str(), it["delta"].int(), it["balance_after"].int(), it["expires_at"].strOrNull())
        },
        purchases = d["purchases"].list().map {
            Purchase(it["id"].int(), it["pack"].str(), it["credits"].int(), it["status"].str(), it["created_at"].str(), it["paid_at"].strOrNull(), it["pay_link"].strOrNull())
        },
        creditMonths = d["credit_months"].int(),
        spent = d["spent"].map().let { SpentStats(it["today"].int(), it["month"].int(), it["all"].int()) },
        checked = d["checked"].map().let { CheckedStats(it["at"].strOrNull(), it["cases"].int(), it["credits"].int(), it["on_check"].int()) },
    )
}

// ---- the Counsel home (board/today) ------------------------------------------------

/** A court on today's list: its name, the bench line the server words, and how many cases. */

/** A case whose date eCourts and the diary disagree on, to settle. */
data class NeedsEye(val id: Int, val cnr: String, val caseNo: String, val title: String, val ecourtsDate: String?, val earlierDate: String?) {
    val heading: String get() = caseNo.ifBlank { cnr }
}

/** The morning check ran short of credits: how far it got. */
data class CheckShort(val checked: Int, val of: Int, val creditsHad: Int, val unchecked: Int)

data class TodayCredits(
    val balance: Int,
    val cost: Int,
    val perMorning: Int,
    /** Whole days of checks left; 0 is less than a day; null when no case is on the daily check. */
    val daysLeft: Int?,
    val daysLabel: String,
    val low: Boolean,
)

data class CounselToday(
    val empty: Boolean,
    val date: String,
    val weekday: String,
    val listedToday: Int,
    val listedTomorrow: Int,
    val casesTracked: Int,
    val onCheck: Int,
    val credits: TodayCredits,
    val short: CheckShort?,
    val checkedAt: String?,
    val courtsN: Int,
    val needsEye: List<NeedsEye>,
)

internal fun parseToday(d: Map<String, Any?>): CounselToday {
    val c = d["credits"].map()
    val s = d["short"].map()
    return CounselToday(
        empty = d["empty"] == true,
        date = d["date"].str(),
        weekday = d["weekday"].str(),
        listedToday = d["listed_today"].int(),
        listedTomorrow = d["listed_tomorrow"].int(),
        casesTracked = d["cases_tracked"].int(),
        onCheck = d["on_check"].int(),
        credits = TodayCredits(
            balance = c["balance"].int(), cost = c["cost"].int(), perMorning = c["per_morning"].int(),
            daysLeft = c["days_left"].intOrNull(), daysLabel = c["days_label"].str(), low = c["low"] == true,
        ),
        short = if (s.isEmpty()) null else CheckShort(s["checked"].int(), s["of"].int(), s["credits_had"].int(), s["unchecked"].int()),
        checkedAt = d["checked_at"].strOrNull(),
        courtsN = d["courts_n"].int(),
        needsEye = d["needs_eye"].list().map {
            NeedsEye(it["id"].int(), it["cnr"].str(), it["case_no"].str(), it["title"].str(), it["ecourts_date"].strOrNull(), it["earlier_date"].strOrNull())
        },
    )
}

internal fun parseCheckout(d: Map<String, Any?>): CheckoutOrder {
    val p = d["pack"].map()
    return CheckoutOrder(d["order"].str(), d["pay_url"].str(), d["amount"].dbl(), p["label"].str(), p["credits"].int())
}

internal fun parseOrder(d: Map<String, Any?>) = OrderStatus(d["order"].str(), d["status"].str(), d["credits"].int(), d["balance"].intOrNull())

/** How long a pack's credits last at the cases on the daily check, the way the web words it. */
fun packLasts(credits: Int, onCheck: Int, costCheck: Int, months: Int): String {
    val cases = maxOf(1, onCheck)
    val cost = maxOf(1, costCheck)
    val days = credits / (cases * cost)
    val span = Math.round(maxOf(1, months) * 365.0 / 12).toInt()
    return if (days >= span) "Covers ${credits / (span * cost)} cases for ${maxOf(1, months)} months"
    else "About $days ${if (days == 1) "day" else "days"} at $cases ${if (cases == 1) "case" else "cases"}"
}

// ---- Home (board/home) ------------------------------------------------------------------

data class HomeProfile(
    val name: String, val displayName: String, val initials: String, val barCouncil: String, val enrolmentNo: String,
    val yearsAtBar: Int?, val chambers: String, val email: String, val mobile: String,
    val courts: Int, val districts: List<String>, val daysOnPlatform: Int,
    /** The firm's check of the enrolment, as the server says: pending | verified | rejected, or empty when it does not say. */
    val review: String = "",
)

data class Specialty(val key: String, val label: String, val side: String, val count: Int)

data class HomeScore(
    val criminal: Double, val civil: Double, val ni: Int, val lead: String,
    val specialties: List<Specialty>, val nextCriminal: Int?, val nextCivil: Int?,
)

data class HomeTiles(
    val ongoing: Int, val criminal: Int, val civil: Int, val ni: Int, val unknown: Int,
    val hearings: Int, val busiestDate: String?, val busiestCount: Int,
    val datesCaught: Int, val disposedThisYear: Int, val oldestRunningYear: Int?,
)

data class CounselHome(val profile: HomeProfile, val score: HomeScore, val tiles: HomeTiles)

internal fun parseHome(d: Map<String, Any?>): CounselHome {
    val p = d["profile"].map()
    val sc = d["score"].map()
    val nx = sc["next"].map()
    val t = d["tiles"].map()
    val dy = t["diary"].map()
    val tm = t["this_month"].map()
    val bz = tm["busiest"].map()
    val ds = t["disposed"].map()
    return CounselHome(
        profile = HomeProfile(
            name = p["name"].str(), displayName = p["display_name"].str(), initials = p["initials"].str(), barCouncil = p["bar_council"].str(),
            enrolmentNo = p["enrolment_no"].str(), yearsAtBar = p["years_at_bar"].intOrNull(), chambers = p["chambers"].str(), email = p["email"].str(),
            mobile = p["mobile"].str(), courts = p["courts"].int(), districts = (p["districts"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            daysOnPlatform = p["days_on_platform"].int(),
            review = p["review"].str().takeIf { it in setOf("pending", "verified", "rejected") }.orEmpty(),
        ),
        score = HomeScore(
            criminal = sc["criminal"].dbl(), civil = sc["civil"].dbl(), ni = sc["ni"].int(), lead = sc["lead"].str(),
            specialties = sc["specialties"].list().map { Specialty(it["key"].str(), it["label"].str(), it["side"].str(), it["count"].int()) },
            nextCriminal = nx["criminal"].intOrNull(), nextCivil = nx["civil"].intOrNull(),
        ),
        tiles = HomeTiles(
            ongoing = dy["ongoing"].int(), criminal = dy["criminal"].int(), civil = dy["civil"].int(), ni = dy["ni"].int(), unknown = dy["unknown"].int(),
            hearings = tm["hearings"].int(), busiestDate = bz["date"].strOrNull(), busiestCount = bz["count"].int(),
            datesCaught = t["dates_caught"].map()["count"].int(), disposedThisYear = ds["this_year"].int(), oldestRunningYear = ds["oldest_running_year"].intOrNull(),
        ),
    )
}

/** The chambers request, as the web's wizard sends it: trimmed, cut to the server's limits (address 191, city 100, PIN 15). */
internal fun chambersFields(address: String, city: String, zip: String): Map<String, String> = mapOf(
    "address" to address.trim().take(191), "city" to city.trim().take(100), "zip" to zip.trim().take(15),
)

/** The line Counsel Home shows while the firm has not yet verified the enrolment (REQ-010); nothing is said for any other state. */
const val ENROLMENT_PENDING_LINE = "Your enrolment is with our reviewers."
fun enrolmentLine(p: HomeProfile): String? = if (p.review == "pending") ENROLMENT_PENDING_LINE else null
