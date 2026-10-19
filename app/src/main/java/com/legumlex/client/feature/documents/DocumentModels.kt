package com.legumlex.client.feature.documents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDateTime

// Server-side rel_type values. Mapped to a friendly Vault category that the
// app uses for filter chips and section headers.
enum class DocCategory(
    val label: String,
    val apiRelType: String?,   // null for "All"
    val icon: ImageVector,
) {
    ALL           ("All",           null,             Icons.AutoMirrored.Outlined.LibraryBooks),
    CASE          ("Cases",         "case",           Icons.Outlined.Description),
    HEARING       ("Hearings",      "hearing",        Icons.Outlined.Gavel),
    LEGAL_NOTICE  ("Notices",       "legal_notice",   Icons.AutoMirrored.Outlined.Send),
    CONSULTATION  ("Sessions",      "consultation",   Icons.Outlined.Forum),
    AGREEMENT     ("Agreements",    "clm_contract",   Icons.Outlined.Handshake),
    DRAFT         ("Drafts",        "agreement_draft",Icons.AutoMirrored.Outlined.Article),
    SHARED        ("Shared",        "client",         Icons.Outlined.Share);

    companion object {
        fun fromApi(rel: String?): DocCategory = when (rel?.lowercase()) {
            "case"             -> CASE
            "hearing"          -> HEARING
            "legal_notice"     -> LEGAL_NOTICE
            "consultation"     -> CONSULTATION
            "clm_contract"     -> AGREEMENT
            "agreement_draft"  -> DRAFT
            "client"           -> SHARED
            else               -> ALL
        }
    }
}

data class Document(
    val id: String,
    val displayName: String,
    val storedFileName: String,
    val mimeType: String?,
    val fileExtension: String?,
    val tag: String?,
    val category: DocCategory,
    val sourceLabel: String,         // "Case file" / "Hearing exhibit" / etc.
    val sourceContext: String?,      // case title, hearing date, notice subject…
    val uploadedBy: String?,         // staff full name, may be blank
    val uploadedAt: LocalDateTime?,
)

fun Document.iconForType(): ImageVector = when (fileExtension?.lowercase()) {
    "pdf"                                      -> Icons.Outlined.PictureAsPdf
    "doc", "docx", "rtf", "odt", "txt"         -> Icons.Outlined.Description
    "xls", "xlsx", "csv", "ods"                -> Icons.Outlined.TableChart
    "jpg", "jpeg", "png", "gif", "bmp", "webp" -> Icons.Outlined.Image
    "mp4", "mov", "avi", "mkv", "webm"         -> Icons.Outlined.VideoFile
    else                                       -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
