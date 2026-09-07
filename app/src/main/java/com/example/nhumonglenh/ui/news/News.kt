package com.example.nhumonglenh.ui.news

data class News(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: String,
    val imageUrl: String,
    val sentiment: String,        // "bullish" | "bearish" | "neutral"
    val confidence: Int,         // 0..100
    val bulletPoints: List<String>
)

