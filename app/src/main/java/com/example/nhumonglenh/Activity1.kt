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
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 1. Tải cấu hình Server URL đã lưu
        val prefs = getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("server_url", "http://10.174.64.109:8083/") ?: "http://10.174.64.109:8083/"
        val savedUsername = prefs.getString("saved_username", "khoi.pro@fnmf.com") ?: "khoi.pro@fnmf.com"
        
        etServerUrl.setText(savedServerUrl)
        etUsername.setText(savedUsername)
        etPassword.setText("mypassword123")

        // 2. Xử lý ĐĂNG NHẬP
        btnLogin.setOnClickListener {
            val serverUrl = prepareServerUrl(etServerUrl.text.toString().trim(), prefs)
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Tên đăng nhập!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Mật khẩu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("saved_username", username).apply()

            btnLogin.isEnabled = false
            btnLogin.text = "Đang đăng nhập..."

            val request = LoginRequest(username = username, password = password)
            RetrofitClient.apiService.login(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        saveToken(token)
                        Toast.makeText(this@Activity1, "✅ Đăng nhập kết nối Laptop thành công!", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    } else {
                        val errMsg = response.body()?.message ?: "Tài khoản hoặc mật khẩu không chính xác!"
                        Toast.makeText(this@Activity1, "❌ $errMsg", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"
                    Log.e(TAG, "Lỗi kết nối login: ${t.message}")
                    Toast.makeText(this@Activity1, "⚠️ Chế độ Offline: Không kết nối được Server (${t.message})", Toast.LENGTH_SHORT).show()
                    navigateToTradingScreen()
                }
            })
        }

        // 3. Xử lý ĐĂNG KÝ TÀI KHOẢN MỚI (CHỈ CẦN USERNAME & PASSWORD)
        btnRegister.setOnClickListener {
            val serverUrl = prepareServerUrl(etServerUrl.text.toString().trim(), prefs)
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Tên đăng nhập muốn tạo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 4) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 4 ký tự!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.equals("khoi.pro@fnmf.com", ignoreCase = true)) {
                Toast.makeText(this, "⚠️ Tài khoản 'khoi.pro@fnmf.com' đã tồn tại! Hãy nhập tên mới (VD: trader1, hung, manh...) để đăng ký.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Đang tạo tài khoản & cấp ví..."

            val request = RegisterRequest(username = username, password = password)
            RetrofitClient.apiService.register(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "✨ ĐĂNG KÝ TÀI KHOẢN (TẶNG $10,000 VÍ)"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        prefs.edit().putString("saved_username", username).apply()
                        saveToken(token)
                        Toast.makeText(this@Activity1, "🎉 Đăng ký thành công! Đã cấp ví $10,000 USD cho '$username'!", Toast.LENGTH_LONG).show()
                        navigateToTradingScreen()
                    } else {
                        val rawErr = response.errorBody()?.string() ?: ""
                        var cleanErr = "Tài khoản '$username' đã tồn tại!"
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

                        if (cleanErr.contains("tồn tại", ignoreCase = true)) {
                            cleanErr = "Tên đăng nhập '$username' đã có người sử dụng! Vui lòng chọn tên khác."
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
        var serverUrl = rawUrl
        if (serverUrl.isEmpty()) {
            serverUrl = "http://10.174.64.109:8083/"
        }
        if (!serverUrl.endsWith("/")) {
            serverUrl += "/"
        }
        prefs.edit().putString("server_url", serverUrl).apply()
        RetrofitClient.updateBaseUrl(serverUrl)
        return serverUrl
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
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
