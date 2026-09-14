package com.example.nhumonglenh.ui.news

data class NewsResponse(
    val status: String,
    val message: String? = null,
    val data: List<News>,
    val maxDailyRefreshes: Int? = null,
    val usedRefreshes: Int? = null,
    val remainingRefreshes: Int? = null,
    val quotaDate: String? = null
)

