package com.example.nhumonglenh.ui.news

data class NewsResponse(
    val status: String,
    val message: String? = null,
    val data: List<News>,
    val maxDailyRefreshes: Int? = null,
    val usedRefreshes: Int? = null,
    val remainingRefreshes: Int? = null,
    val quotaDate: String? = null,
    val stale: Boolean = false,
    val fromCache: Boolean = false,
    val dataAsOf: String? = null,
    val latestPublishedAt: String? = null
)

