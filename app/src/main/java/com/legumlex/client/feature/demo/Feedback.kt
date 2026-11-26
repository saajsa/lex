package com.legumlex.client.feature.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.WizardDialog
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardTextArea
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.ByteArrayOutputStream

internal const val FEEDBACK_MAX = 4000
internal const val SHOT_MAX_BYTES = 5 * 1024 * 1024
internal const val SHOT_LONGEST_PX = 1600

/** The text fields of POST app/feedback (REQ-055 contract), in order; the screenshot part is added beside them. */
internal fun feedbackFields(message: String, screen: String, os: String, device: String): List<Pair<String, String>> = listOf(
    "message" to message.trim(),
    "screen" to screen,
    "app_version" to BuildConfig.VERSION_NAME,
    "version_code" to BuildConfig.VERSION_CODE.toString(),
    "os" to os,
    "device" to device,
)

/** A message the server will take: 1 to 4000 characters once trimmed. */
internal fun feedbackReady(message: String): Boolean = message.trim().length in 1..FEEDBACK_MAX

/** The size a screenshot is scaled to: its longest side at most [SHOT_LONGEST_PX], never enlarged. */
internal fun shotSize(w: Int, h: Int): Pair<Int, Int> {
    val longest = maxOf(w, h)
    if (longest <= SHOT_LONGEST_PX || longest <= 0) return w to h
    val k = SHOT_LONGEST_PX.toDouble() / longest
    return maxOf(1, (w * k).toInt()) to maxOf(1, (h * k).toInt())
}

/** The picked image as a JPEG at most [SHOT_LONGEST_PX] on its longest side; null when it cannot be read or is still over 5 MB. */
private suspend fun shotJpeg(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
        val (w, h) = shotSize(src.width, src.height)
        val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray().takeIf { it.size <= SHOT_MAX_BYTES }
    }.getOrNull()
}

/** The server's words for a refusal, else a plain line. */
private fun refusalText(e: Throwable): String =
    (e as? HttpException)?.response()?.errorBody()?.string()?.let { runCatching { JSONObject(it).optString("message") }.getOrNull() }?.takeIf { it.isNotBlank() }
        ?: "Your feedback could not be sent. Check your connection and try again."

/** A large icon on a round muted badge, leading each step (visual-first rule). */
@Composable
private fun StepPicture(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(Lex.colors.muted), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lex.colors.foreground, modifier = Modifier.size(44.dp))
        }
    }
}

/**
 * Send Feedback (REQ-055, demo only): two steps, Your Message then an optional Screenshot from Photos, to POST app/feedback. The server's
 * message is shown on success through [onSent]; a refusal shows its words and keeps the draft.
 */
@Composable
fun FeedbackWizard(onClose: () -> Unit, onSent: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { shot = uri.toString(); error = null } }

    WizardDialog(onDismiss = { if (!sending) onClose() }) {
        WizardFrame(
            title = "Send Feedback", steps = listOf("Your Message", "Screenshot"), step = step,
            ready = step == 1 || feedbackReady(message), saving = sending, saveLabel = "Send", savingLabel = "Sending…",
            error = error, onClose = { if (!sending) onClose() }, onStep = { error = null; step = it },
            onSave = {
                sending = true; error = null
                scope.launch {
                    val parts = feedbackFields(message, ErrorReports.screen, "Android ${Build.VERSION.RELEASE}", "${Build.MANUFACTURER} ${Build.MODEL}")
                        .map { (k, v) -> MultipartBody.Part.createFormData(k, v) }.toMutableList()
                    val uri = shot?.let(Uri::parse)
                    if (uri != null) {
                        val jpeg = shotJpeg(context, uri)
                        if (jpeg == null) { error = "That image could not be attached. Choose another, or send without it."; sending = false; return@launch }
                        parts += MultipartBody.Part.createFormData("screenshot", "screenshot.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
                    }
                    runCatching { ApiClient.get(context).appFeedback(parts) }
                        .onSuccess { r -> onSent((r["message"] as? String)?.takeIf { it.isNotBlank() } ?: "Thank you. Your feedback has been sent.") }
                        .onFailure { e -> error = refusalText(e) }
                    sending = false
                }
            },
        ) {
            if (step == 0) {
                StepPicture(Icons.Outlined.Feedback)
                WizardHeading("Your Message", "")
                WizardTextArea(message, { message = it }, placeholder = "What worked, what did not, what you expected", maxChars = FEEDBACK_MAX, minHeight = 160)
            } else {
                StepPicture(Icons.Outlined.AddPhotoAlternate)
                WizardHeading("Screenshot", "")
                Text("Optional. Take a screenshot of the screen first, then choose it here.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground)
                Spacer(Modifier.height(14.dp))
                shot?.let { s ->
                    coil.compose.AsyncImage(
                        model = Uri.parse(s), contentDescription = "The screenshot to send", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.height(10.dp))
                    LegumSecondaryButton("Remove Screenshot", { shot = null }, enabled = !sending)
                } ?: LegumSecondaryButton("Attach Screenshot", {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = !sending)
            }
        }
    }
}
