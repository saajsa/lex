package com.legumlex.client.feature.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Thin Compose binding around ML Kit's GmsDocumentScanner — returns a single
 * launch function that opens the camera scanner and hands the resulting PDF
 * Uri (or null on cancel / error) to [onResult]. No wizard UI; the scanner
 * itself owns capture / review / multi-page handling.
 *
 * The staff app has a richer wrapper with rename, tag and case-picker chrome
 * (see legum-lex-staff feature/scanner/). Clients need none of that —
 * they're filling a specific doc-request, so the file just goes straight to
 * the upload action.
 */
@Composable
fun rememberDocumentScannerLauncher(
    onResult: (pdfUri: Uri?) -> Unit,
): () -> Unit {
    val context = LocalContext.current

    val sysLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        onResult(scan?.pdf?.uri)
    }

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    return remember(context, sysLauncher) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                onResult(null)
            } else {
                GmsDocumentScanning.getClient(options)
                    .getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        sysLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener { onResult(null) }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
