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
        setContentView(R.layout.layout_activity1)
        Log.d(TAG, "Activity1 onCreate")

        // Tự động tải địa chỉ máy chủ đã lưu (hỗ trợ chuyển đổi Note 10+ / Cloudflare 24/7)
        val prefs = getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("server_url", "http://localhost:8083/") ?: "http://localhost:8083/"
        RetrofitClient.updateBaseUrl(savedServerUrl)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Gợi ý sẵn tài khoản mặc định
        etEmail.setText("khoi.pro@fnmf.com")

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Email!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Đang đăng nhập..."

            // Gọi API xác thực từ Backend Khôi
            val request = LoginRequest(email = email, password = "mypassword123")
            RetrofitClient.apiService.login(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        saveToken(token)
                        Toast.makeText(this@Activity1, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    } else {
                        // Nếu server chưa bật hoặc sai pass -> Vẫn cho phép vào chế độ Offline Demo
                        Toast.makeText(this@Activity1, "Chế độ Offline Demo (Không cần mạng)", Toast.LENGTH_SHORT).show()
                        navigateToTradingScreen()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Đăng Nhập"
                    Log.e(TAG, "Lỗi kết nối server: ${t.message}")
                    Toast.makeText(this@Activity1, "Vào chế độ Offline / Demo!", Toast.LENGTH_SHORT).show()
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
        val intent = Intent(this@Activity1, Activity2::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "Activity1_Login"
    }
}
