package com.example.nhumonglenh.ui.news

data class News(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: String,
    val imageUrl: String = "",
    val sentiment: String,        // "bullish" | "bearish" | "neutral"
    val confidence: Int,         // 0..100
    val bulletPoints: List<String>,
    val author: String? = null,
    val link: String? = null,
    val originalTitle: String? = null,
    val originalSummary: String? = null,
    val displayTitleVi: String? = null,
    val displaySummaryVi: String? = null,
    val bulletPointsVi: List<String>? = null,
    val publisher: String? = null
) {
    fun getEffectiveTitle(): String = displayTitleVi?.takeIf { it.isNotBlank() } ?: title
    fun getEffectiveSummary(): String = displaySummaryVi?.takeIf { it.isNotBlank() } ?: summary
    fun getEffectiveBullets(): List<String> = bulletPointsVi?.takeIf { it.isNotEmpty() } ?: bulletPoints
    fun getEffectivePublisher(): String = publisher?.takeIf { it.isNotBlank() } ?: source.takeIf { it.isNotBlank() } ?: ""
}
