package com.legumlex.client.data.auth

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.push.PushSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File

/** cacheDir folders that hold a signed-in contact's documents. */
private val DocCacheDirs = setOf("docs", "ahc", "casedoc", "docreq", "noticedoc")

// The customers_api sign-in endpoints (Google, email + password, a code to
// the verified mobile) + JWT persistence.
//
// The Credential Manager → Google ID token call happens in the LoginScreen
// (it needs an Activity context). This repo only handles the
// "got an ID token, now exchange it for a JWT" half.
class AuthRepository(
    private val api: ApiService,
    private val tokens: TokenStore,
    /** The app's cacheDir: the documents opened while signed in are kept there. */
    private val cacheDir: File? = null,
) {
    suspend fun signInWithGoogle(idToken: String): Result<LoginResult> =
        runAuth { api.loginWithGoogle(idToken) }

    suspend fun signInWithEmail(email: String, password: String): Result<LoginResult> =
        runAuth(typedEmail = email) {
            api.login(email = email.trim(), password = password)
        }

    /** Sends a sign-in code to the mobile number; WhatsApp first. */
    suspend fun requestCode(number: String): Result<CodeSent> = runStep {
        val r = api.otpRequest(number.trim())
        CodeSent(
            ref         = r["ref"] as? String ?: error("Server returned no reference"),
            to          = r["to"] as? String ?: "",
            message     = r["message"] as? String ?: "",
            resendAfter = (r["resend_after"] as? Number)?.toInt() ?: 60,
        )
    }

    suspend fun codeStatus(ref: String): Result<CodeStatus> = runStep {
        val r = api.otpStatus(ref)
        CodeStatus(
            state    = r["state"] as? String ?: "expired",
            canSms   = r["can_sms"] == true,
            resendIn = (r["resend_in"] as? Number)?.toInt() ?: 0,
        )
    }

    /** "Send by SMS instead": the same code, by SMS. Returns the server's note. */
    suspend fun codeBySms(ref: String): Result<String> = runStep {
        api.otpSms(ref)["message"] as? String ?: "The code has been sent by SMS."
    }

    suspend fun signInWithCode(ref: String, code: String): Result<LoginResult> =
        runAuth { api.otpVerify(ref, code) }

    /**
     * A lead's standing with the firm, asked at each launch: still a lead, a
     * registration with the firm for approval, or opened, in which case the
     * client token it returns replaces the lead's and the app becomes the
     * client's. Null if it could not be asked (offline): the app stays as it is.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun leadStanding(): String? = runCatching {
        val d = api.leadMe()["data"] as? Map<String, Any?> ?: return null
        SessionRole.name.value = (d["name"] as? String).orEmpty()
        if (d["role"] == "client") {
            val c = d["client"] as? Map<String, Any?> ?: return null
            val jwt = c["token"] as? String ?: return null
            tokens.save(jwt, c["client_id"]?.toString(), c["contact_id"]?.toString(), role = "client")
            c["contact_id"]?.toString()?.toIntOrNull()?.let { PushSetup.registerContact(it) }
            "client"
        } else {
            val st = (d["status"] as? String).orEmpty().ifBlank { "lead" }
            tokens.saveLeadStatus(st)
            st
        }
    }.getOrNull()

    /** Signs out here and on the server (which revokes the token). */
    suspend fun signOut() {
        // Best effort: offline, the token still goes from this device. A
        // lead's token is not a contact's: the server's logout is a client's.
        if (!SessionRole.isLead) withTimeoutOrNull(5_000) { runCatching { api.logout() } }
        forget()
    }

    /** Forgets the sign-in on this device only (the server already refused it). */
    suspend fun forget() {
        // Drop Beams interests before clearing local credentials so an
        // already-queued push doesn't fire after sign-out. Idempotent.
        PushSetup.clear()
        com.legumlex.client.feature.profile.ProfileHub.clear()
        com.legumlex.client.feature.notifications.NotificationHub.clear()
        com.legumlex.client.feature.home.ActionHub.clear()
        tokens.clear()
        clearDocumentCache()
    }

    // Legal documents opened in the app are cached as plain files; the next
    // person to sign in on this device must not find them.
    private suspend fun clearDocumentCache() {
        val root = cacheDir ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                root.listFiles()?.forEach { f ->
                    when {
                        f.isDirectory && f.name in DocCacheDirs -> f.deleteRecursively()
                        f.isFile && (f.name.startsWith("proj_") || f.name.startsWith("projfile_")) -> f.delete()
                    }
                }
            }
        }
    }

    // The OTP steps' error mapping: the server's message, and its error code
    // (e.g. "unverified" when the number may not sign in).
    private suspend fun <T> runStep(call: suspend () -> T): Result<T> =
        runCatching { call() }.recoverCatching { throw it.toSignInException() }

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

        // A lead (no open client account) has no contact: role "lead".
        val role      = if (data["role"] == "lead") "lead" else "client"
        val contactId = data["contact_id"]?.toString()
        tokens.save(
            jwt       = jwt,
            clientId  = data["client_id"]?.toString(),
            contactId = contactId,
            role      = role,
            status    = (data["status"] as? String).orEmpty(),
        )

        // Subscribe this device to the contact's Beams interest so the
        // server can target pushes at the specific human, not the install.
        // Idempotent — Beams dedupes, safe on every login/me-refresh.
        if (role == "client") contactId?.toIntOrNull()?.let { PushSetup.registerContact(it) }
        else SessionRole.name.value = (data["name"] as? String).orEmpty()

        LoginResult(
            email   = (data["email"] as? String).orEmpty().ifBlank { typedEmail.orEmpty() },
            name    = data["name"]    as? String ?: "",
            picture = data["picture"] as? String,
            lead    = role == "lead",
        )
    }.recoverCatching { throw it.toSignInException() }

    // Surface HTTP errors with the server's message when available so the UI
    // can show something useful (e.g. "Invalid username or password") instead
    // of a generic "request failed".
    private fun Throwable.toSignInException(): SignInException = when (this) {
        is SignInException -> this
        is HttpException -> {
            val body = runCatching { JSONObject(response()?.errorBody()?.string().orEmpty()) }.getOrNull()
            val msg  = body?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Sign-in failed (HTTP ${code()})"
            SignInException(msg, code = code(), error = body?.optString("error")?.takeIf { it.isNotBlank() }, cause = this)
        }
        is java.io.IOException -> SignInException("Network error. Please try again.", cause = this)
        else -> SignInException(message ?: "Network error", cause = this)
    }
}

data class LoginResult(
    val email: String,
    val name: String,
    val picture: String?,
    /** Signed in as a lead: no client account, or one awaiting approval. */
    val lead: Boolean = false,
)

data class CodeSent(
    val ref: String,
    val to: String,
    val message: String,
    val resendAfter: Int,
)

data class CodeStatus(
    val state: String,
    val canSms: Boolean,
    val resendIn: Int,
)

class SignInException(
    message: String,
    val code: Int? = null,
    val error: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
