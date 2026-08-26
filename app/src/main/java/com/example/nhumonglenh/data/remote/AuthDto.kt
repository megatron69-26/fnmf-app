package com.example.nhumonglenh.data.remote

data class LoginRequest(
    val username: String,
    val password: String,
    val email: String = if (username.contains("@")) username else "$username@fnmf.com"
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String = if (username.contains("@")) username else "$username@fnmf.com",
    val fullName: String = username
)

data class AuthResponse(
    val token: String?,
    val tokenType: String?,
    val message: String?
)
