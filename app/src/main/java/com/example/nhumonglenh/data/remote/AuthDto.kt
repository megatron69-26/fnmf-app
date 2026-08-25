package com.example.nhumonglenh.data.remote

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String?,
    val tokenType: String?,
    val message: String?
)
