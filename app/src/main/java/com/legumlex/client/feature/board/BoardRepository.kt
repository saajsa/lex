package com.legumlex.client.feature.board

import android.content.Context
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.api.ApiService
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.io.File

/** A board loaded earlier, kept for weak signal: the day, and when it was saved (epoch millis). */
data class CachedDay(val day: BoardDay, val savedAt: Long)

/**
 * The Daily Board's calls (customers_api v1, the board endpoints). Each returns a Result; a
 * refusal is an HttpException whose body [com.legumlex.client.feature.consultations.apiError]
 * reads (message, code), so the screen shows the server's own sentence.
 *
 * The last board loaded for a date is kept in the cache directory, read-only,
 * for weak signal. It lives under cache/board, which sign-out wipes with the
 * other documents on the device.
 */
class BoardRepository(private val api: ApiService, private val cacheRoot: File?) {

    constructor(context: Context) : this(ApiClient.get(context), context.cacheDir)

    private val json = Moshi.Builder().build().adapter(Map::class.java)

    private fun data(m: Map<String, Any?>): Map<String, Any?> = m["data"].map()

    suspend fun summary(): Result<BoardSummary> = runCatching { parseSummary(data(api.boardSummary())) }

    /** The Counsel home: board/today, the web's Today in one shape. */
    suspend fun today(): Result<CounselToday> = runCatching { parseToday(data(api.boardToday())) }

    /** eCourts Is Right: the case takes the date eCourts showed. */
    suspend fun dateAccept(id: Int): Result<Unit> = runCatching { api.boardDateAccept(id.toString()); Unit }

    /** Keep My Date: the counsel's date stays. */
    suspend fun dateKeep(id: Int): Result<Unit> = runCatching { api.boardDateKeep(id.toString()); Unit }

    suspend fun day(date: String): Result<BoardDay> = runCatching {
        val raw = data(api.boardDay(date))
        val day = parseDay(raw)
        saveDay(day.date.ifBlank { date }, raw)
        day
    }

    /** The number listed on each of [n] days from [from]: date (yyyy-MM-dd) to count. */
    suspend fun days(from: java.time.LocalDate, n: Int = StripDays): Result<Map<String, Int>> = runCatching { parseDays(data(api.boardDays(BoardDates.api(from), n))) }

    // ---- weak signal --------------------------------------------------------

    private fun dir(): File? = cacheRoot?.let { File(it, "board").apply { mkdirs() } }
    private fun dayFile(date: String): File? = dir()?.let { File(it, "day_$date.json") }

    private suspend fun saveDay(date: String, raw: Map<String, Any?>) = withContext(Dispatchers.IO) {
        runCatching {
            val f = dayFile(date) ?: return@runCatching
            f.writeText(json.toJson(raw))
            // Keep the last fortnight of boards, no more.
            dir()?.listFiles { x -> x.name.startsWith("day_") }?.sortedByDescending { it.lastModified() }?.drop(14)?.forEach { it.delete() }
        }
    }

    /** The board last loaded for this date, if any; null when none was kept or it cannot be read. */
    suspend fun cachedDay(date: String): CachedDay? = withContext(Dispatchers.IO) {
        runCatching {
            val f = dayFile(date)?.takeIf { it.isFile } ?: return@runCatching null
            @Suppress("UNCHECKED_CAST")
            val raw = json.fromJson(f.readText()) as? Map<String, Any?> ?: return@runCatching null
            CachedDay(parseDay(raw), f.lastModified())
        }.getOrNull()
    }

    // ---- import -------------------------------------------------------------

    suspend fun importPreview(part: MultipartBody.Part): Result<ImportPreview> = runCatching { parsePreview(data(api.boardImport(listOf(part)))) }

    suspend fun importConfirm(importId: String): Result<ImportDone> = runCatching { parseImportDone(data(api.boardImportConfirm(importId))) }

    // ---- cases --------------------------------------------------------------

    suspend fun cases(q: String): Result<Pair<Int, List<BoardCase>>> = runCatching {
        val d = data(api.boardCases(q = q, limit = 500))
        d["total"].int() to d["items"].list().map(::parseCase)
    }

    suspend fun case(id: Int): Result<BoardCase> = runCatching { parseCase(data(api.boardCase(id.toString()))) }

    suspend fun caseAdd(fields: Map<String, String>): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseAdd(fields))["case"].map()) }

    suspend fun caseEdit(id: Int, fields: Map<String, String>): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseEdit(id.toString(), fields))["case"].map()) }

    suspend fun caseRemove(id: Int): Result<Unit> = runCatching { api.boardCaseRemove(id.toString()); Unit }

    /** Update From Court: returns the case as read, and the balance after the charge. */
    suspend fun caseRead(id: Int): Result<Pair<BoardCase, Int>> = runCatching {
        val d = data(api.boardCaseRead(id.toString()))
        parseCase(d["case"].map()) to d["balance"].int()
    }

    /** Gives a hand-added case its CNR (free): the case as it now stands. */
    suspend fun caseAddCnr(id: Int, cnr: String): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseAddCnr(id.toString(), cnr))["case"].map()) }

    /** Add By CNR: the new case, and the balance after the charge. */
    suspend fun caseByCnr(cnr: String): Result<Pair<BoardCase, Int>> = runCatching {
        val d = data(api.boardCaseByCnr(cnr))
        parseCase(d["case"].map()) to d["balance"].int()
    }

    // ---- juniors and courts -------------------------------------------------

    suspend fun juniors(): Result<JuniorsData> = runCatching { parseJuniors(data(api.boardJuniors())) }

    suspend fun juniorAdd(name: String): Result<Unit> = runCatching { api.boardJuniorAdd(name); Unit }

    suspend fun juniorRename(id: Int, name: String): Result<Unit> = runCatching { api.boardJuniorRename(id.toString(), name); Unit }

    suspend fun juniorRemove(id: Int): Result<Unit> = runCatching { api.boardJuniorRemove(id.toString()); Unit }

    /** Assign a court to a junior; 0 clears it. */
    suspend fun courtAssign(courtKey: String, juniorId: Int): Result<Unit> = runCatching { api.boardCourtAssign(courtKey, juniorId.toString()); Unit }

    // ---- results ------------------------------------------------------------

    suspend fun entrySave(fields: Map<String, String>): Result<Unit> = runCatching { api.boardEntrySave(fields); Unit }

    // ---- the sheet ----------------------------------------------------------

    /** The cause list's PDF for a date in the counsel's own format (no junior), or the old junior sheet (an id, "none" or "all"); saved under cache/board/sheets. */
    suspend fun sheet(date: String, junior: String? = null): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val root = dir() ?: error("No cache directory")
            val sheets = File(root, "sheets").apply { mkdirs() }
            val file = File(sheets, "daily-board-$date-${junior?.filter { it.isLetterOrDigit() }.orEmpty().ifBlank { "own" }}.pdf")
            api.boardSheet(date, junior).use { body ->
                body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            file
        }
    }

    /** myCases.txt: every case in the diary, as text, saved under cache/board/export. */
    suspend fun export(): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val root = dir() ?: error("No cache directory")
            val out = File(root, "export").apply { mkdirs() }
            val file = File(out, "myCases.txt")
            api.boardExport().use { body -> body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } } }
            file
        }
    }

    // ---- credits ------------------------------------------------------------

    suspend fun credits(): Result<CreditsData> = runCatching { parseCredits(data(api.boardCredits())) }

    /** Buy a pack, paying first: the hosted pay page to open in a browser tab. No invoice is raised until it is paid. */
    suspend fun checkout(pack: String): Result<CheckoutOrder> = runCatching { parseCheckout(data(api.boardCreditsCheckout(pack))) }

    /** Where an order stands, asked when the app returns from the pay page. */
    suspend fun order(order: String): Result<OrderStatus> = runCatching { parseOrder(data(api.boardCreditsOrder(order))) }
}
