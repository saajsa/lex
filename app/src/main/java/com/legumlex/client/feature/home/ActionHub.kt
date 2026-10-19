package com.legumlex.client.feature.home

import android.content.Context
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The action queue, app-wide: the header's queue button shows its count and
 * the Action Queue screen lists it, so both read one copy (as NotificationHub
 * does for the bell). Null until first read.
 */
object ActionHub {

    private val _bundle = MutableStateFlow<ActionItemsBundle?>(null)
    val bundle: StateFlow<ActionItemsBundle?> = _bundle.asStateFlow()

    fun set(b: ActionItemsBundle) { _bundle.value = b }

    fun clear() { _bundle.value = null }

    /** Read the queue (best-effort; keeps what it had on failure). */
    suspend fun refresh(context: Context): Result<ActionItemsBundle> =
        HomeRepository(ApiClient.get(context)).loadActionItems().onSuccess { set(it) }
}
