package com.example.gamercalendar.data.api

import com.example.gamercalendar.BuildConfig
import com.example.gamercalendar.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    @Volatile
    private var sessionManager: SessionManager? = null

    fun init(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }

    private val authInterceptor = Interceptor { chain ->
        val token = sessionManager?.peekToken()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
