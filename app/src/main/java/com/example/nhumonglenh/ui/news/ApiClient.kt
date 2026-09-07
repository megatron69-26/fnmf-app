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
        level = HttpLoggingInterceptor.Level.BODY
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
        if (isAndroidEmulator()) return "http://10.0.2.2:8083/"

        val prefs = context.getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val tradingUrl = prefs.getString("server_url", null)
        val host = runCatching { Uri.parse(tradingUrl).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LAPTOP_HOST
        return "http://$host:8083/"
    }
}

