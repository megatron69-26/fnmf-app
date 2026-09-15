package com.example.nhumonglenh.data.remote

import com.google.gson.annotations.SerializedName

import java.math.BigDecimal

data class OrderRequest(
    val symbol: String,
    val type: String, // "BUY" hoặc "SELL"
    val quantity: BigDecimal,
    val clientOrderId: String? = null // UUID chống trùng lặp lệnh
)

data class OrderResponse(
    @SerializedName(value = "transactionId", alternate = ["id"])
    val transactionId: Long? = null,
    val symbol: String? = null,
    val type: String? = null,
    val price: Double? = null,
    val quantity: Double? = null,
    val totalAmount: Double? = null,
    val remainingBalance: Double? = null,
    val message: String? = null,
    val walletId: Long? = null,
    @SerializedName(value = "executedAt", alternate = ["createdAt"])
    val executedAt: String? = null
)

data class PortfolioSummaryDto(
    val cashBalanceUsd: Double?,
    val initialBalanceUsd: Double?,
    val totalHoldingsValue: Double?,
    val totalNetWorth: Double?,
    val totalPnL: Double?,
    val totalPnLPercent: Double?,
    val holdings: List<HoldingDto>?,
    val fullyValued: Boolean? = true
)

data class HoldingDto(
    val symbol: String?,
    val quantity: Double?,
    val avgBuyPrice: Double?,
    val currentPrice: Double?,
    val unrealizedPnL: Double?
)

data class ForecastResponse(
    val symbol: String?,
    val assetName: String?,
    val currentPrice: Double?,
    val trendPrediction: String?,
    val timeframe: String?,
    val supportLevel: Double?,
    val resistanceLevel: Double?,
    val recommendation: String?,
    val confidenceScore: Int?,
    val keyDrivers: List<String>?,
    val technicalOutlook: String?,
    val fundamentalOutlook: String?,
    val analysisSource: String? = null,
    val candleCount: Int? = null,
    val fromCache: Boolean? = false,
    val createdAt: String? = null,
    val maxDailyRefreshes: Int? = null,
    val usedRefreshes: Int? = null,
    val remainingRefreshes: Int? = null,
    val quotaDate: String? = null
)

data class RefreshQuotaDto(
    val maxDailyRefreshes: Int = 5,
    val usedRefreshes: Int = 0,
    val remainingRefreshes: Int = 5,
    val quotaDate: String? = null
)

data class ForecastRefreshRequest(
    val symbol: String,
    val timeframe: String = "24H_7D",
    val clientRequestId: String? = null
)

data class WatchlistItemDto(
    val id: Long? = null,
    val symbol: String,
    val name: String? = null,
    val category: String? = null,
    val currentPrice: Double? = null,
    val change24h: Double? = null,
    val displayOrder: Int? = null,
    val createdAt: String? = null,
    val priceAsOf: String? = null,
    val stale: Boolean? = false,
    val recommendation: String? = null,
    val latestReportTitle: String? = null,
    val latestReportUrl: String? = null
)

data class WatchlistRequest(
    val symbol: String,
    val displayOrder: Int? = 1
)

data class StockCatalogDto(
    val symbol: String,
    val name: String,
    val category: String? = "STOCK"
)

data class StockDetailDto(
    val symbol: String,
    val name: String,
    val currentPrice: Double?,
    val change24h: Double?,
    val priceAsOf: String?,
    val stale: Boolean? = false,
    val recommendation: String? = null,
    val latestReportTitle: String? = null,
    val latestReportUrl: String? = null
)
