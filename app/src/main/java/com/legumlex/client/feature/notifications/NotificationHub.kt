package com.legumlex.client.feature.notifications

import android.content.Context
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide unread-count source shared by the header bell badge and the
 * Notifications screen, so marking things read updates the badge immediately
 * without a round-trip. Plain singleton (the app uses no DI).
 */
object NotificationHub {

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    fun set(count: Int) { _unread.value = count.coerceAtLeast(0) }

    fun decrement(by: Int = 1) { _unread.value = (_unread.value - by).coerceAtLeast(0) }

    fun clear() { _unread.value = 0 }

    /** Fetch the server unread count (best-effort; ignored on failure). */
    suspend fun refresh(context: Context) {
        runCatching {
            val resp = ApiClient.get(context).notificationsUnreadCount()
            @Suppress("UNCHECKED_CAST")
            val data = resp["data"] as? Map<String, Any?>
            (data?.get("unread_count") as? Number)?.toInt()
        }.getOrNull()?.let { set(it) }
    }
}
