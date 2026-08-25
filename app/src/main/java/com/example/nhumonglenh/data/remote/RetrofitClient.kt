package com.example.nhumonglenh.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit Client quản lý kết nối mạng tập trung.
 * Hỗ trợ chuyển đổi linh hoạt giữa Localhost (USB), LAN IP và Cloudflare Tunnel 24/7.
 */
object RetrofitClient {

    /**
     * Cấu hình Base URL:
     * - Khi cắm cáp USB vào máy tính (dùng adb reverse): "http://localhost:8083/"
     * - Khi kết nối vào Server Note 10+ qua Cloudflare Tunnel: "https://<subdomain>.trycloudflare.com/"
     */
    var BASE_URL = "http://localhost:8083/"
        private set

    private var currentRetrofit: Retrofit? = null
    private var currentApiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Cập nhật địa chỉ Server mới khi chuyển sang Note 10+ hoặc Cloudflare
     */
    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        BASE_URL = formatted
        currentRetrofit = null
        currentApiService = null
    }

    val apiService: ApiService
        get() {
            if (currentApiService == null) {
                currentRetrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                currentApiService = currentRetrofit!!.create(ApiService::class.java)
            }
            return currentApiService!!
        }
}
