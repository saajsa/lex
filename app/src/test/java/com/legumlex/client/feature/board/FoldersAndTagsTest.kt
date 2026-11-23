package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** REQ-038 038.5: filters, paging, folders and tags, as the web's board/cases. */
class FoldersAndTagsTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()

    /** An ApiService that records each call and answers [body]. */
    private fun api(body: Map<String, Any?>): ApiService =
        Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, a ->
            calls += m.name to a.orEmpty().dropLast(1); body
        } as ApiService

    @Test fun parsesFiltersAndAPage() {
        val f = parseFilters(mapOf("counts" to mapOf("all" to 240.0, "today" to 3.0), "courts" to listOf(mapOf("court_key" to "k1", "court" to "CJ Ludhiana", "cases" to 7.0)),
            "folders" to listOf(mapOf("id" to 2.0, "name" to "Bank Matters", "cases" to 5.0)), "tags" to emptyList<Any>()))
        assertEquals(240, f.counts["all"]); assertEquals(CourtOption("k1", "CJ Ludhiana", 7), f.courts[0]); assertEquals(BoardLabel(2, "Bank Matters", 5), f.folders[0])
        val p = parseCasePage(mapOf("total" to 240.0, "offset" to 100.0, "limit" to 100.0, "items" to listOf(
            mapOf("id" to 9.0, "folder" to mapOf("id" to 2.0, "name" to "Bank Matters"), "tags" to listOf(mapOf("id" to 4.0, "name" to "Urgent"))),
            mapOf("id" to 10.0, "folder" to null, "tags" to emptyList<Any>()),
        )))
        assertEquals(LabelRef(2, "Bank Matters"), p.items[0].folder); assertEquals(listOf(LabelRef(4, "Urgent")), p.items[0].tags)
        assertNull(p.items[1].folder); assertTrue(p.items[1].tags.isEmpty())
        assertEquals("101 to 200 of 240", pagerLine(p))
        assertEquals("201 to 240 of 240", pagerLine(p.copy(offset = 200)))
        assertNull(pagerLine(p.copy(total = 40, offset = 0)))
        assertEquals("1 to 100 of 1,200", pagerLine(p.copy(total = 1200, offset = 0)))
    }

    @Test fun wordsAsTheWeb() {
        assertEquals(listOf("All", "Needs Your Eye", "Undated", "Disposed"), STATUS_SEGMENTS.map { it.second })   // Listed Today/Tomorrow left the app
        assertEquals("Its 3 cases stay, in no folder.", deleteAsk(LabelKind.Folders, BoardLabel(1, "A", 3)))
        assertEquals("It comes off 1 case. The cases stay.", deleteAsk(LabelKind.Tags, BoardLabel(1, "A", 1)))
    }

    @Test fun sendsWhatTheApiTakes() = runBlocking {
        val repo = BoardRepository(api(mapOf("data" to mapOf("total" to 0.0, "items" to emptyList<Any>(), "case" to mapOf("id" to 9.0), "name" to "Urgent"))), null)
        repo.casePage(CaseQuery(), 0)
        repo.casePage(CaseQuery(filter = "eye", court = "k1", folder = "none", tag = 4, q = " x "), 100)
        repo.caseLabels(9, 0, linkedSetOf(4, 7))
        repo.caseLabels(9, 2, emptySet())
        repo.labelSave(LabelKind.Tags, 0, " Urgent ")
        repo.labelSave(LabelKind.Folders, 3, "B")
        repo.labelDelete(LabelKind.Folders, 3)
        assertEquals("boardCases" to listOf<Any?>("", 100, 0, "next", null, null, null, null), calls[0])
        assertEquals("boardCases" to listOf<Any?>("x", 100, 100, "next", "eye", "k1", "none", 4), calls[1])
        assertEquals("boardCaseLabels" to listOf<Any?>(9, 0, "4,7"), calls[2])
        assertEquals("boardCaseLabels" to listOf<Any?>(9, 2, ""), calls[3])
        assertEquals("boardLabelAdd" to listOf<Any?>("tags", "Urgent"), calls[4])
        assertEquals("boardLabelRename" to listOf<Any?>("folders", 3, "B"), calls[5])
        assertEquals("boardLabelDelete" to listOf<Any?>("folders", 3), calls[6])
    }
}

/** The filter sheet's options: Folder starts with In No Folder; each carries its count. */
class PickerOptionsTest {
    private val f = BoardFilters(emptyMap(), listOf(CourtOption("k1", "CJ Ludhiana", 7)), listOf(BoardLabel(2, "Bank Matters", 5)), listOf(BoardLabel(4, "Urgent", 1)))

    @Test fun options() {
        assertEquals(listOf("k1"), courtOptions(f).map { it.value }); assertEquals(7, courtOptions(f)[0].cases)
        assertEquals(listOf("none", "2"), folderOptions(f).map { it.value })
        assertEquals(listOf("Urgent"), tagOptions(f).map { it.name })
    }
}
