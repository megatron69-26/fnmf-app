package com.example.nhumonglenh.data.local

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.nhumonglenh.Activity1
import com.example.nhumonglenh.data.remote.NetworkConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quản lý tập trung phiên đăng nhập và Token (AuthSessionManager).
 * - Sử dụng SecureTokenStore (Android KeyStore AES-GCM) để mã hoá token.
 * - Xử lý 401/403 Unauthorized tập trung, chống loop logout.
 * - Giữ nguyên Server URL và Email người dùng khi đăng xuất.
 */
object AuthSessionManager {

    private val isHandlingUnauthorized = AtomicBoolean(false)

    fun getToken(context: Context): String {
        return SecureTokenStore.getToken(context)
    }

    fun saveToken(context: Context, token: String): Boolean {
        val saved = SecureTokenStore.saveToken(context, token)
        if (saved) {
            isHandlingUnauthorized.set(false)
        }
        return saved
    }

    fun getUserEmail(context: Context): String {
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(NetworkConfig.KEY_SAVED_EMAIL, "") ?: ""
    }

    fun saveUserEmail(context: Context, email: String) {
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = NetworkConfig.normalizeEmail(email)
        prefs.edit().putString(NetworkConfig.KEY_SAVED_EMAIL, normalized).apply()
    }

    fun getAuthHeader(context: Context): String? {
        val token = getToken(context)
        if (token.isBlank()) return null
        return "Bearer $token"
    }

    /**
     * Giải mã claim 'exp' từ JWT payload mà không phụ thuộc vào thư viện bên ngoài.
     * Trả về epoch timestamp dạng giây, hoặc null nếu token sai cấu trúc hoặc không có claim 'exp'.
     */
    fun extractExpirationSeconds(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val parts = token.trim().split(".")
        if (parts.size != 3) return null
        return try {
            val payloadBase64 = parts[1]
            val decodedBytes = android.util.Base64.decode(
                payloadBase64,
                android.util.Base64.URL_SAFE or
                    android.util.Base64.NO_PADDING or
                    android.util.Base64.NO_WRAP
            )
            val jsonString = String(decodedBytes, Charsets.UTF_8)
            try {
                val jsonObject = org.json.JSONObject(jsonString)
                if (jsonObject.has("exp")) {
                    return jsonObject.getLong("exp")
                }
            } catch (ignored: Exception) {
            }
            val match = "\"exp\"\\s*:\\s*(\\d+)".toRegex().find(jsonString)
            match?.groupValues?.get(1)?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Kiểm tra JWT token đã hết hạn hay chưa theo thời gian thực (giây).
     * Trả về true nếu token null, rỗng, không có claim 'exp', hoặc exp <= currentTimeSeconds.
     */
    fun isTokenExpired(token: String?, currentTimeSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val exp = extractExpirationSeconds(token) ?: return true
        return exp <= currentTimeSeconds
    }

    fun isLoggedIn(context: Context): Boolean {
        val token = getToken(context)
        if (token.isBlank()) return false
        if (isTokenExpired(token)) {
            clearSession(context)
            return false
        }
        return true
    }

    fun clearSession(context: Context) {
        SecureTokenStore.clearToken(context)
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Dọn dẹp toàn bộ dữ liệu pending payment orders và idempotency keys khi đăng xuất
        for (key in prefs.all.keys) {
            if (key.startsWith("payment_idemp_") || key.startsWith("fnmf_active_pending_payment_order_id")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun handleUnauthorized(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }
        if (!isHandlingUnauthorized.compareAndSet(false, true)) {
            // Đang trong tiến trình chuyển hướng đăng xuất, bỏ qua để tránh loop
            return
        }

        clearSession(activity)

        activity.runOnUiThread {
            Toast.makeText(
                activity.applicationContext,
                "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(activity, Activity1::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            activity.finish()
            isHandlingUnauthorized.set(false)
        }
    }
}
