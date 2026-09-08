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
     * Cấu hình Base URL mặc định trỏ đến Railway Cloud Backend
     */
    var BASE_URL = NetworkConfig.DEFAULT_SERVER_URL
        private set

    private var currentRetrofit: Retrofit? = null
    private var currentApiService: ApiService? = null

    // Bảo mật: Sử dụng Level.BASIC để chỉ log HTTP method/URL/status, không log headers (Authorization/JWT) hoặc request/response body chứa mật khẩu
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Cập nhật địa chỉ Server mới
     */
    fun updateBaseUrl(newUrl: String) {
        val formatted = NetworkConfig.normalizeUrl(newUrl)
        if (BASE_URL != formatted || currentApiService == null) {
            BASE_URL = formatted
            currentRetrofit = null
            currentApiService = null
        }
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