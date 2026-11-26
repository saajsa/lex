package com.legumlex.client.feature.demo

import android.content.Context
import android.os.Build
import android.util.Log
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.squareup.moshi.Moshi
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.UUID

/**
 * REQ-055: the app's own error reports, for the demo only. A crash is written to app-private storage (then the app crashes as
 * before); at the next launch the reports go to POST app/errors in one batch and the ones the server accepted are deleted.
 * Nothing is saved or sent unless the last sign-in was a demo one (`demo_host`, kept across sign-out): production is unchanged.
 */
object ErrorReports {
    private const val TAG = "ErrorReports"
    private const val PREFS = "app_errors"
    private const val HOST_KEY = "demo_host"
    @Volatile private var app: Context? = null

    /** Called once from Application.onCreate: chain the crash handler, then send what is waiting. */
    fun install(context: Context) {
        val ctx = context.applicationContext
        app = ctx
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { if (demoHost(ctx)) save(dir(ctx), buildReport(e, thread.name, screen, Instant.now().toString(), UUID.randomUUID().toString())) }
            previous?.uncaughtException(thread, e)
        }
        if (demoHost(ctx)) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { sendPending(ctx) }.onFailure { Log.w(TAG, "send failed", it) } }
    }

    /** The route on screen, for the report (set by the shell as it changes). */
    @Volatile var screen: String = ""

    /** The sign-in's demo mark, kept for the next launch (a sign-out does not clear it: a crash on the sign-in screen still counts). */
    fun markHost(demo: Boolean) {
        val ctx = app ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HOST_KEY, demo).apply()
        if (!demo) dir(ctx).listFiles()?.forEach { it.delete() }
    }

    private fun demoHost(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HOST_KEY, false)

    private fun dir(ctx: Context) = File(ctx.filesDir, "app_errors").apply { mkdirs() }

    private suspend fun sendPending(ctx: Context) {
        val files = pending(dir(ctx), System.currentTimeMillis())
        if (files.isEmpty()) return
        val reports = files.mapNotNull { f -> readReport(f) }
        val res = ApiClient.get(ctx).appErrors(mapOf("reports" to reports))
        val accepted = ((res["accepted"] as? List<*>) ?: (res["data"] as? Map<*, *>)?.get("accepted") as? List<*>).orEmpty().map { it.toString() }.toSet()
        files.forEach { f -> if (f.nameWithoutExtension in accepted) f.delete() }
    }
}

/** At most this many reports are kept, newest first; older than [MAX_AGE_MS] are dropped. */
internal const val MAX_REPORTS = 10
internal const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
internal const val MESSAGE_CAP = 500
internal const val STACK_CAP = 16 * 1024

/** One report in the contract's shape (REQ-055): no request bodies, tokens or screen contents; the message and stack capped. */
internal fun buildReport(e: Throwable, thread: String, screen: String, at: String, id: String): Map<String, Any?> {
    val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
    return mapOf(
        "id" to id, "at" to at,
        "app_version" to BuildConfig.VERSION_NAME, "version_code" to BuildConfig.VERSION_CODE,
        "os" to "Android ${Build.VERSION.RELEASE ?: ""}".trim(), "device" to listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
        "screen" to screen, "thread" to thread,
        "exception" to e.javaClass.name,
        "message" to e.message.orEmpty().take(MESSAGE_CAP),
        "stack" to sw.toString().take(STACK_CAP),
    )
}

/** Writes [report] as <id>.json, then keeps only the newest [MAX_REPORTS]. */
internal fun save(dir: File, report: Map<String, Any?>) {
    File(dir, "${report["id"]}.json").writeText(reportJson.toJson(report))
    dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(MAX_REPORTS)?.forEach { it.delete() }
}

/** The reports to send: younger than [MAX_AGE_MS] (older ones are deleted), newest first, at most [MAX_REPORTS]. */
internal fun pending(dir: File, now: Long): List<File> {
    val all = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedByDescending { it.lastModified() }
    val (fresh, stale) = all.partition { now - it.lastModified() <= MAX_AGE_MS }
    stale.forEach { it.delete() }
    return fresh.take(MAX_REPORTS)
}

private val reportJson = Moshi.Builder().build().adapter(Map::class.java)

/** A saved report, as sent: version_code back to a whole number (JSON reads it as a decimal). Null when the file is unreadable. */
@Suppress("UNCHECKED_CAST")
internal fun readReport(f: File): Map<String, Any?>? = runCatching {
    val m = (reportJson.fromJson(f.readText()) as? Map<String, Any?>) ?: return null
    m + ("version_code" to (m["version_code"] as? Number)?.toInt())
}.getOrNull()
