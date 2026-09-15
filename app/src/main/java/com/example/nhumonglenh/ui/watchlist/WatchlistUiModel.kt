package com.example.nhumonglenh.ui.watchlist

data class WatchlistUiModel(
    val symbol: String,
    val fullName: String,
    val price: Double?,
    val changePercent: Double?,
    val iconColor: Int = 0,
    val isOffline: Boolean = false,
    val priceAsOf: String? = null,
    val recommendation: String? = null,
    val latestReportTitle: String? = null,
    val latestReportUrl: String? = null,
    val isStock: Boolean = false
)
