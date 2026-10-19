package com.legumlex.client.data.api

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Read a content Uri (from ActivityResultContracts.OpenDocument / GetContent
 * / camera scanner output) into an in-memory MultipartBody.Part suitable for
 * the customers_api multipart endpoints.
 *
 * Reads bytes on the IO dispatcher. Returns null only if the resolver cannot
 * open a stream for the Uri — call sites should surface that as a user-facing
 * "couldn't read file" toast.
 */
suspend fun uriToFilePart(
    context: Context,
    uri: Uri,
    fieldName: String = "file",
): MultipartBody.Part? = withContext(Dispatchers.IO) {
    val resolver: ContentResolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
    val name  = queryDisplayName(resolver, uri) ?: "upload.bin"
    val mime  = resolver.getType(uri) ?: "application/octet-stream"
    val body  = bytes.toRequestBody(mime.toMediaTypeOrNull())
    MultipartBody.Part.createFormData(fieldName, name, body)
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx) else null
        } else null
    }
