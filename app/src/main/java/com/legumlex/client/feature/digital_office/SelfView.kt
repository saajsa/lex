package com.legumlex.client.feature.digital_office

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/* ============================================================================
 *  The client's own side of the call panel: their front camera in the "You"
 *  tile, and a live level for their microphone. A mirror only — nothing is
 *  recorded, kept or sent; the consultation itself is Google Meet's, which
 *  asks for the camera and microphone again on its own.
 *
 *  Each is asked for when the client turns it on, never on opening the tab,
 *  and each is let go the moment it is turned off, the tab is left or the app
 *  goes to the background.
 * ========================================================================== */

class SelfView(
    val camOn: Boolean,
    val micOn: Boolean,
    /** 0..1, the microphone's current loudness; 0 while it is off. */
    val level: Float,
    val toggleCam: () -> Unit,
    val toggleMic: () -> Unit,
)

private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberSelfView(): SelfView {
    val ctx = LocalContext.current
    // The camera comes back on by itself only if the client already allowed it.
    var camOn by remember { mutableStateOf(granted(ctx, Manifest.permission.CAMERA)) }
    var micOn by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }

    val camAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camOn = it }
    val micAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOn = it }

    val owner = LocalLifecycleOwner.current
    LaunchedEffect(micOn) {
        level = 0f
        if (!micOn || !granted(ctx, Manifest.permission.RECORD_AUDIO)) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) { listen { level = it } }
        }
        level = 0f
    }

    return SelfView(
        camOn = camOn, micOn = micOn, level = level,
        toggleCam = {
            if (camOn) camOn = false
            else if (granted(ctx, Manifest.permission.CAMERA)) camOn = true
            else camAsk.launch(Manifest.permission.CAMERA)
        },
        toggleMic = {
            if (micOn) micOn = false
            else if (granted(ctx, Manifest.permission.RECORD_AUDIO)) micOn = true
            else micAsk.launch(Manifest.permission.RECORD_AUDIO)
        },
    )
}

/** Reads the microphone until cancelled, reporting loudness (RMS, 0..1). Nothing is kept. */
@SuppressLint("MissingPermission") // checked by the caller
private suspend fun listen(onLevel: (Float) -> Unit) {
    val rate = 16_000
    val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
    val rec = runCatching {
        AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
    }.getOrNull() ?: return
    if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
    val buf = ShortArray(size / 2)
    try {
        rec.startRecording()
        var smooth = 0f
        while (kotlin.coroutines.coroutineContext.isActive) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) continue
            var sum = 0.0
            for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
            val rms = (sqrt(sum / n) / 6000.0).toFloat().coerceIn(0f, 1f)
            smooth = smooth * 0.6f + rms * 0.4f
            onLevel(smooth)
        }
    } finally {
        runCatching { rec.stop() }
        rec.release()
        onLevel(0f)
    }
}

/** The front camera, live, mirrored as a selfie is; released when this leaves the screen. */
@Composable
fun CameraSelf(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val provider = remember { ProcessCameraProvider.getInstance(ctx) }
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        var gone = false
        provider.addListener({
            if (!gone) runCatching { provider.get().bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview) }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            gone = true
            // Only this preview: the video lobby may hold the camera too.
            runCatching { if (provider.isDone) provider.get().unbind(preview) }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
