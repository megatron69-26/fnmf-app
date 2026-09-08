package com.example.nhumonglenh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nhumonglenh.data.remote.AuthResponse
import com.example.nhumonglenh.data.remote.LoginRequest
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RegisterRequest
import com.example.nhumonglenh.data.remote.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * =====================================================================
 * ACTIVITY 1 - MÀN HÌNH ĐĂNG NHẬP & ĐĂNG KÝ (AUTH ENTRYPOINT)
 * =====================================================================
 * 1. ĐĂNG NHẬP (LOGIN): Dùng Username & Password
 * 2. ĐĂNG KÝ (REGISTER): Chỉ cần Username & Password -> Tự cấp ví $10,000 USD
 * 3. SERVER CONFIG: Cho phép chỉnh sửa và lưu Server IP Laptop linh hoạt
 * =====================================================================
 */
class Activity1 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_activity1)
        Log.d(TAG, "Activity1 onCreate")

        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 1. Tải cấu hình Server URL đã lưu và tự động migrate sang Railway Cloud
        val prefs = getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val savedServerUrl = NetworkConfig.getOrMigrateServerUrl(prefs)
        RetrofitClient.updateBaseUrl(savedServerUrl)
        etServerUrl.setText(savedServerUrl)

        // Di chuyển SharedPreferences từ saved_username sang saved_email (chạy đúng 1 lần)
        when (val migration = NetworkConfig.migrateSavedAccount(prefs)) {
            is NetworkConfig.EmailMigrationResult.Migrated -> {
                etEmail.setText(migration.email)
            }
            is NetworkConfig.EmailMigrationResult.LegacyAccountNeedsUpdate -> {
                // Tài khoản legacy (như khoi10): KHÔNG autofill, hiển thị cảnh báo
                etEmail.setText("")
                Toast.makeText(
                    this,
                    "⚠️ Tài khoản legacy '${migration.legacyUsername}' cần được Admin cập nhật sang Email thật trên hệ thống Cloud!",
                    Toast.LENGTH_LONG
                ).show()
            }
            NetworkConfig.EmailMigrationResult.AlreadyMigrated -> {
                val saved = prefs.getString(NetworkConfig.KEY_SAVED_EMAIL, "") ?: ""
                if (saved.isNotBlank()) {
                    etEmail.setText(saved)
                }
            }
            NetworkConfig.EmailMigrationResult.NoSavedAccount -> {
                // Không có tài khoản lưu trước đó
            }
        }
        // Ô password mặc định để trống theo yêu cầu bảo mật

        // 2. Xử lý ĐĂNG NHẬP
        btnLogin.setOnClickListener {
            val serverUrl = prepareServerUrl(etServerUrl.text.toString().trim(), prefs)
            val rawEmail = etEmail.text.toString().trim()
            val password = etPassword.text.toString() // Không trim mật khẩu

            if (rawEmail.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Email!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!NetworkConfig.isValidEmail(rawEmail)) {
                Toast.makeText(this, "Email không hợp lệ! Vui lòng nhập đúng định dạng (VD: user@fnmf.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Mật khẩu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalizedEmail = NetworkConfig.normalizeEmail(rawEmail)
            prefs.edit().putString(NetworkConfig.KEY_SAVED_EMAIL, normalizedEmail).apply()

            btnLogin.isEnabled = false
            btnLogin.text = "Đang đăng nhập..."

            val request = LoginRequest(email = normalizedEmail, password = password)
            RetrofitClient.apiService.login(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        saveToken(token)
                        Toast.makeText(this@Activity1, "✅ Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    } else {
                        val errMsg = response.body()?.message ?: "Email hoặc mật khẩu không chính xác!"
                        Toast.makeText(this@Activity1, "❌ $errMsg", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"
                    Log.e(TAG, "Lỗi kết nối login: ${t.message}")
                    Toast.makeText(this@Activity1, "❌ Không thể kết nối tới Server: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        // 3. Xử lý ĐĂNG KÝ TÀI KHOẢN MỚI (CHỈ CẦN EMAIL & PASSWORD)
        btnRegister.setOnClickListener {
            val serverUrl = prepareServerUrl(etServerUrl.text.toString().trim(), prefs)
            val rawEmail = etEmail.text.toString().trim()
            val password = etPassword.text.toString() // Không trim mật khẩu

            if (rawEmail.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Email muốn đăng ký!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!NetworkConfig.isValidEmail(rawEmail)) {
                Toast.makeText(this, "Email không hợp lệ! Vui lòng nhập đúng định dạng (VD: user@fnmf.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 4) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 4 ký tự!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalizedEmail = NetworkConfig.normalizeEmail(rawEmail)

            btnRegister.isEnabled = false
            btnRegister.text = "Đang tạo tài khoản & cấp ví..."

            val request = RegisterRequest(email = normalizedEmail, password = password)
            RetrofitClient.apiService.register(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "✨ ĐĂNG KÝ TÀI KHOẢN (TẶNG $10,000 VÍ)"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        prefs.edit().putString(NetworkConfig.KEY_SAVED_EMAIL, normalizedEmail).apply()
                        saveToken(token)
                        Toast.makeText(this@Activity1, "🎉 Đăng ký thành công! Đã cấp ví $10,000 USD cho '$normalizedEmail'!", Toast.LENGTH_LONG).show()
                        navigateToTradingScreen()
                    } else {
                        val rawErr = response.errorBody()?.string() ?: ""
                        var cleanErr = "Email '$normalizedEmail' đã tồn tại!"
                        try {
                            val json = JSONObject(rawErr)
                            if (json.has("message")) {
                                cleanErr = json.getString("message")
                            } else if (json.has("error")) {
                                cleanErr = json.getString("error")
                            }
                        } catch (e: Exception) {
                            if (rawErr.isNotBlank()) cleanErr = rawErr
                        }

                        if (cleanErr.contains("tồn tại", ignoreCase = true) || cleanErr.contains("already", ignoreCase = true)) {
                            cleanErr = "Email '$normalizedEmail' đã có người sử dụng! Vui lòng dùng email khác."
                        }

                        Toast.makeText(this@Activity1, "❌ $cleanErr", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "✨ ĐĂNG KÝ TÀI KHOẢN (TẶNG $10,000 VÍ)"
                    Log.e(TAG, "Lỗi kết nối register: ${t.message}")
                    Toast.makeText(this@Activity1, "❌ Không thể kết nối tới Server: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun prepareServerUrl(rawUrl: String, prefs: android.content.SharedPreferences): String {
        val serverUrl = NetworkConfig.saveUserConfiguredUrl(prefs, rawUrl)
        RetrofitClient.updateBaseUrl(serverUrl)
        return serverUrl
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("jwt_token", token).apply()
    }

    private fun navigateToTradingScreen() {
        val intent = Intent(this, Activity2::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "Activity1_Auth"
    }
}
