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
        if (!savedCopyAllowed(err.isOffline(), err.apiError().http)) return Result.failure(err)
        val saved = cachedDay(date) ?: return Result.failure(err)
        return Result.success(DayLoad(saved.day, saved.savedAt))
    }

    /** 038.7: one case page from the server (kept on the device), else the kept copy and when it was saved, under the same rule as the day. */
    suspend fun caseOrSaved(id: Int): Result<Pair<BoardCase, Long?>> {
        val live = runCatching { data(api.boardCase(id.toString())) }
        live.getOrNull()?.let { raw -> saveJson("case_$id", raw, "case_", 60); return runCatching { parseCase(raw) to null } }
        val err = live.exceptionOrNull()!!
        if (!savedCopyAllowed(err.isOffline(), err.apiError().http)) return Result.failure(err)
        val (raw, at) = readJson("case_$id") ?: return Result.failure(err)
        return runCatching { parseCase(raw) to at }
    }

    /** 038.7: a Cases page, kept per query and page, else the kept copy (same rule). */
    suspend fun casePageOrSaved(q: CaseQuery, offset: Int): Result<Pair<CasePage, Long?>> {
        val name = "cases_" + casesKey(q, offset)
        val live = runCatching {
            data(api.boardCases(q = q.q.trim(), limit = CASES_PAGE, offset = offset, filter = q.filter.takeIf { it != "all" },
                court = q.court.ifBlank { null }, folder = q.folder.ifBlank { null }, tag = q.tag.takeIf { it > 0 }))
        }
        live.getOrNull()?.let { raw -> saveJson(name, raw, "cases_", 20); return runCatching { parseCasePage(raw) to null } }
        val err = live.exceptionOrNull()!!
        if (!savedCopyAllowed(err.isOffline(), err.apiError().http)) return Result.failure(err)
        val (raw, at) = readJson(name) ?: return Result.failure(err)
        return runCatching { parseCasePage(raw) to at }
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

    /** A server answer kept as JSON under cache/board ([prefix] files beyond [keep] are dropped, oldest first). */
    private suspend fun saveJson(name: String, raw: Map<String, Any?>, prefix: String, keep: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val d = dir() ?: return@runCatching
            File(d, "$name.json").writeText(json.toJson(raw))
            d.listFiles { x -> x.name.startsWith(prefix) }?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
        }
    }

    /** A kept answer and when it was saved; null when none, or unreadable. */
    private suspend fun readJson(name: String): Pair<Map<String, Any?>, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val f = dir()?.let { File(it, "$name.json") }?.takeIf { it.isFile } ?: return@runCatching null
            @Suppress("UNCHECKED_CAST")
            (json.fromJson(f.readText()) as? Map<String, Any?>)?.let { it to f.lastModified() }
        }.getOrNull()
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

    /** One page of the Cases list (REQ-038 038.5): the chip's filter, the court, folder and tag pickers, the search. */
    suspend fun casePage(q: CaseQuery, offset: Int): Result<CasePage> = casePageOrSaved(q, offset).map { it.first }

    /** REQ-038 038.2/038.3: the case's hearings and orders. */
    suspend fun orders(caseId: Int): Result<CaseRecord> = runCatching { parseRecord(data(api.boardOrders(caseId))) }

    /** Fetch one order's copy (charged once by the server; a kept copy is free). */
    suspend fun orderFetch(caseId: Int, orderId: Int): Result<OrderFetch> = runCatching { parseOrderFetch(data(api.boardOrderFetch(caseId, orderId))) }

    /** A kept order's file: from this device when it is here (free, works offline), else downloaded and kept under cache/board/orders. */
    suspend fun orderFile(caseId: Int, orderId: Int): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val f = File(ordersDir() ?: error("No cache directory"), "order_${caseId}_$orderId.pdf")
            if (!f.isFile || f.length() == 0L) download(f) { api.boardOrderFile(caseId, orderId) }
            f
        }
    }

    /**
     * The combined PDF, made fresh by the server (free), with the ids it left out (its X-Orders-Left-Out header). The last one is kept for
     * when the server cannot be reached; that copy's left-out list is not known, so it comes back empty.
     */
    suspend fun combined(caseId: Int): Result<Pair<File, List<Int>>> {
        val f = ordersDir()?.let { File(it, "combined_$caseId.pdf") } ?: return Result.failure(IllegalStateException("No cache directory"))
        val live = runCatching {
            withContext(Dispatchers.IO) {
                val r = api.boardOrdersCombined(caseId)
                if (!r.isSuccessful) throw retrofit2.HttpException(r)
                download(f) { r.body() ?: error("No file") }
                f to leftOutIds(r.headers()["X-Orders-Left-Out"])
            }
        }
        val err = live.exceptionOrNull() ?: return live
        return if (savedCopyAllowed(err.isOffline(), err.apiError().http) && f.isFile && f.length() > 0) Result.success(f to emptyList()) else Result.failure(err)
    }

    private fun ordersDir(): File? = dir()?.let { File(it, "orders").apply { mkdirs() } }

    /** Writes a body to a temp file first, so a broken download never leaves half a PDF behind. */
    private suspend fun download(to: File, call: suspend () -> okhttp3.ResponseBody) {
        val tmp = File(to.parentFile, to.name + ".part")
        call().use { body -> body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } } }
        if (!tmp.renameTo(to)) { tmp.copyTo(to, overwrite = true); tmp.delete() }
    }

    /** The summary to share: the case's own share_text, else the server's share answer. */
    suspend fun shareText(c: BoardCase): Result<String> =
        if (c.shareText.isNotBlank()) Result.success(c.shareText) else runCatching { data(api.boardCaseShare(c.id))["text"].str() }

    suspend fun alerts(): Result<List<BoardAlert>> = runCatching { parseAlerts(data(api.boardAlerts(50))) }

    suspend fun filters(): Result<BoardFilters> = runCatching { parseFilters(data(api.boardFilters())) }

    /** Add ([id] 0) or rename a folder or tag; the saved name comes back. */
    suspend fun labelSave(kind: LabelKind, id: Int, name: String): Result<String> = runCatching {
        val r = if (id > 0) api.boardLabelRename(kind.wire, id, name.trim()) else api.boardLabelAdd(kind.wire, name.trim())
        data(r)["name"].str().ifBlank { name.trim() }
    }

    suspend fun labelDelete(kind: LabelKind, id: Int): Result<Unit> = runCatching { api.boardLabelDelete(kind.wire, id); Unit }

    /** File a case: its folder (0 for none) and its whole set of tags. */
    suspend fun caseLabels(id: Int, folderId: Int, tagIds: Collection<Int>): Result<BoardCase> = runCatching {
        parseCase(data(api.boardCaseLabels(id, folderId, tagIds.joinToString(",")))["case"].map())
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

    /** REQ-038 038.4: the days from..to, each as board/day gives it. */
    suspend fun range(from: String, to: String): Result<BoardRange> = runCatching { parseRange(data(api.boardRange(from, to))) }

    /** The range's PDF, saved under cache/board/sheets. */
    suspend fun rangeSheet(from: String, to: String): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val sheets = File(dir() ?: error("No cache directory"), "sheets").apply { mkdirs() }
            val file = File(sheets, "cause-list-$from-to-$to.pdf")
            api.boardRangeSheet(from, to).use { body -> body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } } }
            file
        }
    }

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

/** 038.7: a kept copy answers only when the server could not be reached or failed (5xx); a refusal (403, 404, 4xx) never falls back. */
internal fun savedCopyAllowed(offline: Boolean, http: Int?): Boolean = offline || (http != null && http >= 500)

/** The file key of one Cases page: the query and the page, made file-safe. */
internal fun casesKey(q: CaseQuery, offset: Int): String =
    listOf(q.filter, q.court, q.folder, q.tag.toString(), q.q.trim().lowercase(), offset.toString()).joinToString("|").hashCode().toUInt().toString(16)
