package com.example.nhumonglenh.ui.news

data class NewsResponse(
    val status: String,
    val message: String? = null,
    val data: List<News>
)

