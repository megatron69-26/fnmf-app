package com.example.nhumonglenh.data.remote

/**
 * DTO dữ liệu nến OHLCV từ Backend Khôi (/api/market/candles).
 * Phục vụ ánh xạ trực tiếp sang CandleEntry của MPAndroidChart.
 */
data class CandleDto(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
