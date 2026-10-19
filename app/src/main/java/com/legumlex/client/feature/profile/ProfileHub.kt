package com.legumlex.client.feature.profile

import android.content.Context
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in contact, app-wide: the header's initials, and the Profile
 * screen's starting point. Loaded once per sign-in, refreshed when the profile
 * changes; cleared at sign-out. Plain singleton (the app uses no DI).
 */
object ProfileHub {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    fun set(p: Profile) { _profile.value = p }

    fun clear() { _profile.value = null }

    suspend fun refresh(context: Context) {
        ProfileRepository(ApiClient.get(context)).load().onSuccess { set(it) }
    }
}
