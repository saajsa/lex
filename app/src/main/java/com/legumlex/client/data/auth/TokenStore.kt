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

    val token: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    suspend fun read(): String? = token.first()

    suspend fun save(jwt: String, clientId: String?, contactId: String?) {
        context.dataStore.edit {
            it[tokenKey] = jwt
            clientId?.let  { v -> it[clientIdKey]  = v }
            contactId?.let { v -> it[contactIdKey] = v }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
