package com.legumlex.client.ui.theme

import android.content.Context

/**
 * The client's choice of theme, as the portal's Dark Mode switch keeps one
 * (mylex kt_topbar.php, localStorage kt-theme): Follow Phone, Light or Dark.
 * Kept on the device; read synchronously at start so the first frame is right.
 */
enum class ThemeChoice(val label: String) { SYSTEM("Follow Phone"), LIGHT("Light"), DARK("Dark") }

object ThemePrefs {
    private const val FILE = "appearance"
    private const val KEY = "theme"

    val choice = kotlinx.coroutines.flow.MutableStateFlow(ThemeChoice.SYSTEM)

    fun load(context: Context) {
        val v = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        choice.value = ThemeChoice.entries.firstOrNull { it.name == v } ?: ThemeChoice.SYSTEM
    }

    fun set(context: Context, c: ThemeChoice) {
        choice.value = c
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, c.name).apply()
    }
}
