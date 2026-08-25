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
import com.example.nhumonglenh.data.remote.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Activity1 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_activity1)
        Log.d(TAG, "Activity1 onCreate")

        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // 1. Tải Server URL đã lưu (mặc định trỏ vào Note 10+ Wi-Fi IP)
        val prefs = getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("server_url", "http://10.174.64.59:8083/") ?: "http://10.174.64.59:8083/"
        
        etServerUrl.setText(savedServerUrl)
        etEmail.setText("khoi.pro@fnmf.com")

        btnLogin.setOnClickListener {
            var serverUrl = etServerUrl.text.toString().trim()
            if (serverUrl.isEmpty()) {
                serverUrl = "http://10.174.64.59:8083/"
            }
            if (!serverUrl.endsWith("/")) {
                serverUrl += "/"
            }

            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Email!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lưu server_url và cập nhật Retrofit Base URL
            prefs.edit().putString("server_url", serverUrl).apply()
            RetrofitClient.updateBaseUrl(serverUrl)

            btnLogin.isEnabled = false
            btnLogin.text = "Đang kết nối Server..."

            // Gọi API xác thực từ Backend Note 10+
            val request = LoginRequest(email = email, password = "mypassword123")
            RetrofitClient.apiService.login(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        saveToken(token)
                        Toast.makeText(this@Activity1, "✅ Đăng nhập kết nối Note 10+ thành công!", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    } else {
                        Toast.makeText(this@Activity1, "Chế độ Offline Demo (Không kết nối được server)", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "ĐĂNG NHẬP VÀO SÀN"
                    Log.e(TAG, "Lỗi kết nối login: ${t.message}")
                    Toast.makeText(this@Activity1, "Chế độ Offline Demo: ${t.message}", Toast.LENGTH_SHORT).show()
                    navigateToTradingScreen()
                }
            })
        }
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
        private const val TAG = "Activity1_Login"
    }
}
