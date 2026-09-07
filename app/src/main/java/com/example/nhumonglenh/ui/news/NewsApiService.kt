package com.example.nhumonglenh.ui.news

import retrofit2.http.GET

interface NewsApiService {
    @GET("api/news/sync")
    suspend fun syncNews(): NewsResponse
}
