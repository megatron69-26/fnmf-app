package com.example.nhumonglenh.data.local

import android.content.Context
import com.example.nhumonglenh.R

/**
 * Điều phối kết quả xác thực và lưu trữ Token an toàn (AuthFlowCoordinator).
 * Đảm bảo nguyên tắc FAIL-CLOSED:
 * - Chỉ cho phép điều hướng vào màn hình chính khi Token được lưu trữ và mã hóa thành công trên thiết bị.
 * - Khi lưu trữ thất bại: dọn dẹp phiên dang dở (clearSession), kích hoạt lại nút bấm và thông báo an toàn,
 *   tuyệt đối không để lộ chi tiết lỗi KeyStore/hạ tầng ra giao diện người dùng.
 */
object AuthFlowCoordinator {

    sealed class PersistenceOutcome {
        object NavigateToMain : PersistenceOutcome()
        data class StayOnAuth(
            val errorMessageResId: Int,
            val isButtonEnabled: Boolean,
            val isSessionCleared: Boolean
        ) : PersistenceOutcome()
    }

    /**
     * Xử lý kết quả đăng nhập / đăng ký thành công từ API.
     */
    fun handleAuthSuccess(
        context: Context,
        token: String?,
        tokenSaver: (Context, String) -> Boolean = { ctx, tok -> AuthSessionManager.saveToken(ctx, tok) },
        sessionClearer: (Context) -> Unit = { ctx -> AuthSessionManager.clearSession(ctx) }
    ): PersistenceOutcome {
        if (token.isNullOrBlank()) {
            sessionClearer(context)
            return PersistenceOutcome.StayOnAuth(
                errorMessageResId = R.string.auth_err_token_persistence_failed,
                isButtonEnabled = true,
                isSessionCleared = true
            )
        }

        val saved = tokenSaver(context, token)
        return if (saved) {
            PersistenceOutcome.NavigateToMain
        } else {
            sessionClearer(context)
            PersistenceOutcome.StayOnAuth(
                errorMessageResId = R.string.auth_err_token_persistence_failed,
                isButtonEnabled = true,
                isSessionCleared = true
            )
        }
    }
}
