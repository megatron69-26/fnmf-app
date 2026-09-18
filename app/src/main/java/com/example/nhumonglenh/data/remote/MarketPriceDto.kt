package com.example.nhumonglenh.data.remote

data class MarketPriceDto(
    val symbol: String,
    val name: String? = null,
    val category: String? = null,
    val price: Double? = null,
    val change24h: Double? = null,
    val bidPrice: Double? = null,
    val askPrice: Double? = null,
    val lastUpdated: String? = null,
    val stale: Boolean? = false,
    val source: String? = null,
    val priceAsOf: String? = null
)
