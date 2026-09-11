package com.example.nhumonglenh.data.repository

import android.content.Context
import com.example.nhumonglenh.data.local.AiAnalysisEntity
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.NewsEntity
import com.example.nhumonglenh.ui.news.ApiClient
import com.example.nhumonglenh.ui.news.News
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Source of Truth cho News và AI Analysis:
 * 1. Online: Đồng bộ từ /api/news/sync, lưu đồng thời vào Room DB (news_table & ai_analysis_table).
 *    Sau khi ghi Room thành công, đọc ngược lại từ Room để hiển thị lên UI.
 *    Nếu ghi Room thất bại -> trả CacheWriteFailure, tuyệt đối không trả cache cũ và nói là isFromCache=false.
 * 2. Offline: Đọc dữ liệu đã lưu trong Room DB (CacheFallback).
 * 3. Phân biệt rõ: SyncSuccess, CacheFallback, CacheWriteFailure, Empty.
 */
class NewsRepository private constructor(private val context: Context) {

    sealed class NewsResult {
        data class SyncSuccess(val news: List<News>) : NewsResult()
        data class CacheFallback(val news: List<News>, val reason: String) : NewsResult()
        data class CacheWriteFailure(val error: Throwable) : NewsResult()
        data class Empty(val message: String) : NewsResult()
    }

    suspend fun getNews(): NewsResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val newsDao = db.newsDao()

        // 1. Thử đồng bộ trực tuyến từ Backend
        val remoteResult = runCatching {
            ApiClient.service(context).syncNews()
        }

        if (remoteResult.isSuccess) {
            val remoteNews = remoteResult.getOrNull()?.data ?: emptyList()
            if (remoteNews.isNotEmpty()) {
                // Ghi vào Room DB nguyên vẹn qua atomic transaction
                try {
                    val newsEntities = ArrayList<NewsEntity>(remoteNews.size)
                    val aiEntities = ArrayList<AiAnalysisEntity>(remoteNews.size)

                    for (item in remoteNews) {
                        val epochMs = parseTimeToEpoch(item.publishedAt)
                        val joinedBulletPoints = if (item.bulletPoints.isNotEmpty()) item.bulletPoints.joinToString("\n") else ""
                        val joinedBulletPointsVi = if (!item.bulletPointsVi.isNullOrEmpty()) item.bulletPointsVi.joinToString("\n") else joinedBulletPoints

                        newsEntities.add(
                            NewsEntity(
                                item.id,
                                item.getEffectiveTitle(),
                                item.link ?: item.id,
                                epochMs,
                                item.getEffectivePublisher(),
                                item.author ?: "",
                                item.publishedAt,
                                item.imageUrl,
                                item.getEffectiveSummary(),
                                item.sentiment,
                                item.confidence,
                                joinedBulletPoints,
                                item.originalTitle ?: "",
                                item.originalSummary ?: "",
                                item.displayTitleVi ?: "",
                                item.displaySummaryVi ?: "",
                                joinedBulletPointsVi,
                                item.publisher ?: ""
                            )
                        )

                        aiEntities.add(
                            AiAnalysisEntity(
                                item.id,
                                item.getEffectiveSummary(),
                                item.sentiment,
                                item.confidence,
                                if (!item.bulletPointsVi.isNullOrEmpty()) item.bulletPointsVi.joinToString(" • ") else if (item.bulletPoints.isNotEmpty()) item.bulletPoints.joinToString(" • ") else item.getEffectiveSummary()
                            )
                        )
                    }

                    newsDao.upsertAllNewsWithAnalysis(newsEntities, aiEntities)

                    // Dọn dẹp các bản ghi cũ chưa được bản địa hóa hợp lệ
                    runCatching {
                        val allInDb = newsDao.getAllNews()
                        val invalidIds = allInDb.filter { !com.example.nhumonglenh.ui.news.NewsLocalizationPolicy.isEntityFullyLocalized(it) }.map { it.newsId }
                        if (invalidIds.isNotEmpty()) {
                            newsDao.deleteNewsByIds(invalidIds)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NewsRepository", "Lỗi lưu cache Room DB: ${e.message}", e)
                    // Nếu ghi Room thất bại, không trả cache cũ và nói là online, cũng không bypass Room
                    return@withContext NewsResult.CacheWriteFailure(e)
                }

                // Đọc lại từ Room DB làm Single Source of Truth
                val roomNews = readNewsFromRoom(newsDao)
                return@withContext if (roomNews.isNotEmpty()) {
                    NewsResult.SyncSuccess(roomNews)
                } else {
                    NewsResult.Empty("Chưa có bản tin tiếng Việt mới")
                }
            } else {
                // Server trả danh sách rỗng
                val roomNews = readNewsFromRoom(newsDao)
                return@withContext if (roomNews.isNotEmpty()) {
                    NewsResult.CacheFallback(roomNews, "Chưa có bản tin tiếng Việt mới")
                } else {
                    NewsResult.Empty("Chưa có bản tin tiếng Việt mới")
                }
            }
        } else {
            val err = remoteResult.exceptionOrNull()
            android.util.Log.w("NewsRepository", "Không thể tải tin tức trực tuyến: ${err?.message}", err)

            // 2. Nếu ngoại tuyến hoặc lỗi mạng, đọc từ Room DB Cache đã lọc bản địa hóa
            val cachedList = readNewsFromRoom(newsDao)
            if (cachedList.isNotEmpty()) {
                val errorMsg = err?.localizedMessage ?: "Mất kết nối mạng"
                return@withContext NewsResult.CacheFallback(cachedList, errorMsg)
            }

            // 3. Nếu cả server và Room đều rỗng hoặc không có tin tiếng Việt hợp lệ: Honest Empty State
            val errorMsg = "Chưa có bản tin tiếng Việt mới"
            return@withContext NewsResult.Empty(errorMsg)
        }
    }

    private fun readNewsFromRoom(newsDao: com.example.nhumonglenh.data.local.NewsDao): List<News> {
        val cachedEntities = try {
            newsDao.getAllNews()
        } catch (e: Exception) {
            android.util.Log.e("NewsRepository", "Lỗi đọc tin từ Room DB: ${e.message}", e)
            emptyList()
        }

        // Lọc nghiêm ngặt theo NewsLocalizationPolicy
        val validEntities = cachedEntities.filter {
            com.example.nhumonglenh.ui.news.NewsLocalizationPolicy.isEntityFullyLocalized(it)
        }

        return validEntities.map { entity ->
            val analysis = try {
                newsDao.getCachedAIAnalysis(entity.newsId)
            } catch (e: Exception) {
                android.util.Log.w("NewsRepository", "Lỗi đọc phân tích AI cho ${entity.newsId}: ${e.message}")
                null
            }

            val finalPublishedAt = if (entity.publishedAtRaw.isNotBlank()) {
                entity.publishedAtRaw
            } else {
                formatEpochToDate(entity.publishedAt)
            }

            val rawBullets = if (entity.bulletPointsVi.isNotBlank()) {
                entity.bulletPointsVi.split("\n").filter { it.isNotBlank() }
            } else if (entity.bulletPoints.isNotBlank()) {
                entity.bulletPoints.split("\n").filter { it.isNotBlank() }
            } else if (analysis?.reason != null && analysis.reason.isNotBlank()) {
                analysis.reason.split(" • ").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val effectiveTitle = if (entity.displayTitleVi.isNotBlank()) entity.displayTitleVi else entity.title
            val effectiveSummary = if (entity.displaySummaryVi.isNotBlank()) {
                entity.displaySummaryVi
            } else if (entity.summary.isNotBlank()) {
                entity.summary
            } else {
                analysis?.summary ?: ""
            }
            val effectivePublisher = if (entity.publisher.isNotBlank()) {
                entity.publisher
            } else if (entity.source.isNotBlank() && !com.example.nhumonglenh.ui.news.NewsCardPresentationMapper.isGeneric(entity.source)) {
                entity.source
            } else {
                ""
            }

            News(
                id = entity.newsId,
                title = effectiveTitle,
                source = effectivePublisher,
                publishedAt = finalPublishedAt,
                summary = effectiveSummary,
                sentiment = if (entity.sentiment.isNotBlank()) entity.sentiment else (analysis?.sentiment ?: "neutral"),
                confidence = if (entity.confidence > 0) entity.confidence else (analysis?.confidenceScore ?: 0),
                bulletPoints = rawBullets,
                author = entity.author,
                imageUrl = entity.imageUrl,
                link = entity.url,
                originalTitle = entity.originalTitle,
                originalSummary = entity.originalSummary,
                displayTitleVi = entity.displayTitleVi,
                displaySummaryVi = entity.displaySummaryVi,
                bulletPointsVi = rawBullets,
                publisher = effectivePublisher
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NewsRepository? = null

        fun getInstance(context: Context): NewsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NewsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun parseTimeToEpoch(timeStr: String?): Long {
            if (timeStr == null || timeStr.isBlank()) return 0L

            val isoFormats = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyyMMdd'T'HHmmss"
            )

            for (pattern in isoFormats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = sdf.parse(timeStr)
                    if (date != null) return date.time
                } catch (_: Exception) {
                }
            }

            try {
                val epoch = timeStr.toLongOrNull()
                if (epoch != null) {
                    return if (epoch < 10000000000L) epoch * 1000L else epoch
                }
            } catch (_: Exception) {
            }

            return 0L
        }

        fun formatEpochToDate(epochMs: Long): String {
            if (epochMs <= 0) return ""
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.format(Date(epochMs))
            } catch (_: Exception) {
                ""
            }
        }
    }
}
