package com.legumlex.client.feature.board

import android.content.Context
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.api.ApiService
import com.legumlex.client.feature.consultations.apiError
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.io.File

/** A board loaded earlier, kept for weak signal: the day, and when it was saved (epoch millis). */
data class CachedDay(val day: BoardDay, val savedAt: Long)

/** A day as loaded: [savedAt] is null for the server's answer, else the time (epoch millis) of the device's saved copy shown instead. */
data class DayLoad(val day: BoardDay, val savedAt: Long?)

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

    /**
     * The day from the server; when the server cannot be reached (offline, or a server error), the copy kept on this
     * device with the time it was saved. A refusal (403, 404 ...) is never answered from the copy: it is the server's word.
     */
    suspend fun dayOrSaved(date: String): Result<DayLoad> {
        val live = day(date)
        live.getOrNull()?.let { return Result.success(DayLoad(it, null)) }
        val err = live.exceptionOrNull() ?: return Result.failure(IllegalStateException("No answer"))
        val http = err.apiError().http
        if (!(err.isOffline() || (http != null && http >= 500))) return Result.failure(err)
        val saved = cachedDay(date) ?: return Result.failure(err)
        return Result.success(DayLoad(saved.day, saved.savedAt))
    }

    /** The counsel's Home. */
    suspend fun home(): Result<CounselHome> = runCatching { parseHome(data(api.boardHome())) }

    /** Save the chambers (the web wizard's own method); returns the chambers line as Home shows it. */
    suspend fun chambersSave(address: String, city: String, zip: String): Result<String> = runCatching {
        data(api.boardChambers(chambersFields(address, city, zip)))["chambers"].str()
    }

    /** The number listed on each of [n] days from [from]: date (yyyy-MM-dd) to count. */
    suspend fun days(from: java.time.LocalDate, n: Int = 7): Result<Map<String, Int>> = runCatching { parseDays(data(api.boardDays(BoardDates.api(from), n))) }

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

    /**
     * Confirm the import. A call that timed out may have been applied by the server, so it is asked once more: a second confirm for the
     * same import returns the first result (repeated: true) and applies nothing twice.
     */
    suspend fun importConfirm(importId: String): Result<ImportDone> {
        val first = runCatching { parseImportDone(data(api.boardImportConfirm(importId))) }
        if (first.isSuccess || !(first.exceptionOrNull()?.isOffline() ?: false)) return first
        return runCatching { parseImportDone(data(api.boardImportConfirm(importId))) }
    }

    // ---- cases --------------------------------------------------------------

    suspend fun cases(q: String): Result<Pair<Int, List<BoardCase>>> = runCatching {
        val d = data(api.boardCases(q = q, limit = 500))
        d["total"].int() to d["items"].list().map(::parseCase)
    }

    suspend fun case(id: Int): Result<BoardCase> = runCatching { parseCase(data(api.boardCase(id.toString()))) }

    /** The Daily Check switch only: the web case page has no other edit, so the app sends nothing else. */
    suspend fun caseEdit(id: Int, fields: Map<String, String>): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseEdit(id.toString(), fields))["case"].map()) }

    suspend fun caseRemove(id: Int): Result<Unit> = runCatching { api.boardCaseRemove(id.toString()); Unit }

    /** Update From Court: returns the case as read, and the balance after the charge. */
    suspend fun caseRead(id: Int): Result<Pair<BoardCase, Int>> = runCatching {
        val d = data(api.boardCaseRead(id.toString()))
        parseCase(d["case"].map()) to d["balance"].int()
    }

    /** Gives a hand-added case its CNR (free): the case as it now stands. */
    suspend fun caseAddCnr(id: Int, cnr: String): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseAddCnr(id.toString(), cnr))["case"].map()) }

    /** Add By CNR, step one: eCourts is asked (credits are taken now); the case it found and the lookup to confirm. */
    suspend fun cnrLookup(cnr: String): Result<CnrLookup> = runCatching { parseLookup(data(api.boardCnrLookup(cnr))) }

    /** Add By CNR, step two (free): the case found is added to the diary. */
    suspend fun cnrConfirm(lookupId: String): Result<BoardCase> = runCatching { parseCase(data(api.boardCnrConfirm(lookupId))["case"].map()) }

    /** A hand-added case (free). Never carries a CNR: the server refuses one. */
    suspend fun handAdd(fields: Map<String, String>): Result<BoardCase> = runCatching { parseCase(data(api.boardCaseAdd(fields - "cnr"))["case"].map()) }

    /** Set Next Date on a hand case. */
    suspend fun dateSet(id: Int, date: String): Result<BoardCase> = runCatching {
        val d = data(api.boardDateCorrect(id.toString(), date))
        parseCase(d["case"].map())
    }

    /** Add By CNR, the one-step call (kept for old builds): the new case, and the balance after the charge. */
    suspend fun caseByCnr(cnr: String): Result<Pair<BoardCase, Int>> = runCatching {
        val d = data(api.boardCaseByCnr(cnr))
        parseCase(d["case"].map()) to d["balance"].int()
    }

    // ---- the sheet ----------------------------------------------------------

    /** The cause list's PDF for a date in the counsel's own format; saved under cache/board/sheets. */
    suspend fun sheet(date: String): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val root = dir() ?: error("No cache directory")
            val sheets = File(root, "sheets").apply { mkdirs() }
            val file = File(sheets, "daily-board-$date-own.pdf")
            api.boardSheet(date).use { body ->
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

    /** One page of the credits history (after the first rows credits() carries). */
    suspend fun creditsHistory(limit: Int, offset: Int): Result<LedgerPage> = runCatching { parseLedgerPage(data(api.boardCreditsHistory(limit, offset, grouped = 1))) }

    /** Where an order stands, asked when the app returns from the pay page. */
    suspend fun order(order: String): Result<OrderStatus> = runCatching { parseOrder(data(api.boardCreditsOrder(order))) }
}
