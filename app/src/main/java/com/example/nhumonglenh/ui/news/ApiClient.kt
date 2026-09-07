package com.example.nhumonglenh.ui.news


import com.example.nhumonglenh.ui.news.NewsApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // âš ï¸ Äá»”I IP NÃ€Y THÃ€NH IP LAN MÃY Báº N (náº¿u cháº¡y trÃªn Ä‘iá»‡n thoáº¡i tháº­t, xem báº±ng lá»‡nh ipconfig)
    // Hoáº·c giá»¯ nguyÃªn 10.0.2.2 náº¿u báº¡n Ä‘ang cháº¡y á»©ng dá»¥ng trÃªn Android Emulator
    private val baseUrl = if (isAndroidEmulator()) {
        "http://10.0.2.2:3000/"
    } else {
        "http://172.18.97.109:3000/"
    }

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
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: NewsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApiService::class.java)
    }
}


