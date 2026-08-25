package com.example.nhumonglenh.data.remote

data class MobileNewsBundleResponse(
    val news: MobileNewsDto,
    val aiAnalysis: MobileAiAnalysisDto
)

data class MobileNewsDto(
    val newsId: String,
    val title: String,
    val url: String,
    val publishedAt: Long
)

data class MobileAiAnalysisDto(
    val newsId: String,
    val summary: String,
    val sentiment: String,
    val confidenceScore: Int,
    val reason: String
)
