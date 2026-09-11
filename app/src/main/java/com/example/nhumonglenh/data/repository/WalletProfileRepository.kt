package com.example.nhumonglenh.data.repository

import android.util.Log
import com.example.nhumonglenh.data.remote.ApiService
import com.example.nhumonglenh.data.remote.AuthResponse
import com.example.nhumonglenh.data.remote.OrderResponse
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    object Unauthorized : RepoResult<Nothing>()
    data class NetworkError(val message: String) : RepoResult<Nothing>()
    data class ServerError(val code: Int, val message: String) : RepoResult<Nothing>()
}

data class WalletProfileCombinedData(
    val authResponse: AuthResponse?,
    val portfolio: PortfolioSummaryDto?,
    val history: List<OrderResponse>
)

class WalletProfileRepository(private val apiService: ApiService) {

    companion object {
        private const val TAG = "WalletProfileRepo"

        /**
         * Kiểm tra xem phản hồi HTTP có phải do lỗi Token / Xác thực hay không.
         * Hỗ trợ HTTP 401, 403 và HTTP 400 (do backend trả 400 kèm thông điệp token/authorization).
         */
        fun isTokenOrAuthError(response: Response<*>): Boolean {
            val code = response.code()
            if (code == 401 || code == 403) return true
            if (code == 400) {
                val errBody = try {
                    response.errorBody()?.string()?.lowercase(Locale.ROOT) ?: ""
                } catch (_: Exception) {
                    ""
                }
                if (errBody.contains("token") ||
                    errBody.contains("bearer") ||
                    errBody.contains("xác thực") ||
                    errBody.contains("hết hạn") ||
                    errBody.contains("authorization") ||
                    errBody.contains("unauthorized")
                ) {
                    return true
                }
            }
            return false
        }
    }

    suspend fun getWalletProfile(token: String): RepoResult<WalletProfileCombinedData> = withContext(Dispatchers.IO) {
        val authHeader = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

        try {
            // Thực thi song song 3 API với khả năng tự hủy (cancel) ngay khi coroutine bị hủy
            val profileDeferred = async { apiService.getProfile(authHeader).awaitCancellable() }
            val portfolioDeferred = async { apiService.getPortfolio(authHeader).awaitCancellable() }
            val historyDeferred = async { apiService.getOrderHistory(authHeader).awaitCancellable() }

            val profileResp = profileDeferred.await()
            val portfolioResp = portfolioDeferred.await()
            val historyResp = historyDeferred.await()

            // 1. Kiểm tra xác thực (401, 403 hoặc 400 do token/xác thực không hợp lệ)
            if (isTokenOrAuthError(profileResp) || isTokenOrAuthError(portfolioResp) || isTokenOrAuthError(historyResp)) {
                Log.w(TAG, "Phát hiện lỗi xác thực (Token thiếu, sai hoặc hết hạn)")
                return@withContext RepoResult.Unauthorized
            }

            // 2. Nếu ít nhất một trong hai API chính (Profile hoặc Portfolio) thành công
            if (profileResp.isSuccessful || portfolioResp.isSuccessful) {
                val profileBody = if (profileResp.isSuccessful) profileResp.body() else null
                val portfolioBody = if (portfolioResp.isSuccessful) portfolioResp.body() else null
                val historyBody = if (historyResp.isSuccessful) historyResp.body() ?: emptyList() else emptyList()

                val combined = WalletProfileCombinedData(
                    authResponse = profileBody,
                    portfolio = portfolioBody,
                    history = historyBody
                )
                RepoResult.Success(combined)
            } else {
                val errCode = if (!profileResp.isSuccessful) profileResp.code() else portfolioResp.code()
                val errMsg = "Máy chủ phản hồi mã lỗi HTTP $errCode"
                Log.e(TAG, errMsg)
                RepoResult.ServerError(errCode, errMsg)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Lỗi kết nối mạng: ${e.message}", e)
            RepoResult.NetworkError("Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng!")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi không xác định khi tải WalletProfile: ${e.message}", e)
            RepoResult.ServerError(-1, "Lỗi hệ thống khi tải thông tin hồ sơ")
        }
    }
}

/**
 * Extension function chuyển Retrofit Call thành Coroutine có hỗ trợ hủy (Cancellation).
 * Khi coroutine bị hủy, Call.cancel() sẽ được gọi ngay lập tức để giải phóng socket và tài nguyên.
 */
suspend fun <T> Call<T>.awaitCancellable(): Response<T> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {}
    }
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (continuation.isActive) {
                continuation.resume(response)
            }
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            if (continuation.isActive) {
                continuation.resumeWithException(t)
            }
        }
    })
}