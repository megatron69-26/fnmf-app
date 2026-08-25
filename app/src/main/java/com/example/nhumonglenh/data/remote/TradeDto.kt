package com.example.nhumonglenh.data.remote

data class OrderRequest(
    val symbol: String,
    val type: String, // "BUY" hoặc "SELL"
    val quantity: Double
)

data class OrderResponse(
    val transactionId: Long?,
    val symbol: String?,
    val type: String?,
    val price: Double?,
    val quantity: Double?,
    val totalAmount: Double?,
    val remainingBalance: Double?,
    val message: String?
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
