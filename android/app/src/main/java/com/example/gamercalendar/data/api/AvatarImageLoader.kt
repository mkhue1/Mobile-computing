package com.example.gamercalendar.data.api

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

// A dedicated loader for avatar requests. It shares ApiClient's OkHttpClient, so the
// auth interceptor attaches the Bearer token. It is deliberately not the
// SingletonImageLoader: that one would also send the JWT to images.igdb.com.
object AvatarImageLoader {

    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        loader ?: synchronized(this) {
            loader ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { ApiClient.okHttpClient }))
                }
                .crossfade(true)
                .build()
                .also { loader = it }
        }
}
