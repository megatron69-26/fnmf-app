package com.example.nhumonglenh.data.remote

import com.google.gson.annotations.SerializedName

data class OrderRequest(
    val symbol: String,
    val type: String, // "BUY" hoặc "SELL"
    val quantity: Double
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
    val holdings: List<HoldingDto>?
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
    val fundamentalOutlook: String?
)
