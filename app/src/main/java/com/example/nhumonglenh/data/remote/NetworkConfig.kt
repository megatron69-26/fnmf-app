package com.example.nhumonglenh.data.remote

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Nguồn cấu hình mạng tập trung cho toàn bộ ứng dụng FNMF.
 * Đảm bảo RetrofitClient và News ApiClient dùng chung 1 nguồn cấu hình duy nhất.
 */
object NetworkConfig {
    private const val TAG = "NetworkConfig"
    const val DEFAULT_LAN_URL = "http://172.18.97.109:8083/"
    const val EMULATOR_URL = "http://10.0.2.2:8083/"

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.startsWith("unknown") ||
               Build.MODEL.contains("google_sdk", ignoreCase = true) ||
               Build.MODEL.contains("Emulator", ignoreCase = true) ||
               Build.MODEL.contains("Android SDK built for", ignoreCase = true)
    }

    /**
     * Lấy Server Base URL chuẩn từ SharedPreferences.
     * Tự động migrate URL cũ đúng một lần và hỗ trợ Android Emulator.
     */
    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("server_url", null)

        val resolvedUrl: String
        if (saved.isNullOrBlank()) {
            resolvedUrl = if (isEmulator()) EMULATOR_URL else DEFAULT_LAN_URL
            prefs.edit().putString("server_url", resolvedUrl).apply()
            Log.d(TAG, "Khởi tạo URL mặc định: $resolvedUrl (isEmulator=${isEmulator()})")
        } else if (saved.contains("10.174.64.109") || saved.contains("10.174.64.59") || saved.contains("3000")) {
            // Migrate URL cũ / cổng 3000 đúng 1 lần
            resolvedUrl = if (isEmulator()) EMULATOR_URL else DEFAULT_LAN_URL
            prefs.edit().putString("server_url", resolvedUrl).apply()
            Log.d(TAG, "Đã migrate URL cũ '$saved' sang: $resolvedUrl")
        } else {
            resolvedUrl = if (saved.endsWith("/")) saved else "$saved/"
        }

        Log.d(TAG, "Server URL đã chuẩn hoá: $resolvedUrl")
        return resolvedUrl
    }
}
