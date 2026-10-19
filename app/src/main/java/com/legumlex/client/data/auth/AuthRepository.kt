package com.legumlex.client.data.auth

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.push.PushSetup
import retrofit2.HttpException

// Wraps the customers_api Google sign-in endpoint + JWT persistence.
//
// The Credential Manager → Google ID token call happens in the LoginScreen
// (it needs an Activity context). This repo only handles the
// "got an ID token, now exchange it for a JWT" half.
class AuthRepository(
    private val api: ApiService,
    private val tokens: TokenStore,
) {
    suspend fun signInWithGoogle(idToken: String): Result<LoginResult> =
        runAuth { api.loginWithGoogle(idToken) }

    suspend fun signInWithEmail(email: String, password: String): Result<LoginResult> =
        runAuth(typedEmail = email) {
            api.login(email = email.trim(), password = password)
        }

    suspend fun signOut() {
        // Drop Beams interests before clearing local credentials so an
        // already-queued push doesn't fire after sign-out. Idempotent.
        PushSetup.clear()
        tokens.clear()
    }

    // Single happy-path + error-mapping pipeline shared by both sign-in flows.
    // `typedEmail` is a fallback for the email/password endpoint which (unlike
    // /authentication/google) doesn't echo the email back in the response.
    private suspend fun runAuth(
        typedEmail: String? = null,
        call: suspend () -> Map<String, Any?>,
    ): Result<LoginResult> = runCatching {
        val response = call()
        val data = response["data"] as? Map<*, *>
            ?: error("Server returned no data block")
        val jwt = data["token"] as? String
            ?: error("Server returned no token")

        val contactId = data["contact_id"]?.toString()
        tokens.save(
            jwt       = jwt,
            clientId  = data["client_id"]?.toString(),
            contactId = contactId,
        )

        // Subscribe this device to the contact's Beams interest so the
        // server can target pushes at the specific human, not the install.
        // Idempotent — Beams dedupes, safe on every login/me-refresh.
        contactId?.toIntOrNull()?.let { PushSetup.registerContact(it) }

        LoginResult(
            email   = (data["email"] as? String).orEmpty().ifBlank { typedEmail.orEmpty() },
            name    = data["name"]    as? String ?: "",
            picture = data["picture"] as? String,
        )
    }.recoverCatching { e ->
        // Surface HTTP errors with the server's message when available so the UI
        // can show something useful (e.g. "Invalid username or password") instead
        // of a generic "request failed".
        when (e) {
            is HttpException -> {
                val body = e.response()?.errorBody()?.string().orEmpty()
                val msg  = parseServerMessage(body) ?: "Sign-in failed (HTTP ${e.code()})"
                throw SignInException(msg, code = e.code(), cause = e)
            }
            else -> throw SignInException(e.message ?: "Network error", cause = e)
        }
    }

    private fun parseServerMessage(body: String): String? {
        // Tiny manual parse — avoids pulling Moshi for one field.
        val key = "\"message\":\""
        val start = body.indexOf(key).takeIf { it >= 0 }?.plus(key.length) ?: return null
        val end = body.indexOf('"', start).takeIf { it > start } ?: return null
        return body.substring(start, end)
    }
}

data class LoginResult(
    val email: String,
    val name: String,
    val picture: String?,
)

class SignInException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
