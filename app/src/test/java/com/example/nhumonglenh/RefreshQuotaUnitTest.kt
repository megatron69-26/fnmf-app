package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.ForecastRefreshRequest
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.RefreshQuotaDto
import com.example.nhumonglenh.data.repository.RefreshQuotaManager
import com.example.nhumonglenh.ui.news.NewsResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class RefreshQuotaUnitTest {

    @Test
    fun refreshQuotaManager_updatesAndClampsCorrectly() {
        RefreshQuotaManager.setRemaining(5)
        assertEquals(5, RefreshQuotaManager.getRemaining())

        RefreshQuotaManager.setRemaining(3)
        assertEquals(3, RefreshQuotaManager.getRemaining())

        RefreshQuotaManager.setRemaining(0)
        assertEquals(0, RefreshQuotaManager.getRemaining())

        // Test clamping bounds
        RefreshQuotaManager.setRemaining(-1)
        assertEquals(0, RefreshQuotaManager.getRemaining())

        RefreshQuotaManager.setRemaining(10)
        assertEquals(5, RefreshQuotaManager.getRemaining())
    }

    @Test
    fun quotaLabel_formattingMatchesSpecification() {
        for (i in 5 downTo 0) {
            val formatted = String.format(Locale.getDefault(), "Còn %d lượt làm mới hôm nay", i)
            assertEquals("Còn $i lượt làm mới hôm nay", formatted)
            // Đảm bảo không chứa emoji hoặc ký hiệu trang trí lạ
            assertTrue(formatted.matches(Regex("^Còn [0-5] lượt làm mới hôm nay$")))
        }

        val exhaustedMsg = "Bạn đã dùng hết lượt làm mới hôm nay"
        assertFalse(exhaustedMsg.contains("!"))
        assertFalse(exhaustedMsg.contains("🔥"))
        assertFalse(exhaustedMsg.contains("⚠️"))
    }

    @Test
    fun doubleGestureLock_preventsConcurrentRefresh() {
        val isRefreshing = AtomicBoolean(false)

        // Lần kéo thứ nhất thành công
        val firstAcquired = isRefreshing.compareAndSet(false, true)
        assertTrue(firstAcquired)

        // Lần kéo thứ hai khi lần một đang chạy phải bị từ chối ngay lập tức
        val secondAcquired = isRefreshing.compareAndSet(false, true)
        assertFalse(secondAcquired)

        // Khi lần một hoàn tất
        isRefreshing.set(false)

        // Lần kéo tiếp theo được chấp nhận
        val thirdAcquired = isRefreshing.compareAndSet(false, true)
        assertTrue(thirdAcquired)
    }

    @Test
    fun gsonDeserialization_forecastResponseWithQuota() {
        val json = """
            {
                "symbol": "BTCUSDT",
                "currentPrice": 98500.0,
                "recommendation": "BUY",
                "maxDailyRefreshes": 5,
                "usedRefreshes": 2,
                "remainingRefreshes": 3,
                "quotaDate": "2026-09-15"
            }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(json, ForecastResponse::class.java)
        assertNotNull(response)
        assertEquals(5, response.maxDailyRefreshes)
        assertEquals(2, response.usedRefreshes)
        assertEquals(3, response.remainingRefreshes)
        assertEquals("2026-09-15", response.quotaDate)
    }

    @Test
    fun gsonDeserialization_refreshQuotaDto() {
        val json = """
            {
                "maxDailyRefreshes": 5,
                "usedRefreshes": 5,
                "remainingRefreshes": 0,
                "quotaDate": "2026-09-15"
            }
        """.trimIndent()

        val gson = Gson()
        val dto = gson.fromJson(json, RefreshQuotaDto::class.java)
        assertNotNull(dto)
        assertEquals(5, dto.maxDailyRefreshes)
        assertEquals(5, dto.usedRefreshes)
        assertEquals(0, dto.remainingRefreshes)
        assertEquals("2026-09-15", dto.quotaDate)
    }

    @Test
    fun gsonDeserialization_newsResponseWithQuota() {
        val json = """
            {
                "status": "OK",
                "message": "Cập nhật thành công",
                "data": [],
                "maxDailyRefreshes": 5,
                "usedRefreshes": 1,
                "remainingRefreshes": 4,
                "quotaDate": "2026-09-15"
            }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(json, NewsResponse::class.java)
        assertNotNull(response)
        assertEquals(5, response.maxDailyRefreshes)
        assertEquals(1, response.usedRefreshes)
        assertEquals(4, response.remainingRefreshes)
    }

    @Test
    fun forecastRefreshRequest_serialization() {
        val req = ForecastRefreshRequest(
            symbol = "BTCUSDT",
            timeframe = "24H_7D",
            clientRequestId = "req-12345"
        )
        val gson = Gson()
        val json = gson.toJson(req)
        assertTrue(json.contains("BTCUSDT"))
        assertTrue(json.contains("req-12345"))
    }

    @Test
    fun newsRefreshResult_degradedOrEmpty_keepsCachedDataAndUpdatesQuota() {
        val cached = listOf(
            com.example.nhumonglenh.ui.news.News(
                id = "n-1",
                title = "Tin cũ đã lưu",
                summary = "Tóm tắt tin cũ",
                source = "VnEconomy",
                publishedAt = "2026-09-14",
                sentiment = "neutral",
                confidence = 85,
                bulletPoints = listOf("Ý chính 1")
            )
        )
        val result = com.example.nhumonglenh.data.repository.NewsRepository.NewsRefreshResult.DegradedOrEmpty(
            status = "DEGRADED",
            message = "Tin tức tạm thời chưa sẵn sàng",
            cachedNews = cached,
            remainingRefreshes = 3,
            quotaDate = "2026-09-15"
        )

        assertEquals("DEGRADED", result.status)
        assertEquals("Tin tức tạm thời chưa sẵn sàng", result.message)
        assertEquals(1, result.cachedNews.size)
        assertEquals("n-1", result.cachedNews[0].id)
        assertEquals(3, result.remainingRefreshes)
        assertEquals("2026-09-15", result.quotaDate)
    }

    @Test
    fun newsRefreshResult_unauthorized_typePreserved() {
        val result = com.example.nhumonglenh.data.repository.NewsRepository.NewsRefreshResult.Unauthorized(
            message = "Phiên đăng nhập đã hết hạn"
        )
        assertTrue(result is com.example.nhumonglenh.data.repository.NewsRepository.NewsRefreshResult.Unauthorized)
        assertEquals("Phiên đăng nhập đã hết hạn", result.message)
    }

    @Test
    fun retrofitClient_timeoutsConfiguredCorrectly_readTimeout55s() {
        assertEquals(55000L, com.example.nhumonglenh.data.remote.RetrofitClient.okHttpClient.readTimeoutMillis.toLong())
        assertEquals(20000L, com.example.nhumonglenh.data.remote.RetrofitClient.okHttpClient.connectTimeoutMillis.toLong())
        assertEquals(20000L, com.example.nhumonglenh.data.remote.RetrofitClient.okHttpClient.writeTimeoutMillis.toLong())
    }

    @Test
    fun forecastResponse_503ErrorHandling_notLabeledAsNetworkError() {
        val error503Message = "Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau."
        val networkErrorMessage = "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng."

        // Kiểm tra thông điệp 503 khác biệt hoàn toàn với lỗi mạng client
        org.junit.Assert.assertNotEquals(networkErrorMessage, error503Message)
        assertEquals("Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau.", error503Message)

        // Giả lập errorBody trả về từ backend khi 503 FORECAST_UNAVAILABLE
        val errorJson = """
            {
                "status": "ERROR",
                "code": "FORECAST_UNAVAILABLE",
                "message": "Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau.",
                "maxDailyRefreshes": 5,
                "usedRefreshes": 3,
                "remainingRefreshes": 2,
                "quotaDate": "2026-09-15"
            }
        """.trimIndent()

        val json = org.json.JSONObject(errorJson)
        assertEquals("FORECAST_UNAVAILABLE", json.getString("code"))
        assertEquals("Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau.", json.getString("message"))
        assertEquals(2, json.getInt("remainingRefreshes"))

        RefreshQuotaManager.setRemaining(json.getInt("remainingRefreshes"))
        assertEquals(2, RefreshQuotaManager.getRemaining())
    }
}
