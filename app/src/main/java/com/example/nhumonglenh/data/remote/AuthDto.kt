package com.example.nhumonglenh.data.remote

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String? = null
)

data class UserDto(
    val id: Long? = null,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val role: String? = null,
    val validEmail: Boolean? = null,
    val needsEmailUpdate: Boolean? = null
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
