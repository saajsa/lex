package com.legumlex.client.data.api

import android.content.Context
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.auth.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile private var service: ApiService? = null

    fun get(context: Context): ApiService = service ?: synchronized(this) {
        service ?: build(context.applicationContext).also { service = it }
    }

    private fun build(appContext: Context): ApiService {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        val http = OkHttpClient.Builder()
            // A call marked `X-Long-Call: 1` waits up to two minutes for its
            // answer: an identity document is read on the server while the
            // call is open, and that can take more than a minute.
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.header("X-Long-Call") == "1") {
                    chain.withReadTimeout(120, TimeUnit.SECONDS).withWriteTimeout(120, TimeUnit.SECONDS)
                        .proceed(req.newBuilder().removeHeader("X-Long-Call").build())
                } else chain.proceed(req)
            }
            .addInterceptor(AuthInterceptor(TokenStore(appContext)))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}
