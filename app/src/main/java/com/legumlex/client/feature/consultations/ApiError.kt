package com.legumlex.client.feature.consultations

import org.json.JSONObject
import retrofit2.HttpException

/**
 * What the server said when it refused a request. Every customers_api 4xx
 * carries a JSON body {status:false, message, code?, action?}; Retrofit
 * throws [HttpException] before that body is read, so without this the
 * client saw "HTTP 409" instead of the server's own sentence.
 */
data class ApiError(val http: Int?, val message: String?, val code: String?, val action: String?)

private val cache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Throwable, ApiError>())

/** The server's refusal, read once (an error body can only be consumed once). */
fun Throwable.apiError(): ApiError = cache.getOrPut(this) {
    val e = this as? HttpException ?: return@getOrPut ApiError(null, null, null, null)
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    val json = runCatching { JSONObject(body) }.getOrNull()
    fun s(k: String) = json?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
    ApiError(e.code(), s("message") ?: s("error"), s("code"), s("action"))
}

/**
 * The sentence to show for a failure: the server's own message when it gave
 * one; otherwise a plain description of what went wrong. Never a raw
 * exception, never "HTTP 409", and a 403 is a permission, not a session.
 */
fun Throwable.friendlyMessage(fallback: String = "Something went wrong. Please try again."): String {
    val err = apiError()
    err.message?.let { return it }
    return when (err.http) {
        null -> when (this) {
            is java.net.UnknownHostException, is java.net.ConnectException,
            is java.net.SocketTimeoutException, is java.io.InterruptedIOException ->
                "Couldn't reach the firm. Please check your connection and try again."
            else -> fallback
        }
        401 -> "Your session has ended. Please sign in again."
        403 -> "Your account does not include this. Please ask the firm if you need it."
        404 -> "This is no longer available."
        409 -> "This can't be done in its current state."
        422 -> "Some of the details were not accepted. Please check them and try again."
        in 500..599 -> "The firm's server had a problem. Please try again in a moment."
        else -> fallback
    }
}
