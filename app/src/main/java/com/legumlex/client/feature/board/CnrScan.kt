package com.legumlex.client.feature.board

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.legumlex.client.ui.theme.Lex

/** A CNR is sixteen characters: four letters, eight letters or digits, four digits (the server's ^[A-Z]{4}[A-Z0-9]{8}[0-9]{4}$). */
private val CnrPattern = Regex("(?<![A-Z0-9])[A-Z]{4}[A-Z0-9]{8}[0-9]{4}(?![A-Z0-9])")

/** The first CNR in a scanned text (a bare CNR, or a link that holds one), else null. The text is upper-cased first. */
fun extractCnr(text: String?): String? = text?.uppercase()?.let { CnrPattern.find(it)?.value }

/**
 * A scan symbol for the CNR field. It opens Google's code scanner (no camera permission of ours) and hands the
 * CNR found in the code to [onCnr]; nothing else happens, the person still presses Next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnrScanButton(onCnr: (String) -> Unit) {
    val context = LocalContext.current
    TooltipBox(
        positionProvider = OnScreenTooltip,
        tooltip = { PlainTooltip { Text("Scan the case's QR code") } },
        state = rememberTooltipState(),
    ) {
        Icon(
            Icons.Outlined.QrCodeScanner, contentDescription = "Scan the case's QR code", tint = Lex.colors.foreground,
            modifier = Modifier.size(28.dp).clickable {
                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { code ->
                        val cnr = extractCnr(code.rawValue)
                        if (cnr != null) onCnr(cnr) else Toast.makeText(context, "This code has no CNR in it.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        // A closed scanner is not an error; anything else is said in plain words.
                        if (e !is com.google.android.gms.common.api.ApiException || e.statusCode != 13) {
                            Toast.makeText(context, "The scanner could not open. Type the CNR instead.", Toast.LENGTH_LONG).show()
                        }
                    }
            },
        )
    }
}
