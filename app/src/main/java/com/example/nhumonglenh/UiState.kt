package com.example.nhumonglenh

/**
 * Quản lý trạng thái giao diện (Loading, Thành công, Lỗi, Trống dữ liệu).
 */
sealed interface UiState {
    object Loading : UiState
    object Empty : UiState
    data class Success(val message: String? = null) : UiState
    data class Error(val errorMessage: String?) : UiState
}
