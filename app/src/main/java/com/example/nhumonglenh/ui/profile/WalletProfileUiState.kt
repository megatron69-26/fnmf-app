package com.example.nhumonglenh.ui.profile

import com.example.nhumonglenh.data.repository.WalletProfileCombinedData

sealed class WalletProfileUiState {
    object Idle : WalletProfileUiState()
    object Loading : WalletProfileUiState()
    data class Success(
        val data: WalletProfileCombinedData,
        val isOffline: Boolean = false,
        val lastUpdatedFormatted: String = ""
    ) : WalletProfileUiState()
    object Empty : WalletProfileUiState()
    object Unauthorized : WalletProfileUiState()
    data class NetworkError(
        val message: String,
        val cachedData: WalletProfileCombinedData? = null
    ) : WalletProfileUiState()
    data class ServerError(
        val code: Int,
        val message: String,
        val cachedData: WalletProfileCombinedData? = null
    ) : WalletProfileUiState()
}