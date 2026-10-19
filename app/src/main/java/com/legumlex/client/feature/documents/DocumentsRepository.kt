package com.legumlex.client.feature.documents

import android.content.Context
import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DocumentsRepository(
    private val api: ApiService,
    private val context: Context,
) {
    suspend fun list(
        category: DocCategory = DocCategory.ALL,
        query: String? = null,
        caseId: String? = null,
    ): Result<List<Document>> = runCatching {
        val response = api.documents(
            relType = category.apiRelType,
            query   = query?.trim()?.takeIf { it.isNotEmpty() },
            caseId  = caseId?.takeIf { it.isNotBlank() },
        )
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parse)
    }

    // Wizard helper: fetch documents tied to a specific parent entity
    // (a single hearing or a single consultation).
    suspend fun listForRel(relType: String, relId: String): Result<List<Document>> = runCatching {
        val response = api.documents(relType = relType, relId = relId)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parse)
    }

    suspend fun download(doc: Document): Result<File> =
        downloadById(doc.id, doc.displayName.ifBlank { doc.storedFileName })

    /**
     * Generic download by id — used by surfaces that aren't backed by the
     * full Document model (consultation attachments, hearing orders, etc.).
     * Caches to <cache>/docs/{id}_<safeName> and is a no-op fetch if already
     * present.
     */
    suspend fun downloadById(id: String, displayName: String): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            val targetDir = File(context.cacheDir, "docs").apply { mkdirs() }
            val safeName  = displayName
                .ifBlank { "file" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target    = File(targetDir, "${id}_$safeName")
            if (target.exists() && target.length() > 0) return@withContext target

            val body = api.downloadDocument(id)
            body.byteStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            target
        }
    }
}

private fun parse(m: Map<String, Any?>): Document {
    val category = DocCategory.fromApi(m["rel_type"] as? String)
    val sourceLabel = (m["source_label"] as? String).orEmpty()
        .ifBlank { category.label }
    val sourceContext = (m["source_context"] as? String)?.takeIf { it.isNotBlank() }
    val uploadedBy = (m["uploaded_by"] as? String)?.trim()?.takeIf { it.isNotBlank() }

    return Document(
        id              = m["id"]?.toString() ?: "",
        displayName     = (m["display_name"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (m["file_name"] as? String) ?: "Untitled",
        storedFileName  = m["file_name"] as? String ?: "",
        mimeType        = (m["filetype"] as? String)?.takeIf { it.isNotBlank() },
        fileExtension   = (m["file_extension"] as? String)?.takeIf { it.isNotBlank() }
                            ?: (m["file_type"] as? String)?.takeIf { it.isNotBlank() },
        // Hide internal doc-request fulfilment tags (doc_req_<rid>) — they're
        // a binding key, not a user-facing label.
        tag             = (m["tag"] as? String)?.takeIf { it.isNotBlank() && !it.startsWith("doc_req_") },
        category        = category,
        sourceLabel     = sourceLabel,
        sourceContext   = sourceContext,
        uploadedBy      = uploadedBy,
        uploadedAt      = parseDateTime((m["date_added"] as? String) ?: (m["dateadded"] as? String)),
    )
}

private val datetimePatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

private fun parseDateTime(s: String?): LocalDateTime? {
    if (s.isNullOrBlank()) return null
    for (p in datetimePatterns) {
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}
