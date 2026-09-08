package com.example.nhumonglenh.ui.news

import android.content.Context
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RetrofitClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

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
        val url = NetworkConfig.getServerUrl(context)
        RetrofitClient.updateBaseUrl(url)
        return url
    }


}