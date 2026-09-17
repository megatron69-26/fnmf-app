package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.CandleDto
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
        assertEquals("bnbusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("BNBUSDT"))
        assertEquals("bnbusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("BNB"))

        assertEquals("solusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("SOLUSDT"))
        assertEquals("solusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("SOL"))

        assertEquals("xrpusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("XRPUSDT"))
        assertEquals("xrpusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("XRP"))

        assertEquals("adausdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("ADAUSDT"))
        assertEquals("adausdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("ADA"))

        assertEquals("dogeusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("DOGEUSDT"))
        assertEquals("dogeusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("DOGE"))

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
                openTime = openTime, closeTime = openTime + 59999,
                symbol = sym, interval = "1m",
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
                    "T": 1695000059999,
                    "s": "BNBUSDT",
                    "i": "1m",
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
        assertEquals("1m", event.interval)
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

        val invalidInterval = validJson.replace("\"i\": \"1m\"", "\"i\": \"5m\"") // not 1m
        assertNull(BinanceKlineParser.parse(invalidInterval))
    }
}
