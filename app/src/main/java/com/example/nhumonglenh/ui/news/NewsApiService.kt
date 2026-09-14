package com.example.nhumonglenh.ui.news

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NewsApiService {
    @GET("api/news/sync")
    suspend fun syncNews(): NewsResponse

    @POST("api/news/refresh")
    suspend fun refreshNews(
        @Header("Authorization") token: String,
        @Header("Client-Request-ID") clientRequestId: String,
        @Query("limit") limit: Int = 5
    ): NewsResponse
}
