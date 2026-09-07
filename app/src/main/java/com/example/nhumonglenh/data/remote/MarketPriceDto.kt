package com.example.nhumonglenh.data.remote

data class MarketPriceDto(
    val symbol: String,
    val name: String? = null,
    val category: String? = null,
    val price: Double = 0.0,
    val change24h: Double = 0.0,
    val bidPrice: Double? = null,
    val askPrice: Double? = null,
    val lastUpdated: String? = null
)
