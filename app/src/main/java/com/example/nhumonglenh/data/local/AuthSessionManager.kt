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
 * - Tránh truy cập rải rác key "jwt_token" trong SharedPreferences.
 * - Xử lý 401/403 Unauthorized tập trung, chống loop logout.
 * - Giữ nguyên Server URL và Email người dùng khi đăng xuất.
 */
object AuthSessionManager {

    private const val KEY_JWT_TOKEN = "jwt_token"
    private val isHandlingUnauthorized = AtomicBoolean(false)

    fun getToken(context: Context): String {
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_JWT_TOKEN, "") ?: ""
    }

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JWT_TOKEN, token.trim()).apply()
        isHandlingUnauthorized.set(false)
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

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context).isNotBlank()
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        // Chỉ xóa token xác thực, bảo lưu server_url và saved_email
        prefs.edit().remove(KEY_JWT_TOKEN).apply()
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
                "⚠️ Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.",
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
