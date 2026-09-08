package com.example.nhumonglenh.ui.news

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val DEFAULT_LAPTOP_HOST = "172.18.97.109"

    private fun isAndroidEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Emulator", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Android SDK built for", ignoreCase = true)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedService: NewsApiService? = null

    fun service(context: Context): NewsApiService {
        val baseUrl = newsBaseUrl(context)
        val existing = cachedService
        if (existing != null && cachedBaseUrl == baseUrl) return existing

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApiService::class.java)
            .also {
                cachedBaseUrl = baseUrl
                cachedService = it
            }
    }

    private fun newsBaseUrl(context: Context): String {
        val url = com.example.nhumonglenh.data.remote.NetworkConfig.getServerUrl(context)
        com.example.nhumonglenh.data.remote.RetrofitClient.updateBaseUrl(url)
        return url
    }


}