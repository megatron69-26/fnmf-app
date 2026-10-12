package com.example.nhumonglenh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nhumonglenh.ui.SystemBarInsets
import com.example.nhumonglenh.data.remote.AuthResponse
import com.example.nhumonglenh.data.remote.LoginRequest
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * =====================================================================
 * ACTIVITY 1 - MÀN HÌNH ĐĂNG NHẬP (AUTH ENTRYPOINT)
 * =====================================================================
 * 1. ĐĂNG NHẬP (LOGIN): Dùng Email & Password
 * 2. ĐĂNG KÝ (REGISTER): Mở màn hình đăng ký riêng
 * 3. KẾT NỐI: Luôn sử dụng Railway Cloud Backend của bản production
 * =====================================================================
 */
class Activity1 : AppCompatActivity() {

    private var loginCall: Call<AuthResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_activity1)
        SystemBarInsets.apply(this, findViewById(android.R.id.content))
        Log.d(TAG, "Activity1 onCreate")

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnServerConfig = findViewById<Button>(R.id.btnServerConfig)
        val tvServerStatus = findViewById<TextView>(R.id.tvServerStatus)

        // 1. Khóa ứng dụng vào Railway Cloud và dọn URL LAN/custom đã lưu từ bản cũ.
        val prefs = getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        fun updateServerStatus() {
            val configuredUrl = NetworkConfig.getOrMigrateServerUrl(prefs)
            val configured = NetworkConfig.isAllowedServerUrl(configuredUrl)
            tvServerStatus.text = if (configured) "Đã lưu URL (chưa kiểm tra kết nối): $configuredUrl" else "Chưa cấu hình máy chủ HTTPS"
            tvServerStatus.setTextColor(android.graphics.Color.parseColor(if (configured) "#B0B6C5" else "#FFA726"))
            RetrofitClient.updateBaseUrl(configuredUrl)
        }
        fun showServerConfigDialog() {
            val input = EditText(this).apply {
                hint = "https://ten-mien-co-dinh.example/"
                setSingleLine(true)
                setText(prefs.getString(NetworkConfig.KEY_SERVER_URL, "")?.takeUnless { it == NetworkConfig.DEFAULT_SERVER_URL } ?: "")
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Máy chủ Note 10+")
                .setMessage("Nhập địa chỉ HTTPS cố định của gateway trên Note 10+. Không dùng URL tunnel tạm hoặc Railway.")
                .setView(input)
                .setPositiveButton("Lưu") { _, _ ->
                    val url = input.text.toString().trim()
                    if (!NetworkConfig.isAllowedServerUrl(url)) {
                        Toast.makeText(this, "Cần URL HTTPS hợp lệ, không phải Railway hay địa chỉ nội bộ.", Toast.LENGTH_LONG).show()
                    } else {
                        prefs.edit().putString(NetworkConfig.KEY_SERVER_URL, NetworkConfig.normalizeUrl(url)).apply()
                        updateServerStatus()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
        btnServerConfig.setOnClickListener { showServerConfigDialog() }
        updateServerStatus()

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
                    "Tài khoản '${migration.legacyUsername}' cần được quản trị viên cập nhật sang email thật.",
                    Toast.LENGTH_LONG
                ).show()
            }
            NetworkConfig.EmailMigrationResult.AlreadyMigrated -> {
                val saved = prefs.getString(NetworkConfig.KEY_SAVED_EMAIL, "") ?: ""
                val legacyIdentifier = prefs.getString(NetworkConfig.KEY_LEGACY_ACCOUNT_IDENTIFIER, "") ?: ""
                if (saved.isNotBlank()) {
                    etEmail.setText(saved)
                } else if (legacyIdentifier.isNotBlank()) {
                    etEmail.setText("")
                    Toast.makeText(
                        this,
                        "Tài khoản '$legacyIdentifier' cần được quản trị viên cập nhật sang email thật.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            NetworkConfig.EmailMigrationResult.NoSavedAccount -> {
                // Không có tài khoản lưu trước đó
            }
        }
        // Ô password mặc định để trống theo yêu cầu bảo mật

        // 2. Xử lý ĐĂNG NHẬP
        btnLogin.setOnClickListener {
            if (loginCall != null) return@setOnClickListener
            if (!NetworkConfig.isAllowedServerUrl(NetworkConfig.getOrMigrateServerUrl(prefs))) {
                showServerConfigDialog()
                return@setOnClickListener
            }

            val rawEmail = etEmail.text.toString().trim()
            val password = etPassword.text.toString() // Không trim mật khẩu

            if (rawEmail.isEmpty()) {
                Toast.makeText(this, getString(R.string.auth_err_empty_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!NetworkConfig.isValidEmail(rawEmail)) {
                Toast.makeText(this, getString(R.string.auth_err_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, getString(R.string.auth_err_empty_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalizedEmail = NetworkConfig.normalizeEmail(rawEmail)
            prefs.edit().putString(NetworkConfig.KEY_SAVED_EMAIL, normalizedEmail).apply()

            btnLogin.isEnabled = false
            btnLogin.text = getString(R.string.auth_logging_in)

            val request = LoginRequest(email = normalizedEmail, password = password)
            val call = RetrofitClient.apiService.login(request)
            loginCall = call
            call.enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (loginCall !== call || isFinishing || isDestroyed) return
                    loginCall = null
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.auth_btn_login)

                    val token = response.body()?.token
                    if (response.isSuccessful && !token.isNullOrEmpty()) {
                        val outcome = com.example.nhumonglenh.data.local.AuthFlowCoordinator.handleAuthSuccess(
                            this@Activity1,
                            token
                        )
                        when (outcome) {
                            is com.example.nhumonglenh.data.local.AuthFlowCoordinator.PersistenceOutcome.NavigateToMain -> {
                                Toast.makeText(this@Activity1, getString(R.string.auth_login_success), Toast.LENGTH_SHORT).show()
                                navigateToTradingScreen()
                            }
                            is com.example.nhumonglenh.data.local.AuthFlowCoordinator.PersistenceOutcome.StayOnAuth -> {
                                btnLogin.isEnabled = outcome.isButtonEnabled
                                btnLogin.text = getString(R.string.auth_btn_login)
                                Toast.makeText(this@Activity1, getString(outcome.errorMessageResId), Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val errMsg = response.body()?.message ?: getString(R.string.auth_err_invalid_credentials)
                        Toast.makeText(this@Activity1, errMsg, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    if (loginCall !== call || call.isCanceled || isFinishing || isDestroyed) return
                    loginCall = null
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.auth_btn_login)
                    Log.e(TAG, "Lỗi kết nối login: ${t.message}")
                    Toast.makeText(this@Activity1, "Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại mạng.", Toast.LENGTH_LONG).show()
                }
            })
        }

        // 3. Mở màn hình đăng ký riêng.
        btnRegister.setOnClickListener {
            if (!NetworkConfig.isAllowedServerUrl(NetworkConfig.getOrMigrateServerUrl(prefs))) {
                showServerConfigDialog()
                return@setOnClickListener
            }
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun navigateToTradingScreen() {
        val intent = Intent(this, Activity2::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        loginCall?.cancel()
        loginCall = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Activity1_Auth"
    }
}
