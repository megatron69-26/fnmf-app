package com.example.nhumonglenh.data.remote

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String?,
    val tokenType: String?,
    val message: String?
)
