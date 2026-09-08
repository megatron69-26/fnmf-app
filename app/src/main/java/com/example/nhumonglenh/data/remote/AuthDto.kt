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

data class UserDto(
    val id: Long? = null,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String? = null
)

data class WalletDto(
    val id: Long? = null,
    val userId: Long? = null,
    val balanceUsd: Double? = null,
    val initialBalance: Double? = null
)

data class AuthResponse(
    val token: String? = null,
    val tokenType: String? = null,
    val user: UserDto? = null,
    val wallet: WalletDto? = null,
    val message: String? = null
)
