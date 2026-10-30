package com.legumlex.client.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.Surface1
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The Test copy updates itself (owner, 2026-10-30): on opening, and on coming
 * back to it at most once an hour, it asks www.legumlex.com for the newest
 * Test build (customers_api app_update/latest, for contacts on app_test_contacts). A newer one is offered in a dialog;
 * Update downloads it, checks its sha256, and installs it in place through
 * PackageInstaller, keeping the sign-in. Android asks once for "Install
 * unknown apps" for this app. Only in the preview build type.
 */
private data class TestBuild(val code: Int, val name: String, val notes: String, val size: Long, val sha256: String)

private sealed interface Step {
    data object Idle : Step
    data class Offer(val b: TestBuild) : Step
    data class Downloading(val b: TestBuild, val done: Long) : Step
    data class Failed(val b: TestBuild, val message: String) : Step
    data class NeedsPermission(val b: TestBuild, val file: File) : Step
}

private object Updater {
    val step = MutableStateFlow<Step>(Step.Idle)
    private var lastCheck = 0L
    private var dismissedCode = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    }

    private suspend fun token(ctx: Context) = TokenStore(ctx).token.first()

    fun check(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 60 * 60 * 1000L) return
        if (step.value !is Step.Idle) return
        scope.launch {
            // Signed out: nothing to ask yet, and the hour has not started.
            val tok = token(ctx)?.takeIf { it.isNotBlank() } ?: return@launch
            lastCheck = now
            android.util.Log.i("TestUpdate", "checking for a newer Test build than ${BuildConfig.VERSION_CODE}")
            val b = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(BuildConfig.API_BASE_URL + "app_update/latest")
                        .header("Authorization", "Bearer $tok").build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use null
                        val d = JSONObject(r.body?.string().orEmpty()).optJSONObject("data") ?: return@use null
                        TestBuild(d.optInt("version_code"), d.optString("version_name"), d.optString("notes"),
                            d.optLong("size"), d.optString("sha256"))
                    }
                }.onFailure { android.util.Log.w("TestUpdate", "check failed", it) }.getOrNull()
            } ?: return@launch
            android.util.Log.i("TestUpdate", "newest published: ${b.code}")
            if (b.code > BuildConfig.VERSION_CODE && b.code != dismissedCode) step.value = Step.Offer(b)
        }
    }

    fun later(b: TestBuild) { dismissedCode = b.code; step.value = Step.Idle }

    fun update(ctx: Context, b: TestBuild) {
        scope.launch {
            step.value = Step.Downloading(b, 0)
            val file = File(ctx.cacheDir, "updates/${b.code}.apk").apply { parentFile?.mkdirs() }
            val err = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(BuildConfig.API_BASE_URL + "app_update/download")
                        .header("Authorization", "Bearer ${token(ctx).orEmpty()}").build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@runCatching "The server said ${r.code}."
                        val md = MessageDigest.getInstance("SHA-256")
                        var done = 0L
                        r.body!!.byteStream().use { input ->
                            file.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n); md.update(buf, 0, n); done += n
                                    step.value = Step.Downloading(b, done)
                                }
                            }
                        }
                        val got = md.digest().joinToString("") { "%02x".format(it) }
                        if (got != b.sha256) "The download arrived damaged. Try again." else null
                    }
                }.getOrElse { "Couldn't download: ${it.message ?: "no connection"}." }
            }
            if (err != null) { file.delete(); step.value = Step.Failed(b, err); return@launch }
            install(ctx, b, file)
        }
    }

    fun install(ctx: Context, b: TestBuild, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            step.value = Step.NeedsPermission(b, file)
            return
        }
        runCatching {
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("app.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val intent = Intent(ctx, InstallResultReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
            step.value = Step.Idle
        }.onFailure { step.value = Step.Failed(b, "Couldn't install: ${it.message}") }
    }

    fun openPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dismiss() { step.value = Step.Idle }
}

/** Android's answer to an install: it may need the user to confirm. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // the app restarts as the new version
            else -> android.util.Log.w("ClientApp", "Test update not installed: " +
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}

@Composable
fun TestUpdateGate() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                // Back from "Install unknown apps": carry on with the install.
                val s = Updater.step.value
                if (s is Step.NeedsPermission && ctx.packageManager.canRequestPackageInstalls()) Updater.install(ctx, s.b, s.file)
                else Updater.check(ctx)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val step by Updater.step.collectAsState()
    val s = step
    if (s is Step.Idle) return
    Dialog(onDismissRequest = { if (s !is Step.Downloading) Updater.dismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1).padding(22.dp),
        ) {
            val b = when (s) {
                is Step.Offer -> s.b; is Step.Downloading -> s.b; is Step.Failed -> s.b; is Step.NeedsPermission -> s.b
                else -> null
            } ?: return@Column
            Text("Update Available", color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text("${b.name} (${b.code}) · ${"%.1f".format(b.size / 1048576.0)} MB", color = SecondaryText, fontSize = 13.sp)
            if (b.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(b.notes, color = PrimaryText, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(16.dp))
            when (s) {
                is Step.Downloading -> {
                    LinearProgressIndicator(
                        progress = { if (b.size > 0) (s.done.toFloat() / b.size).coerceIn(0f, 1f) else 0f },
                        color = AccentInk, trackColor = Surface2, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading…", color = SecondaryText, fontSize = 12.sp)
                }
                is Step.NeedsPermission -> {
                    Text("Android asks once to let Legum Lex Test install its updates. Allow it on the next screen, then come back.",
                        color = SecondaryText, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(14.dp))
                    Buttons("Not Now", { Updater.later(b) }, "Allow", { Updater.openPermission(ctx) })
                }
                is Step.Failed -> {
                    Text(s.message, color = StatusDanger.solid, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Buttons("Later", { Updater.later(b) }, "Try Again", { Updater.update(ctx, b) })
                }
                else -> Buttons("Later", { Updater.later(b) }, "Update", { Updater.update(ctx, b) })
            }
        }
    }
}

@Composable
private fun Buttons(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Surface2).clickable(onClick = onLeft).padding(vertical = 14.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { Text(left, color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(AccentInk).clickable(onClick = onRight).padding(vertical = 14.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { Text(right, color = InverseText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
    }
}
