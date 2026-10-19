package com.legumlex.client.data.api

import com.legumlex.client.data.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

// Attaches the JWT to every authenticated request as a standard Bearer token.
// The customers_api Authorization_token library (after the prefix-stripping
// patch in tokenIsExist/token) normalizes both "Bearer <jwt>" and raw JWTs,
// so this is the safer, more standard form.
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val skip = req.header("X-Skip-Auth") == "1"
        if (skip) return chain.proceed(req.newBuilder().removeHeader("X-Skip-Auth").build())

        val jwt = runBlocking { tokens.read() }
        val rebuilt = if (jwt.isNullOrBlank()) req
        else req.newBuilder().header("Authorization", "Bearer $jwt").build()
        return chain.proceed(rebuilt)
    }
}
