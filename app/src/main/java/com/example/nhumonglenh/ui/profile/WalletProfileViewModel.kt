package com.example.nhumonglenh.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nhumonglenh.data.repository.RepoResult
import com.example.nhumonglenh.data.repository.WalletProfileCombinedData
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletProfileViewModel(
    private val repository: WalletProfileRepository
) : ViewModel() {

    companion object {
        const val DEBOUNCE_INTERVAL_MS = 30_000L // 30 giây debounce sau lần tải thành công
        const val FAILURE_RETRY_INTERVAL_MS = 10_000L // 10 giây debounce khi server offline/lỗi
    }

    private val _uiState = MutableStateFlow<WalletProfileUiState>(WalletProfileUiState.Idle)
    val uiState: StateFlow<WalletProfileUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null
    var cachedData: WalletProfileCombinedData? = null
        private set
    var lastSuccessfulFetchTimestamp: Long = 0L
        private set
    var lastAttemptTimestamp: Long = 0L
        private set

    val isRequestInFlight: Boolean
        get() = currentJob?.isActive == true

    fun loadData(token: String, forceRefresh: Boolean = false) {
        if (token.isBlank()) {
            _uiState.value = WalletProfileUiState.Unauthorized
            return
        }

        // 1. Kiểm tra request đang chạy (in-flight)
        if (isRequestInFlight) {
            if (forceRefresh) {
                // Hủy request cũ đúng cách trước khi bắt đầu request mới
                currentJob?.cancel()
            } else {
                // Bỏ qua nếu đã có request đang chạy
                return
            }
        }

        val currentTime = System.currentTimeMillis()

        // 2. Debounce dựa trên lastSuccessfulFetchTimestamp (30 giây)
        if (!forceRefresh && lastSuccessfulFetchTimestamp > 0 && (currentTime - lastSuccessfulFetchTimestamp < DEBOUNCE_INTERVAL_MS)) {
            return
        }

        // 3. Nếu lần trước thất bại (offline/server error), không spam API khi chuyển tab liên tục (10 giây)
        if (!forceRefresh && lastSuccessfulFetchTimestamp == 0L && lastAttemptTimestamp > 0 && (currentTime - lastAttemptTimestamp < FAILURE_RETRY_INTERVAL_MS)) {
            return
        }

        lastAttemptTimestamp = currentTime

        currentJob = viewModelScope.launch {
            _uiState.value = WalletProfileUiState.Loading

            when (val result = repository.getWalletProfile(token)) {
                is RepoResult.Success -> {
                    val data = result.data
                    if (data.authResponse == null && data.portfolio == null && data.history.isEmpty()) {
                        _uiState.value = WalletProfileUiState.Empty
                    } else {
                        cachedData = data
                        lastSuccessfulFetchTimestamp = System.currentTimeMillis()
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        _uiState.value = WalletProfileUiState.Success(
                            data = data,
                            isOffline = false,
                            lastUpdatedFormatted = timeStr
                        )
                    }
                }
                is RepoResult.Unauthorized -> {
                    _uiState.value = WalletProfileUiState.Unauthorized
                }
                is RepoResult.NetworkError -> {
                    val cached = cachedData
                    if (cached != null) {
                        _uiState.value = WalletProfileUiState.Success(
                            data = cached,
                            isOffline = true
                        )
                    } else {
                        _uiState.value = WalletProfileUiState.NetworkError(result.message)
                    }
                }
                is RepoResult.ServerError -> {
                    val cached = cachedData
                    if (cached != null) {
                        _uiState.value = WalletProfileUiState.Success(
                            data = cached,
                            isOffline = true
                        )
                    } else {
                        _uiState.value = WalletProfileUiState.ServerError(result.code, result.message)
                    }
                }
            }
        }
    }
}

class WalletProfileViewModelFactory(
    private val repository: WalletProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalletProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}