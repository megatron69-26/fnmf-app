package com.example.nhumonglenh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nhumonglenh.ui.SystemBarInsets
import com.example.nhumonglenh.data.local.AuthFlowCoordinator
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.AuthResponse
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RegisterRequest
import com.example.nhumonglenh.data.remote.RetrofitClient
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private var registerCall: Call<AuthResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.layout_register)
        SystemBarInsets.apply(this, findViewById(android.R.id.content))

        val emailInput = findViewById<EditText>(R.id.etRegisterEmail)
        val passwordInput = findViewById<EditText>(R.id.etRegisterPassword)
        val confirmPasswordInput = findViewById<EditText>(R.id.etConfirmPassword)
        val registerButton = findViewById<Button>(R.id.btnSubmitRegister)
        val backToLoginButton = findViewById<Button>(R.id.btnBackToLogin)

        val prefs = getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        RetrofitClient.updateBaseUrl(NetworkConfig.getOrMigrateServerUrl(prefs))

        registerButton.setOnClickListener {
            val rawEmail = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            when {
                rawEmail.isEmpty() -> showMessage(getString(R.string.auth_err_empty_email))
                !NetworkConfig.isValidEmail(rawEmail) -> showMessage(getString(R.string.auth_err_invalid_email))
                !NetworkConfig.isValidPassword(password) -> showMessage(getString(R.string.auth_err_password_too_short))
                password != confirmPassword -> showMessage(getString(R.string.auth_err_confirm_password_mismatch))
                else -> submitRegistration(
                    NetworkConfig.normalizeEmail(rawEmail),
                    password,
                    registerButton
                )
            }
        }

        backToLoginButton.setOnClickListener { finish() }
    }

    private fun submitRegistration(email: String, password: String, button: Button) {
        if (registerCall != null) return

        button.isEnabled = false
        button.text = getString(R.string.auth_registering)

        val call = RetrofitClient.apiService.register(RegisterRequest(email = email, password = password))
        registerCall = call
        call.enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (registerCall !== call || isFinishing || isDestroyed) return
                registerCall = null
                button.isEnabled = true
                button.text = getString(R.string.auth_btn_register_submit)

                val token = response.body()?.token
                if (response.isSuccessful && !token.isNullOrBlank()) {
                    val outcome = AuthFlowCoordinator.handleAuthSuccess(
                        this@RegisterActivity,
                        token
                    )
                    when (outcome) {
                        is AuthFlowCoordinator.PersistenceOutcome.NavigateToMain -> {
                            getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putString(NetworkConfig.KEY_SAVED_EMAIL, email)
                                .apply()
                            showMessage(getString(R.string.auth_register_success))
                            startActivity(Intent(this@RegisterActivity, Activity2::class.java))
                            finishAffinity()
                            return
                        }
                        is AuthFlowCoordinator.PersistenceOutcome.StayOnAuth -> {
                            button.isEnabled = outcome.isButtonEnabled
                            button.text = getString(R.string.auth_btn_register_submit)
                            showMessage(getString(outcome.errorMessageResId))
                            return
                        }
                    }
                }

                showMessage(readRegistrationError(response, email))
            }

            override fun onFailure(call: Call<AuthResponse>, throwable: Throwable) {
                if (registerCall !== call || call.isCanceled || isFinishing || isDestroyed) return
                registerCall = null
                button.isEnabled = true
                button.text = getString(R.string.auth_btn_register_submit)
                Log.e(TAG, "Lỗi kết nối đăng ký: ${throwable.message}")
                showMessage("Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại mạng.")
            }
        })
    }

    private fun readRegistrationError(response: Response<AuthResponse>, email: String): String {
        val responseMessage = response.body()?.message
        if (!responseMessage.isNullOrBlank()) return responseMessage

        val rawError = response.errorBody()?.string().orEmpty()
        val parsedError = runCatching {
            val json = JSONObject(rawError)
            when {
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                else -> null
            }
        }.getOrNull()

        val message = parsedError ?: getString(R.string.auth_err_register_failed)
        return if (message.contains("tồn tại", ignoreCase = true) ||
            message.contains("already", ignoreCase = true)
        ) {
            getString(R.string.auth_err_email_already_used, email)
        } else {
            message
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        registerCall?.cancel()
        registerCall = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RegisterActivity"
    }
}
