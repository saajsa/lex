package com.legumlex.client.feature.profile

import com.legumlex.client.data.api.ApiService
import org.json.JSONObject
import retrofit2.HttpException

/** A step that failed, with the server's own words and its error code. */
class StepException(message: String, val error: String? = null) : Exception(message)

class ProfileRepository(private val api: ApiService) {

    suspend fun load(): Result<Profile> = call {
        @Suppress("UNCHECKED_CAST")
        parseProfile(api.profile()["data"] as? Map<String, Any?> ?: error("No profile"))
    }

    /** A code to the number, on WhatsApp. Returns (ref, number as shown, resend seconds). */
    suspend fun requestCode(number: String): Result<Triple<String, String, Int>> = call {
        val r = api.profilePhone("request", number = number.trim())
        Triple(r["ref"] as? String ?: error("The code could not be sent. Please try again."), (r["to"] as? String).orEmpty(), (r["resend_after"] as? Number)?.toInt() ?: 60)
    }

    /** (state, canSms) of the code sent. */
    suspend fun codeStatus(ref: String): Result<Pair<String, Boolean>> = call {
        val r = api.profilePhone("status", ref = ref)
        (r["state"] as? String ?: "expired") to (r["can_sms"] == true)
    }

    suspend fun codeBySms(ref: String): Result<String> = call {
        api.profilePhone("sms", ref = ref)["message"] as? String ?: "The code has been sent by SMS."
    }

    suspend fun verify(ref: String, code: String): Result<Mobile> = mobileAfter { api.profilePhone("verify", ref = ref, code = code) }

    suspend fun setUpdates(on: Boolean): Result<Mobile> = mobileAfter { api.profilePhone("updates", on = if (on) "1" else "0") }

    suspend fun remove(): Result<Mobile> = mobileAfter { api.profilePhone("remove") }

    @Suppress("UNCHECKED_CAST")
    private suspend fun mobileAfter(block: suspend () -> Map<String, Any?>): Result<Mobile> = call {
        parseMobile(block()["mobile"] as? Map<String, Any?> ?: emptyMap())
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = runCatching { block() }.recoverCatching { e ->
        throw when (e) {
            is HttpException -> {
                val body = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()) }.getOrNull()
                StepException(
                    body?.optString("message")?.takeIf { it.isNotBlank() } ?: "The request could not be completed (error ${e.code()}).",
                    body?.optString("error")?.takeIf { it.isNotBlank() },
                )
            }
            is java.io.IOException -> StepException("Network error. Please try again.")
            else -> e
        }
    }
}
