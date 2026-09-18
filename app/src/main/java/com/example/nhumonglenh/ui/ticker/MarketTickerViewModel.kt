package com.example.nhumonglenh.ui.ticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nhumonglenh.data.remote.ApiService
import com.example.nhumonglenh.data.remote.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.awaitResponse

class MarketTickerViewModel(
    private val apiService: ApiService = RetrofitClient.apiService
) : ViewModel() {

    companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }

    private val _tickerItems = MutableStateFlow<List<MarketTickerUiModel>>(
        MarketTickerPolicy.createDefaultTickerList()
    )
    val tickerItems: StateFlow<List<MarketTickerUiModel>> = _tickerItems.asStateFlow()

    private var pollingJob: Job? = null

    val isPolling: Boolean
        get() = pollingJob?.isActive == true

    /**
     * Khởi chạy vòng lặp polling duy nhất (30s) cấp Activity.
     * Chống trùng lặp nếu vòng lặp đang chạy.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchPrices()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Tạm dừng polling khi Activity vào background (onStop / onPause).
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Thực hiện tải giá thị trường từ GET /api/market/prices.
     * Giữ nguyên snapshot gần nhất nếu gặp lỗi mạng tạm thời.
     */
    suspend fun fetchPrices() {
        try {
            val response = apiService.getMarketPrices().awaitResponse()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    _tickerItems.value = MarketTickerPolicy.mergeTickerData(_tickerItems.value, body)
                }
            } else {
                _tickerItems.value = MarketTickerPolicy.mergeTickerData(_tickerItems.value, null)
            }
        } catch (_: Exception) {
            _tickerItems.value = MarketTickerPolicy.mergeTickerData(_tickerItems.value, null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

class MarketTickerViewModelFactory(
    private val apiService: ApiService = RetrofitClient.apiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MarketTickerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MarketTickerViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
