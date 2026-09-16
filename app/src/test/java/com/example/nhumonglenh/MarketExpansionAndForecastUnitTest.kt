package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.ApiService
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.ui.trading.MarketDataProviderPolicy
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.MarketSymbolMatcher
import com.example.nhumonglenh.ui.trading.StockTradePolicy
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

class MarketExpansionAndForecastUnitTest {

    private val gson = Gson()

    // -------------------------------------------------------------------------
    // 1. WEBSOCKET STREAM RESOLUTION FOR ALL 8 ASSETS
    // -------------------------------------------------------------------------
    @Test
    fun testMarketStreamHelper_resolvesAllEightAssets() {
        // BTC
        assertEquals("btcusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("BTCUSDT"))
        assertEquals("btcusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("BTC"))

        // ETH
        assertEquals("ethusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("ETHUSDT"))
        assertEquals("ethusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("ETH"))

        // XAU (PAXG reference)
        assertEquals("paxgusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("XAUUSD"))
        assertEquals("paxgusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("XAU"))
        assertEquals("paxgusdt@kline_1m", MarketStreamHelper.resolveWebSocketStream("PAXGUSDT"))

        // 5 New Binance Pairs
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

        // Unsupported / Legacy symbols
        assertNull(MarketStreamHelper.resolveWebSocketStream("AAPL"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("MSFT"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("USOIL"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("UNKNOWN"))
    }

    // -------------------------------------------------------------------------
    // 2. MARKET SYMBOL MATCHER FOR REALTIME KLINE EVENTS
    // -------------------------------------------------------------------------
    @Test
    fun testMarketSymbolMatcher_matchesAllSupportedSymbols() {
        assertTrue(MarketSymbolMatcher.matches("BTCUSDT", "BTCUSDT"))
        assertTrue(MarketSymbolMatcher.matches("BTCUSDT", "BTC"))
        assertTrue(MarketSymbolMatcher.matches("ETHUSDT", "ETHUSDT"))
        assertTrue(MarketSymbolMatcher.matches("ETHUSDT", "ETH"))
        assertTrue(MarketSymbolMatcher.matches("PAXGUSDT", "XAUUSD"))
        assertTrue(MarketSymbolMatcher.matches("PAXGUSDT", "XAU"))

        assertTrue(MarketSymbolMatcher.matches("BNBUSDT", "BNBUSDT"))
        assertTrue(MarketSymbolMatcher.matches("BNBUSDT", "BNB"))

        assertTrue(MarketSymbolMatcher.matches("SOLUSDT", "SOLUSDT"))
        assertTrue(MarketSymbolMatcher.matches("SOLUSDT", "SOL"))

        assertTrue(MarketSymbolMatcher.matches("XRPUSDT", "XRPUSDT"))
        assertTrue(MarketSymbolMatcher.matches("XRPUSDT", "XRP"))

        assertTrue(MarketSymbolMatcher.matches("ADAUSDT", "ADAUSDT"))
        assertTrue(MarketSymbolMatcher.matches("ADAUSDT", "ADA"))

        assertTrue(MarketSymbolMatcher.matches("DOGEUSDT", "DOGEUSDT"))
        assertTrue(MarketSymbolMatcher.matches("DOGEUSDT", "DOGE"))

        // Cross-matching must return false
        assertFalse(MarketSymbolMatcher.matches("BNBUSDT", "BTCUSDT"))
        assertFalse(MarketSymbolMatcher.matches("SOLUSDT", "ADAUSDT"))
        assertFalse(MarketSymbolMatcher.matches("DOGEUSDT", "XAUUSD"))
    }

    // -------------------------------------------------------------------------
    // 3. CATALOG WATCHLIST MATCHER WITH 24H CHANGE & PRICES
    // -------------------------------------------------------------------------
    @Test
    fun testStockWatchlistMatcher_with24hChangeAndPrices() {
        val catalog = listOf(
            StockCatalogDto("BNBUSDT", "BNB (Binance)"),
            StockCatalogDto("SOLUSDT", "Solana (Binance)"),
            StockCatalogDto("XRPUSDT", "XRP (Binance)"),
            StockCatalogDto("ADAUSDT", "Cardano (Binance)"),
            StockCatalogDto("DOGEUSDT", "Dogecoin (Binance)")
        )

        val watchlist = listOf(
            WatchlistItemDto(symbol = "BNBUSDT", name = "BNB"),
            WatchlistItemDto(symbol = "DOGEUSDT", name = "Dogecoin")
        )

        val prices = listOf(
            MarketPriceDto(symbol = "BNBUSDT", price = 600.0, change24h = 3.5),
            MarketPriceDto(symbol = "SOLUSDT", price = 150.0, change24h = -2.1),
            MarketPriceDto(symbol = "XRPUSDT", price = 0.55, change24h = 1.2),
            MarketPriceDto(symbol = "ADAUSDT", price = 0.45, change24h = -0.5),
            MarketPriceDto(symbol = "DOGEUSDT", price = 0.12, change24h = 5.0)
        )

        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, watchlist, prices)
        assertEquals(5, result.size)

        val bnb = result.first { it.symbol == "BNBUSDT" }
        assertTrue(bnb.isWatchlisted)
        assertEquals(600.0, bnb.price!!, 0.001)
        assertEquals(3.5, bnb.change24h!!, 0.001)

        val sol = result.first { it.symbol == "SOLUSDT" }
        assertFalse(sol.isWatchlisted)
        assertEquals(150.0, sol.price!!, 0.001)
        assertEquals(-2.1, sol.change24h!!, 0.001)

        val doge = result.first { it.symbol == "DOGEUSDT" }
        assertTrue(doge.isWatchlisted)
        assertEquals(0.12, doge.price!!, 0.001)
        assertEquals(5.0, doge.change24h!!, 0.001)
    }

    // -------------------------------------------------------------------------
    // 4. MARKET DATA PROVIDER POLICY FOR ALL 8 ASSETS
    // -------------------------------------------------------------------------
    @Test
    fun testMarketDataProviderPolicy_allEightAssetsMapToBinance() {
        val supportedSymbols = listOf(
            "BTCUSDT", "ETHUSDT", "XAUUSD", "PAXGUSDT",
            "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT"
        )
        for (sym in supportedSymbols) {
            assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName(sym))
        }
    }

    // -------------------------------------------------------------------------
    // 5. FORECAST API CONTRACT (GET /market & POST /market/refresh)
    // -------------------------------------------------------------------------
    @Test
    fun testForecastApiContract_hasMarketEndpoints() {
        val getMarketMethod = ApiService::class.java.methods.find { it.name == "getMarketForecast" }
        assertNotNull("ApiService must declare getMarketForecast", getMarketMethod)
        val getAnn = getMarketMethod!!.getAnnotation(GET::class.java)
        assertNotNull("getMarketForecast must have @GET", getAnn)
        assertEquals("/api/forecast/market", getAnn!!.value)

        val refreshMarketMethod = ApiService::class.java.methods.find { it.name == "refreshMarketForecast" }
        assertNotNull("ApiService must declare refreshMarketForecast", refreshMarketMethod)
        val postAnn = refreshMarketMethod!!.getAnnotation(POST::class.java)
        assertNotNull("refreshMarketForecast must have @POST", postAnn)
        assertEquals("/api/forecast/market/refresh", postAnn!!.value)
    }

    // -------------------------------------------------------------------------
    // 6. FORECAST RESPONSE DESERIALIZATION WITH STALE & AI_SHARD
    // -------------------------------------------------------------------------
    @Test
    fun testForecastResponse_deserializationWithStaleAndAiShard() {
        val json = """
            {
                "symbol": "MARKET",
                "assetName": "Tổng quan thị trường tài chính",
                "currentPrice": 65000.0,
                "trendPrediction": "BULLISH",
                "timeframe": "24H_7D",
                "supportLevel": 62000.0,
                "resistanceLevel": 68000.0,
                "recommendation": "BUY",
                "confidenceScore": 90,
                "keyDrivers": ["Dòng vốn thị trường mạnh", "Khối lượng giao dịch bùng nổ"],
                "technicalOutlook": "Tích cực",
                "fundamentalOutlook": "Khả quan",
                "fromCache": true,
                "stale": true,
                "aiShard": "GEMINI_SHARD_1"
            }
        """.trimIndent()

        val parsed = gson.fromJson(json, ForecastResponse::class.java)
        assertNotNull(parsed)
        assertEquals("MARKET", parsed.symbol)
        assertEquals("BUY", parsed.recommendation)
        assertEquals(true, parsed.fromCache)
        assertEquals(true, parsed.stale)
        assertEquals("GEMINI_SHARD_1", parsed.aiShard)
    }

    // -------------------------------------------------------------------------
    // 7. NEW BINANCE ASSETS ARE NOT STOCKS
    // -------------------------------------------------------------------------
    @Test
    fun testNewBinanceAssets_notIdentifiedAsStocks() {
        val newPairs = listOf("BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")
        for (pair in newPairs) {
            assertFalse("$pair must not be a stock", StockTradePolicy.isStock(pair))
        }
    }
}
