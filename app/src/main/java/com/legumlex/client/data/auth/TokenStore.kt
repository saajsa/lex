package com.legumlex.client.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// JWT token store backed by DataStore. Production should layer EncryptedSharedPreferences
// or wrap the token via androidx.security.crypto before persistence.
private val Context.dataStore by preferencesDataStore(name = "auth")

class TokenStore(private val context: Context) {
    private val tokenKey   = stringPreferencesKey("jwt")
    private val clientIdKey = stringPreferencesKey("client_id")
    private val contactIdKey = stringPreferencesKey("contact_id")
    // "client" or "lead" (SessionRole); a sign-in from before leads is a client's.
    private val roleKey      = stringPreferencesKey("role")
    private val statusKey    = stringPreferencesKey("lead_status")

    val token: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    suspend fun read(): String? = token.first()

    suspend fun save(jwt: String, clientId: String?, contactId: String?, role: String = "client", status: String = "") {
        context.dataStore.edit {
            it[tokenKey] = jwt
            clientId?.let  { v -> it[clientIdKey]  = v }
            contactId?.let { v -> it[contactIdKey] = v }
            it[roleKey]   = role
            it[statusKey] = status
        }
        SessionRole.set(role, status)
    }

    /** Puts the stored role into SessionRole; the splash calls it before routing. */
    suspend fun loadRole() {
        val p = context.dataStore.data.first()
        SessionRole.set(p[roleKey] ?: "client", p[statusKey])
    }

    suspend fun saveLeadStatus(status: String) {
        context.dataStore.edit { it[statusKey] = status }
        SessionRole.set("lead", status)
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        SessionRole.set(null)
    }
}
