package com.example.nhumonglenh.data.repository

import android.content.Context
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.RefreshQuotaDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Quản lý tập trung trạng thái hạn mức làm mới hàng ngày (Pull-to-Refresh Quota)
 * Dùng chung giữa hai module Dự báo (Forecast) và Tin tức (News).
 */
object RefreshQuotaManager {

    const val MAX_DAILY_REFRESHES = 5

    private val _remainingQuota = MutableStateFlow(MAX_DAILY_REFRESHES)
    val remainingQuota: StateFlow<Int> = _remainingQuota

    fun getRemaining(): Int = _remainingQuota.value

    fun setRemaining(remaining: Int) {
        _remainingQuota.value = remaining.coerceIn(0, MAX_DAILY_REFRESHES)
    }

    /**
     * Đồng bộ trạng thái quota từ máy chủ (GET /api/refresh-quota/status).
     * Tuyệt đối không trừ lượt làm mới.
     */
    fun syncQuotaStatus(context: Context, onComplete: ((Int) -> Unit)? = null) {
        val authHeader = AuthSessionManager.getAuthHeader(context)
        if (authHeader.isNullOrBlank()) {
            onComplete?.invoke(_remainingQuota.value)
            return
        }

        RetrofitClient.apiService.getRefreshQuotaStatus(authHeader).enqueue(object : Callback<RefreshQuotaDto> {
            override fun onResponse(call: Call<RefreshQuotaDto>, response: Response<RefreshQuotaDto>) {
                if (response.isSuccessful && response.body() != null) {
                    val remaining = response.body()!!.remainingRefreshes
                    setRemaining(remaining)
                    onComplete?.invoke(remaining)
                } else {
                    onComplete?.invoke(_remainingQuota.value)
                }
            }

            override fun onFailure(call: Call<RefreshQuotaDto>, t: Throwable) {
                onComplete?.invoke(_remainingQuota.value)
            }
        })
    }
}
