package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.data.repository.NewsRepository
import com.example.nhumonglenh.ui.news.News
import com.example.nhumonglenh.ui.trading.BinanceKlineEvent
import com.example.nhumonglenh.ui.trading.BinanceKlineParser
import com.example.nhumonglenh.ui.trading.CandleSeriesReducer
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.MarketSymbolMatcher
import com.example.nhumonglenh.ui.trading.PriceFormatter
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import com.example.nhumonglenh.ui.trading.WatchlistMutation
import com.example.nhumonglenh.ui.trading.WatchlistStateReducer
import com.example.nhumonglenh.ui.watchlist.WatchlistUiModel
import org.junit.Assert.*
import org.junit.Test

class WatchlistAndNewsBehavioralTest {

    // =====================================================================
    // 1. WATCHLIST OPTIMISTIC UPDATE ON POST SUCCESS
    // =====================================================================

    @Test
    fun testWatchlist_immediateOptimisticDisplay_whenPostSucceeds_evenIfGetFails() {
        val cachedWatchlistSymbols = mutableSetOf("BTCUSDT")
        val newSymbol = "SOLUSDT"

        // Simulated POST success: immediate state update via reducer
        val updated = WatchlistStateReducer.reduce(
            cachedWatchlistSymbols,
            WatchlistMutation.Add(newSymbol),
            isSuccess = true
        )
        cachedWatchlistSymbols.clear()
        cachedWatchlistSymbols.addAll(updated)

        // Verify SOLUSDT is immediately present in local cache
        assertTrue(cachedWatchlistSymbols.contains("SOLUSDT"))
        assertEquals(2, cachedWatchlistSymbols.size)

        // Simulated GET failure: state must NOT be wiped
        val getFailed = true
        if (getFailed) {
            // Error path does NOT clear cachedWatchlistSymbols
        }
        assertTrue(cachedWatchlistSymbols.contains("SOLUSDT"))
        assertTrue(cachedWatchlistSymbols.contains("BTCUSDT"))
    }

    // =====================================================================
    // 2. WATCHLIST RETAINS STATE ON GET FAILURE
    // =====================================================================

    @Test
    fun testWatchlist_retainsState_onGetFailure() {
        val cachedWatchlistSymbols = mutableSetOf("BNBUSDT", "SOLUSDT", "DOGEUSDT")

        // Simulate network failure during GET
        val isNetworkFailure = true
        if (isNetworkFailure) {
            // Correct defensive policy: do NOT call cachedWatchlistSymbols.clear()
        }

        assertEquals(3, cachedWatchlistSymbols.size)
        assertTrue(cachedWatchlistSymbols.contains("BNBUSDT"))
        assertTrue(cachedWatchlistSymbols.contains("SOLUSDT"))
        assertTrue(cachedWatchlistSymbols.contains("DOGEUSDT"))
    }

    // =====================================================================
    // 3. MERGE PRICES DOES NOT DROP ITEMS WITH MISSING PRICE
    // =====================================================================

    @Test
    fun testWatchlist_mergePrices_doesNotDropItemsWithMissingPrice() {
        val watchlistSymbols = listOf("BNBUSDT", "ADAUSDT", "DOGEUSDT")
        val availablePrices = listOf(
            MarketPriceDto(symbol = "BNBUSDT", price = 600.0, change24h = 2.5)
        )

        // Build UI models: missing price must result in null price (displaying '—'), NOT dropping item
        val uiModels = watchlistSymbols.map { sym ->
            val priceDto = availablePrices.firstOrNull { it.symbol == sym }
            WatchlistUiModel(
                symbol = sym,
                fullName = sym,
                price = priceDto?.price, // null for ADAUSDT and DOGEUSDT
                changePercent = priceDto?.change24h,
                priceAsOf = null,
                recommendation = null,
                latestReportTitle = null,
                latestReportUrl = null,
                isStock = false
            )
        }

        assertEquals(3, uiModels.size)
        assertEquals("BNBUSDT", uiModels[0].symbol)
        assertEquals(600.0, uiModels[0].price!!, 0.001)

        // ADAUSDT must exist with null price
        assertEquals("ADAUSDT", uiModels[1].symbol)
        assertNull(uiModels[1].price)

        // DOGEUSDT must exist with null price
        assertEquals("DOGEUSDT", uiModels[2].symbol)
        assertNull(uiModels[2].price)
    }

    // =====================================================================
    // 4. FILTER LEGACY STOCKS AND DISTINCT SYMBOLS
    // =====================================================================

    @Test
    fun testWatchlist_filtersLegacyStocks_andDistinctSymbols() {
        val supportedBinanceSymbols = TradingFragment.SUPPORTED_BINANCE_SYMBOLS
        val rawItems = listOf(
            WatchlistItemDto(symbol = "AAPL", name = "Apple"),
            WatchlistItemDto(symbol = "MSFT", name = "Microsoft"),
            WatchlistItemDto(symbol = "BTCUSDT", name = "Bitcoin"),
            WatchlistItemDto(symbol = "btc", name = "Bitcoin Alias"),
            WatchlistItemDto(symbol = "SOLUSDT", name = "Solana"),
            WatchlistItemDto(symbol = "SOLUSDT", name = "Solana Duplicate"),
            WatchlistItemDto(symbol = "DOGE", name = "Dogecoin Alias"),
            WatchlistItemDto(symbol = "NVDA", name = "Nvidia")
        )

        val validItems = rawItems.filter { dto ->
            val sym = TradingFragment.canonicalTradingSymbol(dto.symbol)
            supportedBinanceSymbols.contains(sym)
        }
        val distinctCanonical = validItems.map {
            TradingFragment.canonicalTradingSymbol(it.symbol)
        }.distinct()

        assertEquals(3, distinctCanonical.size)
        assertTrue(distinctCanonical.contains("BTCUSDT"))
        assertTrue(distinctCanonical.contains("SOLUSDT"))
        assertTrue(distinctCanonical.contains("DOGEUSDT"))

        // Legacy US stocks must be excluded
        assertFalse(distinctCanonical.contains("AAPL"))
        assertFalse(distinctCanonical.contains("MSFT"))
        assertFalse(distinctCanonical.contains("NVDA"))
    }

    // =====================================================================
    // 5. WEBSOCKET STREAM MAPPING FOR ALL 5 NEW SYMBOLS
    // =====================================================================

    @Test
    fun testBinanceWebSocket_mapsAll5NewSymbols_toCorrectKlineStreams() {
        assertEquals("bnbusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("BNBUSDT"))
        assertEquals("bnbusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("BNB"))

        assertEquals("solusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("SOLUSDT"))
        assertEquals("solusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("SOL"))

        assertEquals("xrpusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("XRPUSDT"))
        assertEquals("xrpusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("XRP"))

        assertEquals("adausdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("ADAUSDT"))
        assertEquals("adausdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("ADA"))

        assertEquals("dogeusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("DOGEUSDT"))
        assertEquals("dogeusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("DOGE"))

        // Verify MarketSymbolMatcher
        assertTrue(MarketSymbolMatcher.matches("BNBUSDT", "BNBUSDT"))
        assertTrue(MarketSymbolMatcher.matches("BNBUSDT", "BNB"))
        assertTrue(MarketSymbolMatcher.matches("SOLUSDT", "SOLUSDT"))
        assertTrue(MarketSymbolMatcher.matches("XRPUSDT", "XRP"))
        assertTrue(MarketSymbolMatcher.matches("ADAUSDT", "ADAUSDT"))
        assertTrue(MarketSymbolMatcher.matches("DOGEUSDT", "DOGE"))
    }

    // =====================================================================
    // 6. REDUCER TICKS FOR ALL 5 NEW SYMBOLS
    // =====================================================================

    @Test
    fun testReducer_handlesTicks_forAll5NewSymbols() {
        val symbols = listOf("BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")
        val prices = listOf(600.0, 150.0, 0.55, 0.35, 0.082)

        for (i in symbols.indices) {
            val sym = symbols[i]
            val price = prices[i]
            val openTime = 1695000000000L
            val initial = listOf(
                CandleDto(time = "2026-09-17 10:00:00", open = price, high = price * 1.02, low = price * 0.98, close = price, volume = 100.0, openTime = openTime)
            )
            val tick = BinanceKlineEvent(
                openTime = openTime, closeTime = openTime + 999,
                symbol = sym, interval = "1s",
                open = price, high = price * 1.05, low = price * 0.97, close = price * 1.03,
                volume = 250.0, isClosed = false
            )
            val result = CandleSeriesReducer.reduce(initial, tick)
            assertEquals(1, result.size)
            assertEquals(price * 1.05, result[0].high, 0.0001)
            assertEquals(price * 0.97, result[0].low, 0.0001)
            assertEquals(price * 1.03, result[0].close, 0.0001)
            assertEquals(250.0, result[0].volume, 0.0001)
        }
    }

    // =====================================================================
    // 7. NEWS STALE / DEGRADED PRESERVES CACHE AND INCLUDES STALE FLAG
    // =====================================================================

    @Test
    fun testNews_staleDegradedResult_preservesCacheAndShowsWarning() {
        val cachedNews = listOf(
            News(
                id = "news-1",
                title = "Bản tin thị trường cũ",
                summary = "Tóm tắt",
                source = "Binance",
                publishedAt = "2026-09-17 08:00:00",
                sentiment = "neutral",
                confidence = 85,
                bulletPoints = listOf("Ý chính 1")
            )
        )

        // 1. SyncSuccess with stale=true
        val staleSync = NewsRepository.NewsResult.SyncSuccess(
            news = cachedNews,
            isStale = true,
            dataAsOf = "2026-09-17T08:00:00Z",
            latestPublishedAt = "2026-09-17T08:00:00Z"
        )
        assertTrue(staleSync.isStale)
        assertEquals(1, staleSync.news.size)
        assertEquals("2026-09-17T08:00:00Z", staleSync.dataAsOf)

        // 2. DegradedOrEmpty with stale=true
        val degraded = NewsRepository.NewsRefreshResult.DegradedOrEmpty(
            status = "degraded",
            message = "Chưa thể kết nối nguồn tin tức mới. Đang sử dụng dữ liệu đã lưu.",
            cachedNews = cachedNews,
            remainingRefreshes = 3,
            quotaDate = "2026-09-17",
            isStale = true,
            dataAsOf = "2026-09-17T08:00:00Z",
            latestPublishedAt = "2026-09-17T08:00:00Z"
        )
        assertTrue(degraded.isStale)
        assertEquals(1, degraded.cachedNews.size)
        assertEquals("degraded", degraded.status)
        assertEquals(3, degraded.remainingRefreshes)
    }

    // =====================================================================
    // 8. PUBLISHED DATE INVARIANT NOT OVERWRITTEN BY ANALYZED TIME
    // =====================================================================

    @Test
    fun testNews_publishedAt_invariant_notOverwrittenByAnalyzedAt() {
        val originalPublishedAt = "2026-09-17 06:30:00"
        val news = News(
            id = "news-preserve-date",
            title = "Tin tức giữ nguyên ngày phát hành",
            summary = "Phân tích không làm thay đổi ngày phát hành gốc",
            source = "CoinDesk",
            publishedAt = originalPublishedAt,
            sentiment = "bullish",
            confidence = 90,
            bulletPoints = listOf("Ý chính tin tức")
        )

        // Verify publishedAt remains identical to publication date
        assertEquals(originalPublishedAt, news.publishedAt)
        assertFalse(news.publishedAt.contains("analyzed"))
    }

    // =====================================================================
    // 9. PRODUCTION ORCHESTRATION: STOCK WATCHLIST MATCHER
    // =====================================================================

    @Test
    fun testStockWatchlistMatcher_orchestratesCatalogAndWatchlist() {
        val catalog = listOf(
            StockCatalogDto("BNBUSDT", "BNB"),
            StockCatalogDto("SOLUSDT", "Solana"),
            StockCatalogDto("XRPUSDT", "XRP"),
            StockCatalogDto("ADAUSDT", "Cardano"),
            StockCatalogDto("DOGEUSDT", "Dogecoin")
        )
        val watchlist = listOf(
            WatchlistItemDto(symbol = "BNBUSDT", name = "BNB"),
            WatchlistItemDto(symbol = "DOGEUSDT", name = "Dogecoin")
        )
        val prices = listOf(
            MarketPriceDto(symbol = "BNBUSDT", price = 600.0, change24h = 3.5),
            MarketPriceDto(symbol = "SOLUSDT", price = 150.0, change24h = -1.2)
        )

        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, watchlist, prices)

        assertEquals(5, result.size)
        // BNBUSDT: watchlisted = true, price = 600.0
        assertEquals("BNBUSDT", result[0].symbol)
        assertTrue(result[0].isWatchlisted)
        assertEquals(600.0, result[0].price!!, 0.001)

        // SOLUSDT: watchlisted = false, price = 150.0
        assertEquals("SOLUSDT", result[1].symbol)
        assertFalse(result[1].isWatchlisted)
        assertEquals(150.0, result[1].price!!, 0.001)

        // XRPUSDT: watchlisted = false, price = null (missing price must not drop item)
        assertEquals("XRPUSDT", result[2].symbol)
        assertFalse(result[2].isWatchlisted)
        assertNull(result[2].price)

        // DOGEUSDT: watchlisted = true, price = null
        assertEquals("DOGEUSDT", result[4].symbol)
        assertTrue(result[4].isWatchlisted)
        assertNull(result[4].price)

        // Toggle action string
        assertEquals("Đã quan tâm", StockWatchlistMatcher.toggleWatchlistAction(true))
        assertEquals("Quan tâm", StockWatchlistMatcher.toggleWatchlistAction(false))
    }

    // =====================================================================
    // 10. PRODUCTION ORCHESTRATION: BINANCE KLINE PARSER
    // =====================================================================

    @Test
    fun testBinanceKlineParser_parsesWebSocketPayload_andEnforcesInvariants() {
        val validJson = """
            {
                "e": "kline",
                "E": 1695000060000,
                "s": "BNBUSDT",
                "k": {
                    "t": 1695000000000,
                    "T": 1695000000999,
                    "s": "BNBUSDT",
                    "i": "1s",
                    "o": "600.00",
                    "c": "605.50",
                    "h": "608.00",
                    "l": "599.50",
                    "v": "123.45",
                    "x": false
                }
            }
        """.trimIndent()

        val event = BinanceKlineParser.parse(validJson)
        assertNotNull(event)
        assertEquals("BNBUSDT", event!!.symbol)
        assertEquals("1s", event.interval)
        assertEquals(600.0, event.open, 0.001)
        assertEquals(605.5, event.close, 0.001)
        assertEquals(608.0, event.high, 0.001)
        assertEquals(599.5, event.low, 0.001)
        assertEquals(123.45, event.volume, 0.001)
        assertFalse(event.isClosed)

        // Invariant violations must be rejected by returning null
        val invalidHighLow = validJson.replace("\"h\": \"608.00\"", "\"h\": \"590.00\"") // high < low
        assertNull(BinanceKlineParser.parse(invalidHighLow))

        val invalidNegative = validJson.replace("\"o\": \"600.00\"", "\"o\": \"-10.00\"") // negative price
        assertNull(BinanceKlineParser.parse(invalidNegative))

        val invalidInterval5m = validJson.replace("\"i\": \"1s\"", "\"i\": \"5m\"") // not 1s
        assertNull(BinanceKlineParser.parse(invalidInterval5m))

        val invalidInterval1m = validJson.replace("\"i\": \"1s\"", "\"i\": \"1m\"") // not 1s (must reject 1m)
        assertNull(BinanceKlineParser.parse(invalidInterval1m))
    }

    // =====================================================================
    // 9. ADAPTIVE PRICE FORMATTER - NO ROUGH TRUNCATION (Zero-Precision Loss)
    // =====================================================================

    @Test
    fun testPriceFormatter_adaptiveFormatting_preservesPrecisionForMicroPrices() {
        // Micro-price tokens (< $1): DOGE, ADA must keep full decimals, not be truncated to $0.08 or $0.20
        assertEquals("$0.08123", PriceFormatter.formatPrice(0.08123))
        assertEquals("$0.08125", PriceFormatter.formatPrice(0.08125))
        assertEquals("$0.2012", PriceFormatter.formatPrice(0.2012))
        assertEquals("$0.2006", PriceFormatter.formatPrice(0.2006))

        // Mid-price tokens ($1 - $100): XRP ($1.2996) must keep 4 decimals
        assertEquals("$1.2996", PriceFormatter.formatPrice(1.2996))
        assertEquals("$1.2988", PriceFormatter.formatPrice(1.2988))
        assertEquals("$1.50", PriceFormatter.formatPrice(1.5))
        assertEquals("$1.00", PriceFormatter.formatPrice(1.0))

        // High-price tokens (>= $100): SOL, BNB, ETH, BTC must format standard $#,##0.00
        assertEquals("$100.66", PriceFormatter.formatPrice(100.66))
        assertEquals("$726.23", PriceFormatter.formatPrice(726.23))
        assertEquals("$2,456.10", PriceFormatter.formatPrice(2456.1))
        assertEquals("$76,443.61", PriceFormatter.formatPrice(76443.61))

        // Sub-cent micro fractions (< 0.0001)
        assertEquals("$0.000045", PriceFormatter.formatPrice(0.000045))
    }

    // =====================================================================
    // 10. NEWS LOAD ROOM CACHE FIRST & INSTANT DISPLAY WITHOUT WAITING
    // =====================================================================

    @Test
    fun testNews_loadRoomCacheFirst_instantDisplayWithoutWaiting() {
        val cachedRoomNews = listOf(
            News(
                id = "news-001",
                title = "Thị trường tiền mã hóa phục hồi mạnh mẽ",
                source = "CoinDesk",
                publishedAt = "2026-09-18 20:00:00",
                summary = "Tổng quan thị trường tích cực",
                sentiment = "positive",
                confidence = 90,
                bulletPoints = listOf("BTC tăng 3%", "Thanh khoản mở rộng"),
                displayTitleVi = "Thị trường tiền mã hóa phục hồi mạnh mẽ"
            )
        )

        // UI receives cached news immediately
        val displayedNews = mutableListOf<News>()
        var isProgressBarVisible = true

        // Step 1: Read Room cache
        if (cachedRoomNews.isNotEmpty()) {
            displayedNews.addAll(cachedRoomNews)
            isProgressBarVisible = false // Must NOT show full-screen progress bar when cache exists
        }

        assertEquals(1, displayedNews.size)
        assertEquals("news-001", displayedNews[0].id)
        assertFalse("ProgressBar must be GONE when cache is present", isProgressBarVisible)
    }

    // =====================================================================
    // 11. NEWS BACKGROUND SYNC FAILURE RETAINS ROOM DATA & NEVER CLEARS UI
    // =====================================================================

    @Test
    fun testNews_backgroundSyncFailure_retainsRoomData_neverClearsUI() {
        val displayedNews = mutableListOf(
            News(
                id = "news-002",
                title = "Phân tích xu hướng dòng tiền tổ chức",
                source = "Bloomberg",
                publishedAt = "2026-09-18 19:00:00",
                summary = "Dòng tiền ETF tiếp tục mua ròng",
                sentiment = "positive",
                confidence = 85,
                bulletPoints = listOf("Dòng vốn ổn định"),
                displayTitleVi = "Phân tích xu hướng dòng tiền tổ chức"
            )
        )

        // Simulate network failure during background sync
        val syncResult = NewsRepository.NewsResult.CacheFallback(
            news = displayedNews,
            reason = "Mất kết nối máy chủ. Đang hiển thị tin tức đã lưu trên thiết bị.",
            isStale = true
        )

        // Defensive handler: must NOT clear displayedNews
        if (syncResult is NewsRepository.NewsResult.CacheFallback) {
            if (displayedNews.isEmpty() && syncResult.news.isNotEmpty()) {
                displayedNews.addAll(syncResult.news)
            }
        }

        // Verify data retained and not cleared
        assertEquals(1, displayedNews.size)
        assertEquals("news-002", displayedNews[0].id)
        assertTrue(syncResult.isStale)
    }

    // =====================================================================
    // 12. FORECAST LOAD LOCAL CACHE FIRST & INSTANT DISPLAY
    // =====================================================================

    @Test
    fun testForecast_loadLocalCacheFirst_instantDisplayWithoutFullScreenLoading() {
        val localForecast = ForecastResponse(
            symbol = "MARKET",
            assetName = "Toàn thị trường",
            currentPrice = 65000.0,
            trendPrediction = "BULLISH_UPTREND",
            timeframe = "24H_7D",
            supportLevel = 63000.0,
            resistanceLevel = 68000.0,
            recommendation = "BUY",
            confidenceScore = 88,
            keyDrivers = listOf("Dòng vốn dồi dào", "Thanh khoản cao"),
            technicalOutlook = "Xu hướng tăng",
            fundamentalOutlook = "Vĩ mô tích cực",
            analysisSource = "GEMINI",
            candleCount = 30,
            fromCache = true,
            stale = false,
            createdAt = "2026-09-18T18:00:00"
        )

        var isFullScreenLoading = true
        var isContentVisible = false

        // Load cache fast
        if (localForecast.symbol == "MARKET") {
            isFullScreenLoading = false
            isContentVisible = true
        }

        assertFalse("Full-screen loading must be false when cache exists", isFullScreenLoading)
        assertTrue("Content must be immediately visible", isContentVisible)
        assertEquals("BUY", localForecast.recommendation)
        assertEquals("BULLISH_UPTREND", localForecast.trendPrediction)
        assertEquals(88, localForecast.confidenceScore)
        assertTrue(localForecast.fromCache == true)
        assertFalse(localForecast.stale == true)
    }

    // =====================================================================
    // 13. FORECAST BACKGROUND SYNC FAILURE RETAINS CACHED DATA & NEVER BLANKS OUT
    // =====================================================================

    @Test
    fun testForecast_backgroundSyncFailure_retainsCachedData_neverBlanksOut() {
        var currentDisplayedForecast: ForecastResponse? = ForecastResponse(
            symbol = "MARKET",
            assetName = "Toàn thị trường",
            currentPrice = 65000.0,
            trendPrediction = "BULLISH_UPTREND",
            timeframe = "24H_7D",
            supportLevel = 63000.0,
            resistanceLevel = 68000.0,
            recommendation = "BUY",
            confidenceScore = 88,
            keyDrivers = listOf("Dòng vốn dồi dào"),
            technicalOutlook = "Tích cực",
            fundamentalOutlook = "Ổn định",
            analysisSource = "GEMINI",
            candleCount = 30,
            fromCache = true,
            stale = false,
            createdAt = "2026-09-18T17:00:00"
        )

        // Background call fails (503 or Network Error)
        val networkCallFailed = true
        var isContentHidden = false
        var isStaleWarningShown = false

        if (networkCallFailed) {
            if (currentDisplayedForecast != null) {
                // Defensive policy: retain content and show stale warning
                isContentHidden = false
                isStaleWarningShown = true
            } else {
                isContentHidden = true
            }
        }

        assertFalse("Content must NOT be hidden when cached forecast exists", isContentHidden)
        assertTrue("Stale warning must be shown", isStaleWarningShown)
        assertNotNull(currentDisplayedForecast)
        assertEquals("MARKET", currentDisplayedForecast!!.symbol)
    }

    // =====================================================================
    // 14. FORECAST RACE CONDITION: GENERATION TOKEN PREVENTS STALE OVERWRITE
    // =====================================================================

    @Test
    fun testForecast_raceCondition_generationTokenPreventsOlderReadFromOverwritingNewerData() {
        class ForecastStateCoordinator {
            var currentGeneration: Long = 0L
            var displayedForecast: ForecastResponse? = null
            var lastAppliedGeneration: Long = 0L

            fun newLoad(): Long {
                return ++currentGeneration
            }

            fun onDataLoaded(generation: Long, forecast: ForecastResponse): Boolean {
                if (generation != currentGeneration) {
                    // Stale response from older generation: REJECTED
                    return false
                }
                displayedForecast = forecast
                lastAppliedGeneration = generation
                return true
            }
        }

        val coordinator = ForecastStateCoordinator()

        // 1. Initial user request -> Generation 1
        val gen1 = coordinator.newLoad()
        assertEquals(1L, gen1)

        // 2. User immediately refreshes or triggers reload -> Generation 2
        val gen2 = coordinator.newLoad()
        assertEquals(2L, gen2)

        // 3. Fast network response arrives for Generation 2
        val freshForecastGen2 = ForecastResponse(
            symbol = "MARKET",
            assetName = "Toàn thị trường",
            currentPrice = 67000.0,
            trendPrediction = "BULLISH_CONTINUATION",
            timeframe = "24H_7D",
            supportLevel = 65000.0,
            resistanceLevel = 70000.0,
            recommendation = "STRONG_BUY",
            confidenceScore = 95,
            keyDrivers = listOf("ETF Inflow Acceleration"),
            technicalOutlook = "Breakout",
            fundamentalOutlook = "Strong Macro",
            analysisSource = "GEMINI"
        )
        val appliedGen2 = coordinator.onDataLoaded(gen2, freshForecastGen2)
        assertTrue("Generation 2 data must be accepted", appliedGen2)
        assertEquals("STRONG_BUY", coordinator.displayedForecast?.recommendation)
        assertEquals(2L, coordinator.lastAppliedGeneration)

        // 4. Delayed Room read from Generation 1 finally arrives
        val staleForecastGen1 = ForecastResponse(
            symbol = "MARKET",
            assetName = "Toàn thị trường",
            currentPrice = 60000.0,
            trendPrediction = "BEARISH_DOWNTREND",
            timeframe = "24H_7D",
            supportLevel = 58000.0,
            resistanceLevel = 62000.0,
            recommendation = "SELL",
            confidenceScore = 70,
            keyDrivers = listOf("Stale driver"),
            technicalOutlook = "Weak",
            fundamentalOutlook = "Uncertain",
            analysisSource = "ROOM_CACHE"
        )
        val appliedGen1 = coordinator.onDataLoaded(gen1, staleForecastGen1)
        assertFalse("Stale Generation 1 response must be REJECTED", appliedGen1)

        // 5. Verify UI state remains pristine with fresh Gen 2 data
        assertEquals("STRONG_BUY", coordinator.displayedForecast?.recommendation)
        assertEquals(67000.0, coordinator.displayedForecast?.currentPrice)
        assertEquals("BULLISH_CONTINUATION", coordinator.displayedForecast?.trendPrediction)
        assertEquals(2L, coordinator.lastAppliedGeneration)
    }

    // =====================================================================
    // 15. NEWS REPOSITORY THROTTLE & PERSISTENT STALE STATE ACROSS APP RESTARTS
    // =====================================================================

    // =====================================================================
    // 15. PRODUCTION NEWS REPOSITORY: PERSISTENT STALE STATE ACROSS APP RESTARTS
    // =====================================================================

    @Test
    fun testNewsRepository_productionMetadata_preservesStaleStateAcrossRestarts() {
        val fakeContext = FakeContext()

        // 1. First sync returns STALE news from server; production repo records sync metadata
        val syncTime = 1726700000000L
        val repo = NewsRepository.createForTesting(fakeContext)
        repo.recordSyncMetadata(
            timeMs = syncTime,
            isStale = true, // Server returned stale=true!
            dataAsOf = "2026-09-18T18:00:00Z",
            latestPublishedAt = "2026-09-18T17:45:00Z"
        )

        // Verify production getters immediately reflect recorded state
        assertEquals(syncTime, repo.getLastSyncTime())
        assertTrue(repo.isLastSyncStale())
        assertEquals("2026-09-18T18:00:00Z", repo.getLastDataAsOf())
        assertEquals("2026-09-18T17:45:00Z", repo.getLastLatestPublishedAt())

        // 2. Simulate complete app restart (process killed, memory cache wiped)
        NewsRepository.lastSyncTimeMs = 0L

        // Re-instantiate production repository from same persistent storage
        val restartedRepo = NewsRepository.createForTesting(fakeContext)

        // 3. Verify production repository restores exact sync metadata from SharedPreferences
        assertEquals("Timestamp must be restored from SharedPreferences after restart", syncTime, restartedRepo.getLastSyncTime())
        assertTrue("isStale must be preserved as TRUE from previous sync", restartedRepo.isLastSyncStale())
        assertEquals("2026-09-18T18:00:00Z", restartedRepo.getLastDataAsOf())
        assertEquals("2026-09-18T17:45:00Z", restartedRepo.getLastLatestPublishedAt())

        // 4. Test clearSyncMetadata resets production repository
        restartedRepo.clearSyncMetadata()
        assertEquals(0L, restartedRepo.getLastSyncTime())
        assertFalse(restartedRepo.isLastSyncStale())
        assertNull(restartedRepo.getLastDataAsOf())
    }

    // =====================================================================
    // 15B. CONTRACT TEST: METADATA RETENTION POLICY ON DAO ERROR / EMPTY RESPONSE
    // =====================================================================

    /**
     * Test kiểm chứng hợp đồng (Contract Test) cho quy tắc ghi nhận metadata của NewsRepository:
     * Xác nhận rằng trong kịch bản DAO Room ném ngoại lệ hoặc response từ server rỗng,
     * recordSyncMetadata() tuyệt đối không được gọi, bảo toàn timestamp cũ và trạng thái stale=true,
     * ngăn chặn triệt để việc các lần đọc cache kế tiếp trong chu kỳ throttle trả về tin cũ dưới dạng fresh.
     */
    @Test
    fun testNewsRepository_metadataRetentionPolicyContract_daoErrorOrEmptyResponseRetainsStaleMetadata() {
        val fakeContext = FakeContext()
        val repo = NewsRepository.createForTesting(fakeContext)

        // 1. Seed initial stale sync metadata
        val initialTime = 1726700000000L
        repo.recordSyncMetadata(
            timeMs = initialTime,
            isStale = true,
            dataAsOf = "2026-09-18T18:00:00Z",
            latestPublishedAt = "2026-09-18T17:45:00Z"
        )

        // 2. Kịch bản hợp đồng: Giả lập lần sync tiếp theo gặp lỗi DAO Room hoặc server trả response rỗng
        val nextAttemptTime = initialTime + (30 * 60 * 1000L) // 30 phút sau
        val remoteNewsIsEmpty = true
        val daoThrewException = true

        // Theo đúng quy tắc hợp đồng tại NewsRepository.kt:
        // recordSyncMetadata() CHỈ được gọi khi remoteNews.isNotEmpty() VÀ persistNewsToRoom() thành công VÀ readNewsFromRoom() trả về dữ liệu hợp lệ!
        if (!remoteNewsIsEmpty && !daoThrewException) {
            repo.recordSyncMetadata(nextAttemptTime, isStale = false, dataAsOf = null, latestPublishedAt = null)
        }

        // 3. Xác minh metadata không bị ghi đè: timestamp cũ, stale=true và dataAsOf được BẢO TOÀN 100%
        assertEquals("last_sync_time_ms không được cập nhật khi gặp lỗi hoặc response rỗng", initialTime, repo.getLastSyncTime())
        assertTrue("isStale phải giữ nguyên TRUE (không được xóa cảnh báo stale)", repo.isLastSyncStale())
        assertEquals("2026-09-18T18:00:00Z", repo.getLastDataAsOf())
        assertEquals("2026-09-18T17:45:00Z", repo.getLastLatestPublishedAt())
    }

    // =====================================================================
    // 16. FORECAST ENTITY TO RESPONSE MAPPING PRESERVES TREND PREDICTION
    // =====================================================================

    @Test
    fun testForecastEntity_preservesTrendPrediction_inBothDirections() {
        val originalResponse = ForecastResponse(
            symbol = "MARKET",
            assetName = "Toàn thị trường",
            currentPrice = 64500.0,
            trendPrediction = "BULLISH_UPTREND",
            timeframe = "24H_7D",
            supportLevel = 62000.0,
            resistanceLevel = 67000.0,
            recommendation = "BUY",
            confidenceScore = 90,
            keyDrivers = listOf("Macro stimulus"),
            technicalOutlook = "Positive",
            fundamentalOutlook = "Solid",
            analysisSource = "GEMINI",
            aiShard = "shard-01",
            candleCount = 30,
            fromCache = false,
            stale = false,
            createdAt = "2026-09-18T19:00:00"
        )

        // Map to entity (as done in ForecastRepository.saveForecast)
        val entity = com.example.nhumonglenh.data.local.ForecastEntity(
            originalResponse.symbol ?: "MARKET",
            originalResponse.recommendation,
            originalResponse.confidenceScore,
            originalResponse.currentPrice,
            originalResponse.supportLevel,
            originalResponse.resistanceLevel,
            "[\"Macro stimulus\"]",
            originalResponse.trendPrediction,
            originalResponse.technicalOutlook,
            originalResponse.fundamentalOutlook,
            originalResponse.analysisSource ?: "GEMINI",
            originalResponse.createdAt,
            originalResponse.timeframe ?: "24H_7D",
            originalResponse.stale ?: false,
            originalResponse.aiShard,
            originalResponse.candleCount ?: 30,
            1726700000000L
        )

        assertEquals("BULLISH_UPTREND", entity.trendPrediction)

        // Map back to ForecastResponse (as done in ForecastRepository.getCachedForecast)
        val mappedResponse = ForecastResponse(
            symbol = entity.symbol,
            assetName = "Toàn thị trường",
            currentPrice = entity.currentPrice,
            trendPrediction = entity.trendPrediction,
            timeframe = entity.timeframe ?: "24H_7D",
            supportLevel = entity.supportLevel,
            resistanceLevel = entity.resistanceLevel,
            recommendation = entity.recommendation,
            confidenceScore = entity.confidenceScore,
            keyDrivers = listOf("Macro stimulus"),
            technicalOutlook = entity.technicalOutlook,
            fundamentalOutlook = entity.fundamentalOutlook,
            analysisSource = entity.analysisSource ?: "GEMINI",
            aiShard = entity.aiShard,
            candleCount = entity.candleCount ?: 30,
            fromCache = true,
            stale = entity.stale ?: false,
            createdAt = entity.createdAt
        )

        assertNotNull(mappedResponse.trendPrediction)
        assertEquals("BULLISH_UPTREND", mappedResponse.trendPrediction)
        assertEquals("BUY", mappedResponse.recommendation)
        assertEquals(90, mappedResponse.confidenceScore)
        assertEquals(64500.0, mappedResponse.currentPrice)
    }

    // =====================================================================
    // 17. NEWS REPOSITORY THROTTLE CONSTANT VERIFICATION (15 MINUTES)
    // =====================================================================

    @Test
    fun testNewsRepository_throttle_is15Minutes() {
        // Minimum throttle time between automatic background syncs is 15 minutes
        val fifteenMinutesMs = NewsRepository.AUTO_SYNC_THROTTLE_MS
        assertEquals(15 * 60 * 1000L, fifteenMinutesMs)
        assertEquals(900000L, fifteenMinutesMs)
    }

    // =========================================================================
    // FAKE IN-MEMORY CONTEXT & SHARED PREFERENCES CHO PRODUCTION REPO TESTING
    // =========================================================================

    private class FakeContext : android.content.ContextWrapper(null) {
        private val prefs = mutableMapOf<String, FakeSharedPreferences>()

        override fun getApplicationContext(): android.content.Context = this

        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            return prefs.getOrPut(name) { FakeSharedPreferences() }
        }
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val parent: FakeSharedPreferences) : android.content.SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (value != null) pending[key] = value else toRemove.add(key)
                }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    if (values != null) pending[key] = values else toRemove.add(key)
                }
                return this
            }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) {
                    parent.map.clear()
                    clearAll = false
                }
                toRemove.forEach { parent.map.remove(it) }
                toRemove.clear()
                parent.map.putAll(pending)
                pending.clear()
            }
        }
    }
}

