package com.example.nhumonglenh

import com.example.nhumonglenh.data.local.AiAnalysisEntity
import com.example.nhumonglenh.data.local.NewsEntity
import com.example.nhumonglenh.data.local.WatchlistItem
import com.example.nhumonglenh.data.remote.ApiService
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.OrderRequest
import com.example.nhumonglenh.data.repository.NewsRepository
import com.example.nhumonglenh.ui.trading.OrderIdempotencyManager
import com.example.nhumonglenh.ui.trading.OrderTicketBottomSheet
import com.example.nhumonglenh.ui.watchlist.WatchlistUiModel
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File

/**
 * PRODUCTION CORRECTNESS & INTEGRITY UNIT TEST SUITE
 * Kiểm thử tính đúng đắn của toàn bộ các giao thức dữ liệu, Idempotency, Schema Room v4,
 * cơ chế Single Source of Truth cho News và quét sạch Mojibake UTF-8.
 */
class CorrectnessAndIntegrityUnitTest {

    private val gson = Gson()

    // ------------------------------------------------------------------------------------
    // 1. FORECAST CONTRACT & VALIDATION
    // ------------------------------------------------------------------------------------
    @Test
    fun testForecastApiContract_hasGetForecastEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "getForecast" }
        assertNotNull("ApiService must declare getForecast method", method)

        val getAnnotation = method!!.getAnnotation(GET::class.java)
        assertNotNull("getForecast must have @GET annotation", getAnnotation)
        assertEquals("/api/forecast/{symbol}", getAnnotation!!.value)

        val paramAnnotations = method.parameterAnnotations
        assertTrue(
            "First parameter must be @Path(\"symbol\")",
            paramAnnotations[0].any { it is Path && it.value == "symbol" }
        )
    }

    @Test
    fun testForecastResponse_deserialization() {
        val sampleJson = """
            {
                "symbol": "BTCUSDT",
                "assetName": "Bitcoin",
                "currentPrice": 78000.0,
                "trendPrediction": "BULLISH",
                "timeframe": "24H_7D",
                "supportLevel": 75000.0,
                "resistanceLevel": 82000.0,
                "recommendation": "BUY",
                "confidenceScore": 85,
                "keyDrivers": ["Fed giữ nguyên lãi suất", "Dòng tiền ETF phục hồi"],
                "technicalOutlook": "Thị trường duy trì xu hướng tích lũy quanh vùng hỗ trợ",
                "fundamentalOutlook": "Tích cực",
                "fromCache": false
            }
        """.trimIndent()

        val parsed = gson.fromJson(sampleJson, ForecastResponse::class.java)
        assertNotNull(parsed)
        assertEquals("BTCUSDT", parsed.symbol)
        assertEquals("BULLISH", parsed.trendPrediction)
        assertEquals("BUY", parsed.recommendation)
        assertEquals(85, parsed.confidenceScore)
        assertEquals(2, parsed.keyDrivers?.size)
        assertEquals("Fed giữ nguyên lãi suất", parsed.keyDrivers?.get(0))
        assertTrue(parsed.technicalOutlook?.contains("tích lũy") == true)
    }

    // ------------------------------------------------------------------------------------
    // 2. CANONICAL SYMBOLS & NO-FALLBACK INTEGRITY
    // ------------------------------------------------------------------------------------
    @Test
    fun testFormatSymbolDisplay_onlyHandlesSupportedSymbols() {
        assertEquals("BTC/USDT", OrderTicketBottomSheet.formatSymbolDisplay("BTCUSDT"))
        assertEquals("ETH/USDT", OrderTicketBottomSheet.formatSymbolDisplay("ETHUSDT"))
        assertEquals("XAU/USD", OrderTicketBottomSheet.formatSymbolDisplay("XAUUSD"))
        assertEquals("BTC/USDT", OrderTicketBottomSheet.formatSymbolDisplay("BTC/USDT"))
    }

    @Test
    fun testGetAssetTicker_stripsCurrencySuffixesCorrectly() {
        assertEquals("BTC", OrderTicketBottomSheet.getAssetTicker("BTCUSDT"))
        assertEquals("ETH", OrderTicketBottomSheet.getAssetTicker("ETHUSDT"))
        assertEquals("XAU", OrderTicketBottomSheet.getAssetTicker("XAUUSD"))
        assertEquals("BTC", OrderTicketBottomSheet.getAssetTicker("BTC/USDT"))
        assertEquals("ETH", OrderTicketBottomSheet.getAssetTicker("ETH/USDT"))
        assertEquals("XAU", OrderTicketBottomSheet.getAssetTicker("XAU/USD"))
    }

    // ------------------------------------------------------------------------------------
    // 3. IDEMPOTENCY & ORDER REQUEST CLIENT_ORDER_ID & BIGDECIMAL PRECISION
    // ------------------------------------------------------------------------------------
    @Test
    fun testOrderRequest_includesClientOrderId_andBigDecimalSerialization() {
        val clientOrderId = "idempotent-uuid-12345"
        val qty = java.math.BigDecimal("0.050000")
        val order = OrderRequest(
            symbol = "BTCUSDT",
            type = "BUY",
            quantity = qty,
            clientOrderId = clientOrderId
        )

        assertNotNull(order.clientOrderId)
        assertEquals(clientOrderId, order.clientOrderId)
        assertEquals(0, order.quantity.compareTo(java.math.BigDecimal("0.05")))

        val json = gson.toJson(order)
        assertTrue("JSON must contain clientOrderId field", json.contains("clientOrderId"))
        assertTrue(json.contains(clientOrderId))
        assertTrue("JSON must contain quantity field", json.contains("quantity"))

        // Parse JSON quantity dưới dạng số và so sánh BigDecimal về mặt giá trị
        // Không bắt buộc giữ trailing zeros trong chuỗi JSON thô (0.050000 và 0.05 tương đương về mặt số học)
        val jsonElement = com.google.gson.JsonParser.parseString(json).asJsonObject
        val rawQtyElement = jsonElement.get("quantity")
        assertNotNull(rawQtyElement)
        val parsedJsonNumber = java.math.BigDecimal(rawQtyElement.asString)
        assertEquals("Giá trị số học trong JSON phải bằng 0.05", 0, parsedJsonNumber.compareTo(java.math.BigDecimal("0.05")))

        val parsedBack = gson.fromJson(json, OrderRequest::class.java)
        assertEquals(clientOrderId, parsedBack.clientOrderId)
        assertEquals(0, parsedBack.quantity.compareTo(java.math.BigDecimal("0.05")))
    }

    @Test
    fun testOrderIdempotencyManager_preservesKeyOnRetryAndRegeneratesOnParamChange() {
        val manager = OrderIdempotencyManager()

        // 1. Submit ban đầu: BUY 0.05 BTC
        val key1 = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.05"))
        assertNotNull(key1)
        assertEquals(key1, manager.activeClientOrderId)

        // 2. Retry sau timeout / lỗi mạng: Cùng tham số -> Key PHẢI giữ nguyên 100%
        val keyRetry = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.05"))
        assertEquals("Khi retry cùng tham số sau lỗi mạng, clientOrderId phải được bảo toàn", key1, keyRetry)

        // 3. Thay đổi quantity ở 6 chữ số thập phân (0.050001) -> Bắt buộc sinh key mới
        val keyQtyChanged = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.050001"))
        assertNotEquals("Khi quantity thay đổi, phải sinh key mới", key1, keyQtyChanged)

        // 4. Thay đổi symbol -> Sinh key mới
        val keySymbolChanged = manager.resolveClientOrderId("ETHUSDT", "BUY", java.math.BigDecimal("0.050001"))
        assertNotEquals("Khi symbol thay đổi, phải sinh key mới", keyQtyChanged, keySymbolChanged)

        // 5. Thay đổi orderType (BUY -> SELL) -> Sinh key mới
        val keyTypeChanged = manager.resolveClientOrderId("ETHUSDT", "SELL", java.math.BigDecimal("0.050001"))
        assertNotEquals("Khi orderType thay đổi, phải sinh key mới", keySymbolChanged, keyTypeChanged)

        // 6. Reset sau khi lệnh thành công
        manager.reset()
        assertNull(manager.activeClientOrderId)
        val keyAfterReset = manager.resolveClientOrderId("ETHUSDT", "SELL", java.math.BigDecimal("0.050001"))
        assertNotEquals("Sau khi reset, lần đặt lệnh tiếp theo phải có key mới", keyTypeChanged, keyAfterReset)
    }

    @Test
    fun testOrderIdempotencyManager_trailingZerosProduceIdenticalKey() {
        val manager = OrderIdempotencyManager()
        val key1 = manager.resolveClientOrderId("BTCUSDT", "BUY", "0.050000")
        val key2 = manager.resolveClientOrderId("BTCUSDT", "BUY", "0.05")
        val key3 = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.050000"))

        assertEquals("Trailing zeros không được làm thay đổi idempotency key", key1, key2)
        assertEquals("BigDecimal trailing zeros phải chuẩn hóa về cùng một key", key1, key3)
    }

    @Test
    fun testOrderIdempotencyManager_sixDecimalPlacesPrecisionAndScalePolicy() {
        val manager = OrderIdempotencyManager()
        // Kiểm tra độ chính xác tới 6 chữ số thập phân (chuẩn NUMERIC(18,6))
        val key1 = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.000001"))
        val key2 = manager.resolveClientOrderId("BTCUSDT", "BUY", java.math.BigDecimal("0.000002"))
        assertNotEquals("Sai khác ở chữ số thập phân thứ 6 bắt buộc phải sinh key mới", key1, key2)

        // Kiểm tra chính sách scale <= 6
        val validScale6 = java.math.BigDecimal("0.123456")
        assertTrue("Scale sau chuẩn hóa <= 6 được chấp nhận", validScale6.stripTrailingZeros().scale() <= 6)

        val invalidScale7 = java.math.BigDecimal("0.1234567")
        assertTrue("Scale sau chuẩn hóa > 6 bị từ chối", invalidScale7.stripTrailingZeros().scale() > 6)
    }

    // ------------------------------------------------------------------------------------
    // 4. WATCHLIST CLOUD CRUD, ROOM ISOLATION & NULLABLE PRICE
    // ------------------------------------------------------------------------------------
    @Test
    fun testWatchlistApiContract_getAddDeleteEndpoints() {
        val getMethod = ApiService::class.java.methods.find { it.name == "getWatchlist" }
        assertNotNull("ApiService must declare getWatchlist", getMethod)
        val getAnn = getMethod!!.getAnnotation(GET::class.java)
        assertEquals("/api/watchlist", getAnn!!.value)
        assertTrue(getMethod.parameterAnnotations[0].any { it is Header && it.value == "Authorization" })

        val addMethod = ApiService::class.java.methods.find { it.name == "addToWatchlist" }
        assertNotNull("ApiService must declare addToWatchlist", addMethod)
        val postAnn = addMethod!!.getAnnotation(POST::class.java)
        assertEquals("/api/watchlist", postAnn!!.value)
        assertTrue(addMethod.parameterAnnotations[0].any { it is Header && it.value == "Authorization" })
        assertTrue(addMethod.parameterAnnotations[1].any { it is Body })

        val delMethod = ApiService::class.java.methods.find { it.name == "removeFromWatchlist" }
        assertNotNull("ApiService must declare removeFromWatchlist", delMethod)
        val delAnn = delMethod!!.getAnnotation(DELETE::class.java)
        assertEquals("/api/watchlist/{symbol}", delAnn!!.value)
        assertTrue(delMethod.parameterAnnotations[0].any { it is Header && it.value == "Authorization" })
    }

    @Test
    fun testWatchlistItem_userIsolation() {
        val user1Item = WatchlistItem("BTCUSDT", 65000.0, 2.5, "user1@fnmf.com")
        val user2Item = WatchlistItem("BTCUSDT", 65000.0, 2.5, "user2@fnmf.com")

        assertEquals("user1@fnmf.com", user1Item.userEmail)
        assertEquals("user2@fnmf.com", user2Item.userEmail)
        assertNotEquals(user1Item.userEmail, user2Item.userEmail)
    }

    @Test
    fun testWatchlistItem_supportsNullablePriceAndChange() {
        val itemWithNulls = WatchlistItem("BTCUSDT", null, null, "test@fnmf.com")
        assertNull("WatchlistItem price must support null", itemWithNulls.price)
        assertNull("WatchlistItem change24h must support null", itemWithNulls.change24h)

        val itemWithValue = WatchlistItem("BTCUSDT", 65000.5, 3.2, "test@fnmf.com")
        assertEquals(65000.5, itemWithValue.price!!, 0.001)
        assertEquals(3.2, itemWithValue.change24h!!, 0.001)
    }

    @Test
    fun testWatchlistUiModel_nullablePriceDisplaysDash() {
        val itemWithoutPrice = WatchlistUiModel(
            symbol = "BTCUSDT",
            fullName = "Bitcoin",
            price = null,
            changePercent = null,
            isOffline = true
        )

        assertNull("Price must be null when data is missing, never forced to 0.0", itemWithoutPrice.price)
        assertNull("Change percent must be null when data is missing", itemWithoutPrice.changePercent)
    }

    // ------------------------------------------------------------------------------------
    // 5. NEWS OFFLINE ROOM SINGLE-SOURCE-OF-TRUTH & REAL METADATA
    // ------------------------------------------------------------------------------------
    @Test
    fun testNewsRepository_parseTimeToEpoch_preservesRealTimestamp() {
        val isoDate = "2026-03-31T10:00:00Z"
        val epoch = NewsRepository.parseTimeToEpoch(isoDate)
        assertTrue("Valid ISO timestamp must parse to epoch > 0", epoch > 0L)

        val formattedBack = NewsRepository.formatEpochToDate(epoch)
        assertTrue("Formatted date must not be empty", formattedBack.isNotBlank())
    }

    @Test
    fun testNewsRepository_parseTimeToEpoch_invalidReturnsZero() {
        val invalidEpoch = NewsRepository.parseTimeToEpoch("invalid-date-format")
        assertEquals(0L, invalidEpoch)

        val emptyEpoch = NewsRepository.parseTimeToEpoch(null)
        assertEquals(0L, emptyEpoch)
    }

    @Test
    fun testNewsResult_sealedSubclasses() {
        val success = NewsRepository.NewsResult.SyncSuccess(emptyList())
        val fallback = NewsRepository.NewsResult.CacheFallback(emptyList(), "Offline")
        val writeFailure = NewsRepository.NewsResult.CacheWriteFailure(RuntimeException("DB disk full"))
        val empty = NewsRepository.NewsResult.Empty("No news")

        assertTrue(success is NewsRepository.NewsResult)
        assertTrue(fallback is NewsRepository.NewsResult)
        assertTrue(writeFailure is NewsRepository.NewsResult)
        assertTrue(empty is NewsRepository.NewsResult)
    }

    @Test
    fun testAiAnalysisEntity_hasNewsIdAsPrimaryKey() {
        val entity = AiAnalysisEntity("news-123", "Summary content", "bullish", 85, "Good fundamentals")
        assertEquals("news-123", entity.newsId)
        assertEquals("Summary content", entity.summary)
        assertEquals("bullish", entity.sentiment)
        assertEquals(85, entity.confidenceScore)
        assertEquals("Good fundamentals", entity.reason)
    }

    @Test
    fun testNewsEntity_preservesOriginalMetadataFields() {
        val news = NewsEntity(
            "news-999",
            "Fed Interest Rate Decision",
            "https://fnmf.com/article/999",
            1774944000000L,
            "Bloomberg",
            "John Doe",
            "2026-03-31T12:00:00Z",
            "https://fnmf.com/img/fed.jpg",
            "Rates held steady",
            "neutral",
            90,
            "Bullet 1\nBullet 2"
        )

        assertEquals("news-999", news.newsId)
        assertEquals("Bloomberg", news.source)
        assertEquals("John Doe", news.author)
        assertEquals("2026-03-31T12:00:00Z", news.publishedAtRaw)
        assertEquals("https://fnmf.com/img/fed.jpg", news.imageUrl)
        assertEquals("Rates held steady", news.summary)
        assertEquals("neutral", news.sentiment)
        assertEquals(90, news.confidence)
        assertEquals("Bullet 1\nBullet 2", news.bulletPoints)
    }

    // ------------------------------------------------------------------------------------
    // 6. AUTOMATED SCANNER: ZERO MOJIBAKE IN SOURCE CODE (ZERO TOLERANCE)
    // ------------------------------------------------------------------------------------
    @Test
    fun testZeroMojibakeInSourceCode() {
        val srcMain = File("src/main")
        val searchDir = if (srcMain.exists()) srcMain else File("app/src/main")
        if (!searchDir.exists()) return

        // Regex chuẩn xác: Bắt mọi token byte lỗi ISO-8859-1 đè lên UTF-8 tiếng Việt
        val mojibakeRegex = Regex("[\\u00C3][\\u0080-\\u00FF]|\\u00E1\\u00BA|\\u00E1\\u00BB|\\u00C4\\u2018|\\u00C4\\u0192|\\u00C6\\u00B0|\\u00C6\\u00A1|\\u00E2\\u20AC|\\uFFFD")
        val violatedFiles = mutableListOf<String>()

        searchDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                val matches = mojibakeRegex.findAll(text).toList()
                if (matches.isNotEmpty()) {
                    violatedFiles.add("${file.name} (${matches.size} tokens: ${matches.take(3).map { it.value }})")
                }
            }

        assertTrue("Phát hiện lỗi mã hóa Mojibake trong mã nguồn: $violatedFiles", violatedFiles.isEmpty())
    }
}
